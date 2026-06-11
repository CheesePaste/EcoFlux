package com.s.ecoflux.plant;

/**
 * Plants simple oak/birch trees after a chunk completes biome succession
 * (e.g. plains to forest).
 *
 * <p>Structure: static utility class with a single public entry point
 * {@link #plantForestTrees} that seeds 5-8 randomly positioned trees within a
 * chunk. Uses deterministic randomness seeded from chunk position and game
 * time. Trees are small (4-5 blocks tall) with a simple leaf canopy,
 * placed only on grass/dirt/podzol and only where space permits. Internally
 * uses a private {@code TreeBlocks} record for log/leaves pair selection.
 *
 * <p>Role in Ecoflux: called by
 * {@link com.s.ecoflux.succession.BiomeTransitionService} immediately after a
 * biome replacement to establish visual continuity — a chunk that graduated
 * to a forest biome should look like one. This is a transitional mechanism;
 * the main plant lifecycle and succession scoring are handled separately by
 * {@link VegetationTracker} and {@link PlantSpawner}.
 */

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ForestPlanter {

    private ForestPlanter() {}

    public static int plantForestTrees(ServerLevel level, LevelChunk chunk, long gameTime) {
        Random random = new Random(chunk.getPos().toLong() ^ gameTime ^ 0x5DEECE66DL);
        int targetCount = 5 + random.nextInt(4);
        int planted = 0;
        for (int attempts = 0; attempts < 72 && planted < targetCount; attempts++) {
            int localX = 2 + random.nextInt(12);
            int localZ = 2 + random.nextInt(12);
            int worldX = chunk.getPos().getBlockX(localX);
            int worldZ = chunk.getPos().getBlockZ(localZ);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
            BlockPos basePos = new BlockPos(worldX, surfaceY, worldZ);
            if (placeSimpleTree(level, basePos, random, randomTreeBlocks(random))) {
                planted++;
            }
        }
        return planted;
    }

    private static TreeBlocks randomTreeBlocks(Random random) {
        return random.nextBoolean()
                ? new TreeBlocks(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState())
                : new TreeBlocks(Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_LEAVES.defaultBlockState());
    }

    private static boolean placeSimpleTree(ServerLevel level, BlockPos basePos, Random random, TreeBlocks treeBlocks) {
        BlockState ground = level.getBlockState(basePos.below());
        if (!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT) && !ground.is(Blocks.PODZOL)) {
            return false;
        }

        int height = 4 + random.nextInt(2);
        if (!hasTreeSpace(level, basePos, height)) {
            return false;
        }

        for (int y = 0; y < height; y++) {
            level.setBlock(basePos.above(y), treeBlocks.log(), Block.UPDATE_ALL);
        }

        int leafStart = height - 2;
        int leafEnd = height + 1;
        for (int y = leafStart; y <= leafEnd; y++) {
            int radius = y >= height ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && (y >= height || random.nextBoolean())) {
                        continue;
                    }
                    BlockPos leafPos = basePos.offset(dx, y, dz);
                    if (canReplaceForTree(level.getBlockState(leafPos))) {
                        level.setBlock(leafPos, treeBlocks.leaves(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasTreeSpace(ServerLevel level, BlockPos basePos, int height) {
        for (int y = 0; y <= height + 1; y++) {
            int radius = y < height - 2 ? 0 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!canReplaceForTree(level.getBlockState(basePos.offset(dx, y, dz)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean canReplaceForTree(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN);
    }

    private record TreeBlocks(BlockState log, BlockState leaves) {}
}
