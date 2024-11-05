package software.amazon.opensearch.proxy.offload;

import static io.netty.buffer.Unpooled.copiedBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.util.CharsetUtil;
import java.util.List;

public class TruncatingHttpObjectAggregator extends HttpObjectAggregator {
    private static final ByteBuf TRUNCATED_TAG = copiedBuffer("...<TRUNCATED>", CharsetUtil.UTF_8);

    public TruncatingHttpObjectAggregator(int maxContentLength) {
        super(maxContentLength, false);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        // truncate content otherwise decoder will not read it.
        if (msg instanceof HttpContent) {
            HttpContent contentMsg = (HttpContent) msg;
            if (contentMsg.content().readableBytes() > maxContentLength()) {
                msg = contentMsg.replace(
                        contentMsg.content().copy(0, maxContentLength() - TRUNCATED_TAG.readableBytes()));
                ((HttpContent) msg).content().writeBytes(TRUNCATED_TAG, 0, TRUNCATED_TAG.readableBytes());
            }
        }

        super.decode(ctx, msg, out);
    }

    @Override
    protected boolean isContentLengthInvalid(HttpMessage start, int maxContentLength) {
        // checked on initial message. Override this so that we still read partial
        // content.
        return false;
    }

    @Override
    protected void handleOversizedMessage(ChannelHandlerContext ctx, HttpMessage oversized) throws Exception {
        FullHttpMessage truncatedMessage;
        if (oversized instanceof FullHttpMessage) {
            FullHttpMessage originalMessage = (FullHttpMessage) oversized;
            truncatedMessage = originalMessage.replace(originalMessage
                    .content()
                    .copy(0, maxContentLength() - TRUNCATED_TAG.readableBytes())
                    .writeBytes(TRUNCATED_TAG, 0, TRUNCATED_TAG.readableBytes()));
        } else {
            ByteBuf truncatedContent;
            if (oversized instanceof ByteBufHolder) {
                ByteBuf content = ((ByteBufHolder) oversized).content();
                truncatedContent = content.copy(0, maxContentLength() - TRUNCATED_TAG.readableBytes())
                        .writeBytes(TRUNCATED_TAG, 0, TRUNCATED_TAG.readableBytes());
            } else {
                truncatedContent = Unpooled.EMPTY_BUFFER;
            }

            truncatedMessage = beginAggregation(oversized, truncatedContent);
        }

        finishAggregation(truncatedMessage);

        ctx.fireChannelRead(truncatedMessage);

        // super.handleOversizedMessage(ctx, oversized);
    }
}
