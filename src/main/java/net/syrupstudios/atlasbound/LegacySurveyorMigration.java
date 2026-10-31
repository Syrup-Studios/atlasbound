package net.syrupstudios.atlasbound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.nbt.NbtOps;
import net.rasanovum.rosetta.nbt.NbtCompat;

/** Reads Surveyor data before player saves can replace the legacy player records. */
final class LegacySurveyorMigration {
    private static final long MAX_SOURCE_NBT_BYTES = 64L * 1024 * 1024;
    private static final String SNAPSHOT_DIR = "atlasbound-surveyor-migration";
    private static final Map<MinecraftServer, Snapshot> SNAPSHOTS = new java.util.WeakHashMap<>();
    private static final Set<String> WARNED_SKIPS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private LegacySurveyorMigration() {}

    static void capture(MinecraftServer server, Set<UUID> atlasIds) {
        if (atlasIds.isEmpty()) {
            SNAPSHOTS.put(server, new Snapshot(Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), null, new HashSet<>()));
            return;
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path snapshots = root.resolve("data").resolve(SNAPSHOT_DIR);
        Path players = root.resolve("playerdata");
        Map<UUID, CompoundTag> exploration = new HashMap<>();
        Map<UUID, String> playerErrors = new HashMap<>();
        String sourceError = null;
        Path playerSnapshots = snapshots.resolve("players");
        if (Files.isDirectory(playerSnapshots)) {
            try (var files = Files.list(playerSnapshots)) {
                for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                    try {
                        String fileName = file.getFileName().toString();
                        UUID player = UUID.fromString(fileName.substring(0, fileName.length() - 4));
                        exploration.put(player, read(file));
                    } catch (Exception exception) {
                        Atlasbound.LOGGER.error("Could not read saved Surveyor migration snapshot {}", file, exception);
                        sourceError = exception.toString();
                    }
                }
            } catch (IOException exception) {
                sourceError = exception.toString();
            }
        }
        if (Files.isDirectory(players)) {
            try (var files = Files.list(players)) {
                files.filter(path -> path.getFileName().toString().endsWith(".dat")).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    UUID player;
                    try { player = UUID.fromString(fileName.substring(0, fileName.length() - 4)); }
                    catch (IllegalArgumentException ignored) { return; }
                    if (exploration.containsKey(player) || playerErrors.containsKey(player)) return;
                    Path snapshot = playerSnapshots.resolve(player + ".dat");
                    try {
                        if (Files.exists(snapshot)) {
                            try {
                                exploration.put(player, read(snapshot));
                                return;
                            } catch (Exception exception) {
                                warnSkip("player-snapshot:" + player, "Could not read Surveyor migration snapshot for {}; trying playerdata", player);
                            }
                        }
                        CompoundTag rootTag = read(path);
                        if (rootTag.contains("surveyor", CompoundTag.TAG_COMPOUND)) {
                            CompoundTag surveyor = NbtCompat.getCompound(rootTag, "surveyor");
                            exploration.put(player, Files.exists(snapshot) ? surveyor.copy() : readOrCreateSnapshot(playerSnapshots, snapshot, surveyor));
                        }
                    } catch (Exception exception) {
                        playerErrors.put(player, exception.toString());
                    }
                });
            } catch (IOException exception) {
                Atlasbound.LOGGER.error("Could not scan playerdata for Surveyor migration", exception);
                sourceError = exception.toString();
            }
        }
        Map<ResourceLocation, CompoundTag> landmarks = new HashMap<>();
        Set<ResourceLocation> landmarkErrors = new HashSet<>();
        Path dimensionSnapshots = snapshots.resolve("dimensions");
        if (Files.isDirectory(dimensionSnapshots)) {
            try (var files = Files.list(dimensionSnapshots)) {
                for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                    try {
                        CompoundTag saved = read(file);
                        ResourceLocation dimension = ResourceLocation.tryParse(NbtCompat.getString(saved, "dimension", ""));
                        if (dimension == null || !saved.contains("data", CompoundTag.TAG_COMPOUND))
                            throw new IllegalStateException("Invalid saved Surveyor migration snapshot");
                        landmarks.put(dimension, NbtCompat.getCompound(saved, "data"));
                    } catch (Exception exception) {
                        Atlasbound.LOGGER.error("Could not read saved Surveyor migration snapshot {}", file, exception);
                        sourceError = exception.toString();
                    }
                }
            } catch (IOException exception) {
                sourceError = exception.toString();
            }
        }
        for (ResourceKey<Level> dimension : server.levelKeys()) {
            ResourceLocation id = dimension.location();
            if (landmarks.containsKey(id)) continue;
            Path snapshot = dimensionSnapshots.resolve(snapshotName(id));
            if (Files.exists(snapshot)) {
                try {
                    CompoundTag saved = read(snapshot);
                    if (!id.toString().equals(NbtCompat.getString(saved, "dimension", ""))
                            || !saved.contains("data", CompoundTag.TAG_COMPOUND))
                        throw new IllegalStateException("Invalid Surveyor landmark snapshot for " + id);
                    landmarks.put(id, NbtCompat.getCompound(saved, "data"));
                    continue;
                } catch (Exception exception) {
                    landmarkErrors.add(id);
                    Atlasbound.LOGGER.warn("Could not read saved Surveyor landmark snapshot for {}; trying the original source", id, exception);
                }
            }
            Path file = DimensionType.getStorageFolder(dimension, root).resolve("data/surveyor/landmarks.dat");
            if (!Files.exists(file)) continue;
            try {
                CompoundTag saved = new CompoundTag();
                saved.putString("dimension", id.toString());
                saved.put("data", read(file));
                landmarks.put(id, NbtCompat.getCompound(saved, "data"));
                try {
                    writeSnapshot(dimensionSnapshots, snapshot, saved);
                } catch (IOException exception) {
                    Atlasbound.LOGGER.warn("Could not save Surveyor migration snapshot {}; using source data in memory until restart", snapshot, exception);
                }
            } catch (Exception exception) {
                landmarkErrors.add(id);
                Atlasbound.LOGGER.error("Could not read Surveyor landmarks for {} before migration", id, exception);
            }
        }
        Snapshot snapshot = new Snapshot(Set.copyOf(atlasIds), exploration, playerErrors, landmarks, landmarkErrors, sourceError, new HashSet<>());
        SNAPSHOTS.put(server, snapshot);
    }

    static void clear(MinecraftServer server) { SNAPSHOTS.remove(server); }

    static boolean importFor(MinecraftServer server, UUID atlas, UUID holder, AtlasData data) {
        Snapshot snapshot = SNAPSHOTS.get(server);
        if (snapshot == null || !snapshot.atlasIds.contains(atlas) || data.legacySurveyorImported()) return true;
        ImportKey key = new ImportKey(atlas, holder);
        if (snapshot.failedImports.contains(key)) return false;
        String playerError = snapshot.playerErrors.get(holder);
        Map<ResourceLocation, Map<Long, BitSet>> regions = Map.of();
        if (playerError != null) {
            warnSkip("playerdata:" + holder, "Could not read Surveyor playerdata for {}; importing available landmarks without exploration", holder);
        } else {
            try {
                regions = exploration(snapshot.exploration.get(holder));
            } catch (RuntimeException exception) {
                warnSkip("player-exploration:" + holder, "Could not read Surveyor exploration for {}; importing available landmarks", holder);
            }
        }
        try {
            Map<ResourceLocation, List<AtlasMarker>> markers = new HashMap<>();
            for (var entry : snapshot.landmarks.entrySet()) {
                try {
                    markers.put(entry.getKey(), landmarks(entry.getValue(), holder));
                } catch (RuntimeException exception) {
                    warnSkip("landmark-parse", "Skipped one or more invalid Surveyor landmark records");
                }
            }
            int skippedMarkers = data.importLegacySurveyor(regions, markers);
            if (skippedMarkers > 0)
                Atlasbound.LOGGER.warn("Skipped {} Surveyor markers while importing into atlas {} because its marker limit was reached", skippedMarkers, atlas);
            Atlasbound.LOGGER.info("Imported Surveyor data for {} into existing atlas {}", holder, atlas);
            return true;
        } catch (RuntimeException exception) {
            return fail(snapshot, key, "Surveyor data for {} could not be imported into atlas {}; migration did not complete", exception);
        }
    }

    private static boolean fail(Snapshot snapshot, ImportKey key, String message, RuntimeException exception) {
        if (snapshot.failedImports.add(key)) {
            if (exception == null) Atlasbound.LOGGER.error(message, key.holder, key.atlas);
            else Atlasbound.LOGGER.error(message, key.holder, key.atlas, exception);
        }
        return false;
    }

    private static CompoundTag read(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.create(MAX_SOURCE_NBT_BYTES));
    }

    private static void warnSkip(String key, String message, Object... arguments) {
        if (WARNED_SKIPS.add(key)) Atlasbound.LOGGER.warn(message, arguments);
    }

    private static CompoundTag readOrCreateSnapshot(Path directory, Path snapshot, CompoundTag data) throws IOException {
        if (Files.exists(snapshot)) return read(snapshot);
        try {
            writeSnapshot(directory, snapshot, data.copy());
        } catch (IOException exception) {
            Atlasbound.LOGGER.warn("Could not save Surveyor migration snapshot {}; using source data in memory until restart", snapshot, exception);
        }
        return data.copy();
    }

    private static void writeSnapshot(Path directory, Path snapshot, CompoundTag data) throws IOException {
        Files.createDirectories(directory);
        if (Files.exists(snapshot)) return;
        Path temporary = Files.createTempFile(directory, snapshot.getFileName().toString(), ".tmp");
        try {
            NbtIo.writeCompressed(data, temporary);
            Files.move(temporary, snapshot);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String snapshotName(ResourceLocation dimension) {
        return UUID.nameUUIDFromBytes(dimension.toString().getBytes(StandardCharsets.UTF_8)) + ".dat";
    }

    private static Map<ResourceLocation, Map<Long, BitSet>> exploration(CompoundTag surveyor) {
        Map<ResourceLocation, Map<Long, BitSet>> out = new HashMap<>();
        if (surveyor == null || !surveyor.contains("exploredTerrain", CompoundTag.TAG_COMPOUND)) return out;
        CompoundTag dimensions = NbtCompat.getCompound(surveyor, "exploredTerrain");
        for (String dimensionKey : NbtCompat.getAllKeys(dimensions)) {
            ResourceLocation dimension = ResourceLocation.tryParse(dimensionKey);
            if (dimension == null || !dimensions.contains(dimensionKey, CompoundTag.TAG_LONG_ARRAY)) {
                warnSkip("exploration-dimension", "Skipped one or more invalid Surveyor exploration dimensions");
                continue;
            }
            long[] data = NbtCompat.getLongArray(dimensions, dimensionKey);
            Map<Long, BitSet> regions = new HashMap<>();
            boolean warned = false;
            for (int i = 0; i < data.length;) {
                int start = i;
                try {
                    if (i + 1 >= data.length) throw new IllegalStateException();
                    long region = data[i++];
                    int length = Math.toIntExact(data[i++]);
                    BitSet bits = new BitSet(1024);
                    if (length == -1) bits.set(0, 1024);
                    else {
                        if (length < 0 || length > 16 || i + length > data.length) throw new IllegalStateException();
                        bits = BitSet.valueOf(java.util.Arrays.copyOfRange(data, i, i + length));
                        if (bits.length() > 1024) throw new IllegalStateException();
                        i += length;
                    }
                    if (regions.containsKey(region)) throw new IllegalStateException();
                    regions.put(region, bits);
                } catch (RuntimeException exception) {
                    if (!warned) warnSkip("exploration-region", "Skipped malformed Surveyor region data in dimension {} from record {} onward", dimension, start);
                    warned = true;
                    break;
                }
            }
            if (!regions.isEmpty()) out.put(dimension, regions);
        }
        return out;
    }

    private static List<AtlasMarker> landmarks(CompoundTag root, UUID holder) {
        List<AtlasMarker> out = new ArrayList<>();
        if (!root.contains("landmarks", CompoundTag.TAG_COMPOUND)) return out;
        CompoundTag owners = NbtCompat.getCompound(root, "landmarks");
        for (String ownerKey : NbtCompat.getAllKeys(owners)) {
            UUID owner;
            try { owner = UUID.fromString(ownerKey); }
            catch (IllegalArgumentException exception) { warnSkip("landmark-owner", "Skipped one or more invalid Surveyor landmark owners"); continue; }
            if (!owner.toString().equals(ownerKey)) { warnSkip("landmark-owner", "Skipped one or more invalid Surveyor landmark owners"); continue; }
            if (!owner.equals(holder) && !owner.equals(new UUID(-7378697629483820647L, -7378697629483820647L))) continue;
            if (!owners.contains(ownerKey, CompoundTag.TAG_COMPOUND)) { warnSkip("landmark-owner-record", "Skipped one or more invalid Surveyor landmark owner records"); continue; }
            CompoundTag entries = NbtCompat.getCompound(owners, ownerKey);
            for (String markerKey : NbtCompat.getAllKeys(entries)) {
                try {
                if (!entries.contains(markerKey, CompoundTag.TAG_COMPOUND)) throw new IllegalStateException();
                CompoundTag marker = NbtCompat.getCompound(entries, markerKey);
                if (!marker.contains("components", CompoundTag.TAG_COMPOUND)) throw new IllegalStateException();
                CompoundTag components = NbtCompat.getCompound(marker, "components");
                if (!components.contains("surveyor:pos")) continue;
                BlockPos pos = BlockPos.CODEC.parse(NbtOps.INSTANCE, components.get("surveyor:pos"))
                        .result().orElseThrow(() -> new IllegalStateException("Invalid Surveyor landmark position"));
                if (Math.abs((long) pos.getX()) > 30_000_000 || Math.abs((long) pos.getZ()) > 30_000_000
                        || Math.abs((long) pos.getY()) > 2048) continue;
                String name = "";
                if (components.contains("surveyor:name")) {
                    name = ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, components.get("surveyor:name"))
                            .result().orElseThrow(() -> new IllegalStateException("Invalid Surveyor landmark name")).getString();
                }
                int color = NbtCompat.getInt(components, "surveyor:color", 0xffffff) & 0xffffff;
                String identity = owner + ":" + markerKey;
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("atlasbound", "custom/surveyor/" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)));
                out.add(new AtlasMarker(id, pos, name.length() > AtlasMarker.MAX_NAME_LENGTH ? name.substring(0, AtlasMarker.MAX_NAME_LENGTH) : name, color));
                } catch (RuntimeException exception) {
                    warnSkip("landmark-record", "Skipped one or more invalid Surveyor landmarks");
                }
            }
        }
        return out;
    }

    private record Snapshot(Set<UUID> atlasIds, Map<UUID, CompoundTag> exploration,
            Map<UUID, String> playerErrors, Map<ResourceLocation, CompoundTag> landmarks,
            Set<ResourceLocation> landmarkErrors, String sourceError, Set<ImportKey> failedImports) {}

    private record ImportKey(UUID atlas, UUID holder) {}
}
