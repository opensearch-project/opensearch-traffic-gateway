package software.amazon.opensearch.proxy.offload;

import java.io.IOException;
import lombok.AllArgsConstructor;
import org.opensearch.migrations.tracing.commoncontexts.IConnectionContext;
import org.opensearch.migrations.trafficcapture.IChannelConnectionCaptureSerializer;
import org.opensearch.migrations.trafficcapture.IConnectionCaptureFactory;
import software.amazon.opensearch.proxy.util.UserIdExtractor;

@AllArgsConstructor
public class TrafficAggregatingLogOffloaderFactory implements IConnectionCaptureFactory<Void> {
    private final SerializableHttpMessageFactory messageFactory;
    private final int maxContentLength;

    public TrafficAggregatingLogOffloaderFactory() {
        this(false);
    }

    public TrafficAggregatingLogOffloaderFactory(boolean keepResponseBody) {
        this(
                keepResponseBody,
                TrafficAggregatingLogOffloader.DEFAULT_MAX_CONTENT_LENGTH,
                UserIdExtractor.DEFAULT_SAML_USER_ID_XPATH,
                UserIdExtractor.DEFAULT_SAML_TOKEN_COOKIE_NAME);
    }

    public TrafficAggregatingLogOffloaderFactory(
            boolean keepResponseBody, int maxConentLenth, String samlUserIdXPath, String samlTokenCookieName) {
        this(
                new SerializableHttpMessageFactory(keepResponseBody, samlUserIdXPath, samlTokenCookieName),
                maxConentLenth);
    }

    @Override
    public IChannelConnectionCaptureSerializer<Void> createOffloader(IConnectionContext ctx) throws IOException {
        return new TrafficAggregatingLogOffloader(maxContentLength, messageFactory);
    }
}
