package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.s.ecoflux.plant.tree.morphology.TreeMorphology.class)
public abstract class TreeMorphologyMixin {

    @Inject(method = "growStage", at = @At("HEAD"))
    private static void onEnter(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.push("tree.grow_stage");
    }

    @Inject(method = "growStage", at = @At("RETURN"))
    private static void onExit(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
