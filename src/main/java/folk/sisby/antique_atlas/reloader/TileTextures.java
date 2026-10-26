package folk.sisby.antique_atlas.reloader;

import net.syrupstudios.atlasbound.Atlasbound;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import folk.sisby.antique_atlas.TileTexture;
import folk.sisby.antique_atlas.util.CodecUtil;
//? if fabric {
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
//?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TileTextures extends SimplePreparableReloadListener<Map<ResourceLocation, TileTextures.TileTextureMeta>>
//? if fabric
implements IdentifiableResourceReloadListener
{
	public static final TileTextures INSTANCE = new TileTextures();
	public static final ResourceLocation ID = Atlasbound.legacyId("tile_textures");

	public static TileTextures getInstance() {
		return INSTANCE;
	}

	protected final Map<ResourceLocation, TileTexture> textures = new HashMap<>();

	public Map<ResourceLocation, TileTexture> getTextures() {
		return textures;
	}

	@Override
	protected Map<ResourceLocation, TileTextures.TileTextureMeta> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<ResourceLocation, TileTextureMeta> textureMeta = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("textures/atlas/tile", id -> id.getPath().endsWith(".png")).entrySet()) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), e.getKey().getPath().substring("textures/atlas/tile/".length(), e.getKey().getPath().length() - ".png".length()));
			try {
				ResourceMetadata metadata = e.getValue().metadata();
				metadata.getSection(TileTextureMeta.METADATA).ifPresentOrElse(meta -> textureMeta.put(id, meta), () -> {
					Atlasbound.LOGGER.info("[Antique Atlas] Metadata not present for {} - using defaults.", e.getKey());
					textureMeta.put(id, TileTextureMeta.DEFAULT);
				});
			} catch (IOException ex) {
				Atlasbound.LOGGER.error("[Antique Atlas] Failed to access tile texture metadata for {}", e.getKey(), ex);
				textureMeta.put(id, TileTextureMeta.DEFAULT);
			}
		}
		return textureMeta;
	}

	@Override
	protected void apply(Map<ResourceLocation, TileTextureMeta> prepared, ResourceManager manager, ProfilerFiller profiler) {
		Atlasbound.LOGGER.info("[Antique Atlas] Reloading Tile Textures...");
		// Validate IDs
		prepared.forEach((id, meta) -> meta.warnMissing(id, prepared.keySet()));

		// Validate Parents
		Map<ResourceLocation, ResourceLocation> invalidParents = new HashMap<>();
		prepared.forEach((id, meta) -> {
			if (meta.parent != null && !prepared.containsKey(meta.parent)) {
				invalidParents.put(id, meta.parent);
				Atlasbound.LOGGER.error("[Antique Atlas] Failed to reload a tile texture! {} had invalid parent {}", id, meta.parent);
			}
		});
		invalidParents.keySet().forEach(prepared::remove);

		// Propagate fields to children
		prepared.forEach((id, meta) -> {
			Optional<TileTextureMeta> parent = meta.parent().map(prepared::get);
			while (parent.isPresent()) {
				meta.inheritFromAncestor(parent.orElseThrow());
				parent = parent.orElseThrow().parent().map(prepared::get);
			}
		});

		// Populate Tags
		Map<ResourceLocation, Set<ResourceLocation>> textureTags = new HashMap<>();
		prepared.forEach((id, meta) -> meta.tags.forEach(tag -> textureTags.computeIfAbsent(tag, t -> new HashSet<>()).add(id)));

		// Substitute Tags
		prepared.forEach((id, meta) -> meta.substituteTags(id, textureTags));

		// Apply TilesToThis
		prepared.forEach((id, meta) -> meta.applyTilesToThis(id, prepared));

		// Create Builders
		Map<ResourceLocation, TileTexture.Builder> textureBuilders = new HashMap<>();
		prepared.forEach((id, meta) -> textureBuilders.put(id, meta.toBuilder(id)));

		// Create Empty Textures
		textures.clear();
		textureBuilders.forEach((id, builder) -> textures.put(id, TileTexture.empty(id, builder.innerBorder())));

		// Build Textures
		textureBuilders.forEach((id, builder) -> builder.build(textures));
	}

//? if fabric {
	@Override
	public ResourceLocation getFabricId() {
		return ID;
	}
