package org.com.bloodplace.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.com.bloodplace.util.ShowcaseDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public class StripLootTableMixin {

    @Inject(method = "load", at = @At("HEAD"))
    private void bloodplace$stripLoot(CompoundTag tag, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self instanceof RandomizableContainerBlockEntity)) return;

        Level level = self.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        if (!ShowcaseDimensions.isShowcaseDimension(sl.dimension().location())) return;

        tag.remove("Items");
        tag.remove("LootTable");
        tag.remove("LootTableSeed");
    }
}
