package org.com.bloodpalace.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.com.bloodpalace.config.SpawnConfig;
import org.com.bloodpalace.util.ShowcaseDimensions;
import org.com.bloodpalace.util.ShowcaseStructures;
import org.popcraft.chunky.ChunkyProvider;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class BloodPalaceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("bloodpalace")
                .executes(BloodPalaceCommand::showHelp)
                .then(Commands.literal("list")
                    .executes(BloodPalaceCommand::listStructures))
                .then(Commands.literal("back")
                    .executes(BloodPalaceCommand::goBack))
                .then(Commands.literal("setspawn")
                    .requires(source -> source.hasPermission(2))
                    .executes(BloodPalaceCommand::setSpawnHere)
                    .then(Commands.argument("structure", StringArgumentType.word())
                        .suggests(BloodPalaceCommand::suggestStructures)
                        .executes(ctx -> setSpawnHere(
                            ctx,
                            StringArgumentType.getString(ctx, "structure")))))
                .then(Commands.argument("structure", StringArgumentType.word())
                    .suggests(BloodPalaceCommand::suggestStructures)
                    .executes(ctx -> teleportToStructure(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "structure"))))
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                """
                        §6===== BloodPalace Showcase =====
                        §e/bloodpalace list §7— §f列出所有可用结构
                        §e/bloodpalace <名称> §7— §f传送到结构展示维度
                        §e/bloodpalace back §7— §f返回主世界"""), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "\u00a7e/bloodpalace setspawn \u00a77- \u00a7fSave current showcase dimension spawn\n" +
            "\u00a7e/bloodpalace setspawn <name> \u00a77- \u00a7fSave current position for a structure"), false);
        return 1;
    }

    private static int listStructures(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
            () -> Component.literal("§6===== §e" + ShowcaseDimensions.STRUCTURES.size()
                + "§6 个可用结构 ====="), false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ShowcaseDimensions.STRUCTURES.size(); i++) {
            sb.append("§7").append(String.format("%2d", i + 1))
              .append(". §f").append(ShowcaseDimensions.STRUCTURES.get(i))
              .append(i % 3 == 2 ? "\n" : "    ");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestStructures(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String name : ShowcaseDimensions.STRUCTURES) {
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static int setSpawnHere(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String structureName = ShowcaseDimensions.structureFromShowcaseDimension(
            player.level().dimension().location());
        if (structureName == null) {
            ctx.getSource().sendFailure(Component.literal(
                "§cStand in a BloodPalace showcase dimension, or use /bloodpalace setspawn <name>."));
            return 0;
        }
        return saveSpawn(ctx.getSource(), player, structureName);
    }

    private static int setSpawnHere(CommandContext<CommandSourceStack> ctx, String structureName)
            throws CommandSyntaxException {
        if (!ShowcaseDimensions.isKnownStructure(structureName)) {
            ctx.getSource().sendFailure(Component.literal("§cUnknown structure: " + structureName));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return saveSpawn(ctx.getSource(), player, structureName);
    }

    private static int saveSpawn(CommandSourceStack source, ServerPlayer player, String structureName) {
        SpawnConfig.SpawnPoint spawnPoint = new SpawnConfig.SpawnPoint(
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot());

        String dimensionId = ShowcaseDimensions.dimensionIdForStructure(structureName);
        try {
            SpawnConfig.set(dimensionId, spawnPoint);
        } catch (IOException e) {
            source.sendFailure(Component.literal(
                "§cFailed to save spawn config: " + SpawnConfig.getPath()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "§aSaved spawn for §6" + dimensionId
                + "§a at "
                + ShowcaseDimensions.formatCoordinate(spawnPoint.x) + " "
                + ShowcaseDimensions.formatCoordinate(spawnPoint.y) + " "
                + ShowcaseDimensions.formatCoordinate(spawnPoint.z)), true);
        return 1;
    }

    // ── /bloodpalace <name> ──
    private static int teleportToStructure(CommandSourceStack source, String structureName)
            throws CommandSyntaxException {
        if (!ShowcaseDimensions.isKnownStructure(structureName)) {
            source.sendFailure(Component.literal("§c未知结构: " + structureName));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        String dimStr = ShowcaseDimensions.dimensionIdForStructure(structureName);
        var dimKey = ShowcaseDimensions.dimensionKeyForStructure(structureName);

        // Store origin
        Level origin = player.level();
        player.getPersistentData().putString("bp_origin_dim",
            origin.dimension().location().toString());
        player.getPersistentData().putDouble("bp_origin_x", player.getX());
        player.getPersistentData().putDouble("bp_origin_y", player.getY());
        player.getPersistentData().putDouble("bp_origin_z", player.getZ());
        player.getPersistentData().putFloat("bp_origin_yaw", player.getYRot());
        player.getPersistentData().putFloat("bp_origin_pitch", player.getXRot());

        source.sendSuccess(
            () -> Component.literal("§7正在预加载 §6"
                + ShowcaseDimensions.formatName(structureName) + "§7..."), false);

        // Get the dimension (triggers creation) then preload with Chunky
        ServerLevel level = source.getServer().getLevel(dimKey);
        if (level == null) {
            source.sendFailure(Component.literal("§c展示维度不存在"));
            return 0;
        }

        preloadAndEnter(player, level, dimStr, structureName);

        return 1;
    }

    private static void preloadAndEnter(ServerPlayer player, ServerLevel level,
            String dimKey, String structureName) {
        try {
            var api = ChunkyProvider.get().getApi();

            // Register callback BEFORE starting task
            api.onGenerationComplete(event -> {
                if (!dimKey.equals(event.world())) return;
                player.getServer().tell(
                    new net.minecraft.server.TickTask(player.getServer().getTickCount(), () ->
                        doEnter(player, level, structureName)));
            });

            if (!api.isRunning(dimKey)) {
                api.startTask(dimKey, "square", 0, 0, 200, 200, "concentric");
            }
        } catch (Exception e) {
            // Chunky not available — enter directly
            doEnter(player, level, structureName);
        }
    }

    private static void doEnter(ServerPlayer player, ServerLevel level, String name) {
        BlockPos target = ShowcaseStructures.findEntryPosition(level, name);
        player.teleportTo(level,
            target.getX() + 0.5, target.getY() + 10, target.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(
            Component.literal("§a已传送到 §6" + ShowcaseDimensions.formatName(name)));
    }

    // ── /bloodpalace back ──
    private static int goBack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!player.getPersistentData().contains("bp_origin_dim")) {
            ctx.getSource().sendFailure(Component.literal("§c没有之前的传送记录"));
            return 0;
        }
        String dimStr = player.getPersistentData().getString("bp_origin_dim");
        ResourceLocation dimId = ResourceLocation.parse(dimStr);
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        ServerLevel target = ctx.getSource().getServer().getLevel(dimKey);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§c无法找到原始维度"));
            return 0;
        }
        player.teleportTo(target,
            player.getPersistentData().getDouble("bp_origin_x"),
            player.getPersistentData().getDouble("bp_origin_y"),
            player.getPersistentData().getDouble("bp_origin_z"),
            player.getPersistentData().getFloat("bp_origin_yaw"),
            player.getPersistentData().getFloat("bp_origin_pitch"));
        player.getPersistentData().remove("bp_origin_dim");
        ctx.getSource().sendSuccess(() -> Component.literal("§a已返回"), false);
        return 1;
    }

}
