package org.com.bloodpalace.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.com.bloodpalace.util.RoomEditor;

public class RoomCoreItem extends Item {

    public RoomCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative() || !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("\u00a7cOnly creative operators can place room cores."));
            return InteractionResult.FAIL;
        }

        BlockPos center = context.getClickedPos().relative(context.getClickedFace());
        return RoomEditor.createCore(player, center) > 0
            ? InteractionResult.CONSUME
            : InteractionResult.FAIL;
    }
}
