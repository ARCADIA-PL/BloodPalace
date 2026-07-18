package org.com.bloodpalace.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.com.bloodpalace.util.TeleportAnchorManager;

public class TeleportAnchorItem extends Item {

    public TeleportAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            return TeleportAnchorManager.place(player, context.getClickedPos()) > 0
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
        }
        return TeleportAnchorManager.open(player) > 0
            ? InteractionResult.CONSUME
            : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        return player instanceof ServerPlayer serverPlayer && TeleportAnchorManager.open(serverPlayer) > 0
            ? InteractionResultHolder.consume(stack)
            : InteractionResultHolder.fail(stack);
    }
}
