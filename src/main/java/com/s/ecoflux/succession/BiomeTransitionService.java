package com.s.ecoflux.succession;

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.network.ModNetworking;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class BiomeTransitionService {
    private BiomeTransitionService() {
    }

    public static String applyRegression(
            ServerLevel level,
            LevelChunk chunk,
            SuccessionChunkData chunkData,
            com.s.ecoflux.config.SuccessionPathDefinition path) {
        ResourceKey<Biome> fallbackKey = chunkData.getPreviousBiome().orElse(null);
        if (path.fallbackBiome() != null) {
            ResourceLocation fallbackId = path.fallbackBiome();
            fallbackKey = ResourceKey.create(Registries.BIOME, fallbackId);
        }

        if (fallbackKey == null) {
            return "区块 " + chunk.getPos() + " 跳过群系回退：没有回退目标群系。";
        }

        Holder<Biome> biomeHolder = level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(fallbackKey);
        chunk.fillBiomesFromNoise(
                (x, y, z, sampler) -> biomeHolder,
                level.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        level.getServer()
                .getPlayerList()
                .broadcastAll(
                        ClientboundChunksBiomesPacket.forChunks(List.of(chunk)),
                        level.dimension());

        ResourceKey<Biome> oldBiome = chunkData.getCurrentBiome().orElse(null);
        chunkData.setPreviousBiome(oldBiome);
        chunkData.setCurrentBiome(fallbackKey);
        chunkData.clearRuntimeState();
        chunkData.setLastEvaluationGameTime(level.getGameTime());
        ModNetworking.syncChunkToTracking(level, chunk);

        EcofluxConstants.LOGGER.info(
                "区块 {} 演替回退：{} -> {}",
                chunk.getPos(),
                oldBiome == null ? "未知" : oldBiome.location(),
                fallbackKey.location());
        return "区块 " + chunk.getPos() + " 已从 "
                + (oldBiome == null ? "未知" : oldBiome.location())
                + " 回退到 " + fallbackKey.location() + "。";
    }

    public static String applyTransition(ServerLevel level, LevelChunk chunk, SuccessionChunkData chunkData) {
        SuccessionChunkData data = chunkData;
        java.util.Optional<ResourceKey<Biome>> targetBiome = data.getTargetBiome();
        if (targetBiome.isEmpty()) {
            return "区块 " + chunk.getPos() + " 跳过群系转化：没有目标群系。";
        }

        Holder<Biome> biomeHolder = level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .getOrThrow(targetBiome.get());
        chunk.fillBiomesFromNoise(
                (x, y, z, sampler) -> biomeHolder,
                level.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        level.getServer()
                .getPlayerList()
                .broadcastAll(
                        ClientboundChunksBiomesPacket.forChunks(List.of(chunk)),
                        level.dimension());
        int plantedTrees = plantForestTrees(level, chunk, level.getGameTime());

        ResourceKey<Biome> oldBiome = data.getCurrentBiome().orElse(null);
        data.setPreviousBiome(oldBiome);
        data.setCurrentBiome(targetBiome.get());
        data.clearRuntimeState();
        data.setLastEvaluationGameTime(level.getGameTime());
        ModNetworking.syncChunkToTracking(level, chunk);

        EcofluxConstants.LOGGER.info(
                "区块 {} 演替完成：{} -> {}，生成树木 {} 棵",
                chunk.getPos(),
                oldBiome == null ? "未知" : oldBiome.location(),
                targetBiome.get().location(),
                plantedTrees);
        return "区块 " + chunk.getPos() + " 已从 "
                + (oldBiome == null ? "未知" : oldBiome.location())
                + " 转化为 " + targetBiome.get().location()
                + "，生成树木 " + plantedTrees + " 棵。";
    }

    private static int plantForestTrees(ServerLevel level, LevelChunk chunk, long gameTime) {
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

    private record TreeBlocks(BlockState log, BlockState leaves) {
    }
}
