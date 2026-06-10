package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.GrowthPlacement;
import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import com.s.ecoflux.plant.tree.TreeShapeUtils;
import com.s.ecoflux.plant.tree.morphology.MorphologyParams;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SpruceGrowthProfile implements TreeGrowthProfile {
    public static final SpruceGrowthProfile INSTANCE = new SpruceGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("spruce");
    private static final int CLEAR_TRUNK = 2;

    private SpruceGrowthProfile() {
    }

    @Override public ResourceLocation treeType() { return TYPE; }
    @Override public int minTrunkHeight() { return 8; }
    @Override public int maxTrunkHeight() { return 15; }
    @Override public int ticksPerStage() { return 4800; }
    @Override public Block logBlock() { return Blocks.SPRUCE_LOG; }
    @Override public Block leavesBlock() { return Blocks.SPRUCE_LEAVES; }
    @Override public boolean is2x2() { return false; }

    @Override
    public MorphologyParams morphologyParams() {
        return MorphologyParams.spruce();
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                                int totalStages, int resolvedHeight) {
        BlockPos checkPos = saplingPos.above(currentStage + 2);
        if (checkPos.getY() >= level.getMaxBuildHeight()) return false;
        BlockState state = level.getBlockState(checkPos);
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    @Override
    public List<GrowthPlacement> growStage(ServerLevel level, BlockPos saplingPos, int currentStage,
                          int totalStages, int resolvedHeight, RandomSource random) {
        int canopyStart = resolvedHeight;
        if (currentStage < canopyStart) {
            placeTrunkStage(level, saplingPos, currentStage, resolvedHeight, random);
        } else {
            placeCanopyTip(level, saplingPos, resolvedHeight, random);
        }
        return List.of();
    }

    private void placeTrunkStage(ServerLevel level, BlockPos saplingPos, int stage,
                                 int resolvedHeight, RandomSource random) {
        BlockPos trunkPos = saplingPos.above(stage + 1);
        TreeShapeUtils.tryPlaceLog(level, trunkPos, logBlock());

        int yFromGround = stage + 1;
        if (yFromGround > CLEAR_TRUNK) {
            double radius = TreeShapeUtils.spruceCanopyRadius(yFromGround, resolvedHeight, CLEAR_TRUNK);
            if (radius >= 0.5) {
                TreeShapeUtils.placeLeafDisc(level, trunkPos, radius, leavesBlock(),
                        trunkPos, 0.12, random);
            }
        }
    }

    private void placeCanopyTip(ServerLevel level, BlockPos saplingPos, int resolvedHeight,
                                RandomSource random) {
        BlockPos tipPos = saplingPos.above(resolvedHeight + 1);
        if (level.getBlockState(tipPos).isAir()) {
            TreeShapeUtils.tryPlaceLog(level, tipPos, logBlock());
        }
        TreeShapeUtils.placeLeafDisc(level, tipPos, 1.2, leavesBlock(), tipPos, 0.10, random);
        BlockPos topLeaf = tipPos.above();
        TreeShapeUtils.placeLeaf(level, topLeaf, leavesBlock(), 1);
        if (random.nextDouble() < 0.5) {
            TreeShapeUtils.placeLeaf(level, topLeaf.above(), leavesBlock(), 1);
        }
    }
}
