package folk.sisby.antique_atlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import folk.sisby.antique_atlas.gui.HandheldAtlasRenderer;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class MixinHeldItemRenderer {
	@Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
	protected void renderFirstPersonAtlas(PoseStack matrices, MultiBufferSource vertexConsumers, int light, ItemStack stack, CallbackInfo ci) {
		if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return;
		if (!(AtlasClientState.isSelectedAtlas(stack))) return;
		HandheldAtlasRenderer.fromContext(Minecraft.getInstance().player).renderHandheldAtlas(matrices, vertexConsumers, light);
		ci.cancel();
	}

	//? if fabric {
	@ModifyExpressionValue(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z", ordinal = 0))
	protected boolean enableFirstPersonAtlasRenderingFabric(boolean original, AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
		return original || AtlasClientState.isSelectedAtlas(stack);
	}
	//?} else {
	/*
	@ModifyExpressionValue(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;", ordinal = 0))
	protected Item enableFirstPersonAtlasRenderingNeoForge(Item original, AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
		// The native map branch checks this type before it calls the shared map renderer.
		return AtlasClientState.isSelectedAtlas(stack) ? Items.FILLED_MAP : original;
	}
	*///?}
}
