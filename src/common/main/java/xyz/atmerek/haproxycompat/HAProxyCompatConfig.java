package xyz.atmerek.haproxycompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class HAProxyCompatConfig {

    public static final Logger LOGGER = LoggerFactory.getLogger("haproxycompat");

    @FunctionalInterface
    public interface Value<T> {
        T get();
    }

    // Initialized by the loader-specific entrypoint before any connections arrive.
    public static Value<Boolean> ENABLED;
    public static Value<Boolean> REQUIRE_PROXY_PROTOCOL;
    public static Value<Boolean> LOG_CONNECTIONS;
    private static Supplier<List<String>> trustedProxiesSupplier;

    // Parsed CIDRs, rebuilt only when the raw list changes.
    private static volatile List<String> cachedRaw;
    private static volatile List<CidrRange> cachedParsed = List.of();

    public static void init(
            final Value<Boolean> enabled,
            final Value<Boolean> requireProxyProtocol,
            final Value<Boolean> logConnections,
            final Supplier<List<String>> trustedProxies) {
        ENABLED = enabled;
        REQUIRE_PROXY_PROTOCOL = requireProxyProtocol;
        LOG_CONNECTIONS = logConnections;
        trustedProxiesSupplier = trustedProxies;
    }

    public static List<CidrRange> trustedProxies() {
        final List<String> raw = new ArrayList<>(trustedProxiesSupplier.get());
        if (raw.equals(cachedRaw)) {
            return cachedParsed;
        }
        final List<CidrRange> parsed = new ArrayList<>(raw.size());
        for (final String entry : raw) {
            try {
                parsed.add(CidrRange.parse(entry));
            } catch (final IllegalArgumentException e) {
                LOGGER.warn("Ignoring invalid trusted_proxies entry '{}': {}", entry, e.getMessage());
            }
        }
        cachedRaw = raw;
        cachedParsed = List.copyOf(parsed);
        return cachedParsed;
    }

    public static boolean isTrusted(final InetAddress address) {
        if (address == null) {
            return false;
        }
        for (final CidrRange range : trustedProxies()) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private HAProxyCompatConfig() {}
}
