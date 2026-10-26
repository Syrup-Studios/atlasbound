package folk.sisby.antique_atlas;

import net.syrupstudios.atlasbound.Atlasbound;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;


public class AntiqueAtlasKeybindings {
	public static final KeyMapping ATLAS_KEYMAPPING = new KeyMapping("key.antique_atlas.open", InputConstants.Type.KEYSYM, 77, "key.antique_atlas.category");

	public static void onClientTick(Minecraft client) {
		while (ATLAS_KEYMAPPING.consumeClick()) Atlasbound.Client.openAtlasScreen();
	}
}
