/*
 * Portions of this file incorporate code from DynamicTrees
 * (https://github.com/ferreusveritas/DynamicTrees), used under the MIT License:
 *
 * MIT License
 * Copyright (c) 2025 DynamicTreesTeam
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Specifically adapted from:
 *   MathUtils.selectRandomFromDistribution()           → selectWeightedIndex()
 *   GrowthLogicKit.populateDirectionProbabilityMap()   → selectDirection() probMap
 *   ConiferLogic.selectNewDirection() / populateDirectionProbabilityMap()
 *                                                      → conifer branching / energy cap
 *   GrowSignal class                                   → GrowSignal inner class + signal-based log growth
 */

package com.cp.ecoflux.plant.tree.spacecolonization;

import com.cp.ecoflux.plant.tree.TreeShapeUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

public final class SpaceColonizationGenerator {

    private SpaceColonizationGenerator() {}

    public record FullTreePlan(List<BlockPos> logPositions, List<BlockPos> leafPositions, int resolvedHeight) {}

    public record StagePlan(List<List<BlockPos>> logsByStage, List<List<BlockPos>> leavesByStage) {}

    private static class GrowSignal {
        BlockPos pos;
        Direction dir;
        float energy;
        int numTurns;
        int numSteps;
        int parentIndex;
        boolean isBranch;

        GrowSignal(BlockPos pos, Direction dir, float energy, int parentIndex, boolean isBranch) {
            this.pos = pos;
            this.dir = dir;
            this.energy = energy;
            this.parentIndex = parentIndex;
            this.isBranch = isBranch;
            this.numTurns = 0;
            this.numSteps = 0;
        }
    }

    private record Node(BlockPos pos, int parentIndex) {}

    // --------------- Public API ---------------

    public static FullTreePlan generateFull(BlockPos saplingPos, SpaceColonizationParams params,
                                            int resolvedHeight, boolean is2x2, RandomSource random) {
        List<Node> nodes = growLogs(saplingPos, params, resolvedHeight, is2x2, random);
        List<BlockPos> logs = nodes.stream().map(n -> n.pos).toList();
        List<BlockPos> leaves = generateLeaves(logs, saplingPos, params, is2x2, random);
        return new FullTreePlan(logs, leaves, resolvedHeight);
    }

    public static StagePlan generateAndPartition(BlockPos saplingPos, SpaceColonizationParams params,
                                                  int resolvedHeight, int totalStages, boolean is2x2,
                                                  RandomSource random) {
        FullTreePlan full = generateFull(saplingPos, params, resolvedHeight, is2x2, random);
        return partitionIntoStages(full, totalStages);
    }

    // ---------- Direction selection (DT-style probMap) ----------

    private static int selectWeightedIndex(int[] probMap, RandomSource random) {
        int total = 0;
        for (int w : probMap) total += w;
        if (total <= 0) return 1; // UP fallback
        int rnd = random.nextInt(total);
        for (int i = 0; i < 6; i++) {
            rnd -= probMap[i];
            if (rnd < 0) return i;
        }
        return 1;
    }

