package net.syrupstudios.atlasbound.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.syrupstudios.atlasbound.AtlasItemData;
import net.syrupstudios.atlasbound.AtlasStorage;
import net.syrupstudios.atlasbound.network.AtlasNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class AtlasItemUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void atlasbound$use(Level level, Player player, InteractionHand hand,
                                CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!AtlasItemData.isAtlas(stack)) return;
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? serverPlayer.getInventory().selected : 40;
            AtlasNetworking.open(serverPlayer, slot);
        }
        // Skip AA4's item-use GUI path; the server opens our possession-checked atlas view.
        callback.setReturnValue(InteractionResultHolder.sidedSuccess(stack, level.isClientSide()));
    }

    @Inject(method = "onCraftedBy", at = @At("TAIL"))
    private void atlasbound$crafted(Level level, Player player, int amount, CallbackInfo callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && AtlasItemData.isAtlas(stack)) {
            // Stamp crafted stacks before they can be copied or moved into containers.
            AtlasItemData.ensureId(serverPlayer, stack).ifPresent(id -> {
                try {
                    AtlasStorage.get(serverPlayer.getServer(), id);
                } catch (IllegalStateException ignored) {
                    // Keep the crafted item; invalid saved data remains disabled for this identity.
                }
            });
        }
    }
}
