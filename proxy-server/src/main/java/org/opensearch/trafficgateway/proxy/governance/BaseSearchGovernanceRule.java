package org.opensearch.trafficgateway.proxy.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;

/*
 * TODO: javadoc.
 */
@Getter
@EqualsAndHashCode(callSuper = false)
@ToString
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public abstract class BaseSearchGovernanceRule implements GovernanceRule {
    private static final String URI_SEARCH_KEYWORD = "_search";
    private static final String WILDCARD = "*";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @NonNull
    Pattern indexRegexPattern;

    public BaseSearchGovernanceRule(@NonNull String indexRegex) {
        this.indexRegexPattern = Pattern.compile(indexRegex);
    }

    protected boolean isGetOrPost(FullHttpRequest request) {
        HttpMethod method = request.method();
        return method.equals(HttpMethod.POST) || method.equals(HttpMethod.GET);
    }

    protected ParsedSearchRequest tryParseSearchRequest(FullHttpRequest request) {
        if (!isGetOrPost(request)) {
            return null;
        }

        JsonNode searchBody;
        String index;
        try {
            String path = getPathOrNull(request);
            if (path == null) {
                return null;
            }
            if (path.startsWith("/_dashboards/internal/search")) {
                String requestBody = request.content().toString(CharsetUtil.UTF_8);
                if (requestBody.isBlank()) {
                    return null;
                }
                JsonNode body = MAPPER.readTree(requestBody);

                searchBody = body.get("params").get("body");
                index = body.get("params").get("index").asText();
            } else {
                String[] pathComponents = path.split("/");

                pathComponents =
                        Arrays.stream(pathComponents).filter(e -> !e.isEmpty()).toArray(String[]::new);

                if (pathComponents.length == 1 && pathComponents[0].equals(URI_SEARCH_KEYWORD)) {
                    index = WILDCARD;
                } else if (pathComponents.length >= 2 && pathComponents[1].equals(URI_SEARCH_KEYWORD)) {
                    index = pathComponents[0];
                } else {
                    return null;
                }

                String requestBody = request.content().toString(CharsetUtil.UTF_8);
                if (requestBody.isBlank()) {
                    return null;
                }
                searchBody = MAPPER.readTree(requestBody);
            }
        } catch (Exception e) {
            return null;
        }

        return new ParsedSearchRequest(index, searchBody);
    }

    protected boolean requestMatchesIndex(ParsedSearchRequest parsedRequest) {
        if (parsedRequest == null) {
            return false;
        }

        return indexRegexPattern.matcher(parsedRequest.getIndex()).matches();
    }

    private String getPathOrNull(FullHttpRequest request) throws URISyntaxException {
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
        String path = queryStringDecoder.path();

        if (!path.equals("/_dashboards/api/console/proxy")) {
            return path;
        }

        List<String> pathParam = queryStringDecoder.parameters().get("path");

        if (pathParam == null || pathParam.size() != 1) {
            return null;
        }

        return pathParam.get(0);
    }

    @Value
    protected static class ParsedSearchRequest {
        String index;
        JsonNode searchBody;
    }
}
