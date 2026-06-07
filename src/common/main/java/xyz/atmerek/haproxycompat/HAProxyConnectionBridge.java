package xyz.atmerek.haproxycompat;

import io.netty.channel.Channel;

import java.net.SocketAddress;

// Implemented on Connection by ConnectionMixin so the handler can read/write connection metadata.
public interface HAProxyConnectionBridge {

    void haproxycompat$setRealAddress(SocketAddress realAddress);

    Channel haproxycompat$channel();
}
