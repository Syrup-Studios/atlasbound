package folk.sisby.antique_atlas.gui;

import net.syrupstudios.atlasbound.Atlasbound;
import folk.sisby.antique_atlas.MarkerTexture;
import folk.sisby.antique_atlas.gui.core.Component;
import folk.sisby.antique_atlas.gui.core.ScrollBoxComponent;
import folk.sisby.antique_atlas.gui.core.ToggleButtonRadioGroup;
import folk.sisby.antique_atlas.reloader.MarkerTextures;
import folk.sisby.antique_atlas.util.ColorUtil;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import net.syrupstudios.atlasbound.map.AtlasLandmark;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * This GUI is used select marker icon and enter a label.
 * When the user clicks on the confirmation button, the call to MarkerAPI is made.
 *
 * @author Hunternif
 */
public class MarkerModal extends Component {
	protected ResourceLocation dimension;
	protected RegistryAccess manager;
	protected AtlasLandmark baseLandmark = null;

	protected MarkerTexture selectedTexture = MarkerTexture.DEFAULT;
	protected DyeColor selectedColor = DyeColor.WHITE;

	public static final int BUTTON_WIDTH = 80;
	public static final int BUTTON_SPACING = 8;

	public static final int TYPE_SPACING = 1;

	protected Button btnDone;
	protected Button btnCancel;
	protected EditBox textField;
	protected ScrollBoxComponent textureScrollBox;
	protected ToggleButtonRadioGroup<TexturePreviewButton<MarkerTexture>> textureRadioGroup;
	protected ScrollBoxComponent colorScrollBox;
	protected ToggleButtonRadioGroup<TexturePreviewButton<DyeColor>> colorRadioGroup;
	protected Map<MarkerTexture, TexturePreviewButton<MarkerTexture>> textureButtons = new LinkedHashMap<>();
	protected Map<DyeColor, TexturePreviewButton<DyeColor>> colorButtons = new LinkedHashMap<>();

	protected final List<IMarkerTypeSelectListener> markerListeners = new ArrayList<>();

	public MarkerModal() {
	}

	void setMarkerData(ResourceLocation dimension, RegistryAccess manager, AtlasLandmark baseLandmark) {
		this.dimension = dimension;
		this.manager = manager;
		this.baseLandmark = baseLandmark;
		this.selectedColor = Arrays.stream(DyeColor.values()).filter(d -> d.getTextureDiffuseColor() == baseLandmark.color()).findAny().orElse(DyeColor.WHITE);
		this.selectedTexture = MarkerTextures.getInstance().fromLandmark(baseLandmark);
		if (!selectedTexture.keyId().getPath().startsWith("custom/")) selectedTexture = textureButtons.keySet().stream().findFirst().orElse(MarkerTexture.DEFAULT);
		if (colorRadioGroup != null) updateSelected();
	}

	void addMarkerListener(IMarkerTypeSelectListener listener) {
		markerListeners.add(listener);
	}

	protected void updateSelected() {
		colorRadioGroup.setSelectedButton(colorButtons.get(selectedColor));
		textureRadioGroup.setSelectedButton(textureButtons.get(selectedTexture));
	}

