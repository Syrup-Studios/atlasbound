package net.syrupstudios.atlasbound.map;

import java.util.BitSet;

/** One 256-column floor projection used to make atlas tiles. */
public record AtlasTerrainLayer(BitSet exists, int[] depths, int[] biomes, int[] blocks, int[] waterDepths) {
    public AtlasTerrainLayer {
        if (exists == null || depths == null || biomes == null || blocks == null || waterDepths == null
                || exists.length() > 256 || depths.length != 256 || biomes.length != 256
                || blocks.length != 256 || waterDepths.length != 256) {
            throw new IllegalArgumentException("Invalid atlas terrain layer");
        }
        exists = (BitSet) exists.clone();
        depths = depths.clone();
        biomes = biomes.clone();
        blocks = blocks.clone();
        waterDepths = waterDepths.clone();
    }

    @Override public BitSet exists() { return (BitSet) exists.clone(); }
    @Override public int[] depths() { return depths.clone(); }
    @Override public int[] biomes() { return biomes.clone(); }
    @Override public int[] blocks() { return blocks.clone(); }
    @Override public int[] waterDepths() { return waterDepths.clone(); }
}
