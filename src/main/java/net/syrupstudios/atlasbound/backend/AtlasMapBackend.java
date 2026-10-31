package net.syrupstudios.atlasbound.backend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.syrupstudios.atlasbound.AtlasManager;
import net.syrupstudios.atlasbound.AtlasStorage;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.map.AtlasMapData;
import net.syrupstudios.atlasbound.network.AtlasMapPackets;
import net.syrupstudios.atlasbound.network.AtlasNetworking;
import net.rasanovum.rosetta.network.RosettaNetwork;

/** Serves explored map chunks from saved world data. */
public final class AtlasMapBackend {
    private static final int MAX_PENDING = 128;
    private static final int MAX_IN_FLIGHT = 32;
    private static final int MAX_PLAYER_OUTSTANDING = 16;
    private static final int MAX_PLAYER_REQUESTS_PER_TICK = 8;
    private static final Map<MinecraftServer, State> STATES = new HashMap<>();
    private AtlasMapBackend() {}

    public static void request(ServerPlayer player, AtlasMapPackets.Request packet) {
        MinecraftServer server = player.getServer();
        if (!server.isSameThread() || Math.abs((long) packet.x()) > 1_875_000L || Math.abs((long) packet.z()) > 1_875_000L
                || server.getLevel(ResourceKey.create(Registries.DIMENSION, packet.dimension())) == null
                || !authorized(player, packet.epoch(), packet.dimension(), packet.x(), packet.z())) return;
        State state = STATES.computeIfAbsent(server, AtlasMapBackend::newState);
        Key key = new Key(packet.dimension(), packet.x(), packet.z());
        Work work = new Work(player.getUUID(), packet.epoch(), key, packet.revision());
        RequestId id = id(work);
        int tick = server.getTickCount();
        Ingress ingress = state.ingress.computeIfAbsent(work.player, ignored -> new Ingress(tick));
        if (ingress.tick != tick) { ingress.tick = tick; ingress.count = 0; }
        if (ingress.count >= MAX_PLAYER_REQUESTS_PER_TICK) return;
        ingress.count++;
        if (state.outstanding.size() >= MAX_PENDING || countPlayer(state, work.player) >= MAX_PLAYER_OUTSTANDING
                || !state.outstanding.add(id)) return;
        state.pending.addLast(work);
    }

