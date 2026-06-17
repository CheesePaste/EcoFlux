package com.cp.ecoflux.network;

import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.EcofluxServerConfig;
import com.cp.ecoflux.init.ModAttachments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PanelDataHelper {

    /** Must match {@code SatelliteMapRenderer.CHUNK_GRID}. */
    private static final int MAP_CHUNK_GRID = 8;

    private static final Set<UUID> panelOpenPlayers =
            Collections.synchronizedSet(new HashSet<>());

    private PanelDataHelper() {}

    public static void markPanelOpen(ServerPlayer player) {
        panelOpenPlayers.add(player.getUUID());
    }

    public static void markPanelClosed(ServerPlayer player) {
        panelOpenPlayers.remove(player.getUUID());
    }

    public static boolean isPanelOpen(ServerPlayer player) {
        return panelOpenPlayers.contains(player.getUUID());
    }

    public static PanelDataPayload build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ChunkPos center = player.chunkPosition();

        double totalProgress = 0;
        int chunksWithPath = 0;
        int totalVeg = 0;
        List<ChunkDataEntry> entries = new ArrayList<>(9);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) continue;
                SuccessionChunkData d = c.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
                boolean hasPath = d.getActivePathId().isPresent();
                if (hasPath) {
                    totalProgress += d.getProgress();
                    chunksWithPath++;
                }
                totalVeg += d.getVegetationRecords().size();

                entries.add(new ChunkDataEntry(
                        cx, cz,
                        d.getCurrentBiome().map(k -> k.location()).orElse(null),
                        d.getTargetBiome().map(k -> k.location()).orElse(null),
                        d.getActivePathId().orElse(null),
                        d.getProgress(),
                        d.getContributingVegetationPoints(),
                        d.getConsumingValue(),
                        (int) d.countContributingVegetation(),
                        d.getVegetationRecords().size(),
                        d.isSuccessionDisabled()));
            }
        }

        // Scan 8×8 viewport for excluded chunks (for map tab red borders)
        int halfGrid = MAP_CHUNK_GRID / 2;
        List<ChunkPos> excludedChunks = new ArrayList<>();
        for (int dx = -halfGrid; dx < halfGrid; dx++) {
            for (int dz = -halfGrid; dz < halfGrid; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) continue;
                SuccessionChunkData d = c.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
                if (d.isSuccessionDisabled()) {
                    excludedChunks.add(new ChunkPos(cx, cz));
                }
            }
        }

        double avg = chunksWithPath > 0 ? totalProgress / chunksWithPath : Double.NaN;
        boolean enabled = !EcofluxServerConfig.disablePlantSpawning();

        return new PanelDataPayload(avg, chunksWithPath, totalVeg, enabled,
                List.copyOf(entries), List.copyOf(excludedChunks));
    }

    public static void sendFullSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, build(player));
    }

    public static void pushToAllOpen(ServerLevel level) {
        for (ServerPlayer sp : level.players()) {
            if (isPanelOpen(sp)) {
                PacketDistributor.sendToPlayer(sp, build(sp));
            }
        }
    }
}
