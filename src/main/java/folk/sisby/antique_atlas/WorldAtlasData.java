package folk.sisby.antique_atlas;

import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.AtlasboundConfig;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.antique_atlas.reloader.MarkerTextures;
import folk.sisby.antique_atlas.reloader.StructureTileProviders;
import folk.sisby.antique_atlas.reloader.TileTextures;
import folk.sisby.antique_atlas.util.Rect;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import net.syrupstudios.atlasbound.map.AtlasLandmark;
import net.syrupstudios.atlasbound.map.AtlasStructureStart;
import net.syrupstudios.atlasbound.map.AtlasTerrainChunk;

/** The tile and marker data shown for one selected atlas dimension. */
public class WorldAtlasData {
	public static final Map<ResourceKey<Level>, WorldAtlasData> WORLDS = new HashMap<>();

	public static WorldAtlasData getOrCreate(ResourceKey<Level> dimension) {
		return AtlasClientState.view(dimension);
	}

	public static boolean isEmpty(ResourceKey<Level> dimension) {
		return !WORLDS.containsKey(dimension) || WORLDS.get(dimension).isEmpty();
	}

	protected ResourceKey<Level> dimension;
	protected final Map<ChunkPos, TileTexture> biomeTiles = new HashMap<>();
	protected final Map<ChunkPos, TileTexture> structureTiles = new HashMap<>();
	protected final Map<UUID, Map<ResourceLocation, Pair<AtlasLandmark, MarkerTexture>>> landmarkMarkers = new HashMap<>();
	protected final Map<AtlasLandmark, MarkerTexture> structureMarkers = new HashMap<>();
	protected final Rect tileScope = new Rect(0, 0, 0, 0);
	protected final Set<ChunkPos> terrainDequeHash = new HashSet<>();
	protected final ArrayDeque<ChunkPos> terrainDeque = new ArrayDeque<>();
	protected final Map<ChunkPos, String> debugBiomePredicates = new HashMap<>();
	protected final Map<ChunkPos, String> debugStructurePredicates = new HashMap<>();
	protected final Map<ChunkPos, TerrainTileProvider> debugBiomes = new HashMap<>();
	protected final Map<ChunkPos, StructureTileProvider> debugStructures = new HashMap<>();
	protected final Map<ChunkPos, AtlasTerrainChunk> terrainSnapshots = new HashMap<>();
	protected final Map<StructureSnapshotKey, AtlasStructureStart> structureSnapshots = new LinkedHashMap<>();
	private record StructureSnapshotKey(ChunkPos chunk, ResourceLocation key) {}

	public WorldAtlasData() {}

	protected WorldAtlasData(ResourceKey<Level> dimension) { this.dimension = dimension; }

	public void setDimension(ResourceKey<Level> dimension) { this.dimension = dimension; }

	private boolean isEmpty() {
		return tileScope.maxY == tileScope.minY && terrainDequeHash.isEmpty();
	}

	/** Accept raw terrain captured from a loaded or saved chunk. */
	public void acceptTerrain(AtlasTerrainChunk chunk, RegistryAccess registries) {
		if (chunk == null || registries == null) return;
		terrainSnapshots.put(chunk.pos(), chunk);
		if (!biomeTiles.containsKey(chunk.pos()) && terrainDequeHash.add(chunk.pos())) terrainDeque.addLast(chunk.pos());
	}

	/** Accept a structure start captured from a loaded or saved chunk. */
	public void acceptStructure(AtlasStructureStart structure, RegistryAccess registries) {
		if (structure == null || registries == null) return;
		structureSnapshots.put(new StructureSnapshotKey(structure.chunk(), structure.key()), structure);
		StructureTileProviders.getInstance().resolve(structureTiles, debugStructures, debugStructurePredicates,
				structureMarkers, structure, registries);
	}

	/** Build a bounded number of queued tiles on the client thread. */
	public void tick(RegistryAccess registries) {
		if (registries == null || dimension == null) return;
		Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
		if (!BiomeTileProviders.getInstance().hasFallbacks()) BiomeTileProviders.getInstance().registerFallbacks(biomes);
		for (int i = 0; i < Atlasbound.Client.CONFIG.chunkTickLimit.get(); i++) {
			ChunkPos pos = terrainDeque.pollFirst();
			if (pos == null) break;
			terrainDequeHash.remove(pos);
			AtlasTerrainChunk terrain = terrainSnapshots.get(pos);
			if (terrain == null) continue;
			Pair<TerrainTileProvider, TileElevation> tile = TerrainTiling.terrainToTile(terrain, registries, dimension);
			if (tile == null) continue;
			tileScope.extendTo(pos.x, pos.z);
			biomeTiles.put(pos, tile.left().getTexture(pos, tile.right()));
			debugBiomes.put(pos, tile.left());
			debugBiomePredicates.put(pos, tile.right() == null ? null : tile.right().getName());
		}
	}

