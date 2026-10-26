package net.syrupstudios.atlasbound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/** Tracks active atlas selection and records loaded player-tracked chunks for carried atlases. */
public final class AtlasManager {
    public static final int EQUIPPED_SLOT = 41;
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static Transport transport = new Transport() {};

    private AtlasManager() {}

    public static void setTransport(Transport callback) {
        transport = callback == null ? new Transport() {} : callback;
    }

    /** Slot indexes 0-35 are player inventory slots; 40 is offhand; 41 is an equipped atlas slot. */
    public static boolean open(ServerPlayer player, int inventorySlot) {
        ItemStack stack = inventorySlot == EQUIPPED_SLOT ? AtlasEquipment.getAtlas(player) : slot(player.getInventory(), inventorySlot);
        if (stack == null || !AtlasItemData.isAtlas(stack)) return false;
        UUID id = AtlasItemData.ensureId(player, stack).orElse(null);
        if (id == null) return false;
        AtlasData data;
        try {
            data = AtlasStorage.get(player.getServer(), id);
        } catch (IllegalStateException exception) {
            return false;
        }
        VIEWS.put(player.getUUID(), new View(id, true));
        Atlasbound.LOGGER.debug("{} opened atlas {} from slot {}", player.getGameProfile().getName(), id, inventorySlot);
        transport.selection(player, id, data, true);
        return true;
    }

    public static void close(ServerPlayer player) {
        View previous = VIEWS.get(player.getUUID());
        if (previous != null) previous.open = false;
        refreshSelection(player);
    }

