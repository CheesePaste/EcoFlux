package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.s.ecoflux.plant.tree.morphology.SkeletonGenerator.class)
public abstract class SkeletonGeneratorMixin {

    @Inject(method = "generate", at = @At("HEAD"))
    private static void onEnter(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.push("tree.skeleton");
    }

    @Inject(method = "generate", at = @At("RETURN"))
    private static void onExit(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
