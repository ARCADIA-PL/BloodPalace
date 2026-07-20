package org.com.bloodpalace.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.com.bloodpalace.config.SpawnConfig;
import org.com.bloodpalace.handler.ShowcaseHandler;
import org.com.bloodpalace.worldgen.prefab.PrefabChunkGenerator;

import java.util.Optional;
import java.util.UUID;

public final class ShowcaseTeleports {

    private static final String PENDING_ENTER_TOKEN = "bp_pending_enter_token";
    private static final String SKIP_SPAWN_EVENT_DIM = "bp_skip_spawn_event_dim";
    private static final String ORIGIN_DIM = "bp_origin_dim";
    private static final String ORIGIN_X = "bp_origin_x";
    private static final String ORIGIN_Y = "bp_origin_y";
    private static final String ORIGIN_Z = "bp_origin_z";
    private static final String ORIGIN_YAW = "bp_origin_yaw";
    private static final String ORIGIN_PITCH = "bp_origin_pitch";

    private ShowcaseTeleports() {
    }

    public static int enter(CommandSourceStack source, String structureName) {
        return enter(source, structureName, false);
    }

    public static int enterDebug(CommandSourceStack source, String structureName) {
        return enter(source, structureName, true);
    }

    private static int enter(CommandSourceStack source, String structureName, boolean legacyDebug) {
        if (!ShowcaseDimensions.isKnownStructure(structureName)) {
            source.sendFailure(Component.literal("\u00a7cUnknown structure: " + structureName));
            return 0;
        }

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("\u00a7cThis command requires a player."));
            return 0;
        }

        String dimString = legacyDebug
            ? ShowcaseDimensions.legacyDimensionIdForStructure(structureName)
            : ShowcaseDimensions.dimensionIdForStructure(structureName);
        ResourceKey<Level> dimKey = legacyDebug
            ? ShowcaseDimensions.legacyDimensionKeyForStructure(structureName)
            : ShowcaseDimensions.dimensionKeyForStructure(structureName);
        ResourceLocation dimensionId = legacyDebug
            ? ShowcaseDimensions.legacyDimensionLocationForStructure(structureName)
            : ShowcaseDimensions.dimensionLocationForStructure(structureName);

        ServerLevel level = source.getServer().getLevel(dimKey);
        if (level == null) {
            source.sendFailure(Component.literal("\u00a7cShowcase dimension does not exist."));
            return 0;
        }
        if (!legacyDebug
                && !(level.getChunkSource().getGenerator() instanceof PrefabChunkGenerator)) {
            source.sendFailure(Component.literal(
                "\u00a7cThis structure is not available in production mode yet. "
                    + "Use /bloodpalace debug " + structureName + " for legacy development."));
            return 0;
        }

        rememberOrigin(player);

        source.sendSuccess(() -> Component.literal(
            "\u00a77Preparing " + (legacyDebug ? "\u00a7cDEBUG \u00a76" : "\u00a76")
                + ShowcaseDimensions.formatName(structureName) + "\u00a77..."), false);

        if (ShowcaseHandler.isResetting(dimensionId)) {
            String token = UUID.randomUUID().toString();
            player.getPersistentData().putString(PENDING_ENTER_TOKEN, token);
            source.sendSuccess(() -> Component.literal(
                "\u00a77Target dimension is resetting, about \u00a7e"
                    + (ShowcaseHandler.resetTicksRemaining(dimensionId) / 20 + 1)
                    + "\u00a77 seconds remaining..."), false);
            waitForResetAndEnter(player, source.getServer(), dimKey, dimensionId,
                structureName, token);
            return 1;
        }

        preloadAndEnter(player, level, structureName);
        return 1;
    }

    public static int back(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("\u00a7cThis command requires a player."));
            return 0;
        }

        if (!player.getPersistentData().contains(ORIGIN_DIM)) {
            source.sendFailure(Component.literal("\u00a7cNo previous teleport record."));
            return 0;
        }

        ResourceLocation dimId = ResourceLocation.parse(player.getPersistentData().getString(ORIGIN_DIM));
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel target = source.getServer().getLevel(dimKey);
        if (target == null) {
            source.sendFailure(Component.literal("\u00a7cCannot find origin dimension."));
            return 0;
        }

        player.teleportTo(target,
            player.getPersistentData().getDouble(ORIGIN_X),
            player.getPersistentData().getDouble(ORIGIN_Y),
            player.getPersistentData().getDouble(ORIGIN_Z),
            player.getPersistentData().getFloat(ORIGIN_YAW),
            player.getPersistentData().getFloat(ORIGIN_PITCH));
        player.getPersistentData().remove(ORIGIN_DIM);
        source.sendSuccess(() -> Component.literal("\u00a7aReturned."), false);
        return 1;
    }

    public static boolean consumeSpawnEventSkip(ServerPlayer player, ResourceLocation dimensionId) {
        if (!dimensionId.toString().equals(player.getPersistentData().getString(SKIP_SPAWN_EVENT_DIM))) {
            return false;
        }
        player.getPersistentData().remove(SKIP_SPAWN_EVENT_DIM);
        return true;
    }

    public static void teleportToConfiguredSpawn(ServerPlayer player, ResourceLocation dimensionId) {
        configuredSpawn(dimensionId).ifPresent(spawnPoint ->
            player.getServer().tell(new TickTask(player.getServer().getTickCount(), () -> {
                if (!dimensionId.equals(player.level().dimension().location())) return;
                player.teleportTo(player.serverLevel(),
                    spawnPoint.x, spawnPoint.y, spawnPoint.z,
                    spawnPoint.yaw, spawnPoint.pitch);
            })));
    }

    private static Optional<SpawnConfig.SpawnPoint> configuredSpawn(ResourceLocation dimensionId) {
        return SpawnConfig.get(dimensionId.toString());
    }

    private static void rememberOrigin(ServerPlayer player) {
        Level origin = player.level();
        player.getPersistentData().putString(ORIGIN_DIM, origin.dimension().location().toString());
        player.getPersistentData().putDouble(ORIGIN_X, player.getX());
        player.getPersistentData().putDouble(ORIGIN_Y, player.getY());
        player.getPersistentData().putDouble(ORIGIN_Z, player.getZ());
        player.getPersistentData().putFloat(ORIGIN_YAW, player.getYRot());
        player.getPersistentData().putFloat(ORIGIN_PITCH, player.getXRot());
    }

    private static void preloadAndEnter(ServerPlayer player, ServerLevel level,
            String structureName) {
        if (level.getChunkSource().getGenerator() instanceof PrefabChunkGenerator) {
            player.getPersistentData().remove(PENDING_ENTER_TOKEN);
            doEnter(player, level, structureName);
            return;
        }
        try {
            String token = UUID.randomUUID().toString();
            player.getPersistentData().putString(PENDING_ENTER_TOKEN, token);
            if (!ChunkyPreloadCoordinator.await(player.getServer(), player.getUUID(),
                    level.dimension(), structureName, token)) {
                player.getPersistentData().remove(PENDING_ENTER_TOKEN);
                doEnter(player, level, structureName);
            }
        } catch (Exception e) {
            player.getPersistentData().remove(PENDING_ENTER_TOKEN);
            doEnter(player, level, structureName);
        }
    }

    private static void waitForResetAndEnter(ServerPlayer player, MinecraftServer server,
            ResourceKey<Level> dimKey, ResourceLocation dimensionId,
            String structureName, String token) {
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (!token.equals(player.getPersistentData().getString(PENDING_ENTER_TOKEN))) return;

            if (ShowcaseHandler.isResetting(dimensionId)) {
                waitForResetAndEnter(player, server, dimKey, dimensionId,
                    structureName, token);
                return;
            }

            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                player.getPersistentData().remove(PENDING_ENTER_TOKEN);
                player.sendSystemMessage(Component.literal("\u00a7cShowcase dimension does not exist."));
                return;
            }

            preloadAndEnter(player, level, structureName);
        }));
    }

    static void completeChunkyPreload(ChunkyPreloadCoordinator.PendingEntry entry) {
        ServerPlayer player = entry.server().getPlayerList().getPlayer(entry.playerId());
        if (player == null) return;
        ServerLevel level = entry.server().getLevel(entry.dimension());
        if (level == null) {
            expireChunkyPreload(entry);
            return;
        }
        doEnterIfPending(player, level, entry.structureName(), entry.token());
    }

    static void expireChunkyPreload(ChunkyPreloadCoordinator.PendingEntry entry) {
        ServerPlayer player = entry.server().getPlayerList().getPlayer(entry.playerId());
        if (player == null) return;
        if (!entry.token().equals(player.getPersistentData().getString(PENDING_ENTER_TOKEN))) return;
        player.getPersistentData().remove(PENDING_ENTER_TOKEN);
        player.sendSystemMessage(Component.literal("\u00a7cChunk preloading timed out. Please try again."));
    }

    public static void cancelPendingEnter(ServerPlayer player) {
        player.getPersistentData().remove(PENDING_ENTER_TOKEN);
        ChunkyPreloadCoordinator.cancel(player.getUUID());
    }

    private static void doEnterIfPending(ServerPlayer player, ServerLevel level, String name, String token) {
        if (!token.equals(player.getPersistentData().getString(PENDING_ENTER_TOKEN))) return;
        player.getPersistentData().remove(PENDING_ENTER_TOKEN);
        doEnter(player, level, name);
    }

    private static void doEnter(ServerPlayer player, ServerLevel level, String name) {
        String targetDimensionId = level.dimension().location().toString();
        String spawnConfigDimensionId = ShowcaseDimensions.dimensionIdForStructure(name);
        player.getPersistentData().putString(SKIP_SPAWN_EVENT_DIM, targetDimensionId);
        Optional<SpawnConfig.SpawnPoint> configuredSpawn = SpawnConfig.get(spawnConfigDimensionId);
        if (configuredSpawn.isPresent()) {
            SpawnConfig.SpawnPoint spawn = configuredSpawn.get();
            player.teleportTo(level, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
        } else {
            BlockPos target = ShowcaseStructures.findEntryPosition(level, name);
            player.teleportTo(level,
                target.getX() + 0.5, target.getY() + 10, target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        }
        player.sendSystemMessage(Component.literal(
            "\u00a7aTeleported to "
                + (ShowcaseDimensions.isLegacyShowcaseDimension(level.dimension().location())
                    ? "\u00a7cDEBUG \u00a76" : "\u00a76")
                + ShowcaseDimensions.formatName(name)));
    }
}
