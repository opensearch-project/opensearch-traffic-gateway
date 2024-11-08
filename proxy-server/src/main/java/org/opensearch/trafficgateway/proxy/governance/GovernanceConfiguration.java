package org.opensearch.trafficgateway.proxy.governance;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Value
public class GovernanceConfiguration {

    @Jacksonized
    @Builder
    @Value
    public static class GovernanceRuleConfiguration {
        private final String ruleClass;
        private final Map<String, Object> ruleConfig;
    }

    private final GovernanceRuleConfiguration[] rules;
    private final String bypassKey;
    private final boolean disableAllGovernanceRules;
}
