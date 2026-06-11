package com.s.ecoflux.gametest;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.config.SuccessionConfigRegistry;
import com.s.ecoflux.config.SuccessionPathDefinition;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.succession.SuccessionService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Shared constants and utility methods for Ecoflux GameTest classes.
 *
 * <h3>Position conversion rule</h3>
 * {@link GameTestHelper#setBlock} auto-converts relative → absolute positions.
 * Ecoflux APIs ({@code VegetationTracker}, {@code SuccessionChunkData}) work with
 * <b>absolute</b> world positions. Always use {@link #abs(GameTestHelper, BlockPos)}
 * when passing positions to Ecoflux methods.
 *
 * <p>Every public test class should use the structure and batch constants defined here.
 */
public final class EcofluxGameTestHelper {

    /** 3×3×3 glass-floor structure. Use for tests that only need a tiny space. */
    public static final String DEFAULT_STRUCTURE = "default_3x3";

    /** 5×5×3 structure with grass floor. Use for plant lifecycle tests. */
    public static final String GRASS_PLOT = "grass_plot";

    /** 8×8×5 structure with grass floor. Use for succession pipeline tests. */
    public static final String SUCCESSION_ARENA = "succession_arena";

    // ---- batch names ----

    public static final String BATCH_CONFIG = "ecoflux_config";
    public static final String BATCH_PLANT = "ecoflux_plant";
    public static final String BATCH_SUCCESSION = "ecoflux_succession";
    public static final String BATCH_TREE = "ecoflux_tree";

    private EcofluxGameTestHelper() {}

    /** Convert a relative structure position to an absolute world position. */
    public static BlockPos abs(GameTestHelper helper, BlockPos rel) {
        return helper.absolutePos(rel);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EcofluxConstants.MOD_ID, path);
    }

    /** Get the ServerLevel from a test helper. */
    public static ServerLevel level(GameTestHelper helper) {
        return helper.getLevel();
    }

    /** Get the chunk containing the structure origin. */
    public static LevelChunk testChunk(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getChunkAt(origin);
    }

    /** Get (or lazily create) the SuccessionChunkData for the test chunk. */
    public static SuccessionChunkData chunkData(GameTestHelper helper) {
        return testChunk(helper).getData(ModAttachments.SUCCESSION_CHUNK_DATA);
    }

    /** Initialize the test chunk's succession data with a specific path. */
    public static void initSuccession(GameTestHelper helper, ResourceLocation pathId) {
        LevelChunk chunk = testChunk(helper);
        SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        data.clearRuntimeState();

        Optional<SuccessionPathDefinition> path = SuccessionConfigRegistry.getPath(pathId);
        if (path.isEmpty()) {
            helper.fail("Succession path not found: " + pathId);
            return;
        }
        SuccessionService.initializeChunk(chunk);
    }

    /** Verify that the config registry has loaded at least one path. */
    public static void assertConfigsLoaded(GameTestHelper helper) {
        boolean hasPaths = SuccessionConfigRegistry.getPath(
                ResourceLocation.fromNamespaceAndPath(EcofluxConstants.MOD_ID, "plains_to_forest"))
                .isPresent();
        if (!hasPaths) {
            helper.fail("Succession config registry is empty — paths may not have loaded");
        }
    }
}
