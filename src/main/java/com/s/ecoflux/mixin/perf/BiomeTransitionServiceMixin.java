package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.s.ecoflux.succession.BiomeTransitionService.class)
public abstract class BiomeTransitionServiceMixin {

    @Inject(method = "applyTransition", at = @At("HEAD"))
    private static void onTransitionEnter(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.push("transition.apply");
    }

    @Inject(method = "applyTransition", at = @At("RETURN"))
    private static void onTransitionExit(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }

    @Inject(method = "applyRegression", at = @At("HEAD"))
    private static void onRegressionEnter(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.push("transition.regression");
    }

    @Inject(method = "applyRegression", at = @At("RETURN"))
    private static void onRegressionExit(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