    public static void tick(MinecraftServer server) {
        boolean discover = server.getTickCount() % 20 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<UUID> carried = initializeInventory(player);
            transport.carried(player, carried);
            refreshSelection(player);
            if (!discover) continue;
            ServerLevel level = player.serverLevel();
            Map<UUID, AtlasData> atlases = recordingAtlases(player, server);
            if (atlases.isEmpty()) continue;
            ResourceLocation dimension = level.dimension().location();
            Map<UUID, Set<Long>> changedRegions = new HashMap<>();
            // This is the current loaded server tracking range, not Surveyor's saved history.
            player.getChunkTrackingView().forEach(chunk -> {
                if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return;
                for (var entry : atlases.entrySet()) {
                    UUID id = entry.getKey();
                    if (!entry.getValue().record(dimension, chunk)) continue;
                    long region = ChunkPos.asLong(chunk.x >> 5, chunk.z >> 5);
                    changedRegions.computeIfAbsent(id, ignored -> new HashSet<>()).add(region);
                }
            });
            for (var atlasChanges : changedRegions.entrySet()) {
                UUID id = atlasChanges.getKey();
                AtlasData data = atlases.get(id);
                for (long region : atlasChanges.getValue()) {
                    Atlasbound.LOGGER.debug("Recorded atlas {} exploration in {} region {}", id, dimension, region);
                    var bits = data.region(dimension, region);
                    for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                        View view = VIEWS.get(viewer.getUUID());
                        if (view != null && id.equals(activeId(viewer)) && possesses(viewer, id))
                            transport.regionChanged(viewer, id, dimension, region, bits);
                    }
                }
            }
        }
    }

    public static void removePlayer(ServerPlayer player) {
        VIEWS.remove(player.getUUID());
        Atlasbound.LOGGER.debug("Cleared atlas selection for disconnected player {}", player.getGameProfile().getName());
    }

    /** Returns the open, held, equipped, or first inventory atlas identity. */
    public static UUID selected(ServerPlayer player) {
        return refreshSelection(player);
    }

    /** Returns the identity of the physically held atlas with the current open view. */
    public static UUID editingAtlas(ServerPlayer player) {
        View view = VIEWS.get(player.getUUID());
        return view != null && view.open && possesses(player, view.id) ? view.id : null;
    }

    public static void serverStopped(MinecraftServer server) {
        VIEWS.clear();
        AtlasStorage.clear(server);
        AtlasItemData.clearWarnings();
    }

    private static UUID refreshSelection(ServerPlayer player) {
        View view = VIEWS.get(player.getUUID());
        UUID selected = activeId(player);
        boolean open = view != null && view.open && Objects.equals(selected, view.id);
        if (selected == null) {
            if (view != null) {
                VIEWS.remove(player.getUUID());
                transport.selection(player, null, null, false);
            }
            return null;
        }
        if (view == null || !selected.equals(view.id) || view.open != open) {
            view = new View(selected, open);
            VIEWS.put(player.getUUID(), view);
            Atlasbound.LOGGER.debug("Selected atlas {} for {}", selected, player.getGameProfile().getName());
            try {
                transport.selection(player, selected, AtlasStorage.get(player.getServer(), selected), open);
            } catch (IllegalStateException ignored) {
                VIEWS.remove(player.getUUID());
                transport.selection(player, null, null, false);
                return null;
            }
        }
        return selected;
    }

    private static Set<UUID> initializeInventory(ServerPlayer player) {
        Set<UUID> carried = new HashSet<>();
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) addCarried(player, stack, carried);
        addCarried(player, inventory.offhand.get(0), carried);
        for (ItemStack stack : AtlasEquipment.getAtlases(player)) addCarried(player, stack, carried);
        return carried;
    }

    private static void addCarried(ServerPlayer player, ItemStack stack, Set<UUID> carried) {
        if (!AtlasItemData.isAtlas(stack)) return;
        UUID id = AtlasItemData.id(stack).orElse(null);
        boolean initialized = id != null;
        if (!initialized) id = AtlasItemData.ensureId(player, stack).orElse(null);
        if (id == null || !carried.add(id)) return;
        if (initialized) return;
        try {
            AtlasStorage.get(player.getServer(), id);
        } catch (IllegalStateException ignored) {
            // Invalid atlas data stays disabled; other inventory items can still work.
        }
    }

    private static Map<UUID, AtlasData> recordingAtlases(ServerPlayer player, MinecraftServer server) {
        Map<UUID, AtlasData> atlases = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) addRecordingAtlas(player, server, stack, atlases);
        addRecordingAtlas(player, server, inventory.offhand.get(0), atlases);
        for (ItemStack stack : AtlasEquipment.getAtlases(player)) addRecordingAtlas(player, server, stack, atlases);
        return atlases;
    }

    private static void addRecordingAtlas(ServerPlayer player, MinecraftServer server, ItemStack stack,
            Map<UUID, AtlasData> atlases) {
        if (!AtlasItemData.isAtlas(stack)) return;
        UUID id = AtlasItemData.ensureId(player, stack).orElse(null);
        if (id == null || atlases.containsKey(id)) return;
        try {
            atlases.put(id, AtlasStorage.get(server, id));
        } catch (IllegalStateException ignored) {
            // One invalid atlas must not block discovery for other carried atlases.
        }
    }

    private static UUID preferredHeldId(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        UUID id = AtlasItemData.ensureId(player, main).orElse(null);
        return id != null ? id : AtlasItemData.ensureId(player, player.getOffhandItem()).orElse(null);
    }

    private static UUID activeId(ServerPlayer player) {
        View view = VIEWS.get(player.getUUID());
        if (view != null && view.open && possesses(player, view.id)) return view.id;
        UUID held = preferredHeldId(player);
        if (held != null) return held;
        UUID equipped = AtlasItemData.ensureId(player, AtlasEquipment.getAtlas(player)).orElse(null);
        if (equipped != null) return equipped;
        for (ItemStack stack : player.getInventory().items) {
            UUID id = AtlasItemData.ensureId(player, stack).orElse(null);
            if (id != null) return id;
        }
        return null;
    }

    private static boolean possesses(ServerPlayer player, UUID expected) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (AtlasItemData.id(stack).filter(expected::equals).isPresent()) return true;
        }
        if (AtlasItemData.id(player.getOffhandItem()).filter(expected::equals).isPresent()) return true;
        return AtlasEquipment.getAtlases(player).stream()
                .anyMatch(stack -> AtlasItemData.id(stack).filter(expected::equals).isPresent());
    }

    private static ItemStack slot(Inventory inventory, int index) {
        if (index >= 0 && index < inventory.items.size()) return inventory.items.get(index);
        if (index == 40) return inventory.offhand.get(0);
        return null;
    }

    private static final class View {
        private final UUID id;
        private boolean open;
        private View(UUID id, boolean open) { this.id = id; this.open = open; }
    }

    public interface Transport {
        default void selection(ServerPlayer player, UUID atlasId, AtlasData data, boolean openScreen) {}
        default void carried(ServerPlayer player, Set<UUID> atlases) {}
        default void regionChanged(ServerPlayer player, UUID atlasId, ResourceLocation dimension, long region, java.util.BitSet bits) {}
    }
}
