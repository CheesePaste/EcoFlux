package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.s.ecoflux.succession.SuccessionService.class)
public abstract class SuccessionServiceMixin {

    @Inject(method = "doPipeline", at = @At("HEAD"))
    private static void onEnter(CallbackInfoReturnable<String> ci) {
        PerformanceProfiler.INSTANCE.push("pipeline.total");
    }

    @Inject(method = "doPipeline", at = @At("RETURN"))
    private static void onExit(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }

    @Inject(method = "initializeChunk", at = @At("HEAD"))
    private static void onInitEnter(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.push("tick.chunk_init");
    }

    @Inject(method = "initializeChunk", at = @At("RETURN"))
    private static void onInitExit(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
