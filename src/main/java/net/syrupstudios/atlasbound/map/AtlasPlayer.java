package net.syrupstudios.atlasbound.map;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A player visible in the current client world. */
public record AtlasPlayer(UUID id, String name, ResourceKey<Level> dimension, Vec3 pos, float yaw, boolean online) {
    public static AtlasPlayer from(Player player) {
        return new AtlasPlayer(player.getUUID(), player.getGameProfile().getName(), player.level().dimension(),
                player.position(), player.getYRot(), true);
    }
}
