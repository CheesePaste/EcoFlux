package com.s.ecoflux.plant.tree;

/**
 * Shared static utility methods for tree shape generation.
 *
 * <p>Structure: Position-deterministic noise and random functions, 2x2 sapling
 * detection, 2x2 trunk position generation, and a safe log placement helper that
 * respects block replaceability (air, leaves, replaceable tags).
 *
 * <p>Role in Ecoflux: Common utility layer used by both legacy growth profiles and
 * the morphology system ({@link com.s.ecoflux.plant.tree.morphology.SkeletonGenerator},
 * {@link com.s.ecoflux.plant.tree.morphology.LeafFiller}). Ensures consistent
 * deterministic randomness for reproducible tree shapes across server restarts.
 */

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeShapeUtils {

    private TreeShapeUtils() {
    }

    public static double positionNoise(long seed, int x, int y, int z) {
        long h = seed;
        h ^= (long) x * 0x9e3779b97f4a7c15L;
        h ^= (long) y * 0xc6a4a7935bd1e995L;
        h ^= (long) z * 0x27ae2f10b06b6e7dL;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        h = h ^ (h >>> 31);
        return (h & 0x7fffffffffffffffL) / (double) Long.MAX_VALUE;
    }

    public static RandomSource positionRandom(BlockPos saplingPos, long worldSeed) {
        long h = worldSeed;
        h ^= (long) saplingPos.getX() * 0x9e3779b97f4a7c15L;
        h ^= (long) saplingPos.getY() * 0xc6a4a7935bd1e995L;
        h ^= (long) saplingPos.getZ() * 0x27ae2f10b06b6e7dL;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        return RandomSource.create(h);
    }

    public static boolean tryPlaceLog(ServerLevel level, BlockPos pos, Block logBlock, Direction.Axis axis) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing.is(BlockTags.LEAVES) || existing.is(BlockTags.REPLACEABLE)) {
            level.setBlock(pos, logBlock.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), 3);
            return true;
        }
        return false;
    }

    public static BlockPos find2x2NWCorner(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SaplingBlock)) return null;
        ResourceLocation type = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        for (int baseDx = 0; baseDx >= -1; baseDx--) {
            for (int baseDz = 0; baseDz >= -1; baseDz--) {
                int minX = pos.getX() + baseDx;
                int minZ = pos.getZ() + baseDz;
                boolean valid = true;
                for (int dx = 0; dx <= 1 && valid; dx++) {
                    for (int dz = 0; dz <= 1 && valid; dz++) {
                        BlockPos checkPos = new BlockPos(minX + dx, pos.getY(), minZ + dz);
                        BlockState checkState = level.getBlockState(checkPos);
                        if (!(checkState.getBlock() instanceof SaplingBlock)) {
                            valid = false;
                            break;
                        }
                        ResourceLocation checkType = BuiltInRegistries.BLOCK.getKey(checkState.getBlock());
                        if (!type.equals(checkType)) {
                            valid = false;
                            break;
                        }
                    }
                }
                if (valid) return new BlockPos(minX, pos.getY(), minZ);
            }
        }
        return null;
    }

    public static BlockPos[] trunk2x2Positions(BlockPos nwCorner, int y) {
        return new BlockPos[]{
                new BlockPos(nwCorner.getX(), y, nwCorner.getZ()),
                new BlockPos(nwCorner.getX() + 1, y, nwCorner.getZ()),
                new BlockPos(nwCorner.getX(), y, nwCorner.getZ() + 1),
                new BlockPos(nwCorner.getX() + 1, y, nwCorner.getZ() + 1)
        };
    }
}
