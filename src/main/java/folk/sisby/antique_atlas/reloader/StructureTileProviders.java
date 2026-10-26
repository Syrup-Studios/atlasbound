package folk.sisby.antique_atlas.reloader;

import net.syrupstudios.atlasbound.Atlasbound;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import folk.sisby.antique_atlas.MarkerTexture;
import folk.sisby.antique_atlas.StructureTileProvider;
import folk.sisby.antique_atlas.TileTexture;
import net.syrupstudios.atlasbound.map.AtlasLandmark;
import net.syrupstudios.atlasbound.map.AtlasStructurePiece;
import net.syrupstudios.atlasbound.map.AtlasStructureStart;
import it.unimi.dsi.fastutil.Pair;
//? if fabric {
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

import static folk.sisby.antique_atlas.reloader.BiomeTileProviders.resolveTextureJson;

public class StructureTileProviders extends SimpleJsonResourceReloadListener
//? if fabric
implements IdentifiableResourceReloadListener
{
	public static final StructureTileProviders INSTANCE = new StructureTileProviders();

	public static final ResourceLocation ID = Atlasbound.legacyId("structures");

	public static StructureTileProviders getInstance() {
		return INSTANCE;
	}

	protected final Map<ResourceLocation, StructureTileProvider> startTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> typeTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> tagTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> pieceTypeTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> pieceJigsawSingleTiles = new HashMap<>();
	protected final Map<ResourceLocation, StructureTileProvider> pieceJigsawFeatureTiles = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> startMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> typeMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> tagMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> pieceTypeMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> pieceJigsawSingleMarkers = new HashMap<>();
	protected final Map<ResourceLocation, MarkerTexture> pieceJigsawFeatureMarkers = new HashMap<>();

	public enum ProviderType {
		START("start"),
		TAG("tag"),
		TYPE("type"),
		PIECE_TYPE("piece/type"),
		JIGSAW_SINGLE("piece/jigsaw/single"),
		JIGSAW_FEATURE("piece/jigsaw/feature");

		public final String key;

		ProviderType(String key) {
			this.key = key;
		}

		public String prefix() {
			return key + "/";
		}

		public String translation(ResourceLocation id) {
			return "structure.%s.%s".formatted(key.replace('/', '.'), id.toString().replace(':', '.'));
		}
	}

	protected final Map<ProviderType, Pair<Map<ResourceLocation, StructureTileProvider>, Map<ResourceLocation, MarkerTexture>>> PROVIDER_MAPS = Map.of(
		ProviderType.START, Pair.of(startTiles, startMarkers),
		ProviderType.TYPE, Pair.of(typeTiles, typeMarkers),
		ProviderType.TAG, Pair.of(tagTiles, tagMarkers),
		ProviderType.PIECE_TYPE, Pair.of(pieceTypeTiles, pieceTypeMarkers),
		ProviderType.JIGSAW_SINGLE, Pair.of(pieceJigsawSingleTiles, pieceJigsawSingleMarkers),
		ProviderType.JIGSAW_FEATURE, Pair.of(pieceJigsawFeatureTiles, pieceJigsawFeatureMarkers)
	);

	public StructureTileProviders() {
		super(new Gson(), "atlas/structure");
	}

	public void resolve(Map<ChunkPos, TileTexture> outTiles,
			Map<ChunkPos, StructureTileProvider> structureProviders,
			Map<ChunkPos, String> debugPredicates,
			Map<AtlasLandmark, MarkerTexture> outMarkers,
			AtlasStructureStart start,
			net.minecraft.core.RegistryAccess registries) {
		ChunkPos pos = start.chunk();
		ResourceLocation key = start.key();
		ResourceLocation type = start.type();
		if (startMarkers.containsKey(key)) {
			putStartMarker(outMarkers, key, pos, ProviderType.START.translation(key), startMarkers.get(key));
		} else if (typeMarkers.containsKey(type)) {
			putStartMarker(outMarkers, key, pos, ProviderType.TYPE.translation(type), typeMarkers.get(type));
		} else {
			tagMarkers.entrySet().stream().filter(entry -> start.tags().contains(entry.getKey())).findFirst()
				.ifPresent(entry -> putStartMarker(outMarkers, key, pos,
						ProviderType.TAG.translation(entry.getKey()), entry.getValue()));
		}

		StructureTileProvider provider = startTiles.get(key);
		if (provider == null) provider = typeTiles.get(type);
		if (provider == null) {
			for (ResourceLocation tag : start.tags()) {
				provider = tagTiles.get(tag);
				if (provider != null) break;
			}
		}
		if (provider != null) apply(provider, provider.getTextures(start.bounds(), debugPredicates), outTiles, structureProviders);

		for (AtlasStructurePiece piece : start.pieces()) resolvePiece(outTiles, structureProviders, debugPredicates, piece);
	}

	private void putStartMarker(Map<AtlasLandmark, MarkerTexture> out, ResourceLocation key, ChunkPos chunk,
			String translation, MarkerTexture texture) {
		ResourceLocation id = key.withPath(path -> "start/" + path + "/" + chunk.x + "/" + chunk.z);
		out.put(new AtlasLandmark(AtlasLandmark.STRUCTURE_OWNER, id, chunk.getMiddleBlockPosition(0), Set.of(chunk),
				Component.translatable(translation), List.of(), 0xFFFFFF, net.minecraft.world.item.ItemStack.EMPTY, 0L, 0), texture);
	}

	private void resolvePiece(Map<ChunkPos, TileTexture> outTiles,
			Map<ChunkPos, StructureTileProvider> structureProviders,
			Map<ChunkPos, String> predicates, AtlasStructurePiece piece) {
		StructureTileProvider provider = null;
		if (piece.jigsawId() != null) {
			provider = "feature".equals(piece.jigsawKind() == null ? null : piece.jigsawKind().getPath())
					? pieceJigsawFeatureTiles.get(piece.jigsawId()) : pieceJigsawSingleTiles.get(piece.jigsawId());
		} else {
			provider = pieceTypeTiles.get(piece.type());
		}
		if (provider == null) return;
		apply(provider, provider.getTextures(piece.bounds(), piece.junctions(), predicates), outTiles, structureProviders);
	}

	private static void apply(StructureTileProvider provider, Map<ChunkPos, TileTexture> matches,
			Map<ChunkPos, TileTexture> outTiles, Map<ChunkPos, StructureTileProvider> structureProviders) {
		matches.forEach((pos, texture) -> {
			StructureTileProvider old = structureProviders.get(pos);
			if (old != null && old.priority() < provider.priority()) return;
			outTiles.put(pos, texture);
			structureProviders.put(pos, provider);
		});
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
		Atlasbound.LOGGER.info("[Antique Atlas] Reloading Structure Tile / Marker Providers...");
		Map<ResourceLocation, TileTexture> textures = TileTextures.getInstance().getTextures();
		Set<TileTexture> unusedTextures = new HashSet<>(textures.values().stream().filter(t -> t.id().getPath().startsWith("structure")).toList());

		PROVIDER_MAPS.values().forEach(p -> p.left().clear());
		PROVIDER_MAPS.values().forEach(p -> p.right().clear());
		for (Map.Entry<ResourceLocation, JsonElement> fileEntry : prepared.entrySet()) {
			ResourceLocation fileId = fileEntry.getKey();
			PROVIDER_MAPS.forEach((providerType, pair) -> {
				Map<ResourceLocation, StructureTileProvider> providerMap = pair.left();
				Map<ResourceLocation, MarkerTexture> markerMap = pair.right();
				if (fileId.getPath().startsWith(providerType.prefix())) {
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), fileId.getPath().substring(providerType.prefix().length()));
					try {
						JsonObject fileJson = fileEntry.getValue().getAsJsonObject();
						if (fileJson.has("textures")) {
							JsonElement textureJson = fileJson.get("textures");
							int priority = fileJson.has("priority") ? fileJson.get("priority").getAsInt() : 999;
							List<TileTexture> defaultTextures = resolveTextureJson(textures, textureJson);
							if (defaultTextures != null) {
								StructureTileProvider provider = new StructureTileProvider(id, priority, defaultTextures);
								providerMap.put(provider.id(), provider);
								unusedTextures.removeAll(provider.allTextures());
							} else {
								JsonObject textureObject = textureJson.getAsJsonObject();
								Map<StructureTileProvider.ChunkMatcher, List<TileTexture>> matchers = new HashMap<>();
								for (String matcherKey : textureObject.keySet()) {
									ResourceLocation matcherId = Atlasbound.legacyId(matcherKey);
									StructureTileProvider.ChunkMatcher matcher = StructureTileProvider.getChunkMatcher(matcherId);
									if (matcher == null) throw new IllegalStateException("Matcher %s does not exist!".formatted(matcherId.toString()));
									List<TileTexture> matcherTextures = resolveTextureJson(textures, textureObject.get(matcherKey));
									if (matcherTextures == null) throw new IllegalStateException("Malformed object %s in textures object!".formatted(matcherId.toString()));
									matcherTextures.forEach(unusedTextures::remove);
									matchers.put(matcher, matcherTextures);
								}
								if (matchers.isEmpty()) {
									throw new IllegalStateException("No matcher keys were found in the textures object!");
								}
								StructureTileProvider provider = new StructureTileProvider(id, priority, matchers);
								providerMap.put(provider.id(), provider);
								unusedTextures.removeAll(provider.allTextures());
							}
						}
						if (fileJson.has("markers")) {
							JsonElement markerJson = fileJson.get("markers");
							ResourceLocation markerTextureId = ResourceLocation.tryParse(markerJson.getAsString());
							MarkerTexture texture = MarkerTextures.getInstance().asMap().get(markerTextureId);
							if (texture == null) throw new IllegalStateException("Marker texture %s does not exist!".formatted(markerTextureId));
							if (Atlasbound.Client.CONFIG.structureMarkers.get().getOrDefault(fileId.toString(), true)) {
								markerMap.put(id, texture);
							}
						}
					} catch (Exception e) {
						Atlasbound.LOGGER.error("[Antique Atlas] Error reading structure tile provider {}!", fileId, e);
					}
				}
			});
		}

		for (TileTexture texture : unusedTextures) {
			Atlasbound.LOGGER.warn("[Antique Atlas] Tile texture {} isn't referenced by any structure tile provider!", texture.displayId());
		}
	}

//? if fabric {
	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}

	@Override
	public Collection<ResourceLocation> getFabricDependencies() {
		return List.of(TileTextures.ID, MarkerTextures.ID);
	}
//?}
}
