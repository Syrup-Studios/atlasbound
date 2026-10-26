package folk.sisby.antique_atlas;

import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import net.syrupstudios.atlasbound.map.AtlasTerrainChunk;
import net.syrupstudios.atlasbound.map.AtlasTerrainLayer;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Reference2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import folk.sisby.antique_atlas.util.ForgeTags;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

/**
 * Hottest class in the mod. Might get ugly.
 */
public class TerrainTiling {
	public static final int EMPTY_PRIORITY = 16;
	public static final int RAVINE_PRIORITY = 12;
	public static final int LAVA_PRIORITY = 6;
	public static final int WATER_PRIORITY = 4;
	public static final int ICE_PRIORITY = 3;
	public static final int BEACH_PRIORITY = 3;

	public static final List<ResourceLocation> CUSTOM_TILES = List.of(
		FeatureTiles.BEDROCK_ROOF,
		FeatureTiles.EMPTY,
		FeatureTiles.END_VOID,
		FeatureTiles.WATER,
		FeatureTiles.ICE,
		FeatureTiles.TILE_RAVINE,
		FeatureTiles.SWAMP_WATER,
		FeatureTiles.TILE_LAVA,
		FeatureTiles.TILE_LAVA_SHORE
	);

	public static final int NETHER_SCAN_HEIGHT = 50;
	public static final Map<Biome, Integer> priorityCache = new Reference2IntArrayMap<>();
	public static final Map<Biome, Boolean> swampCache = new Reference2BooleanArrayMap<>();
	private static final int SEA_LEVEL = 63;

	public static int priorityForBiome(Registry<Biome> biomeRegistry, Biome biome) {
		return priorityCache.computeIfAbsent(biome, b -> {
			Holder<Biome> biomeEntry = biomeRegistry.wrapAsHolder(biome);
			if (biomeEntry.is(BiomeTags.IS_BEACH)) {
				return BEACH_PRIORITY;
			} else if (biomeEntry.is(BiomeTags.IS_NETHER)) {
				return 2;
			} else {
				return 1;
			}
		});
	}

	public static boolean isSwamp(Registry<Biome> biomeRegistry, Biome biome) {
		return swampCache.computeIfAbsent(biome, b -> biomeRegistry.wrapAsHolder(b).is(ForgeTags.Conventional.V1.SWAMP));
	}

	public static Pair<TerrainTileProvider, TileElevation> frequencyToTexture(int[][] possibleTiles, Registry<Biome> biomeRegistry, List<ResourceLocation> biomePalette) {
		int elevationOrdinal = -1;
		int biomeIndex = -1;
		int bestFrequency = 0;
		for (int i = 0; i < possibleTiles.length; i++) {
			for (int j = 0; j < possibleTiles[i].length; j++) {
				if (possibleTiles[i][j] > bestFrequency) {
					elevationOrdinal = i;
					biomeIndex = j;
					bestFrequency = possibleTiles[i][j];
				}
			}
		}
		if (bestFrequency == 0) return null;
		int customTileIndex = biomeIndex - possibleTiles[0].length + CUSTOM_TILES.size();
		ResourceLocation providerId = customTileIndex >= 0 ? CUSTOM_TILES.get(customTileIndex) : biomePalette.get(biomeIndex);
		if (providerId == null) {
			throw new RuntimeException("Missing tile provider ID at index " + biomeIndex);
		}
		return Pair.of(BiomeTileProviders.getInstance().getTileProvider(providerId), elevationOrdinal == TileElevation.values().length ? null : TileElevation.values()[elevationOrdinal]);
	}

	public static Pair<TerrainTileProvider, TileElevation> terrainToTile(AtlasTerrainChunk chunk, RegistryAccess registries, ResourceKey<Level> dimension) {
		boolean nether = dimension.equals(Level.NETHER);
		if (nether) return terrainToTileNether(chunk, registries);
		int defaultTile = CUSTOM_TILES.indexOf(dimension.equals(Level.END) ? FeatureTiles.END_VOID : FeatureTiles.EMPTY);
		return terrainToSurface(chunk, registries, defaultTile, dimension.equals(Level.OVERWORLD));
	}

