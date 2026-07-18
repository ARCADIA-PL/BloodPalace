package org.com.bloodpalace.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.com.bloodpalace.entity.BloodPalaceEntityTypes;
import org.com.bloodpalace.entity.TeleportAnchorEntity;
import org.com.bloodpalace.network.BloodPalaceNetwork;
import org.com.bloodpalace.network.TeleportAnchorInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TeleportAnchorManager {

    private TeleportAnchorManager() {
    }

    public static int place(ServerPlayer player, BlockPos supportPos) {
        ServerLevel level = player.serverLevel();
        if (!isBloodPalaceDimension(level)) {
            player.sendSystemMessage(Component.literal("\u00a7cTeleport anchors can only be placed in BloodPalace dimensions."));
            return 0;
        }

        BlockState support = level.getBlockState(supportPos);
        if (!support.isFaceSturdy(level, supportPos, net.minecraft.core.Direction.UP)) {
            player.sendSystemMessage(Component.literal("\u00a7cTeleport anchors must be placed on top of a sturdy block."));
            return 0;
        }

        BlockPos anchorPos = supportPos.above();
        if (!level.noCollision(BloodPalaceEntityTypes.TELEPORT_ANCHOR.get().getAABB(
                anchorPos.getX() + 0.1D,
                anchorPos.getY(),
                anchorPos.getZ() + 0.1D))) {
            player.sendSystemMessage(Component.literal("\u00a7cThere is not enough space for a teleport anchor here."));
            return 0;
        }

        TeleportAnchorEntity anchor = BloodPalaceEntityTypes.TELEPORT_ANCHOR.get().create(level);
        if (anchor == null) {
            player.sendSystemMessage(Component.literal("\u00a7cFailed to create teleport anchor."));
            return 0;
        }

        anchor.moveTo(anchorPos.getX() + 0.5D, anchorPos.getY(), anchorPos.getZ() + 0.5D,
            player.getYRot(), 0.0F);
        anchor.setAnchorName(defaultName(anchor));
        level.addFreshEntity(anchor);
        player.sendSystemMessage(Component.literal("\u00a7aPlaced teleport anchor \u00a76" + anchor.getAnchorName()));
        return 1;
    }

    public static int open(ServerPlayer player) {
        if (!isBloodPalaceDimension(player.serverLevel())) {
            player.sendSystemMessage(Component.literal("\u00a7cTeleport anchors are only available in BloodPalace dimensions."));
            return 0;
        }
        BloodPalaceNetwork.openTeleportAnchors(player, list(player.serverLevel()));
        return 1;
    }

    public static int teleport(ServerPlayer player, UUID anchorId) {
        ServerLevel level = player.serverLevel();
        if (!isBloodPalaceDimension(level)) {
            player.sendSystemMessage(Component.literal("\u00a7cTeleport anchors are only available in BloodPalace dimensions."));
            return 0;
        }

        TeleportAnchorEntity anchor = find(level, anchorId);
        if (anchor == null) {
            player.sendSystemMessage(Component.literal("\u00a7cTeleport anchor is no longer available."));
            return 0;
        }

        player.teleportTo(level, anchor.getX(), anchor.getY() + 0.1D, anchor.getZ(),
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("\u00a7aTeleported to \u00a76" + anchor.getAnchorName()));
        return 1;
    }

    private static List<TeleportAnchorInfo> list(ServerLevel level) {
        List<TeleportAnchorInfo> anchors = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof TeleportAnchorEntity anchor && anchor.isAlive()) {
                anchors.add(new TeleportAnchorInfo(
                    anchor.getUUID(),
                    anchor.getAnchorName(),
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ()));
            }
        }
        anchors.sort(Comparator.comparing(TeleportAnchorInfo::name)
            .thenComparingDouble(TeleportAnchorInfo::x)
            .thenComparingDouble(TeleportAnchorInfo::z));
        return anchors;
    }

    private static TeleportAnchorEntity find(ServerLevel level, UUID anchorId) {
        Entity entity = level.getEntity(anchorId);
        return entity instanceof TeleportAnchorEntity anchor && anchor.isAlive() ? anchor : null;
    }

    private static boolean isBloodPalaceDimension(ServerLevel level) {
        return ShowcaseDimensions.isShowcaseDimension(level.dimension().location());
    }

    private static String defaultName(TeleportAnchorEntity anchor) {
        return "Anchor " + anchor.getBlockX() + " " + anchor.getBlockY() + " " + anchor.getBlockZ();
    }
}
