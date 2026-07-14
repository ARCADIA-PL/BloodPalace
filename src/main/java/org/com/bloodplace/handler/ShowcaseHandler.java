package org.com.bloodplace.handler;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

public class ShowcaseHandler {

    private static final String DIM_DIR = "dimensions/bloodplace";
    private static final int BORDER_SIZE = 500;

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        deleteAllShowcaseDirs(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        deleteAllShowcaseDirs(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation toDim = event.getTo().location();
        ResourceLocation fromDim = event.getFrom().location();

        if (isShowcaseDimension(toDim)) {
            WorldBorder border = player.serverLevel().getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(BORDER_SIZE);
        }

        if (isShowcaseDimension(fromDim)) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, fromDim);
            ServerLevel left = player.getServer().getLevel(key);
            if (left != null && left.players().isEmpty()) {
                deleteDimensionDir(player.getServer(), fromDim.getPath());
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!isShowcaseDimension(event.getPlayer().level().dimension().location())) return;
        if (event.getPlayer().isCreative()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isShowcaseDimension(player.level().dimension().location())) return;
        if (player.isCreative()) return;
        event.setCanceled(true);
    }

    private boolean isShowcaseDimension(ResourceLocation dimId) {
        return "bloodplace".equals(dimId.getNamespace())
            && dimId.getPath().endsWith("_showcase");
    }

    static void deleteAllShowcaseDirs(MinecraftServer server) {
        Path dir = server.getServerDirectory().toPath().resolve(DIM_DIR);
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && p.getFileName().toString().endsWith("_showcase")) {
                    deleteRecursive(p);
                }
            }
        } catch (IOException ignored) {}
    }

    static void deleteDimensionDir(MinecraftServer server, String dimName) {
        Path dir = server.getServerDirectory().toPath()
            .resolve(DIM_DIR).resolve(dimName);
        if (Files.exists(dir)) deleteRecursive(dir);
    }

    private static void deleteRecursive(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
