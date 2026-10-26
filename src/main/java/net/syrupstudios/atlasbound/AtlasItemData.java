package net.syrupstudios.atlasbound;

import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Persistent identity for one antique atlas item. */
public final class AtlasItemData {
    private static final String ID_KEY = "atlasbound:id";
    private static final Set<UUID> WARNED_PLAYERS = new HashSet<>();

    private AtlasItemData() {}

    public static boolean isAtlas(ItemStack stack) {
        return stack.is(Atlasbound.ATLAS.get());
    }

    public static boolean canCopyAtlas(ItemStack atlas, ItemStack book) {
        return isAtlas(atlas) && atlas.getCount() == 1 && id(atlas).isPresent() && book.is(Items.BOOK);
    }

    public static Optional<UUID> id(ItemStack stack) {
        if (!isAtlas(stack)) return Optional.empty();
        CompoundTag data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(ID_KEY, CompoundTag.TAG_STRING)) return Optional.empty();
        try {
            String value = data.getString(ID_KEY);
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Assign an identity to a new stack; a malformed existing value is never replaced. */
    public static Optional<UUID> ensureId(ServerPlayer player, ItemStack stack) {
        if (!isAtlas(stack)) return Optional.empty();
        CompoundTag data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains(ID_KEY)) {
            Optional<UUID> existing = id(stack);
            if (existing.isEmpty()) {
                if (WARNED_PLAYERS.add(player.getUUID()))
                    Atlasbound.LOGGER.warn("Ignoring atlas with malformed identity held by {}", player.getGameProfile().getName());
            }
            return existing;
        }
        UUID id = UUID.randomUUID();
        data.putString(ID_KEY, id.toString());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(data));
        Atlasbound.LOGGER.debug("Assigned atlas identity {} to {}", id, player.getGameProfile().getName());
        return Optional.of(id);
    }

    public static void clearWarnings() {
        WARNED_PLAYERS.clear();
    }
}
