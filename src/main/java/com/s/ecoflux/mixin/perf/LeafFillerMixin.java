package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.s.ecoflux.plant.tree.morphology.LeafFiller.class)
public abstract class LeafFillerMixin {

    @Inject(method = "fillLeaves", at = @At("HEAD"))
    private static void onEnter(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.push("tree.leaf_fill");
    }

    @Inject(method = "fillLeaves", at = @At("RETURN"))
    private static void onExit(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
