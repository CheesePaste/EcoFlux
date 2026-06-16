package com.cp.ecoflux.network;

/**
 * Server-side helper that builds {@link PanelDataPayload} from
 * {@link com.cp.ecoflux.attachment.SuccessionChunkData} and tracks which
 * players currently have the panel open (for push-on-evaluate).
 */

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.init.ModAttachments;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PanelDataHelper {

    private static final Set<UUID> panelOpenPlayers =
            Collections.synchronizedSet(new HashSet<>());

    private PanelDataHelper() {}

    // ── Panel-open tracking ─────────────────────────────────────────

    public static void markPanelOpen(ServerPlayer player) {
        panelOpenPlayers.add(player.getUUID());
    }

    public static void markPanelClosed(ServerPlayer player) {
        panelOpenPlayers.remove(player.getUUID());
    }

    public static boolean isPanelOpen(ServerPlayer player) {
        return panelOpenPlayers.contains(player.getUUID());
    }

    // ── Data builders ────────────────────────────────────────────────

    /**
     * Builds a full {@link PanelDataPayload} for the player's current chunk,
     * computing the global average progress from all loaded chunks.
     */
    public static PanelDataPayload buildFullSync(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ChunkPos chunkPos = player.chunkPosition();
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);

        double globalAvg = computeGlobalAverage(level);

        return buildPayload(level, chunkPos, data, globalAvg);
    }

    /** Computes global average progress across all loaded chunks. */
    public static double computeGlobalAverage(ServerLevel level) {
        long[] chunkPositions = com.cp.ecoflux.init.ModChunkEvents.snapshotLoadedChunks(level.dimension());
        if (chunkPositions.length == 0) return 0.0;

        double total = 0;
        int count = 0;
        for (long pos : chunkPositions) {
            LevelChunk c = level.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(pos),
                    net.minecraft.world.level.ChunkPos.getZ(pos));
            if (c == null) continue;
            SuccessionChunkData d = c.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
            if (d.getActivePathId().isPresent()) {
                total += d.getProgress();
                count++;
            }
        }
        return count > 0 ? total / count : 0.0;
    }

    /**
     * Builds a delta payload (no global average recalc) for the given chunk.
     */
    public static PanelDataPayload buildDelta(ServerLevel level, LevelChunk chunk) {
        SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        return buildPayload(level, chunk.getPos(), data, Double.NaN);
    }

    private static PanelDataPayload buildPayload(
            ServerLevel level, ChunkPos pos, SuccessionChunkData data, double globalAvg) {
        return new PanelDataPayload(
                globalAvg,
                pos.x,
                pos.z,
                data.getCurrentBiome().map(key -> key.location()).orElse(null),
                data.getTargetBiome().map(key -> key.location()).orElse(null),
                data.getActivePathId().orElse(null),
                data.getProgress(),
                data.getContributingVegetationPoints(),
                data.getConsumingValue(),
                (int) data.countContributingVegetation(),
                data.getVegetationRecords().size(),
                false // TODO: successionDisabled field not yet added to SuccessionChunkData
        );
    }

    // ── Push ─────────────────────────────────────────────────────────

    /** Push delta to a player if they have the panel open. */
    public static void pushDeltaIfOpen(ServerPlayer player, ServerLevel level, LevelChunk chunk) {
        if (isPanelOpen(player)) {
            PacketDistributor.sendToPlayer(player, buildDelta(level, chunk));
        }
    }
}
