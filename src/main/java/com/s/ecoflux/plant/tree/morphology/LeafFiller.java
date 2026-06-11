package com.s.ecoflux.plant.tree.morphology;

/**
 * Skeleton-aware leaf placement algorithm that fills the canopy volume with leaves.
 *
 * <p>Structure: Static utility class. {@link #fillLeaves} traverses the AABB around
 * the tree, checks canopy density via {@link CanopyEnvelope.DensityFunction}, computes
 * Chebyshev distance to the nearest skeleton node, applies noise-based probability,
 * shuffles candidates deterministically, and places leaves with proper
 * {@link net.minecraft.world.level.block.LeavesBlock#DISTANCE} and
 * {@link net.minecraft.world.level.block.LeavesBlock#PERSISTENT} properties.
 * Capped per growth stage (scales with stage progress from ~12 to ~212 leaves).
 *
 * <p>Role in Ecoflux: The final stage of the morphology pipeline. Called by
 * {@link TreeMorphology#growStage} after log placement. Uses
 * {@link com.s.ecoflux.plant.tree.TreeShapeUtils#positionNoise} for deterministic
 * randomness.
 */

import com.s.ecoflux.plant.tree.TreeShapeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class LeafFiller {
    private static int maxLeavesForStage(double stageProgress) {
        return (int) (12 + stageProgress * stageProgress * 200);
    }

    private LeafFiller() {}

    public static void fillLeaves(
            ServerLevel level,
            TreeSkeleton skeleton,
            CanopyEnvelope.DensityFunction densityFn,
            Block leavesBlock,
            BlockPos trunkTop,
            double leafDensity,
            double branchClustering,
            double edgeFeather,
            int currentStage,
            int totalStages,
            long worldSeed,
            RandomSource random
    ) {
        double stageProgress = (double) currentStage / totalStages;
        double densityThreshold = 0.08 + stageProgress * 0.06;
        double effectiveDensity = leafDensity * (0.8 + stageProgress * 0.2);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        int[] bounds = computeStageBounds(skeleton, currentStage, totalStages);
        int worldMaxY = level.getMaxBuildHeight() - 1;
        int worldMinY = level.getMinBuildHeight();
        int minX = bounds[0], maxX = bounds[1];
        int minY = Math.max(bounds[2], worldMinY), maxY = Math.min(bounds[3], worldMaxY);
        int minZ = bounds[4], maxZ = bounds[5];

        record Candidate(BlockPos pos, double distToSkeleton) {}
        List<Candidate> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState existing = level.getBlockState(mutable);
                    if (!existing.isAir() && !existing.is(BlockTags.LEAVES)) continue;

                    double density = densityFn.density(x, y, z);
                    if (density <= densityThreshold) continue;

                    double distToSkeleton = minDistanceToSkeleton(skeleton, x, y, z);
                    double maxLeafDist = 4.0;
                    if (distToSkeleton > maxLeafDist) continue;

                    double distToBranch = minDistanceToBranch(skeleton, x, y, z);
                    double noise = TreeShapeUtils.positionNoise(worldSeed, x, y, z);
                    double branchBoost = distToBranch < 3.0 ? (1.0 + (3.0 - distToBranch) * 1.2) : 1.0;
                    double proximity = 1.0 / (1.0 + distToSkeleton * 0.5);

                    double prob = proximity * density * effectiveDensity * branchBoost * (0.6 + branchClustering * 0.4)
                            + noise * 0.12;

                    if (noise < prob) {
                        candidates.add(new Candidate(mutable.immutable(), distToSkeleton));
                    }
                }
            }
        }

        Collections.shuffle(candidates, new Random(worldSeed ^ (long) currentStage));

        int placed = 0;
        for (Candidate candidate : candidates) {
            if (placed >= maxLeavesForStage(stageProgress)) break;

            BlockPos pos = candidate.pos;
            double distToSkeleton = candidate.distToSkeleton;
            int distance = Math.max(1, (int) Math.ceil(distToSkeleton));
            if (distance > 5) continue;

            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !existing.is(BlockTags.LEAVES)) continue;

            level.setBlock(pos, leavesBlock.defaultBlockState()
                    .setValue(LeavesBlock.DISTANCE, Math.min(distance, 7))
                    .setValue(LeavesBlock.PERSISTENT, true), 3);
            placed++;
        }
    }

    private static int[] computeStageBounds(TreeSkeleton skeleton, int stage, int totalStages) {
        int stageTrunkProgress = Math.min(stage, skeleton.trunkHeight());
        int canopyStage = Math.max(0, stage - skeleton.trunkHeight());

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (SkeletonNode node : skeleton.nodes()) {
            if (node.type() == NodeType.TWIG) continue;
            if (node.depth() > stageTrunkProgress + canopyStage) continue;
            minX = Math.min(minX, node.pos().getX());
            maxX = Math.max(maxX, node.pos().getX());
            minY = Math.min(minY, node.pos().getY());
            maxY = Math.max(maxY, node.pos().getY());
            minZ = Math.min(minZ, node.pos().getZ());
            maxZ = Math.max(maxZ, node.pos().getZ());
        }

        if (minX == Integer.MAX_VALUE) {
            minX = skeleton.saplingPos().getX();
            maxX = minX;
            minY = skeleton.saplingPos().getY() + stage + 1;
            maxY = minY;
            minZ = skeleton.saplingPos().getZ();
            maxZ = minZ;
        }

        int padding = 4 + canopyStage * 2;
        minX -= padding;
        maxX += padding;
        minY -= 2;
        maxY += 3 + canopyStage;
        minZ -= padding;
        maxZ += padding;

        minY = Math.max(minY, skeleton.saplingPos().getY() + 3);

        return new int[]{minX, maxX, minY, maxY, minZ, maxZ};
    }

    public static double minDistanceToSkeleton(TreeSkeleton skeleton, int x, int y, int z) {
        double minDist = Double.MAX_VALUE;
        for (SkeletonNode node : skeleton.nodes()) {
            if (node.type() == NodeType.TWIG) continue;
            double dist = chebyshevDist(
                    x, y, z,
                    node.pos().getX(), node.pos().getY(), node.pos().getZ());
            if (dist < minDist) {
                minDist = dist;
                if (minDist <= 1.0) return minDist;
            }
        }
        return minDist == Double.MAX_VALUE ? 10.0 : minDist;
    }

    private static double minDistanceToBranch(TreeSkeleton skeleton, int x, int y, int z) {
        double minDist = Double.MAX_VALUE;
        for (SkeletonNode node : skeleton.nodes()) {
            if (node.type() != NodeType.PRIMARY_BRANCH
                    && node.type() != NodeType.SECONDARY_BRANCH) continue;
            double dist = chebyshevDist(
                    x, y, z,
                    node.pos().getX(), node.pos().getY(), node.pos().getZ());
            if (dist < minDist) {
                minDist = dist;
                if (minDist <= 1.0) return minDist;
            }
        }
        return minDist == Double.MAX_VALUE ? 10.0 : minDist;
    }

    private static double chebyshevDist(int x1, int y1, int z1, int x2, int y2, int z2) {
        return Math.max(Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2)), Math.abs(z1 - z2));
    }

    public static int countLeavesInBounds(ServerLevel level, int[] bounds) {
        int count = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds[0]; x <= bounds[1]; x++) {
            for (int y = bounds[2]; y <= bounds[3]; y++) {
                for (int z = bounds[4]; z <= bounds[5]; z++) {
                    mutable.set(x, y, z);
                    if (level.getBlockState(mutable).is(BlockTags.LEAVES)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
