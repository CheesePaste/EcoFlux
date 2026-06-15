package com.cp.ecoflux.test.sample;

import com.mojang.serialization.MapCodec;
import com.cp.ecoflux.EcofluxConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SamplingBiomeSources {
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> REG =
            DeferredRegister.create(Registries.BIOME_SOURCE, EcofluxConstants.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<SamplingBiomeSource>> SAMPLING =
            REG.register("sampling", () -> SamplingBiomeSource.CODEC);

    private SamplingBiomeSources() {}

    public static void register(IEventBus modEventBus) {
        REG.register(modEventBus);
    }
}
