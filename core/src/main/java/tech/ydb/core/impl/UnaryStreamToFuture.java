package tech.ydb.core.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import io.grpc.ClientCall;
import io.grpc.Metadata;

import tech.ydb.core.Issue;
import tech.ydb.core.Result;
import tech.ydb.core.Status;
import tech.ydb.core.StatusCode;
import tech.ydb.core.grpc.GrpcStatuses;


/**
 * @author Sergey Polovko
 * @param <T> type of value
 */
public class UnaryStreamToFuture<T> extends ClientCall.Listener<T> {
    private static final Status NO_VALUE = Status.of(StatusCode.CLIENT_INTERNAL_ERROR)
            .withIssues(Issue.of("No value received for gRPC unary call", Issue.Severity.ERROR));

    private static final Status MULTIPLY_VALUES = Status.of(StatusCode.CLIENT_INTERNAL_ERROR)
            .withIssues(Issue.of("More than one value received for gRPC unary call", Issue.Severity.ERROR));

    private final CompletableFuture<Result<T>> responseFuture;
    private final Consumer<Metadata> trailersHandler;
    private final Consumer<io.grpc.Status> statusHandler;
    private final AtomicReference<T> value = new AtomicReference<>();

    public UnaryStreamToFuture(CompletableFuture<Result<T>> responseFuture,
            Consumer<Metadata> trailersHandler,
            Consumer<io.grpc.Status> statusHandler) {
        this.responseFuture = responseFuture;
        this.trailersHandler = trailersHandler;
        this.statusHandler = statusHandler;
    }

    @Override
    public void onMessage(T value) {
        if (!this.value.compareAndSet(null, value)) {
            responseFuture.complete(Result.fail(MULTIPLY_VALUES));
        }
    }

    @Override
    public void onClose(io.grpc.Status status, @Nullable Metadata trailers) {
        if (trailersHandler != null && trailers != null) {
            trailersHandler.accept(trailers);
        }
        if (statusHandler != null) {
            statusHandler.accept(status);
        }

        if (status.isOk()) {
            T snapshotValue = value.get();

            if (snapshotValue == null) {
                responseFuture.complete(Result.fail(NO_VALUE));
            } else {
                responseFuture.complete(Result.success(snapshotValue));
            }
        } else {
            responseFuture.complete(GrpcStatuses.toResult(status));
        }
    }
}
