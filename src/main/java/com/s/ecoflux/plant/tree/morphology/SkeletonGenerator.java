package com.s.ecoflux.plant.tree.morphology;

import com.s.ecoflux.plant.tree.TreeShapeUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public final class SkeletonGenerator {

    private SkeletonGenerator() {}

    public static TreeSkeleton generate(
            BlockPos saplingPos,
            int resolvedHeight,
            boolean is2x2,
            float maxLeanAngle,
            int minBranches,
            int maxBranches,
            float branchStartRatio,
            float branchEndRatio,
            float branchAngleMin,
            float branchAngleMax,
            float branchLengthRatio,
            boolean hasSecondary,
            float secondaryProb,
            int secondaryMaxLength,
            RandomSource random
    ) {
        TreeSkeleton skeleton = new TreeSkeleton();
        skeleton.setSaplingPos(saplingPos);
        if (is2x2) {
            skeleton.setTrunkLevels(resolvedHeight);
        }

        generateTrunk(skeleton, saplingPos, resolvedHeight, is2x2, maxLeanAngle, random);
        if (maxBranches > 0) {
            generatePrimaryBranches(skeleton, resolvedHeight, minBranches, maxBranches,
                    branchStartRatio, branchEndRatio, branchAngleMin, branchAngleMax,
                    branchLengthRatio, is2x2, random);
        }
        if (hasSecondary) {
            generateSecondaryBranches(skeleton, secondaryProb, secondaryMaxLength, random);
        }

        return skeleton;
    }

    private static void generateTrunk(TreeSkeleton skeleton, BlockPos saplingPos,
                                       int height, boolean is2x2, float maxLeanAngle,
                                       RandomSource random) {
        double leanAngleRad = Math.toRadians(maxLeanAngle);
        double leanAngle = maxLeanAngle > 0
                ? (random.nextDouble() - 0.5) * 2.0 * leanAngleRad
                : 0;
        double leanDir = random.nextDouble() * 2.0 * Math.PI;

        BlockPos currentPos = saplingPos;
        for (int y = 0; y < height; y++) {
            int dx = 0;
            int dz = 0;
            if (y > 0 && maxLeanAngle > 0) {
                double noise = TreeShapeUtils.positionNoise(
                        random.nextLong(), saplingPos.getX(), y, saplingPos.getZ());
                double angle = leanDir + (noise - 0.5) * leanAngleRad * 0.5;
                double stepLen = Math.tan(leanAngle) * y * (0.8 + noise * 0.4);
                dx = (int) Math.round(Math.cos(angle) * stepLen);
                dz = (int) Math.round(Math.sin(angle) * stepLen);
            }

            BlockPos nodePos = saplingPos.offset(dx, y + 1, dz);

            if (is2x2) {
                int baseNodeCount = skeleton.nodeCount();
                for (int tx = 0; tx < 2; tx++) {
                    for (int tz = 0; tz < 2; tz++) {
                        BlockPos pos2x2 = new BlockPos(
                                saplingPos.getX() + tx + dx,
                                saplingPos.getY() + y + 1,
                                saplingPos.getZ() + tz + dz);
                        int parentIdx = y == 0 ? -1 : baseNodeCount - 4 + (tx * 2 + tz);
                        int idx = skeleton.addNode(new SkeletonNode(
                                pos2x2, NodeType.TRUNK, 0.5f, parentIdx, y));
                        if (y == 0 && tx == 0 && tz == 0) {
                            skeleton.setRootIndex(idx);
                        }
                        skeleton.addTrunkNode(idx);
                    }
                }
            } else {
                float radius = 0.5f + 0.15f * (1.0f - (float) y / height);
                int parentIndex = skeleton.trunkPath().isEmpty()
                        ? -1 : skeleton.trunkPath().get(skeleton.trunkPath().size() - 1);
                int idx = skeleton.addNode(new SkeletonNode(
                        nodePos, NodeType.TRUNK, radius, parentIndex, y));
                if (y == 0) {
                    skeleton.setRootIndex(idx);
                }
                skeleton.addTrunkNode(idx);
            }
            currentPos = nodePos;
        }
    }

    private static void generatePrimaryBranches(TreeSkeleton skeleton, int height,
                                                 int minBranches, int maxBranches,
                                                 float branchStartRatio, float branchEndRatio,
                                                 float branchAngleMin, float branchAngleMax,
                                                 float branchLengthRatio, boolean is2x2,
                                                 RandomSource random) {
        int branchCount = minBranches + random.nextInt(maxBranches - minBranches + 1);
        List<Integer> trunkNodes = skeleton.trunkPath();

        int startIdx = (int) (trunkNodes.size() * branchStartRatio);
        int endIdx = (int) (trunkNodes.size() * branchEndRatio);
        if (endIdx <= startIdx) endIdx = Math.min(startIdx + 1, trunkNodes.size());

        double angleMinRad = Math.toRadians(branchAngleMin);
        double angleMaxRad = Math.toRadians(branchAngleMax);

        for (int i = 0; i < branchCount; i++) {
            int trunkPickIdx = startIdx + random.nextInt(endIdx - startIdx + 1);
            if (trunkPickIdx >= trunkNodes.size()) trunkPickIdx = trunkNodes.size() - 1;
            int trunkNodeIdx = trunkNodes.get(trunkPickIdx);
            SkeletonNode attachNode = skeleton.getNode(trunkNodeIdx);
            BlockPos startPos = attachNode.pos();

            double azimuth = random.nextDouble() * 2.0 * Math.PI;
            double elevation = angleMinRad + random.nextDouble() * (angleMaxRad - angleMinRad);

            double dirX = Math.cos(azimuth) * Math.cos(elevation);
            double dirY = Math.sin(elevation);
            double dirZ = Math.sin(azimuth) * Math.cos(elevation);

            double heightRatio = (double) trunkPickIdx / Math.max(1, trunkNodes.size());
            double lengthFactor = 1.0 - heightRatio * 0.3;
            int maxLength = (int) Math.ceil(height * branchLengthRatio * lengthFactor);
            int branchLength = 3 + random.nextInt(Math.max(1, maxLength));

            BlockPos current = startPos;
            int parentIdx = trunkNodeIdx;

            for (int step = 0; step < branchLength; step++) {
                double noise = TreeShapeUtils.positionNoise(
                        random.nextLong(), current.getX(), step, current.getZ());
                double stepX = dirX + (noise - 0.5) * 0.4;
                double stepY = dirY + (noise - 0.5) * 0.3;
                double stepZ = dirZ + (noise - 0.5) * 0.4;
                double len = Math.sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ);
                stepX /= len;
                stepY /= len;
                stepZ /= len;

                int nx = current.getX() + (int) Math.round(stepX);
                int ny = current.getY() + (int) Math.round(stepY);
                int nz = current.getZ() + (int) Math.round(stepZ);
                BlockPos nextPos = new BlockPos(nx, Math.max(ny, current.getY()), nz);

                NodeType branchType = NodeType.PRIMARY_BRANCH;
                float radius = 0.35f * (1.0f - (float) step / branchLength);

                int nodeIdx = skeleton.addNode(new SkeletonNode(
                        nextPos, branchType, radius, parentIdx,
                        skeleton.getNode(parentIdx).depth() + 1));
                parentIdx = nodeIdx;

                if (step == 0) {
                    skeleton.addPrimaryBranch(nodeIdx);
                }
                current = nextPos;
            }
        }
    }

    private static void generateSecondaryBranches(TreeSkeleton skeleton, float probability,
                                                   int maxLength, RandomSource random) {
        List<Integer> primaryStarts = new ArrayList<>(skeleton.primaryBranches());
        for (int primaryIdx : primaryStarts) {
            List<Integer> branchPath = collectBranchPath(skeleton, primaryIdx);
            if (branchPath.size() < 3) continue;

            int midPoint = branchPath.size() / 2;
            for (int i = midPoint; i < branchPath.size() - 1; i++) {
                if (random.nextFloat() >= probability) continue;

                SkeletonNode node = skeleton.getNode(branchPath.get(i));
                int length = 1 + random.nextInt(maxLength);

                SkeletonNode parent = skeleton.getNode(node.parentIndex());
                double bx = node.pos().getX() - parent.pos().getX();
                double by = node.pos().getY() - parent.pos().getY();
                double bz = node.pos().getZ() - parent.pos().getZ();
                double blen = Math.sqrt(bx * bx + by * by + bz * bz);
                if (blen < 0.01) continue;
                bx /= blen;
                by /= blen;
                bz /= blen;

                double angle = random.nextDouble() * 2.0 * Math.PI;
                double deflectRad = Math.toRadians(40 + random.nextDouble() * 40);
                double cosA = Math.cos(deflectRad);
                double sinA = Math.sin(deflectRad);

                double ux, uy, uz;
                if (Math.abs(bx) < 0.9) {
                    uy = Math.sqrt(1 - bx * bx);
                    ux = -by * bx / uy;
                    uz = 0;
                } else {
                    uz = Math.sqrt(1 - bx * bx);
                    ux = -bz * bx / uz;
                    uy = 0;
                }
                double vx = by * uz - bz * uy;
                double vy = bz * ux - bx * uz;
                double vz = bx * uy - by * ux;

                double perpX = Math.cos(angle) * ux + Math.sin(angle) * vx;
                double perpY = Math.cos(angle) * uy + Math.sin(angle) * vy;
                double perpZ = Math.cos(angle) * uz + Math.sin(angle) * vz;

                double dirX = bx * cosA + perpX * sinA;
                double dirY = by * cosA + perpY * sinA;
                double dirZ = bz * cosA + perpZ * sinA;

                BlockPos current = node.pos();
                int parentIdx = branchPath.get(i);

                for (int step = 0; step < length; step++) {
                    double noise = TreeShapeUtils.positionNoise(
                            random.nextLong(), current.getX(), step, current.getZ());
                    int nx = current.getX() + (int) Math.round(dirX + (noise - 0.5) * 0.5);
                    int ny = current.getY() + (int) Math.round(dirY + (noise - 0.5) * 0.3);
                    int nz = current.getZ() + (int) Math.round(dirZ + (noise - 0.5) * 0.5);
                    BlockPos nextPos = new BlockPos(nx, Math.max(ny, current.getY()), nz);

                    NodeType branchType = NodeType.SECONDARY_BRANCH;
                    float radius = 0.2f * (1.0f - (float) step / length);
                    int depth = skeleton.getNode(parentIdx).depth() + 1;

                    int nodeIdx = skeleton.addNode(new SkeletonNode(
                            nextPos, branchType, radius, parentIdx, depth));
                    parentIdx = nodeIdx;
                    current = nextPos;
                }
            }
        }
    }

    private static List<Integer> collectBranchPath(TreeSkeleton skeleton, int startIdx) {
        List<Integer> path = new ArrayList<>();
        int current = startIdx;
        int safety = 0;
        while (current >= 0 && safety < 100) {
            path.add(current);
            SkeletonNode node = skeleton.getNode(current);
            List<Integer> children = findChildren(skeleton, current);
            current = children.isEmpty() ? -1 : children.get(0);
            safety++;
        }
        return path;
    }

    private static List<Integer> findChildren(TreeSkeleton skeleton, int parentIdx) {
        List<Integer> children = new ArrayList<>();
        for (int i = 0; i < skeleton.nodeCount(); i++) {
            if (skeleton.getNode(i).parentIndex() == parentIdx) {
                children.add(i);
            }
        }
        return children;
    }
}
