package org.opensearch.trafficgateway.proxy.offload;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.trafficgateway.proxy.offload.SerializableHttpMessage.RequestType;
import org.opensearch.trafficgateway.proxy.util.UserIdExtractor;

public class SerializableHttpMessageFactory {
    private static final Set<AsciiString> HEADERS_TO_REMOVE = Set.of(HttpHeaderNames.AUTHORIZATION,
            HttpHeaderNames.COOKIE, HttpHeaderNames.SET_COOKIE);

    private final boolean keepResponseBody;
    private final UserIdExtractor userIdExtractor;

    public SerializableHttpMessageFactory(
            boolean keepResponseBody, String samlUserIdXPath, String samlTokenCookieName) {
        this.keepResponseBody = keepResponseBody;
        this.userIdExtractor = new UserIdExtractor(samlUserIdXPath, samlTokenCookieName);
    }

    public SerializableHttpMessage serializeRequest(String requestId, Instant timestamp, FullHttpRequest request) {
        HttpHeaders requestHeaders = request.headers();

        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
        String requestBody = request.content().toString(CharsetUtil.UTF_8);

        String userId = userIdExtractor.extractUserId(request);
        String userToken = userIdExtractor.extractUserToken(request);

        List<Map.Entry<String, String>> headerList = getHeadersToCapture(requestHeaders);

        return new SerializableHttpMessage(
                RequestType.REQUEST,
                requestId,
                userId,
                userToken,
                timestamp.toEpochMilli(),
                request.method().toString(),
                queryStringDecoder.path(),
                queryStringDecoder.parameters(),
                headerList,
                null,
                null,
                requestBody);
    }

    public SerializableHttpMessage serializeResponse(String requestId, Instant timestamp, FullHttpResponse response) {
        return new SerializableHttpMessage(
                RequestType.RESPONSE,
                requestId,
                null,
                userIdExtractor.extractUserToken(response),
                timestamp.toEpochMilli(),
                null,
                null,
                null,
                getHeadersToCapture(response.headers()),
                response.status().code(),
                response.status().reasonPhrase(),
                keepResponseBody ? response.content().toString(CharsetUtil.UTF_8) : null);
    }

    List<Map.Entry<String, String>> getHeadersToCapture(HttpHeaders headers) {
        List<Map.Entry<String, String>> headerList = headers.entries();
        headerList.removeIf(h -> {
            for (AsciiString headerToRemove : HEADERS_TO_REMOVE) {
                if (headerToRemove.contentEqualsIgnoreCase(h.getKey())) {
                    return true;
                }
            }

            return false;
        });

        return headerList;
    }
}
