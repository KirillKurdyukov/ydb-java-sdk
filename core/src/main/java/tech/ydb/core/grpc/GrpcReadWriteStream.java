package tech.ydb.core.grpc;


/**
 *
 * @author Aleksandr Gorshenin
 * @param <R> type of message received
 * @param <W> type of message to be sent to the server
 */
public interface GrpcReadWriteStream<R, W> extends GrpcReadStream<R>, AutoCloseable {
    String authToken();

    void sendNext(W message);

    @Override
    void close();
}
