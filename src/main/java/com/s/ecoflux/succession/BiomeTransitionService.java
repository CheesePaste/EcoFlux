package com.s.ecoflux.succession;

/**
 * Biome replacement service for chunk succession transitions and regressions.
 *
 * <p>Structure: static utility class providing {@code applyTransition()} to
 * advance a chunk to its target biome and {@code applyRegression()} to revert a
 * chunk to its fallback biome. Both methods call
 * {@code ChunkAccess.fillBiomesFromNoise()} to overwrite the chunk's biome data,
 * broadcast {@code ClientboundChunksBiomesPacket} to update all clients, soft-reset
 * chunk runtime state (preserving existing vegetation and tree growth sessions),
 * re-resolve the succession target for the new biome, and push a visual sync via
 * {@code ModNetworking.syncChunkToTracking()}.
 *
 * <p>Role in Ecoflux: the final step in the succession pipeline, invoked by
 * {@code SuccessionService} when evaluation determines a chunk has reached or
 * fallen below the transition/regression threshold.
 */

import com.s.ecoflux.EcofluxConstants;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.network.ModNetworking;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

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
        chunkData.softReset();
        chunkData.setLastEvaluationGameTime(level.getGameTime());
        SuccessionTargetResolver.resolveTarget(chunk);
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
        ResourceKey<Biome> oldBiome = data.getCurrentBiome().orElse(null);
        data.setPreviousBiome(oldBiome);
        data.setCurrentBiome(targetBiome.get());
        data.softReset();
        data.setLastEvaluationGameTime(level.getGameTime());
        SuccessionTargetResolver.resolveTarget(chunk);
        ModNetworking.syncChunkToTracking(level, chunk);

        EcofluxConstants.LOGGER.info(
                "区块 {} 演替完成：{} -> {}",
                chunk.getPos(),
                oldBiome == null ? "未知" : oldBiome.location(),
                targetBiome.get().location());
        return "区块 " + chunk.getPos() + " 已从 "
                + (oldBiome == null ? "未知" : oldBiome.location())
                + " 转化为 " + targetBiome.get().location() + "。";
    }
}
