//? if neoforge {
/*
package net.syrupstudios.atlasbound;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// Curios API calls are isolated so the API stays optional at runtime.
public final class CuriosCompat {
    private CuriosCompat() {}

    public static ItemStack getAtlas(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(handler -> handler.findFirstCurio(Atlasbound.ATLAS.get()))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    public static List<ItemStack> getAtlases(LivingEntity entity) {
        List<ItemStack> atlases = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> handler.getCurios().values().forEach(curio -> {
            var stacks = curio.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (AtlasItemData.isAtlas(stack)) atlases.add(stack);
            }
        }));
        return atlases;
    }
}
*///?}
