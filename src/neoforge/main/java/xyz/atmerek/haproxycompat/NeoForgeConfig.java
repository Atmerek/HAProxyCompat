package xyz.atmerek.haproxycompat;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class NeoForgeConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue REQUIRE_PROXY_PROTOCOL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TRUSTED_PROXIES;
    public static final ModConfigSpec.BooleanValue LOG_CONNECTIONS;
    public static final ModConfigSpec.ConfigValue<String> KICK_MESSAGE;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("HAProxyCompat configuration").push("general");

        ENABLED = builder
                .comment(
                        "Master switch. When true, HAProxyCompat reads HAProxy PROXY protocol headers",
                        "(v1 and v2) from incoming TCP connections and replaces the proxy address with",
                        "the real client IP. Set to false to disable all handler injection.")
                .define("enabled", true);

        REQUIRE_PROXY_PROTOCOL = builder
                .comment(
                        "When true, every connection must present a valid PROXY header from a trusted",
                        "proxy; connections without one are dropped. Use this when the Minecraft port is",
                        "only reachable through your reverse proxy.",
                        "When false, connections without a PROXY header are allowed through as normal",
                        "direct connections (lets you mix proxied and direct traffic on one port).")
                .define("require_proxy_protocol", true);

        TRUSTED_PROXIES = builder
                .comment(
                        "IP addresses / CIDR ranges allowed to send PROXY protocol headers. A PROXY header",
                        "from any other peer is never trusted (prevents IP spoofing and ban evasion when the",
                        "port is directly reachable). Add your reverse proxy's address, e.g. \"10.0.0.5/32\".",
                        "Both IPv4 and IPv6 are supported; a bare IP means a single host. An empty list",
                        "trusts nobody (all PROXY headers rejected).")
                .defineListAllowEmpty(
                        "trusted_proxies",
                        List.of("127.0.0.1/32", "::1/128"),
                        () -> "127.0.0.1/32",
                        NeoForgeConfig::isValidCidr);

        LOG_CONNECTIONS = builder
                .comment(
                        "Log a line for each connection decision (proxied / direct / rejected). Useful for",
                        "diagnosing reverse-proxy configuration, but noisy on busy servers.")
                .define("log_connections", false);

        KICK_MESSAGE = builder
                .comment(
                        "Message shown to clients that connect directly when require_proxy_protocol is true.",
                        "They will see this in the disconnect screen instead of hanging until timeout.")
                .define("kick_message", "This server requires a proxy connection.");

        builder.pop();
        SPEC = builder.build();
    }

    private static boolean isValidCidr(final Object value) {
        if (!(value instanceof String s)) {
            return false;
        }
        try {
            CidrRange.parse(s);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    private NeoForgeConfig() {}
}
