package org.opensearch.trafficgateway.proxy;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import lombok.Lombok;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.opensearch.migrations.tracing.ActiveContextTracker;
import org.opensearch.migrations.tracing.ActiveContextTrackerByActivityType;
import org.opensearch.migrations.tracing.CompositeContextTracker;
import org.opensearch.migrations.tracing.RootOtelContext;
import org.opensearch.migrations.trafficcapture.IConnectionCaptureFactory;
import org.opensearch.migrations.trafficcapture.netty.HeaderValueFilteringCapturePredicate;
import org.opensearch.migrations.trafficcapture.netty.RequestCapturePredicate;
import org.opensearch.migrations.trafficcapture.netty.tracing.IRootWireLoggingContext;
import org.opensearch.migrations.trafficcapture.proxyserver.CaptureProxy;
import org.opensearch.migrations.trafficcapture.proxyserver.RootCaptureContext;
import org.opensearch.migrations.trafficcapture.proxyserver.netty.BacksideConnectionPool;
import org.opensearch.security.ssl.DefaultSecurityKeyStore;
import org.opensearch.trafficgateway.proxy.governance.GovernanceHandlerFactory;
import org.opensearch.trafficgateway.proxy.governance.GovernanceRuleConfigLoader;
import org.opensearch.trafficgateway.proxy.offload.MultiTargetCaptureFactory;
import org.opensearch.trafficgateway.proxy.offload.MultiTargetCaptureFactory.MultiTargetCaptureFactoryBuilder;
import org.opensearch.trafficgateway.proxy.offload.TrafficAggregatingLogOffloader;
import org.opensearch.trafficgateway.proxy.offload.TrafficAggregatingLogOffloaderFactory;
import org.opensearch.trafficgateway.proxy.util.UserIdExtractor;

@Log4j2
public class ProxyServer extends CaptureProxy {
    protected static class GovernanceProxyParameters extends Parameters {
        @Parameter(
                required = false,
                names = "--jaasConfigPath",
                arity = 1,
                description = "Path to the JAAS configuration file specifying authentication for secure connections")
        public String jaasConfigPath;

        @Parameter(
                required = false,
                names = "--rulesConfigPath",
                arity = 1,
                description = "Path to the Rules configuration file specifying different rules for filtering")
        public String rulesConfigPath;

        @Parameter(
                required = false,
                names = "--captureResponseBody",
                arity = 0,
                description = "Whether to capture the response body in captured traffic.")
        public boolean captureResponseBody = false;

        @Parameter(
                required = false,
                names = "--maxCapturedContentLength",
                arity = 1,
                description =
                        "Max content length per request/response for captured HTTP content. Content longer than this will be truncated.")
        public int maxCapturedContentLength = TrafficAggregatingLogOffloader.DEFAULT_MAX_CONTENT_LENGTH;

        @Parameter(
                required = true,
                names = {"--capture"},
                arity = 1,
                description =
                        "What capture implementations to use. Valid values are 'kafka' and 'log'. Multiple values can be specified like '--capture kafka --capture log'.")
        public List<String> captures = new ArrayList<>();

        @Parameter(
                required = false,
                names = "--samlUserIdXPath",
                arity = 1,
                description = "XPath to the userId in the saml assertion XML. Must return a String.")
        public String samlUserIdXPath = UserIdExtractor.DEFAULT_SAML_USER_ID_XPATH;

        @Parameter(
                required = false,
                names = "--samlTokenCookieName",
                arity = 1,
                description = "Cookie name that contains the SAML assertion that is sent to OpenSearch.")
        public String samlTokenCookieName = UserIdExtractor.DEFAULT_SAML_TOKEN_COOKIE_NAME;
    }

    private static GovernanceProxyParameters parseGovernanceArgs(String[] args) {
        GovernanceProxyParameters p = new GovernanceProxyParameters();
        JCommander jCommander = new JCommander(p);
        try {
            jCommander.parse(args);
            return p;
        } catch (ParameterException e) {
            log.error(e);
            log.error("Got args: " + String.join("; ", args));
            log.error("Got args: " + String.join("; ", args));
            StringBuilder usage = new StringBuilder();
            jCommander.getUsageFormatter().usage(usage);
            log.error(usage.toString());
            System.exit(2);
            return null;
        }
    }

    private static void applySystemProperties(GovernanceProxyParameters params) {
        if (params.jaasConfigPath != null) {
            System.setProperty("java.security.auth.login.config", params.jaasConfigPath);
        }

        if (params.rulesConfigPath != null) {
            System.setProperty("proxy.governance.configurationFile", params.rulesConfigPath);
        }
    }

