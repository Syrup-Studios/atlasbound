package net.syrupstudios.atlasbound.map;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** One structure piece, including jigsaw data when the piece has it. */
public record AtlasStructurePiece(
        ResourceLocation type,
        BoundingBox bounds,
        ResourceLocation jigsawKind,
        ResourceLocation jigsawId,
        List<AtlasJunction> junctions) {
    public AtlasStructurePiece {
        if (type == null || bounds == null || junctions == null || (jigsawKind == null) != (jigsawId == null)) {
            throw new IllegalArgumentException("Invalid atlas structure piece");
        }
        bounds = new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        junctions = List.copyOf(junctions);
    }

    @Override public BoundingBox bounds() {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }
}
