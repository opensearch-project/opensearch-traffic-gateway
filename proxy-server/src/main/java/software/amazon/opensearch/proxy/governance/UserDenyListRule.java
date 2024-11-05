package software.amazon.opensearch.proxy.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import software.amazon.opensearch.proxy.util.UserIdExtractor;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class UserDenyListRule implements GovernanceRule {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final UserIdExtractor userIdExtractor;

    @NonNull
    private Set<String> denyList;

    String responseMessage;

    public UserDenyListRule(@NonNull String userDenyList) {
        this(
                userDenyList,
                UserIdExtractor.DEFAULT_SAML_USER_ID_XPATH,
                UserIdExtractor.DEFAULT_SAML_TOKEN_COOKIE_NAME,
                null);
    }

    @SneakyThrows
    public UserDenyListRule(
            @NonNull String userDenyList,
            @NonNull String samlUserIdXPath,
            @NonNull String samlTokenCookieName,
            String responseMessage) {
        denyList = new HashSet<String>();
        JsonNode denyListJson = JSON_MAPPER.readTree(userDenyList);
        for (JsonNode entry : denyListJson) {
            denyList.add(entry.asText());
        }

        userIdExtractor = new UserIdExtractor(samlUserIdXPath, samlTokenCookieName);

        if (responseMessage != null) {
            this.responseMessage = responseMessage;
        } else {
            this.responseMessage = "Your userId or token is on the list of denied users.";
        }
    }

    @Override
    public int getRejectResultHttpStatusCode() {
        return HttpResponseStatus.UNAUTHORIZED.code();
    }

    @Override
    public GovernanceRuleResult evaluate(FullHttpRequest request) {
        String userId = userIdExtractor.extractUserId(request);
        if (userId != null && getDenyList().contains(userId)) {
            return getRejectResultWithMessage(getResponseMessage());
        }

        String userToken = userIdExtractor.extractUserToken(request);
        if (userToken != null && getDenyList().contains(userToken)) {
            return getRejectResultWithMessage(getResponseMessage());
        }

        return getPassResult();
    }
}
