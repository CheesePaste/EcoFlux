package com.s.ecoflux.plant.tree.morphology;

import com.s.ecoflux.plant.tree.TreeShapeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
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
                int end = (c == canopyStages - 1) ? allRemaining.size() : (c + 1) * perStage;
                stageGroups.add(new ArrayList<>(allRemaining.subList(start, Math.min(end, allRemaining.size()))));
            }
        }

        return new GrowStagePlan(totalStages, stageGroups);
    }

    public static int growStage(
            ServerLevel level,
            TreeSkeleton skeleton,
            MorphologyParams params,
            GrowStagePlan plan,
            int currentStage,
            long worldSeed,
            RandomSource random,
            Block logBlock,
            Block leavesBlock
    ) {
        if (currentStage >= plan.totalStages()) return 0;

        List<Integer> stageNodeIndices = plan.stageGroups().get(currentStage);
        int worldMaxY = level.getMaxBuildHeight() - 1;

        int placed = 0;

        for (int idx : stageNodeIndices) {
            SkeletonNode node = skeleton.getNode(idx);
            if (node.type() == NodeType.TWIG) continue;

            BlockPos pos = node.pos();
            if (pos.getY() > worldMaxY) continue;
            if (TreeShapeUtils.tryPlaceLog(level, pos, logBlock)) {
                placed++;
            }
        }

        BlockPos saplingPos = skeleton.saplingPos();
        int trunkBaseY = saplingPos.getY();
        int finalTrunkY = trunkBaseY + skeleton.trunkHeight();
        int currentTrunkY = trunkBaseY + Math.min(currentStage + 1, skeleton.trunkHeight());
        double stageProgress = (double) currentStage / Math.max(1, plan.totalStages());

        int trunkCenterX = saplingPos.getX();
        int trunkCenterZ = saplingPos.getZ();
        if (params.is2x2()) {
            trunkCenterX = saplingPos.getX();
            trunkCenterZ = saplingPos.getZ();
        }

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
        CanopyEnvelope.CanopyConfig canopyConfig = new CanopyEnvelope.CanopyConfig(
                params.canopyType(),
                trunkCenterX,
                trunkCenterZ,
                trunkBaseY,
                currentTrunkY,
                finalTrunkY,
                skeleton.trunkHeight(),
                params.canopyRadiusXZ(),
                params.canopyRadiusY(),
                params.canopyCenterYBias(),
                params.edgeFeather(),
                params.subClusters(),
                params.subClusterRadius(),
                branchNodes,
                branchNodeRadii,
                stageProgress
        );

        CanopyEnvelope.DensityFunction densityFn = CanopyEnvelope.createDensityFunction(canopyConfig);

        int leavesPlaced = LeafFiller.fillLeaves(
                level, skeleton, densityFn, leavesBlock, saplingPos.above(currentTrunkY - trunkBaseY),
                params.leafDensity(), params.branchClustering(), params.edgeFeather(),
                currentStage, plan.totalStages(), worldSeed, random
        );

        return placed + leavesPlaced;
    }

    public record GrowStagePlan(int totalStages, List<List<Integer>> stageGroups) {}
}
