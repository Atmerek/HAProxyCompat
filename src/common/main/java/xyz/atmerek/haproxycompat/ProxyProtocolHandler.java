package xyz.atmerek.haproxycompat;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.Map;

// Takes the decoded HAProxyMessage, pulls out the real client address, and writes it onto the Connection.
// The message is consumed here, not forwarded; later traffic passes through.
public final class ProxyProtocolHandler extends ChannelInboundHandlerAdapter {

    // Real client address resolved from the PROXY header.
    public static final AttributeKey<InetSocketAddress> REAL_ADDRESS =
            AttributeKey.valueOf("haproxycompat:real_address");

    private final boolean logConnections;

    public ProxyProtocolHandler(final boolean logConnections) {
        this.logConnections = logConnections;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HAProxyMessage proxy) {
            try {
                final String source = proxy.sourceAddress();
                if (proxy.command() == HAProxyCommand.LOCAL || source == null) {
                    // Health check / no client address: leave the connection as-is.
                    if (logConnections) {
                        HAProxyCompatConfig.LOGGER.info(
                                "PROXY LOCAL/health-check from {} (no client address applied)",
                                ctx.channel().remoteAddress());
                    }
                } else {
                    final InetSocketAddress realAddress = new InetSocketAddress(source, proxy.sourcePort());
                    ctx.channel().attr(REAL_ADDRESS).set(realAddress);
                    applyToConnection(ctx, realAddress);
                    if (logConnections) {
                        HAProxyCompatConfig.LOGGER.info("PROXY {} -> real client {}",
                                ctx.channel().remoteAddress(), realAddress);
                    }
                }
            } finally {
                proxy.release();
            }
            return;
        }

        super.channelRead(ctx, msg);
    }

    // Find the Connection in the pipeline (by type, not name) and overwrite its stored address.
    private static void applyToConnection(final ChannelHandlerContext ctx, final InetSocketAddress realAddress) {
        for (final Map.Entry<String, ChannelHandler> entry : ctx.pipeline()) {
            if (entry.getValue() instanceof HAProxyConnectionBridge bridge) {
                bridge.haproxycompat$setRealAddress(realAddress);
                return;
            }
        }
    }
}
