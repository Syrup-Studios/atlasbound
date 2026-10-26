package folk.sisby.antique_atlas.gui;

import net.syrupstudios.atlasbound.Atlasbound;
import com.mojang.blaze3d.systems.RenderSystem;
import folk.sisby.antique_atlas.gui.core.ToggleButtonComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


public class TexturePreviewButton<T> extends ToggleButtonComponent {
	public static final ResourceLocation FRAME_SELECTED = Atlasbound.legacyId("textures/gui/frame_selected.png");
	public static final ResourceLocation FRAME_UNSELECTED = Atlasbound.legacyId("textures/gui/frame.png");
	public static final int FRAME_SIZE = 34;

	protected final T value;
	protected final ResourceLocation texture;
	protected final int textureWidth;
	protected final int textureHeight;
	protected final int v;
	protected float[] tint;

	public TexturePreviewButton(T value, ResourceLocation texture, int textureWidth, int textureHeight, int v, float[] tint) {
		super(false);
		this.value = value;
		this.texture = texture;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.v = v;
		this.tint = tint;
		setSize(FRAME_SIZE, FRAME_SIZE);
	}

	public T getValue() {
		return value;
	}

	public void reTint(float[] tint) {
		if (this.tint != null) this.tint = tint;
	}

	protected void drawTexture(GuiGraphics context, int x, int y) {
		if (tint != null) RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1.0F);
		context.blit(texture, x, y, 0, v, textureWidth, textureHeight, textureWidth, textureHeight + v);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
		ResourceLocation frameTexture = isSelected() ? FRAME_SELECTED : FRAME_UNSELECTED;
		context.blit(frameTexture, getGuiX() + 1, getGuiY() + 1, 0, 0, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

		int centerX = getGuiX() + (FRAME_SIZE - textureWidth) / 2;
		int centerY = getGuiY() + (FRAME_SIZE - textureHeight) / 2;
		drawTexture(context, centerX, centerY);

		super.render(context, mouseX, mouseY, partialTick);
	}
}
