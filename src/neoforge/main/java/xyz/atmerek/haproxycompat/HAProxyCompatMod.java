package xyz.atmerek.haproxycompat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;

// Entrypoint. Dedicated-server only; the actual work is done by the mixins.
@Mod(value = HAProxyCompatMod.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class HAProxyCompatMod {

    public static final String MOD_ID = "haproxycompat";

    public HAProxyCompatMod(final IEventBus modEventBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SPEC);
        HAProxyCompatConfig.init(
                NeoForgeConfig.ENABLED::get,
                NeoForgeConfig.REQUIRE_PROXY_PROTOCOL::get,
                NeoForgeConfig.LOG_CONNECTIONS::get,
                () -> new ArrayList<>(NeoForgeConfig.TRUSTED_PROXIES.get())
        );
        modEventBus.addListener(this::onCommonSetup);
        HAProxyCompatConfig.LOGGER.info("HAProxyCompat loaded (dedicated server). PROXY protocol v1/v2 support is ready.");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        if (!HAProxyCompatConfig.ENABLED.get()) {
            HAProxyCompatConfig.LOGGER.info("HAProxyCompat is disabled (enabled=false); PROXY headers will not be processed.");
            return;
        }

        // Parse now so invalid trusted_proxies entries are reported at startup.
        final List<CidrRange> trusted = HAProxyCompatConfig.trustedProxies();
        if (trusted.isEmpty()) {
            HAProxyCompatConfig.LOGGER.warn("HAProxyCompat: 'trusted_proxies' is empty — every PROXY header will be rejected "
                    + "and no real client IPs will be applied. Add your reverse proxy's IP/CIDR to the config.");
        } else {
            HAProxyCompatConfig.LOGGER.info("HAProxyCompat active: {} trusted proxy range(s), require_proxy_protocol={}, log_connections={}.",
                    trusted.size(),
                    HAProxyCompatConfig.REQUIRE_PROXY_PROTOCOL.get(),
                    HAProxyCompatConfig.LOG_CONNECTIONS.get());
        }
    }
}
