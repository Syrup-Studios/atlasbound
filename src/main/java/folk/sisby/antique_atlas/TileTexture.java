package folk.sisby.antique_atlas;

import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.AtlasboundConfig;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public record TileTexture(ResourceLocation id, boolean innerBorder, Set<TileTexture> tilesTo, Set<TileTexture> tilesToHorizontal, Set<TileTexture> tilesToVertical) {
	public static TileTexture empty(ResourceLocation id, boolean innerBorder) {
		return new TileTexture(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/atlas/tile/%s.png".formatted(id.getPath())), innerBorder, new ReferenceOpenHashSet<>(), new ReferenceOpenHashSet<>(), new ReferenceOpenHashSet<>());
	}

	public static final TileTexture DEFAULT = empty(Atlasbound.legacyId(Atlasbound.Client.CONFIG.fallbackFailHandling.get() == AtlasboundConfig.FallbackHandling.TEST ? "test" : "missing"), false);

	public String displayId() {
		ResourceLocation trimmed = id.withPath(p -> p.substring("textures/atlas/tile/".length(), id.getPath().length() - 4));
		return id.getNamespace().equals(Atlasbound.LEGACY_RESOURCE_NAMESPACE) ? trimmed.getPath() : trimmed.toString();
	}

	public boolean tiles(TileTexture other) {
		return this == other || (innerBorder ^ (tilesTo.contains(other) || tilesToHorizontal.contains(other) || tilesToVertical.contains(other)));
	}

	public boolean tilesHorizontally(TileTexture other) {
		return this == other || (innerBorder ^ (tilesTo.contains(other) || tilesToHorizontal.contains(other)));
	}

	public boolean tilesVertically(TileTexture other) {
		return this == other || (innerBorder ^ (tilesTo.contains(other) || tilesToVertical.contains(other)));
	}

	public record Builder(ResourceLocation id, boolean innerBorder, Set<ResourceLocation> tilesTo, Set<ResourceLocation> tilesToHorizontal, Set<ResourceLocation> tilesToVertical) {
		public void build(Map<ResourceLocation, TileTexture> emptyTextures) {
			if (!tilesTo.isEmpty()) emptyTextures.get(id).tilesTo.addAll(tilesTo.stream().map(emptyTextures::get).collect(Collectors.toSet()));
			if (!tilesToHorizontal.isEmpty()) emptyTextures.get(id).tilesToHorizontal.addAll(tilesToHorizontal.stream().map(emptyTextures::get).collect(Collectors.toSet()));
			if (!tilesToVertical.isEmpty()) emptyTextures.get(id).tilesToVertical.addAll(tilesToVertical.stream().map(emptyTextures::get).collect(Collectors.toSet()));
		}
	}
}
