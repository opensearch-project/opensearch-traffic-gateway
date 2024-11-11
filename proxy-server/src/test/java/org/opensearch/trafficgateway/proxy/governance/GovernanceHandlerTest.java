package org.opensearch.trafficgateway.proxy.governance;

import static org.mockito.Mockito.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.trafficgateway.proxy.UnitTestBase;

@ExtendWith(MockitoExtension.class)
public class GovernanceHandlerTest extends UnitTestBase {

    private ChannelHandlerContext ctx;

    private Channel channel;

    private ChannelPipeline pipeline;

    private EmbeddedChannel requestEncoder;
    private EmbeddedChannel chunkedDecoder;

    @BeforeEach
    public void initTest() {
        ctx = mock(ChannelHandlerContext.class);
        channel = mock(Channel.class);
        pipeline = mock(ChannelPipeline.class);
        requestEncoder = new EmbeddedChannel(new HttpRequestEncoder());
        chunkedDecoder = new EmbeddedChannel(new HttpRequestDecoder(4096, 8192, 4));

        when(ctx.pipeline()).thenReturn(pipeline);
    }

    @AfterEach
    void cleanupTest() {
        this.requestEncoder.close();
        this.chunkedDecoder.close();
    }

    @Test
    void testValidBypassKeyWithInvalidQueryHandlesMultiPartRequests() {
        // given
        ArgumentCaptor<Object> readCaptor = ArgumentCaptor.forClass(Object.class);
        when(ctx.fireChannelRead(readCaptor.capture())).thenReturn(null);
        when(ctx.channel()).thenReturn(channel);

        String content = "{\"query\":{\"prefix\": {\"speaker\": 9}}, \"bypassKey\": \"correctBypassKey\"}";
        ByteBuf byteContent = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/.opendistro_security/_search", byteContent);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, byteContent.readableBytes());

        requestEncoder.writeOutbound(request);
        Object requestBuf = requestEncoder.readOutbound();
        chunkedDecoder.writeInbound(requestBuf);

        for (Object decodedMsg = chunkedDecoder.readInbound();
                decodedMsg != null;
                decodedMsg = chunkedDecoder.readInbound()) {
            requestEncoder.writeOutbound(decodedMsg);
        }

        RejectSearchRegexFieldRule testRule = new RejectSearchRegexFieldRule(
                "query.prefix.speaker", "[0-9]", "^\\.opendistro_security$", "The custom response.");
        GovernanceHandler governanceHandler = new GovernanceHandler("correctBypassKey", false, testRule);

        // when
        try {
            for (Object encodedMsg = requestEncoder.readOutbound();
                    encodedMsg != null;
                    encodedMsg = requestEncoder.readOutbound()) {
                governanceHandler.channelRead(this.ctx, encodedMsg);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // then
        verify(ctx, times(1)).fireChannelRead(readCaptor.capture());

        ByteBuf capturedValue = (ByteBuf) readCaptor.getValue();
        String[] splitCapturedValue = capturedValue.toString(CharsetUtil.UTF_8).split("\n");
        Assertions.assertNotNull(splitCapturedValue[3]);
        Assertions.assertEquals(splitCapturedValue[3], "{\"query\":{\"prefix\":{\"speaker\":9}}}");
    }

    @Test
    void testInvalidBypassKeyWithInvalidQueryHandlesMultiPartRequests() {
        // given
        ArgumentCaptor<Object> ctxPipelineWriteCaptor = ArgumentCaptor.forClass(Object.class);
        when(ctx.pipeline().write(ctxPipelineWriteCaptor.capture())).thenReturn(null);
        when(ctx.channel()).thenReturn(channel);

        String content = "{\"query\":{\"prefix\": {\"speaker\": 9}}, \"bypassKey\": \"wrongBypassKey\"}";
        ByteBuf byteContent = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/.opendistro_security/_search", byteContent);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, byteContent.readableBytes());

        requestEncoder.writeOutbound(request);
        Object requestBuf = requestEncoder.readOutbound();
        chunkedDecoder.writeInbound(requestBuf);

        for (Object decodedMsg = chunkedDecoder.readInbound();
                decodedMsg != null;
                decodedMsg = chunkedDecoder.readInbound()) {
            requestEncoder.writeOutbound(decodedMsg);
        }

        RejectSearchRegexFieldRule testRule = new RejectSearchRegexFieldRule(
                "query.prefix.speaker", "[0-9]", "^\\.opendistro_security$", "The custom response.");
        GovernanceHandler governanceHandler = new GovernanceHandler("correctBypassKey", false, testRule);

        // when
        try {
            for (Object encodedMsg = requestEncoder.readOutbound();
                    encodedMsg != null;
                    encodedMsg = requestEncoder.readOutbound()) {
                governanceHandler.channelRead(this.ctx, encodedMsg);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // then
        verify(ctx.pipeline(), times(1)).write(ctxPipelineWriteCaptor.capture());

        ByteBuf capturedValue = (ByteBuf) ctxPipelineWriteCaptor.getValue();
        String[] splitCapturedValue = capturedValue.toString(CharsetUtil.UTF_8).split("\n");
        Assertions.assertNotNull(splitCapturedValue[0]);
        String[] httpStatusSplit = splitCapturedValue[0].split(" ");
        Assertions.assertNotNull(httpStatusSplit[0]);

        Assertions.assertEquals(httpStatusSplit[1], "400");
    }

    @Test
    void testThatNonJSONBodyStillHitsRules() throws Exception {
        // given
        String content = "SAMLResponse=somerandomcharacters";
        ByteBuf byteContent = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/_dashboards/_opendistro/_security/saml/acs", byteContent);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, byteContent.readableBytes());

        requestEncoder.writeOutbound(request);
        Object requestBuf = requestEncoder.readOutbound();

        GovernanceRule testRule = mock(GovernanceRule.class);
        when(testRule.evaluate(any())).thenReturn(GovernanceRule.PASS);
        GovernanceHandler governanceHandler = new GovernanceHandler("correctBypassKey", false, testRule);

        // when
        governanceHandler.channelRead(this.ctx, requestBuf);

        // then
        verify(testRule, times(1)).evaluate(any());
    }
}
