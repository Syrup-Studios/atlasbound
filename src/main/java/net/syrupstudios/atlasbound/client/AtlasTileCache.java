package net.syrupstudios.atlasbound.client;

import folk.sisby.antique_atlas.TileElevation;
import folk.sisby.antique_atlas.TerrainTiling;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.antique_atlas.reloader.TileTextures;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.syrupstudios.atlasbound.Atlasbound;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

final class AtlasTileCache {
    private static final int MAGIC = 0x41544243;
    private static final int FORMAT = 2;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ENTRIES = 2_000_000;
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_ELEVATION_LENGTH = 32;
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32), task -> {
        Thread thread = new Thread(task, "Atlasbound tile cache");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile String resourceFingerprint;
    private static volatile int generation;
    private static RegistryAccess fingerprintRegistries;
    private static int fingerprintGeneration = -1;
    private static String registryFingerprint;

    private AtlasTileCache() {}

    static void reload(ResourceManager manager) {
        generation++;
        fingerprintRegistries = null;
        registryFingerprint = null;
        try {
            MessageDigest digest = digest();
            hashResources(manager, digest, "atlas/biome", id -> id.getPath().endsWith(".json"));
            hashResources(manager, digest, "textures/atlas/tile", id -> true);
            hashClass(digest, TerrainTiling.class);
            hashClass(digest, BiomeTileProviders.class);
            hashClass(digest, TileElevation.class);
            hashClass(digest, AtlasTileCache.class);
            TileTextures.getInstance().getTextures().keySet().stream().sorted()
                    .forEach(id -> hashText(digest, id.toString()));
            hashText(digest, SharedConstants.getCurrentVersion().getName());
            hashText(digest, Atlasbound.Client.CONFIG.fallbackFailHandling.get().name());
            resourceFingerprint = HexFormat.of().formatHex(digest.digest());
        } catch (IOException | RuntimeException | NoSuchAlgorithmException exception) {
            Atlasbound.LOGGER.warn("Could not fingerprint rendered tile resources", exception);
            resourceFingerprint = null;
        }
    }

    static String fingerprint(RegistryAccess registries) {
        String resources = resourceFingerprint;
        if (resources == null || registries == null) return null;
        if (fingerprintRegistries != registries || fingerprintGeneration != generation) {
            try {
                MessageDigest digest = digest();
                hashText(digest, resources);
                Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
                List<ResourceLocation> ids = biomes.keySet().stream().sorted().toList();
                for (ResourceLocation id : ids) {
                    hashText(digest, id.toString());
                    ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                    Holder<Biome> holder = biomes.getHolder(key).orElse(null);
                    if (holder == null) return null;
                    holder.tags().map(tag -> tag.location().toString()).sorted().forEach(tag -> hashText(digest, tag));
                    hashText(digest, "\0");
                }
                registryFingerprint = HexFormat.of().formatHex(digest.digest());
                fingerprintRegistries = registries;
                fingerprintGeneration = generation;
            } catch (RuntimeException | NoSuchAlgorithmException exception) {
                Atlasbound.LOGGER.warn("Could not fingerprint biome registry", exception);
                fingerprintRegistries = registries;
                fingerprintGeneration = generation;
                registryFingerprint = null;
            }
        }
        return registryFingerprint;
    }

    static int generation() { return generation; }

    static boolean load(UUID world, UUID atlas, ResourceLocation dimension, String fingerprint,
                        Consumer<Map<ChunkPos, Entry>> callback) {
        if (fingerprint == null || callback == null) return false;
        Path path = path(world, atlas, dimension);
        try {
            IO.execute(() -> {
                Map<ChunkPos, Entry> entries = read(path, world, atlas, dimension, fingerprint);
                Minecraft.getInstance().execute(() -> callback.accept(entries));
            });
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    static boolean save(UUID world, UUID atlas, ResourceLocation dimension, String fingerprint,
                        Map<ChunkPos, Entry> snapshot) {
        if (fingerprint == null || snapshot == null || snapshot.size() > MAX_ENTRIES) return false;
        Path path = path(world, atlas, dimension);
        Map<ChunkPos, Entry> immutable;
        try {
            immutable = Map.copyOf(snapshot);
        } catch (RuntimeException invalidSnapshot) {
            return false;
        }
        try {
            IO.execute(() -> {
                Map<ChunkPos, Entry> merged = read(path, world, atlas, dimension, fingerprint);
                merged.putAll(immutable);
                write(path, world, atlas, dimension, fingerprint, merged);
            });
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    static void flush() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        FutureTask<Void> barrier = new FutureTask<>(() -> null);
        try {
            try {
                IO.execute(barrier);
            } catch (RejectedExecutionException full) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || !IO.getQueue().offer(barrier, remaining, TimeUnit.NANOSECONDS))
                    throw new TimeoutException("Tile cache queue did not accept flush barrier");
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("Tile cache flush timed out");
            barrier.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Atlasbound.LOGGER.warn("Interrupted while flushing rendered tile cache", exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            Atlasbound.LOGGER.warn("Could not flush rendered tile cache", exception);
        }
    }

    private static Path path(UUID world, UUID atlas, ResourceLocation dimension) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("atlasbound/tile-cache-v2")
                .resolve(world.toString()).resolve(atlas.toString()).resolve(hash(dimension.toString()) + ".dat");
    }

    private static Map<ChunkPos, Entry> read(Path path, UUID world, UUID atlas, ResourceLocation dimension,
                                              String fingerprint) {
        Map<ChunkPos, Entry> entries = new HashMap<>();
        if (!Files.isRegularFile(path)) return entries;
        try {
            long size = Files.size(path);
            if (size < 80 || size > MAX_BYTES) return entries;
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                if (input.readInt() != MAGIC || input.readInt() != FORMAT
                        || !input.readUTF().equals(fingerprint)
                        || !readUuid(input).equals(world) || !readUuid(input).equals(atlas)
                        || !input.readUTF().equals(dimension.toString())) return entries;
                int count = input.readInt();
                if (count < 0 || count > MAX_ENTRIES) return entries;
                for (int i = 0; i < count; i++) {
                    ChunkPos pos = new ChunkPos(input.readInt(), input.readInt());
                    String id = input.readUTF();
                    String elevation = input.readUTF();
                    long revision = input.readLong();
                    if (Math.abs((long) pos.x) > 1_875_000L || Math.abs((long) pos.z) > 1_875_000L
                            || id.length() > MAX_ID_LENGTH || elevation.length() > MAX_ELEVATION_LENGTH || revision <= 0)
                        return new HashMap<>();
                    ResourceLocation provider = ResourceLocation.tryParse(id);
                    if (provider == null) continue;
                    if (!elevation.isEmpty() && !isElevation(elevation)) continue;
                    entries.put(pos, new Entry(provider, elevation.isEmpty() ? null : elevation, revision));
                }
                if (input.read() != -1) return new HashMap<>();
            }
        } catch (IOException | RuntimeException exception) {
            Atlasbound.LOGGER.debug("Ignoring invalid rendered tile cache {}", path, exception);
            entries.clear();
        }
        return entries;
    }

    private static void write(Path path, UUID world, UUID atlas, ResourceLocation dimension,
                              String fingerprint, Map<ChunkPos, Entry> entries) {
        if (entries.size() > MAX_ENTRIES) return;
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new BoundedOutputStream(Files.newOutputStream(temp), MAX_BYTES)))) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT);
                output.writeUTF(fingerprint);
                writeUuid(output, world);
                writeUuid(output, atlas);
                output.writeUTF(dimension.toString());
                List<Map.Entry<ChunkPos, Entry>> ordered = new ArrayList<>(entries.entrySet());
                ordered.sort(Comparator.comparingInt((Map.Entry<ChunkPos, Entry> e) -> e.getKey().x)
                        .thenComparingInt(e -> e.getKey().z));
                ordered.removeIf(item -> {
                    Entry entry = item.getValue();
                    return Math.abs((long) item.getKey().x) > 1_875_000L
                            || Math.abs((long) item.getKey().z) > 1_875_000L
                            || entry == null || entry.provider() == null
                            || entry.provider().toString().length() > MAX_ID_LENGTH
                            || entry.revision() <= 0
                            || (entry.elevation() != null && !isElevation(entry.elevation()));
                });
                if (ordered.size() > MAX_ENTRIES) return;
                output.writeInt(ordered.size());
                for (Map.Entry<ChunkPos, Entry> item : ordered) {
                    Entry entry = item.getValue();
                    output.writeInt(item.getKey().x);
                    output.writeInt(item.getKey().z);
                    output.writeUTF(entry.provider().toString());
                    output.writeUTF(entry.elevation() == null ? "" : entry.elevation());
                    output.writeLong(entry.revision());
                }
            }
            if (Files.size(temp) > MAX_BYTES) {
                Files.deleteIfExists(temp);
                return;
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            Atlasbound.LOGGER.warn("Could not save rendered tile cache {}", path, exception);
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }

    private static boolean isElevation(String name) {
        for (TileElevation value : TileElevation.values()) if (value.getName().equals(name)) return true;
        return false;
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void hashResources(ResourceManager manager, MessageDigest digest, String prefix,
                                      java.util.function.Predicate<ResourceLocation> filter) throws IOException {
        manager.listResources(prefix, filter).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            hashText(digest, entry.getKey().toString());
            try (var input = entry.getValue().open()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) >= 0) digest.update(buffer, 0, length);
            } catch (IOException exception) {
                throw new FingerprintException(exception);
            }
        });
    }

    private static void hashClass(MessageDigest digest, Class<?> type) throws IOException {
        String name = "/" + type.getName().replace('.', '/') + ".class";
        try (var input = type.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing class bytes for " + type.getName());
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) >= 0) digest.update(buffer, 0, length);
        }
    }

    private static void hashText(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest digest() throws NoSuchAlgorithmException { return MessageDigest.getInstance("SHA-256"); }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static final class FingerprintException extends RuntimeException {
        private FingerprintException(IOException cause) { super(cause); }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream output;
        private final int limit;
        private int count;
        private BoundedOutputStream(OutputStream output, int limit) { this.output = output; this.limit = limit; }
        @Override public void write(int value) throws IOException { check(1); output.write(value); count++; }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            check(length); output.write(bytes, offset, length); count += length;
        }
        private void check(int length) throws IOException {
            if (length < 0 || count + (long) length > limit) throw new IOException("Rendered tile cache exceeds size limit");
        }
        @Override public void flush() throws IOException { output.flush(); }
        @Override public void close() throws IOException { output.close(); }
    }

    record Entry(ResourceLocation provider, String elevation, long revision) {}
}
