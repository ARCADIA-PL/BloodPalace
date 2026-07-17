package org.com.bloodplace.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class ShowcaseStructures {

    private static final BlockPos SEARCH_ORIGIN = new BlockPos(0, 120, 0);
    private static final BlockPos FALLBACK_ENTRY_POS = new BlockPos(0, 120, 0);
    private static final int SEARCH_RADIUS = 200;

    private ShowcaseStructures() {
    }

    public static BlockPos findEntryPosition(ServerLevel level, String structureName) {
        BlockPos target = locate(level, structureName);
        return target.equals(BlockPos.ZERO) ? FALLBACK_ENTRY_POS : target;
    }

    private static BlockPos locate(ServerLevel level, String structureName) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(
                ShowcaseDimensions.NAMESPACE, structureName + ShowcaseDimensions.DIM_SUFFIX));
        var reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holder = reg.getHolder(key);
        if (holder.isEmpty()) return BlockPos.ZERO;

        var result = level.getChunkSource().getGenerator()
            .findNearestMapStructure(level,
                HolderSet.direct(holder.get()),
                SEARCH_ORIGIN, SEARCH_RADIUS, false);
        return result != null ? result.getFirst() : BlockPos.ZERO;
    }
}
