package com.s.ecoflux.init;

/**
 * Player-triggered block event handlers for vegetation tracking.
 *
 * <p>Structure: listens to {@code BlockEvent.EntityPlaceEvent} and
 * {@code BlockEvent.BreakEvent} on the NeoForge event bus. On place it calls
 * {@code VegetationTracker.trackAt}; on break it calls
 * {@code VegetationTracker.untrack}.
 * <p>Role in Ecoflux: keeps vegetation records in sync when players manually
 * place or destroy plants.
 */

import com.s.ecoflux.plant.VegetationTracker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
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
        LevelChunk chunk = getChunk(level, pos);
        if (chunk == null) {
            return;
        }

        VegetationTracker.INSTANCE.trackAt(level, chunk, pos, Optional.empty(), Optional.empty());
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

        VegetationTracker.INSTANCE.untrack(chunk, pos);
    }

    private static LevelChunk getChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
