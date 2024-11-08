package org.opensearch.trafficgateway.proxy;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import lombok.NonNull;
import org.opensearch.migrations.trafficcapture.IConnectionCaptureFactory;
import org.opensearch.migrations.trafficcapture.netty.ConditionallyReliableLoggingHttpHandler;
import org.opensearch.migrations.trafficcapture.netty.RequestCapturePredicate;
import org.opensearch.migrations.trafficcapture.netty.tracing.IRootWireLoggingContext;
import org.opensearch.migrations.trafficcapture.proxyserver.netty.BacksideConnectionPool;
import org.opensearch.migrations.trafficcapture.proxyserver.netty.FrontsideHandler;
import org.opensearch.migrations.trafficcapture.proxyserver.netty.ProxyChannelInitializer;
import org.opensearch.trafficgateway.proxy.governance.GovernanceHandlerFactory;

public class GovernanceChannelInitializer<T> extends ProxyChannelInitializer<T> {
    private final GovernanceHandlerFactory governanceHandlerFactory;

    public GovernanceChannelInitializer(
            IRootWireLoggingContext rootContext,
            BacksideConnectionPool backsideConnectionPool,
            Supplier<SSLEngine> sslEngineSupplier,
            IConnectionCaptureFactory<T> connectionCaptureFactory,
            @NonNull RequestCapturePredicate requestCapturePredicate,
            GovernanceHandlerFactory governanceHandlerFactory) {
        super(
                rootContext,
                backsideConnectionPool,
                sslEngineSupplier,
                connectionCaptureFactory,
                requestCapturePredicate);
        this.governanceHandlerFactory = governanceHandlerFactory;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws IOException {
        var sslContext = sslEngineProvider != null ? sslEngineProvider.get() : null;
        if (sslContext != null) {
            ch.pipeline().addLast(new SslHandler(sslEngineProvider.get()));
        }

        var connectionId = ch.id().asLongText();
        ch.pipeline()
                .addLast(new ConditionallyReliableLoggingHttpHandler<>(
                        rootContext,
                        "",
                        connectionId,
                        connectionCaptureFactory,
                        requestCapturePredicate,
                        this::shouldGuaranteeMessageOffloading));
        ch.pipeline().addLast(governanceHandlerFactory.createGovernanceHandler());
        ch.pipeline().addLast(new FrontsideHandler(backsideConnectionPool));
    }
}
