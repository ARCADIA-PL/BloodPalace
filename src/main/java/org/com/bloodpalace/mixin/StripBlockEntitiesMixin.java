package org.com.bloodpalace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.com.bloodpalace.util.ShowcaseDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureTemplate.class)
public class StripBlockEntitiesMixin {

    @Inject(method = "placeInWorld", at = @At("RETURN"))
    private void bloodpalace$clearSkullNBT(
            ServerLevelAccessor level, BlockPos pos, BlockPos pivot,
            StructurePlaceSettings settings, RandomSource random, int flags,
            CallbackInfoReturnable<Boolean> cir) {

        if (!cir.getReturnValue()) return;
        if (!(level instanceof ServerLevel sl)) return;
        if (!ShowcaseDimensions.isShowcaseDimension(sl.dimension().location())) return;

        StructureTemplate self = (StructureTemplate) (Object) this;
        Vec3i size = self.getSize(settings.getRotation());
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        List<BlockPos> skullsToRemove = new ArrayList<>();

        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    scan.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    BlockEntity be = sl.getBlockEntity(scan);
                    if (be instanceof SkullBlockEntity
                            && !(sl.getBlockState(scan).getBlock() instanceof AbstractSkullBlock)) {
                        skullsToRemove.add(scan.immutable());
                    }
                    if (be instanceof RandomizableContainerBlockEntity container) {
                        container.setLootTable(null, 0);
                    }
                }
            }
        }
        for (BlockPos p : skullsToRemove) {
            sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
