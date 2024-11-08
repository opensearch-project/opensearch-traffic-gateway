package org.opensearch.trafficgateway.proxy.governance;

import io.netty.handler.codec.http.FullHttpResponse;
import lombok.Value;

@Value
public class GovernanceRuleResult {
    GovernanceRuleResultType resultType;
    FullHttpResponse governanceRuleResponse;
}
