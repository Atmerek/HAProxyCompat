package xyz.atmerek.haproxycompat;

import net.fabricmc.api.DedicatedServerModInitializer;

import java.util.List;

// Entrypoint. Declared as "server" in fabric.mod.json — runs only on dedicated servers.
public final class HAProxyCompatMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "haproxycompat";

    @Override
    public void onInitializeServer() {
        final FabricConfig config = FabricConfig.loadOrCreate();
        HAProxyCompatConfig.init(
                () -> config.enabled,
                () -> config.requireProxyProtocol,
                () -> config.logConnections,
                () -> config.trustedProxies,
                () -> config.kickMessage
        );

        HAProxyCompatConfig.LOGGER.info("HAProxyCompat loaded (dedicated server). PROXY protocol v1/v2 support is ready.");

        if (!config.enabled) {
            HAProxyCompatConfig.LOGGER.info("HAProxyCompat is disabled (enabled=false); PROXY headers will not be processed.");
            return;
        }
        final List<CidrRange> trusted = HAProxyCompatConfig.trustedProxies();
        if (trusted.isEmpty()) {
            HAProxyCompatConfig.LOGGER.warn("HAProxyCompat: 'trusted_proxies' is empty — every PROXY header will be rejected "
                    + "and no real client IPs will be applied. Add your reverse proxy's IP/CIDR to the config.");
        } else {
            HAProxyCompatConfig.LOGGER.info("HAProxyCompat active: {} trusted proxy range(s), require_proxy_protocol={}, log_connections={}.",
                    trusted.size(), config.requireProxyProtocol, config.logConnections);
        }
    }
}
