package net.syrupstudios.atlasbound;

import java.util.UUID;
import java.util.Set;
import java.util.WeakHashMap;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Stores each atlas under a separate key in the overworld data storage. */
public final class AtlasStorage {
    private static final java.util.Map<MinecraftServer, Set<UUID>> DISABLED = new WeakHashMap<>();
    private AtlasStorage() {}

    public static AtlasData get(MinecraftServer server, UUID id) {
        if (DISABLED.getOrDefault(server, Set.of()).contains(id))
            throw new IllegalStateException("Atlas storage is disabled for " + id);
        String key = Atlasbound.MOD_ID + "_" + id;
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(key + ".dat");
        try {
            var storage = server.overworld().getDataStorage();
            AtlasData loaded = storage.get(AtlasData.FACTORY, key);
            if (loaded != null) return loaded;
            if (Files.exists(file)) throw new IllegalStateException("Saved atlas file could not be loaded");
            Atlasbound.LOGGER.debug("No saved data exists yet for atlas {}; starting empty", id);
            AtlasData created = storage.computeIfAbsent(AtlasData.FACTORY, key);
            created.setDirty();
            return created;
        } catch (Exception exception) {
            DISABLED.computeIfAbsent(server, ignored -> new java.util.HashSet<>()).add(id);
            Atlasbound.LOGGER.error("Disabled atlas {} because its saved data is invalid; original file is preserved", id, exception);
            throw new IllegalStateException("Invalid saved data for atlas " + id, exception);
        }
    }

    public static void clear(MinecraftServer server) {
        DISABLED.remove(server);
    }
}