	private static Pair<TerrainTileProvider, TileElevation> terrainToSurface(AtlasTerrainChunk chunk, RegistryAccess registries, int defaultTile, boolean checkRavines) {
		AtlasTerrainLayer lithograph = chunk.surface();
		List<ResourceLocation> biomePalette = chunk.biomePalette();
		List<ResourceLocation> blockPalette = chunk.blockPalette();
		Registry<Biome> biomeRegistry = registries.registryOrThrow(Registries.BIOME);
		if (lithograph.exists().isEmpty()) return Pair.of(BiomeTileProviders.getInstance().getTileProvider(CUSTOM_TILES.get(defaultTile)), null);
		int elevationSize = TileElevation.values().length;
		int biomeCount = biomePalette.size();
		int[][] possibleTiles = new int[elevationSize + 1][biomeCount + CUSTOM_TILES.size()];
		var exists = lithograph.exists(); int[] depths = lithograph.depths(); int[] biomes = lithograph.biomes();
		int[] blocks = lithograph.blocks(); int[] waterDepths = lithograph.waterDepths();
		for (int i = 0; i < depths.length; i++) {
			if (!exists.get(i)) { possibleTiles[elevationSize][defaultTile] += EMPTY_PRIORITY; continue; }
			int height = 999 - depths[i] + waterDepths[i];
			Block block = BuiltInRegistries.BLOCK.get(blockPalette.get(blocks[i]));
			Biome biome = biomeRegistry.get(biomePalette.get(biomes[i]));
			if (biome == null) continue;
			if (checkRavines && height - SEA_LEVEL < -7) possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_RAVINE)] += RAVINE_PRIORITY;
			else if (waterDepths[i] > 0) possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(isSwamp(biomeRegistry, biome) ? FeatureTiles.SWAMP_WATER : FeatureTiles.WATER)] += WATER_PRIORITY;
			else if (block == Blocks.ICE) possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.ICE)] += ICE_PRIORITY;
			else if (block == Blocks.LAVA) possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_LAVA)] += LAVA_PRIORITY;
			possibleTiles[TileElevation.fromBlocksAboveSea(height - SEA_LEVEL).ordinal()][biomes[i]] += priorityForBiome(biomeRegistry, biome);
		}
		return frequencyToTexture(possibleTiles, biomeRegistry, biomePalette);
	}

	private static Pair<TerrainTileProvider, TileElevation> terrainToTileNether(AtlasTerrainChunk chunk, RegistryAccess registries) {
		int defaultTile = CUSTOM_TILES.indexOf(FeatureTiles.BEDROCK_ROOF);
		AtlasTerrainLayer lowLithograph = chunk.netherLow();
		AtlasTerrainLayer fullLithograph = chunk.netherFull();
		List<ResourceLocation> biomePalette = chunk.biomePalette();
		List<ResourceLocation> blockPalette = chunk.blockPalette();
		Registry<Biome> biomeRegistry = registries.registryOrThrow(Registries.BIOME);

		int elevationSize = TileElevation.values().length;
		int elevationCount = elevationSize + 1;
		int biomeCount = biomePalette.size();
		int baseTileCount = biomeCount + CUSTOM_TILES.size();
		int[][] possibleTiles = new int[elevationCount][baseTileCount];

		var fullExists = fullLithograph == null ? new java.util.BitSet() : fullLithograph.exists();
		var lowExists = lowLithograph == null ? new java.util.BitSet() : lowLithograph.exists();
		int[] fullDepths = fullLithograph == null ? new int[256] : fullLithograph.depths();
		int[] fullBiomes = fullLithograph == null ? new int[256] : fullLithograph.biomes();
		int[] lowDepths = lowLithograph == null ? new int[256] : lowLithograph.depths();
		int[] lowBlocks = lowLithograph == null ? new int[256] : lowLithograph.blocks();
		if (fullLithograph == null || fullExists.isEmpty()) {
			return Pair.of(BiomeTileProviders.getInstance().getTileProvider(CUSTOM_TILES.get(defaultTile)), null);
		}

		int SEA_DEPTH = 999 - 31;

		if (lowLithograph == null || lowExists.isEmpty()) {
			for (int i = 0; i < fullDepths.length; i++) {
				if (!fullExists.get(i)) {
					possibleTiles[elevationSize][defaultTile] += EMPTY_PRIORITY;
				} else {
					Biome biome = biomeRegistry.get(biomePalette.get(fullBiomes[i]));
					if (biome != null) possibleTiles[elevationSize][fullBiomes[i]] += priorityForBiome(biomeRegistry, biome);
				}
			}
		} else {
			for (int i = 0; i < lowDepths.length; i++) {
				if (!lowExists.get(i) || lowDepths[i] > SEA_DEPTH) {
					Biome biome = biomeRegistry.get(biomePalette.get(fullBiomes[i]));
					if (biome != null) possibleTiles[elevationSize][fullBiomes[i]] += priorityForBiome(biomeRegistry, biome);
				} else {
					Block block = BuiltInRegistries.BLOCK.get(blockPalette.get(lowBlocks[i]));
					if (block == Blocks.LAVA) { // Lava Sea
						possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_LAVA)] += LAVA_PRIORITY;
					} else { // Low Floor
						possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_LAVA_SHORE)] += BEACH_PRIORITY;
					}
				}
			}
		}

		return frequencyToTexture(possibleTiles, biomeRegistry, biomePalette);
	}
}
