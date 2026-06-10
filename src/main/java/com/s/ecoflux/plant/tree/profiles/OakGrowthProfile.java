package com.s.ecoflux.plant.tree.profiles;

import com.s.ecoflux.plant.tree.GrowthPlacement;
import com.s.ecoflux.plant.tree.TreeGrowthProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class OakGrowthProfile implements TreeGrowthProfile {
    public static final OakGrowthProfile INSTANCE = new OakGrowthProfile();
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("oak");

    private OakGrowthProfile() {
    }

    @Override
    public ResourceLocation treeType() {
        return TYPE;
    }

    @Override
    public int totalStages() {
        return 5;
    }

    @Override
    public int ticksPerStage() {
        return 40;
    }

    @Override
    public boolean canGrowStage(ServerLevel level, BlockPos saplingPos, int currentStage) {
        BlockPos topPos = saplingPos.above(2 + currentStage);
        BlockState state = level.getBlockState(topPos);
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    @Override
    public List<GrowthPlacement> growStage(ServerLevel level, BlockPos saplingPos, int currentStage) {
        List<GrowthPlacement> placed = new ArrayList<>();
        int newStage = currentStage + 1;

        // Base delay: trunk blocks animate bottom-up, each stage +3 ticks
        int baseDelay = (newStage - 1) * 3;
        BlockPos trunkPos = saplingPos.above(newStage);

        if (level.getBlockState(trunkPos).isAir() || level.getBlockState(trunkPos).is(BlockTags.LEAVES)) {
            level.setBlock(trunkPos, Blocks.OAK_LOG.defaultBlockState(), 3);
            placed.add(new GrowthPlacement(trunkPos.immutable(), GrowthPlacement.ANIM_TRUNK, baseDelay));
        }

        int radius = newStage >= 4 ? 2 : 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (radius > 1 && Math.abs(dx) == radius && Math.abs(dz) == radius
                        && level.random.nextFloat() > 0.6) continue;
                BlockPos leafPos = trunkPos.offset(dx, 0, dz);
                if (level.getBlockState(leafPos).isAir()) {
                    level.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(LeavesBlock.DISTANCE, 1)
                            .setValue(LeavesBlock.PERSISTENT, false), 3);
                    // Leaves animate outward from trunk: farther = later
                    int leafDelay = baseDelay + (Math.abs(dx) + Math.abs(dz)) * 2;
                    placed.add(new GrowthPlacement(leafPos.immutable(), GrowthPlacement.ANIM_LEAF_INFLATE, leafDelay));
                }
            }
        }

        if (newStage == totalStages() || newStage % 3 == 0) {
            BlockPos topPos = trunkPos.above();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos leafPos = topPos.offset(dx, 0, dz);
                    if (level.getBlockState(leafPos).isAir()) {
                        level.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState()
                                .setValue(LeavesBlock.DISTANCE, 1)
                                .setValue(LeavesBlock.PERSISTENT, false), 3);
                        int canopyDelay = baseDelay + (Math.abs(dx) + Math.abs(dz)) * 2;
                        placed.add(new GrowthPlacement(leafPos.immutable(), GrowthPlacement.ANIM_LEAF_CLUSTER, canopyDelay));
                    }
                }
            }
        }

        return placed;
    }
}
