package org.opensearch.trafficgateway.proxy.governance;

import lombok.Setter;

public class GovernanceHandlerFactory {
    private GovernanceRule[] rules;

    @Setter
    private String bypassKey;

    @Setter
    private boolean disableAllGovernanceRules;

    public GovernanceHandlerFactory(GovernanceRule... rules) {
        this.rules = rules;
    }

    public GovernanceHandlerFactory(String bypassKey, boolean disableAllGovernanceRules, GovernanceRule... rules) {
        this.bypassKey = bypassKey;
        this.disableAllGovernanceRules = disableAllGovernanceRules;
        this.rules = rules;
    }

    public GovernanceHandler createGovernanceHandler() {
        return new GovernanceHandler(this.bypassKey, this.disableAllGovernanceRules, this.rules);
    }
}
