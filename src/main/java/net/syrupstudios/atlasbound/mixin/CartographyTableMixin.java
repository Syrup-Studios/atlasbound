package net.syrupstudios.atlasbound.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.syrupstudios.atlasbound.AtlasCopyResultSlot;
import net.syrupstudios.atlasbound.AtlasCopyInputSlot;
import net.syrupstudios.atlasbound.AtlasItemData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMixin extends AbstractContainerMenu {
    protected CartographyTableMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Shadow @Final public Container container;
    @Shadow @Final private ResultContainer resultContainer;

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void atlasbound$updateCopyResult(Container changed, CallbackInfo ci) {
        ItemStack atlas = container.getItem(0);
        ItemStack book = container.getItem(1);
        ItemStack result = resultContainer.getItem(2);
        if (AtlasItemData.isAtlas(atlas)) {
            resultContainer.setItem(2, AtlasItemData.canCopyAtlas(atlas, book) ? atlas.copyWithCount(1) : ItemStack.EMPTY);
            broadcastChanges();
            ci.cancel();
        } else if (AtlasItemData.isAtlas(result)) {
            resultContainer.setItem(2, ItemStack.EMPTY);
        }
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void atlasbound$wrapCopySlots(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        slots.set(0, new AtlasCopyInputSlot(slots.get(0), 0));
        slots.set(1, new AtlasCopyInputSlot(slots.get(1), 1));
        Slot original = slots.get(2);
        slots.set(2, new AtlasCopyResultSlot(original, container));
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void atlasbound$quickMoveCopyInput(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index < 3 || index >= 39) return;
        Slot source = getSlot(index);
        ItemStack stack = source.getItem();
        int target;
        if (AtlasItemData.isAtlas(stack) && stack.getCount() == 1 && AtlasItemData.id(stack).isPresent() && container.getItem(0).isEmpty()) {
            target = 0;
        } else if (stack.is(Items.BOOK)) {
            target = 1;
        } else {
            return;
        }

        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, target, target + 1, false)) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
        source.setChanged();
        cir.setReturnValue(original);
    }
}
