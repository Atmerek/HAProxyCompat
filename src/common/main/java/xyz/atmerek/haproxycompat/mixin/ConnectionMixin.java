package xyz.atmerek.haproxycompat.mixin;

import net.minecraft.network.Connection;
import xyz.atmerek.haproxycompat.HAProxyConnectionBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.net.SocketAddress;

// Exposes a setter on Connection's address field. getRemoteAddress() and getLoggableAddress() both
// read it, so overwriting it fixes the IP everywhere (login log, ban checks, ...). Done at decode
// time rather than channelActive, which runs before the PROXY header is read.
@Mixin(Connection.class)
public abstract class ConnectionMixin implements HAProxyConnectionBridge {

    @Shadow
    private SocketAddress address;

    @Override
    public void haproxycompat$setRealAddress(final SocketAddress realAddress) {
        this.address = realAddress;
    }
}
