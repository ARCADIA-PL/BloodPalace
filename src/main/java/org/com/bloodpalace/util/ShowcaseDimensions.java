package org.com.bloodpalace.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ShowcaseDimensions {

    public static final String NAMESPACE = "bloodpalace";
    public static final String DIM_SUFFIX = "_showcase";

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

    private ShowcaseDimensions() {
    }

    public static boolean isKnownStructure(String structureName) {
        return STRUCTURES.contains(structureName);
    }

    public static String dimensionIdForStructure(String structureName) {
        return NAMESPACE + ":" + structureName + DIM_SUFFIX;
    }

    public static ResourceLocation dimensionLocationForStructure(String structureName) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, structureName + DIM_SUFFIX);
    }

    public static ResourceKey<Level> dimensionKeyForStructure(String structureName) {
        return ResourceKey.create(Registries.DIMENSION, dimensionLocationForStructure(structureName));
    }

    public static boolean isShowcaseDimension(ResourceLocation dimensionId) {
        return NAMESPACE.equals(dimensionId.getNamespace())
            && dimensionId.getPath().endsWith(DIM_SUFFIX);
    }

    public static String structureFromShowcaseDimension(ResourceLocation dimensionId) {
        if (!isShowcaseDimension(dimensionId)) return null;

        String path = dimensionId.getPath();
        String structureName = path.substring(0, path.length() - DIM_SUFFIX.length());
        return isKnownStructure(structureName) ? structureName : null;
    }

    public static String formatName(String name) {
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    public static String formatCoordinate(double value) {
        return String.format("%.2f", value);
    }
}
