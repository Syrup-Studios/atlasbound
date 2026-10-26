package net.syrupstudios.atlasbound.client;

import net.syrupstudios.atlasbound.Atlasbound;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import folk.sisby.antique_atlas.MarkerTexture;
import folk.sisby.antique_atlas.TileTexture;
import folk.sisby.antique_atlas.TileElevation;
import folk.sisby.antique_atlas.TerrainTileProvider;
import folk.sisby.antique_atlas.WorldAtlasData;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.syrupstudios.atlasbound.AtlasMarker;
import net.syrupstudios.atlasbound.map.AtlasLandmark;
import net.syrupstudios.atlasbound.map.AtlasMapData;
import net.syrupstudios.atlasbound.map.AtlasStructureStart;
import net.syrupstudios.atlasbound.map.AtlasTerrainChunk;
import net.rasanovum.mapfoundation.map.ChunkDataClient;

public final class AtlasMapView extends WorldAtlasData {
    private final ResourceLocation dimension;
    private final ResourceKey<Level> dimensionKey;
    private final UUID owner;
    private UUID worldIdentity;
    private final Map<Long, java.util.BitSet> regionMasks = new HashMap<>();
    private final java.util.List<Long> regionOrder = new java.util.ArrayList<>();
    private final Map<ChunkPos, Long> inputRevisions = new HashMap<>();
    private final Map<ChunkPos, Long> structureRevisions = new HashMap<>();
    private final Map<Long, Long> structureRegionRevisions = new HashMap<>();
    private final java.util.LinkedHashSet<ChunkPos> renderPriority = new java.util.LinkedHashSet<>();
    private final Map<ChunkPos, AtlasTileCache.Entry> cachedTiles = new HashMap<>();
    private static final int MAX_RENDER_PRIORITY = 256;
    private int regionCursor;
    private int bitCursor;
    private long cacheRequest;
    private UUID cacheWorld;
    private String cacheFingerprint;
    private int cacheGeneration = -1;
    private boolean cacheLoadPending;
    private boolean cacheLoaded;
    private boolean cacheDirty;

