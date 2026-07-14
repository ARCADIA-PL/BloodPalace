package org.com.bloodplace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureTemplate.class)
public class CleanStructureMixin {

    @Inject(method = "placeInWorld", at = @At("RETURN"))
    private void bloodplace$removeSpawners(
            ServerLevelAccessor level, BlockPos pos, BlockPos pivot,
            StructurePlaceSettings settings, RandomSource random, int flags,
            CallbackInfoReturnable<Boolean> cir) {

        if (!cir.getReturnValue()) return;
        if (!(level instanceof ServerLevel sl)) return;
        if (!bloodplace$isShowcaseDimension(sl.dimension().location())) return;

        StructureTemplate self = (StructureTemplate) (Object) this;

        Vec3i size = self.getSize(settings.getRotation());

        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    scanPos.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    BlockState state = sl.getBlockState(scanPos);
                    if (state.getBlock() instanceof SpawnerBlock) {
                        sl.setBlock(scanPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    @Unique
    private static boolean bloodplace$isShowcaseDimension(ResourceLocation dimId) {
        return "bloodplace".equals(dimId.getNamespace())
            && dimId.getPath().endsWith("_showcase");
    }
}
