package net.syrupstudios.atlasbound;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import de.marhali.json5.Json5;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import net.syrupstudios.syruplibrary.config.value.ConfigValue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class LegacyAtlasConfig {
	private static final Json5 JSON5 = new Json5();

	private LegacyAtlasConfig() {
	}

	static void importIfNeeded(Path legacyPath, Path targetPath, Map<String, ConfigValue<?>> values) {
		if (Files.exists(targetPath) || !Files.exists(legacyPath)) return;
		Path temporary = null;
		try {
			Config legacy;
			try (Reader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
				legacy = Config.of(LinkedHashMap::new, TomlFormat.instance());
				new TomlParser().parse(reader, legacy, ParsingMode.REPLACE);
			}
			Json5Object imported = new Json5Object();
			for (var entry : values.entrySet()) {
				if (!legacy.contains(entry.getKey())) continue;
				ConfigValue<?> value = entry.getValue();
				Object candidate = convert(value, legacy.get(entry.getKey()));
				addPath(imported, value.path(), encode(value, candidate));
			}
			Path parent = targetPath.getParent();
			Files.createDirectories(parent);
			temporary = Files.createTempFile(parent, "." + targetPath.getFileName() + "-", ".tmp");
			Files.writeString(temporary, JSON5.serialize(imported) + "\n", StandardCharsets.UTF_8);
			if (Files.exists(targetPath)) return;
			try {
				Files.createLink(targetPath, temporary);
			} catch (FileAlreadyExistsException exception) {
				return;
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Could not import legacy config " + legacyPath + " to " + targetPath
				+ "; the TOML file was left unchanged", exception);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
					// The temporary file does not affect config loading or a later import.
				}
			}
		}
	}

	private static Object convert(ConfigValue<?> value, Object candidate) {
		if (value.declaredType().isEnum() && candidate instanceof String name) {
			for (Object constant : value.declaredType().getEnumConstants()) {
				if (((Enum<?>) constant).name().equalsIgnoreCase(name)) return constant;
			}
			throw new IllegalArgumentException("Unknown enum value " + name + " for " + value.path());
		}
		if (candidate instanceof UnmodifiableConfig config) return new LinkedHashMap<>(config.valueMap());
		if (candidate instanceof Map<?, ?> map) return new LinkedHashMap<>(map);
		return candidate;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Json5Element encode(ConfigValue value, Object candidate) {
		return value.type().encode(value.validate(candidate));
	}

	private static void addPath(Json5Object root, String path, Json5Element value) {
		String[] parts = path.split("\\.");
		Json5Object current = root;
		for (int i = 0; i < parts.length - 1; i++) {
			String key = parts[i];
			if (!current.has(key)) current.add(key, new Json5Object());
			current = current.getAsJson5Object(key);
		}
		current.add(parts[parts.length - 1], value);
	}
}
