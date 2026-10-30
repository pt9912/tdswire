package io.github.pt9912.tdswire.transport.channel;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Ein Testkanal, dessen {@code close()} den vorgegebenen Fehler wirft (eine {@link IOException} oder
 * eine Laufzeitausnahme); alle anderen Operationen sind nicht vorgesehen. Liegt in Java, weil die
 * Signatur von {@code connect} {@code Void} verlangt, das die Kotlin-Regeln dieses Repos verbieten.
 */
final class FailingCloseChannel extends AsynchronousSocketChannel {
    private final Exception failure;
    private int closeCalls;

    FailingCloseChannel(Exception failure) {
        super(null);
        this.failure = failure;
    }

    int closeCalls() {
        return closeCalls;
    }

    @Override
    public void close() throws IOException {
        closeCalls++;
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        throw (RuntimeException) failure;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException();
    }

    @Override
    public AsynchronousSocketChannel bind(SocketAddress local) {
        throw unsupported();
    }

    @Override
    public <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value) {
        throw unsupported();
    }

    @Override
    public <T> T getOption(SocketOption<T> name) {
        throw unsupported();
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        throw unsupported();
    }

    @Override
    public AsynchronousSocketChannel shutdownInput() {
        throw unsupported();
    }

    @Override
    public AsynchronousSocketChannel shutdownOutput() {
        throw unsupported();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        throw unsupported();
    }

    @Override
    public SocketAddress getLocalAddress() {
        throw unsupported();
    }

    @Override
    public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler) {
        throw unsupported();
    }

    @Override
    public Future<Void> connect(SocketAddress remote) {
        throw unsupported();
    }

    @Override
    public <A> void read(
            ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw unsupported();
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        throw unsupported();
    }

    @Override
    public <A> void read(
            ByteBuffer[] dsts,
            int offset,
            int length,
            long timeout,
            TimeUnit unit,
            A attachment,
            CompletionHandler<Long, ? super A> handler) {
        throw unsupported();
    }

    @Override
    public <A> void write(
            ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw unsupported();
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        throw unsupported();
    }

    @Override
    public <A> void write(
            ByteBuffer[] srcs,
            int offset,
            int length,
            long timeout,
            TimeUnit unit,
            A attachment,
            CompletionHandler<Long, ? super A> handler) {
        throw unsupported();
    }
}
