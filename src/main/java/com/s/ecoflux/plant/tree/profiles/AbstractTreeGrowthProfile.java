package com.s.ecoflux.plant.tree.profiles;

/**
 * Base class implementing common {@link com.s.ecoflux.plant.tree.TreeGrowthProfile} behavior.
 *
 * <p>Structure: provides shared static utility methods for trunk growth validation
 * ({@link #singleTrunkCanGrowStage}) and mushroom stem placement
 * ({@link #placeMushroomStem}) used by all species profiles.
 * <p>Role in Ecoflux: reduces duplication across the 12 concrete growth profiles;
 * profiles override only species-specific parameters and behavior while inheriting
 * common block-replacement logic.
 */

import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractTreeGrowthProfile implements TreeGrowthProfile {

    protected static boolean singleTrunkCanGrowStage(ServerLevel level, BlockPos saplingPos,
                                                      int currentStage, int totalStages, int resolvedHeight) {
        BlockPos checkPos = saplingPos.above(currentStage + 2);
        if (checkPos.getY() >= level.getMaxBuildHeight()) return false;
        BlockState state = level.getBlockState(checkPos);
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    protected static void placeMushroomStem(ServerLevel level, BlockPos saplingPos, int yAbove) {
        BlockPos stemPos = saplingPos.above(yAbove);
        BlockState existing = level.getBlockState(stemPos);
        if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)
                || existing.is(Blocks.BROWN_MUSHROOM) || existing.is(Blocks.RED_MUSHROOM)
                || existing.is(Blocks.BROWN_MUSHROOM_BLOCK) || existing.is(Blocks.RED_MUSHROOM_BLOCK)
                || existing.is(Blocks.MUSHROOM_STEM)) {
            level.setBlock(stemPos, Blocks.MUSHROOM_STEM.defaultBlockState(), 3);
        }
    }
}
