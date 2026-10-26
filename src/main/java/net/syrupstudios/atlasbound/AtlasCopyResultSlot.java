package net.syrupstudios.atlasbound;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AtlasCopyResultSlot extends Slot {
    private final Slot original;
    private final Container inputs;

    public AtlasCopyResultSlot(Slot original, Container inputs) {
        super(original.container, original.getContainerSlot(), original.x, original.y);
        this.original = original;
        this.inputs = inputs;
        this.index = original.index;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return original.mayPlace(stack);
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        ItemStack atlas = inputs.getItem(0);
        boolean preserve = AtlasItemData.canCopyAtlas(atlas, inputs.getItem(1));
        ItemStack snapshot = preserve ? atlas.copy() : ItemStack.EMPTY;
        try {
            original.onTake(player, stack);
        } finally {
            if (preserve) inputs.setItem(0, snapshot);
        }
    }
}
