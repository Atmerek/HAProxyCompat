package xyz.atmerek.haproxycompat.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import xyz.atmerek.haproxycompat.HAProxyConnectionBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.net.SocketAddress;

// Exposes address/channel access on Connection. getRemoteAddress() and getLoggableAddress() both
// read the address field, so overwriting it fixes the IP everywhere (login log, ban checks, ...).
@Mixin(Connection.class)
public abstract class ConnectionMixin implements HAProxyConnectionBridge {

    @Shadow
    private SocketAddress address;

    @Shadow
    private Channel channel;

    @Override
    public void haproxycompat$setRealAddress(final SocketAddress realAddress) {
        this.address = realAddress;
    }

    @Override
    public Channel haproxycompat$channel() {
        return this.channel;
    }
}
