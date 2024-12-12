package org.opensearch.trafficgateway.proxy.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RejectSearchQueryDenyListRule extends BaseSearchGovernanceRule {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @NonNull
    private JsonNode denyListArray;

    String responseMessage;

    public RejectSearchQueryDenyListRule(@NonNull String indexRegex, @NonNull String queryStructure) {
        this(indexRegex, queryStructure, null);
    }

    @SneakyThrows
    public RejectSearchQueryDenyListRule(
            @NonNull String indexRegex, @NonNull String queryStructure, String responseMessage) {
        super(indexRegex);
        this.denyListArray = JSON_MAPPER.readTree(queryStructure);
        if (responseMessage != null) {
            this.responseMessage = responseMessage;
        } else {
            this.responseMessage = "Query matches one of the deny-listed structures: '" + queryStructure + "'";
        }
    }

    @Override
    public GovernanceRuleResult evaluate(FullHttpRequest request) {
        ParsedSearchRequest searchRequest = tryParseSearchRequest(request);

        if (searchRequest == null || !requestMatchesIndex(searchRequest)) {
            return getPassResult();
        }

        if (requestQueryMatchesQueryStructure(searchRequest.getSearchBody())) {
            return getRejectResultWithMessage(getResponseMessage());
        }

        return getPassResult();
    }

    private boolean requestQueryMatchesQueryStructure(JsonNode requestBody) {
        return compareJson(requestBody, getDenyListArray());
    }

    // Flatten the request and deny list json from FullHttpRequest and
    // governance-config.json.
    // Using "." for maps, ":" for array items and "=" for primitive assignment.
    // We don't need to do a check if the de-limiter is inside the key or value
    // since we're not going to actually use it further. i.e. split on it or
    // something
    // We're just comparing the flattened json's themselves so as long as the
    // de-limiters are consistent
    // for both the request body and deny structure then we can just do a direct
    // comparison on the String Sets.
    // TODO: Use JMES for this.
    private boolean compareJson(JsonNode testJsonElement, JsonNode denyListJsonArray) {
        Set<String> testElements = new HashSet<>();

        try {
            buildPaths(testJsonElement, new StringBuilder(), testElements);

            for (JsonNode denyJson : denyListJsonArray) {
                Set<String> denyElements = new HashSet<>();
                buildPaths(denyJson, new StringBuilder(), denyElements);
                if (testElements.equals(denyElements)) {
                    return true;
                }
            }
        } catch (IllegalStateException | IndexOutOfBoundsException e) {
            return false;
        }

        return false;
    }

    private static void buildPaths(JsonNode element, StringBuilder currentPath, Set<String> paths)
            throws IllegalStateException, IndexOutOfBoundsException {
        if (element.isObject()) {
            if (element.isEmpty()) {
                paths.add(currentPath.toString());
            } else {
                for (Map.Entry<String, JsonNode> entry : element.properties()) {
                    int length = currentPath.length();
                    if (length > 0) {
                        currentPath.append(".");
                    }
                    currentPath.append(entry.getKey());
                    buildPaths(entry.getValue(), currentPath, paths);
                    currentPath.setLength(length);
                }
            }
        } else if (element.isArray()) {
            for (int i = 0; i < element.size(); i++) {
                buildPaths(element.get(i), currentPath.append(":"), paths);
                currentPath.setLength(currentPath.length() - 1);
            }
            if (element.isEmpty()) {
                paths.add(currentPath.toString());
            }
        } else if (element.isValueNode()) {
            paths.add(currentPath.toString() + '=' + element.asText());
        } else {
            paths.add(currentPath.toString() + "=null");
        }
    }
}
