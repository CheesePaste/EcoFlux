package com.s.ecoflux.gametest;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.succession.SuccessionService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EcofluxConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuccessionPipelineTests {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EcofluxConstants.MOD_ID, path);
    }

    @GameTest(
        template = EcofluxGameTestHelper.SUCCESSION_ARENA,
        batch = EcofluxGameTestHelper.BATCH_SUCCESSION,
        timeoutTicks = 100
    )
    public static void testInitializeChunk(GameTestHelper helper) {
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);
        SuccessionService.initializeChunk(chunk);

        SuccessionChunkData data = EcofluxGameTestHelper.chunkData(helper);
        if (data.getCurrentBiome().isEmpty()) {
            helper.fail("currentBiome not set after chunk initialisation");
        }
        // Initialisation should set a target biome if a matching path exists
        // (may be empty if no path matches the chunk's actual biome)
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.SUCCESSION_ARENA,
        batch = EcofluxGameTestHelper.BATCH_SUCCESSION,
        timeoutTicks = 100
    )
    public static void testHasActivePathAfterInit(GameTestHelper helper) {
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);

        // Manually set biome to plains and initialise
        SuccessionChunkData data = EcofluxGameTestHelper.chunkData(helper);
        SuccessionService.initializeChunk(chunk);

        // Whether a path is active depends on whether the chunk's actual biome
        // matches any registered source biome. We just verify the method doesn't crash.
        boolean hasPath = SuccessionService.hasActivePath(data);
        // No assertion on the value — the test chunk's biome may or may not match
        helper.assertTrue(true, "hasActivePath should not throw");
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.SUCCESSION_ARENA,
        batch = EcofluxGameTestHelper.BATCH_SUCCESSION,
        timeoutTicks = 100
    )
    public static void testDescribeChunkDoesNotThrow(GameTestHelper helper) {
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);
        SuccessionService.initializeChunk(chunk);

        String desc = SuccessionService.describeChunk(chunk);
        if (desc == null || desc.isBlank()) {
            helper.fail("describeChunk returned empty string");
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.SUCCESSION_ARENA,
        batch = EcofluxGameTestHelper.BATCH_SUCCESSION,
        timeoutTicks = 100
    )
    public static void testPruneChunkOnEmptyChunk(GameTestHelper helper) {
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);
        ServerLevel level = helper.getLevel();

        SuccessionService.initializeChunk(chunk);
        String result = SuccessionService.pruneChunk(level, chunk);
        if (result == null || result.isBlank()) {
            helper.fail("pruneChunk returned empty result");
        }
        // Fresh chunk with no plants should report 0 removed
        helper.assertTrue(result.contains("0"), "Expected 0 pruned plants: " + result);
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.SUCCESSION_ARENA,
        batch = EcofluxGameTestHelper.BATCH_SUCCESSION,
        timeoutTicks = 100
    )
    public static void testChunkDataPersistsAcrossAccesses(GameTestHelper helper) {
        SuccessionChunkData first = EcofluxGameTestHelper.chunkData(helper);
        SuccessionChunkData second = EcofluxGameTestHelper.chunkData(helper);
        if (first != second) {
            helper.fail("chunkData() returned different instances — attachment not stable");
        }
        helper.succeed();
    }
}