//?}

	public static class TileTextureMeta {
		public static final TileTextureMeta DEFAULT = new TileTextureMeta(null, null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		public static final Codec<TileTextureMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(TileTextureMeta::parent),
			CodecUtil.ofEnum(BorderType.class).optionalFieldOf("borderType").forGetter(TileTextureMeta::borderType),
			CodecUtil.set(ResourceLocation.CODEC).fieldOf("tags").orElseGet(HashSet::new).forGetter(TileTextureMeta::tags),
			CodecUtil.set(ExtraCodecs.TAG_OR_ELEMENT_ID).fieldOf("tilesTo").orElseGet(HashSet::new).forGetter(TileTextureMeta::tilesTo),
			CodecUtil.set(ExtraCodecs.TAG_OR_ELEMENT_ID).fieldOf("tilesToHorizontal").orElseGet(HashSet::new).forGetter(TileTextureMeta::tilesToHorizontal),
			CodecUtil.set(ExtraCodecs.TAG_OR_ELEMENT_ID).fieldOf("tilesToVertical").orElseGet(HashSet::new).forGetter(TileTextureMeta::tilesToVertical),
			CodecUtil.set(ExtraCodecs.TAG_OR_ELEMENT_ID).fieldOf("tilesToThis").orElseGet(HashSet::new).forGetter(TileTextureMeta::tilesToThis),
			CodecUtil.set(ExtraCodecs.TAG_OR_ELEMENT_ID).fieldOf("tilesToThisHorizontal").orElseGet(HashSet::new).forGetter(TileTextureMeta::tilesToThisHorizontal),
			CodecUtil.set(ExtraCodecs.TAG_OR_ELEMENT_ID).fieldOf("tilesToThisVertical").orElseGet(HashSet::new).forGetter(TileTextureMeta::tilesToThisVertical)
		).apply(instance, (p, b, t, tt, tth, ttv, ttt, ttth, tttv) -> new TileTextureMeta(p.orElse(null), b.orElse(null), t, tt, tth, ttv, ttt, ttth, tttv)));

		public enum BorderType {
			OUTER, INNER
		}

		public static final MetadataSectionSerializer<TileTextureMeta> METADATA = new CodecUtil.CodecResourceMetadataSerializer<>(CODEC, Atlasbound.legacyId("tiling"));
		protected final ResourceLocation parent;
		protected BorderType borderType;
		protected final Set<ResourceLocation> tags;
		protected final Set<ExtraCodecs.TagOrElementLocation> tilesTo;
		protected final Set<ExtraCodecs.TagOrElementLocation> tilesToHorizontal;
		protected final Set<ExtraCodecs.TagOrElementLocation> tilesToVertical;
		protected final Set<ExtraCodecs.TagOrElementLocation> tilesToThis;
		protected final Set<ExtraCodecs.TagOrElementLocation> tilesToThisHorizontal;
		protected final Set<ExtraCodecs.TagOrElementLocation> tilesToThisVertical;

		public TileTextureMeta(ResourceLocation parent, BorderType borderType, Set<ResourceLocation> tags, Set<ExtraCodecs.TagOrElementLocation> tilesTo, Set<ExtraCodecs.TagOrElementLocation> tilesToHorizontal, Set<ExtraCodecs.TagOrElementLocation> tilesToVertical, Set<ExtraCodecs.TagOrElementLocation> tilesToThis, Set<ExtraCodecs.TagOrElementLocation> tilesToThisHorizontal, Set<ExtraCodecs.TagOrElementLocation> tilesToThisVertical) {
			this.parent = parent;
			this.borderType = borderType;
			this.tags = tags;
			this.tilesTo = tilesTo;
			this.tilesToHorizontal = tilesToHorizontal;
			this.tilesToVertical = tilesToVertical;
			this.tilesToThis = tilesToThis;
			this.tilesToThisHorizontal = tilesToThisHorizontal;
			this.tilesToThisVertical = tilesToThisVertical;
		}

		public void warnMissing(ResourceLocation thisId, Set<ResourceLocation> identifiers) {
			for (Set<ExtraCodecs.TagOrElementLocation> entrySet : List.of(tilesTo, tilesToHorizontal, tilesToVertical, tilesToThis, tilesToThisHorizontal, tilesToThisVertical)) {
				for (ExtraCodecs.TagOrElementLocation entry : entrySet) {
					if (!entry.tag() && !identifiers.contains(entry.id())) {
						Atlasbound.LOGGER.warn("[Antique Atlas] Tile texture {} references texture {}, which is missing!", thisId, entry.id());
					}
				}
			}
		}

		void inheritFromAncestor(TileTextureMeta other) {
			if (other.borderType().isPresent()) borderType = other.borderType;
			tags.addAll(other.tags);
			tilesTo.addAll(other.tilesTo);
			tilesToHorizontal.addAll(other.tilesToHorizontal);
			tilesToVertical.addAll(other.tilesToVertical);
			tilesToThis.addAll(other.tilesToThis);
			tilesToThisHorizontal.addAll(other.tilesToThisHorizontal);
			tilesToThisVertical.addAll(other.tilesToThisVertical);
		}

		void substituteTags(ResourceLocation thisId, Map<ResourceLocation, Set<ResourceLocation>> tags) {
			for (Set<ExtraCodecs.TagOrElementLocation> entrySet : List.of(tilesTo, tilesToHorizontal, tilesToVertical, tilesToThis, tilesToThisHorizontal, tilesToThisVertical)) {
				Set<ExtraCodecs.TagOrElementLocation> entryTags = new HashSet<>();
				for (ExtraCodecs.TagOrElementLocation entry : entrySet) {
					if (entry.tag()) {
						entryTags.add(entry);
					}
				}
				if (!entryTags.isEmpty()) entrySet.removeAll(entryTags);
				for (ExtraCodecs.TagOrElementLocation entry : entryTags) {
					Set<ResourceLocation> resolvedIds = tags.getOrDefault(entry.id(), Set.of());
					if (resolvedIds.isEmpty()) {
						Atlasbound.LOGGER.warn("[Antique Atlas] Tile texture {} references tag {}, which is empty", thisId, entry.id());
					} else {
						entrySet.addAll(resolvedIds.stream().map(id -> new ExtraCodecs.TagOrElementLocation(id, false)).toList());
					}
				}
			}
		}

		void applyTilesToThis(ResourceLocation thisId, Map<ResourceLocation, TileTextureMeta> map) {
			for (ExtraCodecs.TagOrElementLocation entryId : tilesToThis) {
				if (entryId.tag()) throw new IllegalStateException("tags must be resolved to apply tilesToThis!");
				if (map.containsKey(entryId.id())) {
					map.get(entryId.id()).tilesTo.add(new ExtraCodecs.TagOrElementLocation(thisId, false));
				} else {
					Atlasbound.LOGGER.warn("[Antique Atlas] Tile texture {} references texture {}, which is missing", thisId, entryId.id());
				}
			}
			for (ExtraCodecs.TagOrElementLocation entryId : tilesToThisHorizontal) {
				if (entryId.tag()) throw new IllegalStateException("tags must be resolved to apply tilesToThis!");
				if (map.containsKey(entryId.id())) {
					map.get(entryId.id()).tilesToHorizontal.add(new ExtraCodecs.TagOrElementLocation(thisId, false));
				} else {
					Atlasbound.LOGGER.warn("[Antique Atlas] Tile texture {} references texture {}, which is missing", thisId, entryId.id());
				}
			}
			for (ExtraCodecs.TagOrElementLocation entryId : tilesToThisVertical) {
				if (entryId.tag()) throw new IllegalStateException("tags must be resolved to apply tilesToThis!");
				if (map.containsKey(entryId.id())) {
					map.get(entryId.id()).tilesToVertical.add(new ExtraCodecs.TagOrElementLocation(thisId, false));
				} else {
					Atlasbound.LOGGER.warn("[Antique Atlas] Tile texture {} references texture {}, which is missing", thisId, entryId.id());
				}
			}
		}

		public TileTexture.Builder toBuilder(ResourceLocation thisId) {
			return new TileTexture.Builder(thisId, borderType().orElse(BorderType.OUTER) == BorderType.INNER, tilesTo.stream().map(ExtraCodecs.TagOrElementLocation::id).collect(Collectors.toSet()), tilesToHorizontal.stream().map(ExtraCodecs.TagOrElementLocation::id).collect(Collectors.toSet()), tilesToVertical.stream().map(ExtraCodecs.TagOrElementLocation::id).collect(Collectors.toSet()));
		}

		public Optional<ResourceLocation> parent() {
			return Optional.ofNullable(parent);
		}

		public Optional<BorderType> borderType() {
			return Optional.ofNullable(borderType);
		}

		public Set<ResourceLocation> tags() {
			return tags;
		}

		public Set<ExtraCodecs.TagOrElementLocation> tilesTo() {
			return tilesTo;
		}

		public Set<ExtraCodecs.TagOrElementLocation> tilesToHorizontal() {
			return tilesToHorizontal;
		}

		public Set<ExtraCodecs.TagOrElementLocation> tilesToVertical() {
			return tilesToVertical;
		}

		public Set<ExtraCodecs.TagOrElementLocation> tilesToThis() {
			return tilesToThis;
		}

		public Set<ExtraCodecs.TagOrElementLocation> tilesToThisHorizontal() {
			return tilesToThisHorizontal;
		}

		public Set<ExtraCodecs.TagOrElementLocation> tilesToThisVertical() {
			return tilesToThisVertical;
		}
	}
}
