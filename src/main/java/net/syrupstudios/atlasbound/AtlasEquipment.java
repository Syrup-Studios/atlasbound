package net.syrupstudios.atlasbound;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.rasanovum.rosetta.loaders.Platform;

/** Optional accessory APIs are isolated so their classes stay unloaded when absent. */
public final class AtlasEquipment {
    private AtlasEquipment() {}

    public static ItemStack getAtlas(LivingEntity entity) {
        //? if neoforge {
        /*
        if (Platform.INSTANCE.isModLoaded("curios")) {
            ItemStack atlas = CuriosCompat.getAtlas(entity);
            if (AtlasItemData.isAtlas(atlas)) return atlas;
        }
        *///?}
        //? if fabric {
        return Platform.INSTANCE.isModLoaded("trinkets")
                ? TrinketsCompat.getAtlas(entity) : ItemStack.EMPTY;
        //?} else {
        /*return ItemStack.EMPTY;*/
        //?}
    }

    public static List<ItemStack> getAtlases(LivingEntity entity) {
        List<ItemStack> atlases = new ArrayList<>();
        //? if neoforge {
        /*
        if (Platform.INSTANCE.isModLoaded("curios")) atlases.addAll(CuriosCompat.getAtlases(entity));
        *///?}
        //? if fabric {
        if (Platform.INSTANCE.isModLoaded("trinkets")) atlases.addAll(TrinketsCompat.getAtlases(entity));
        //?}
        return atlases;
    }
}
