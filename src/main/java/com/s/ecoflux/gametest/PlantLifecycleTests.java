package com.s.ecoflux.gametest;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.plant.VegetationLifecycleStage;
import com.s.ecoflux.plant.VegetationTracker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EcofluxConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlantLifecycleTests {

    /** Centre of the 5×5 grass plot (relative). */
    private static final BlockPos CENTER_REL = new BlockPos(2, 1, 2);

    /** Convert a relative position to absolute. */
    private static BlockPos abs(GameTestHelper helper, BlockPos rel) {
        return helper.absolutePos(rel);
    }

    @GameTest(
        template = EcofluxGameTestHelper.GRASS_PLOT,
        batch = EcofluxGameTestHelper.BATCH_PLANT,
        timeoutTicks = 200
    )
    public static void testTrackDandelion(GameTestHelper helper) {
        helper.setBlock(CENTER_REL, Blocks.DANDELION);

        BlockPos worldPos = abs(helper, CENTER_REL);
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);
        ServerLevel level = helper.getLevel();

        VegetationTracker.INSTANCE.trackAt(
                level, chunk, worldPos,
                Optional.empty(), Optional.empty());

        SuccessionChunkData data = EcofluxGameTestHelper.chunkData(helper);
        ActiveVegetationRecord record = data.getVegetationRecords().get(worldPos);
        if (record == null) {
            helper.fail("Dandelion was not tracked at " + CENTER_REL);
        }
        if (record.lifeStage() != VegetationLifecycleStage.BORN) {
            helper.fail("Expected BORN stage, got " + record.lifeStage());
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.GRASS_PLOT,
        batch = EcofluxGameTestHelper.BATCH_PLANT,
        timeoutTicks = 200
    )
    public static void testTrackMultipleFlowers(GameTestHelper helper) {
        BlockPos relA = new BlockPos(1, 1, 2);
        BlockPos relB = new BlockPos(3, 1, 2);

        helper.setBlock(relA, Blocks.POPPY);
        helper.setBlock(relB, Blocks.OXEYE_DAISY);

        BlockPos a = abs(helper, relA);
        BlockPos b = abs(helper, relB);
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);
        ServerLevel level = helper.getLevel();

        VegetationTracker.INSTANCE.trackAt(level, chunk, a, Optional.empty(), Optional.empty());
        VegetationTracker.INSTANCE.trackAt(level, chunk, b, Optional.empty(), Optional.empty());

        SuccessionChunkData data = EcofluxGameTestHelper.chunkData(helper);
        if (data.getVegetationRecords().get(a) == null) {
            helper.fail("Poppy was not tracked at " + relA);
        }
        if (data.getVegetationRecords().get(b) == null) {
            helper.fail("Oxeye daisy was not tracked at " + relB);
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.GRASS_PLOT,
        batch = EcofluxGameTestHelper.BATCH_PLANT,
        timeoutTicks = 200
    )
    public static void testUntrackedFlowerIsRejected(GameTestHelper helper) {
        helper.setBlock(CENTER_REL, Blocks.STONE);

        BlockPos worldPos = abs(helper, CENTER_REL);
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);
        ServerLevel level = helper.getLevel();

        VegetationTracker.INSTANCE.trackAt(
                level, chunk, worldPos,
                Optional.empty(), Optional.empty());

        ActiveVegetationRecord record = EcofluxGameTestHelper.chunkData(helper)
                .getVegetationRecords().get(worldPos);
        if (record != null) {
            helper.fail("Stone was unexpectedly tracked — adapter should have rejected it");
        }
        helper.succeed();
    }

    @GameTest(
        template = EcofluxGameTestHelper.GRASS_PLOT,
        batch = EcofluxGameTestHelper.BATCH_PLANT,
        timeoutTicks = 600
    )
    public static void testObserveChunkAdvancesStages(GameTestHelper helper) {
        helper.setBlock(CENTER_REL, Blocks.DANDELION);

        BlockPos worldPos = abs(helper, CENTER_REL);
        LevelChunk chunk = EcofluxGameTestHelper.testChunk(helper);
        ServerLevel level = helper.getLevel();

        VegetationTracker.INSTANCE.trackAt(
                level, chunk, worldPos,
                Optional.empty(), Optional.empty());

        helper.runAfterDelay(20, () -> {
            VegetationTracker.INSTANCE.observeChunk(level, chunk);

            ActiveVegetationRecord record = EcofluxGameTestHelper.chunkData(helper)
                    .getVegetationRecords().get(worldPos);
            if (record == null) {
                helper.fail("Dandelion record disappeared after observeChunk");
                return;
            }
            if (record.lastObservedGameTime() <= record.birthGameTime()) {
                helper.fail("Plant was not observed — lastObservedGameTime did not advance");
            }
            helper.succeed();
        });
    }

    @GameTest(
        template = EcofluxGameTestHelper.GRASS_PLOT,
        batch = EcofluxGameTestHelper.BATCH_PLANT,
        timeoutTicks = 200
    )
    public static void testSuccessionChunkDataDefaultState(GameTestHelper helper) {
        SuccessionChunkData data = EcofluxGameTestHelper.chunkData(helper);
        if (data.getVegetationRecords() == null) {
            helper.fail("vegetationRecords map should not be null");
        }
        if (!data.getVegetationRecords().isEmpty()) {
            helper.fail("Fresh chunk should have empty vegetation records, got "
                    + data.getVegetationRecords().size());
        }
        helper.succeed();
    }
}