	/** Rebuild rendered tiles after resource providers change, using the accepted raw snapshots. */
	public void rebuildTiles(RegistryAccess registries) {
		biomeTiles.clear();
		structureTiles.clear();
		debugBiomePredicates.clear();
		debugStructurePredicates.clear();
		debugBiomes.clear();
		debugStructures.clear();
		structureMarkers.clear();
		tileScope.set(0, 0, 0, 0);
		terrainDeque.clear();
		terrainDequeHash.clear();
		terrainSnapshots.keySet().stream().sorted((a, b) -> {
			int x = Integer.compare(a.x, b.x);
			return x == 0 ? Integer.compare(a.z, b.z) : x;
		}).forEach(pos -> {
			terrainDeque.addLast(pos);
			terrainDequeHash.add(pos);
		});
		structureSnapshots.values().forEach(start -> StructureTileProviders.getInstance().resolve(
				structureTiles, debugStructures, debugStructurePredicates, structureMarkers, start, registries));
	}

	public Rect getScope() { return tileScope; }

	public TileTexture getTile(int x, int z) { return getTile(new ChunkPos(x, z)); }

	public TileTexture getTile(ChunkPos pos) {
		if (!biomeTiles.containsKey(pos)) return Atlasbound.Client.CONFIG.emptyHandling.get() == AtlasboundConfig.EmptyHandling.CLOUDS
				? TileTextures.getInstance().getTextures().get(Atlasbound.legacyId("clouds")) : null;
		return structureTiles.getOrDefault(pos, biomeTiles.get(pos));
	}

	public ResourceLocation getProvider(ChunkPos pos) {
		if (structureTiles.containsKey(pos)) return debugStructures.get(pos).id();
		return debugBiomes.containsKey(pos) ? debugBiomes.get(pos).id() : null;
	}

	public String getTilePredicate(ChunkPos pos) {
		return structureTiles.containsKey(pos) ? debugStructurePredicates.get(pos) : debugBiomePredicates.get(pos);
	}

	public void addLandmark(AtlasLandmark landmark) {
		if (landmark == null) return;
		addLandmarkMarker(landmark, MarkerTextures.getInstance().fromLandmark(landmark));
		if (Minecraft.getInstance().screen instanceof AtlasScreen screen) screen.updateBookmarkerList();
	}

	public void addLandmarkMarker(AtlasLandmark landmark, MarkerTexture texture) {
		landmarkMarkers.computeIfAbsent(landmark.owner(), ignored -> new HashMap<>())
				.put(landmark.id(), Pair.of(landmark, texture));
	}

	public boolean deleteLandmark(ResourceKey<Level> dimension, AtlasLandmark landmark) {
		if (landmark == null || !dimension.equals(this.dimension)) return false;
		return AtlasClientState.submitMarker(dimension.location(), landmark, null);
	}

	public Map<AtlasLandmark, MarkerTexture> getEditableLandmarks() {
		Map<AtlasLandmark, MarkerTexture> editable = new HashMap<>();
		UUID owner = AtlasClientState.markerOwner();
		if (owner == null) return editable;
		landmarkMarkers.getOrDefault(owner, Map.of()).values()
			.forEach(pair -> editable.put(pair.left(), pair.right()));
		return editable;
	}

	public Map<AtlasLandmark, MarkerTexture> getAllMarkers(int tileChunks) {
		Map<AtlasLandmark, MarkerTexture> markers = new HashMap<>();
		landmarkMarkers.values().forEach(entries -> entries.values().forEach(pair -> markers.put(pair.left(), pair.right())));
		structureMarkers.forEach((landmark, texture) -> {
			if (tileChunks >= texture.nearClip() && tileChunks <= texture.farClip()) markers.put(landmark, texture);
		});
		return markers;
	}

	public MarkerTexture getMarkerTexture(AtlasLandmark landmark) {
		Map<ResourceLocation, Pair<AtlasLandmark, MarkerTexture>> entries = landmarkMarkers.get(landmark.owner());
		return entries != null && entries.containsKey(landmark.id()) ? entries.get(landmark.id()).right() : structureMarkers.get(landmark);
	}

	public boolean isLoading() { return terrainDequeHash.size() > 20; }
}
