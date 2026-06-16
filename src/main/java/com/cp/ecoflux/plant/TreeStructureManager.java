package com.cp.ecoflux.plant;

import com.cp.ecoflux.attachment.SuccessionChunkData;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BFS-based tree structure rebuild, progressive death decay, and bulk block removal.
 *
 * <p>Extracted from {@link VegetationTracker} to keep each class under 200 lines.
 */
public final class TreeStructureManager {
    private static final int REBUILD_BFS_MAX = 3000;

    private TreeStructureManager() {}

    /**
     * Rebuilds a TreeStructure from world blocks via BFS from the root position.
     * Used when TreeStructure was not persisted to NBT (on chunk reload).
     */
    public static TreeStructure rebuild(ServerLevel level, BlockPos rootPos) {
        Set<BlockPos> logs = new LinkedHashSet<>();
        Set<BlockPos> leaves = new LinkedHashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(rootPos);
        visited.add(rootPos);

        while (!queue.isEmpty() && visited.size() < REBUILD_BFS_MAX) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            boolean isLog = state.is(BlockTags.LOGS);
            boolean isLeaf = state.is(BlockTags.LEAVES);

            if (isLog) {
                logs.add(pos);
            } else if (isLeaf) {
                leaves.add(pos);
            } else if (!pos.equals(rootPos)) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = pos.offset(dx, dy, dz);
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        if (logs.isEmpty() && leaves.isEmpty()) {
            return new TreeStructure(new long[0], new long[0]);
        }
        return new TreeStructure(
                logs.stream().mapToLong(BlockPos::asLong).toArray(),
                leaves.stream().mapToLong(BlockPos::asLong).toArray());
    }

    /** Removes a fraction of tree blocks each call, leaves first then logs. */
    public static TreeStructure processDeath(ServerLevel level, TreeStructure ts) {
        long[] leaves = ts.leafPositions();
        long[] logs = ts.logPositions();
        int maxRemove = Math.max(1, (leaves.length + logs.length) / 48);

        if (leaves.length > 0) {
            int removeLeaves = Math.min(maxRemove, leaves.length);
            for (int i = 0; i < removeLeaves; i++) {
                BlockPos leafPos = BlockPos.of(leaves[i]);
                BlockState leafState = level.getBlockState(leafPos);
                if (leafState.is(BlockTags.LEAVES)) {
                    level.destroyBlock(leafPos, false);
                }
            }
            long[] remainingLeaves = new long[leaves.length - removeLeaves];
            System.arraycopy(leaves, removeLeaves, remainingLeaves, 0, remainingLeaves.length);
            return new TreeStructure(logs, remainingLeaves);
        }

        if (logs.length > 0) {
            int removeLogs = Math.min(maxRemove, logs.length);
            for (int i = 0; i < removeLogs; i++) {
                BlockPos logPos = BlockPos.of(logs[i]);
                BlockState logState = level.getBlockState(logPos);
                if (logState.is(BlockTags.LOGS)) {
                    level.destroyBlock(logPos, false);
                }
            }
            long[] remainingLogs = new long[logs.length - removeLogs];
            System.arraycopy(logs, removeLogs, remainingLogs, 0, remainingLogs.length);
            return new TreeStructure(remainingLogs, new long[0]);
        }

        return ts;
    }

    /** Destroys all blocks in a tree structure and removes vegetation records. */
    public static void removeAllBlocks(ServerLevel level, SuccessionChunkData chunkData,
                                        BlockPos recordPos, TreeStructure ts) {
        for (long packed : ts.leafPositions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.getBlockState(pos).isAir()) {
                level.destroyBlock(pos, false);
            }
            chunkData.removeVegetation(pos);
        }
        for (long packed : ts.logPositions()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.getBlockState(pos).isAir()) {
                level.destroyBlock(pos, false);
            }
            chunkData.removeVegetation(pos);
        }
        chunkData.removeVegetation(recordPos);
    }
}
