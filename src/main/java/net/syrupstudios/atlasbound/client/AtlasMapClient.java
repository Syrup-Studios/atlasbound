package net.syrupstudios.atlasbound.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.rasanovum.rosetta.network.RosettaNetwork;
import net.syrupstudios.atlasbound.map.AtlasMapData;
import net.syrupstudios.atlasbound.network.AtlasMapPackets;

public final class AtlasMapClient {
    private record Key(ResourceLocation dimension, long pos) {}
    private record Data(long revision, byte[] raw) {}
    private static final int MAX_PENDING = 256;
    private static final int MAX_IN_FLIGHT = 16;
    private static final int MAX_REVISION_RECORDS = 100_000;
    private static final long MAX_RAW_BYTES = 16L * 1024 * 1024;
    private static final long REQUEST_TIMEOUT = 100;
    private static final long REFRESH_TICKS = 100;
    private static final long RETRY_TICKS = 40;
    private static final LinkedHashMap<Key, Long> pending = new LinkedHashMap<>();
    private static final java.util.Set<Key> priorityPending = new java.util.HashSet<>();
    private static final LinkedHashMap<Key, Long> revisions = new LinkedHashMap<>(256, .75f, true);
    private static final Map<Key, Long> inFlight = new java.util.HashMap<>();
    private static final Map<Key, Long> retryAfter = new java.util.HashMap<>();
    private static final LinkedHashMap<Key, Long> checkedAt = new LinkedHashMap<>(256, .75f, true);
    private static final LinkedHashMap<Key, Data> cache = new LinkedHashMap<>(64, .75f, true);
    private static long cacheBytes;
    private static UUID world;
    private static long epoch = -1;
    private static long tick;

    private AtlasMapClient() {}

    public static UUID worldIdentity(ResourceKey<Level> dimension) { return world; }

    public static long revision(ResourceKey<Level> dimension, ChunkPos pos) {
        return revision(dimension, pos, false);
    }

    public static long revisionPriority(ResourceKey<Level> dimension, ChunkPos pos) {
        return revision(dimension, pos, true);
    }

    private static long revision(ResourceKey<Level> dimension, ChunkPos pos, boolean priority) {
        Key key = new Key(dimension.location(), pos.toLong());
        if (!valid(Minecraft.getInstance(), key)) return 0;
        long revision = revisions.getOrDefault(key, 0L);
        if (revision == 0 || tick - checkedAt.getOrDefault(key, 0L) >= REFRESH_TICKS) schedule(key, priority);
        return revision;
    }

    public static byte[] get(ResourceKey<Level> dimension, ChunkPos pos) { return get(dimension, pos, false); }
    public static byte[] getPriority(ResourceKey<Level> dimension, ChunkPos pos) { return get(dimension, pos, true); }

    private static byte[] get(ResourceKey<Level> dimension, ChunkPos pos, boolean priority) {
        Minecraft client = Minecraft.getInstance();
        Key key = new Key(dimension.location(), pos.toLong());
        if (!valid(client, key)) return null;
        Data data = cache.get(key);
        if (tick - checkedAt.getOrDefault(key, 0L) >= REFRESH_TICKS) schedule(key, priority);
        if (data != null) return data.raw();
        revisions.remove(key);
        checkedAt.remove(key);
        schedule(key, priority);
        return null;
    }

    public static void world(AtlasMapPackets.World packet) {
        Minecraft.getInstance().execute(() -> {
            if (packet.epoch() != AtlasClientState.currentEpoch() || AtlasClientState.currentEpoch() < 0) return;
            if (!java.util.Objects.equals(world, packet.world()) || epoch != packet.epoch()) {
                world = packet.world();
                epoch = packet.epoch();
                revisions.clear();
                inFlight.clear();
                pending.clear();
                priorityPending.clear();
                retryAfter.clear();
                cache.clear();
                cacheBytes = 0;
                checkedAt.clear();
            } else {
                epoch = packet.epoch();
            }
        });
    }

    public static void receive(AtlasMapPackets.Chunk packet) {
        Minecraft.getInstance().execute(() -> receiveOnClient(packet));
    }

    private static void receiveOnClient(AtlasMapPackets.Chunk packet) {
        Key key = new Key(packet.dimension(), ChunkPos.asLong(packet.x(), packet.z()));
        Minecraft client = Minecraft.getInstance();
        if (packet.epoch() != epoch || packet.epoch() != AtlasClientState.currentEpoch() || !valid(client, key)) return;
        inFlight.remove(key);
        if (packet.revision() == 0 && packet.compressed().length == 0) {
            retryAfter.put(key, tick + RETRY_TICKS);
            markChecked(key);
            return;
        }
        if (packet.revision() <= 0) return;
        if (packet.compressed().length == 0) {
            Data old = cache.get(key);
            if (old != null && old.revision() == packet.revision()) {
                revisions.put(key, packet.revision());
                markChecked(key);
                return;
            }
            revisions.put(key, packet.revision());
            retryAfter.put(key, tick + RETRY_TICKS);
            markChecked(key);
            trimRevisions();
            return;
        }
        byte[] raw = inflate(packet.compressed());
        if (raw == null) {
            retryAfter.put(key, tick + RETRY_TICKS);
            return;
        }
        try {
            if (!new ChunkPos(packet.x(), packet.z()).equals(AtlasMapData.decode(raw).terrain().pos())) return;
        } catch (RuntimeException invalid) {
            retryAfter.put(key, tick + RETRY_TICKS);
            return;
        }
        revisions.put(key, packet.revision());
        markChecked(key);
        trimRevisions();
        put(key, new Data(packet.revision(), raw));
    }

