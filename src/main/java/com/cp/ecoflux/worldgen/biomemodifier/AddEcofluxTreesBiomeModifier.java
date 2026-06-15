package com.cp.ecoflux.worldgen.biomemodifier;

import com.mojang.serialization.MapCodec;
import com.cp.ecoflux.worldgen.EcofluxFeatures;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class AddEcofluxTreesBiomeModifier implements BiomeModifier {

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) return;

        var placedFeatures = ServerLifecycleHooks.getCurrentServer()
                .registryAccess().registryOrThrow(Registries.PLACED_FEATURE);

        builder.getGenerationSettings().addFeature(
                GenerationStep.Decoration.VEGETAL_DECORATION,
                placedFeatures.getHolderOrThrow(EcofluxFeatures.ECOFLUX_TREE_PLACED));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return EcofluxBiomeModifiers.ADD_ECoflUX_TREES.get();
    }
}
