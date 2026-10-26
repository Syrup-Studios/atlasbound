package folk.sisby.antique_atlas.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


/**
 * A GUI element that follows the mouse cursor and is meant to replace it.
 *
 * @author Hunternif
 */
public class CursorComponent extends Component {

	protected ResourceLocation texture;
	protected int textureWidth, textureHeight;
	/**
	 * Coordinates of the cursor point on the texture.
	 */
	protected int pointX, pointY;

	/**
	 * @param texture texture image file
	 * @param width   cursor width
	 * @param height  cursor height
	 * @param pointX  X of the cursor point on the image
	 * @param pointY  Y of the cursor point on the image
	 */
	public void setTexture(ResourceLocation texture, int width, int height, int pointX, int pointY) {
		this.texture = texture;
		this.textureWidth = width;
		this.textureHeight = height;
		this.pointX = pointX;
		this.pointY = pointY;
	}

	@Override
	public int getWidth() {
		return 0;
	}

	@Override
	public int getHeight() {
		return 0;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
		context.blit(texture, mouseX - pointX, mouseY - pointY, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
	}
}
