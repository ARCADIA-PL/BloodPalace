package org.com.bloodpalace.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import org.com.bloodpalace.util.RoomEditor;
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
                .then(Commands.literal("room")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("edit")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> roomEdit(ctx,
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("move")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-128, 128))
                            .then(Commands.argument("y", IntegerArgumentType.integer(-128, 128))
                                .then(Commands.argument("z", IntegerArgumentType.integer(-128, 128))
                                    .executes(BloodPalaceCommand::roomMove)))))
                    .then(Commands.literal("scale")
                        .then(Commands.argument("x", IntegerArgumentType.integer(-64, 64))
                            .then(Commands.argument("y", IntegerArgumentType.integer(-64, 64))
                                .then(Commands.argument("z", IntegerArgumentType.integer(-64, 64))
                                    .executes(BloodPalaceCommand::roomScale)))))
                    .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> roomName(ctx,
                                StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("save")
                        .executes(BloodPalaceCommand::roomSave))
                    .then(Commands.literal("cancel")
                        .executes(BloodPalaceCommand::roomCancel))
                    .then(Commands.literal("list")
                        .executes(BloodPalaceCommand::roomList))
                    .then(Commands.literal("show")
                        .executes(BloodPalaceCommand::roomShowAll)
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> roomShow(ctx,
                                StringArgumentType.getString(ctx, "id")))))
                    .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes(ctx -> roomDelete(ctx,
                                StringArgumentType.getString(ctx, "id"))))))
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
                        \u00a76===== BloodPalace Showcase =====
                        \u00a7e/bloodpalace list \u00a77- \u00a7fList structures
                        \u00a7e/bloodpalace <name> \u00a77- \u00a7fEnter showcase dimension
                        \u00a7e/bloodpalace back \u00a77- \u00a7fReturn to origin
                        \u00a7e/bloodpalace setspawn \u00a77- \u00a7fSave current showcase spawn
                        \u00a7e/bloodpalace setspawn <name> \u00a77- \u00a7fSave spawn for a structure
                        \u00a7e/bloodpalace room edit <id> \u00a77- \u00a7fCreate or edit a room core
                        \u00a7e/bloodpalace room move <x> <y> <z> \u00a77- \u00a7fMove editing room
                        \u00a7e/bloodpalace room scale <x> <y> <z> \u00a77- \u00a7fScale editing room"""), false);
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

    private static int roomEdit(CommandContext<CommandSourceStack> ctx, String roomId)
            throws CommandSyntaxException {
        return RoomEditor.edit(ctx.getSource().getPlayerOrException(), roomId);
    }

    private static int roomMove(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return RoomEditor.move(
            ctx.getSource().getPlayerOrException(),
            IntegerArgumentType.getInteger(ctx, "x"),
            IntegerArgumentType.getInteger(ctx, "y"),
            IntegerArgumentType.getInteger(ctx, "z"));
    }

    private static int roomScale(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return RoomEditor.scale(
            ctx.getSource().getPlayerOrException(),
            IntegerArgumentType.getInteger(ctx, "x"),
            IntegerArgumentType.getInteger(ctx, "y"),
            IntegerArgumentType.getInteger(ctx, "z"));
    }

    private static int roomName(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        return RoomEditor.rename(ctx.getSource().getPlayerOrException(), name);
    }

    private static int roomSave(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return RoomEditor.save(ctx.getSource().getPlayerOrException());
    }

    private static int roomCancel(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return RoomEditor.cancel(ctx.getSource().getPlayerOrException());
    }

    private static int roomList(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return RoomEditor.list(ctx.getSource().getPlayerOrException());
    }

    private static int roomShowAll(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return RoomEditor.showAll(ctx.getSource().getPlayerOrException());
    }

    private static int roomShow(CommandContext<CommandSourceStack> ctx, String roomId)
            throws CommandSyntaxException {
        return RoomEditor.show(ctx.getSource().getPlayerOrException(), roomId);
    }

    private static int roomDelete(CommandContext<CommandSourceStack> ctx, String roomId)
            throws CommandSyntaxException {
        return RoomEditor.delete(ctx.getSource().getPlayerOrException(), roomId);
    }

    private static int teleportToStructure(CommandSourceStack source, String structureName)
            throws CommandSyntaxException {
        return ShowcaseTeleports.enter(source, structureName);
    }

    private static int goBack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ShowcaseTeleports.back(ctx.getSource());
    }
}
