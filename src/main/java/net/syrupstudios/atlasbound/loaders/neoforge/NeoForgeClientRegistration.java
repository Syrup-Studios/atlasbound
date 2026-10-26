//? if neoforge {
/*
package net.syrupstudios.atlasbound.loaders.neoforge;

import folk.sisby.antique_atlas.AntiqueAtlasKeybindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.antique_atlas.reloader.MarkerTextures;
import folk.sisby.antique_atlas.reloader.StructureTileProviders;
import folk.sisby.antique_atlas.reloader.TileTextures;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.client.AtlasboundClient;

@EventBusSubscriber(modid = Atlasbound.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeClientRegistration {
    private NeoForgeClientRegistration() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(AntiqueAtlasKeybindings.ATLAS_KEYMAPPING);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(TileTextures.getInstance());
        event.registerReloadListener(MarkerTextures.getInstance());
        event.registerReloadListener(StructureTileProviders.getInstance());
        event.registerReloadListener(BiomeTileProviders.getInstance());
        event.registerReloadListener((ResourceManagerReloadListener) AtlasboundClient::onResourceManagerReload);
    }

    @SubscribeEvent
    public static void addAtlasToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)
            event.accept(Atlasbound.ATLAS.get());
    }
}
*/
//?}
