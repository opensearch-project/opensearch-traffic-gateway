package org.opensearch.trafficgateway.proxy.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.shibboleth.utilities.java.support.security.KeyNotFoundException;
import org.opensearch.trafficgateway.proxy.governance.GovernanceConfiguration.GovernanceRuleConfiguration;

@Log4j2
public class GovernanceRuleConfigLoader {
    public static final String CONFIG_FILE_PROPERTY_NAME = "proxy.governance.configurationFile";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Getter(lazy = true)
    private final GovernanceConfiguration governanceConfig = parseGovernanceConfig();

    @SneakyThrows
    private GovernanceConfiguration parseGovernanceConfig() {
        try (BufferedReader configFileReader =
                new BufferedReader(new FileReader(System.getProperty(CONFIG_FILE_PROPERTY_NAME)))) {
            return JSON_MAPPER.readValue(configFileReader, GovernanceConfiguration.class);
        }
    }

    public GovernanceRule[] getRules() {
        GovernanceRule[] rules = Stream.of(getGovernanceConfig().getRules())
                .map(ruleConfig -> {
                    log.info(ruleConfig.getRuleClass());
                    return instantiateRule(ruleConfig);
                })
                .toArray(GovernanceRule[]::new);
        return rules;
    }

    public String getBypassKey() {
        return getGovernanceConfig().getBypassKey();
    }

    public boolean getDisableAllGovernanceRules() {
        return getGovernanceConfig().isDisableAllGovernanceRules();
    }

    @SneakyThrows
    public GovernanceRule instantiateRule(GovernanceRuleConfiguration ruleConfig) {
        @SuppressWarnings("unchecked")
        Class<? extends GovernanceRule> ruleClass =
                (Class<? extends GovernanceRule>) Class.forName(ruleConfig.getRuleClass());

        int numParameters = ruleConfig.getRuleConfig().size();
        List<Class<String>> constructorTypes = Collections.nCopies(numParameters, String.class);

        Constructor<? extends GovernanceRule> ruleConstructor;
        try {
            ruleConstructor = ruleClass.getDeclaredConstructor(constructorTypes.toArray(Class<?>[]::new));
        } catch (NoSuchMethodException e) {
            log.error(
                    () -> ruleClass.getName() + " does not contain a constructor that takes " + numParameters
                            + " arguments.",
                    e);
            throw e;
        }

        String[] params = new String[numParameters];
        for (int i = 0; i < numParameters; i++) {
            Parameter param = ruleConstructor.getParameters()[i];

            if (!param.isNamePresent()) {
                throw new IllegalStateException("All constructor parameters must have names");
            }

            if (!ruleConfig.getRuleConfig().containsKey(param.getName())) {
                throw new KeyNotFoundException("No argument found in rule config for parameter: " + param.getName());
            }

            Object paramValue = ruleConfig.getRuleConfig().get(param.getName());
            params[i] = paramValue instanceof String ? (String) paramValue : JSON_MAPPER.writeValueAsString(paramValue);
        }

        GovernanceRule rule = ruleConstructor.newInstance((Object[]) params);

        return rule;
    }
}
