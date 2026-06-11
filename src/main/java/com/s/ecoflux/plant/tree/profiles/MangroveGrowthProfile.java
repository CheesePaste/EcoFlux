package com.s.ecoflux.plant.tree.profiles;

/**
 * Mangrove tree growth profile (singleton).
 *
 * <p>Structure: rounded ellipsoid canopy, 6-10 block height (shortest),
 * 3200 ticks/stage, 3-6 branches. Includes unique prop roots at the base,
 * placed during early growth stages via {@link #growStage} override.
 * Delegates to the morphology system via {@link MorphologyPresets#mangrove()}.
 * <p>Role in Ecoflux: defines the parametric growth parameters for mangrove trees
 * in the ecological succession tree lifecycle system.
 */

import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MangroveGrowthProfile extends AbstractTreeGrowthProfile {
    public static final MangroveGrowthProfile INSTANCE = new MangroveGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("mangrove");

    private MangroveGrowthProfile() {}

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 3200; }
    @Override public Block logBlock() { return Blocks.MANGROVE_LOG; }
    @Override public Block leavesBlock() { return Blocks.MANGROVE_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyPresets.mangrove();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        return singleTrunkCanGrowStage(level, saplingPos, currentStage, totalStages, resolvedHeight);
    }

    @Override
    public void growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        // Place prop roots at the base during early stages
        if (currentStage < 2 && resolvedHeight >= 6) {
            placePropRoots(level, saplingPos, random);
        }
    }

    private void placePropRoots(ServerLevel level, BlockPos basePos, RandomSource random) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (random.nextFloat() > 0.6f) continue;
            BlockPos rootPos = basePos.relative(dir);
            if (rootPos.getY() <= level.getMinBuildHeight() + 1) continue;
            BlockState existing = level.getBlockState(rootPos);
            if (existing.isAir() || existing.is(BlockTags.REPLACEABLE) || existing.is(BlockTags.LEAVES)) {
                TreeShapeUtils.tryPlaceLog(level, rootPos, logBlock(), dir.getAxis());
            }
            // Extend root downward
            BlockPos downPos = rootPos.below();
            if (random.nextFloat() > 0.7f) continue;
            BlockState downExisting = level.getBlockState(downPos);
            if (downExisting.isAir() || downExisting.is(BlockTags.REPLACEABLE) || downExisting.is(BlockTags.LEAVES)) {
                TreeShapeUtils.tryPlaceLog(level, downPos, logBlock(), Direction.Axis.Y);
            }
        }
    }
}
