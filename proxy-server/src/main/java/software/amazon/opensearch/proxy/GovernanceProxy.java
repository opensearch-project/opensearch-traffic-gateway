package software.amazon.opensearch.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import lombok.NonNull;
import org.opensearch.migrations.trafficcapture.IConnectionCaptureFactory;
import org.opensearch.migrations.trafficcapture.netty.RequestCapturePredicate;
import org.opensearch.migrations.trafficcapture.netty.tracing.IRootWireLoggingContext;
import org.opensearch.migrations.trafficcapture.proxyserver.netty.BacksideConnectionPool;
import org.opensearch.migrations.trafficcapture.proxyserver.netty.NettyScanningHttpProxy;
import software.amazon.opensearch.proxy.governance.GovernanceHandlerFactory;

public class GovernanceProxy extends NettyScanningHttpProxy {
    private final GovernanceHandlerFactory governanceHandlerFactory;

    public GovernanceProxy(int proxyPort, GovernanceHandlerFactory governanceHandlerFactory) {
        super(proxyPort);
        this.governanceHandlerFactory = governanceHandlerFactory;
    }

    @Override
    public void start(
            IRootWireLoggingContext rootContext,
            BacksideConnectionPool backsideConnectionPool,
            int numThreads,
            Supplier<SSLEngine> sslEngineSupplier,
            IConnectionCaptureFactory<Object> connectionCaptureFactory,
            @NonNull RequestCapturePredicate requestCapturePredicate)
            throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("captureProxyPoolBoss"));
        workerGroup = new NioEventLoopGroup(numThreads, new DefaultThreadFactory("captureProxyPoolWorker"));
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        try {
            mainChannel = serverBootstrap
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new GovernanceChannelInitializer<>(
                            rootContext,
                            backsideConnectionPool,
                            sslEngineSupplier,
                            connectionCaptureFactory,
                            requestCapturePredicate,
                            governanceHandlerFactory))
                    .childOption(ChannelOption.AUTO_READ, false)
                    .bind(proxyPort)
                    .sync()
                    .channel();
        } catch (Exception e) {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            throw e;
        }
    }
}
