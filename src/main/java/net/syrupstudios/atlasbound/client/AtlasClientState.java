package net.syrupstudios.atlasbound.client;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import folk.sisby.antique_atlas.WorldAtlasData;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.AtlasEquipment;
import net.syrupstudios.atlasbound.AtlasManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.syrupstudios.atlasbound.AtlasItemData;
import net.syrupstudios.atlasbound.AtlasMarker;
import net.syrupstudios.atlasbound.network.AtlasPackets;
import net.rasanovum.rosetta.network.RosettaNetwork;
import net.syrupstudios.atlasbound.map.AtlasLandmark;

public final class AtlasClientState {
    private static long epoch = -1;
    private static UUID atlas;
    private static boolean pendingOpen;
    private static int pendingSince;
    private static boolean allowScreenOpen;
    private static boolean screenOpen;
    private static boolean refreshOnReady;
    private static Set<UUID> carried;
    private static final Map<ResourceLocation, Map<Long, BitSet>> regions = new HashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, AtlasMarker>> markers = new HashMap<>();
    private static Map<ResourceLocation, AtlasMapView> views = new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, AtlasMapView>> viewCache = new HashMap<>();

    private AtlasClientState() {}

    public static void begin(AtlasPackets.Selection packet) {
        if (packet.epoch() < epoch) return;
        if (packet.epoch() == epoch) {
            if (!java.util.Objects.equals(packet.atlas(), atlas)) return;
            pendingOpen = packet.openScreen() && atlas != null;
            pendingSince = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
            return;
        }
        saveViews(views);
        closeScreen();
        detachViews();
        if (atlas != null) cacheViews(atlas, views);
        epoch = packet.epoch();
        atlas = packet.atlas();
        views = atlas == null ? new HashMap<>() : viewCache.getOrDefault(atlas, new HashMap<>());
        if (atlas != null) {
            views.values().forEach(view -> view.setMarkers(Map.of()));
            cacheViews(atlas, views);
        }
        refreshOnReady = true;
        pendingOpen = packet.openScreen() && atlas != null;
        pendingSince = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
        regions.clear();
        markers.clear();
        Atlasbound.LOGGER.debug("Received atlas selection {} at epoch {} (open={})", atlas, epoch, packet.openScreen());
    }

    public static void region(AtlasPackets.Region packet) {
        if (atlas == null || packet.epoch() != epoch) return;
        BitSet bits = BitSet.valueOf(packet.bits());
        if (bits.length() > 1024) return;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension());
        AtlasMapView view = view(dimension);
        BitSet atlasBits = regions.computeIfAbsent(packet.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(packet.region(), ignored -> new BitSet(1024));
        atlasBits.or(bits);
        view.addRegion(packet.region(), bits);
        publish(view, dimension);
        view.setMarkers(markers.getOrDefault(packet.dimension(), Map.of()));
    }

    public static void ready(AtlasPackets.Ready packet) {
        if (packet.epoch() != epoch) return;
        if (refreshOnReady) {
            refreshViews();
            refreshOnReady = false;
        }
        if (pendingOpen) {
            pendingOpen = false;
            allowScreenOpen = true;
            try {
                screenOpen = Atlasbound.Client.openAtlasScreen() != null;
                Atlasbound.LOGGER.debug("Opened atlas screen for {} at epoch {}", atlas, epoch);
            } finally {
                allowScreenOpen = false;
            }
        }
    }

