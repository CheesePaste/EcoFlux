package com.cp.ecoflux.api.registry;

import com.cp.ecoflux.api.adapter.TreeGrowthProfile;
import com.cp.ecoflux.plant.tree.spacecolonization.SpaceColonizationParams;
import com.cp.ecoflux.plant.tree.spacecolonization.SpaceColonizationProfile;
import com.cp.ecoflux.plant.tree.profiles.MushroomGrowthProfile;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe public registry for tree growth profiles.
 *
 * <p>External mods call {@link #register(TreeGrowthProfile)} during mod init
 * to add custom tree types. Built-in vanilla profiles are initialized by
 * {@link #initBuiltin()}, called from {@code TreeGrowthHandler} static init.
 */
public final class TreeGrowthProfileRegistry {
    private static final ConcurrentHashMap<ResourceLocation, TreeGrowthProfile> PROFILES =
            new ConcurrentHashMap<>();

    private TreeGrowthProfileRegistry() {}

    /** Register a growth profile. Call during mod init, before world load. */
    public static void register(TreeGrowthProfile profile) {
        PROFILES.put(profile.treeType(), profile);
    }

    /** Register an alias pointing to an already-registered profile. */
    public static void registerAlias(ResourceLocation alias, ResourceLocation targetType) {
        TreeGrowthProfile profile = PROFILES.get(targetType);
        if (profile != null) {
            PROFILES.put(alias, profile);
        }
    }

    /** Find a profile by tree type ID. Returns null if not found. */
    @Nullable
    public static TreeGrowthProfile find(ResourceLocation key) {
        TreeGrowthProfile profile = PROFILES.get(key);
        if (profile != null) return profile;

        // Fallback: if non-minecraft namespace, try minecraft namespace with same path
        if (!"minecraft".equals(key.getNamespace())) {
            return PROFILES.get(ResourceLocation.fromNamespaceAndPath("minecraft", key.getPath()));
        }
        return null;
    }

    /** Resolve a growth profile from a sapling block ID. */
    @Nullable
    public static TreeGrowthProfile resolveFromSapling(ResourceLocation saplingId) {
        TreeGrowthProfile profile = find(saplingId);
        if (profile != null) return profile;

        String path = saplingId.getPath();
        if (path.endsWith("_sapling")) {
            String treeName = path.substring(0, path.length() - "_sapling".length());
            return find(ResourceLocation.fromNamespaceAndPath(saplingId.getNamespace(), treeName));
        }
        return null;
    }

    /** Resolve a growth profile from a log block ID. */
    @Nullable
    public static TreeGrowthProfile resolveFromLog(ResourceLocation logId) {
        String path = logId.getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        for (String suffix : new String[]{"_log", "_wood", "_stem"}) {
            if (path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        return find(ResourceLocation.fromNamespaceAndPath(logId.getNamespace(), path));
    }

    /** Called once during TreeGrowthHandler static init. */
    public static void initBuiltin() {
        reg(new SpaceColonizationProfile(id("oak"), 1200, Blocks.OAK_LOG, Blocks.OAK_LEAVES, false,
                SpaceColonizationParams.oak(), null));
        reg(new SpaceColonizationProfile(id("birch"), 800, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, false,
                SpaceColonizationParams.birch(), null));
        reg(new SpaceColonizationProfile(id("spruce_1x1"), 1400, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, false,
                SpaceColonizationParams.spruce(), null));
        reg(new SpaceColonizationProfile(id("cherry"), 1200, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, false,
                SpaceColonizationParams.cherry(), null));
        reg(new SpaceColonizationProfile(id("jungle_1x1"), 1400, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, false,
                SpaceColonizationParams.jungle(), null));
        reg(new SpaceColonizationProfile(id("acacia"), 1200, Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES, false,
                SpaceColonizationParams.acacia(), null));
        reg(new SpaceColonizationProfile(id("mangrove"), 1067, Blocks.MANGROVE_LOG, Blocks.MANGROVE_LEAVES, false,
                SpaceColonizationParams.mangrove(), SpaceColonizationProfile::placePropRoots));

        reg(new SpaceColonizationProfile(id("jungle"), 1600, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, true,
                SpaceColonizationParams.jungle(), null));
        reg(new SpaceColonizationProfile(id("dark_oak"), 1200, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, true,
                SpaceColonizationParams.darkOak(), null));
        reg(new SpaceColonizationProfile(id("spruce"), 2000, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, true,
                SpaceColonizationParams.spruce(), null));

        reg(new MushroomGrowthProfile(id("brown_mushroom"), 4, 7, 800,
                Blocks.BROWN_MUSHROOM_BLOCK, MushroomGrowthProfile.MushroomCapStyle.FLAT));
        reg(new MushroomGrowthProfile(id("red_mushroom"), 3, 7, 800,
                Blocks.RED_MUSHROOM_BLOCK, MushroomGrowthProfile.MushroomCapStyle.DOMED));

        // Mangrove propagule is a distinct block from mangrove sapling
        PROFILES.put(id("mangrove_propagule"), PROFILES.get(id("mangrove")));
    }

    private static void reg(TreeGrowthProfile profile) {
        ResourceLocation type = profile.treeType();
        PROFILES.put(type, profile);
        String path = type.getPath();
        if (!path.endsWith("_sapling")) {
            PROFILES.put(id(path + "_sapling"), profile);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
