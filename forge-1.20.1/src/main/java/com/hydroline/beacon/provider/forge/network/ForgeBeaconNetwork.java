package com.hydroline.beacon.provider.forge.network;

import com.hydroline.beacon.provider.gateway.BeaconGatewayManager;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ChannelConstants;
import com.hydroline.beacon.provider.protocol.MessageSerializer;
import com.hydroline.beacon.provider.service.BeaconProviderService;
import com.hydroline.beacon.provider.service.BeaconServiceFactory;
import com.hydroline.beacon.provider.transport.ChannelMessageRouter;
import com.hydroline.beacon.provider.transport.ChannelMessenger;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.mtr.MtrQueryRegistry;
import com.hydroline.beacon.provider.forge.mtr.ForgeMtrQueryGateway;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.NoSuchElementException;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ICustomPacket;

/**
 * Forge-side wiring that binds vanilla custom payloads into the shared channel router.
 */
public final class ForgeBeaconNetwork {
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation(ChannelConstants.CHANNEL_NAME);
    private static final String HANDLER_NAME = "hydroline-beacon-provider";

    private final BeaconProviderService service;
    private final ChannelMessageRouter router;
    private final ForgeChannelMessenger messenger;
    private final BeaconGatewayManager gatewayManager;

    public ForgeBeaconNetwork() {
        this.service = BeaconServiceFactory.createDefault();
        this.messenger = new ForgeChannelMessenger();
        this.router = new ChannelMessageRouter(service, messenger);
        this.gatewayManager = new BeaconGatewayManager(service);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        messenger.setServer(server);
        MtrQueryRegistry.register(new ForgeMtrQueryGateway(() -> server));
        gatewayManager.start(FMLPaths.CONFIGDIR.get());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        messenger.setServer(null);
        MtrQueryRegistry.register(MtrQueryGateway.UNAVAILABLE);
        gatewayManager.stop();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            attachHandler(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            detachHandler(player);
        }
    }

    private void attachHandler(ServerPlayer player) {
        Connection connection = player.connection == null ? null : player.connection.connection;
        if (connection == null) {
            return;
        }
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }
        UUID playerId = player.getUUID();
        channel.eventLoop().execute(() -> {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER_NAME) == null) {
                pipeline.addBefore("packet_handler", HANDLER_NAME, new PluginPayloadInboundHandler(playerId));
            }
        });
    }

    private void detachHandler(ServerPlayer player) {
        Connection connection = player.connection == null ? null : player.connection.connection;
        if (connection == null) {
            return;
        }
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }
        channel.eventLoop().execute(() -> {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                try {
                    pipeline.remove(HANDLER_NAME);
                } catch (NoSuchElementException ignored) {
                }
            }
        });
    }

    private final class PluginPayloadInboundHandler extends SimpleChannelInboundHandler<ServerboundCustomPayloadPacket> {
        private final UUID playerUuid;

        private PluginPayloadInboundHandler(UUID playerUuid) {
            super(false);
            this.playerUuid = playerUuid;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ServerboundCustomPayloadPacket packet) throws Exception {
            ResourceLocation id = ((ICustomPacket<?>) packet).getName();
            if (!CHANNEL_ID.equals(id)) {
                ctx.fireChannelRead(packet);
                return;
            }
            FriendlyByteBuf payload = ((ICustomPacket<?>) packet).getInternalData();
            if (payload == null) {
                return;
            }
            byte[] bytes = new byte[payload.readableBytes()];
            payload.readBytes(bytes);
            MinecraftServer server = messenger.getServer();
            if (server != null) {
                server.execute(() -> router.handleIncoming(playerUuid, bytes));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.fireExceptionCaught(cause);
        }
    }

    private static final class ForgeChannelMessenger implements ChannelMessenger {
        private volatile MinecraftServer server;

        void setServer(MinecraftServer server) {
            this.server = server;
        }

        MinecraftServer getServer() {
            return server;
        }

        @Override
        public void reply(UUID playerUuid, BeaconResponse response) {
            MinecraftServer current = server;
            if (current == null) {
                return;
            }
            ServerPlayer player = current.getPlayerList().getPlayer(playerUuid);
            if (player == null) {
                return;
            }
            byte[] bytes = MessageSerializer.serialize(response);
            FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer(bytes.length));
            reply.writeBytes(bytes);
            player.connection.send(new ClientboundCustomPayloadPacket(CHANNEL_ID, reply));
        }
    }
}
