package org.com.bloodpalace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.com.bloodpalace.util.ShowcaseBlockCleaner;
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

        if (settings.getBoundingBox() != null) {
            ShowcaseBlockCleaner.cleanArea(sl, settings.getBoundingBox());
        } else {
            StructureTemplate self = (StructureTemplate) (Object) this;
            ShowcaseBlockCleaner.cleanArea(sl, self.getBoundingBox(settings, pos));
        }

        AABB placedBounds = AABB.of(settings.getBoundingBox() != null
            ? settings.getBoundingBox()
            : ((StructureTemplate) (Object) this).getBoundingBox(settings, pos));
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, placedBounds)) {
            mob.discard();
        }
    }
}
