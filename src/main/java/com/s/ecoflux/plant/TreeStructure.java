package com.s.ecoflux.plant;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public record TreeStructure(long[] logPositions, long[] leafPositions) {
    private static final String LOG_POSITIONS = "tree_logs";
    private static final String LEAF_POSITIONS = "tree_leaves";

    public boolean isEmpty() {
        return (logPositions == null || logPositions.length == 0)
                && (leafPositions == null || leafPositions.length == 0);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (logPositions != null && logPositions.length > 0) {
            tag.putLongArray(LOG_POSITIONS, logPositions);
        }
        if (leafPositions != null && leafPositions.length > 0) {
            tag.putLongArray(LEAF_POSITIONS, leafPositions);
        }
        return tag;
    }

    @Nullable
    public static TreeStructure fromTag(CompoundTag tag) {
        long[] logs = null;
        long[] leaves = null;
        if (tag.contains(LOG_POSITIONS)) {
            logs = tag.getLongArray(LOG_POSITIONS);
        }
        if (tag.contains(LEAF_POSITIONS)) {
            leaves = tag.getLongArray(LEAF_POSITIONS);
        }
        if (logs == null && leaves == null) {
            return null;
        }
        return new TreeStructure(logs != null ? logs : new long[0],
                leaves != null ? leaves : new long[0]);
    }

    public int totalBlocks() {
        int total = 0;
        if (logPositions != null) total += logPositions.length;
        if (leafPositions != null) total += leafPositions.length;
        return total;
    }
}
