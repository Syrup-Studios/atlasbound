package net.syrupstudios.atlasbound.mixin;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkStorage.class)
public interface AtlasChunkStorageAccessor {
    @Invoker("storageInfo")
    RegionStorageInfo atlasbound$getStorageInfo();
}
