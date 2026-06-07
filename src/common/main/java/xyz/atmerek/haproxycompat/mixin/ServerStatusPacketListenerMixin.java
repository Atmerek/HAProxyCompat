package xyz.atmerek.haproxycompat.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.atmerek.haproxycompat.HAProxyConnectionBridge;
import xyz.atmerek.haproxycompat.ProxyProtocolDetector;

import java.util.Optional;

// For direct connections on a proxy-only server, replaces the normal status response with one that
// shows a red X (protocol -1 never matches any client) and the kick message as the MOTD.
@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class ServerStatusPacketListenerMixin {

    @Shadow @Final private Connection connection;

    @Inject(method = "handleStatusRequest", at = @At("HEAD"), cancellable = true)
    private void haproxycompat$rejectStatus(final ServerboundStatusRequestPacket pkt, final CallbackInfo ci) {
        final String reason = ((HAProxyConnectionBridge) connection).haproxycompat$channel()
                .attr(ProxyProtocolDetector.KICK_REASON).get();
        if (reason == null) return;

        final ServerStatus status = new ServerStatus(
                Component.literal(reason),
                Optional.empty(),
                Optional.of(new ServerStatus.Version("Proxy required", -1)),
                Optional.empty(),
                false
        );
        connection.send(new ClientboundStatusResponsePacket(status));
        ci.cancel();
    }
}
