package org.com.bloodpalace.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

public final class ShowcaseBlockCleaner {

    private ShowcaseBlockCleaner() {
    }

    public static void cleanChunk(ServerLevel level, LevelChunk chunk) {
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) continue;

            int minY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!shouldRemove(state)) continue;
                        scan.set(minX + x, minY + y, minZ + z);
                        level.setBlock(scan, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
            BlockPos pos = blockEntity.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (isInvalidSkullBlockEntity(state, blockEntity)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                container.setLootTable(null, 0);
                container.setChanged();
            }
        }
    }

    public static void cleanArea(ServerLevel level, BoundingBox box) {
        cleanArea(level,
            box.minX(), Math.max(box.minY(), level.getMinBuildHeight()), box.minZ(),
            box.maxX(), Math.min(box.maxY(), level.getMaxBuildHeight() - 1), box.maxZ());
    }

    public static void cleanArea(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    scan.set(x, y, z);
                    cleanBlock(level, scan);
                }
            }
        }
    }

    private static void cleanBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (shouldRemove(state) || isInvalidSkullBlockEntity(state, blockEntity)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }

        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(null, 0);
        }
    }

    public static boolean shouldRemove(BlockState state) {
        return state.is(Blocks.SPAWNER)
            || state.is(Blocks.COBWEB)
            || state.is(Blocks.PLAYER_HEAD)
            || state.is(Blocks.PLAYER_WALL_HEAD);
    }

    private static boolean isInvalidSkullBlockEntity(BlockState state, BlockEntity blockEntity) {
        return blockEntity instanceof SkullBlockEntity
            && !(state.getBlock() instanceof AbstractSkullBlock);
    }
}
