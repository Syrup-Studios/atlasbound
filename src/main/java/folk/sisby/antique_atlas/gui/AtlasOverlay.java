package folk.sisby.antique_atlas.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.syrupstudios.atlasbound.map.AtlasPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

public interface AtlasOverlay {
	default void onScreenInit(AtlasScreen screen) {
	}

	default void onScreenRender(AtlasScreenRenderContext context) {
		onRender(new AtlasRenderContext(context.screen(), context.context().pose(), null, context.mouseX(), context.mouseY(), AtlasScreen.MAX_LIGHT, context.markerScale(), context.friends()));
	}

	default void onRender(AtlasRenderContext context) {
	}

	record AtlasScreenRenderContext(AtlasScreen screen, GuiGraphics context, int mouseX, int mouseY, float markerScale, Map<UUID, AtlasPlayer> friends) {
	}

	record AtlasRenderContext(AtlasRenderer renderer, PoseStack matrices, MultiBufferSource vertexConsumers, @Nullable Integer mouseX, @Nullable Integer mouseY, int light, float markerScale, Map<UUID, AtlasPlayer> friends) {
	}
}
