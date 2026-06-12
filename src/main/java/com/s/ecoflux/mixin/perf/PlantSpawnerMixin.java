package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.s.ecoflux.plant.PlantSpawner.class)
public abstract class PlantSpawnerMixin {

    @Inject(method = "pruneInvalidPlants", at = @At("HEAD"))
    private static void onPruneEnter(CallbackInfoReturnable<Integer> cir) {
        PerformanceProfiler.INSTANCE.push("spawner.prune");
    }

    @Inject(method = "pruneInvalidPlants", at = @At("RETURN"))
    private static void onPruneExit(CallbackInfoReturnable<Integer> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }

    @Inject(method = "trySpawnPlant", at = @At("HEAD"))
    private static void onSpawnEnter(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.push("spawner.try_spawn");
    }

    @Inject(method = "trySpawnPlant", at = @At("RETURN"))
    private static void onSpawnExit(CallbackInfoReturnable<String> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
