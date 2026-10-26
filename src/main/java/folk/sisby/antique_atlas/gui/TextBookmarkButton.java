package folk.sisby.antique_atlas.gui;

import net.syrupstudios.atlasbound.Atlasbound;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class TextBookmarkButton extends BookmarkButton {
	public static final ResourceLocation RULER_TEXTURE_RIGHT = Atlasbound.legacyId("textures/gui/ruler_right.png");
	public static final int SHORT_WIDTH = 20;
	protected Component label;

	public TextBookmarkButton(Component title, Component label) {
		super(RULER_TEXTURE_RIGHT, title, null, null, null, 32, 32, false, false);
		this.clickSound = null;
		this.label = label;
	}

	public void setLabel(Component label) {
		this.label = label;
	}

	@Override
	public void renderTooltip(GuiGraphics context, int mouseX, int mouseY, float partialTick, boolean mouseOver) {
		boolean isExtended = mouseOver || isSelected();
		int centerOffsetX = (SHORT_WIDTH - font.width(label)) / 2;
		context.drawString(font, label, getGuiX() + centerOffsetX + (isExtended ? 3 : 0), getGuiY() + 5, 0xFF000000, false);
		if (!label.getString().equals("1c")) super.renderTooltip(context, mouseX, mouseY, partialTick, mouseOver);
	}
}