    AtlasMapView(ResourceLocation dimension, UUID owner) {
        this.dimension = dimension;
        this.dimensionKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension);
        this.owner = owner;
        setDimension(dimensionKey);
    }

    ResourceLocation dimensionId() { return dimension; }
    void addRegion(long region, java.util.BitSet bits) {
        java.util.BitSet mask = regionMasks.get(region);
        if (mask == null) {
            mask = new java.util.BitSet(1024);
            regionMasks.put(region, mask);
            regionOrder.add(region);
        }
        mask.or(bits);
    }
    @Override
    public void acceptTerrain(AtlasTerrainChunk chunk, RegistryAccess registries) {
        if (hasWorldData() && AtlasClientState.allows(dimension, chunk.pos()))
            super.acceptTerrain(chunk, registries);
    }

    @Override
    public void acceptStructure(AtlasStructureStart structure, RegistryAccess registries) {
        if (hasWorldData() && AtlasClientState.allows(dimension, structure.chunk()))
            folk.sisby.antique_atlas.reloader.StructureTileProviders.getInstance().resolve(
                    structureTiles, debugStructures, debugStructurePredicates, structureMarkers, structure, registries);
    }

    @Override
    public void tick(RegistryAccess registries) {
        if (!AtlasClientState.owns(owner) || registries == null || regionMasks.isEmpty()) return;
        UUID currentWorld = ChunkDataClient.worldIdentity(dimensionKey);
        if (currentWorld == null) return;
        if (!currentWorld.equals(worldIdentity)) {
            if (cacheDirty && cacheFingerprint != null && !saveCache()) return;
            invalidateCacheLoad();
            clearDerivedState();
            worldIdentity = currentWorld;
        }
        loadCache(currentWorld, registries);
        int requested = 0;
        int checked = 0;
        int emptyRegions = 0;
        int limit = Math.max(1, Atlasbound.Client.CONFIG.chunkTickLimit.get());
        var visible = renderPriority.iterator();
        while (requested < limit && visible.hasNext()) {
            processChunk(visible.next(), registries, true);
            visible.remove();
            requested++;
        }
        while (requested < limit && checked < limit * 8 && !regionOrder.isEmpty()) {
            if (regionCursor >= regionOrder.size()) regionCursor = 0;
            long region = regionOrder.get(regionCursor);
            int bit = regionMasks.get(region).nextSetBit(bitCursor);
            if (bit < 0) {
                regionCursor++;
                bitCursor = 0;
                if (++emptyRegions >= regionOrder.size()) break;
                continue;
            }
            emptyRegions = 0;
            bitCursor = bit + 1;
            checked++;
            int x, z;
            try {
                x = Math.addExact(Math.multiplyExact(ChunkPos.getX(region), 32), bit >>> 5);
                z = Math.addExact(Math.multiplyExact(ChunkPos.getZ(region), 32), bit & 31);
            } catch (ArithmeticException outOfRange) {
                continue;
            }
            if (Math.abs((long) x) > 1_875_000L || Math.abs((long) z) > 1_875_000L) continue;
            ChunkPos pos = new ChunkPos(x, z);
            processChunk(pos, registries, false);
            requested++;
        }
        super.tick(registries);
        terrainSnapshots.keySet().removeIf(pos -> !terrainDequeHash.contains(pos));
    }

    @Override public void rebuildTiles(RegistryAccess registries) {
        invalidateCacheLoad();
        super.rebuildTiles(registries);
        terrainSnapshots.clear();
        structureSnapshots.clear();
        inputRevisions.clear();
        structureRevisions.clear();
        structureRegionRevisions.clear();
        terrainDeque.clear();
        terrainDequeHash.clear();
        regionCursor = 0;
        bitCursor = 0;
        renderPriority.clear();
    }

    void resourceReloaded(RegistryAccess registries) {
        saveCache();
        if (registries != null) {
            rebuildTiles(registries);
            return;
        }
        invalidateCacheLoad();
        clearDerivedState();
        worldIdentity = null;
    }

    private void clearDerivedState() {
        biomeTiles.clear();
        structureTiles.clear();
        debugBiomePredicates.clear();
        debugStructurePredicates.clear();
        debugBiomes.clear();
        debugStructures.clear();
        structureMarkers.clear();
        tileScope.set(0, 0, 0, 0);
        terrainSnapshots.clear();
        structureSnapshots.clear();
        inputRevisions.clear();
        structureRevisions.clear();
        structureRegionRevisions.clear();
        terrainDeque.clear();
        terrainDequeHash.clear();
        regionCursor = 0;
        bitCursor = 0;
        renderPriority.clear();
    }

    boolean hasWorldData() {
        return AtlasClientState.owns(owner) && worldIdentity != null
                && worldIdentity.equals(ChunkDataClient.worldIdentity(dimensionKey));
    }

    private void processChunk(ChunkPos pos, RegistryAccess registries, boolean priority) {
        if (!AtlasClientState.allows(dimension, pos)) return;
        long revision = ChunkDataClient.revision(dimensionKey, pos);
        if (revision == 0) return;
        restoreCached(pos, revision);
        long previous = inputRevisions.getOrDefault(pos, 0L);
        boolean changed = revision != previous;
        boolean needTerrain = changed || !biomeTiles.containsKey(pos);
        boolean needStructures = structureRevisions.getOrDefault(pos, 0L) != revision;
        if (!needTerrain && !needStructures) return;
        byte[] raw = priority ? ChunkDataClient.getPriority(dimensionKey, pos) : ChunkDataClient.get(dimensionKey, pos);
        if (raw == null) return;
        try {
            AtlasMapData data = AtlasMapData.decode(raw);
            if (!pos.equals(data.terrain().pos())) return;
            if (changed) {
                if (previous != 0) invalidateTerrain(pos);
                structureRevisions.remove(pos);
                needStructures = structureRevisions.getOrDefault(pos, 0L) != revision;
            }
            if (needTerrain) {
                super.acceptTerrain(data.terrain(), registries);
                cacheDirty = true;
            }
            if (needStructures) {
                long region = net.rasanovum.mapfoundation.map.MapRegionVersion.key(pos.x >> 5, pos.z >> 5);
                Long previousRegionRevision = structureRegionRevisions.put(region, revision);
                if (previousRegionRevision != null && previousRegionRevision != revision) invalidateStructures();
                for (AtlasStructureStart structure : data.structures()) acceptStructure(structure, registries);
                structureRevisions.put(pos, revision);
            }
            inputRevisions.put(pos, revision);
        } catch (RuntimeException ignored) {
            // Invalid or stale chunk data is requested again from Map Foundation.
        }
    }

    private void prioritize(ChunkPos pos) {
        if (!hasWorldData() || !AtlasClientState.allows(dimension, pos)) return;
        long revision = ChunkDataClient.revision(dimensionKey, pos);
        if (revision == 0 || (biomeTiles.containsKey(pos)
                && inputRevisions.getOrDefault(pos, 0L) == revision
                && structureRevisions.getOrDefault(pos, 0L) == revision)) return;
        if (renderPriority.size() >= MAX_RENDER_PRIORITY) return;
        renderPriority.add(pos);
    }

    private void invalidateTerrain(ChunkPos pos) {
        if (biomeTiles.containsKey(pos)) cacheDirty = true;
        biomeTiles.remove(pos);
        debugBiomes.remove(pos);
        debugBiomePredicates.remove(pos);
        terrainSnapshots.remove(pos);
        terrainDeque.remove(pos);
        terrainDequeHash.remove(pos);
    }

    private void loadCache(UUID world, RegistryAccess registries) {
        String fingerprint = AtlasTileCache.fingerprint(registries);
        int generation = AtlasTileCache.generation();
        if (!world.equals(cacheWorld) || !java.util.Objects.equals(fingerprint, cacheFingerprint)
                || generation != cacheGeneration) {
            invalidateCacheLoad();
            cacheWorld = world;
            cacheFingerprint = fingerprint;
            cacheGeneration = generation;
        }
        if (cacheLoaded || cacheLoadPending || fingerprint == null) return;
        long request = ++cacheRequest;
        if (AtlasTileCache.load(world, owner, dimension, fingerprint, entries -> {
            if (request != cacheRequest) return;
            cacheLoadPending = false;
            Minecraft client = Minecraft.getInstance();
            if (generation != AtlasTileCache.generation()
                    || !world.equals(worldIdentity) || !world.equals(ChunkDataClient.worldIdentity(dimensionKey))
                    || !java.util.Objects.equals(fingerprint, AtlasTileCache.fingerprint(client.getConnection() == null
                            ? null : client.getConnection().registryAccess()))
                    || !AtlasClientState.isCurrentOrRetained(this)) return;
            cacheLoaded = true;
            cachedTiles.putAll(entries);
        })) cacheLoadPending = true;
    }

    private void invalidateCacheLoad() {
        cacheRequest++;
        cacheLoadPending = false;
        cacheLoaded = false;
        cachedTiles.clear();
        cacheWorld = null;
        cacheFingerprint = null;
        cacheGeneration = -1;
    }

    private void restoreCached(ChunkPos pos, long revision) {
        AtlasTileCache.Entry entry = cachedTiles.get(pos);
        if (entry == null) return;
        if (revision != entry.revision()) {
            if (revision != 0) cachedTiles.remove(pos);
            return;
        }
        if (!AtlasClientState.owns(owner) || !hasWorldData() || !AtlasClientState.allows(dimension, pos)) return;
        long previous = inputRevisions.getOrDefault(pos, 0L);
        if (biomeTiles.containsKey(pos) && previous == revision) {
            cachedTiles.remove(pos);
            return;
        }
        TerrainTileProvider provider = BiomeTileProviders.getInstance().getTileProvider(entry.provider());
        if (!provider.id().equals(entry.provider())) {
            cachedTiles.remove(pos);
            return;
        }
        TileElevation elevation = null;
        if (entry.elevation() != null) {
            for (TileElevation value : TileElevation.values()) {
                if (value.getName().equals(entry.elevation())) {
                    elevation = value;
                    break;
                }
            }
            if (elevation == null) {
                cachedTiles.remove(pos);
                return;
            }
        }
        try {
            biomeTiles.put(pos, provider.getTexture(pos, elevation));
        } catch (RuntimeException invalidProvider) {
            cachedTiles.remove(pos);
            return;
        }
        debugBiomes.put(pos, provider);
        debugBiomePredicates.put(pos, elevation == null ? null : elevation.getName());
        tileScope.extendTo(pos.x, pos.z);
        inputRevisions.put(pos, revision);
        long region = net.rasanovum.mapfoundation.map.MapRegionVersion.key(pos.x >> 5, pos.z >> 5);
        structureRegionRevisions.putIfAbsent(region, revision);
        terrainSnapshots.remove(pos);
        terrainDeque.remove(pos);
        terrainDequeHash.remove(pos);
        cachedTiles.remove(pos);
    }

    boolean saveCache() {
        if (!cacheDirty) return true;
        if (worldIdentity == null || cacheFingerprint == null) return false;
        Map<ChunkPos, AtlasTileCache.Entry> snapshot = new HashMap<>();
        biomeTiles.forEach((pos, tile) -> {
            TerrainTileProvider provider = debugBiomes.get(pos);
            long revision = inputRevisions.getOrDefault(pos, 0L);
            if (tile != null && provider != null && revision != 0)
                snapshot.put(pos, new AtlasTileCache.Entry(provider.id(), debugBiomePredicates.get(pos), revision));
        });
        if (!AtlasTileCache.save(worldIdentity, owner, dimension, cacheFingerprint, snapshot)) return false;
        cacheDirty = false;
        return true;
    }

    boolean cacheSavePending() {
        return cacheDirty && worldIdentity != null && cacheFingerprint != null;
    }

    private void invalidateStructures() {
        structureTiles.clear();
        debugStructures.clear();
        debugStructurePredicates.clear();
        structureMarkers.clear();
        structureSnapshots.clear();
        structureRevisions.clear();
    }

    void setMarkers(Map<ResourceLocation, AtlasMarker> markers) {
        landmarkMarkers.clear();
        if (!AtlasClientState.owns(owner)) return;
        markers.values().forEach(marker -> super.addLandmark(AtlasClientState.landmark(marker)));
    }

    @Override public boolean deleteLandmark(ResourceKey<Level> dimension, AtlasLandmark landmark) {
        if (!hasWorldData() || !this.dimension.equals(dimension.location())
                || landmark == null || !landmark.owner().equals(AtlasClientState.markerOwner())) return false;
        AtlasMarker marker = AtlasClientState.marker(this.dimension, landmark.id());
        if (marker == null) return false;
        return AtlasClientState.editMarker(this.dimension, marker.id(), null);
    }

    @Override public Map<AtlasLandmark, MarkerTexture> getEditableLandmarks() {
        return hasWorldData() ? super.getEditableLandmarks() : Map.of();
    }

    @Override public Map<AtlasLandmark, MarkerTexture> getAllMarkers(int tileChunks) {
        if (!hasWorldData()) return Map.of();
        Map<AtlasLandmark, MarkerTexture> visible = new HashMap<>(super.getAllMarkers(tileChunks));
        visible.keySet().removeIf(marker -> !isVisible(marker));
        return visible;
    }

    private boolean isVisible(AtlasLandmark marker) {
        if (marker.owner().equals(AtlasClientState.markerOwner())) return true;
        if (marker.pos() == null) return marker.chunks().stream().anyMatch(pos -> AtlasClientState.allows(dimension, pos));
        if (!AtlasClientState.allows(dimension, new ChunkPos(marker.pos()))) return false;
        return marker.chunks().stream().allMatch(pos -> AtlasClientState.allows(dimension, pos));
    }

    @Override
    public TileTexture getTile(ChunkPos pos) {
        prioritize(pos);
        return hasWorldData() && AtlasClientState.allows(dimension, pos) ? super.getTile(pos) : null;
    }

    @Override public ResourceLocation getProvider(ChunkPos pos) {
        prioritize(pos);
        return hasWorldData() && AtlasClientState.allows(dimension, pos) ? super.getProvider(pos) : null;
    }

    @Override public String getTilePredicate(ChunkPos pos) {
        prioritize(pos);
        return hasWorldData() && AtlasClientState.allows(dimension, pos) ? super.getTilePredicate(pos) : null;
    }
}
