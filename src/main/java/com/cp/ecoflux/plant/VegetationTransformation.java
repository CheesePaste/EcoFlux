package com.cp.ecoflux.plant;

import net.minecraft.resources.ResourceLocation;

public record VegetationTransformation(
        ResourceLocation targetVegetationId,
        ResourceLocation targetAdapterType,
        VegetationLifecycleStage targetStage,
        int targetBasePointValue,
        int targetCurrentPointValue,
        long targetExpireGameTime) {
}
