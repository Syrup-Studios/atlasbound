package net.syrupstudios.atlasbound.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
//? if fabric {
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.server.packs.PackType;
//?}

final class AtlasResourceReloadListener
//? if fabric {
        implements SimpleSynchronousResourceReloadListener, IdentifiableResourceReloadListener
//?}
{
    //? if fabric {
    static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new AtlasResourceReloadListener());
    }

    @Override public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath("atlasbound", "map_tiles"); }
    @Override public java.util.Collection<ResourceLocation> getFabricDependencies() {
        return java.util.List.of(folk.sisby.antique_atlas.reloader.BiomeTileProviders.ID,
                folk.sisby.antique_atlas.reloader.StructureTileProviders.ID,
                folk.sisby.antique_atlas.reloader.TileTextures.ID,
                folk.sisby.antique_atlas.reloader.MarkerTextures.ID);
    }
    //?}

    public void onResourceManagerReload(ResourceManager manager) {
        AtlasClientState.resourceReloaded(manager);
    }
}
