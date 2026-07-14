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
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.popcraft.chunky.ChunkyProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BloodPlaceCommand {

    private static final String DIM_PREFIX = "bloodplace:";
    private static final String DIM_SUFFIX = "_showcase";

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
                    .executes(ctx -> teleportToStructure(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "structure"))))
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§6===== BloodPlace Showcase =====\n" +
            "§e/bloodplace list §7— §f列出所有可用结构\n" +
            "§e/bloodplace <名称> §7— §f传送到结构展示维度\n" +
            "§e/bloodplace back §7— §f返回主世界"), false);
        return 1;
    }

    private static int listStructures(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
            () -> Component.literal("§6===== §e" + STRUCTURES.size() + "§6 个可用结构 ====="), false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < STRUCTURES.size(); i++) {
            sb.append("§7").append(String.format("%2d", i + 1))
              .append(". §f").append(STRUCTURES.get(i))
              .append(i % 3 == 2 ? "\n" : "    ");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestStructures(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String name : STRUCTURES) {
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    // ── /bloodplace <name> ──
    private static int teleportToStructure(CommandSourceStack source, String structureName)
            throws CommandSyntaxException {
        if (!STRUCTURES.contains(structureName)) {
            source.sendFailure(Component.literal("§c未知结构: " + structureName));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        String dimStr = DIM_PREFIX + structureName + DIM_SUFFIX;
        ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath("bloodplace",
            structureName + DIM_SUFFIX);
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);

        // Store origin
        Level origin = player.level();
        player.getPersistentData().putString("bp_origin_dim",
            origin.dimension().location().toString());
        player.getPersistentData().putDouble("bp_origin_x", player.getX());
        player.getPersistentData().putDouble("bp_origin_y", player.getY());
        player.getPersistentData().putDouble("bp_origin_z", player.getZ());

        source.sendSuccess(
            () -> Component.literal("§7正在预加载 §6" + formatName(structureName) + "§7..."), false);

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
        BlockPos target = locate(level, name);
        if (target.equals(BlockPos.ZERO)) target = new BlockPos(0, 120, 0);
        player.teleportTo(level,
            target.getX() + 0.5, target.getY() + 10, target.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(
            Component.literal("§a已传送到 §6" + formatName(name)));
    }

    // ── /bloodplace back ──
    private static int goBack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!player.getPersistentData().contains("bp_origin_dim")) {
            ctx.getSource().sendFailure(Component.literal("§c没有之前的传送记录"));
            return 0;
        }
        String dimStr = player.getPersistentData().getString("bp_origin_dim");
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse(dimStr));
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

    private static BlockPos locate(ServerLevel level, String name) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("bloodplace", name + "_showcase"));
        var reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holder = reg.getHolder(key);
        if (holder.isEmpty()) return BlockPos.ZERO;
        var result = level.getChunkSource().getGenerator()
            .findNearestMapStructure(level,
                HolderSet.direct(holder.get()),
                new BlockPos(0, 120, 0), 200, false);
        return result != null ? result.getFirst() : BlockPos.ZERO;
    }

    private static String formatName(String name) {
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
