package com.cp.ecoflux.mixin;

/**
 * Server-side mixin intercepting {@code SaplingBlock.advanceTree()}.
 *
 * <p>Structure: {@code @Inject} at {@code HEAD} with {@code cancellable = true}.
 * When the sapling is tracked by Ecoflux and gradual tree growth is enabled,
 * cancels vanilla instant growth and delegates to
 * {@code TreeGrowthHandler} for staged growth.
 * <p>Role in Ecoflux: replaces vanilla instant tree growth with the mod's
 * multi-stage growth system on the server side.
 */

import com.cp.ecoflux.attachment.ActiveVegetationRecord;
import com.cp.ecoflux.attachment.SuccessionChunkData;
import com.cp.ecoflux.config.EcofluxServerConfig;
import com.cp.ecoflux.init.ModAttachments;
import com.cp.ecoflux.plant.SaplingAdapter;
import com.cp.ecoflux.plant.tree.TreeGrowthHandler;
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
        if (state.hasProperty(BlockStateProperties.STAGE) && state.getValue(BlockStateProperties.STAGE) == 0) {
            return;
        }

        LevelChunk chunk = level.getChunkAt(pos);
        SuccessionChunkData data = chunk.getData(ModAttachments.SUCCESSION_CHUNK_DATA);
        ActiveVegetationRecord record = data.getVegetationRecords().get(pos);
        if (record != null && SaplingAdapter.TYPE_ID.equals(record.adapterType())) {
            if (!EcofluxServerConfig.gradualTreeGrowth()) {
                return;
            }
            ci.cancel();
            com.cp.ecoflux.EcofluxConstants.LOGGER.info(
                    "[Ecoflux] SaplingBlockMixin: INTERCEPTED at {}, forcing advance stage", pos);
            TreeGrowthHandler handler = TreeGrowthHandler.INSTANCE;
            handler.interceptGrowth(level, pos, record);
            handler.forceAdvanceStage(level, pos);
        }
    }
}
