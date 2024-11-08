package org.opensearch.trafficgateway.proxy.governance;

import com.fasterxml.jackson.databind.JsonNode;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.jackson.JacksonRuntime;
import io.netty.handler.codec.http.FullHttpRequest;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RejectSearchRegexFieldRule extends BaseSearchGovernanceRule {
    private static final JmesPath<JsonNode> JMES_RUNTIME = new JacksonRuntime();

    @NonNull
    Expression<JsonNode> fieldPath;

    @NonNull
    Pattern fieldRegexPattern;

    String responseMessage;

    public RejectSearchRegexFieldRule(
            @NonNull String fieldName, @NonNull String fieldRegex, @NonNull String indexRegex) {
        this(fieldName, fieldRegex, indexRegex, null);
    }

    public RejectSearchRegexFieldRule(
            @NonNull String fieldName, @NonNull String fieldRegex, @NonNull String indexRegex, String responseMessage) {
        super(indexRegex);
        this.fieldPath = JMES_RUNTIME.compile(fieldName);
        fieldRegexPattern = Pattern.compile(fieldRegex);
        if (responseMessage != null) {
            this.responseMessage = responseMessage;
        } else {
            this.responseMessage = "Field: '" + fieldName + "' matches regex pattern: '" + fieldRegexPattern.pattern()
                    + "'";
        }
    }

    @Override
    public GovernanceRuleResult evaluate(FullHttpRequest request) {
        ParsedSearchRequest searchRequest = tryParseSearchRequest(request);

        if (searchRequest == null || !requestMatchesIndex(searchRequest)) {
            return getPassResult();
        }

        if (fieldMatchesRegex(searchRequest.getSearchBody())) {
            return getRejectResultWithMessage(getResponseMessage());
        }

        return getPassResult();
    }

    private boolean fieldMatchesRegex(JsonNode requestBody) {
        return getValueOfFieldNameAndMatch(requestBody);
    }

    // Iterate using the fieldName through the JSON tree and stop at the value
    // Then do different matches checks depending on if the value is a primitive,
    // object or another array
    private boolean getValueOfFieldNameAndMatch(JsonNode jsonObject) {
        JsonNode valueOfFieldName = getValueOfFieldName(jsonObject);
        if (valueOfFieldName == null) {
            return false;
        }

        return valueOfFieldNameMatchesFieldRegex(valueOfFieldName);
    }

    private JsonNode getValueOfFieldName(JsonNode element) {
        return getFieldPath().search(element);
    }

    private boolean valueOfFieldNameMatchesFieldRegex(JsonNode currentElement) {
        try {
            if (currentElement.isArray()) {
                for (JsonNode arrayElement : currentElement) {
                    if (checkMatch(arrayElement)) {
                        return true;
                    }
                }
            } else {
                return checkMatch(currentElement);
            }
        } catch (PatternSyntaxException | UnsupportedOperationException | IllegalStateException e) {
            return false;
        }
        return false;
    }

    private boolean checkMatch(JsonNode element) {
        String elementAsString;
        if (element.isValueNode()) {
            elementAsString = element.asText();
        } else {
            elementAsString = element.toString();
        }

        return fieldRegexPattern.matcher(elementAsString).matches();
    }
}
