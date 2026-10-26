package net.syrupstudios.atlasbound;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.rasanovum.rosetta.nbt.NbtCompat;

/** A user marker stored with an atlas. */
public record AtlasMarker(ResourceLocation id, BlockPos pos, String name, int color) {
    public static final int MAX_MARKERS = 1024;
    public static final int MAX_ID_LENGTH = 256;
    public static final int MAX_NAME_LENGTH = 128;

    public static final StreamCodec<FriendlyByteBuf, AtlasMarker> STREAM_CODEC = StreamCodec.of(
            (buf, marker) -> {
                buf.writeUtf(marker.id.toString(), MAX_ID_LENGTH);
                buf.writeBlockPos(marker.pos);
                buf.writeUtf(marker.name, MAX_NAME_LENGTH);
                buf.writeInt(marker.color);
            }, buf -> new AtlasMarker(ResourceLocation.parse(buf.readUtf(MAX_ID_LENGTH)),
                    buf.readBlockPos(), buf.readUtf(MAX_NAME_LENGTH), buf.readInt()));

    public AtlasMarker {
        if (id == null || !id.getPath().startsWith("custom/") || id.toString().length() > MAX_ID_LENGTH)
            throw new IllegalArgumentException("Invalid Atlasbound marker id");
        if (pos == null || Math.abs((long) pos.getX()) > 30_000_000 || Math.abs((long) pos.getZ()) > 30_000_000
                || Math.abs((long) pos.getY()) > 2048)
            throw new IllegalArgumentException("Invalid Atlasbound marker position");
        pos = pos.immutable();
        if (name == null || name.length() > MAX_NAME_LENGTH) throw new IllegalArgumentException("Invalid Atlasbound marker name");
        if (color < 0 || color > 0xffffff) throw new IllegalArgumentException("Invalid Atlasbound marker color");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putString("name", name);
        tag.putInt("color", color);
        return tag;
    }

    public static AtlasMarker load(CompoundTag tag) {
        if (!tag.contains("id", CompoundTag.TAG_STRING) || !tag.contains("x", CompoundTag.TAG_INT)
                || !tag.contains("y", CompoundTag.TAG_INT) || !tag.contains("z", CompoundTag.TAG_INT)
                || !tag.contains("name", CompoundTag.TAG_STRING) || !tag.contains("color", CompoundTag.TAG_INT))
            throw new IllegalStateException("Invalid Atlasbound marker fields");
        ResourceLocation id = ResourceLocation.tryParse(NbtCompat.getString(tag, "id", ""));
        if (id == null) throw new IllegalStateException("Invalid Atlasbound marker id");
        try {
            return new AtlasMarker(id, new BlockPos(NbtCompat.getInt(tag, "x", 0), NbtCompat.getInt(tag, "y", 0), NbtCompat.getInt(tag, "z", 0)),
                    NbtCompat.getString(tag, "name", ""), NbtCompat.getInt(tag, "color", 0));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Atlasbound marker", e);
        }
    }
}
