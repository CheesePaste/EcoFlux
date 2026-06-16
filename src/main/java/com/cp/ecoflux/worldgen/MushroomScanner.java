package com.cp.ecoflux.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;

/**
 * Detects and extracts huge mushroom structures from world-gen chunks.
 *
 * <p>Extracted from {@link WorldGenVegetationScanner} to keep each class focused.
 */
public final class MushroomScanner {
    private static final int MAX_BFS_SIZE = 4096;

    private MushroomScanner() {}

    public record TreeComponent(Set<BlockPos> logs, Set<BlockPos> leaves) {}

    @Nullable
    public static TreeComponent extract(ServerLevel level, BlockPos startStem, LevelChunk chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        Set<BlockPos> stems = new HashSet<>();
        Queue<BlockPos> stemQueue = new ArrayDeque<>();
        stems.add(startStem);
        stemQueue.add(startStem);

        while (!stemQueue.isEmpty()) {
            BlockPos pos = stemQueue.poll();
            for (BlockPos neighbor : neighbors26(pos)) {
                if (stems.contains(neighbor)) continue;
                if (neighbor.getY() < minY || neighbor.getY() >= maxY) continue;
                BlockState ns = getBlockStateIfLoaded(level, neighbor);
                if (ns == null) continue;
                if (ns.is(Blocks.MUSHROOM_STEM)) {
                    stems.add(neighbor);
                    stemQueue.add(neighbor);
                    if (stems.size() > MAX_BFS_SIZE) return null;
                }
            }
        }

        Set<BlockPos> caps = new HashSet<>();
        for (BlockPos stem : stems) {
            for (BlockPos neighbor : neighbors26(stem)) {
                if (stems.contains(neighbor) || caps.contains(neighbor)) continue;
                if (neighbor.getY() < minY || neighbor.getY() >= maxY) continue;
                BlockState ns = getBlockStateIfLoaded(level, neighbor);
                if (ns == null) continue;
                if (ns.is(Blocks.BROWN_MUSHROOM_BLOCK) || ns.is(Blocks.RED_MUSHROOM_BLOCK)) {
                    caps.add(neighbor);
                }
            }
        }

        return new TreeComponent(stems, caps);
    }

    @Nullable
    public static ResourceLocation inferCapType(ServerLevel level, Set<BlockPos> caps) {
        int brown = 0;
        int red = 0;
        for (BlockPos pos : caps) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
                brown++;
            } else if (state.is(Blocks.RED_MUSHROOM_BLOCK)) {
                red++;
            }
        }
        if (brown >= red && brown > 0) {
            return ResourceLocation.withDefaultNamespace("brown_mushroom");
        }
        if (red > brown) {
            return ResourceLocation.withDefaultNamespace("red_mushroom");
        }
        return null;
    }

    public static BlockPos findLowest(Set<BlockPos> logs) {
        BlockPos lowest = null;
        for (BlockPos log : logs) {
            if (lowest == null || log.getY() < lowest.getY()) {
                lowest = log;
            }
        }
        return lowest;
    }

    @Nullable
    private static BlockState getBlockStateIfLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk c = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (c == null) return null;
        return c.getBlockState(pos);
    }

    private static List<BlockPos> neighbors26(BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    result.add(pos.offset(dx, dy, dz));
                }
            }
        }
        return result;
    }
}
