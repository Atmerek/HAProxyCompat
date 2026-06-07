package xyz.atmerek.haproxycompat;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.util.AttributeKey;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

// First handler in the pipeline. Detects a PROXY header on the first bytes, checks the peer is a
// trusted proxy, then either decodes it, drops the connection, or passes through to vanilla.
// When require_proxy_protocol is true and no header is present, marks the channel with KICK_REASON
// so ServerLoginPacketListenerMixin can send LoginDisconnect via Minecraft's own mechanism.
public final class ProxyProtocolDetector extends ByteToMessageDecoder {

    public static final AttributeKey<String> KICK_REASON = AttributeKey.valueOf("haproxycompat:kick_reason");

    static final String DECODER_NAME = "haproxycompat:haproxy_decoder";
    static final String APPLIER_NAME = "haproxycompat:proxy_apply";

    private static final byte[] V1_SIGNATURE = {'P', 'R', 'O', 'X', 'Y'};
    private static final byte[] V2_SIGNATURE = {
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    private enum Detection { PRESENT, ABSENT, NEED_MORE }

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
        final Detection detection = detect(in);
        if (detection == Detection.NEED_MORE) {
            return;
        }

        final boolean requireProxy = HAProxyCompatConfig.REQUIRE_PROXY_PROTOCOL.get();
        final boolean log = HAProxyCompatConfig.LOG_CONNECTIONS.get();
        final SocketAddress peer = ctx.channel().remoteAddress();

        if (detection == Detection.PRESENT) {
            if (!isTrusted(peer)) {
                if (log) {
                    HAProxyCompatConfig.LOGGER.warn("Rejecting PROXY header from untrusted source {}", peer);
                }
                drop(ctx, in);
                return;
            }
            // Decode with Netty's parser, then apply the IP. Unconsumed bytes flow to the decoder
            // when this handler removes itself.
            ctx.pipeline().addAfter(ctx.name(), DECODER_NAME, new HAProxyMessageDecoder());
            ctx.pipeline().addAfter(DECODER_NAME, APPLIER_NAME, new ProxyProtocolHandler(log));
            ctx.pipeline().remove(this);
            return;
        }

        // No header.
        if (requireProxy) {
            if (log) {
                HAProxyCompatConfig.LOGGER.warn(
                        "Rejecting connection from {} (no PROXY header; require_proxy_protocol=true)", peer);
            }
            // Mark the channel so the login mixin can kick with the configured message. Then pass
            // through: status pings reach vanilla's status handler, login triggers the mixin.
            ctx.channel().attr(KICK_REASON).set(HAProxyCompatConfig.KICK_MESSAGE.get());
            ctx.pipeline().remove(this);
            return;
        }
        if (log) {
            HAProxyCompatConfig.LOGGER.info("Direct connection from {} (no PROXY header)", peer);
        }
        ctx.pipeline().remove(this);
    }

    private void drop(final ChannelHandlerContext ctx, final ByteBuf in) {
        in.skipBytes(in.readableBytes());
        ctx.close();
    }

    private static boolean isTrusted(final SocketAddress peer) {
        if (peer instanceof InetSocketAddress isa) {
            final InetAddress addr = isa.getAddress();
            return addr != null && HAProxyCompatConfig.isTrusted(addr);
        }
        return false;
    }

    private static Detection detect(final ByteBuf in) {
        if (in.readableBytes() == 0) {
            return Detection.NEED_MORE;
        }
        final int first = in.getByte(in.readerIndex()) & 0xFF;
        if (first == (V1_SIGNATURE[0] & 0xFF)) {
            return matchPrefix(in, V1_SIGNATURE);
        }
        if (first == (V2_SIGNATURE[0] & 0xFF)) {
            return matchPrefix(in, V2_SIGNATURE);
        }
        return Detection.ABSENT;
    }

    private static Detection matchPrefix(final ByteBuf in, final byte[] signature) {
        final int readable = in.readableBytes();
        final int base = in.readerIndex();
        final int compare = Math.min(readable, signature.length);
        for (int i = 0; i < compare; i++) {
            if (in.getByte(base + i) != signature[i]) {
                return Detection.ABSENT;
            }
        }
        return readable >= signature.length ? Detection.PRESENT : Detection.NEED_MORE;
    }
}
