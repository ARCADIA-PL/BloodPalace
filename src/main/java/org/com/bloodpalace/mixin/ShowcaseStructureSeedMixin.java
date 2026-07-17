package org.com.bloodpalace.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.com.bloodpalace.util.ShowcaseDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ChunkGeneratorStructureState.class)
public class ShowcaseStructureSeedMixin {

    private static final long BLOODPALACE_SHOWCASE_SEED = 0L;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static long bloodpalace$fixShowcaseStructureSeed(
            long seed,
            RandomState randomState,
            BiomeSource biomeSource,
            long originalSeed,
            long concentricRingsSeed,
            List<Holder<StructureSet>> possibleStructureSets) {

        return hasShowcaseStructureSet(possibleStructureSets) ? BLOODPALACE_SHOWCASE_SEED : seed;
    }

    private static boolean hasShowcaseStructureSet(List<Holder<StructureSet>> possibleStructureSets) {
        for (Holder<StructureSet> holder : possibleStructureSets) {
            if (holder.unwrapKey()
                .map(key -> ShowcaseDimensions.isShowcaseStructureSet(key.location()))
                .orElse(false)) {
                return true;
            }
        }
        return false;
    }
}
