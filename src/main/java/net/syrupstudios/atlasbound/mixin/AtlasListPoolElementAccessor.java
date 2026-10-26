package net.syrupstudios.atlasbound.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ListPoolElement.class)
public interface AtlasListPoolElementAccessor {
    @Accessor("elements")
    List<StructurePoolElement> atlasbound$getElements();
}
