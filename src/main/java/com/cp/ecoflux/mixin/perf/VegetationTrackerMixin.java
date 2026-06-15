package com.cp.ecoflux.mixin.perf;

import com.cp.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.cp.ecoflux.plant.VegetationTracker.class)
public abstract class VegetationTrackerMixin {

    @Inject(method = "observeChunk", at = @At("HEAD"))
    private void onObserveChunkEnter(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.push("tracker.observe_chunk");
    }

    @Inject(method = "observeChunk", at = @At("RETURN"))
    private void onObserveChunkExit(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }

    @Inject(method = "observeTrackedInternal", at = @At("HEAD"))
    private void onObserveInternalEnter(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.push("tracker.observe_internal");
    }

    @Inject(method = "observeTrackedInternal", at = @At("RETURN"))
    private void onObserveInternalExit(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }

    @Inject(method = "trackAt", at = @At("HEAD"))
    private void onTrackAtEnter(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.push("tracker.track");
    }

    @Inject(method = "trackAt", at = @At("RETURN"))
    private void onTrackAtExit(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
