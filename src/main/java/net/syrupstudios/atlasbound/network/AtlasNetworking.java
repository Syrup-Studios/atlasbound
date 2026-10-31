package net.syrupstudios.atlasbound.network;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.rasanovum.rosetta.network.RosettaNetwork;
import net.syrupstudios.atlasbound.AtlasData;
import net.syrupstudios.atlasbound.AtlasManager;
import net.syrupstudios.atlasbound.AtlasStorage;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.backend.AtlasMapBackend;
import net.syrupstudios.atlasbound.client.AtlasClientPacketHandlers;

/** Network transport and saved atlas map synchronization. */
public final class AtlasNetworking {
    private static final RosettaNetwork.Channel CHANNEL = RosettaNetwork.channel("atlasbound");
    private static final Map<UUID, ClientState> CLIENTS = new HashMap<>();
    private static long epochCounter;
    private static final Map<UUID, Integer> LAST_OPEN_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_TELEPORT_NANOS = new HashMap<>();
    private static final Map<UUID, Long> LAST_MARKER_EDIT_NANOS = new HashMap<>();
    private static boolean initialized;

    private AtlasNetworking() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        CHANNEL.serverbound("open", AtlasPackets.Open.class, AtlasPackets.Open::write, AtlasPackets.Open::new,
                (packet, level, player) -> openPacket(packet, (ServerPlayer) player));
        CHANNEL.serverbound("close", AtlasPackets.Close.class, AtlasPackets.Close::write, AtlasPackets.Close::new,
                (packet, level, player) -> closePacket(packet, (ServerPlayer) player));
        CHANNEL.serverbound("marker_edit", AtlasPackets.MarkerEdit.class, AtlasPackets.MarkerEdit::write,
                AtlasPackets.MarkerEdit::new, (packet, level, player) -> markerEditPacket(packet, (ServerPlayer) player));
        CHANNEL.serverbound("teleport", AtlasPackets.Teleport.class, AtlasPackets.Teleport::write,
                AtlasPackets.Teleport::new, (packet, level, player) -> teleportPacket(packet, (ServerPlayer) player));
        CHANNEL.serverbound("map_request", AtlasMapPackets.Request.class, AtlasMapPackets.Request::write,
                AtlasMapPackets.Request::new, (packet, level, player) -> AtlasMapBackend.request((ServerPlayer) player, packet));
        CHANNEL.clientbound("selection", AtlasPackets.Selection.class, AtlasPackets.Selection::write,
                AtlasPackets.Selection::new, (packet, level, player) -> AtlasClientPacketHandlers.selection(packet, player));
        CHANNEL.clientbound("region", AtlasPackets.Region.class, AtlasPackets.Region::write,
                AtlasPackets.Region::new, (packet, level, player) -> AtlasClientPacketHandlers.region(packet, player));
        CHANNEL.clientbound("ready", AtlasPackets.Ready.class, AtlasPackets.Ready::write,
                AtlasPackets.Ready::new, (packet, level, player) -> AtlasClientPacketHandlers.ready(packet, player));
        CHANNEL.clientbound("marker", AtlasPackets.Marker.class, AtlasPackets.Marker::write,
                AtlasPackets.Marker::new, (packet, level, player) -> AtlasClientPacketHandlers.marker(packet, player));
        CHANNEL.clientbound("carried", AtlasPackets.Carried.class, AtlasPackets.Carried::write,
                AtlasPackets.Carried::new, (packet, level, player) -> AtlasClientPacketHandlers.carried(packet, player));
        CHANNEL.clientbound("map_world", AtlasMapPackets.World.class, AtlasMapPackets.World::write,
                AtlasMapPackets.World::new, (packet, level, player) -> net.syrupstudios.atlasbound.client.AtlasMapClient.world(packet));
        CHANNEL.clientbound("map_chunk", AtlasMapPackets.Chunk.class, AtlasMapPackets.Chunk::write,
                AtlasMapPackets.Chunk::new, (packet, level, player) -> net.syrupstudios.atlasbound.client.AtlasMapClient.receive(packet));
        AtlasManager.setTransport(new AtlasManager.Transport() {
            @Override
            public void selection(ServerPlayer player, UUID atlasId, AtlasData data, boolean openScreen) {
                sendSelection(player, atlasId, data, openScreen);
            }

            @Override
            public void carried(ServerPlayer player, Set<UUID> atlases) {
                ClientState state = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new ClientState());
                if (atlases.equals(state.lastCarried)) return;
                state.lastCarried = Set.copyOf(atlases);
                RosettaNetwork.sendToPlayer(new AtlasPackets.Carried(state.lastCarried), player);
            }

            @Override
            public void regionChanged(ServerPlayer player, UUID atlasId, ResourceLocation dimension, long region, BitSet bits) {
                ClientState state = CLIENTS.get(player.getUUID());
                if (state == null || !atlasId.equals(state.atlas)) return;
                RosettaNetwork.sendToPlayer(new AtlasPackets.Region(state.epoch, dimension, region, bits.toLongArray()), player);
            }
        });
    }

    /** Called for both a keybind request and a vanilla atlas item-use event. */
    public static boolean open(ServerPlayer player, int slot) {
        int tick = player.getServer().getTickCount();
        Integer previous = LAST_OPEN_TICK.get(player.getUUID());
        if (previous != null && tick - previous < 10) return false;
        LAST_OPEN_TICK.put(player.getUUID(), tick);
        return AtlasManager.open(player, slot);
    }

    public static void disconnect(ServerPlayer player) {
        AtlasMapBackend.disconnect(player);
        AtlasManager.removePlayer(player);
        CLIENTS.remove(player.getUUID());
        LAST_OPEN_TICK.remove(player.getUUID());
        LAST_TELEPORT_NANOS.remove(player.getUUID());
        LAST_MARKER_EDIT_NANOS.remove(player.getUUID());
    }

    public static void serverStopped(MinecraftServer server) {
        CLIENTS.clear();
        LAST_OPEN_TICK.clear();
        LAST_TELEPORT_NANOS.clear();
        LAST_MARKER_EDIT_NANOS.clear();
        AtlasMapBackend.serverStopped(server);
        AtlasManager.serverStopped(server);
    }

    private static void sendSelection(ServerPlayer player, UUID atlas, AtlasData data, boolean openScreen) {
        ClientState state = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new ClientState());
        if (atlas != null && atlas.equals(state.atlas)) {
            RosettaNetwork.sendToPlayer(new AtlasPackets.Selection(state.epoch, atlas, openScreen), player);
            sendWorld(player, state);
            RosettaNetwork.sendToPlayer(new AtlasPackets.Ready(state.epoch), player);
            return;
        }
        state.epoch = ++epochCounter;
        state.atlas = atlas;
        RosettaNetwork.sendToPlayer(new AtlasPackets.Selection(state.epoch, atlas, openScreen), player);
        sendWorld(player, state);
        if (atlas != null && data != null) {
            // Snapshot transfer size grows with the atlas's explored regions.
            ResourceLocation currentDimension = player.serverLevel().dimension().location();
            Map<ResourceLocation, Map<Long, BitSet>> dimensions = data.dimensions();
            Map<Long, BitSet> currentRegions = dimensions.get(currentDimension);
            if (currentRegions != null) {
                ChunkPos playerChunk = player.chunkPosition();
                int playerRegionX = playerChunk.x >> 5;
                int playerRegionZ = playerChunk.z >> 5;
                List<Map.Entry<Long, BitSet>> orderedRegions = new ArrayList<>(currentRegions.entrySet());
                orderedRegions.sort((left, right) -> Long.compare(
                        regionDistance(left.getKey(), playerRegionX, playerRegionZ),
                        regionDistance(right.getKey(), playerRegionX, playerRegionZ)));
                orderedRegions.forEach(entry -> sendRegion(player, state, currentDimension, entry.getKey(), entry.getValue()));
            }
            dimensions.forEach((dimension, regions) -> {
                if (dimension.equals(currentDimension)) return;
                regions.forEach((region, bits) -> sendRegion(player, state, dimension, region, bits));
            });
            data.markers().forEach((dimension, markers) -> markers.values().forEach(marker ->
                    RosettaNetwork.sendToPlayer(new AtlasPackets.Marker(state.epoch, dimension, null, marker), player)));
        }
        RosettaNetwork.sendToPlayer(new AtlasPackets.Ready(state.epoch), player);
    }

    private static void sendWorld(ServerPlayer player, ClientState state) {
        RosettaNetwork.sendToPlayer(new AtlasMapPackets.World(state.epoch,
                AtlasMapBackend.worldId(player.getServer())), player);
    }

    public static boolean mapRequestValid(ServerPlayer player, long epoch) {
        ClientState state = CLIENTS.get(player.getUUID());
        return state != null && state.epoch == epoch && state.atlas != null
                && state.atlas.equals(AtlasManager.selected(player));
    }

    private static long regionDistance(long region, int playerRegionX, int playerRegionZ) {
        return Math.abs((long) ChunkPos.getX(region) - playerRegionX)
                + Math.abs((long) ChunkPos.getZ(region) - playerRegionZ);
    }

    private static void sendRegion(
            ServerPlayer player, ClientState state, ResourceLocation dimension, long region, BitSet bits) {
        RosettaNetwork.sendToPlayer(
                new AtlasPackets.Region(state.epoch, dimension, region, bits.toLongArray()), player);
    }

    private static void openPacket(AtlasPackets.Open packet, ServerPlayer player) {
        open(player, packet.slot());
    }

    private static void closePacket(AtlasPackets.Close packet, ServerPlayer player) {
        AtlasManager.close(player);
    }

    private static void markerEditPacket(AtlasPackets.MarkerEdit packet, ServerPlayer player) {
        editMarker(player, packet);
    }

    private static void teleportPacket(AtlasPackets.Teleport packet, ServerPlayer serverPlayer) {
        ClientState state = CLIENTS.get(serverPlayer.getUUID());
        if (state == null || state.epoch != packet.epoch() || state.atlas == null
                || !state.atlas.equals(AtlasManager.editingAtlas(serverPlayer))) return;
        var teleport = serverPlayer.getServer().getCommands().getDispatcher().getRoot().getChild("teleport");
        if (teleport == null || !teleport.canUse(serverPlayer.createCommandSourceStack())) {
            serverPlayer.displayClientMessage(Component.translatable("message.atlasbound.teleport_denied"), false);
            return;
        }

        long now = System.nanoTime();
        Long previous = LAST_TELEPORT_NANOS.get(serverPlayer.getUUID());
        if (previous != null && now - previous < 500_000_000L) return;
        LAST_TELEPORT_NANOS.put(serverPlayer.getUUID(), now);

        ServerLevel level = serverPlayer.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, packet.dimension()));
        BlockPos requested = new BlockPos(packet.x(), 0, packet.z());
        if (level == null || !Level.isInSpawnableBounds(requested)
                || !level.getWorldBorder().isWithinBounds(new Vec3(packet.x() + 0.5, 0, packet.z() + 0.5))) {
            serverPlayer.displayClientMessage(Component.translatable("message.atlasbound.teleport_unsafe"), false);
            return;
        }

        LevelChunk chunk = level.getChunk(packet.x() >> 4, packet.z() >> 4);
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, packet.x() & 15, packet.z() & 15) + 1;
        if (!safeLanding(serverPlayer, level, packet.x(), y, packet.z())) {
            serverPlayer.displayClientMessage(Component.translatable("message.atlasbound.teleport_unsafe"), false);
            return;
        }
        serverPlayer.teleportTo(level, packet.x() + 0.5, y, packet.z() + 0.5,
                Set.of(), serverPlayer.getYRot(), serverPlayer.getXRot());
    }

    private static boolean safeLanding(ServerPlayer player, ServerLevel level, int x, int y, int z) {
        int logicalMax = level.getMinBuildHeight() + level.dimensionType().logicalHeight();
        if (y <= level.getMinBuildHeight() || y + 1 >= level.getMaxBuildHeight() || y + 1 >= logicalMax) return false;
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = feet.above();
        BlockPos floor = feet.below();
        BlockState floorState = level.getBlockState(floor);
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        if (!floorState.isFaceSturdy(level, floor, Direction.UP)
                || !floorState.getFluidState().isEmpty()
                || !feetState.getFluidState().isEmpty()
                || !headState.getFluidState().isEmpty()
                || player.getType().isBlockDangerous(floorState)
                || player.getType().isBlockDangerous(feetState)
                || player.getType().isBlockDangerous(headState)) return false;
        double dx = x + 0.5 - player.getX();
        double dy = y - player.getY();
        double dz = z + 0.5 - player.getZ();
        AABB destination = player.getBoundingBox().move(dx, dy, dz);
        return level.noCollision(player, destination);
    }

    private static void editMarker(ServerPlayer player, AtlasPackets.MarkerEdit packet) {
        UUID atlas = AtlasManager.editingAtlas(player);
        ClientState state = CLIENTS.get(player.getUUID());
        long now = System.nanoTime();
        Long previousEdit = LAST_MARKER_EDIT_NANOS.get(player.getUUID());
        if (atlas == null || state == null || !atlas.equals(state.atlas) || packet.epoch() != state.epoch
                || previousEdit != null && now - previousEdit < 100_000_000L
                || packet.marker() == null && packet.previous() == null
                || player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, packet.dimension())) == null)
            return;

        AtlasData data;
        try {
            data = AtlasStorage.get(player.getServer(), atlas, player.getUUID());
        } catch (IllegalStateException exception) {
            Atlasbound.LOGGER.debug("Rejected marker edit for invalid atlas {}", atlas);
            return;
        }
        LAST_MARKER_EDIT_NANOS.put(player.getUUID(), now);
        if (!data.updateMarker(packet.dimension(), packet.previous(), packet.marker())) {
            Atlasbound.LOGGER.debug("Rejected stale marker edit for atlas {} in {}", atlas, packet.dimension());
            return;
        }

        Atlasbound.LOGGER.debug("Accepted marker edit for atlas {} in {}", atlas, packet.dimension());
        for (ServerPlayer viewer : player.getServer().getPlayerList().getPlayers()) {
            if (!atlas.equals(AtlasManager.selected(viewer))) continue;
            ClientState viewerState = CLIENTS.get(viewer.getUUID());
            if (viewerState != null && atlas.equals(viewerState.atlas))
                RosettaNetwork.sendToPlayer(new AtlasPackets.Marker(viewerState.epoch,
                        packet.dimension(), packet.previous(), packet.marker()), viewer);
        }
    }

    private static final class ClientState {
        private long epoch;
        private UUID atlas;
        private Set<UUID> lastCarried;
    }
}