    public static void tick() {
        tick++;
        inFlight.entrySet().removeIf(entry -> {
            if (entry.getValue() > tick) return false;
            retryAfter.put(entry.getKey(), tick + RETRY_TICKS);
            return true;
        });
        retryAfter.entrySet().removeIf(entry -> entry.getValue() <= tick);
        if (retryAfter.size() > MAX_PENDING) retryAfter.keySet().stream().limit(retryAfter.size() - MAX_PENDING).toList().forEach(retryAfter::remove);
        Iterator<Map.Entry<Key, Long>> iterator = pending.entrySet().iterator();
        int sent = 0;
        while (iterator.hasNext() && sent < 4 && inFlight.size() < MAX_IN_FLIGHT) {
            var entry = iterator.next();
            Key key = entry.getKey();
            iterator.remove();
            priorityPending.remove(key);
            if (!valid(Minecraft.getInstance(), key) || retryAfter.getOrDefault(key, 0L) > tick) continue;
            RosettaNetwork.sendToServer(new AtlasMapPackets.Request(epoch, key.dimension(), ChunkPos.getX(key.pos()),
                    ChunkPos.getZ(key.pos()), revisions.getOrDefault(key, 0L)));
            inFlight.put(key, tick + REQUEST_TIMEOUT);
            sent++;
        }
    }

    public static void clear() {
        world = null;
        epoch = -1;
        tick = 0;
        pending.clear();
        priorityPending.clear();
        revisions.clear();
        inFlight.clear();
        retryAfter.clear();
        cache.clear();
        cacheBytes = 0;
        checkedAt.clear();
    }

    private static boolean valid(Minecraft client, Key key) {
        return epoch >= 0 && epoch == AtlasClientState.currentEpoch() && world != null
                && AtlasClientState.allows(key.dimension(), new ChunkPos(key.pos()))
                && client.getConnection() != null;
    }

    private static void schedule(Key key, boolean priority) {
        if (inFlight.containsKey(key) || retryAfter.getOrDefault(key, 0L) > tick) return;
        if (pending.containsKey(key)) {
            if (priority) {
                priorityPending.add(key);
                pending.remove(key);
                LinkedHashMap<Key, Long> ordered = new LinkedHashMap<>();
                ordered.put(key, tick);
                ordered.putAll(pending);
                pending.clear();
                pending.putAll(ordered);
            }
            return;
        }
        if (pending.size() >= MAX_PENDING) {
            if (!priority) return;
            Key evict = null;
            for (Key queued : pending.keySet()) if (!priorityPending.contains(queued)) evict = queued;
            if (evict == null) return;
            pending.remove(evict);
        }
        if (priority) {
            LinkedHashMap<Key, Long> ordered = new LinkedHashMap<>();
            ordered.put(key, tick);
            ordered.putAll(pending);
            pending.clear();
            pending.putAll(ordered);
            priorityPending.add(key);
        } else pending.put(key, tick);
    }

    private static void put(Key key, Data data) {
        Data old = cache.put(key, data);
        if (old != null) cacheBytes -= old.raw().length;
        cacheBytes += data.raw().length;
        while (cacheBytes > MAX_RAW_BYTES && !cache.isEmpty()) {
            var eldest = cache.entrySet().iterator().next();
            cacheBytes -= eldest.getValue().raw().length;
            cache.remove(eldest.getKey());
        }
    }

    private static void trimRevisions() {
        if (revisions.size() <= MAX_REVISION_RECORDS) return;
        Iterator<Key> iterator = revisions.keySet().iterator();
        while (revisions.size() > MAX_REVISION_RECORDS && iterator.hasNext()) {
            Key key = iterator.next();
            iterator.remove();
            checkedAt.remove(key);
            Data removed = cache.remove(key);
            if (removed != null) cacheBytes -= removed.raw().length;
        }
    }

    private static void markChecked(Key key) {
        checkedAt.put(key, tick);
        while (checkedAt.size() > MAX_REVISION_RECORDS) {
            Key oldest = checkedAt.keySet().iterator().next();
            checkedAt.remove(oldest);
        }
    }

    private static byte[] inflate(byte[] compressed) {
        if (compressed.length > 60 * 1024 + 1024) return null;
        try (var input = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(compressed));
             var output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 60 * 1024) return null;
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (java.io.IOException invalid) {
            return null;
        }
    }
}
