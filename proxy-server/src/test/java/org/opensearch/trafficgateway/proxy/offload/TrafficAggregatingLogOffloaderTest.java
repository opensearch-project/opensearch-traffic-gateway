package org.opensearch.trafficgateway.proxy.offload;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.logging.log4j.LogBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.migrations.testutils.WrapWithNettyLeakDetection;
import org.opensearch.trafficgateway.proxy.UnitTestBase;
import org.opensearch.trafficgateway.proxy.util.UserIdExtractor;

@ExtendWith(MockitoExtension.class)
public class TrafficAggregatingLogOffloaderTest extends UnitTestBase {
    private static final Random RANDOM = new Random();
    private static final List<Object> capturedTrafficLogs = new ArrayList<>();

    private EmbeddedChannel httpRequestEncoderChannel;
    private EmbeddedChannel httpResponseEncoderChannel;

    @BeforeAll
    static void initClass() {
        MockedStatic<LogManager> mocked = mockStatic(LogManager.class, Answers.CALLS_REAL_METHODS);
        LogBuilder logBuilder = mock(LogBuilder.class);
        Logger mockLogger = mock(Logger.class);
        mocked.when(() -> LogManager.getLogger(TrafficAggregatingLogOffloader.class))
                .thenReturn(mockLogger);
        when(mockLogger.always()).thenReturn(logBuilder);
        when(logBuilder.withMarker(TrafficAggregatingLogOffloader.CAPTURED_TRAFFIC_MARKER))
                .thenReturn(logBuilder);
        doAnswer(invocation -> capturedTrafficLogs.add(invocation.getArgument(0)))
                .when(logBuilder)
                .log(any(Object.class));
    }

    @BeforeEach
    void initTest() {
        // initialize encoder channels
        httpRequestEncoderChannel = new EmbeddedChannel(new HttpRequestEncoder());
        httpResponseEncoderChannel = new EmbeddedChannel(new HttpResponseEncoder());

        // cleanup logged messages from previous test
        capturedTrafficLogs.clear();
    }

    @AfterEach
    void cleanupTest() {
        // close embedded channel
        httpRequestEncoderChannel.close();
        httpResponseEncoderChannel.close();
    }

    @Test
    void testThatAddReadEventWithFullHttpMessageLogsMessageWithMarker() throws IOException {
        // given
        TrafficAggregatingLogOffloader offloader = new TrafficAggregatingLogOffloader();
        String requestBody = "Hello World!";
        ByteBuf requestContent = copiedBuffer(requestBody, CharsetUtil.UTF_8);
        FullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/test", requestContent);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, requestContent.readableBytes());
        httpRequestEncoderChannel.writeOutbound(request);
        ByteBuf requestByteBuf = httpRequestEncoderChannel.readOutbound();

        // when
        offloader.addReadEvent(Instant.ofEpochMilli(RANDOM.nextLong()), requestByteBuf);

        // then
        assertThat(capturedTrafficLogs).hasSize(1);

        Object capturedTrafficLog = capturedTrafficLogs.get(0);
        assertThat(capturedTrafficLog).isInstanceOf(SerializableHttpMessage.class);

        SerializableHttpMessage message = (SerializableHttpMessage) capturedTrafficLog;
        assertThat(message).matches(m -> m.getBody().equals(requestBody));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testThatAddWriteEventWithFullHttpMessageLogsMessageWithMarker(boolean keepResponseBody) throws IOException {
        // given
        SerializableHttpMessageFactory messageFactory = new SerializableHttpMessageFactory(
                keepResponseBody,
                UserIdExtractor.DEFAULT_SAML_USER_ID_XPATH,
                UserIdExtractor.DEFAULT_SAML_TOKEN_COOKIE_NAME);
        TrafficAggregatingLogOffloader offloader = new TrafficAggregatingLogOffloader(
                TrafficAggregatingLogOffloader.DEFAULT_MAX_CONTENT_LENGTH, messageFactory);
        String responseBody = "Hello World!";
        ByteBuf responseContent = copiedBuffer(responseBody, CharsetUtil.UTF_8);
        FullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseContent);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseContent.readableBytes());
        httpResponseEncoderChannel.writeOutbound(response);
        ByteBuf responseByteBuf = httpResponseEncoderChannel.readOutbound();

        // when
        offloader.addWriteEvent(Instant.ofEpochMilli(RANDOM.nextLong()), responseByteBuf);

        // then
        assertThat(capturedTrafficLogs).hasSize(1);

        Object capturedTrafficLog = capturedTrafficLogs.get(0);
        assertThat(capturedTrafficLog).isInstanceOf(SerializableHttpMessage.class);

        SerializableHttpMessage message = (SerializableHttpMessage) capturedTrafficLog;
        assertThat(message).matches(m -> keepResponseBody ? m.getBody().equals(responseBody) : (m.getBody() == null));
    }

    @Test
    @WrapWithNettyLeakDetection(repetitions = 32)
    void testThatAddReadEventWithFullHttpMessageDoesNotLeak() throws IOException {
        testThatAddReadEventWithFullHttpMessageLogsMessageWithMarker();
        capturedTrafficLogs.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WrapWithNettyLeakDetection(repetitions = 32)
    void testThatAddWriteEventWithFullHttpMessageDoesNotLeak(boolean keepResponseBody) throws IOException {
        testThatAddWriteEventWithFullHttpMessageLogsMessageWithMarker(keepResponseBody);
        capturedTrafficLogs.clear();
    }
}
