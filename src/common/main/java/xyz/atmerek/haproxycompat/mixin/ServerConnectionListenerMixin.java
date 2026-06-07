package xyz.atmerek.haproxycompat.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.local.LocalServerChannel;
import xyz.atmerek.haproxycompat.HAProxyCompatConfig;
import xyz.atmerek.haproxycompat.ProxyProtocolDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Prepends our detector to each connection's pipeline. The TCP pipeline is built in the anonymous
// ChannelInitializer inside startTcpServerListener, compiled to ServerConnectionListener$1.
@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public class ServerConnectionListenerMixin {

    @Inject(method = "initChannel(Lio/netty/channel/Channel;)V", at = @At("RETURN"))
    private void haproxycompat$injectProxyProtocol(final Channel channel, final CallbackInfo ci) {
        if (!HAProxyCompatConfig.ENABLED.get()) {
            return;
        }
        // LAN / integrated server connections have no real IP behind a proxy.
        if (channel instanceof LocalServerChannel) {
            return;
        }
        final ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(ProxyProtocolDetector.class) != null) {
            return;
        }
        // addFirst: ahead of every vanilla handler, including "timeout".
        pipeline.addFirst("haproxycompat:detector", new ProxyProtocolDetector());
    }
}
