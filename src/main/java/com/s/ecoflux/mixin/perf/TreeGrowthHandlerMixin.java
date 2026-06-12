package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.s.ecoflux.plant.tree.TreeGrowthHandler.class)
public abstract class TreeGrowthHandlerMixin {

    @Inject(method = "tickAll", at = @At("HEAD"))
    private void onEnter(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.push("tree.tick_all");
    }

    @Inject(method = "tickAll", at = @At("RETURN"))
    private void onExit(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
