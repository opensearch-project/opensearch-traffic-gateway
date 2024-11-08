package org.opensearch.trafficgateway.proxy.offload;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.opensearch.migrations.trafficcapture.IChannelConnectionCaptureSerializer;
import org.opensearch.trafficgateway.proxy.util.UserIdExtractor;

@Log4j2
public class TrafficAggregatingLogOffloader implements IChannelConnectionCaptureSerializer<Void> {
    public static final int DEFAULT_MAX_CONTENT_LENGTH = 209715200; // 200 MB
    public static final Marker CAPTURED_TRAFFIC_MARKER = MarkerManager
            .getMarker("org.opensearch.trafficgateway.proxy.offload.CAPTURED_TRAFFIC");

    private static final SerializableHttpMessageFactory DEfAULT_MESSAGE_FACTORY_INSTANCE = new SerializableHttpMessageFactory(
            false, UserIdExtractor.DEFAULT_SAML_USER_ID_XPATH, UserIdExtractor.DEFAULT_SAML_TOKEN_COOKIE_NAME);

    private final EmbeddedChannel requestProcessingChannel;
    private final EmbeddedChannel responseProcessingChannel;
    private final SerializableHttpMessageFactory messageFactory;

    private Instant currentRequestTimestamp = null;
    private Instant currentResponseTimestamp = null;
    private String currentRequestId;

    public TrafficAggregatingLogOffloader() {
        this(DEFAULT_MAX_CONTENT_LENGTH, DEfAULT_MESSAGE_FACTORY_INSTANCE);
    }

    public TrafficAggregatingLogOffloader(int maxContentLength, SerializableHttpMessageFactory messageFactory) {
        requestProcessingChannel = new EmbeddedChannel(
                new HttpRequestDecoder(), new TruncatingHttpObjectAggregator(maxContentLength), new RequestLogger());
        responseProcessingChannel = new EmbeddedChannel(
                new HttpResponseDecoder(), new TruncatingHttpObjectAggregator(maxContentLength), new ResponseLogger());
        this.messageFactory = messageFactory;
    }

    class RequestLogger extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            currentRequestId = UUID.randomUUID().toString();

            SerializableHttpMessage request = messageFactory.serializeRequest(currentRequestId, currentRequestTimestamp,
                    msg);
            log.always().withMarker(CAPTURED_TRAFFIC_MARKER).log(request);

            currentRequestTimestamp = null;
        }
    }

    class ResponseLogger extends SimpleChannelInboundHandler<FullHttpResponse> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
            if (currentRequestId == null) {
                // Just in case. RequestId is used as OpenSearch DocumentId so can't be null.
                currentRequestId = UUID.randomUUID().toString();
            }

            SerializableHttpMessage response = messageFactory.serializeResponse(currentRequestId,
                    currentResponseTimestamp, msg);
            log.always().withMarker(CAPTURED_TRAFFIC_MARKER).log(response);
            currentResponseTimestamp = null;
        }
    }

    @Override
    public void addReadEvent(Instant timestamp, ByteBuf buffer) throws IOException {
        log.debug("Received read event in traffic offloader.");
        if (currentRequestTimestamp == null) {
            currentRequestTimestamp = timestamp;
        }
        requestProcessingChannel.writeInbound(buffer.retainedDuplicate());
        requestProcessingChannel.releaseInbound();
        requestProcessingChannel.releaseOutbound();
    }

    @Override
    public void addWriteEvent(Instant timestamp, ByteBuf buffer) throws IOException {
        log.debug("Received write event in traffic offloader.");
        if (currentResponseTimestamp == null) {
            currentResponseTimestamp = timestamp;
        }
        responseProcessingChannel.writeInbound(buffer.retainedDuplicate());
        responseProcessingChannel.releaseInbound();
        responseProcessingChannel.releaseOutbound();
    }
}
