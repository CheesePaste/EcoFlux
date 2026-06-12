package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.s.ecoflux.world.ChunkSamplingHelper.class)
public abstract class ChunkSamplingHelperMixin {

    @Inject(method = "findSpawnPos", at = @At("HEAD"))
    private static void onEnter(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.push("spawner.find_pos");
    }

    @Inject(method = "findSpawnPos", at = @At("RETURN"))
    private static void onExit(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
