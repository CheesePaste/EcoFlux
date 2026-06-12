package com.s.ecoflux.mixin.perf;

import com.s.ecoflux.test.performance.PerformanceProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(com.s.ecoflux.attachment.SuccessionChunkData.class)
public abstract class SuccessionChunkDataMixin {

    @Inject(method = "serializeNBT", at = @At("HEAD"))
    private void onSerializeEnter(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.push("nbt.serialize");
    }

    @Inject(method = "serializeNBT", at = @At("RETURN"))
    private void onSerializeExit(CallbackInfoReturnable<?> cir) {
        PerformanceProfiler.INSTANCE.pop();
    }

    @Inject(method = "deserializeNBT", at = @At("HEAD"))
    private void onDeserializeEnter(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.push("nbt.deserialize");
    }

    @Inject(method = "deserializeNBT", at = @At("RETURN"))
    private void onDeserializeExit(CallbackInfo ci) {
        PerformanceProfiler.INSTANCE.pop();
    }
}
