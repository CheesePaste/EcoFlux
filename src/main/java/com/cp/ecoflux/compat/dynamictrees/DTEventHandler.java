package com.cp.ecoflux.compat.dynamictrees;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.api.data.ActiveVegetationRecord;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.api.config.PlantDefinition;
import com.cp.ecoflux.init.ModAttachments;
import com.cp.ecoflux.network.ModNetworking;
import com.cp.ecoflux.api.data.VegetationLifecycleStage;
import com.cp.ecoflux.plant.adapters.SaplingAdapter;
import com.cp.ecoflux.plant.adapters.dynamictrees.DTTreeAdapter;
import com.dtteam.dynamictrees.event.SeedVoluntaryPlantEvent;
import com.dtteam.dynamictrees.event.SpeciesPostGenerationEvent;
import com.dtteam.dynamictrees.event.TransitionSaplingToTreeEvent;
import com.dtteam.dynamictrees.tree.species.Species;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Listens to Dynamic Trees events and registers DT trees in EcoFlux's
 * vegetation lifecycle tracking system.
 */
public final class DTEventHandler {

    private DTEventHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DTEventHandler::onSpeciesPostGeneration);
        NeoForge.EVENT_BUS.addListener(DTEventHandler::onTransitionSaplingToTree);
        NeoForge.EVENT_BUS.addListener(DTEventHandler::onSeedVoluntaryPlant);
    }

    /** World-gen tree placement: register in VegetationTracker. */
    private static void onSpeciesPostGeneration(SpeciesPostGenerationEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Species species = event.getSpecies();
        BlockPos rootPos = event.getRootPos();
        PlantDefinition plantDef = DTSpeciesMapping.resolve(species);

        EcofluxConstants.LOGGER.info("[Ecoflux-DT] SpeciesPostGeneration: species={} rootPos={} plantId={}",
                species.getRegistryName(), rootPos.toShortString(), plantDef.plantId());

        int chunkX = rootPos.getX() >> 4;
        int chunkZ = rootPos.getZ() >> 4;
        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            EcofluxConstants.LOGGER.info("[Ecoflux-DT] SpeciesPostGeneration: chunk null for {}", rootPos.toShortString());
            return;
        }

        ActiveVegetationRecord record = DTTreeAdapter.INSTANCE.captureBirth(
                serverLevel, rootPos, serverLevel.getBlockState(rootPos),
                serverLevel.getGameTime(),
                Optional.empty(), Optional.empty(), plantDef);

        chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA).trackVegetation(record);
        ModNetworking.syncChunkToTracking(serverLevel, chunk);
        com.cp.ecoflux.init.ModChunkEvents.markChunkHasTreeStructure(serverLevel, chunk.getPos().toLong());

        EcofluxConstants.LOGGER.debug("[Ecoflux-DT] Registered world-gen tree: species={} at {}",
                species.getRegistryName(), rootPos);
    }

    /** Sapling → tree transition: register in VegetationTracker. */
    private static void onTransitionSaplingToTree(TransitionSaplingToTreeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Species species = event.getSpecies();
        BlockPos pos = event.getPos();
        PlantDefinition plantDef = DTSpeciesMapping.resolve(species);

        EcofluxConstants.LOGGER.info("[Ecoflux-DT] TransitionSaplingToTree: species={} pos={} plantId={}",
                species.getRegistryName(), pos.toShortString(), plantDef.plantId());

        // Remove any prior sapling record at this position
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            EcofluxConstants.LOGGER.info("[Ecoflux-DT] TransitionSaplingToTree: chunk null for {}", pos.toShortString());
            return;
        }

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        chunkData.removeVegetation(pos);

        // The root position after transition is slightly different — DT places rooty dirt
        // below the sapling position. The sapling's old position becomes a branch block.
        // We'll register at the sapling pos for now; the DTTreeAdapter will find the root
        // during observation.
        ActiveVegetationRecord record = DTTreeAdapter.INSTANCE.captureBirth(
                serverLevel, pos, serverLevel.getBlockState(pos),
                serverLevel.getGameTime(),
                chunkData.getCurrentBiome().map(key -> key.location()),
                chunkData.getActivePathId(), plantDef);

        chunkData.trackVegetation(record);
        ModNetworking.syncChunkToTracking(serverLevel, chunk);
        com.cp.ecoflux.init.ModChunkEvents.markChunkHasTreeStructure(serverLevel, chunk.getPos().toLong());

        EcofluxConstants.LOGGER.debug("[Ecoflux-DT] Registered sapling-grown tree: species={} at {}",
                species.getRegistryName(), pos);
    }

    /**
     * Intercepts DT voluntary seed planting to respect Ecoflux's per-chunk plant capacity.
     * If under capacity, pre-registers a sapling record so the plant count reflects the new
     * sapling immediately. If at capacity, the seed is prevented from planting.
     */
    private static void onSeedVoluntaryPlant(SeedVoluntaryPlantEvent event) {
        if (!(event.getEntityItem().level() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) return;

        SuccessionChunkData chunkData = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        int maxCount = chunkData.getMaxPlantCount();
        // Only enforce capacity in EcoFlux-initialized chunks (maxCount > 0)
        if (maxCount <= 0) return;

        int currentCount = chunkData.getCurrentPlantCount();
        if (currentCount >= maxCount) {
            event.setCanceled(true);
            EcofluxConstants.LOGGER.debug("[Ecoflux-DT] Blocked seed planting at {} (chunk plant count {}/{})",
                    pos.toShortString(), currentCount, maxCount);
            return;
        }

        // Pre-register a sapling record — will be replaced by tree record when
        // TransitionSaplingToTreeEvent fires.
        PlantDefinition plantDef = DTSpeciesMapping.resolve(event.getSpecies());
        ActiveVegetationRecord saplingRecord = new ActiveVegetationRecord(
                plantDef.plantId(),
                SaplingAdapter.TYPE_ID,
                pos.immutable(),
                VegetationLifecycleStage.BORN,
                serverLevel.getGameTime(),
                serverLevel.getGameTime(),
                serverLevel.getGameTime() + 1200L, // short-lived; DT will transition it to tree soon
                plantDef.pointValue(),
                1,
                chunkData.getCurrentBiome().map(key -> key.location()).orElse(null),
                chunkData.getActivePathId().orElse(null),
                null);
        chunkData.trackVegetation(saplingRecord);
        EcofluxConstants.LOGGER.debug("[Ecoflux-DT] Pre-registered seed sapling: plantId={} at {} (chunk count {}/{})",
                plantDef.plantId(), pos.toShortString(), currentCount + 1, maxCount);
    }
}
