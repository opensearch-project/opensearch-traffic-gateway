package software.amazon.opensearch.proxy.governance;

import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class GovernanceRejectionResponseBody {
    public static final String GOVERNANCE_REJECTION_ERROR_TYPE = "governance_rejection";

    @Value
    public static class GovernanceRejectionError {
        String type;
        String reason;
    }

    public GovernanceRejectionResponseBody(String message) {
        this(
                new GovernanceRejectionError(GOVERNANCE_REJECTION_ERROR_TYPE, message),
                HttpResponseStatus.BAD_REQUEST.code());
    }

    GovernanceRejectionError error;
    int status;
}
