package com.s.ecoflux.plant.tree.profiles;

/**
 * Acacia tree growth profile (singleton).
 *
 * <p>Structure: flat disc + scattered sphere cluster canopy, 8-14 block height,
 * 3600 ticks/stage (~27 real minutes), 15-18 degree trunk lean (most angled),
 * 4-7 branches, 4-block clear trunk. Distinctive leaning shape.
 * Overrides {@link #canGrowStage} to check a 3x3 neighborhood for the leaning trunk.
 * Delegates to the morphology system via {@link MorphologyPresets#acacia()}.
 * <p>Role in Ecoflux: defines the parametric growth parameters for acacia trees
 * in the ecological succession tree lifecycle system.
 */

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class AcaciaGrowthProfile extends AbstractTreeGrowthProfile {
    public static final AcaciaGrowthProfile INSTANCE = new AcaciaGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("acacia");

    private AcaciaGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 3600; }
    @Override public Block logBlock() { return Blocks.ACACIA_LOG; }
    @Override public Block leavesBlock() { return Blocks.ACACIA_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyPresets.acacia();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        int checkY = saplingPos.getY() + currentStage + 2;
        if (checkY >= level.getMaxBuildHeight()) return false;
        BlockPos checkCenter = saplingPos.atY(checkY);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos cp = checkCenter.offset(dx, 0, dz);
                BlockState s = level.getBlockState(cp);
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) return true;
            }
        }
        return false;
    }
}
