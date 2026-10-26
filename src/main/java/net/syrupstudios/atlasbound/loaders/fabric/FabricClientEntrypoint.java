//? if fabric {
package net.syrupstudios.atlasbound.loaders.fabric;

import folk.sisby.antique_atlas.AntiqueAtlasKeybindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.antique_atlas.reloader.MarkerTextures;
import folk.sisby.antique_atlas.reloader.StructureTileProviders;
import folk.sisby.antique_atlas.reloader.TileTextures;
import net.minecraft.world.item.CreativeModeTabs;
import net.syrupstudios.atlasbound.Atlasbound;

public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(AntiqueAtlasKeybindings.ATLAS_KEYMAPPING);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(Atlasbound.ATLAS.get()));

        ResourceManagerHelper resources = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        resources.registerReloadListener(TileTextures.getInstance());
        resources.registerReloadListener(MarkerTextures.getInstance());
        resources.registerReloadListener(StructureTileProviders.getInstance());
        resources.registerReloadListener(BiomeTileProviders.getInstance());
    }
}
//?}
