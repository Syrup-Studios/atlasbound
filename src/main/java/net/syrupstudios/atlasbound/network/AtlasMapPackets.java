package net.syrupstudios.atlasbound.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.rasanovum.rosetta.network.RosettaPacket;
import java.util.UUID;

public final class AtlasMapPackets {
    private AtlasMapPackets() {}

    public record World(long epoch, UUID world) implements RosettaPacket {
        public World(FriendlyByteBuf buf) { this(buf.readVarLong(), buf.readUUID()); }
        public void write(FriendlyByteBuf buf) { buf.writeVarLong(epoch); buf.writeUUID(world); }
    }

    public record Request(long epoch, ResourceLocation dimension, int x, int z, long revision) implements RosettaPacket {
        public Request(FriendlyByteBuf buf) { this(buf.readVarLong(), buf.readResourceLocation(), buf.readInt(), buf.readInt(), buf.readVarLong()); }
        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(epoch); buf.writeResourceLocation(dimension); buf.writeInt(x); buf.writeInt(z); buf.writeVarLong(revision);
        }
    }

    public record Chunk(long epoch, ResourceLocation dimension, int x, int z, long revision, byte[] compressed) implements RosettaPacket {
        public Chunk(FriendlyByteBuf buf) { this(buf.readVarLong(), buf.readResourceLocation(), buf.readInt(), buf.readInt(), buf.readVarLong(), read(buf)); }
        public void write(FriendlyByteBuf buf) {
            if (compressed.length > 60 * 1024 + 1024) throw new IllegalArgumentException("Atlasbound map chunk is too large");
            buf.writeVarLong(epoch); buf.writeResourceLocation(dimension); buf.writeInt(x); buf.writeInt(z); buf.writeVarLong(revision); buf.writeByteArray(compressed);
        }
        private static byte[] read(FriendlyByteBuf buf) {
            byte[] bytes = buf.readByteArray(60 * 1024 + 1024);
            return bytes;
        }
    }
}
