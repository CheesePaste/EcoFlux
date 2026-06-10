package com.s.ecoflux.mixin;

import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.plant.tree.TreeGrowthHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MushroomBlock.class)
public abstract class MushroomBlockMixin {

    @Inject(
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ecoflux$interceptMushroomGrowth(
            ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
            CallbackInfo ci) {
        if (!EcofluxServerConfig.gradualTreeGrowth()) {
            return;
        }
        ci.cancel();
        TreeGrowthHandler.INSTANCE.interceptMushroomGrowth(level, pos, state);
        TreeGrowthHandler.INSTANCE.forceAdvanceStage(level, pos);
    }
}
