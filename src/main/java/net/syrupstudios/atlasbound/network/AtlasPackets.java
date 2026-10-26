package net.syrupstudios.atlasbound.network;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.syrupstudios.atlasbound.AtlasMarker;
import net.rasanovum.rosetta.network.RosettaPacket;

public final class AtlasPackets {
    private AtlasPackets() {}

    public record Open(int slot) implements RosettaPacket {
        public Open(FriendlyByteBuf buf) { this(buf.readVarInt()); }
        public void write(FriendlyByteBuf buf) { buf.writeVarInt(slot); }
    }

    public record Close() implements RosettaPacket {
        public Close(FriendlyByteBuf buf) { this(); }
        public void write(FriendlyByteBuf buf) {}
    }

    public record Selection(long epoch, UUID atlas, boolean openScreen) implements RosettaPacket {
        public Selection(FriendlyByteBuf buf) {
            this(buf.readVarLong(), buf.readBoolean() ? buf.readUUID() : null, buf.readBoolean());
        }
        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(epoch);
            buf.writeBoolean(atlas != null);
            if (atlas != null) buf.writeUUID(atlas);
            buf.writeBoolean(openScreen);
        }
    }

    public record Region(long epoch, ResourceLocation dimension, long region, long[] bits) implements RosettaPacket {
        public Region(FriendlyByteBuf buf) {
            this(buf.readVarLong(), buf.readResourceLocation(), buf.readLong(), readBits(buf));
        }
        public void write(FriendlyByteBuf buf) {
            if (bits.length > 16) throw new IllegalArgumentException("Atlasbound region exceeds 1024 bits");
            buf.writeVarLong(epoch);
            buf.writeResourceLocation(dimension);
            buf.writeLong(region);
            buf.writeByte(bits.length);
            for (long word : bits) buf.writeLong(word);
        }
        private static long[] readBits(FriendlyByteBuf buf) {
            int count = buf.readUnsignedByte();
            if (count > 16) throw new IllegalArgumentException("Atlasbound region exceeds 1024 bits");
            long[] bits = new long[count];
            for (int i = 0; i < count; i++) bits[i] = buf.readLong();
            if (BitSet.valueOf(bits).length() > 1024)
                throw new IllegalArgumentException("Atlasbound region exceeds 1024 bits");
            return bits;
        }
    }

    public record Ready(long epoch) implements RosettaPacket {
        public Ready(FriendlyByteBuf buf) { this(buf.readVarLong()); }
        public void write(FriendlyByteBuf buf) { buf.writeVarLong(epoch); }
    }

    public record Carried(Set<UUID> atlases) implements RosettaPacket {
        public Carried(FriendlyByteBuf buf) {
            this(readAtlases(buf));
        }

        public Carried {
            atlases = Set.copyOf(atlases);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(atlases.size());
            atlases.forEach(buf::writeUUID);
        }

        private static Set<UUID> readAtlases(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > buf.readableBytes() / 16)
                throw new IllegalArgumentException("Invalid carried atlas count");
            Set<UUID> atlases = new HashSet<>(count);
            for (int i = 0; i < count; i++) atlases.add(buf.readUUID());
            return atlases;
        }
    }

    public record MarkerEdit(long epoch, ResourceLocation dimension, ResourceLocation previous, AtlasMarker marker)
            implements RosettaPacket {
        public MarkerEdit(FriendlyByteBuf buf) {
            this(readMarkerChange(buf));
        }
        private MarkerEdit(MarkerChange change) { this(change.epoch, change.dimension, change.removed, change.marker); }
        public void write(FriendlyByteBuf buf) { writeMarkerChange(buf, epoch, dimension, previous, marker); }
        public MarkerEdit { requireMarkerChange(previous, marker); }
    }

    public record Teleport(long epoch, ResourceLocation dimension, int x, int z) implements RosettaPacket {
        public Teleport(FriendlyByteBuf buf) {
            this(buf.readVarLong(), buf.readResourceLocation(), buf.readInt(), buf.readInt());
        }
        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(epoch);
            buf.writeResourceLocation(dimension);
            buf.writeInt(x);
            buf.writeInt(z);
        }
    }

    public record Marker(long epoch, ResourceLocation dimension, ResourceLocation removed, AtlasMarker marker)
            implements RosettaPacket {
        public Marker(FriendlyByteBuf buf) { this(readMarkerChange(buf)); }
        private Marker(MarkerChange change) { this(change.epoch, change.dimension, change.removed, change.marker); }
        public void write(FriendlyByteBuf buf) { writeMarkerChange(buf, epoch, dimension, removed, marker); }
        public Marker { requireMarkerChange(removed, marker); }
    }

    private record MarkerChange(long epoch, ResourceLocation dimension, ResourceLocation removed, AtlasMarker marker) {}

    private static void requireMarkerChange(ResourceLocation removed, AtlasMarker marker) {
        if (removed == null && marker == null) throw new IllegalArgumentException("Empty Atlasbound marker change");
        if (removed != null && removed.toString().length() > AtlasMarker.MAX_ID_LENGTH)
            throw new IllegalArgumentException("Atlasbound marker id is too long");
    }

    private static void writeMarkerChange(FriendlyByteBuf buf, long epoch, ResourceLocation dimension,
                                          ResourceLocation removed, AtlasMarker marker) {
        requireMarkerChange(removed, marker);
        buf.writeVarLong(epoch);
        buf.writeResourceLocation(dimension);
        buf.writeBoolean(removed != null);
        if (removed != null) buf.writeUtf(removed.toString(), AtlasMarker.MAX_ID_LENGTH);
        buf.writeBoolean(marker != null);
        if (marker != null) AtlasMarker.STREAM_CODEC.encode(buf, marker);
    }

    private static MarkerChange readMarkerChange(FriendlyByteBuf buf) {
        long epoch = buf.readVarLong();
        ResourceLocation dimension = buf.readResourceLocation();
        ResourceLocation removed = buf.readBoolean() ? ResourceLocation.parse(buf.readUtf(AtlasMarker.MAX_ID_LENGTH)) : null;
        AtlasMarker marker = buf.readBoolean() ? AtlasMarker.STREAM_CODEC.decode(buf) : null;
        requireMarkerChange(removed, marker);
        return new MarkerChange(epoch, dimension, removed, marker);
    }
}
