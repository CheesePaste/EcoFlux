package com.s.ecoflux.gametest;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EcofluxConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigValidationTests {

    // ---- known path IDs -------------------------------------------------------

    private static final ResourceLocation PLAINS_TO_FOREST = id("plains_to_forest");
    private static final ResourceLocation PLAINS_TO_MEADOW = id("plains_to_meadow");
    private static final ResourceLocation FOREST_TO_BIRCH = id("forest_to_birch_forest");
    private static final ResourceLocation FOREST_TO_DARK = id("forest_to_dark_forest");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EcofluxConstants.MOD_ID, path);
    }

    // ---- tests ---------------------------------------------------------------

    @GameTest(
        template = EcofluxGameTestHelper.DEFAULT_STRUCTURE,
        batch = EcofluxGameTestHelper.BATCH_CONFIG,
        timeoutTicks = 20
    )
    public static void testRegistryIsPopulated(GameTestHelper helper) {
        EcofluxGameTestHelper.assertConfigsLoaded(helper);

        List<SuccessionPathDefinition> all = SuccessionConfigRegistry.getAllPaths();
        if (all.isEmpty()) {
            helper.fail("No succession paths registered");
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.DEFAULT_STRUCTURE,
        batch = EcofluxGameTestHelper.BATCH_CONFIG,
        timeoutTicks = 20
    )
    public static void testFindPlainsToForest(GameTestHelper helper) {
        Optional<SuccessionPathDefinition> path = SuccessionConfigRegistry.getPath(PLAINS_TO_FOREST);
        if (path.isEmpty()) {
            helper.fail(PLAINS_TO_FOREST + " not found in registry");
        }
        SuccessionPathDefinition def = path.get();
        if (!def.pathId().equals(PLAINS_TO_FOREST)) {
            helper.fail("Path ID mismatch: " + def.pathId());
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.DEFAULT_STRUCTURE,
        batch = EcofluxGameTestHelper.BATCH_CONFIG,
        timeoutTicks = 20
    )
    public static void testFindBestMatchForPlains(GameTestHelper helper) {
        Optional<SuccessionPathDefinition> match = SuccessionConfigRegistry.findBestMatch(
                Biomes.PLAINS, 0.8F, 0.4F);
        if (match.isEmpty()) {
            helper.fail("No match found for plains biome (temp=0.8, downfall=0.4)");
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.DEFAULT_STRUCTURE,
        batch = EcofluxGameTestHelper.BATCH_CONFIG,
        timeoutTicks = 20
    )
    public static void testAllPathIdsHaveTargetBiome(GameTestHelper helper) {
        for (SuccessionPathDefinition def : SuccessionConfigRegistry.getAllPaths()) {
            if (def.targetBiome() == null) {
                helper.fail("Path " + def.pathId() + " has null target biome");
            }
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.DEFAULT_STRUCTURE,
        batch = EcofluxGameTestHelper.BATCH_CONFIG,
        timeoutTicks = 20
    )
    public static void testNoDuplicatePathIds(GameTestHelper helper) {
        List<SuccessionPathDefinition> all = SuccessionConfigRegistry.getAllPaths();
        long distinct = all.stream().map(SuccessionPathDefinition::pathId).distinct().count();
        if (distinct != all.size()) {
            helper.fail("Duplicate path IDs detected: " + all.size() + " paths but only " + distinct + " distinct IDs");
        }
        helper.succeed();
    }
}
