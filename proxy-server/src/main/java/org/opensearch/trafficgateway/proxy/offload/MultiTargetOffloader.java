package org.opensearch.trafficgateway.proxy.offload;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.opensearch.migrations.trafficcapture.IChannelConnectionCaptureSerializer;

/*
 * TODO: This might be more maintainable if multiple netty handlers were used in succession and
 * there were no side effects (might need a setup/teardown sort of functionality to bookend the
 * offloading handlers)
 */
@Log4j2
public class MultiTargetOffloader implements IChannelConnectionCaptureSerializer<Object> {
    private final IChannelConnectionCaptureSerializer<?>[] offloaders;

    public MultiTargetOffloader(IChannelConnectionCaptureSerializer<?>... offloaders) {
        this.offloaders = offloaders;
    }

    @Override
    public void addBindEvent(Instant timestamp, SocketAddress addr) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addBindEvent(timestamp, addr);
        }
    }

    @Override
    public void addConnectEvent(Instant timestamp, SocketAddress remote, SocketAddress local) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addConnectEvent(timestamp, remote, local);
        }
    }

    @Override
    public void addDisconnectEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addDisconnectEvent(timestamp);
        }
    }

    @Override
    public void addCloseEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addCloseEvent(timestamp);
        }
    }

    @Override
    public void addDeregisterEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addDeregisterEvent(timestamp);
        }
    }

    @Override
    public void addReadEvent(Instant timestamp, ByteBuf buffer) throws IOException {
        log.debug("Sending read to all offloaders");
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            log.debug("Sending read to {}", () -> offloader.getClass().getName());
            offloader.addReadEvent(timestamp, buffer);
        }
    }

    @Override
    public void addWriteEvent(Instant timestamp, ByteBuf buffer) throws IOException {
        log.debug("Sending write to all offloaders");
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            log.debug("Sending write to {}", () -> offloader.getClass().getName());
            offloader.addWriteEvent(timestamp, buffer);
        }
    }

    @Override
    public void addFlushEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addFlushEvent(timestamp);
        }
    }

    @Override
    public void addChannelRegisteredEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addChannelRegisteredEvent(timestamp);
        }
    }

    @Override
    public void addChannelUnregisteredEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addChannelUnregisteredEvent(timestamp);
        }
    }

    @Override
    public void addChannelActiveEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addChannelActiveEvent(timestamp);
        }
    }

    @Override
    public void addChannelInactiveEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addChannelInactiveEvent(timestamp);
        }
    }

    @Override
    public void addChannelReadEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addChannelReadEvent(timestamp);
        }
    }

    @Override
    public void addChannelReadCompleteEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addChannelReadCompleteEvent(timestamp);
        }
    }

    @Override
    public void addUserEventTriggeredEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addUserEventTriggeredEvent(timestamp);
        }
    }

    @Override
    public void addChannelWritabilityChangedEvent(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addChannelWritabilityChangedEvent(timestamp);
        }
    }

    @Override
    public void addExceptionCaughtEvent(Instant timestamp, Throwable t) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addExceptionCaughtEvent(timestamp, t);
        }
    }

    @Override
    public void addEndOfFirstLineIndicator(int characterIndex) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addEndOfFirstLineIndicator(characterIndex);
        }
    }

    @Override
    public void addEndOfHeadersIndicator(int characterIndex) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.addEndOfHeadersIndicator(characterIndex);
        }
    }

    @Override
    public void commitEndOfHttpMessageIndicator(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.commitEndOfHttpMessageIndicator(timestamp);
        }
    }

    @Override
    public CompletableFuture<Object> flushCommitAndResetStream(boolean isFinal) throws IOException {
        CompletableFuture<?>[] futures = Stream.of(offloaders)
                .map(o -> {
                    try {
                        return o.flushCommitAndResetStream(isFinal);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .toArray(CompletableFuture<?>[]::new);

        return CompletableFuture.allOf(futures).thenApply(v -> (Object) null);
    }

    @Override
    public void cancelCaptureForCurrentRequest(Instant timestamp) throws IOException {
        for (IChannelConnectionCaptureSerializer<?> offloader : offloaders) {
            offloader.cancelCaptureForCurrentRequest(timestamp);
        }
    }
}
