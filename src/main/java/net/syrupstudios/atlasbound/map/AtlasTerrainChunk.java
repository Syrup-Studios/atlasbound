package net.syrupstudios.atlasbound.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Registry;

/** Registry IDs and the three floor views used by Antique Atlas. */
// Floor sampling follows Surveyor's ChunkSummary behavior (LGPL-3.0-or-later).
public record AtlasTerrainChunk(
        ChunkPos pos,
        List<ResourceLocation> biomePalette,
        List<ResourceLocation> blockPalette,
        AtlasTerrainLayer surface,
        AtlasTerrainLayer netherLow,
        AtlasTerrainLayer netherFull) {
    public static final int MAX_PALETTE = 3 * 256;
    private static final int TILE_HEIGHT = 999;

    public AtlasTerrainChunk {
        if (pos == null || biomePalette == null || blockPalette == null || surface == null) {
            throw new IllegalArgumentException("Invalid atlas terrain chunk");
        }
        pos = new ChunkPos(pos.x, pos.z);
        biomePalette = List.copyOf(biomePalette);
        blockPalette = List.copyOf(blockPalette);
        if (biomePalette.size() > MAX_PALETTE || blockPalette.size() > MAX_PALETTE
                || !valid(surface, biomePalette.size(), blockPalette.size())
                || (netherLow != null && (!valid(netherLow, biomePalette.size(), blockPalette.size())
                || !valid(netherFull, biomePalette.size(), blockPalette.size())))) {
            throw new IllegalArgumentException("Terrain palette index is out of range");
        }
        if ((netherLow == null) != (netherFull == null)) {
            throw new IllegalArgumentException("Nether projections must be supplied together");
        }
    }

    private static boolean valid(AtlasTerrainLayer layer, int biomeCount, int blockCount) {
        if (layer == null) return false;
        java.util.BitSet exists = layer.exists();
        int[] biomes = layer.biomes();
        int[] blocks = layer.blocks();
        int[] waterDepths = layer.waterDepths();
        for (int index = exists.nextSetBit(0); index >= 0; index = exists.nextSetBit(index + 1)) {
            if (biomes[index] < 0 || biomes[index] >= biomeCount || blocks[index] < 0 || blocks[index] >= blockCount
                    || waterDepths[index] < 0) return false;
        }
        return true;
    }

    /** Capture raw map-colour floors from a loaded or detached chunk. */
    public static AtlasTerrainChunk capture(ServerLevel level, LevelChunk chunk) {
        if (level == null || chunk == null) throw new IllegalArgumentException("Missing atlas terrain source");
        return capture(level.registryAccess(), level.dimension(), level.dimensionType(), level.getSeaLevel(), chunk);
    }

    /** Capture using only the chunk and saved dimension metadata. */
    public static AtlasTerrainChunk capture(RegistryAccess registries, ResourceKey<Level> dimension,
                                            DimensionType dimensionType, int seaLevel, LevelChunk chunk) {
        if (registries == null || dimension == null || dimensionType == null || chunk == null)
            throw new IllegalArgumentException("Missing atlas terrain source");
        boolean nether = dimension.equals(Level.NETHER);
        Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
        Capture capture = new Capture(chunk, biomes, dimension, dimensionType, seaLevel);
        AtlasTerrainLayer[] projections = capture.scan(nether);
        AtlasTerrainLayer surface = projections[0];
        AtlasTerrainLayer low = nether ? projections[1] : null;
        AtlasTerrainLayer full = nether ? projections[2] : null;
        return new AtlasTerrainChunk(chunk.getPos(), capture.biomePalette, capture.blockPalette, surface, low, full);
    }

    private static final class Capture {
        private final LevelChunk chunk;
        private final Registry<Biome> biomeRegistry;
        private final ResourceKey<Level> dimension;
        private final DimensionType dimensionType;
        private final int seaLevel;
        private final List<ResourceLocation> biomePalette = new ArrayList<>();
        private final List<ResourceLocation> blockPalette = new ArrayList<>();
        private final Map<ResourceLocation, Integer> biomeIndices = new HashMap<>();
        private final Map<ResourceLocation, Integer> blockIndices = new HashMap<>();
        private final int minY;
        private final int maxY;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        private Capture(LevelChunk chunk, Registry<Biome> biomeRegistry, ResourceKey<Level> dimension,
                        DimensionType dimensionType, int seaLevel) {
            this.chunk = chunk;
            this.biomeRegistry = biomeRegistry;
            this.dimension = dimension;
            this.dimensionType = dimensionType;
            this.seaLevel = seaLevel;
            this.minY = dimensionType.minY();
            this.maxY = dimensionType.minY() + dimensionType.height() - 1;
        }

        private AtlasTerrainLayer[] scan(boolean nether) {
            LayerBuilder surface = new LayerBuilder();
            LayerBuilder low = nether ? new LayerBuilder() : null;
            LayerBuilder full = nether ? new LayerBuilder() : null;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = x * 16 + z;
                    captureColumn(x, z, index, surface, low, full);
                }
            }
            return new AtlasTerrainLayer[] { surface.build(), low == null ? null : low.build(), full == null ? null : full.build() };
        }

        private void captureColumn(int x, int z, int index, LayerBuilder surface, LayerBuilder low, LayerBuilder full) {
            int walkspace = 2;
            int waterDepth = 0;
            int carpetY = Integer.MAX_VALUE;
            Block carpetBlock = null;
            int[] boundaries = sampleBoundaries();
            for (int layer = 0; layer < boundaries.length - 1; layer++) {
                Floor found = null;
                for (int y = boundaries[layer]; y > boundaries[layer + 1]; y--) {
                    cursor.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                    BlockState state = chunk.getBlockState(cursor);
                    Fluid fluid = state.getFluidState().getType();
                    MapColor mapColor = state.getMapColor(chunk, cursor);
                    if (!state.blocksMotion() && fluid == Fluids.EMPTY) {
                        walkspace++;
                        waterDepth = 0;
                        if (walkspace >= 2 && mapColor != MapColor.NONE) {
                            carpetY = y;
                            carpetBlock = state.getBlock();
                        }
                    } else if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
                        waterDepth++;
                    } else {
                        if (found == null) {
                            if (carpetY == y + 1) {
                                Floor carpetFloor = new Floor(carpetY, carpetBlock, waterDepth);
                                if (carpetY > boundaries[layer]) assign(index, carpetFloor, surface, low, full);
                                else found = carpetFloor;
                                walkspace = 0;
                                waterDepth = 0;
                            } else if (walkspace >= 2 && mapColor != MapColor.NONE) {
                                found = new Floor(y, state.getBlock(), waterDepth);
                                walkspace = 0;
                                waterDepth = 0;
                            }
                        }
                        if (mapColor != MapColor.NONE) {
                            walkspace = 0;
                            waterDepth = 0;
                        }
                    }
                }
                if (found != null) {
                    assign(index, found, surface, low, full);
                }
            }
        }

        private void assign(int index, Floor floor, LayerBuilder surface, LayerBuilder low, LayerBuilder full) {
            if (!surface.exists.get(index)) surface.set(index, floor);
            if (floor.y() <= 50 && low != null && !low.exists.get(index)) low.set(index, floor);
            if (floor.y() <= 126 && full != null && !full.exists.get(index)) full.set(index, floor);
        }

        private int[] sampleBoundaries() {
            Set<Integer> boundaries = new LinkedHashSet<>();
            boundaries.add(maxY);
            if (dimensionType.logicalHeight() != dimensionType.height()) {
                addBoundary(boundaries, dimensionType.minY() + dimensionType.logicalHeight() - 2);
            }
            if (dimensionType.minY() + dimensionType.height() > 256) addBoundary(boundaries, 256);
            if (dimensionType.hasSkyLight()) addBoundary(boundaries, seaLevel - 2);
            addBoundary(boundaries, 0);
            if (dimension.equals(Level.NETHER)) {
                addBoundary(boundaries, 70);
                addBoundary(boundaries, 40);
            }
            boundaries.add(minY);
            return boundaries.stream().sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
        }

        private void addBoundary(Set<Integer> boundaries, int y) {
            if (dimensionType.minY() < y && y < dimensionType.minY() + dimensionType.height()) boundaries.add(y);
        }

        private static int paletteIndex(List<ResourceLocation> palette, Map<ResourceLocation, Integer> indices,
                                        ResourceLocation id) {
            return indices.computeIfAbsent(id, key -> {
                palette.add(key);
                return palette.size() - 1;
            });
        }

        private record Floor(int y, Block block, int waterDepth) {}

        private final class LayerBuilder {
            private final java.util.BitSet exists = new java.util.BitSet(256);
            private final int[] depths = new int[256];
            private final int[] biomes = new int[256];
            private final int[] blocks = new int[256];
            private final int[] waterDepths = new int[256];

            private void set(int index, Floor floor) {
                int x = index >> 4;
                int z = index & 15;
                cursor.set(chunk.getPos().getMinBlockX() + x, floor.y(), chunk.getPos().getMinBlockZ() + z);
                ResourceLocation biomeId = biomeRegistry.getKey(chunk.getNoiseBiome(cursor.getX() >> 2, cursor.getY() >> 2, cursor.getZ() >> 2).value());
                ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(floor.block());
                if (biomeId == null || blockId == null) return;
                exists.set(index);
                depths[index] = TILE_HEIGHT - floor.y();
                biomes[index] = paletteIndex(biomePalette, biomeIndices, biomeId);
                blocks[index] = paletteIndex(blockPalette, blockIndices, blockId);
                waterDepths[index] = floor.waterDepth();
            }

            private AtlasTerrainLayer build() {
                return new AtlasTerrainLayer(exists, depths, biomes, blocks, waterDepths);
            }
        }
    }
}