    private static Direction selectDirection(GrowSignal signal, SpaceColonizationParams params,
                                             Set<BlockPos> occupied, RandomSource random) {
        int[] probMap = new int[6];
        Direction originDir = signal.dir.getOpposite();
        boolean isConifer = params.envelopeType() == SpaceColonizationParams.EnvelopeType.CONE;
        boolean inTrunk = signal.numTurns == 0;

        // --- UP probability ---
        if (signal.dir != Direction.DOWN) {
            if (isConifer) {
                probMap[1] = inTrunk ? params.upProbability() : 0;
            } else {
                probMap[1] = params.upProbability();
            }
        }

        // --- Momentum: reinforce current direction ---
        if (isConifer) {
            probMap[signal.dir.ordinal()] += inTrunk ? 0 : (signal.numTurns == 1 ? 2 : 1);
        } else {
            probMap[signal.dir.ordinal()] += 1;
        }

        // --- Side/horizontal directions ---
        for (Direction dir : Direction.values()) {
            if (dir == originDir) continue;          // never go back
            if (dir == Direction.DOWN) continue;      // never go down
            if (dir == signal.dir) continue;          // already weighted
            if (dir == Direction.UP) continue;        // already weighted

            if (isConifer) {
                // Conifer: horizontal only allowed when branched, OR in trunk on odd steps
                boolean allow = !inTrunk || (inTrunk && signal.numSteps % 2 == 1);
                if (allow) {
                    BlockPos target = signal.pos.relative(dir);
                    probMap[dir.ordinal()] = occupied.contains(target) ? 0 : 2;
                }
            } else {
                // Non-conifer: check if target is free
                if (!inTrunk || signal.numSteps >= params.lowestBranchHeight()) {
                    BlockPos target = signal.pos.relative(dir);
                    probMap[dir.ordinal()] = occupied.contains(target) ? 0 : 1;
                }
            }
        }

        int choice = selectWeightedIndex(probMap, random);
        return Direction.values()[choice];
    }

    // --------------- Log generation (DT-inspired) ---------------

    private static List<Node> growLogs(BlockPos saplingPos, SpaceColonizationParams params,
                                        int resolvedHeight, boolean is2x2, RandomSource random) {
        List<Node> nodes = new ArrayList<>();
        Set<BlockPos> occupied = new LinkedHashSet<>();
        boolean isConifer = params.envelopeType() == SpaceColonizationParams.EnvelopeType.CONE;

        List<GrowSignal> signals = new ArrayList<>();

        if (is2x2) {
            BlockPos[] starts = {
                    saplingPos.above(),
                    saplingPos.above().east(),
                    saplingPos.above().south(),
                    saplingPos.above().east().south()
            };
            for (BlockPos startPos : starts) {
                Node rootNode = new Node(startPos, -1);
                nodes.add(rootNode);
                occupied.add(startPos);
                signals.add(new GrowSignal(startPos, Direction.UP, resolvedHeight, nodes.size() - 1, false));
            }
        } else {
            BlockPos startPos = saplingPos.above();
            Node rootNode = new Node(startPos, -1);
            nodes.add(rootNode);
            occupied.add(startPos);
            signals.add(new GrowSignal(startPos, Direction.UP, resolvedHeight, 0, false));
        }

        while (!signals.isEmpty()) {
            List<GrowSignal> nextSignals = new ArrayList<>();

            for (GrowSignal sig : signals) {
                if (sig.energy <= 0) continue;

                // Step forward
                BlockPos newPos = sig.pos.relative(sig.dir);
                sig.pos = newPos;
                sig.numSteps++;
                sig.energy -= 1.0f;

                if (!occupied.contains(newPos)) {
                    occupied.add(newPos);
                    int nodeIdx = nodes.size();
                    nodes.add(new Node(newPos, sig.parentIndex));
                    sig.parentIndex = nodeIdx;
                }

                // Select direction for next step
                Direction newDir = selectDirection(sig, params, occupied, random);
                boolean turned = (sig.dir != newDir);

                if (turned && !sig.isBranch && sig.numSteps >= params.lowestBranchHeight()) {
                    // TRUNK BRANCHING: create a branch in the new direction
                    float branchEnergy = (float) (sig.energy * params.branchLengthRatio());
                    if (isConifer) {
                        branchEnergy = sig.energy / 3.0f;
                        if (branchEnergy > 16f) branchEnergy = 16f;
                    }
                    if (branchEnergy >= 2.0f) {
                        GrowSignal branch = new GrowSignal(sig.pos, newDir, branchEnergy, sig.parentIndex, true);
                        nextSignals.add(branch);
                    }
                    // Trunk keeps going UP
                    sig.dir = Direction.UP;
                } else {
                    sig.dir = newDir;
                    if (turned) sig.numTurns++;
                }

                // Secondary branching on existing branches
                if (sig.isBranch && sig.numTurns <= 1 && random.nextDouble() < params.secondaryChance() && sig.energy >= 2.0f) {
                    Direction secDir = pickSecondaryDir(sig.dir, sig.pos, occupied, random);
                    if (secDir != null) {
                        float secEnergy = sig.energy * 0.4f;
                        GrowSignal secondary = new GrowSignal(sig.pos, secDir, secEnergy, sig.parentIndex, true);
                        secondary.numTurns = 1; // mark as already branched
                        nextSignals.add(secondary);
                    }
                }

                if (sig.energy > 0) {
                    nextSignals.add(sig);
                }
            }

            signals = nextSignals;
        }

        return nodes;
    }

