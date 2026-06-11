package com.s.ecoflux.world;

/**
 * Global registry tracking which chunks are currently being processed or
 * observed, keyed by dimension.
 *
 * <p>Structure: static utility class backed by a per-dimension
 * {@code LinkedHashSet&lt;Long&gt;} of chunk position longs. Provides
 * {@code addTrackedChunk()}, {@code removeTrackedChunk()},
 * {@code getTrackedChunks()}, and {@code hasTrackedChunks()}.
 *
 * <p>Role in Ecoflux: used to coordinate chunk-level operations (e.g. accelerated
 * prototype mode) across dimensions without attaching tracking metadata directly
 * to chunk data.
 */

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ChunkTrackingState {
    private static final Map<ResourceKey<Level>, LinkedHashSet<Long>> TRACKED_CHUNKS = new HashMap<>();

    private ChunkTrackingState() {}

    public static void addTrackedChunk(ResourceKey<Level> dim, long chunkPos) {
        TRACKED_CHUNKS.computeIfAbsent(dim, k -> new LinkedHashSet<>()).add(chunkPos);
    }

    public static void removeTrackedChunk(ResourceKey<Level> dim, long chunkPos) {
        LinkedHashSet<Long> chunks = TRACKED_CHUNKS.get(dim);
        if (chunks != null) {
            chunks.remove(chunkPos);
            if (chunks.isEmpty()) {
                TRACKED_CHUNKS.remove(dim);
            }
        }
    }

    public static LinkedHashSet<Long> getTrackedChunks(ResourceKey<Level> dim) {
        return TRACKED_CHUNKS.get(dim);
    }

    public static boolean hasTrackedChunks(ResourceKey<Level> dim) {
        LinkedHashSet<Long> chunks = TRACKED_CHUNKS.get(dim);
        return chunks != null && !chunks.isEmpty();
    }
}
