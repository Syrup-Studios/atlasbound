package folk.sisby.antique_atlas;

import net.syrupstudios.atlasbound.Atlasbound;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import folk.sisby.antique_atlas.util.DrawBatcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Vector2d;

public record MarkerTexture(ResourceLocation id, ResourceLocation accentId, ResourceLocation item, int offsetX, int offsetY, int textureWidth, int textureHeight, int mipLevels, int nearClip, int farClip) {
	public static ResourceLocation idToTexture(ResourceLocation id) {
		return id.withPrefix("textures/atlas/marker/").withSuffix(".png");
	}

	public static MarkerTexture ofId(ResourceLocation id, ResourceLocation item, int offsetX, int offsetY, int width, int height, int mipLevels, int nearClip, int farClip, boolean accent) {
		return new MarkerTexture(idToTexture(id), accent ? idToTexture(id.withSuffix("_accent")) : null, item, offsetX, offsetY, width, height, mipLevels, nearClip, farClip);
	}

	public static MarkerTexture centered(ResourceLocation id, ResourceLocation item, int width, int height, int mipLevels, int nearClip, int farClip, boolean accent) {
		return ofId(id, item, -width / 2, -height / 2, width, height, mipLevels, nearClip, farClip, accent);
	}

	public static final MarkerTexture DEFAULT = centered(Atlasbound.legacyId("custom/point"), ResourceLocation.fromNamespaceAndPath("minecraft", "emerald"), 32, 32, 0, 1, Integer.MAX_VALUE, true);

	public ResourceLocation keyId() {
		return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring("textures/atlas/marker/".length(), id.getPath().length() - 4));
	}

	public String displayId() {
		return id.getNamespace().equals(Atlasbound.LEGACY_RESOURCE_NAMESPACE) ? keyId().getPath() : keyId().toString();
	}

	public int fullTextureWidth() {
		int width = textureWidth;
		for (int i = 0; i < mipLevels; i++) {
			width += textureWidth >> (i + 1);
		}
		return width;
	}

	public int getU(int mipLevel) {
		int currentMipLevel = mipLevel - 1;
		int u = 0;
		while (currentMipLevel >= 0) {
			u += textureWidth / (1 << currentMipLevel);
			currentMipLevel--;
		}
		return u;
	}

	public Vector2d getCenter(int tileChunks) {
		int mipLevel = Mth.clamp(Mth.ceillog2(tileChunks), 0, mipLevels);
		return new Vector2d(((double) offsetX + (double) textureWidth / 2.0) / (double) (1 << mipLevel), ((double) offsetY + (double) textureHeight / 2.0) / (double) (1 << mipLevel));
	}

	public double getSquaredSize(int tileChunks) {
		int mipLevel = Mth.clamp(Mth.ceillog2(tileChunks), 0, mipLevels);
		return textureWidth * textureHeight / (double) (1 << mipLevel);
	}

	public void drawIcon(GuiGraphics context, int x, int y, float[] accent) {
		context.blit(id, x, y, 0, 0, textureWidth, textureHeight, fullTextureWidth(), textureHeight);
		if (accentId != null && accent != null) {
			RenderSystem.setShaderColor(accent[0], accent[1], accent[2], 1F);
			context.blit(accentId, x, y, 0, 0, textureWidth, textureHeight, fullTextureWidth(), textureHeight);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	public void draw(PoseStack matrices, MultiBufferSource vertexConsumers, double markerX, double markerY, float z, float markerScale, int tileChunks, float[] accent, float tint, float alpha, int light) {
		if (alpha == 0) return;
		matrices.pushPose();
		matrices.translate(markerX, markerY, 0.0);
		matrices.scale(markerScale, markerScale, 1.0F);
		int mainArgb = FastColor.ARGB32.color((int) (alpha * 255), (int) (tint * 255), (int) (tint * 255), (int) (tint * 255));
		int accentArgb = accent != null ? FastColor.ARGB32.color((int) (alpha * 255), (int) (tint * accent[0] * 255), (int) (tint * accent[1] * 255), (int) (tint * accent[2] * 255)) : 0;
		if (tileChunks > 1 && mipLevels > 0) {
			int mipLevel = Mth.clamp(Mth.ceillog2(tileChunks), 0, mipLevels);
			DrawBatcher.drawSingle(matrices, vertexConsumers, id, fullTextureWidth(), textureHeight, light, offsetX / (1 << mipLevel), offsetY / (1 << mipLevel), z, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), getU(mipLevel), 0, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), mainArgb, false);
			if (accentId != null && accent != null) {
				DrawBatcher.drawSingle(matrices, vertexConsumers, accentId, fullTextureWidth(), textureHeight, light, offsetX / (1 << mipLevel), offsetY / (1 << mipLevel), z, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), getU(mipLevel), 0, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), accentArgb, false);
			}
		} else {
			DrawBatcher.drawSingle(matrices, vertexConsumers, id, fullTextureWidth(), textureHeight, light, offsetX, offsetY, z, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, mainArgb, false);
			if (accentId != null && accent != null) {
				DrawBatcher.drawSingle(matrices, vertexConsumers, accentId, fullTextureWidth(), textureHeight, light, offsetX, offsetY, z, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, accentArgb, false);
			}
		}
		matrices.popPose();
	}
}
