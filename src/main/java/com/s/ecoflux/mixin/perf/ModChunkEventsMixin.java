package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.s.ecoflux.init.ModChunkEvents.class)
public abstract class ModChunkEventsMixin {

    @Inject(method = "processChunkSet", at = @At("HEAD"))
    private static void onChunkSetEnter(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.push("tick.chunk_set");
    }

    @Inject(method = "processChunkSet", at = @At("RETURN"))
    private static void onChunkSetExit(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.pop();
    }

    @Inject(method = "processTreeGrowth", at = @At("HEAD"))
    private static void onTreeGrowthEnter(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.push("tick.tree_growth");
    }

    @Inject(method = "processTreeGrowth", at = @At("RETURN"))
    private static void onTreeGrowthExit(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
