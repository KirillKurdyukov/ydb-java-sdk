package tech.ydb.core.grpc;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import tech.ydb.core.Result;
import tech.ydb.core.auth.AuthProvider;
import tech.ydb.core.auth.NopAuthProvider;
import tech.ydb.core.rpc.OperationTray;
import tech.ydb.core.rpc.RpcTransport;
import tech.ydb.core.rpc.StreamObserver;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.util.RoundRobinLoadBalancerFactory;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;


/**
 * @author Sergey Polovko
 */
public class GrpcTransport implements RpcTransport {

    private static final Logger logger = Logger.getLogger(GrpcTransport.class.getName());

    private final ManagedChannel realChannel;
    private final Channel channel;
    private final CallOptions callOptions;
    private final GrpcOperationTray operationTray;

    GrpcTransport(GrpcTransportBuilder builder) {
        if (builder.endpoint != null) {
            String target = YdbNameResolver.SCHEME + "://" + builder.endpoint +
                (builder.database.startsWith("/") ? "" : "/") + builder.database;

            Metadata extraHeaders = new Metadata();
            extraHeaders.put(YdbHeaders.DATABASE, builder.database);
            ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(extraHeaders);

            this.realChannel = createChannel(target, builder.authProvider);
            this.channel = ClientInterceptors.intercept(realChannel, interceptor);
        } else {
            this.channel = this.realChannel = createChannel(builder.host, builder.port);
        }

        if (builder.authProvider != NopAuthProvider.INSTANCE) {
            this.callOptions = builder.callOptions
                .withCallCredentials(new YdbCallCredentials(builder.authProvider));
        } else {
            this.callOptions = builder.callOptions;
        }

        this.operationTray = new GrpcOperationTray(this);
    }

    private static ManagedChannel createChannel(String host, int port) {
        return NettyChannelBuilder.forAddress(new InetSocketAddress(host, port))
            .negotiationType(NegotiationType.PLAINTEXT)
            .maxInboundMessageSize(64 << 20) // 64 MiB
            .withOption(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT)
            .build();
    }

    private static ManagedChannel createChannel(String target, AuthProvider authProvider) {
        return NettyChannelBuilder.forTarget(target)
            .negotiationType(NegotiationType.PLAINTEXT)
            .maxInboundMessageSize(64 << 20) // 64 MiB
            .withOption(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT)
            .nameResolverFactory(YdbNameResolver.newFactory(authProvider))
            .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
            .build();
    }

    public <ReqT, RespT> CompletableFuture<Result<RespT>> unaryCall(MethodDescriptor<ReqT, RespT> method, ReqT request) {
        CompletableFuture<Result<RespT>> promise = new CompletableFuture<>();
        ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
        sendOneRequest(call, request, new UnaryStreamToFuture<>(promise));
        return promise;
    }

    public <ReqT, RespT> void unaryCall(
        MethodDescriptor<ReqT, RespT> method,
        ReqT request,
        Consumer<Result<RespT>> consumer)
    {
        ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
        sendOneRequest(call, request, new UnaryStreamToConsumer<>(consumer));
    }

    public <ReqT, RespT> void unaryCall(
        MethodDescriptor<ReqT, RespT> method,
        ReqT request,
        BiConsumer<RespT, Status> consumer)
    {
        ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
        sendOneRequest(call, request, new UnaryStreamToBiConsumer<>(consumer));
    }

    public <ReqT, RespT> void serverStreamCall(
        MethodDescriptor<ReqT, RespT> method,
        ReqT request,
        StreamObserver<RespT> observer)
    {
        ClientCall<ReqT, RespT> call = channel.newCall(method, callOptions);
        ServerStreamToObserver<ReqT, RespT> listener = new ServerStreamToObserver<>(observer, call);
        sendOneRequest(call, request, listener);
    }

    private static <ReqT, RespT> void sendOneRequest(
        ClientCall<ReqT, RespT> call,
        ReqT request,
        ClientCall.Listener<RespT> listener)
    {
        try {
            call.start(listener, new Metadata());
            call.request(1);
            call.sendMessage(request);
            call.halfClose();
        } catch (Throwable t) {
            try {
                call.cancel(null, t);
            } catch (Throwable ex) {
                logger.log(Level.SEVERE, "Exception encountered while closing the call", ex);
            }
            listener.onClose(Status.INTERNAL.withCause(t), null);
        }
    }

    @Override
    public OperationTray getOperationTray() {
        return operationTray;
    }

    @Override
    public void close() {
        operationTray.close();
        realChannel.shutdown();
    }
}
