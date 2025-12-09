package com.hydroline.beacon.provider.gateway;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.BeaconProviderMod;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ChannelConstants;
import com.hydroline.beacon.provider.protocol.MessageSerializer;
import com.hydroline.beacon.provider.protocol.ResultCode;
import com.hydroline.beacon.provider.transport.BeaconRequestDispatcher;
import com.hydroline.beacon.provider.transport.TransportContext;
import com.hydroline.beacon.provider.transport.TransportKind;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class GatewayServer implements AutoCloseable {
    private final GatewayConfig config;
    private final BeaconRequestDispatcher dispatcher;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;

    public GatewayServer(GatewayConfig config, BeaconRequestDispatcher dispatcher) {
        this.config = config;
        this.dispatcher = dispatcher;
    }

    public synchronized void start() {
        if (serverChannel != null) {
            return;
        }
        if (config.listenPort() <= 0) {
            BeaconProviderMod.LOGGER.info("Beacon Netty gateway disabled (listenPort={})", config.listenPort());
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                    pipeline.addLast(new LengthFieldPrepender(4));
                    if (config.idleTimeoutSeconds() > 0) {
                        pipeline.addLast(new IdleStateHandler(config.idleTimeoutSeconds(), 0, 0));
                    }
                    pipeline.addLast(new GatewayChannelHandler());
                }
            })
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true);
        ChannelFuture bound = bootstrap.bind(new InetSocketAddress(config.listenAddress(), config.listenPort()));
        bound.syncUninterruptibly();
        serverChannel = bound.channel();
        BeaconProviderMod.LOGGER.info(
            "Beacon Netty gateway listening on {}:{}", config.listenAddress(), config.listenPort());
    }

    @Override
    public synchronized void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
    }

    private final class GatewayChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private GatewayConnection connection;

        @Override
        public void channelActive(io.netty.channel.ChannelHandlerContext ctx) {
            this.connection = new GatewayConnection(ctx.channel());
            if (config.handshakeTimeoutSeconds() > 0) {
                connection.scheduleHandshakeTimeout(() -> {
                    if (!connection.handshakeComplete()) {
                        BeaconProviderMod.LOGGER.warn(
                            "Gateway connection {} did not complete handshake in time", connection.temporaryId());
                        sendErrorAndClose(connection, ResultCode.INVALID_PAYLOAD, "Handshake timeout");
                    }
                }, config.handshakeTimeoutSeconds(), TimeUnit.SECONDS);
            }
        }

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] bytes = ByteBufUtil.getBytes(msg);
            try {
                GatewayEnvelope envelope = GatewayCodec.decode(bytes);
                handleEnvelope(ctx, connection, envelope);
            } catch (Exception ex) {
                BeaconProviderMod.LOGGER.error("Failed to parse gateway message", ex);
                sendErrorAndClose(connection, ResultCode.INVALID_PAYLOAD, "Invalid gateway message");
            }
        }

        @Override
        public void userEventTriggered(io.netty.channel.ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                BeaconProviderMod.LOGGER.info("Closing idle gateway connection {}", connection.temporaryId());
                sendErrorAndClose(connection, ResultCode.ERROR, "Idle timeout");
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            BeaconProviderMod.LOGGER.warn("Gateway connection {} error", connection.temporaryId(), cause);
            ctx.close();
        }

        @Override
        public void channelInactive(io.netty.channel.ChannelHandlerContext ctx) {
            connection.cancelHandshakeTimeout();
        }
    }

    private void handleEnvelope(io.netty.channel.ChannelHandlerContext ctx, GatewayConnection connection, GatewayEnvelope envelope) {
        switch (envelope.type()) {
            case HANDSHAKE:
                handleHandshake(ctx, connection, envelope.body());
                break;
            case REQUEST:
                handleRequest(connection, envelope.body());
                break;
            case PING:
                handlePing(connection, envelope.body());
                break;
            default:
                sendError(connection, ResultCode.INVALID_PAYLOAD, "Unsupported message type");
                break;
        }
    }

    private void handleHandshake(io.netty.channel.ChannelHandlerContext ctx, GatewayConnection connection, JsonObject body) {
        if (connection.handshakeComplete()) {
            sendError(connection, ResultCode.INVALID_PAYLOAD, "Handshake already completed");
            return;
        }
        String token = body.has("token") ? body.get("token").getAsString() : "";
        if (!config.authToken().equals(token)) {
            sendErrorAndClose(connection, ResultCode.INVALID_PAYLOAD, "Invalid token");
            return;
        }
        int protocolVersion = body.has("protocolVersion") ? body.get("protocolVersion").getAsInt() : ChannelConstants.PROTOCOL_VERSION;
        if (protocolVersion != ChannelConstants.PROTOCOL_VERSION) {
            sendErrorAndClose(connection, ResultCode.INVALID_PAYLOAD, "Unsupported protocol version");
            return;
        }
        UUID connectionId = UUID.randomUUID();
        connection.markHandshakeComplete(connectionId);
        connection.cancelHandshakeTimeout();
        JsonObject ack = new JsonObject();
        ack.addProperty("protocolVersion", ChannelConstants.PROTOCOL_VERSION);
        ack.addProperty("connectionId", connectionId.toString());
        ack.addProperty("serverName", BeaconProviderMod.MOD_NAME);
        ack.addProperty("modVersion", BeaconProviderMod.getVersion());
        ack.addProperty("heartbeatIntervalSeconds", Math.max(5, config.idleTimeoutSeconds() / 2));
        ack.addProperty("message", "ready");
        connection.send(GatewayMessageType.HANDSHAKE_ACK, ack);
    }

    private void handleRequest(GatewayConnection connection, JsonObject body) {
        if (!connection.handshakeComplete()) {
            sendError(connection, ResultCode.INVALID_PAYLOAD, "Handshake required");
            return;
        }
        JsonObject requestJson = body == null ? new JsonObject() : body;
        byte[] requestBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);
        TransportContext context = new TransportContext(connection.connectionId(), TransportKind.NETTY_GATEWAY, Instant.now());
        BeaconResponse response = dispatcher.dispatch(requestBytes, context);
        JsonObject responseJson = MessageSerializer.serializeToJson(response);
        connection.send(GatewayMessageType.RESPONSE, responseJson);
    }

    private void handlePing(GatewayConnection connection, JsonObject body) {
        if (!connection.handshakeComplete()) {
            return;
        }
        connection.send(GatewayMessageType.PONG, body);
    }

    private void sendError(GatewayConnection connection, ResultCode code, String message) {
        JsonObject body = new JsonObject();
        body.addProperty("errorCode", code.name());
        body.addProperty("message", message);
        Channel channel = connection.channel();
        channel.writeAndFlush(channel.alloc().buffer().writeBytes(
            GatewayCodec.encode(GatewayMessageType.ERROR, connection.connectionId(), body)));
    }

    private void sendError(Channel channel, ResultCode code, String message) {
        JsonObject body = new JsonObject();
        body.addProperty("errorCode", code.name());
        body.addProperty("message", message);
        channel.writeAndFlush(channel.alloc().buffer().writeBytes(GatewayCodec.encode(GatewayMessageType.ERROR, null, body)));
    }

    private void sendErrorAndClose(GatewayConnection connection, ResultCode code, String message) {
        sendError(connection, code, message);
        connection.channel().close();
    }

    private void sendErrorAndClose(Channel channel, ResultCode code, String message) {
        sendError(channel, code, message);
        channel.close();
    }
}
