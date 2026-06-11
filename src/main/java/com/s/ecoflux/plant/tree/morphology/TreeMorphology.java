package com.s.ecoflux.plant.tree.morphology;

/**
 * Integration entry point for the morphology-based tree growth pipeline.
 *
 * <p>Structure: Three-stage static pipeline --
 * {@link #generateSkeleton} (delegates to {@link SkeletonGenerator#generate}),
 * {@link #planStages} (partitions nodes into per-stage groups: trunk levels first,
 * then branch and canopy nodes distributed across canopy stages),
 * {@link #growStage} (places log blocks for the current stage's nodes, then fills
 * leaves via {@link CanopyEnvelope} and {@link LeafFiller}).
 * The inner record {@link GrowStagePlan} maps stage indices to node-index groups.
 *
 * <p>Role in Ecoflux: Called by {@link com.s.ecoflux.plant.tree.TreeGrowthHandler#tickAll}
 * and {@link com.s.ecoflux.plant.tree.TreeGrowthHandler#forceAdvanceStage} for profiles
 * returning non-null {@link MorphologyParams}. Replaces Minecraft's instant tree growth
 * with progressive staged growth (trunk rises, branches extend, canopy fills in).
 */

import com.s.ecoflux.plant.tree.TreeShapeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

public final class TreeMorphology {

    private TreeMorphology() {}

    public static TreeSkeleton generateSkeleton(
            BlockPos saplingPos,
            MorphologyParams params,
            int resolvedHeight,
            RandomSource random
    ) {
        return SkeletonGenerator.generate(
                saplingPos,
                resolvedHeight,
                params.is2x2(),
                params.maxLeanAngle(),
                params.minBranches(),
                params.maxBranches(),
                params.branchStartRatio(),
                params.branchEndRatio(),
                params.branchAngleMin(),
                params.branchAngleMax(),
                params.branchLengthRatio(),
                params.hasSecondary(),
                params.secondaryProb(),
                params.secondaryMaxLength(),
                random
        );
    }

    public static GrowStagePlan planStages(TreeSkeleton skeleton, MorphologyParams params) {
        int trunkHeight = skeleton.trunkHeight();
        int canopyStages = params.canopyStages();
        int totalStages = trunkHeight + canopyStages;

        Map<Integer, List<Integer>> trunkNodesByY = new HashMap<>();
        for (int idx : skeleton.trunkPath()) {
            SkeletonNode node = skeleton.getNode(idx);
            int stage = node.depth();
            trunkNodesByY.computeIfAbsent(stage, k -> new ArrayList<>()).add(idx);
        }

        for (int stage = 0; stage < trunkHeight; stage++) {
            List<Integer> stageNodes = trunkNodesByY.getOrDefault(stage, Collections.emptyList());
            for (SkeletonNode node : skeleton.nodes()) {
                if (node.parentIndex() >= 0) {
                    SkeletonNode parent = skeleton.getNode(node.parentIndex());
                    if (parent.depth() == stage && node.type() == NodeType.PRIMARY_BRANCH) {
                        stageNodes = new ArrayList<>(stageNodes);
                        stageNodes.add(skeleton.nodes().indexOf(node));
                        trunkNodesByY.put(stage, stageNodes);
                    }
                }
            }
        }

        List<List<Integer>> stageGroups = new ArrayList<>();
        for (int stage = 0; stage < trunkHeight; stage++) {
            stageGroups.add(trunkNodesByY.getOrDefault(stage, Collections.emptyList()));
        }

        List<Integer> allRemaining = new ArrayList<>();
        for (int i = 0; i < skeleton.nodeCount(); i++) {
            SkeletonNode node = skeleton.getNode(i);
            if (node.depth() >= trunkHeight
                    || (node.type() != NodeType.TRUNK && node.depth() >= trunkHeight / 2)) {
                allRemaining.add(i);
            }
        }

        if (canopyStages > 0) {
            int perStage = Math.max(1, allRemaining.size() / canopyStages);
            for (int c = 0; c < canopyStages; c++) {
                int start = c * perStage;
                if (start >= allRemaining.size()) {
                    stageGroups.add(Collections.emptyList());
                } else {
                    int end = (c == canopyStages - 1) ? allRemaining.size() : (c + 1) * perStage;
                    stageGroups.add(new ArrayList<>(allRemaining.subList(start, Math.min(end, allRemaining.size()))));
                }
            }
        }

        return new GrowStagePlan(totalStages, stageGroups);
    }

