package folk.sisby.antique_atlas;

import net.syrupstudios.atlasbound.Atlasbound;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.syrupstudios.atlasbound.map.AtlasJunction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record StructureTileProvider(ResourceLocation id, int priority, Map<ChunkMatcher, List<TileTexture>> matchers) {
	public static final BiMap<ResourceLocation, ChunkMatcher> CHUNK_MATCHERS = HashBiMap.create();
	private static final int SEA_LEVEL = 63;

	public static ChunkMatcher getChunkMatcher(ResourceLocation id) {
		return CHUNK_MATCHERS.get(id);
	}

	public StructureTileProvider(ResourceLocation id, int priority, List<TileTexture> textures) {
		this(id, priority, Map.of(ChunkMatcher::center, textures));
	}

	public StructureTileProvider(ResourceLocation id, int priority, ChunkMatcher matcher, List<TileTexture> textures) {
		this(id, priority, Map.of(matcher, textures));
	}

	public Map<ChunkPos, TileTexture> getTextures(Function<ChunkMatcher, Collection<ChunkPos>> matcherFunction, Map<ChunkPos, String> tilePredicates) {
		Map<ChunkPos, TileTexture> outMap = new HashMap<>();
		matchers.forEach((matcher, textures) -> {
			for (ChunkPos pos : matcherFunction.apply(matcher)) {
				tilePredicates.put(pos, Objects.toString(CHUNK_MATCHERS.inverse().get(matcher), null));
				int variation = (int) (Mth.getSeed(pos.x, pos.z, pos.x * pos.z) & 0x7FFFFFFF);
				outMap.put(pos, textures.get(variation % textures.size()));
			}
		});
		return outMap;
	}

	public Map<ChunkPos, TileTexture> getTextures(BoundingBox box, Map<ChunkPos, String> tilePredicates) {
		return getTextures(matcher -> matcher.matches(box), tilePredicates);
	}

	public Map<ChunkPos, TileTexture> getTextures(BoundingBox box, List<AtlasJunction> junctions, Map<ChunkPos, String> tilePredicates) {
		return getTextures(matcher -> matcher.matches(box, junctions), tilePredicates);
	}

	public Set<TileTexture> allTextures() {
		Set<TileTexture> outSet = new HashSet<>();
		matchers.values().forEach(outSet::addAll);
		return outSet;
	}

	public interface ChunkMatcher {
		Collection<ChunkPos> matches(BoundingBox box);

		default Collection<ChunkPos> matches(BoundingBox box, List<AtlasJunction> junctions) {
			return matches(box);
		}

		static Collection<ChunkPos> center(BoundingBox box) {
			return Collections.singleton(new ChunkPos(box.getCenter()));
		}

		static Collection<ChunkPos> topAboveGround(BoundingBox box) {
			if (SEA_LEVEL <= box.maxY()) {
				return Collections.singleton(new ChunkPos(box.getCenter()));
			}

			return Collections.emptyList();
		}

		static Collection<ChunkPos> aboveGround(BoundingBox box) {
			BlockPos center = new BlockPos(box.getCenter());
			if (SEA_LEVEL - 4 <= center.getY()) {
				return Collections.singleton(new ChunkPos(center));
			}

			return Collections.emptyList();
		}

		static Collection<ChunkPos> bridgeHorizontal(BoundingBox box) {
			HashSet<ChunkPos> matches = new HashSet<>();

			if (box.getXSpan() > 16) {
				int chunkZ = box.getCenter().getZ() >> 4;
				for (int x = box.minX(); x < box.maxX(); x += 16) {
					matches.add(new ChunkPos(x >> 4, chunkZ));
				}
			}

			return matches;
		}

		static Collection<ChunkPos> bridgeVertical(BoundingBox box) {
			HashSet<ChunkPos> matches = new HashSet<>();

			if (box.getZSpan() > 16) {
				int chunkX = box.getCenter().getX() >> 4;
				for (int z = box.minZ(); z < box.maxZ(); z += 16) {
					matches.add(new ChunkPos(chunkX, z >> 4));
				}
			}

			return matches;
		}

		static Collection<ChunkPos> centerIfHorizontal(BoundingBox box) {
			if (box.getXSpan() > box.getZSpan()) {
				return Collections.singleton(new ChunkPos(box.getCenter()));
			} else {
				return Collections.emptySet();
			}
		}

		static Collection<ChunkPos> centerIfVertical(BoundingBox box) {
			if (box.getZSpan() > box.getXSpan()) {
				return Collections.singleton(new ChunkPos(box.getCenter()));
			} else {
				return Collections.emptySet();
			}
		}
	}

	public interface ChunkJunctionMatcher extends ChunkMatcher {
		default Collection<ChunkPos> matches(BoundingBox box) {
			return matches(box, List.of());
		}

		Collection<ChunkPos> matches(BoundingBox box, List<AtlasJunction> junctions);

		static Collection<ChunkPos> straightHorizontal(BoundingBox box, List<AtlasJunction> junctions) {
			if (junctions.size() == 2 && (junctions.get(0).sourceZ() == junctions.get(1).sourceZ() || junctions.get(0).sourceX() != junctions.get(1).sourceX())) {
				return List.of(new ChunkPos(box.getCenter()));
			}
			return List.of();
		}

		static Collection<ChunkPos> straightVertical(BoundingBox box, List<AtlasJunction> junctions) {
			if (junctions.size() == 2 && (junctions.get(0).sourceX() == junctions.get(1).sourceX() || junctions.get(0).sourceZ() != junctions.get(1).sourceZ())) {
				return List.of(new ChunkPos(new BlockPos((junctions.get(0).sourceX() + junctions.get(1).sourceX()) / 2, 0, (junctions.get(0).sourceZ() + junctions.get(1).sourceZ()) / 2)));
			}
			return List.of();
		}
	}

	static {
		CHUNK_MATCHERS.put(Atlasbound.legacyId("center"), ChunkMatcher::center);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("center_above_ground"), ChunkMatcher::aboveGround);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("center_top_above_ground"), ChunkMatcher::topAboveGround);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("center_horizontal"), ChunkMatcher::centerIfHorizontal);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("center_vertical"), ChunkMatcher::centerIfVertical);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("bridge_horizontal"), ChunkMatcher::bridgeHorizontal);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("bridge_vertical"), ChunkMatcher::bridgeVertical);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("path_straight_horizontal"), (ChunkJunctionMatcher) ChunkJunctionMatcher::straightHorizontal);
		CHUNK_MATCHERS.put(Atlasbound.legacyId("path_straight_vertical"), (ChunkJunctionMatcher) ChunkJunctionMatcher::straightVertical);
	}
}

