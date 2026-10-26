package folk.sisby.antique_atlas.reloader;

import net.syrupstudios.atlasbound.Atlasbound;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import folk.sisby.antique_atlas.MarkerTexture;
import folk.sisby.antique_atlas.util.CodecUtil;
import net.syrupstudios.atlasbound.map.AtlasLandmark;
//? if fabric {
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
//?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MarkerTextures extends SimplePreparableReloadListener<Map<ResourceLocation, MarkerTextures.MarkerTextureMeta>>
//? if fabric
implements IdentifiableResourceReloadListener
{
	public static final MarkerTextures INSTANCE = new MarkerTextures();
	public static final ResourceLocation ID = Atlasbound.legacyId("marker_textures");

	public static MarkerTextures getInstance() {
		return INSTANCE;
	}

	protected final Map<ResourceLocation, MarkerTexture> textures = new HashMap<>();

	public MarkerTexture get(ResourceLocation id) {
		return textures.get(id);
	}

	public MarkerTexture getOrDefault(ResourceLocation id) {
		return getOrDefault(id, MarkerTexture.DEFAULT);
	}

	public MarkerTexture getOrDefault(ResourceLocation id, MarkerTexture defaultTexture) {
		return textures.getOrDefault(id, defaultTexture);
	}

	public ResourceLocation minimumId(ResourceLocation id) {
		while (get(id) == null && id.getPath().contains("/")) {
			id = id.withPath(id.getPath().substring(0, id.getPath().lastIndexOf('/')));
		}
		return get(id) == null ? id.withPath("default") : id;
	}

	public MarkerTexture fromLandmark(AtlasLandmark landmark) {
		return getOrDefault(minimumId(landmark.id()));
	}

	public MarkerTexture fromLandmark(AtlasLandmark landmark, String variant) {
		ResourceLocation id = minimumId(landmark.id());
		return getOrDefault(id.withPath(p -> p + "/" + variant), getOrDefault(id));
	}

	public Map<ResourceLocation, MarkerTexture> asMap() {
		return new HashMap<>(textures);
	}

	@Override
	protected Map<ResourceLocation, MarkerTextureMeta> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<ResourceLocation, MarkerTextures.MarkerTextureMeta> textureMeta = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("textures/atlas/marker", id -> id.getPath().endsWith(".png")).entrySet()) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), e.getKey().getPath().substring("textures/atlas/marker/".length(), e.getKey().getPath().length() - ".png".length()));
			try {
				ResourceMetadata metadata = e.getValue().metadata();
				textureMeta.put(id, metadata.getSection(MarkerTextures.MarkerTextureMeta.METADATA).orElse(MarkerTextureMeta.DEFAULT));
			} catch (IOException ex) {
				Atlasbound.LOGGER.error("[Antique Atlas] Failed to access marker texture metadata for {}", e.getKey(), ex);
				textureMeta.put(id, MarkerTextures.MarkerTextureMeta.DEFAULT);
			}
		}
		return textureMeta;
	}

	@Override
	protected void apply(Map<ResourceLocation, MarkerTextureMeta> prepared, ResourceManager manager, ProfilerFiller profiler) {
		Atlasbound.LOGGER.info("[Antique Atlas] Reloading Marker Textures...");
		textures.clear();
		prepared.forEach((id, meta) -> {
			if (id.getPath().endsWith("_accent")) {
				ResourceLocation mainId = id.withPath(s -> s.substring(0, s.length() - "_accent".length()));
				MarkerTextureMeta main = prepared.get(mainId);
				if (main != null) {
					textures.put(mainId, main.build(mainId, true));
				} else {
					Atlasbound.LOGGER.error("[Antique Atlas] Marker accent {} has no main texture! Discarding.", id);
				}
			}
		});
		prepared.forEach((id, meta) -> {
			if (!textures.containsKey(id) && !id.getPath().endsWith("_accent")) {
				textures.put(id, meta.build(id, false));
			}
		});
	}

//? if fabric {
	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}
//?}

	public record MarkerTextureMeta(Optional<ResourceLocation> item, Optional<Integer> textureWidth, Optional<Integer> textureHeight, Optional<Integer> mipLevels, Optional<Integer> offsetX, Optional<Integer> offsetY, Optional<Integer> nearClip, Optional<Integer> farClip) {
		public static final MarkerTextureMeta DEFAULT = new MarkerTextureMeta(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

		public static final Codec<MarkerTextureMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceLocation.CODEC.optionalFieldOf("item").forGetter(MarkerTextureMeta::item),
			Codec.INT.optionalFieldOf("textureWidth").forGetter(MarkerTextureMeta::textureWidth),
			Codec.INT.optionalFieldOf("textureHeight").forGetter(MarkerTextureMeta::textureHeight),
			Codec.INT.optionalFieldOf("mipLevels").forGetter(MarkerTextureMeta::mipLevels),
			Codec.INT.optionalFieldOf("offsetX").forGetter(MarkerTextureMeta::offsetX),
			Codec.INT.optionalFieldOf("offsetY").forGetter(MarkerTextureMeta::offsetY),
			Codec.INT.optionalFieldOf("nearClip").forGetter(MarkerTextureMeta::nearClip),
			Codec.INT.optionalFieldOf("farClip").forGetter(MarkerTextureMeta::farClip)
		).apply(instance, MarkerTextureMeta::new));

		public static final MetadataSectionSerializer<MarkerTextureMeta> METADATA = new CodecUtil.CodecResourceMetadataSerializer<>(CODEC, Atlasbound.legacyId("marker"));

		public MarkerTexture build(ResourceLocation id, boolean accent) {
			int textureWidth = this.textureWidth.orElse(32);
			int textureHeight = this.textureHeight.orElse(32);
			int mipLevels = this.mipLevels.orElse(0);
			int offsetX = this.offsetX.orElse(-textureWidth / 2);
			int offsetY = this.offsetY.orElse(-textureHeight / 2);
			int nearClip = this.nearClip.orElse(1);
			int farClip = this.farClip.orElse(Integer.MAX_VALUE);
			ResourceLocation item = this.item.orElse(null);
			return MarkerTexture.ofId(id, item, offsetX, offsetY, textureWidth, textureHeight, mipLevels, nearClip, farClip, accent);
		}
	}
}
