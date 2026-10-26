package net.syrupstudios.atlasbound.client;

import folk.sisby.antique_atlas.AntiqueAtlasKeybindings;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import net.rasanovum.rosetta.entrypoint.RosettaClientEntrypoint;
import net.syrupstudios.atlasbound.Atlasbound;

public final class AtlasboundClient implements RosettaClientEntrypoint {
    public static void onResourceManagerReload(ResourceManager manager) {
        AtlasClientState.resourceReloaded(manager);
    }

    @Override public void initialize() {
        Atlasbound.Client.initialize();
        //? if fabric
        AtlasResourceReloadListener.register();
    }
    @Override public void onDisconnect() { AtlasClientState.clear(true); }
    @Override public void onJoin(Player player) { AtlasClientState.clear(true); }
    @Override public void onClientStopping() { AtlasClientState.clearCache(); }
    @Override public void onEndClientTick() {
        Minecraft client = Minecraft.getInstance();
        AntiqueAtlasKeybindings.onClientTick(client);
        AtlasClientState.tick(client);
        AtlasMapClient.tick();
    }
}
