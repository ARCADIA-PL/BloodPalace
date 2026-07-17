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
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ShowcaseBlockCleaner {

    private ShowcaseBlockCleaner() {
    }

    public static void cleanChunk(ServerLevel level, LevelChunk chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        cleanArea(level,
            minX, level.getMinBuildHeight(), minZ,
            minX + 15, level.getMaxBuildHeight() - 1, minZ + 15);
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

        if (state.is(Blocks.SPAWNER)
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.PLAYER_HEAD)
                || state.is(Blocks.PLAYER_WALL_HEAD)
                || isInvalidSkullBlockEntity(state, blockEntity)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }

        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(null, 0);
        }
    }

    private static boolean isInvalidSkullBlockEntity(BlockState state, BlockEntity blockEntity) {
        return blockEntity instanceof SkullBlockEntity
            && !(state.getBlock() instanceof AbstractSkullBlock);
    }
}