    public static void tick(ServerLevel level) {
        if (level != level.getServer().overworld()) return;
        State state = STATES.get(level.getServer());
        if (state == null) return;
        int sentBytes = 0;
        Map<UUID, Integer> sentByPlayer = new HashMap<>();
        int outboundCount = state.outbound.size();
        while (outboundCount-- > 0 && !state.outbound.isEmpty() && sentBytes < 256 * 1024) {
            Outbound item = state.outbound.peekFirst();
            int responseBytes = item.work.revision != 0 && item.work.revision == item.record.revision
                    ? 0 : item.record.compressed.length;
            if (responseBytes > 256 * 1024 - sentBytes
                    || responseBytes > 64 * 1024 - sentByPlayer.getOrDefault(item.work.player, 0)) {
                state.outbound.addLast(state.outbound.removeFirst());
                continue;
            }
            state.outbound.removeFirst();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(item.work.player);
            if (player != null) {
                sendIfCurrent(player, item.work, item.record);
                sentBytes += item.record.compressed.length;
                sentByPlayer.merge(item.work.player, item.record.compressed.length, Integer::sum);
            }
            finish(state, item.work);
        }
        for (int captured = 0; captured < 2 && !state.captures.isEmpty(); captured++) {
            Ready ready = state.captures.removeFirst();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ready.work.player);
            if (player == null || !authorized(player, ready.work.epoch, ready.work.key.dimension, ready.work.key.x, ready.work.key.z)) {
                finish(state, ready.work);
                continue;
            }
            byte[] raw = ready.tag != null
                    ? AtlasMapDataProvider.captureSaved(ready.level, new ChunkPos(ready.work.key.x, ready.work.key.z), ready.tag)
                    : AtlasMapDataProvider.captureLive(ready.level, ready.chunk);
            complete(level.getServer(), state, ready.work, raw);
        }
        int started = 0;
        while (started < 2 && state.inFlight.size() < MAX_IN_FLIGHT && !state.pending.isEmpty()) {
            Work work = state.pending.removeFirst();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(work.player);
            if (player == null || !authorized(player, work.epoch, work.key.dimension, work.key.x, work.key.z)) {
                finish(state, work);
                continue;
            }
            state.inFlight.add(id(work));
            started++;
            readChunk(level.getServer(), state, work, player);
        }
    }

    public static void serverStopped(MinecraftServer server) {
        State state = STATES.get(server);
        if (state != null) {
            while (!state.captures.isEmpty()) {
                Ready ready = state.captures.removeFirst();
                byte[] raw = ready.tag != null
                        ? AtlasMapDataProvider.captureSaved(ready.level, new ChunkPos(ready.work.key.x, ready.work.key.z), ready.tag)
                        : AtlasMapDataProvider.captureLive(ready.level, ready.chunk);
                if (raw != null) complete(server, state, ready.work, raw);
            }
        }
        STATES.remove(server);
        if (state != null) {
            state.pending.clear(); state.outbound.clear();
            state.executor.shutdown();
            try {
                if (!state.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Atlasbound.LOGGER.error("Atlasbound map IO did not drain before shutdown; queued cache writes may be lost");
                    state.executor.shutdownNow();
                }
            }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); state.executor.shutdownNow(); }
        }
    }

    public static void disconnect(ServerPlayer player) {
        State state = STATES.get(player.getServer());
        if (state == null) return;
        state.pending.removeIf(work -> {
            if (!work.player.equals(player.getUUID())) return false;
            finish(state, work);
            return true;
        });
        state.captures.removeIf(ready -> {
            if (!ready.work.player.equals(player.getUUID())) return false;
            finish(state, ready.work);
            return true;
        });
        state.outbound.removeIf(item -> {
            if (!item.work.player.equals(player.getUUID())) return false;
            finish(state, item.work);
            return true;
        });
        state.ingress.remove(player.getUUID());
        for (RequestId id : Set.copyOf(state.outstanding)) {
            if (id.player.equals(player.getUUID())) {
                state.outstanding.remove(id);
                state.inFlight.remove(id);
            }
        }
    }

    private static State newState(MinecraftServer server) {
        return new State(loadWorldId(server));
    }

    public static UUID worldId(MinecraftServer server) {
        return STATES.computeIfAbsent(server, AtlasMapBackend::newState).world;
    }

    private static UUID loadWorldId(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("atlasbound-world-id");
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) return UUID.fromString(Files.readString(file).trim());
            UUID id = UUID.randomUUID();
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, id.toString());
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            return id;
        } catch (IOException | IllegalArgumentException error) {
            Atlasbound.LOGGER.error("Could not load Atlasbound world identity", error);
            return UUID.randomUUID();
        }
    }

    private static void readChunk(MinecraftServer server, State state, Work work, ServerPlayer player) {
        if (STATES.get(server) != state) { finish(state, work); return; }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, work.key.dimension);
        ServerLevel level = server.getLevel(dimension);
        if (level == null) { finish(state, work); return; }
        CacheRecord memory = state.cache.get(work.key);
        var loaded = level.getChunkSource().getChunkNow(work.key.x, work.key.z);
        if (memory != null && memory.capturedAt > 0 && server.getTickCount() - memory.capturedAt < 100) {
            ServerPlayer current = server.getPlayerList().getPlayer(work.player);
            if (current != null) queueOutbound(state, work, memory);
            else finish(state, work);
            return;
        }
        if (loaded != null) {
            state.captures.addLast(new Ready(work, level, null, loaded));
            return;
        }
        Path cacheFile = cacheFile(server, work.key);
        try {
            state.executor.execute(() -> {
                CacheRecord diskCached = readCache(cacheFile, regionFile(server, work.key), work.key);
                server.execute(() -> {
                    if (STATES.get(server) != state) { finish(state, work); return; }
                    ServerPlayer current = server.getPlayerList().getPlayer(work.player);
                    if (current == null || !authorized(current, work.epoch, work.key.dimension, work.key.x, work.key.z)) {
                        finish(state, work);
                    } else if (diskCached != null) {
                        state.cache.put(work.key, diskCached);
                        queueOutbound(state, work, diskCached);
                    } else {
                        state.cache.remove(work.key);
                        readSavedChunk(server, state, work, level);
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            queueOutbound(state, work, new CacheRecord(0, new byte[0], server.getTickCount()));
        }
    }

    private static void readSavedChunk(MinecraftServer server, State state, Work work, ServerLevel level) {
        level.getChunkSource().chunkMap.read(new ChunkPos(work.key.x, work.key.z)).whenComplete((tag, error) -> server.execute(() -> {
            if (STATES.get(server) != state) { finish(state, work); return; }
            ServerPlayer current = server.getPlayerList().getPlayer(work.player);
            if (error != null || tag == null || tag.isEmpty() || current == null
                    || !authorized(current, work.epoch, work.key.dimension, work.key.x, work.key.z)) {
                if (current != null && authorized(current, work.epoch, work.key.dimension, work.key.x, work.key.z))
                    queueOutbound(state, work, new CacheRecord(0, new byte[0], server.getTickCount()));
                else finish(state, work);
                return;
            }
            if (state.captures.size() >= MAX_PENDING) {
                queueOutbound(state, work, new CacheRecord(0, new byte[0], server.getTickCount()));
                return;
            }
            state.captures.addLast(new Ready(work, level, tag.get(), null));
        }));
    }

    private static void complete(MinecraftServer server, State state, Work work, byte[] raw) {
        if (raw == null || raw.length == 0 || raw.length > 60 * 1024 || STATES.get(server) != state) {
            queueOutbound(state, work, new CacheRecord(0, new byte[0], server.getTickCount()));
            return;
        }
        CacheRecord previous = state.cache.get(work.key);
        int capturedAt = server.getTickCount();
        try { state.executor.execute(() -> {
            try {
                long revision = java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256").digest(raw)).getLong() & Long.MAX_VALUE;
                if (revision == 0) revision = 1;
                byte[] compressed = compress(raw);
                if (compressed == null || compressed.length > 60 * 1024 + 1024) throw new IOException("Compressed map record exceeds packet limit");
                CacheRecord record = new CacheRecord(revision, compressed, capturedAt);
                if (previous == null || previous.revision != revision) persist(server, work.key, record);
                server.execute(() -> {
                    if (STATES.get(server) != state) return;
                    state.cache.put(work.key, record);
                    queueOutbound(state, work, record);
                });
            } catch (Exception error) {
                Atlasbound.LOGGER.warn("Could not encode Atlasbound map chunk {} in {}", work.key, server.getWorldData().getLevelName(), error);
                server.execute(() -> {
                    if (STATES.get(server) == state) queueOutbound(state, work, new CacheRecord(0, new byte[0], 0));
                    else finish(state, work);
                });
            }
        }); } catch (java.util.concurrent.RejectedExecutionException full) {
            queueOutbound(state, work, new CacheRecord(0, new byte[0], server.getTickCount()));
        }
    }

    private static byte[] compress(byte[] raw) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) { deflater.write(raw); }
            return output.toByteArray();
        } catch (IOException impossible) { return null; }
    }

    private static void persist(MinecraftServer server, Key key, CacheRecord record) {
        Path file = cacheFile(server, key);
        Path root = file.getParent();
        try {
            Files.createDirectories(root);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var out = new java.io.DataOutputStream(Files.newOutputStream(temp))) {
                out.writeInt(1); out.writeLong(record.revision); out.writeInt(record.compressed.length); out.write(record.compressed);
            }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) {
            Atlasbound.LOGGER.warn("Could not save Atlasbound map cache record {}", file, error);
        }
    }

    private static Path cacheFile(MinecraftServer server, Key key) {
        String dimension = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.dimension.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("atlasbound-map-cache")
                .resolve(dimension).resolve(key.x + "." + key.z + ".dat");
    }

    private static CacheRecord readCache(Path file, Path region, Key key) {
        if (!Files.isRegularFile(file)) return null;
        try (var in = new java.io.DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != 1) return null;
            long revision = in.readLong();
            int length = in.readInt();
            if (revision == 0 || length < 1 || length > 60 * 1024 + 1024) return null;
            byte[] bytes = in.readNBytes(length);
            if (bytes.length != length || in.read() != -1) return null;
            if (Files.isRegularFile(region)
                    && Files.getLastModifiedTime(region).compareTo(Files.getLastModifiedTime(file)) > 0) return null;
            byte[] raw;
            try (var inflate = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes));
                 var output = new ByteArrayOutputStream()) {
                byte[] block = new byte[4096];
                int count;
                while ((count = inflate.read(block)) >= 0) {
                    if (output.size() + count > 60 * 1024) return null;
                    output.write(block, 0, count);
                }
                raw = output.toByteArray();
            }
            AtlasMapData decoded = AtlasMapData.decode(raw);
            if (decoded.terrain().pos().x != key.x || decoded.terrain().pos().z != key.z) return null;
            long actual = java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256").digest(raw)).getLong() & Long.MAX_VALUE;
            if (actual == 0) actual = 1;
            return actual == revision ? new CacheRecord(revision, bytes, 0) : null;
        } catch (IOException | RuntimeException | java.security.NoSuchAlgorithmException invalidRecord) {
            Atlasbound.LOGGER.warn("Ignoring invalid Atlasbound map cache record {}", file);
            return null;
        }
    }

    private static Path regionFile(MinecraftServer server, Key key) {
        Path dimensionPath = DimensionType.getStorageFolder(
                ResourceKey.create(Registries.DIMENSION, key.dimension), server.getWorldPath(LevelResource.ROOT));
        return dimensionPath.resolve("region")
                .resolve("r." + (key.x >> 5) + "." + (key.z >> 5) + ".mca");
    }

    private static void sendIfCurrent(ServerPlayer player, Work work, CacheRecord record) {
        if (!authorized(player, work.epoch, work.key.dimension, work.key.x, work.key.z)) return;
        byte[] bytes = work.revision != 0 && work.revision == record.revision ? new byte[0] : record.compressed;
        RosettaNetwork.sendToPlayer(new AtlasMapPackets.Chunk(work.epoch, work.key.dimension, work.key.x,
                work.key.z, record.revision, bytes), player);
    }

    private static void queueOutbound(State state, Work work, CacheRecord record) {
        if (state.outbound.size() < MAX_PENDING) state.outbound.addLast(new Outbound(work, record));
        else finish(state, work);
    }

    private static boolean authorized(ServerPlayer player, long epoch, ResourceLocation dimension, int x, int z) {
        if (!AtlasNetworking.mapRequestValid(player, epoch)) return false;
        UUID atlas = AtlasManager.selected(player);
        if (atlas == null) return false;
        try { return AtlasStorage.get(player.getServer(), atlas, player.getUUID()).allows(dimension, new ChunkPos(x, z)); }
        catch (IllegalStateException invalidAtlas) { return false; }
    }

    private static RequestId id(Work work) { return new RequestId(work.player, work.epoch, work.key); }
    private static int countPlayer(State state, UUID player) {
        int count = 0;
        for (RequestId request : state.outstanding) if (request.player.equals(player)) count++;
        return count;
    }
    private static void finish(State state, Work work) {
        RequestId id = id(work);
        state.inFlight.remove(id);
        state.outstanding.remove(id);
    }
    private record Key(ResourceLocation dimension, int x, int z) {}
    private record RequestId(UUID player, long epoch, Key key) {}
    private record Work(UUID player, long epoch, Key key, long revision) {}
    private record CacheRecord(long revision, byte[] compressed, int capturedAt) {}
    private record Ready(Work work, ServerLevel level, CompoundTag tag, net.minecraft.world.level.chunk.LevelChunk chunk) {}
    private record Outbound(Work work, CacheRecord record) {}
    private static final class Ingress { int tick; int count; Ingress(int tick) { this.tick = tick; } }
    private static final class State {
        final UUID world;
        final ArrayDeque<Work> pending = new ArrayDeque<>();
        final ArrayDeque<Ready> captures = new ArrayDeque<>();
        final ArrayDeque<Outbound> outbound = new ArrayDeque<>();
        final Set<RequestId> outstanding = new HashSet<>();
        final Set<RequestId> inFlight = new HashSet<>();
        final Map<UUID, Ingress> ingress = new HashMap<>();
        final ThreadPoolExecutor executor;
        final Map<Key, CacheRecord> cache = new LinkedHashMap<>(256, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Key, CacheRecord> eldest) { return size() > 256; }
        };
        State(UUID world) {
            this.world = world;
            executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), task -> {
                Thread thread = new Thread(task, "Atlasbound map IO"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        }
    }
}
