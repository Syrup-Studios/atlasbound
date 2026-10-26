package net.syrupstudios.atlasbound.backend;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.map.AtlasMapData;
import net.syrupstudios.atlasbound.map.AtlasStructureStart;
import net.syrupstudios.atlasbound.map.AtlasTerrainChunk;
import net.syrupstudios.atlasbound.mixin.AtlasChunkStorageAccessor;

public final class AtlasMapDataProvider {
    private AtlasMapDataProvider() {}

    public static byte[] captureLive(ServerLevel level, LevelChunk chunk) {
        try {
            return new AtlasMapData(AtlasTerrainChunk.capture(level, chunk), AtlasStructureStart.capture(level, chunk)).encode();
        } catch (RuntimeException error) {
            Atlasbound.LOGGER.warn("Could not encode atlas map data for {} in {}", chunk.getPos(),
                    level.dimension().location(), error);
            return null;
        }
    }

    public static byte[] captureSaved(ServerLevel level, ChunkPos position, CompoundTag tag) {
        try {
            ProtoChunk proto = ChunkSerializer.read(level, level.getPoiManager(),
                    ((AtlasChunkStorageAccessor) level.getChunkSource().chunkMap).atlasbound$getStorageInfo(), position, tag);
            if (!proto.getPersistedStatus().isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.FULL)) return null;
            return captureLive(level, new LevelChunk(level, proto, null));
        } catch (RuntimeException error) {
            Atlasbound.LOGGER.warn("Could not capture atlas map data for {} in {}", position,
                    level.dimension().location(), error);
            return null;
        }
    }
}
