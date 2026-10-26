package net.syrupstudios.atlasbound;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.rasanovum.rosetta.nbt.NbtCompat;

/** Server-owned exploration data, keyed by atlas identity. */
public final class AtlasData extends SavedData {
    private static final int SCHEMA = 2;
    private static final int REGION_BITS = 1024;
    private final Map<ResourceLocation, Map<Long, BitSet>> explored = new HashMap<>();
    private final Map<ResourceLocation, Map<ResourceLocation, AtlasMarker>> markers = new HashMap<>();

    public static final Factory<AtlasData> FACTORY = new Factory<>(AtlasData::new, AtlasData::load, null);

    public AtlasData() {}

    public boolean record(ResourceLocation dimension, ChunkPos chunk) {
        long region = ChunkPos.asLong(chunk.x >> 5, chunk.z >> 5);
        int index = ((chunk.x & 31) << 5) + (chunk.z & 31);
        BitSet bits = explored.computeIfAbsent(dimension, ignored -> new HashMap<>())
                .computeIfAbsent(region, ignored -> new BitSet(REGION_BITS));
        if (bits.get(index)) return false;
        bits.set(index);
        setDirty();
        return true;
    }

    public boolean allows(ResourceLocation dimension, ChunkPos chunk) {
        long region = ChunkPos.asLong(chunk.x >> 5, chunk.z >> 5);
        int index = ((chunk.x & 31) << 5) + (chunk.z & 31);
        BitSet bits = explored.getOrDefault(dimension, Map.of()).get(region);
        return bits != null && bits.get(index);
    }

    public BitSet region(ResourceLocation dimension, long region) {
        BitSet bits = explored.getOrDefault(dimension, Map.of()).get(region);
        return bits == null ? new BitSet(REGION_BITS) : (BitSet) bits.clone();
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("schema", SCHEMA);
        CompoundTag dimensions = new CompoundTag();
        explored.forEach((dimension, regions) -> {
            CompoundTag regionTags = new CompoundTag();
            regions.forEach((region, bits) -> regionTags.putByteArray(Long.toString(region), bits.toByteArray()));
            dimensions.put(dimension.toString(), regionTags);
        });
        root.put("dimensions", dimensions);
        CompoundTag markerDimensions = new CompoundTag();
        markers.forEach((dimension, entries) -> {
            CompoundTag markerTags = new CompoundTag();
            entries.forEach((id, marker) -> markerTags.put(id.toString(), marker.save()));
            markerDimensions.put(dimension.toString(), markerTags);
        });
        root.put("markers", markerDimensions);
        return root;
    }

