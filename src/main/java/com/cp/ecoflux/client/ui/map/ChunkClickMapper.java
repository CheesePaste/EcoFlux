package com.cp.ecoflux.client.ui.map;

/**
 * Converts a mouse position within the satellite map into a world {@link ChunkPos}.
 *
 * <p>The map covers {@link SatelliteMapRenderer#CHUNK_GRID}×{@link SatelliteMapRenderer#CHUNK_GRID}
 * chunks. Row 0 = north (most negative Z), Column 0 = west (most negative X).
 */

import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

public final class ChunkClickMapper {

    private ChunkClickMapper() {}

    @Nullable
    public static ChunkPos screenToChunk(
            double mouseX, double mouseY,
            int mapLeft, int mapTop, int mapSize,
            ChunkPos centerChunk) {

        if (mouseX < mapLeft || mouseX >= mapLeft + mapSize
                || mouseY < mapTop  || mouseY >= mapTop + mapSize) {
            return null;
        }

        double chunkPx = (double) mapSize / SatelliteMapRenderer.CHUNK_GRID;

        int col = (int) ((mouseX - mapLeft) / chunkPx);
        int row = (int) ((mouseY - mapTop)  / chunkPx);

        int max = SatelliteMapRenderer.CHUNK_GRID - 1;
        if (col < 0) col = 0; else if (col > max) col = max;
        if (row < 0) row = 0; else if (row > max) row = max;

        int half = SatelliteMapRenderer.CHUNK_GRID / 2;
        return new ChunkPos(centerChunk.x + col - half, centerChunk.z + row - half);
    }
}
