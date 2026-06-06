package xyz.atmerek.haproxycompat;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

import java.util.List;

// Entrypoint. Dedicated-server only; the actual work is done by the mixins.
@Mod(value = HAProxyCompatMod.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class HAProxyCompatMod {

    public static final String MOD_ID = "haproxycompat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HAProxyCompatMod(final IEventBus modEventBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, HAProxyCompatConfig.SPEC);
        modEventBus.addListener(this::onCommonSetup);
        LOGGER.info("HAProxyCompat loaded (dedicated server). PROXY protocol v1/v2 support is ready.");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        if (!HAProxyCompatConfig.ENABLED.get()) {
            LOGGER.info("HAProxyCompat is disabled (enabled=false); PROXY headers will not be processed.");
            return;
        }

        // Parse now so invalid trusted_proxies entries are reported at startup.
        final List<CidrRange> trusted = HAProxyCompatConfig.trustedProxies();
        if (trusted.isEmpty()) {
            LOGGER.warn("HAProxyCompat: 'trusted_proxies' is empty — every PROXY header will be rejected "
                    + "and no real client IPs will be applied. Add your reverse proxy's IP/CIDR to the config.");
        } else {
            LOGGER.info("HAProxyCompat active: {} trusted proxy range(s), require_proxy_protocol={}, log_connections={}.",
                    trusted.size(),
                    HAProxyCompatConfig.REQUIRE_PROXY_PROTOCOL.get(),
                    HAProxyCompatConfig.LOG_CONNECTIONS.get());
        }
    }
}
