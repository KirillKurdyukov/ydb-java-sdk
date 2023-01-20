package tech.ydb.core.grpc;

import java.time.Duration;
import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

import io.grpc.Metadata;

/**
 * @author Nikolay Perfilov
 */
public class GrpcRequestSettings {
    private final long deadlineAfter;
    private final EndpointInfo preferredEndpoint;
    private final Metadata extraHeaders;
    private final Consumer<Metadata> trailersHandler;

    private GrpcRequestSettings(Builder builder) {
        this.deadlineAfter = builder.getDeadlineAfter();
        this.preferredEndpoint = builder.getPreferredEndpoint();
        this.extraHeaders = builder.getExtraHeaders();
        this.trailersHandler = builder.getTrailersHandler();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public long getDeadlineAfter() {
        return deadlineAfter;
    }

    public EndpointInfo getPreferredEndpoint() {
        return preferredEndpoint;
    }

    public Metadata getExtraHeaders() {
        return extraHeaders;
    }

    public Consumer<Metadata> getTrailersHandler() {
        return trailersHandler;
    }

    @ParametersAreNonnullByDefault
    public static final class Builder {
        private long deadlineAfter = 0;
        private EndpointInfo preferredEndpoint = null;
        private Metadata extraHeaders = null;
        private Consumer<Metadata> trailersHandler = null;

        /**
         * Returns a new {@code Builder} with a deadline, based on the running Java Virtual Machine's
         * high-resolution time source {@link System#nanoTime() }
         * If the value is null or negative, then the default
         * {@link GrpcTransportBuilder#withReadTimeout(java.time.Duration)} will be used.
         *
         * @param deadlineAfter the value of the JVM time source, when request will be cancelled, in nanoseconds
         * @return {@code Builder} with a deadline
         */
        public Builder withDeadlineAfter(long deadlineAfter) {
            this.deadlineAfter = deadlineAfter;
            return this;
        }

        /**
         * Returns a new {@code Builder} with a deadline. Specified duration will be converted to the value of JVM
         * high-resolution time source
         * @param duration the deadline duration
         * @return {@code Builder} with a deadline
         */
        public Builder withDeadline(Duration duration) {
            if (duration != null && !duration.isNegative()) {
                this.deadlineAfter = System.nanoTime() + duration.toNanos();
            } else {
                this.deadlineAfter = 0l;
            }
            return this;
        }

        public Builder withPreferredEndpoint(EndpointInfo preferredEndpoint) {
            this.preferredEndpoint = preferredEndpoint;
            return this;
        }

        public Builder withExtraHeaders(Metadata headers) {
            this.extraHeaders = headers;
            return this;
        }

        public Builder withTrailersHandler(Consumer<Metadata> handler) {
            this.trailersHandler = handler;
            return this;
        }

        public long getDeadlineAfter() {
            return deadlineAfter;
        }

        public EndpointInfo getPreferredEndpoint() {
            return preferredEndpoint;
        }

        public Metadata getExtraHeaders() {
            return extraHeaders;
        }

        public Consumer<Metadata> getTrailersHandler() {
            return trailersHandler;
        }

        public GrpcRequestSettings build() {
            return new GrpcRequestSettings(this);
        }
    }
}
