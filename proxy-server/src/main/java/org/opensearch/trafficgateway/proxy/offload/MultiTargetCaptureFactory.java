package org.opensearch.trafficgateway.proxy.offload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import org.opensearch.migrations.tracing.commoncontexts.IConnectionContext;
import org.opensearch.migrations.trafficcapture.IChannelConnectionCaptureSerializer;
import org.opensearch.migrations.trafficcapture.IConnectionCaptureFactory;

@Builder
public class MultiTargetCaptureFactory implements IConnectionCaptureFactory<Object> {
    @Singular
    private final List<IConnectionCaptureFactory<?>> factories;

    @Override
    public IChannelConnectionCaptureSerializer<Object> createOffloader(IConnectionContext ctx) throws IOException {
        return new MultiTargetOffloader(factories.stream()
                .map(f -> {
                    try {
                        return f.createOffloader(ctx);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .toArray(IChannelConnectionCaptureSerializer<?>[]::new));
    }
}
