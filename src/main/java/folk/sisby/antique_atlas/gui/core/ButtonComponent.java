package folk.sisby.antique_atlas.gui.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * A GuiComponent that can act like a button.
 */
@SuppressWarnings("rawtypes")
public class ButtonComponent extends Component {
	protected final List<IButtonListener> listeners = new ArrayList<>();

	protected SoundEvent clickSound = SoundEvents.UI_BUTTON_CLICK.value();

	@Override
	public boolean mouseClicked(double x, double y, int mouseButton) {
		if (!isClipped && mouseButton == 0 && isMouseOver(x, y)) {
			onClick();
			return true;
		}

		return super.mouseClicked(x, y, mouseButton);
	}

	/**
	 * Called when the user left-clicks on this component.
	 */
	@SuppressWarnings("unchecked")
	public void onClick() {
		if (clickSound != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(clickSound, 1.0F));
		}

		for (IButtonListener listener : listeners) {
			listener.onClick(this);
		}
	}

	public void addListener(IButtonListener listener) {
		listeners.add(listener);
	}
}
