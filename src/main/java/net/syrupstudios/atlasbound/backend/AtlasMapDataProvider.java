package net.syrupstudios.atlasbound.backend;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.rasanovum.mapfoundation.map.MapDataProvider;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.map.AtlasMapData;
import net.syrupstudios.atlasbound.map.AtlasStructureStart;
import net.syrupstudios.atlasbound.map.AtlasTerrainChunk;
import net.syrupstudios.atlasbound.mixin.AtlasChunkStorageAccessor;

/** Captures Antique Atlas input when Map Foundation requests a chunk record. */
public final class AtlasMapDataProvider implements MapDataProvider {
    @Override public String format() { return "atlas_tiles_v1"; }

    @Override
    public byte[] captureLive(ServerLevel level, LevelChunk chunk) {
        try {
            return encode(level, chunk);
        } catch (RuntimeException error) {
            Atlasbound.LOGGER.warn("Could not capture atlas map data for {} in {}", chunk.getPos(),
                    level.dimension().location(), error);
            return null;
        }
    }

    @Override
    public byte[] captureSaved(ServerLevel level, ChunkPos position, CompoundTag tag) {
        ProtoChunk proto;
        try {
            proto = ChunkSerializer.read(level, level.getPoiManager(),
                    ((AtlasChunkStorageAccessor) level.getChunkSource().chunkMap).atlasbound$getStorageInfo(), position, tag);
        } catch (RuntimeException error) {
            Atlasbound.LOGGER.debug("Could not capture atlas map data from saved chunk {} in {}", position,
                    level.dimension().location(), error);
            return null;
        }
        if (!proto.getPersistedStatus().isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.FULL)) return null;
        try {
            return encode(level, new LevelChunk(level, proto, null));
        } catch (RuntimeException error) {
            Atlasbound.LOGGER.warn("Could not encode atlas map data for {} in {}", position,
                    level.dimension().location(), error);
            return null;
        }
    }

    private static byte[] encode(ServerLevel level, LevelChunk chunk) {
        return new AtlasMapData(AtlasTerrainChunk.capture(level, chunk), AtlasStructureStart.capture(level, chunk)).encode();
    }
}
