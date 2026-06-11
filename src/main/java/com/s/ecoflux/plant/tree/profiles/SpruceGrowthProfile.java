package com.s.ecoflux.plant.tree.profiles;

/**
 * Spruce tree growth profile (singleton).
 *
 * <p>Structure: full-height conical foliage, 13-22 block trunk height (tallest),
 * 4800 ticks/stage (~48 real minutes, slowest growing), strictly vertical trunk,
 * 12-20 horizontal branches, 3-block clear trunk. Densely branched.
 * Delegates to the morphology system via {@link MorphologyPresets#spruce()}.
 * <p>Role in Ecoflux: defines the parametric growth parameters for spruce trees
 * in the ecological succession tree lifecycle system.
 */

import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class SpruceGrowthProfile extends AbstractTreeGrowthProfile {
    public static final SpruceGrowthProfile INSTANCE = new SpruceGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("spruce");

    private SpruceGrowthProfile() {}

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int ticksPerStage() { return 4800; }
    @Override public Block logBlock() { return Blocks.SPRUCE_LOG; }
    @Override public Block leavesBlock() { return Blocks.SPRUCE_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyPresets.spruce();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        return singleTrunkCanGrowStage(level, saplingPos, currentStage, totalStages, resolvedHeight);
    }
}
