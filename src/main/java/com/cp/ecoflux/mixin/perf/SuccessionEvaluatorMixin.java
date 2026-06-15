package com.cp.ecoflux.mixin.perf;

import com.cp.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.cp.ecoflux.succession.SuccessionEvaluator.class)
public abstract class SuccessionEvaluatorMixin {

    @Inject(method = "evaluate", at = @At("HEAD"))
    private static void onEnter(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.push("evaluator.evaluate");
    }

    @Inject(method = "evaluate", at = @At("RETURN"))
    private static void onExit(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
