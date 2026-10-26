package folk.sisby.antique_atlas.util;

import net.minecraft.util.FastColor;

public class ColorUtil {
	public static float[] componentsFromRgb(int color) {
		return new float[]{FastColor.ARGB32.red(color) / 255f, FastColor.ARGB32.green(color) / 255f, FastColor.ARGB32.blue(color) / 255f};
	}

	public static int rgbFromComponents(float[] components) {
		return FastColor.ARGB32.color(255, (int) (255 * components[0]), (int) (255 * components[1]), (int) (255 * components[2]));
	}
}
