package com.cp.ecoflux.worldgen;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.worldgen.feature.EcofluxTreeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EcofluxFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, EcofluxConstants.MOD_ID);

    public static final DeferredHolder<Feature<?>, EcofluxTreeFeature> ECOFLUX_TREE_FEATURE =
            FEATURES.register("ecoflux_tree", EcofluxTreeFeature::new);

    /** ResourceKey for the placed feature defined in data/ecoflux/worldgen/placed_feature/ecoflux_trees.json */
    public static final ResourceKey<PlacedFeature> ECOFLUX_TREE_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, EcofluxConstants.id("ecoflux_trees"));

    private EcofluxFeatures() {}

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
