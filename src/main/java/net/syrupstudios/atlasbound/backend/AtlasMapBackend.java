package net.syrupstudios.atlasbound.backend;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.rasanovum.mapfoundation.MapFoundation;
import net.rasanovum.mapfoundation.MapSettings;
import net.rasanovum.mapfoundation.map.MapArea;
import net.rasanovum.mapfoundation.map.MapCoverage;
import net.rasanovum.mapfoundation.map.MapRegionVersion;
import net.rasanovum.mapfoundation.map.MapSource;
import net.rasanovum.mapfoundation.map.ServerMaps;
import net.rasanovum.mapfoundation.map.storage.ServerMapManager;
import net.rasanovum.mapfoundation.map.TerrainStreamService;
import net.syrupstudios.atlasbound.AtlasManager;
import net.syrupstudios.atlasbound.AtlasStorage;
import net.syrupstudios.atlasbound.Atlasbound;

/** Exposes atlas-owned regions to Map Foundation and enforces atlas permissions. */
public final class AtlasMapBackend {
    private static final int MAX_SCOPES = MapCoverage.MAX_REGIONS / 4;
    private static final Map<UUID, Scope> SCOPES = new HashMap<>();
    private static boolean initialized;
    private static final MapSource SOURCE = new MapSource() {
        @Override public Collection<MapArea> areas(ServerLevel level) {
            var active = new java.util.HashSet<UUID>();
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers())
                active.add(AtlasManager.selected(player));
            SCOPES.entrySet().removeIf(entry -> !active.contains(entry.getValue().atlas));
            return SCOPES.values().stream()
                    .filter(scope -> active.contains(scope.atlas) && scope.area.dimension().equals(level.dimension()))
                    .map(scope -> scope.area).toList();
        }

        @Override public boolean canView(ServerPlayer player, MapArea area) {
            Scope scope = SCOPES.get(area.scopeId());
            return scope != null && scope.area.equals(area)
                    && scope.atlas.equals(AtlasManager.selected(player)) && owns(player, scope.atlas);
        }

        @Override public boolean canViewChunk(ServerPlayer player, ResourceKey<Level> dimension, ChunkPos chunk) {
            UUID atlas = AtlasManager.selected(player);
            if (atlas == null) return false;
            try {
                return AtlasStorage.get(player.getServer(), atlas).allows(dimension.location(), chunk);
            } catch (IllegalStateException invalidAtlas) {
                return false;
            }
        }
    };

    private AtlasMapBackend() {}

    public static synchronized void initialize() {
        if (initialized) return;
        MapFoundation.configure(new MapSettings("atlasbound", false, 5, List.of()));
        MapFoundation.configureData(new AtlasMapDataProvider());
        TerrainStreamService.setSource(SOURCE);
        MapFoundation.initialize();
        initialized = true;
    }

    public static void queueRegion(ServerPlayer player, ResourceLocation dimension, long region, BitSet explored) {
        if (!player.getServer().isSameThread()) {
            BitSet copy = (BitSet) explored.clone();
            player.getServer().execute(() -> queueRegion(player, dimension, region, copy));
            return;
        }
        if (explored.length() > 1024) return;
        UUID atlas = AtlasManager.selected(player);
        if (atlas == null) return;
        try {
            if (!explored.equals(AtlasStorage.get(player.getServer(), atlas).region(dimension, region))) return;
        } catch (IllegalStateException invalidAtlas) {
            return;
        }
        if (explored.isEmpty()) return;
        int regionX = ChunkPos.getX(region);
        int regionZ = ChunkPos.getZ(region);
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimension);
        UUID id = UUID.nameUUIDFromBytes((atlas + "\n" + dimension + "\n" + region).getBytes(StandardCharsets.UTF_8));
        int minX, minZ;
        try {
            minX = Math.multiplyExact(regionX, 512);
            minZ = Math.multiplyExact(regionZ, 512);
        } catch (IllegalArgumentException | ArithmeticException invalidRegion) {
            Atlasbound.LOGGER.warn("Rejected atlas map region outside supported bounds: {} in {}", region, dimension);
            return;
        }
        MapArea area;
        try {
            area = new MapArea(dimensionKey, id, minX, minZ,
                    Math.addExact(minX, 511), Math.addExact(minZ, 511));
            area.coverage();
        } catch (IllegalArgumentException | ArithmeticException invalidRegion) {
            Atlasbound.LOGGER.warn("Rejected atlas map region outside supported bounds: {} in {}", region, dimension);
            return;
        }
        Scope scope = new Scope(atlas, area);
        if (!SCOPES.containsKey(id) && SCOPES.size() >= MAX_SCOPES) {
            Atlasbound.LOGGER.warn("Atlas map scope limit reached; cannot add region {} in {}", region, dimension);
            return;
        }
        SCOPES.put(id, scope);

        BitSet detail = new BitSet(1024);
        for (int bit = explored.nextSetBit(0); bit >= 0; bit = explored.nextSetBit(bit + 1))
            detail.set((bit & 31) * 32 + (bit >>> 5));
        long key = MapRegionVersion.key(regionX, regionZ);
        ServerLevel level = player.getServer().getLevel(dimensionKey);
        if (level == null) return;
        ServerMapManager.Request request = new ServerMapManager.Request();
        request.coarse.or(detail);
        request.detail.or(detail);
        ServerMaps.getManager(level).request(Map.of(key, request)).whenComplete((unused, error) -> {
            while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
            if (error != null && !(error instanceof CancellationException))
                Atlasbound.LOGGER.error("Could not prepare atlas map region {} in {}", region, dimension, error);
        });
    }

    public static void serverStopped() { SCOPES.clear(); }

    private static boolean owns(ServerPlayer player, UUID atlas) {
        try {
            return AtlasStorage.get(player.getServer(), atlas) != null;
        } catch (IllegalStateException invalidAtlas) {
            return false;
        }
    }

    private record Scope(UUID atlas, MapArea area) {}
}
