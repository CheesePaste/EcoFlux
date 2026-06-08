package com.s.ecoflux.mixin;

import com.s.ecoflux.attachment.ActiveVegetationRecord;
import com.s.ecoflux.attachment.SuccessionChunkData;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.plant.SaplingAdapter;
import com.s.ecoflux.plant.tree.TreeGrowthHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin {

    @Inject(
            method = "advanceTree(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ecoflux$interceptSaplingGrowth(
            ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
            CallbackInfo ci) {
        int stage = state.hasProperty(BlockStateProperties.STAGE) ? state.getValue(BlockStateProperties.STAGE) : -1;
        com.s.ecoflux.EcofluxConstants.LOGGER.info(
                "[Ecoflux] SaplingBlockMixin: advanceTree called at {} stage={}", pos, stage);

        if (state.hasProperty(BlockStateProperties.STAGE) && state.getValue(BlockStateProperties.STAGE) == 0) {
            com.s.ecoflux.EcofluxConstants.LOGGER.info(
                    "[Ecoflux] SaplingBlockMixin: STAGE=0, passing through vanilla at {}", pos);
            return;
        }

        LevelChunk chunk = level.getChunkAt(pos);
        SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = data.getVegetationRecords().get(pos);
        com.s.ecoflux.EcofluxConstants.LOGGER.info(
                "[Ecoflux] SaplingBlockMixin: pos={}, tracked={}, adapterType={}",
                pos, record != null, record != null ? record.adapterType() : "N/A");

        if (record != null && SaplingAdapter.TYPE_ID.equals(record.adapterType())) {
            ci.cancel();
            com.s.ecoflux.EcofluxConstants.LOGGER.info(
                    "[Ecoflux] SaplingBlockMixin: INTERCEPTED! Cancelling vanilla growth at {}", pos);
            TreeGrowthHandler.INSTANCE.interceptGrowth(level, pos, record);
        } else {
            com.s.ecoflux.EcofluxConstants.LOGGER.info(
                    "[Ecoflux] SaplingBlockMixin: NOT tracked as sapling, letting vanilla handle at {}", pos);
        }
    }
}