    protected static IConnectionCaptureFactory<Object> getCaptureFactory(
            GovernanceProxyParameters params, RootCaptureContext rootContext) throws IOException {
        MultiTargetCaptureFactoryBuilder captureFactoryBuilder = MultiTargetCaptureFactory.builder();

        for (String capture : params.captures) {
            IConnectionCaptureFactory<?> captureToAdd;
            switch (capture) {
                case "log":
                    captureToAdd = new TrafficAggregatingLogOffloaderFactory(
                            params.captureResponseBody,
                            params.maxCapturedContentLength,
                            params.samlUserIdXPath,
                            params.samlTokenCookieName);
                    break;
                case "kafka":
                    // workaround for the fact that buildKafkaProperties is not visible in parent
                    // class.
                    // as long as kafkaConnection != null, getConnectionCaptureFactory will return
                    // the kafka capture factory
                    if (params.kafkaConnection == null) {
                        throw new IllegalArgumentException(
                                "'kafka' capture was specified by '--kafkaConnection' was not provided.");
                    }
                    @SuppressWarnings("unchecked")
                    IConnectionCaptureFactory<Object> suppressedCaptureToAdd = (IConnectionCaptureFactory<Object>)
                            CaptureProxy.getConnectionCaptureFactory(params, rootContext);
                    captureToAdd = suppressedCaptureToAdd;
                    break;
                default:
                    throw new NotImplementedException("No capture found for " + capture);
            }

            captureFactoryBuilder.factory(captureToAdd);
        }

        return captureFactoryBuilder.build();
    }

    private static BacksideConnectionPool getBacksideConnectionPool(Parameters params) throws SSLException {
        var backsideUri = convertStringToUri(params.backsideUriString);
        var pooledConnectionTimeout = params.destinationConnectionPoolSize == 0
                ? Duration.ZERO
                : Duration.parse(params.destinationConnectionPoolTimeout);

        return new BacksideConnectionPool(
                backsideUri,
                loadBacksideSslContext(backsideUri, params.allowInsecureConnectionsToBackside),
                params.destinationConnectionPoolSize,
                pooledConnectionTimeout);
    }

    private static GovernanceHandlerFactory getGovernanceHandlerFactory() throws FileNotFoundException, IOException {
        var ruleConfigLoader = new GovernanceRuleConfigLoader();
        var rules = ruleConfigLoader.getRules();
        var bypassKey = ruleConfigLoader.getBypassKey();
        var disableAllGovernanceRules = ruleConfigLoader.getDisableAllGovernanceRules();

        return new GovernanceHandlerFactory(bypassKey, disableAllGovernanceRules, rules);
    }

    private static Supplier<SSLEngine> initSSL(Parameters params) {
        var sksOp = Optional.ofNullable(params.sslConfigFilePath)
                .map(sslConfigFile -> new DefaultSecurityKeyStore(
                        getSettings(sslConfigFile),
                        Paths.get(sslConfigFile).toAbsolutePath().getParent()));

        sksOp.ifPresent(DefaultSecurityKeyStore::initHttpSSLConfig);

        return sksOp.map(sks -> (Supplier<SSLEngine>) () -> {
                    try {
                        return sks.createHTTPSSLEngine();
                    } catch (Exception e) {
                        throw Lombok.sneakyThrow(e);
                    }
                })
                .orElse(null);
    }

    private static GovernanceProxy startProxy(
            Parameters params,
            GovernanceHandlerFactory governanceHandlerFactory,
            IRootWireLoggingContext rootContext,
            BacksideConnectionPool backsideConnectionPool,
            Supplier<SSLEngine> sslEngineSupplier,
            IConnectionCaptureFactory<Object> captureFactory,
            RequestCapturePredicate requestCapturePredicate)
            throws InterruptedException {
        GovernanceProxy proxy = new GovernanceProxy(params.frontsidePort, governanceHandlerFactory);

        proxy.start(
                rootContext,
                backsideConnectionPool,
                params.numThreads,
                sslEngineSupplier,
                captureFactory,
                requestCapturePredicate);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("Received shutdown signal.  Trying to shutdown cleanly");
                proxy.stop();
                log.info("Done stopping the proxy.");
            } catch (InterruptedException e) {
                log.error("Caught InterruptedException while shutting down, resetting interrupt status.", e);
                Thread.currentThread().interrupt();
            }
        }));

        return proxy;
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        log.info("Starting Capture Proxy");
        log.info("Got args: " + String.join("; ", args));

        GovernanceProxyParameters params = parseGovernanceArgs(args);
        applySystemProperties(params);

        GovernanceHandlerFactory governanceHandlerFactory = getGovernanceHandlerFactory();

        RootCaptureContext rootContext = new RootCaptureContext(
                RootOtelContext.initializeOpenTelemetryWithCollectorOrAsNoop(params.otelCollectorEndpoint, "capture"),
                new CompositeContextTracker(new ActiveContextTracker(), new ActiveContextTrackerByActivityType()));

        BacksideConnectionPool backsideConnectionPool = getBacksideConnectionPool(params);

        Supplier<SSLEngine> sslEngineSupplier = initSSL(params);

        IConnectionCaptureFactory<Object> captureFactory = getCaptureFactory(params, rootContext);

        HeaderValueFilteringCapturePredicate headerCapturePredicate =
                new HeaderValueFilteringCapturePredicate(convertPairListToMap(params.suppressCaptureHeaderPairs));

        GovernanceProxy proxy = startProxy(
                params,
                governanceHandlerFactory,
                rootContext,
                backsideConnectionPool,
                sslEngineSupplier,
                captureFactory,
                headerCapturePredicate);

        // This loop just gives the main() function something to do while the netty
        // event loops
        // work in the background.
        proxy.waitForClose();
    }
}
