package net.syrupstudios.atlasbound;

import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Primitive;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import folk.sisby.antique_atlas.WorldAtlasData;
import net.syrupstudios.syruplibrary.config.ConfigSection;
import net.syrupstudios.syruplibrary.config.ConfigSpec;
import net.syrupstudios.syruplibrary.config.RegisteredConfig;
import net.syrupstudios.syruplibrary.config.SyrupConfigManager;
import net.syrupstudios.syruplibrary.config.value.ConfigType;
import net.syrupstudios.syruplibrary.config.value.ConfigValue;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AtlasboundConfig {
	public enum GraveStyle { CAUSE, GRAVE, ITEMS, DIED, EUPHEMISMS }
	public enum FallbackHandling { TEST, MISSING, PLAINS, CRASH }
	public enum EmptyHandling { CLOUDS, EMPTY }

	private static final ConfigType<Map<String, Boolean>> BOOLEAN_MAP = new ConfigType<>(Map.class,
		AtlasboundConfig::decodeBooleanMap, AtlasboundConfig::encodeBooleanMap,
		value -> normalizeMap(value, Boolean.class));
	private static final ConfigType<Map<String, Integer>> INTEGER_MAP = new ConfigType<>(Map.class,
		AtlasboundConfig::decodeIntegerMap, AtlasboundConfig::encodeIntegerMap,
		value -> normalizeMap(value, Integer.class));

	private static final ConfigSpec SPEC = createSpec();
	private static final Map<String, ConfigValue<?>> LEGACY_VALUES = legacyValues();

	public final RegisteredConfig file;
	public final ConfigValue<Boolean> fullscreen;
	public final ConfigValue<Boolean> requireItem;
	public final ConfigValue<Boolean> keepZoom;
	public final ConfigValue<Boolean> keepOffset;
	public final ConfigValue<GraveStyle> graveStyle;
	public final ConfigValue<Integer> maxTileChunks;
	public final ConfigValue<Integer> maxTilePixels;
	public final ConfigValue<Integer> mapScale;
	public final ConfigValue<Integer> chunkTickLimit;
	public final ConfigValue<FallbackHandling> fallbackFailHandling;
	public final ConfigValue<EmptyHandling> emptyHandling;
	public final ConfigValue<Map<String, Boolean>> structureMarkers;
	public final Dimensions dimensions;

	public AtlasboundConfig() {
		var manager = SyrupConfigManager.getInstance();
		LegacyAtlasConfig.importIfNeeded(manager.configDirectory().resolve("antique-atlas.toml"),
			manager.configDirectory().resolve("atlasbound.json5"), LEGACY_VALUES);
		file = manager.register(SPEC);
		fullscreen = valueInSpec("fullscreen");
		requireItem = valueInSpec("require_item");
		keepZoom = valueInSpec("keep_zoom");
		keepOffset = valueInSpec("keep_offset");
		graveStyle = valueInSpec("grave_style");
		maxTileChunks = valueInSpec("max_tile_chunks");
		maxTilePixels = valueInSpec("max_tile_pixels");
		mapScale = valueInSpec("map_scale");
		chunkTickLimit = valueInSpec("chunk_tick_limit");
		fallbackFailHandling = valueInSpec("fallback_fail_handling");
		emptyHandling = valueInSpec("empty_handling");
		structureMarkers = valueInSpec("structure_markers");
		dimensions = new Dimensions(valueInSpec("dimensions.scales"));
	}

	private static ConfigSpec createSpec() {
		ConfigSpec spec = ConfigSpec.builder("atlasbound").owner("atlasbound")
			.header("Atlasbound map settings.").build();
		spec.bool("fullscreen", true, "Whether to display the map in full-screen.");
		spec.bool("require_item", false, "Whether to require an item to display the map.");
		spec.bool("keep_zoom", false, "Whether to keep scale after closing the map.");
		spec.bool("keep_offset", false, "Whether to keep offset after closing the map.");
		spec.enumValue("grave_style", GraveStyle.EUPHEMISMS, "How to depict player death locations.");
		spec.integer("max_tile_chunks", 5, 0, 6,
			"Maximum chunks represented as one tile, as a power of two; the minimum zoom.");
		spec.integer("max_tile_pixels", 1, 0, 3,
			"Maximum tile render size multiplier, as a power of two; the maximum zoom.");
		spec.integer("map_scale", 0, -2, 10,
			"Effective GUI scale for tiles and markers. Zero matches the GUI scale; -1 rounds up and -2 rounds down at half scale.");
		spec.value("chunk_tick_limit", ConfigType.INTEGER, 100,
			"Maximum chunks loaded onto the map per tick after entering a world.");
		spec.enumValue("fallback_fail_handling", FallbackHandling.MISSING,
			"How to handle biomes outside the supported biome tags.");
		spec.enumValue("empty_handling", EmptyHandling.EMPTY, "How to display unexplored areas.");
		spec.value("structure_markers", BOOLEAN_MAP,
			orderedMap("minecraft:type/end_city", false), "Which structure markers to display.");
		ConfigSection dimensions = spec.section("dimensions",
			"Options to adjust map behaviour for custom or modified dimensions.");
		dimensions.value("scales", INTEGER_MAP, orderedMap(
			"minecraft:overworld", 8,
			"minecraft:the_nether", 1,
			"minecraft:the_end", 0),
			"Dimension cycle order and coordinate scales. Zero hides relative position.");
		return spec;
	}

	private static Map<String, ConfigValue<?>> legacyValues() {
		Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
		for (ConfigValue<?> value : SPEC.values()) {
			if (value.path().contains(".")) continue;
			values.put(switch (value.key()) {
				case "require_item" -> "requireItem";
				case "keep_zoom" -> "keepZoom";
				case "keep_offset" -> "keepOffset";
				case "grave_style" -> "graveStyle";
				case "max_tile_chunks" -> "maxTileChunks";
				case "max_tile_pixels" -> "maxTilePixels";
				case "map_scale" -> "mapScale";
				case "chunk_tick_limit" -> "chunkTickLimit";
				case "fallback_fail_handling" -> "fallbackFailHandling";
				case "empty_handling" -> "emptyHandling";
				case "structure_markers" -> "structureMarkers";
			default -> value.key();
			}, value);
		}
		values.put("dimensions.scales", valueInSpec("dimensions.scales"));
		return Collections.unmodifiableMap(values);
	}

	@SuppressWarnings("unchecked")
	private static <T> ConfigValue<T> valueInSpec(String path) {
		return (ConfigValue<T>) SPEC.values().stream().filter(value -> value.path().equals(path)).findFirst()
			.orElseThrow(() -> new IllegalStateException("Missing config value: " + path));
	}

	private static Map<String, Boolean> decodeBooleanMap(Json5Element element) {
		if (!element.isJson5Object()) throw new IllegalArgumentException("Expected object of booleans");
		Map<String, Boolean> result = new LinkedHashMap<>();
		for (var entry : element.getAsJson5Object().entrySet()) {
			Json5Element value = entry.getValue();
			if (!value.isJson5Primitive() || !value.getAsJson5Primitive().isBoolean())
				throw new IllegalArgumentException("Expected boolean map values");
			result.put(entry.getKey(), value.getAsJson5Primitive().getAsBoolean());
		}
		return result;
	}

	private static Map<String, Integer> decodeIntegerMap(Json5Element element) {
		if (!element.isJson5Object()) throw new IllegalArgumentException("Expected object of integers");
		Map<String, Integer> result = new LinkedHashMap<>();
		for (var entry : element.getAsJson5Object().entrySet()) {
			Json5Element value = entry.getValue();
			if (!value.isJson5Primitive() || !value.getAsJson5Primitive().isNumber())
				throw new IllegalArgumentException("Expected integer map values");
			result.put(entry.getKey(), value.getAsJson5Primitive().getAsBigDecimal().intValueExact());
		}
		return result;
	}

	private static Json5Element encodeBooleanMap(Map<String, Boolean> values) {
		Json5Object result = new Json5Object();
		values.forEach((key, value) -> result.add(key, Json5Primitive.fromBoolean(value)));
		return result;
	}

	private static Json5Element encodeIntegerMap(Map<String, Integer> values) {
		Json5Object result = new Json5Object();
		values.forEach((key, value) -> result.add(key, Json5Primitive.fromNumber(value)));
		return result;
	}

	private static <T> Map<String, T> normalizeMap(Object value, Class<T> valueType) {
		if (!(value instanceof Map<?, ?> source)) throw new IllegalArgumentException("Expected map");
		Map<String, T> result = new LinkedHashMap<>();
		for (var entry : source.entrySet()) {
			if (!(entry.getKey() instanceof String key) || !valueType.isInstance(entry.getValue()))
				throw new IllegalArgumentException("Expected string keys and " + valueType.getSimpleName() + " values");
			result.put(key, valueType.cast(entry.getValue()));
		}
		return Collections.unmodifiableMap(result);
	}

	private static <T> Map<String, T> orderedMap(Object... entries) {
		Map<String, T> result = new LinkedHashMap<>();
		for (int i = 0; i < entries.length; i += 2) {
			@SuppressWarnings("unchecked") T value = (T) entries[i + 1];
			result.put((String) entries[i], value);
		}
		return result;
	}

	public static final class Dimensions {
		public final ConfigValue<Map<String, Integer>> scales;

		private Dimensions(ConfigValue<Map<String, Integer>> scales) {
			this.scales = scales;
		}

		public List<ResourceKey<Level>> getOrder(ClientPacketListener handler) {
			List<ResourceKey<Level>> dimensions = activeDimensions(handler);
			Map<String, Integer> configured = scales.get();
			return dimensions.stream().sorted(Comparator
				.comparingInt((ResourceKey<Level> key) -> orderOf(configured, key.location().toString()))
				.thenComparing(ResourceKey::toString)).toList();
		}

		public Map<ResourceKey<Level>, Integer> getScales(ClientPacketListener handler) {
			Map<String, Integer> configured = scales.get();
			return activeDimensions(handler).stream().collect(Collectors.toMap(
				key -> key, key -> configured.getOrDefault(key.location().toString(), 0),
				(first, second) -> first, LinkedHashMap::new));
		}

		private static List<ResourceKey<Level>> activeDimensions(ClientPacketListener handler) {
			return handler.levels().stream().filter(dim -> !WorldAtlasData.isEmpty(dim))
				.sorted(Comparator.comparing(ResourceKey::toString)).toList();
		}

		private static int orderOf(Map<String, Integer> configured, String id) {
			int index = 0;
			for (String key : configured.keySet()) {
				if (key.equals(id)) return index;
				index++;
			}
			return Integer.MAX_VALUE;
		}
	}
}