    public static void growStage(
            ServerLevel level,
            TreeSkeleton skeleton,
            MorphologyParams params,
            GrowStagePlan plan,
            int currentStage,
            long worldSeed,
            RandomSource random,
            Block logBlock,
            Block leavesBlock,
            Set<BlockPos> outLogs,
            Set<BlockPos> outLeaves
    ) {
        if (currentStage >= plan.totalStages()) return;

        List<Integer> stageNodeIndices = plan.stageGroups().get(currentStage);
        int worldMaxY = level.getMaxBuildHeight() - 1;

        for (int idx : stageNodeIndices) {
            SkeletonNode node = skeleton.getNode(idx);
            if (node.type() == NodeType.TWIG) continue;

            BlockPos pos = node.pos();
            if (pos.getY() > worldMaxY) continue;
            Direction.Axis axis = resolveAxis(skeleton, idx);
            if (TreeShapeUtils.tryPlaceLog(level, pos, logBlock, axis)) {
                outLogs.add(pos.immutable());
            }
        }

        BlockPos saplingPos = skeleton.saplingPos();
        int trunkBaseY = saplingPos.getY();
        int currentTrunkY = trunkBaseY + Math.min(currentStage + 1, skeleton.trunkHeight());
        int finalTrunkY = trunkBaseY + skeleton.trunkHeight();

        int trunkCenterX = saplingPos.getX();
        int trunkCenterZ = saplingPos.getZ();

        int maxNodeDepth = currentStage + 3;
        double baseSr = params.subClusterRadius() > 0 ? params.subClusterRadius() : 3.5;
        List<BlockPos> branchNodes = new ArrayList<>();
        List<Double> branchNodeRadii = new ArrayList<>();
        for (int i = 0; i < skeleton.nodeCount(); i++) {
            SkeletonNode node = skeleton.getNode(i);
            if ((node.type() == NodeType.PRIMARY_BRANCH
                    || node.type() == NodeType.SECONDARY_BRANCH)
                    && node.depth() <= maxNodeDepth) {
                double growthRatio = Math.min(1.0, Math.max(0.0, (maxNodeDepth - node.depth()) / 3.0));
                double effectiveRadius = baseSr * growthRatio;
                if (effectiveRadius > 0.1) {
                    branchNodes.add(node.pos());
                    branchNodeRadii.add(effectiveRadius);
                }
            }
        }
        double stageProgress = (double) currentStage / Math.max(1, plan.totalStages());
        CanopyEnvelope.CanopyConfig canopyConfig = CanopyEnvelope.CanopyConfig.fromMorphology(
                params,
                trunkCenterX, trunkCenterZ,
                trunkBaseY, currentTrunkY, finalTrunkY, skeleton.trunkHeight(),
                branchNodes, branchNodeRadii,
                stageProgress
        );

        CanopyEnvelope.DensityFunction densityFn = CanopyEnvelope.createDensityFunction(canopyConfig);

        LeafFiller.fillLeaves(
                level, skeleton, densityFn, leavesBlock, saplingPos.above(currentTrunkY - trunkBaseY),
                params.leafDensity(), params.branchClustering(), params.edgeFeather(),
                currentStage, plan.totalStages(), worldSeed, random, outLeaves
        );
    }

    private static Direction.Axis resolveAxis(TreeSkeleton skeleton, int nodeIdx) {
        SkeletonNode node = skeleton.getNode(nodeIdx);
        if (node.type() == NodeType.TRUNK) {
            return Direction.Axis.Y;
        }
        if (node.parentIndex() < 0) {
            return Direction.Axis.Y;
        }
        SkeletonNode parent = skeleton.getNode(node.parentIndex());
        int dx = Math.abs(node.pos().getX() - parent.pos().getX());
        int dz = Math.abs(node.pos().getZ() - parent.pos().getZ());
        if (dx >= dz) {
            return Direction.Axis.X;
        }
        return Direction.Axis.Z;
    }

    public record GrowStagePlan(int totalStages, List<List<Integer>> stageGroups) {}
}
