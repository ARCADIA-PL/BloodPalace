package org.com.bloodplace.command;

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
import org.com.bloodplace.handler.ShowcaseHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BloodPlaceCommand {

    public static final List<String> STRUCTURES = List.of(
        "abandoned_temple", "aviary", "bandit_towers", "bandit_village", "bathhouse",
        "ceryneian_hind", "coliseum", "fishing_hut", "foundry", "giant_mushroom",
        "greenwood_pub", "heavenly_challenger", "heavenly_conqueror", "heavenly_rider",
        "illager_campsite", "illager_corsair", "illager_fort", "illager_galley",
        "illager_windmill", "infested_temple", "jungle_tree_house", "keep_kayra",
        "lighthouse", "mechanical_nest", "merchant_campsite", "mining_system", "monastery",
        "mushroom_house", "mushroom_mines", "mushroom_village", "plague_asylum",
        "scorched_mines", "shiraz_palace", "small_blimp", "small_prairie_house",
        "thornborn_towers", "typhon", "undead_pirate_ship", "wishing_well"
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("bloodplace")
                .executes(BloodPlaceCommand::showHelp)
                .then(Commands.literal("list")
                    .executes(BloodPlaceCommand::listStructures))
                .then(Commands.literal("back")
                    .executes(BloodPlaceCommand::goBack))
                .then(Commands.argument("structure", StringArgumentType.word())
                    .suggests(BloodPlaceCommand::suggestStructures)
                    .executes(ctx -> teleportToShowcase(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "structure"))))
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
            () -> Component.literal(
                "§6===== BloodPlace Showcase =====\n" +
                "§e/bloodplace list §7— §f列出所有可用结构\n" +
                "§e/bloodplace <名称> §7— §f传送到结构展示维度\n" +
                "§e/bloodplace back §7— §f返回主世界"),
            false);
        return 1;
    }

    private static int listStructures(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
            () -> Component.literal("§6===== §e" + STRUCTURES.size() + "§6 个可用结构 ====="),
            false);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < STRUCTURES.size(); i++) {
            sb.append("§7").append(String.format("%2d", i + 1))
              .append(". §f").append(STRUCTURES.get(i))
              .append(i % 3 == 2 ? "\n" : "    ");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        ctx.getSource().sendSuccess(
            () -> Component.literal("§7使用 §e/bloodplace <名称> §7进入展示维度"),
            false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestStructures(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String name : STRUCTURES) {
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static int teleportToShowcase(CommandSourceStack source, String structureName) throws CommandSyntaxException {
        if (!STRUCTURES.contains(structureName)) {
            source.sendFailure(Component.literal("§c未知结构: " + structureName));
            source.sendFailure(Component.literal("§7使用 §e/bloodplace list §7查看所有可用结构"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();

        ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath("bloodplace", structureName + "_showcase");
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);

        // Reset: unload from memory + delete save → getLevel creates fresh dimension
        ShowcaseHandler.resetDimension(source.getServer(), dimKey, structureName + "_showcase");

        ServerLevel showcaseLevel = source.getServer().getLevel(dimKey);
        if (showcaseLevel == null) {
            source.sendFailure(Component.literal("§c展示维度不存在: " + dimId));
            return 0;
        }

        // Store origin for /bloodplace back
        Level originLevel = player.level();
        player.getPersistentData().putString("bp_origin_dim",
            originLevel.dimension().location().toString());
        player.getPersistentData().putDouble("bp_origin_x", player.getX());
        player.getPersistentData().putDouble("bp_origin_y", player.getY());
        player.getPersistentData().putDouble("bp_origin_z", player.getZ());
        player.getPersistentData().putFloat("bp_origin_yaw", player.getYRot());
        player.getPersistentData().putFloat("bp_origin_pitch", player.getXRot());

        // Teleport to dimension spawn
        player.teleportTo(showcaseLevel,
            0.5, 120.0, 0.5,
            player.getYRot(), player.getXRot());

        source.sendSuccess(
            () -> Component.literal("§a已传送到 §6" +
                formatName(structureName) + " §a展示维度"),
            false);

        return 1;
    }

    private static int goBack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (!player.getPersistentData().contains("bp_origin_dim")) {
            ctx.getSource().sendFailure(Component.literal("§c没有之前的传送记录"));
            return 0;
        }

        String dimStr = player.getPersistentData().getString("bp_origin_dim");
        double x = player.getPersistentData().getDouble("bp_origin_x");
        double y = player.getPersistentData().getDouble("bp_origin_y");
        double z = player.getPersistentData().getDouble("bp_origin_z");
        float yaw = player.getPersistentData().getFloat("bp_origin_yaw");
        float pitch = player.getPersistentData().getFloat("bp_origin_pitch");

        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(dimStr));
        ServerLevel targetLevel = ctx.getSource().getServer().getLevel(dimKey);

        if (targetLevel == null) {
            ctx.getSource().sendFailure(Component.literal("§c无法找到原始维度: " + dimStr));
            return 0;
        }

        player.teleportTo(targetLevel, x, y, z, yaw, pitch);

        // Clean up stored data
        player.getPersistentData().remove("bp_origin_dim");
        player.getPersistentData().remove("bp_origin_x");
        player.getPersistentData().remove("bp_origin_y");
        player.getPersistentData().remove("bp_origin_z");
        player.getPersistentData().remove("bp_origin_yaw");
        player.getPersistentData().remove("bp_origin_pitch");

        ctx.getSource().sendSuccess(() -> Component.literal("§a已返回"), false);
        return 1;
    }

    private static String formatName(String name) {
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