    public static void marker(AtlasPackets.Marker packet) {
        if (atlas == null || packet.epoch() != epoch) return;
        Map<ResourceLocation, AtlasMarker> dimensionMarkers = markers.computeIfAbsent(packet.dimension(), ignored -> new HashMap<>());
        if (packet.removed() != null) dimensionMarkers.remove(packet.removed());
        if (packet.marker() != null) dimensionMarkers.put(packet.marker().id(), packet.marker());
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension());
        view(dimension).setMarkers(dimensionMarkers);
        if (Minecraft.getInstance().screen instanceof AtlasScreen screen) screen.updateBookmarkerList();
    }

    static boolean editMarker(ResourceLocation dimension, ResourceLocation previous, AtlasMarker marker) {
        Minecraft client = Minecraft.getInstance();
        if (atlas == null || client.getConnection() == null) return false;
        RosettaNetwork.sendToServer(new AtlasPackets.MarkerEdit(epoch, dimension, previous, marker));
        return true;
    }

    public static void teleport(ResourceLocation dimension, int x, int z) {
        Minecraft client = Minecraft.getInstance();
        if (atlas == null || client.getConnection() == null) return;
        RosettaNetwork.sendToServer(new AtlasPackets.Teleport(epoch, dimension, x, z));
    }

    static AtlasMarker marker(ResourceLocation dimension, ResourceLocation id) {
        return markers.getOrDefault(dimension, Map.of()).get(id);
    }

    static boolean hasMarker(ResourceLocation dimension, ResourceLocation id) {
        return markers.getOrDefault(dimension, Map.of()).containsKey(id);
    }

    public static UUID markerOwner() { return UUID.nameUUIDFromBytes("atlasbound-custom-markers".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    static AtlasLandmark landmark(AtlasMarker marker) {
        return new AtlasLandmark(markerOwner(), marker.id(), marker.pos(), java.util.Set.of(),
                Component.literal(marker.name()), java.util.List.of(), marker.color(), ItemStack.EMPTY, 0L, 0);
    }

    public static boolean submitMarker(ResourceLocation dimension, AtlasLandmark previous, AtlasLandmark updated) {
        if (atlas == null || (previous == null && updated == null)) return false;
        ResourceLocation id = previous != null ? previous.id() : updated.id();
        if (updated == null) return editMarker(dimension, id, null);
        if (updated.pos() == null || !updated.owner().equals(markerOwner())) return false;
        String name = updated.name() == null ? "" : updated.name().getString();
        if (name.length() > AtlasMarker.MAX_NAME_LENGTH) name = name.substring(0, AtlasMarker.MAX_NAME_LENGTH);
        try {
            return editMarker(dimension, previous == null ? null : previous.id(),
                    new AtlasMarker(updated.id(), updated.pos(), name, updated.color() & 0xFFFFFF));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static AtlasMapView view(ResourceKey<Level> dimension) {
        AtlasMapView view = views.computeIfAbsent(dimension.location(), key -> new AtlasMapView(key, atlas));
        publish(view, dimension);
        return view;
    }

    public static void carried(Set<UUID> atlases) {
        carried = Set.copyOf(atlases);
        if (atlas != null && !carried.contains(atlas)) saveViews(views);
        viewCache.forEach((owner, atlasViews) -> {
            if (!carried.contains(owner)) saveViews(atlasViews);
        });
        viewCache.keySet().removeIf(owner -> !carried.contains(owner));
    }

    static BitSet allowed(ResourceLocation dimension, long region) {
        BitSet bits = regions.getOrDefault(dimension, Map.of()).get(region);
        return bits == null ? new BitSet() : (BitSet) bits.clone();
    }

    public static boolean allows(ResourceLocation dimension, ChunkPos pos) {
        if (atlas == null) return false;
        long key = ChunkPos.asLong(pos.x >> 5, pos.z >> 5);
        BitSet bits = regions.getOrDefault(dimension, Map.of()).get(key);
        return bits != null && bits.get((pos.x & 31) * 32 + (pos.z & 31));
    }

    static boolean owns(UUID owner) { return owner != null && owner.equals(atlas); }

    public static boolean isSelectedAtlas(ItemStack stack) {
        return atlas != null && AtlasItemData.id(stack).filter(atlas::equals).isPresent();
    }

    public static boolean allowScreenOpen() {
        return allowScreenOpen;
    }

    public static void requestOpen() {
        Minecraft client = Minecraft.getInstance();
        if (pendingOpen || client.player == null || client.getConnection() == null) return;
        int slot = findAtlasSlot(client.player);
        if (slot < 0) return;
        pendingOpen = true;
        pendingSince = client.player.tickCount;
        RosettaNetwork.sendToServer(new AtlasPackets.Open(slot));
    }

    public static boolean hasAtlas(Player player) { return findAtlasSlot(player) >= 0; }

    private static int findAtlasSlot(Player player) {
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        if (AtlasItemData.isAtlas(AtlasEquipment.getAtlas(player))) return AtlasManager.EQUIPPED_SLOT;
        if (AtlasItemData.isAtlas(inventory.getSelected())) return selected;
        if (AtlasItemData.isAtlas(inventory.offhand.get(0))) return 40;
        for (int i = 0; i < inventory.items.size(); i++)
            if (AtlasItemData.isAtlas(inventory.items.get(i))) return i;
        return -1;
    }

    public static void tick(Minecraft client) {
        if (atlas != null && client.getConnection() != null) {
            boolean refreshBookmarks = false;
            for (AtlasMapView view : views.values()) {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, view.dimensionId());
                boolean wasEmpty = WorldAtlasData.isEmpty(dimension);
                boolean wasPublished = WorldAtlasData.WORLDS.containsKey(dimension);
                view.tick(client.getConnection().registryAccess());
                publish(view, dimension);
                if (wasEmpty != WorldAtlasData.isEmpty(dimension)
                        && wasPublished == WorldAtlasData.WORLDS.containsKey(dimension)) refreshBookmarks = true;
            }
            if (refreshBookmarks && client.screen instanceof AtlasScreen screen) screen.updateBookmarkerList();
        }
        if (pendingOpen && client.player != null && client.player.tickCount - pendingSince > 100) {
            pendingOpen = false;
            Atlasbound.LOGGER.debug("Atlas open request timed out at epoch {}", epoch);
            client.player.displayClientMessage(Component.translatable("message.atlasbound.open_rejected"), false);
        }
        if (screenOpen && !(client.screen instanceof AtlasScreen)) {
            saveViews(views);
            screenOpen = false;
            if (client.getConnection() != null) RosettaNetwork.sendToServer(new AtlasPackets.Close());
        }
    }

    public static void clear(boolean close) {
        saveAllViews();
        if (close) closeScreen();
        if (atlas != null) cacheViews(atlas, views);
        detachViews();
        views = new HashMap<>();
        epoch = -1;
        atlas = null;
        pendingOpen = false;
        pendingSince = 0;
        allowScreenOpen = false;
        screenOpen = false;
        refreshOnReady = false;
        regions.clear();
        markers.clear();
        carried = null;
    }

    private static void detachViews() {
        WorldAtlasData.WORLDS.entrySet().removeIf(entry -> entry.getValue() instanceof AtlasMapView);
    }

    private static void cacheViews(UUID owner, Map<ResourceLocation, AtlasMapView> atlasViews) {
        if (carried != null && !carried.contains(owner)) return;
        viewCache.put(owner, atlasViews);
    }

    private static void publish(AtlasMapView view, ResourceKey<Level> dimension) {
        boolean allowed = view.hasWorldData()
                && regions.getOrDefault(dimension.location(), Map.of()).values().stream().anyMatch(bits -> !bits.isEmpty());
        boolean changed = allowed
                ? WorldAtlasData.WORLDS.put(dimension, view) != view
                : WorldAtlasData.WORLDS.remove(dimension, view);
        if (changed && Minecraft.getInstance().screen instanceof AtlasScreen screen) screen.updateBookmarkerList();
    }

    public static void clearCache() {
        clear(false);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        do {
            saveAllViews();
            AtlasTileCache.flush();
            if (!hasPendingCacheSaves()) break;
            if (System.nanoTime() < deadline) {
                try { Thread.sleep(25); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (System.nanoTime() < deadline);
        if (hasPendingCacheSaves()) Atlasbound.LOGGER.warn("Some rendered atlas tiles could not be saved before client shutdown");
        viewCache.clear();
    }

    static boolean isCurrentOrRetained(AtlasMapView view) {
        return views.get(view.dimensionId()) == view
                || viewCache.values().stream().anyMatch(atlasViews -> atlasViews.get(view.dimensionId()) == view);
    }

    static void resourceReloaded(ResourceManager manager) {
        saveAllViews();
        AtlasTileCache.reload(manager);
        Minecraft client = Minecraft.getInstance();
        net.minecraft.core.RegistryAccess registries = client.getConnection() == null
                ? null : client.getConnection().registryAccess();
        java.util.Set<AtlasMapView> allViews = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        allViews.addAll(views.values());
        viewCache.values().forEach(atlasViews -> allViews.addAll(atlasViews.values()));
        allViews.forEach(view -> view.resourceReloaded(registries));
        if (registries == null) return;
        for (AtlasMapView view : views.values()) {
            view.setMarkers(markers.getOrDefault(view.dimensionId(), Map.of()));
            publish(view, ResourceKey.create(Registries.DIMENSION, view.dimensionId()));
        }
        if (atlas != null) cacheViews(atlas, views);
    }

    private static void saveAllViews() {
        saveViews(views);
        viewCache.values().forEach(AtlasClientState::saveViews);
    }

    private static void saveViews(Map<ResourceLocation, AtlasMapView> atlasViews) {
        atlasViews.values().forEach(AtlasMapView::saveCache);
    }

    private static boolean hasPendingCacheSaves() {
        return views.values().stream().anyMatch(AtlasMapView::cacheSavePending)
                || viewCache.values().stream().flatMap(atlasViews -> atlasViews.values().stream())
                        .anyMatch(AtlasMapView::cacheSavePending);
    }

    private static void closeScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AtlasScreen) client.setScreen(null);
        screenOpen = false;
    }

    private static void refreshViews() {
        java.util.Set<ResourceLocation> dimensions = new java.util.HashSet<>(regions.keySet());
        dimensions.addAll(markers.keySet());
        for (ResourceLocation location : dimensions) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location);
            AtlasMapView view = view(dimension);
            view.setMarkers(markers.getOrDefault(location, Map.of()));
        }
    }
}
