package folk.sisby.antique_atlas.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public class ForgeTags {
	public static class Conventional {
		public static class V1 {
			public static final TagKey<Biome> IN_THE_END = tag("in_the_end");
			public static final TagKey<Biome> IN_NETHER = tag("in_nether");
			public static final TagKey<Biome> TAIGA = tag("taiga");
			public static final TagKey<Biome> EXTREME_HILLS = tag("extreme_hills");
			public static final TagKey<Biome> WINDSWEPT = tag("windswept");
			public static final TagKey<Biome> JUNGLE = tag("jungle");
			public static final TagKey<Biome> MESA = tag("mesa");
			public static final TagKey<Biome> PLAINS = tag("plains");
			public static final TagKey<Biome> SAVANNA = tag("savanna");
			public static final TagKey<Biome> ICY = tag("icy");
			public static final TagKey<Biome> AQUATIC_ICY = tag("aquatic_icy");
			public static final TagKey<Biome> BEACH = tag("beach");
			public static final TagKey<Biome> FOREST = tag("forest");
			public static final TagKey<Biome> BIRCH_FOREST = tag("birch_forest");
			public static final TagKey<Biome> OCEAN = tag("ocean");
			public static final TagKey<Biome> DESERT = tag("desert");
			public static final TagKey<Biome> RIVER = tag("river");
			public static final TagKey<Biome> SWAMP = tag("swamp");
			public static final TagKey<Biome> MUSHROOM = tag("mushroom");
			public static final TagKey<Biome> UNDERGROUND = tag("underground");
			public static final TagKey<Biome> MOUNTAIN = tag("mountain");
			public static final TagKey<Biome> CLIMATE_HOT = tag("climate_hot");
			public static final TagKey<Biome> CLIMATE_TEMPERATE = tag("climate_temperate");
			public static final TagKey<Biome> CLIMATE_COLD = tag("climate_cold");
			public static final TagKey<Biome> CLIMATE_WET = tag("climate_wet");
			public static final TagKey<Biome> CLIMATE_DRY = tag("climate_dry");
			public static final TagKey<Biome> VEGETATION_SPARSE = tag("vegetation_sparse");
			public static final TagKey<Biome> VEGETATION_DENSE = tag("vegetation_dense");
			public static final TagKey<Biome> TREE_CONIFEROUS = tag("tree_coniferous");
			public static final TagKey<Biome> TREE_SAVANNA = tag("tree_savanna");
			public static final TagKey<Biome> TREE_JUNGLE = tag("tree_jungle");
			public static final TagKey<Biome> TREE_DECIDUOUS = tag("tree_deciduous");
			public static final TagKey<Biome> VOID = tag("void");
			public static final TagKey<Biome> MOUNTAIN_PEAK = tag("mountain_peak");
			public static final TagKey<Biome> MOUNTAIN_SLOPE = tag("mountain_slope");
			public static final TagKey<Biome> AQUATIC = tag("aquatic");
			public static final TagKey<Biome> WASTELAND = tag("wasteland");
			public static final TagKey<Biome> DEAD = tag("dead");
			public static final TagKey<Biome> FLORAL = tag("floral");
			public static final TagKey<Biome> SNOWY = tag("snowy");
			public static final TagKey<Biome> BADLANDS = tag("badlands");
			public static final TagKey<Biome> CAVES = tag("caves");
			public static final TagKey<Biome> END_ISLANDS = tag("end_islands");
			public static final TagKey<Biome> NETHER_FORESTS = tag("nether_forests");
			public static final TagKey<Biome> SNOWY_PLAINS = tag("snowy_plains");
			public static final TagKey<Biome> STONY_SHORES = tag("stony_shores");
			public static final TagKey<Biome> FLOWER_FORESTS = tag("flower_forests");
			public static final TagKey<Biome> DEEP_OCEAN = tag("deep_ocean");
			public static final TagKey<Biome> SHALLOW_OCEAN = tag("shallow_ocean");

			private static TagKey<Biome> tag(String name) {
				return commonTag(name);
			}
		}

		public static class V2 {
			public static final TagKey<Biome> IS_VOID = tag("is_void");
			public static final TagKey<Biome> IS_END = tag("is_end");
			public static final TagKey<Biome> IS_VEGETATION_SPARSE = tag("is_sparse_vegetation");
			public static final TagKey<Biome> IS_VEGETATION_DENSE = tag("is_dense_vegetation");
			public static final TagKey<Biome> IS_NETHER_FOREST = tag("is_nether_forest");
			public static final TagKey<Biome> IS_NETHER = tag("is_nether");
			public static final TagKey<Biome> IS_SWAMP = tag("is_swamp");
			public static final TagKey<Biome> IS_OCEAN = tag("is_ocean");
			public static final TagKey<Biome> IS_RIVER = tag("is_river");
			public static final TagKey<Biome> IS_AQUATIC_ICY = tag("is_aquatic_icy");
			public static final TagKey<Biome> IS_ICY = tag("is_icy");
			public static final TagKey<Biome> IS_STONY_SHORES = tag("is_stony_shores");
			public static final TagKey<Biome> IS_BEACH = tag("is_beach");
			public static final TagKey<Biome> IS_JUNGLE_TREE = tag("is_tree/jungle");
			public static final TagKey<Biome> IS_JUNGLE = tag("is_jungle");
			public static final TagKey<Biome> IS_FLOWER_FOREST = tag("is_flower_forest");
			public static final TagKey<Biome> IS_FLORAL = tag("is_floral");
			public static final TagKey<Biome> IS_SAVANNA_TREE = tag("is_tree/savanna");
			public static final TagKey<Biome> IS_SAVANNA = tag("is_savanna");
			public static final TagKey<Biome> IS_BADLANDS = tag("is_badlands");
			public static final TagKey<Biome> IS_CONIFEROUS_TREE = tag("is_tree/coniferous");
			public static final TagKey<Biome> IS_TAIGA = tag("is_taiga");
			public static final TagKey<Biome> IS_SNOWY = tag("is_snowy");
			public static final TagKey<Biome> IS_OLD_GROWTH = tag("is_old_growth");
			public static final TagKey<Biome> IS_BIRCH_FOREST = tag("is_birch_forest");
			public static final TagKey<Biome> IS_DECIDUOUS_TREE = tag("is_tree/deciduous");
			public static final TagKey<Biome> IS_FOREST = tag("is_forest");
			public static final TagKey<Biome> IS_SNOWY_PLAINS = tag("is_snowy_plains");
			public static final TagKey<Biome> IS_PLAINS = tag("is_plains");
			public static final TagKey<Biome> IS_WASTELAND = tag("is_wasteland");
			public static final TagKey<Biome> IS_DEAD = tag("is_dead");
			public static final TagKey<Biome> IS_DESERT = tag("is_desert");
			public static final TagKey<Biome> IS_WINDSWEPT = tag("is_windswept");
			public static final TagKey<Biome> IS_MOUNTAIN_PEAK = tag("is_mountain/peak");
			public static final TagKey<Biome> IS_MOUNTAIN_SLOPE = tag("is_mountain/slope");
			public static final TagKey<Biome> IS_MOUNTAIN = tag("is_mountain");
			public static final TagKey<Biome> IS_MUSHROOM = tag("is_mushroom");
			public static final TagKey<Biome> IS_HILL = tag("is_hill");
			public static final TagKey<Biome> IS_UNDERGROUND = tag("is_underground");
			public static final TagKey<Biome> IS_CAVE = tag("is_cave");
			public static final TagKey<Biome> IS_HOT = tag("is_hot");
			public static final TagKey<Biome> IS_COLD = tag("is_cold");
			public static final TagKey<Biome> IS_TEMPERATE = tag("is_temperate");
			public static final TagKey<Biome> IS_DRY = tag("is_dry");
			public static final TagKey<Biome> IS_WET = tag("is_wet");

			private static TagKey<Biome> tag(String name) {
				return commonTag(name);
			}
		}
	}

	private static TagKey<Biome> commonTag(String name) {
		return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", name));
	}

	public static class Biomes {
		public static final TagKey<Biome> IS_HOT = tag("is_hot");
		public static final TagKey<Biome> IS_HOT_OVERWORLD = tag("is_hot/overworld");
		public static final TagKey<Biome> IS_HOT_NETHER = tag("is_hot/nether");
		public static final TagKey<Biome> IS_HOT_END = tag("is_hot/end");

		public static final TagKey<Biome> IS_COLD = tag("is_cold");
		public static final TagKey<Biome> IS_COLD_OVERWORLD = tag("is_cold/overworld");
		public static final TagKey<Biome> IS_COLD_NETHER = tag("is_cold/nether");
		public static final TagKey<Biome> IS_COLD_END = tag("is_cold/end");

		public static final TagKey<Biome> IS_SPARSE = tag("is_sparse");
		public static final TagKey<Biome> IS_SPARSE_OVERWORLD = tag("is_sparse/overworld");
		public static final TagKey<Biome> IS_SPARSE_NETHER = tag("is_sparse/nether");
		public static final TagKey<Biome> IS_SPARSE_END = tag("is_sparse/end");
		public static final TagKey<Biome> IS_DENSE = tag("is_dense");
		public static final TagKey<Biome> IS_DENSE_OVERWORLD = tag("is_dense/overworld");
		public static final TagKey<Biome> IS_DENSE_NETHER = tag("is_dense/nether");
		public static final TagKey<Biome> IS_DENSE_END = tag("is_dense/end");

		public static final TagKey<Biome> IS_WET = tag("is_wet");
		public static final TagKey<Biome> IS_WET_OVERWORLD = tag("is_wet/overworld");
		public static final TagKey<Biome> IS_WET_NETHER = tag("is_wet/nether");
		public static final TagKey<Biome> IS_WET_END = tag("is_wet/end");
		public static final TagKey<Biome> IS_DRY = tag("is_dry");
		public static final TagKey<Biome> IS_DRY_OVERWORLD = tag("is_dry/overworld");
		public static final TagKey<Biome> IS_DRY_NETHER = tag("is_dry/nether");
		public static final TagKey<Biome> IS_DRY_END = tag("is_dry/end");

		public static final TagKey<Biome> IS_CONIFEROUS = tag("is_coniferous");

		public static final TagKey<Biome> IS_SPOOKY = tag("is_spooky");
		public static final TagKey<Biome> IS_DEAD = tag("is_dead");
		public static final TagKey<Biome> IS_LUSH = tag("is_lush");
		public static final TagKey<Biome> IS_MUSHROOM = tag("is_mushroom");
		public static final TagKey<Biome> IS_MAGICAL = tag("is_magical");
		public static final TagKey<Biome> IS_RARE = tag("is_rare");
		public static final TagKey<Biome> IS_PLATEAU = tag("is_plateau");
		public static final TagKey<Biome> IS_MODIFIED = tag("is_modified");

		public static final TagKey<Biome> IS_WATER = tag("is_water");
		public static final TagKey<Biome> IS_DESERT = tag("is_desert");
		public static final TagKey<Biome> IS_PLAINS = tag("is_plains");
		public static final TagKey<Biome> IS_SWAMP = tag("is_swamp");
		public static final TagKey<Biome> IS_SANDY = tag("is_sandy");
		public static final TagKey<Biome> IS_SNOWY = tag("is_snowy");
		public static final TagKey<Biome> IS_WASTELAND = tag("is_wasteland");
		public static final TagKey<Biome> IS_VOID = tag("is_void");
		public static final TagKey<Biome> IS_UNDERGROUND = tag("is_underground");

		public static final TagKey<Biome> IS_CAVE = tag("is_cave");
		public static final TagKey<Biome> IS_PEAK = tag("is_peak");
		public static final TagKey<Biome> IS_SLOPE = tag("is_slope");
		public static final TagKey<Biome> IS_MOUNTAIN = tag("is_mountain");

		public static TagKey<Biome> tag(String name) {
			return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", name));
		}
	}
}
