package com.s.ecoflux.init;

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.network.ModNetworking;
import com.s.ecoflux.plant.VegetationTracker;
import com.s.ecoflux.plant.VegetationTypeAdapter;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class ModPlayerEvents {
    private ModPlayerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ModPlayerEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ModPlayerEvents::onBlockBroken);
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();
        LevelChunk chunk = getChunk(level, pos);
        if (chunk == null) {
            return;
        }

        Optional<VegetationTypeAdapter> adapter = VegetationTracker.INSTANCE.findAdapter(state);
        if (adapter.isEmpty()) {
            return;
        }

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = adapter.get().captureBirth(
                level,
                pos,
                state,
                level.getGameTime(),
                chunkData.getCurrentBiome().map(key -> key.location()),
                chunkData.getActivePathId());
        chunkData.trackVegetation(record);
        ModNetworking.syncChunkToTracking(level, chunk);
    }

    private static void onBlockBroken(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) levelAccessor;
        BlockPos pos = event.getPos();
        LevelChunk chunk = getChunk(level, pos);
        if (chunk == null) {
            return;
        }

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        if (!chunkData.getVegetationRecords().containsKey(pos)) {
            return;
        }

        chunkData.removeVegetation(pos);
        ModNetworking.syncChunkToTracking(level, chunk);
    }

    private static LevelChunk getChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
