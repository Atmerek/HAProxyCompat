package xyz.atmerek.haproxycompat;

import java.net.SocketAddress;

// Implemented on Connection by ConnectionMixin so the handler can set the real address.
public interface HAProxyConnectionBridge {

    void haproxycompat$setRealAddress(SocketAddress realAddress);
}
