package net.syrupstudios.atlasbound.map;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/** Marker data used by the map and marker editor. */
public record AtlasLandmark(
        UUID owner,
        ResourceLocation id,
        BlockPos pos,
        Set<ChunkPos> chunks,
        Component name,
        List<Component> lore,
        int color,
        ItemStack stack,
        long time,
        int seed) {
    public static final UUID STRUCTURE_OWNER = new UUID(0L, 0L);

    public AtlasLandmark {
        if (owner == null || id == null) throw new IllegalArgumentException("Invalid atlas landmark");
        pos = pos == null ? null : pos.immutable();
        chunks = chunks == null ? Set.of() : Set.copyOf(chunks);
        lore = lore == null ? List.of() : List.copyOf(lore);
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Override public ItemStack stack() { return stack.copy(); }
}
