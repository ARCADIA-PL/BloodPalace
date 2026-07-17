package org.com.bloodpalace.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.com.bloodpalace.config.SpawnConfig;
import org.com.bloodpalace.util.ShowcaseDimensions;
import org.com.bloodpalace.util.ShowcaseTeleports;

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
                        §e/bloodpalace list §7- §fList structures
                        §e/bloodpalace <name> §7- §fEnter showcase dimension
                        §e/bloodpalace back §7- §fReturn to origin
                        §e/bloodpalace setspawn §7- §fSave current showcase spawn
                        §e/bloodpalace setspawn <name> §7- §fSave spawn for a structure"""), false);
        return 1;
    }

    private static int listStructures(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
            () -> Component.literal("\u00a76===== \u00a7e" + ShowcaseDimensions.STRUCTURES.size()
                + "\u00a76 structures ====="), false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ShowcaseDimensions.STRUCTURES.size(); i++) {
            sb.append("\u00a77").append(String.format("%2d", i + 1))
              .append(". \u00a7f").append(ShowcaseDimensions.STRUCTURES.get(i))
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
                "\u00a7cStand in a BloodPalace showcase dimension, or use /bloodpalace setspawn <name>."));
            return 0;
        }
        return saveSpawn(ctx.getSource(), player, structureName);
    }

    private static int setSpawnHere(CommandContext<CommandSourceStack> ctx, String structureName)
            throws CommandSyntaxException {
        if (!ShowcaseDimensions.isKnownStructure(structureName)) {
            ctx.getSource().sendFailure(Component.literal("\u00a7cUnknown structure: " + structureName));
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
                "\u00a7cFailed to save spawn config: " + SpawnConfig.getPath()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "\u00a7aSaved spawn for \u00a76" + dimensionId
                + "\u00a7a at "
                + ShowcaseDimensions.formatCoordinate(spawnPoint.x) + " "
                + ShowcaseDimensions.formatCoordinate(spawnPoint.y) + " "
                + ShowcaseDimensions.formatCoordinate(spawnPoint.z)), true);
        return 1;
    }

    private static int teleportToStructure(CommandSourceStack source, String structureName)
            throws CommandSyntaxException {
        return ShowcaseTeleports.enter(source, structureName);
    }

    private static int goBack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ShowcaseTeleports.back(ctx.getSource());
    }
}
