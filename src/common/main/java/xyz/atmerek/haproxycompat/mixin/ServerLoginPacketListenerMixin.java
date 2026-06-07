package xyz.atmerek.haproxycompat.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.atmerek.haproxycompat.HAProxyConnectionBridge;
import xyz.atmerek.haproxycompat.ProxyProtocolDetector;

// Kicks clients that connected without a PROXY header on a proxy-only server.
// ProxyProtocolDetector sets KICK_REASON on the channel and passes through to vanilla; this mixin
// checks it at LoginStart and uses Minecraft's own disconnect so the client sees the reason.
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerMixin {

    @Shadow @Final private Connection connection;

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void haproxycompat$checkKick(final ServerboundHelloPacket pkt, final CallbackInfo ci) {
        final String reason = ((HAProxyConnectionBridge) connection).haproxycompat$channel()
                .attr(ProxyProtocolDetector.KICK_REASON).get();
        if (reason != null) {
            disconnect(Component.literal(reason));
            ci.cancel();
        }
    }

    @Shadow
    public abstract void disconnect(Component reason);
}
