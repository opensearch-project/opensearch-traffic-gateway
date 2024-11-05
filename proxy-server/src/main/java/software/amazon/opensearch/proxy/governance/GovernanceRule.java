package software.amazon.opensearch.proxy.governance;

import static io.netty.buffer.Unpooled.copiedBuffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import lombok.SneakyThrows;
import software.amazon.opensearch.proxy.governance.GovernanceRejectionResponseBody.GovernanceRejectionError;

public interface GovernanceRule {
    static final GovernanceRuleResult PASS = new GovernanceRuleResult(GovernanceRuleResultType.PASS, null);
    static final ObjectMapper MAPPER = new ObjectMapper();

    public GovernanceRuleResult evaluate(FullHttpRequest request);

    public default GovernanceRuleResult getPassResult() {
        return PASS;
    }

    public default GovernanceRuleResult getRejectResultWithMessage(String message) {
        ByteBuf content = copiedBuffer(constructJsonRejectionResponse(message), CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, new HttpResponseStatus(getRejectResultHttpStatusCode(), message), content);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        return new GovernanceRuleResult(GovernanceRuleResultType.REJECT, response);
    }

    public default int getRejectResultHttpStatusCode() {
        return HttpResponseStatus.BAD_REQUEST.code();
    }

    @SneakyThrows
    private String constructJsonRejectionResponse(String message) {
        GovernanceRejectionError error =
                new GovernanceRejectionError(GovernanceRejectionResponseBody.GOVERNANCE_REJECTION_ERROR_TYPE, message);
        GovernanceRejectionResponseBody response =
                new GovernanceRejectionResponseBody(error, getRejectResultHttpStatusCode());
        return MAPPER.writeValueAsString(response);
    }
}
