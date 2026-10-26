package net.syrupstudios.atlasbound;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AtlasCopyInputSlot extends Slot {
    private final Slot original;
    private final int input;

    public AtlasCopyInputSlot(Slot original, int input) {
        super(original.container, original.getContainerSlot(), original.x, original.y);
        this.original = original;
        this.input = input;
        this.index = original.index;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (input == 0 && AtlasItemData.isAtlas(stack) && stack.getCount() == 1 && AtlasItemData.id(stack).isPresent()) return true;
        if (input == 1 && stack.is(Items.BOOK)) return true;
        return original.mayPlace(stack);
    }
}
