package net.syrupstudios.atlasbound.map;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.resources.ResourceLocation;
import net.syrupstudios.atlasbound.mixin.AtlasFeaturePoolElementAccessor;
import net.syrupstudios.atlasbound.mixin.AtlasListPoolElementAccessor;
import net.syrupstudios.atlasbound.mixin.AtlasPoolElementPieceAccessor;
import net.syrupstudios.atlasbound.mixin.AtlasSinglePoolElementAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;

/** Structure data needed by Antique Atlas structure tile rules. */
public record AtlasStructureStart(
        ResourceLocation key,
        ResourceLocation type,
        Set<ResourceLocation> tags,
        ChunkPos chunk,
        BoundingBox bounds,
        List<AtlasStructurePiece> pieces) {
    public AtlasStructureStart {
        if (key == null || type == null || chunk == null || bounds == null || pieces == null) {
            throw new IllegalArgumentException("Invalid atlas structure start");
        }
        bounds = copy(bounds);
        tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        pieces = List.copyOf(pieces);
    }

    @Override public BoundingBox bounds() { return copy(bounds); }

    private static BoundingBox copy(BoundingBox box) {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /** Capture structure starts already attached to a loaded chunk. */
    public static List<AtlasStructureStart> capture(ServerLevel level, LevelChunk chunk) {
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return chunk.getAllStarts().values().stream()
                .filter(StructureStart::isValid)
                .map(start -> captureOne(structures, start))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static AtlasStructureStart captureOne(Registry<Structure> structures, StructureStart start) {
        Structure structure = start.getStructure();
        ResourceLocation key = structures.getKey(structure);
        ResourceLocation type = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        if (key == null || type == null) return null;
        Set<ResourceLocation> tags = structures.wrapAsHolder(structure).tags()
                .map(tag -> tag.location()).collect(Collectors.toCollection(LinkedHashSet::new));
        List<AtlasStructurePiece> pieces = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) capturePiece(piece, pieces);
        return new AtlasStructureStart(key, type, tags, start.getChunkPos(), start.getBoundingBox(), pieces);
    }

    private static void capturePiece(StructurePiece piece, List<AtlasStructurePiece> out) {
        ResourceLocation pieceType = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
        if (pieceType == null) return;
        if (piece instanceof PoolElementStructurePiece poolPiece) {
            StructureTemplateManager manager = ((AtlasPoolElementPieceAccessor) poolPiece).atlasbound$getStructureTemplateManager();
            captureElement(poolPiece.getElement(), pieceType, manager, poolPiece.getPosition(), poolPiece.getRotation(),
                    piece.getBoundingBox(), poolPiece.getJunctions(), out);
        } else {
            out.add(new AtlasStructurePiece(pieceType, piece.getBoundingBox(), null, null, List.of()));
        }
    }

    private static void captureElement(StructurePoolElement element, ResourceLocation pieceType,
                                       StructureTemplateManager manager, BlockPos position, Rotation rotation,
                                       BoundingBox fallbackBounds, List<JigsawJunction> junctions,
                                       List<AtlasStructurePiece> out) {
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : ((AtlasListPoolElementAccessor) list).atlasbound$getElements())
                captureElement(child, pieceType, manager, position, rotation, fallbackBounds, junctions, out);
            return;
        }
        BoundingBox bounds = element.getBoundingBox(manager, position, rotation);
        if (bounds == null) bounds = fallbackBounds;
        ResourceLocation kind = null;
        ResourceLocation id = null;
        if (element instanceof SinglePoolElement single) {
            kind = ResourceLocation.withDefaultNamespace("single");
            id = ((AtlasSinglePoolElementAccessor) single).atlasbound$getTemplate().left().orElse(null);
        } else if (element instanceof FeaturePoolElement feature) {
            kind = ResourceLocation.withDefaultNamespace("feature");
            id = ((AtlasFeaturePoolElementAccessor) feature).atlasbound$getFeature().unwrapKey()
                    .map(key -> key.location()).orElse(null);
        }
        out.add(new AtlasStructurePiece(pieceType, bounds, kind, id,
                junctions.stream().map(junction -> new AtlasJunction(junction.getSourceX(), junction.getSourceZ())).toList()));
    }
}
