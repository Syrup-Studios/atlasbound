package net.syrupstudios.atlasbound.map;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record AtlasMapData(AtlasTerrainChunk terrain, List<AtlasStructureStart> structures) {
    private static final int MAX_RECORD_BYTES = 60 * 1024;
    private static final int MAX_STRUCTURES = 256;
    private static final int MAX_TAGS = 128;
    private static final int MAX_PIECES = 4096;
    private static final int MAX_JUNCTIONS = 256;

    public AtlasMapData {
            if (terrain == null || structures == null || structures.size() > MAX_STRUCTURES)
                throw new IllegalArgumentException("Invalid Atlasbound map data");
            structures = List.copyOf(structures);
        }

    public byte[] encode() {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(4096, MAX_RECORD_BYTES));
            try {
                writeTerrain(buf, terrain);
                writeStructures(buf, structures);
                if (buf.readableBytes() > MAX_RECORD_BYTES)
                    throw new IllegalArgumentException("Atlasbound map data is too large");
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return bytes;
            } finally {
                buf.release();
            }
        }

    public static AtlasMapData decode(byte[] bytes) {
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_RECORD_BYTES)
                throw new IllegalArgumentException("Invalid Atlasbound map data length");
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try {
                AtlasMapData data = new AtlasMapData(readTerrain(buf), readStructures(buf));
                if (buf.isReadable()) throw new IllegalArgumentException("Trailing Atlasbound map data");
                return data;
            } finally {
                buf.release();
            }
        }

        private static void writeTerrain(FriendlyByteBuf buf, AtlasTerrainChunk terrain) {
            buf.writeChunkPos(terrain.pos());
            writePalette(buf, terrain.biomePalette());
            writePalette(buf, terrain.blockPalette());
            writeLayer(buf, terrain.surface());
            buf.writeBoolean(terrain.netherLow() != null);
            if (terrain.netherLow() != null) {
                writeLayer(buf, terrain.netherLow());
                writeLayer(buf, terrain.netherFull());
            }
        }

        private static AtlasTerrainChunk readTerrain(FriendlyByteBuf buf) {
            var pos = buf.readChunkPos();
            List<ResourceLocation> biomes = readPalette(buf);
            List<ResourceLocation> blocks = readPalette(buf);
            AtlasTerrainLayer surface = readLayer(buf, biomes.size(), blocks.size());
            AtlasTerrainLayer low = null;
            AtlasTerrainLayer full = null;
            if (buf.readBoolean()) {
                low = readLayer(buf, biomes.size(), blocks.size());
                full = readLayer(buf, biomes.size(), blocks.size());
            }
            return new AtlasTerrainChunk(pos, biomes, blocks, surface, low, full);
        }

        private static void writePalette(FriendlyByteBuf buf, List<ResourceLocation> palette) {
            requireCount(palette.size(), AtlasTerrainChunk.MAX_PALETTE);
            buf.writeVarInt(palette.size());
            palette.forEach(buf::writeResourceLocation);
        }

        private static List<ResourceLocation> readPalette(FriendlyByteBuf buf) {
            int size = readCount(buf, AtlasTerrainChunk.MAX_PALETTE);
            List<ResourceLocation> palette = new ArrayList<>(size);
            for (int i = 0; i < size; i++) palette.add(buf.readResourceLocation());
            return List.copyOf(palette);
        }

        private static void writeLayer(FriendlyByteBuf buf, AtlasTerrainLayer layer) {
            buf.writeByteArray(layer.exists().toByteArray());
            writeSignedInts(buf, layer.depths());
            writeInts(buf, layer.biomes());
            writeInts(buf, layer.blocks());
            writeInts(buf, layer.waterDepths());
        }

        private static AtlasTerrainLayer readLayer(FriendlyByteBuf buf, int biomeCount, int blockCount) {
            BitSet exists = BitSet.valueOf(buf.readByteArray(32));
            if (exists.length() > 256) throw new IllegalArgumentException("Atlasbound terrain layer exceeds 256 columns");
            int[] depths = readSignedInts(buf);
            int[] biomes = readInts(buf, AtlasTerrainChunk.MAX_PALETTE - 1);
            int[] blocks = readInts(buf, AtlasTerrainChunk.MAX_PALETTE - 1);
            int[] waterDepths = readInts(buf, 8192);
            for (int index = exists.nextSetBit(0); index >= 0; index = exists.nextSetBit(index + 1)) {
                if (biomes[index] >= biomeCount || blocks[index] >= blockCount)
                    throw new IllegalArgumentException("Atlasbound terrain palette index is invalid");
            }
            return new AtlasTerrainLayer(exists, depths, biomes, blocks, waterDepths);
        }

        private static void writeInts(FriendlyByteBuf buf, int[] values) {
            if (values.length != 256) throw new IllegalArgumentException("Atlasbound terrain array must have 256 values");
            for (int value : values) {
                if (value < 0) throw new IllegalArgumentException("Invalid Atlasbound terrain value");
                buf.writeVarInt(value);
            }
        }

        private static void writeSignedInts(FriendlyByteBuf buf, int[] values) {
            if (values.length != 256) throw new IllegalArgumentException("Atlasbound terrain array must have 256 values");
            for (int value : values) buf.writeInt(value);
        }

        private static int[] readInts(FriendlyByteBuf buf, int maximum) {
            int[] values = new int[256];
            for (int i = 0; i < values.length; i++) {
                values[i] = buf.readVarInt();
                if (values[i] < 0 || values[i] > maximum)
                    throw new IllegalArgumentException("Invalid Atlasbound terrain value");
            }
            return values;
        }

        private static int[] readSignedInts(FriendlyByteBuf buf) {
            int[] values = new int[256];
            for (int i = 0; i < values.length; i++) {
                values[i] = buf.readInt();
                if (Math.abs((long) values[i]) > 30_000_000L)
                    throw new IllegalArgumentException("Invalid Atlasbound terrain depth");
            }
            return values;
        }

        private static void writeStructures(FriendlyByteBuf buf, List<AtlasStructureStart> structures) {
            requireCount(structures.size(), MAX_STRUCTURES);
            buf.writeVarInt(structures.size());
            for (AtlasStructureStart start : structures) {
                buf.writeResourceLocation(start.key());
                buf.writeResourceLocation(start.type());
                requireCount(start.tags().size(), MAX_TAGS);
                buf.writeVarInt(start.tags().size());
                start.tags().stream().sorted().forEach(buf::writeResourceLocation);
                buf.writeChunkPos(start.chunk());
                writeBounds(buf, start.bounds());
                requireCount(start.pieces().size(), MAX_PIECES);
                buf.writeVarInt(start.pieces().size());
                for (AtlasStructurePiece piece : start.pieces()) {
                    buf.writeResourceLocation(piece.type());
                    writeBounds(buf, piece.bounds());
                    writeOptionalId(buf, piece.jigsawKind());
                    writeOptionalId(buf, piece.jigsawId());
                    requireCount(piece.junctions().size(), MAX_JUNCTIONS);
                    buf.writeVarInt(piece.junctions().size());
                    for (AtlasJunction junction : piece.junctions()) {
                        buf.writeVarInt(junction.sourceX());
                        buf.writeVarInt(junction.sourceZ());
                    }
                }
            }
        }

        private static List<AtlasStructureStart> readStructures(FriendlyByteBuf buf) {
            int count = readCount(buf, MAX_STRUCTURES);
            List<AtlasStructureStart> structures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ResourceLocation key = buf.readResourceLocation();
                ResourceLocation type = buf.readResourceLocation();
                int tagCount = readCount(buf, MAX_TAGS);
                Set<ResourceLocation> tags = new HashSet<>();
                for (int j = 0; j < tagCount; j++) tags.add(buf.readResourceLocation());
                var chunk = buf.readChunkPos();
                var bounds = readBounds(buf);
                int pieceCount = readCount(buf, MAX_PIECES);
                List<AtlasStructurePiece> pieces = new ArrayList<>(pieceCount);
                for (int j = 0; j < pieceCount; j++) {
                    ResourceLocation pieceType = buf.readResourceLocation();
                    var pieceBounds = readBounds(buf);
                    ResourceLocation jigsawKind = readOptionalId(buf);
                    ResourceLocation jigsawId = readOptionalId(buf);
                    int junctionCount = readCount(buf, MAX_JUNCTIONS);
                    List<AtlasJunction> junctions = new ArrayList<>(junctionCount);
                    for (int k = 0; k < junctionCount; k++)
                        junctions.add(new AtlasJunction(buf.readVarInt(), buf.readVarInt()));
                    pieces.add(new AtlasStructurePiece(pieceType, pieceBounds, jigsawKind, jigsawId, junctions));
                }
                structures.add(new AtlasStructureStart(key, type, tags, chunk, bounds, pieces));
            }
            return List.copyOf(structures);
        }

        private static void writeBounds(FriendlyByteBuf buf, net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {
            buf.writeVarInt(bounds.minX()); buf.writeVarInt(bounds.minY()); buf.writeVarInt(bounds.minZ());
            buf.writeVarInt(bounds.maxX()); buf.writeVarInt(bounds.maxY()); buf.writeVarInt(bounds.maxZ());
        }

        private static net.minecraft.world.level.levelgen.structure.BoundingBox readBounds(FriendlyByteBuf buf) {
            return new net.minecraft.world.level.levelgen.structure.BoundingBox(buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        private static void writeOptionalId(FriendlyByteBuf buf, ResourceLocation id) {
            buf.writeBoolean(id != null);
            if (id != null) buf.writeResourceLocation(id);
        }

        private static ResourceLocation readOptionalId(FriendlyByteBuf buf) {
            return buf.readBoolean() ? buf.readResourceLocation() : null;
        }

        private static int readCount(FriendlyByteBuf buf, int maximum) {
            int count = buf.readVarInt();
            requireCount(count, maximum);
            return count;
        }

        private static void requireCount(int count, int maximum) {
            if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid Atlasbound terrain packet count");
        }
    }