    private static AtlasData load(CompoundTag root, HolderLookup.Provider registries) {
        if (!root.contains("schema", CompoundTag.TAG_INT) || !root.contains("dimensions", CompoundTag.TAG_COMPOUND)) {
            throw new IllegalStateException("Invalid Atlasbound saved data root; refusing to replace it");
        }
        int schema = NbtCompat.getInt(root, "schema", 0);
        if (schema != 1 && schema != SCHEMA)
            throw new IllegalStateException("Invalid Atlasbound saved data root; refusing to replace it");
        AtlasData data = new AtlasData();
        CompoundTag dimensions = NbtCompat.getCompound(root, "dimensions");
        for (String dimensionKey : NbtCompat.getAllKeys(dimensions)) {
            ResourceLocation dimension = ResourceLocation.tryParse(dimensionKey);
            if (dimension == null || !dimensions.contains(dimensionKey, CompoundTag.TAG_COMPOUND))
                throw new IllegalStateException("Invalid Atlasbound dimension record: " + dimensionKey);
            CompoundTag regions = NbtCompat.getCompound(dimensions, dimensionKey);
            Map<Long, BitSet> validRegions = new HashMap<>();
            for (String regionKey : NbtCompat.getAllKeys(regions)) {
                try {
                    long region = Long.parseLong(regionKey);
                    if (!Long.toString(region).equals(regionKey))
                        throw new IllegalStateException("Non-canonical Atlasbound region key: " + regionKey);
                    if (!regions.contains(regionKey, CompoundTag.TAG_BYTE_ARRAY))
                        throw new IllegalStateException("Invalid Atlasbound region data: " + regionKey);
                    byte[] bytes = NbtCompat.getByteArray(regions, regionKey);
                    if (bytes.length > REGION_BITS / 8) throw new IllegalStateException("Atlasbound region exceeds 1024 bits: " + regionKey);
                    validRegions.put(region, BitSet.valueOf(bytes));
                } catch (NumberFormatException ignored) {
                    throw new IllegalStateException("Invalid Atlasbound region key: " + regionKey, ignored);
                }
            }
            if (!validRegions.isEmpty()) data.explored.put(dimension, validRegions);
        }
        if (schema == SCHEMA) {
            if (!root.contains("markers", CompoundTag.TAG_COMPOUND))
                throw new IllegalStateException("Invalid Atlasbound markers root");
            CompoundTag markerDimensions = NbtCompat.getCompound(root, "markers");
            int markerCount = 0;
            for (String dimensionKey : NbtCompat.getAllKeys(markerDimensions)) {
                ResourceLocation dimension = ResourceLocation.tryParse(dimensionKey);
                if (dimension == null || !markerDimensions.contains(dimensionKey, CompoundTag.TAG_COMPOUND))
                    throw new IllegalStateException("Invalid Atlasbound marker dimension: " + dimensionKey);
                CompoundTag markerTags = NbtCompat.getCompound(markerDimensions, dimensionKey);
                Map<ResourceLocation, AtlasMarker> entries = new HashMap<>();
                for (String idKey : NbtCompat.getAllKeys(markerTags)) {
                    ResourceLocation id = ResourceLocation.tryParse(idKey);
                    if (id == null || !markerTags.contains(idKey, CompoundTag.TAG_COMPOUND))
                        throw new IllegalStateException("Invalid Atlasbound marker record: " + idKey);
                    AtlasMarker marker = AtlasMarker.load(NbtCompat.getCompound(markerTags, idKey));
                    if (!marker.id().equals(id) || entries.put(id, marker) != null)
                        throw new IllegalStateException("Mismatched Atlasbound marker id: " + idKey);
                    if (++markerCount > AtlasMarker.MAX_MARKERS)
                        throw new IllegalStateException("Atlasbound marker limit exceeded");
                }
                if (!entries.isEmpty()) data.markers.put(dimension, entries);
            }
        }
        int regionCount = data.explored.values().stream().mapToInt(Map::size).sum();
        Atlasbound.LOGGER.debug("Loaded atlas data: {} dimensions, {} regions", data.explored.size(), regionCount);
        return data;
    }

    public Map<ResourceLocation, Map<Long, BitSet>> dimensions() {
        Map<ResourceLocation, Map<Long, BitSet>> copy = new HashMap<>();
        explored.forEach((dimension, regions) -> {
            Map<Long, BitSet> regionCopy = new HashMap<>();
            regions.forEach((key, bits) -> regionCopy.put(key, (BitSet) bits.clone()));
            copy.put(dimension, Map.copyOf(regionCopy));
        });
        return Map.copyOf(copy);
    }

    public Map<ResourceLocation, Map<ResourceLocation, AtlasMarker>> markers() {
        Map<ResourceLocation, Map<ResourceLocation, AtlasMarker>> copy = new HashMap<>();
        markers.forEach((dimension, entries) -> copy.put(dimension, Map.copyOf(entries)));
        return Map.copyOf(copy);
    }

    public boolean updateMarker(ResourceLocation dimension, ResourceLocation previousId, AtlasMarker marker) {
        Map<ResourceLocation, AtlasMarker> entries = markers.getOrDefault(dimension, Map.of());
        if (previousId == null) {
            if (marker == null || entries.containsKey(marker.id()) || markerCount() >= AtlasMarker.MAX_MARKERS) return false;
        } else if (!entries.containsKey(previousId) || (marker != null && !previousId.equals(marker.id()) && entries.containsKey(marker.id()))) {
            return false;
        }
        if (marker != null && previousId != null && previousId.equals(marker.id())
                && marker.equals(entries.get(previousId))) return false;
        Map<ResourceLocation, AtlasMarker> mutable = markers.computeIfAbsent(dimension, ignored -> new HashMap<>());
        if (previousId != null) mutable.remove(previousId);
        if (marker != null) mutable.put(marker.id(), marker);
        if (mutable.isEmpty()) markers.remove(dimension);
        setDirty();
        return true;
    }

    private int markerCount() {
        return markers.values().stream().mapToInt(Map::size).sum();
    }
}