	@Override
	public void init() { // set up in here because it scales to parent size
		removeAllChildren();
		super.init();

		addRenderableWidget(btnDone = Button.builder(net.minecraft.network.chat.Component.translatable("gui.done"), (button) -> {
			var label = net.minecraft.network.chat.Component.literal(textField.getValue());
			Item item = manager.registryOrThrow(Registries.ITEM).get(selectedTexture.item());
			ItemStack stack = item == null ? baseLandmark.stack() : item.getDefaultInstance().copy();
			var pos = baseLandmark.pos();
			ResourceLocation id = selectedTexture.keyId().withSuffix("/" + selectedColor.getName() + "/" + pos.getX() + "/" + pos.getZ());
			AtlasLandmark updated = new AtlasLandmark(baseLandmark.owner(), id, pos, baseLandmark.chunks(), label,
					baseLandmark.lore(), selectedColor.getTextureDiffuseColor(), stack, baseLandmark.time(), baseLandmark.seed());
			AtlasClientState.submitMarker(dimension,
					baseLandmark.id().equals(Atlasbound.legacyId("newmarker")) ? null : baseLandmark, updated);
			((AtlasScreen) getParent()).updateBookmarkerList();
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1F));
			closeChild();
		}).bounds(this.width / 2 - BUTTON_WIDTH - BUTTON_SPACING / 2, this.height / 2 + 70, BUTTON_WIDTH, 20).build());
		addRenderableWidget(btnCancel = Button.builder(net.minecraft.network.chat.Component.translatable("gui.cancel"), (button) -> closeChild())
			.bounds(this.width / 2 + BUTTON_SPACING / 2, this.height / 2 + 70, BUTTON_WIDTH, 20).build());
		textField = new EditBox(Minecraft.getInstance().font, (this.width - 200) / 2, this.height / 2 - 65, 200, 20, net.minecraft.network.chat.Component.translatable("gui.antique_atlas.marker.label"));
		textField.setEditable(true);
		textField.setCanLoseFocus(true);
		textField.setFocused(true);
		textField.setHint(net.minecraft.network.chat.Component.translatable("gui.antique_atlas.marker.label"));
		textField.setValue(baseLandmark.name() == null ? "" : baseLandmark.name().getString());

		textureScrollBox = new ScrollBoxComponent(false, (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING));
		this.addChild(textureScrollBox);

		int typeCount = (int) MarkerTextures.getInstance().asMap().values().stream().filter(t -> t.keyId().getPath().startsWith("custom/")).count();
		int typesOnScreen = Math.min(typeCount, 7);
		int typeScrollWidth = typesOnScreen * (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING) - TYPE_SPACING;
		textureScrollBox.getViewport().setSize(typeScrollWidth, TexturePreviewButton.FRAME_SIZE + TYPE_SPACING);
		textureScrollBox.setGuiCoords((this.width - typeScrollWidth) / 2, this.height / 2 - 35);

		textureRadioGroup = new ToggleButtonRadioGroup<>();
		textureRadioGroup.addListener(button -> {
			selectedTexture = button.getValue();
			for (IMarkerTypeSelectListener listener : markerListeners) {
				listener.onSelectMarkerType(button.getValue());
			}
		});
		int contentX = 0;
		for (MarkerTexture texture : MarkerTextures.getInstance().asMap().values()) {
			if (!texture.keyId().getPath().startsWith("custom/")) continue;
			if (selectedTexture == MarkerTexture.DEFAULT) selectedTexture = texture;
			TexturePreviewButton<MarkerTexture> markerGui = new MarkerPreviewButton(texture, ColorUtil.componentsFromRgb(selectedColor.getFireworkColor()));
			textureButtons.put(texture, markerGui);
			textureRadioGroup.addButton(markerGui);
			textureScrollBox.getViewport().addContent(markerGui).setRelativeX(contentX);
			contentX += TexturePreviewButton.FRAME_SIZE + TYPE_SPACING;
		}

		// Color

		colorScrollBox = new ScrollBoxComponent(false, (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING));
		this.addChild(colorScrollBox);

		int colorsOnScreen = Math.min(DyeColor.values().length, 7);
		int colorScrollWidth = colorsOnScreen * (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING) - TYPE_SPACING;
		colorScrollBox.getViewport().setSize(colorScrollWidth, TexturePreviewButton.FRAME_SIZE + TYPE_SPACING);
		colorScrollBox.setGuiCoords((this.width - colorScrollWidth) / 2, this.height / 2 + 10);

		colorRadioGroup = new ToggleButtonRadioGroup<>();
		colorRadioGroup.addListener(button -> {
			selectedColor = button.getValue();
			for (TexturePreviewButton<MarkerTexture> preview : textureRadioGroup) {
				preview.reTint(ColorUtil.componentsFromRgb(selectedColor.getFireworkColor()));
			}
		});
		int colorContentX = 0;
		for (DyeColor color : DyeColor.values()) {
			TexturePreviewButton<DyeColor> colorGui = new TexturePreviewButton<>(color, BookmarkButton.TEXTURE_LEFT, BookmarkButton.WIDTH, BookmarkButton.HEIGHT, BookmarkButton.HEIGHT, ColorUtil.componentsFromRgb(color.getFireworkColor()));
			colorButtons.put(color, colorGui);
			colorRadioGroup.addButton(colorGui);
			colorScrollBox.getViewport().addContent(colorGui).setRelativeX(colorContentX);
			colorContentX += TexturePreviewButton.FRAME_SIZE + TYPE_SPACING;
		}

		updateSelected();
	}

	@Override
	public void closeChild() {
		super.closeChild();
		if (textureScrollBox != null) {
			textureScrollBox.closeChild();
		}
		if (colorScrollBox != null) {
			colorScrollBox.closeChild();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return super.mouseClicked(mouseX, mouseY, button) || textField.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int aa, int bb, int cc) {
		return super.keyPressed(aa, bb, cc) || textField.keyPressed(aa, bb, cc);
	}

	@Override
	public boolean charTyped(char aa, int bb) {
		return super.charTyped(aa, bb) || textField.charTyped(aa, bb);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(context, mouseX, mouseY, partialTick);
		drawCentered(context, net.minecraft.network.chat.Component.translatable("gui.antique_atlas.marker.label"), this.height / 2 - 80, 0xDDDDDD, true);
		btnCancel.render(context, mouseX, mouseY, partialTick);
		btnDone.render(context, mouseX, mouseY, partialTick);
		textField.render(context, mouseX, mouseY, partialTick);
		// Darker background for marker type selector
		context.fillGradient(textureScrollBox.getGuiX() + 1, textureScrollBox.getGuiY() + 1,
			textureScrollBox.getGuiX() + textureScrollBox.getWidth(),
			textureScrollBox.getGuiY() + textureScrollBox.getHeight(),
			0x88101010, 0x99101010);
		context.fillGradient(colorScrollBox.getGuiX() + 1, colorScrollBox.getGuiY() + 1,
			colorScrollBox.getGuiX() + colorScrollBox.getWidth(),
			colorScrollBox.getGuiY() + colorScrollBox.getHeight(),
			0x88101010, 0x99101010);
		super.render(context, mouseX, mouseY, partialTick);
	}

	public interface IMarkerTypeSelectListener {
		void onSelectMarkerType(MarkerTexture texture);
	}
}