    private static Direction pickSecondaryDir(Direction parentDir, BlockPos pos,
                                               Set<BlockPos> occupied, RandomSource random) {
        // Pick first free perpendicular horizontal direction
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir.getOpposite() == parentDir) continue;
            BlockPos target = pos.relative(dir);
            if (!occupied.contains(target)) return dir;
        }
        // If all horizontals are occupied, try a diagonal by checking no candidates
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (random.nextBoolean()) return dir; // accept even if occupied, it'll just skip
        }
        return null;
    }

    // --------------- Leaf generation ---------------

    private static Set<BlockPos> findEndpoints(List<BlockPos> logPositions) {
        Set<BlockPos> logSet = Set.copyOf(logPositions);
        Set<BlockPos> endpoints = new LinkedHashSet<>();
        for (BlockPos pos : logPositions) {
            int neighbors = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (logSet.contains(new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))) {
                            neighbors++;
                        }
                    }
                }
            }
            if (neighbors <= 2) {
                endpoints.add(pos);
            }
        }
        return endpoints;
    }

    private static List<BlockPos> generateLeaves(List<BlockPos> logPositions, BlockPos saplingPos,
                                                  SpaceColonizationParams params, boolean is2x2,
                                                  RandomSource random) {
        Set<BlockPos> logSet = Set.copyOf(logPositions);
        Set<BlockPos> endpoints = findEndpoints(logPositions);
        Set<BlockPos> leafSet = new LinkedHashSet<>();
        int radius = params.leafRadius();
        long seed = random.nextLong();

        double rXZ = params.envelopeRadiusXZ();
        double rY = params.envelopeHeight() * 0.5;
        double centerX = saplingPos.getX() + (is2x2 ? 1.0 : 0.5);
        double centerY = saplingPos.getY() + params.envelopeCenterYOffset() + 0.5;
        double centerZ = saplingPos.getZ() + (is2x2 ? 1.0 : 0.5);
        SpaceColonizationParams.EnvelopeType type = params.envelopeType();

        int minLeafY = saplingPos.getY() + params.lowestBranchHeight();

        // Phase 1: Stamp leaf clusters at endpoints (DT-style twig leaf clusters)
        int clusterRadius = Math.max(2, radius);
        for (BlockPos endpoint : endpoints) {
            for (int dx = -clusterRadius; dx <= clusterRadius; dx++) {
                for (int dy = -clusterRadius; dy <= clusterRadius; dy++) {
                    for (int dz = -clusterRadius; dz <= clusterRadius; dz++) {
                        int cx = endpoint.getX() + dx;
                        int cy = endpoint.getY() + dy;
                        int cz = endpoint.getZ() + dz;
                        if (cy < minLeafY) continue;
                        BlockPos candidate = new BlockPos(cx, cy, cz);
                        if (logSet.contains(candidate) || leafSet.contains(candidate)) continue;
                        if (!isAdjacentTo(candidate, logSet)) continue;

                        double noise = TreeShapeUtils.positionNoise(seed, cx, cy, cz);
                        if (noise < params.leafDensity()) {
                            leafSet.add(candidate);
                        }
                    }
                }
            }
        }

        // Pre-compute top log Y for 2x2 envelope bypass (Phase 2) and top cap (Phase 3)
        int topY = 0;
        if (is2x2) {
            for (BlockPos p : logPositions) {
                if (p.getY() > topY) topY = p.getY();
            }
        }

        // Phase 2: Fill remaining canopy volume around non-endpoint logs with envelope check
        for (BlockPos logPos : logPositions) {
            if (endpoints.contains(logPos)) continue; // already covered by Phase 1
            // For 2x2 trees, bypass envelope check near trunk top so Phase 2 fills
            // leaves around the upper trunk even when envelope density is near zero.
            boolean nearTop = is2x2 && (logPos.getY() >= topY - 2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int cx = logPos.getX() + dx;
                        int cy = logPos.getY() + dy;
                        int cz = logPos.getZ() + dz;
                        if (cy < minLeafY) continue;
                        BlockPos candidate = new BlockPos(cx, cy, cz);

                        if (logSet.contains(candidate) || leafSet.contains(candidate)) continue;
                        if (!isAdjacentTo(candidate, logSet)) continue;

                        double prob;
                        if (nearTop) {
                            prob = params.leafDensity();
                        } else {
                            double envelopeDensity = envelopeDensityAt(
                                    cx + 0.5, cy + 0.5, cz + 0.5,
                                    centerX, centerY, centerZ, rXZ, rY, type);
                            if (envelopeDensity <= 0.01) continue;
                            prob = params.leafDensity() * envelopeDensity;
                        }
                        double noise = TreeShapeUtils.positionNoise(seed, cx, cy, cz);
                        if (noise < prob) {
                            leafSet.add(candidate);
                        }
                    }
                }
            }
        }

        // Phase 3: 2x2 trunk top cap
        // For 2x2 trees the envelope top can fall below the resolved trunk height
        // (gap up to ~4.5 blocks for spruce), leaving the 2x2 trunk platform bare.
        // Two-pass: first pass adjacent to logs, second pass extends upward using
        // the LIVE leafSet (NOT a stale snapshot) so leaves at dy=N anchor dy=N+1.
        if (is2x2) {
            // capR: generous radius — leafRadius+2, minimum 5 for spruce coverage
            int capR = Math.max(radius + 2, 5);
            // Pass 1: leaves adjacent to top logs (dy 0..1)
            for (BlockPos logPos : logPositions) {
                if (logPos.getY() != topY) continue;
                for (int dx = -capR; dx <= capR; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dz = -capR; dz <= capR; dz++) {
                            int cx = logPos.getX() + dx;
                            int cy = logPos.getY() + dy;
                            int cz = logPos.getZ() + dz;
                            if (cy < minLeafY) continue;
                            BlockPos candidate = new BlockPos(cx, cy, cz);
                            if (logSet.contains(candidate) || leafSet.contains(candidate)) continue;
                            if (!isAdjacentTo(candidate, logSet)) continue;
                            double noise = TreeShapeUtils.positionNoise(seed, cx, cy, cz);
                            if (noise < params.leafDensity()) {
                                leafSet.add(candidate);
                            }
                        }
                    }
                }
            }
            // Pass 2: extend upward (dy 2..capR), anchoring against the LIVE
            // leafSet so leaves placed at dy=N can serve as anchors for dy=N+1.
            for (BlockPos logPos : logPositions) {
                if (logPos.getY() != topY) continue;
                for (int dx = -capR; dx <= capR; dx++) {
                    for (int dy = 2; dy <= capR; dy++) {
                        for (int dz = -capR; dz <= capR; dz++) {
                            int cx = logPos.getX() + dx;
                            int cy = logPos.getY() + dy;
                            int cz = logPos.getZ() + dz;
                            if (cy < minLeafY) continue;
                            BlockPos candidate = new BlockPos(cx, cy, cz);
                            if (logSet.contains(candidate) || leafSet.contains(candidate)) continue;
                            if (!isAdjacentTo(candidate, logSet) && !isAdjacentTo(candidate, leafSet)) continue;
                            double noise = TreeShapeUtils.positionNoise(seed, cx, cy, cz);
                            if (noise < params.leafDensity()) {
                                leafSet.add(candidate);
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(leafSet);
    }

    private static double envelopeDensityAt(double x, double y, double z,
                                             double cx, double cy, double cz,
                                             double rXZ, double rY,
                                             SpaceColonizationParams.EnvelopeType type) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        double normXZ = (dx * dx + dz * dz) / (rXZ * rXZ);

        return switch (type) {
            case ELLIPSOID, TALL_ELLIPSOID -> {
                double normY = (dy * dy) / (rY * rY);
                double raw = normXZ + normY;
                if (raw >= 1.0) yield 0;
                if (raw < 0.35) yield 1.0;
                yield 1.0 - (raw - 0.35) / 0.65;
            }
            case CONE -> {
                double normalizedY = (y - (cy - rY)) / (2.0 * rY);
                if (normalizedY < 0 || normalizedY > 1.0) yield 0;
                double maxRadius = rXZ * (1.0 - normalizedY);
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > maxRadius) yield 0;
                if (dist < maxRadius * 0.35) yield 1.0;
                yield 1.0 - (dist - maxRadius * 0.35) / (maxRadius * 0.65);
            }
        };
    }

    private static boolean isAdjacentTo(BlockPos pos, Set<BlockPos> target) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (target.contains(new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --------------- Stage partitioning ---------------

    static StagePlan partitionIntoStages(FullTreePlan full, int totalStages) {
        List<BlockPos> logs = full.logPositions();
        List<BlockPos> leaves = full.leafPositions();
        Set<BlockPos> logSet = Set.copyOf(logs);

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : logs) {
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        int yRange = Math.max(1, maxY - minY);
        int heightPerStage = Math.max(1, yRange / totalStages);

        List<List<BlockPos>> logsByStage = new ArrayList<>();
        List<List<BlockPos>> leavesByStage = new ArrayList<>();
        for (int s = 0; s < totalStages; s++) {
            logsByStage.add(new ArrayList<>());
            leavesByStage.add(new ArrayList<>());
        }

        for (BlockPos p : logs) {
            int stageY = p.getY() - minY;
            int stage = Math.min(totalStages - 1, stageY / heightPerStage);
            logsByStage.get(stage).add(p);
        }

        for (BlockPos leaf : leaves) {
            int bestStage = totalStages - 1;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = new BlockPos(leaf.getX() + dx, leaf.getY() + dy, leaf.getZ() + dz);
                        if (logSet.contains(neighbor)) {
                            int neighborY = neighbor.getY() - minY;
                            int neighborStage = Math.min(totalStages - 1, neighborY / heightPerStage);
                            if (neighborStage < bestStage) {
                                bestStage = neighborStage;
                            }
                        }
                    }
                }
            }
            leavesByStage.get(bestStage).add(leaf);
        }

        return new StagePlan(logsByStage, leavesByStage);
    }

    // --------------- Verification ---------------

    public static boolean verifyConnectivity(FullTreePlan plan) {
        List<BlockPos> logs = plan.logPositions();
        if (logs.isEmpty()) return true;
        Set<BlockPos> logSet = Set.copyOf(logs);
        Set<BlockPos> visited = new LinkedHashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        queue.add(logs.get(0));
        visited.add(logs.get(0));

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeLast();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = new BlockPos(
                                current.getX() + dx, current.getY() + dy, current.getZ() + dz);
                        if (logSet.contains(neighbor) && visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return visited.size() == logs.size();
    }

    public static int countFloatingLeaves(FullTreePlan plan) {
        Set<BlockPos> logSet = Set.copyOf(plan.logPositions());
        int floating = 0;
        for (BlockPos leaf : plan.leafPositions()) {
            if (!isAdjacentTo(leaf, logSet)) {
                floating++;
            }
        }
        return floating;
    }
}
