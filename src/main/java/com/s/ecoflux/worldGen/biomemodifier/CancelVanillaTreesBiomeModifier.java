package com.s.ecoflux.worldgen.biomemodifier;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RootSystemConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public class CancelVanillaTreesBiomeModifier implements BiomeModifier {

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE) return;

        BiomeGenerationSettingsBuilder genSettings = builder.getGenerationSettings();

        for (GenerationStep.Decoration stage : GenerationStep.Decoration.values()) {
            var features = genSettings.getFeatures(stage);
            features.removeIf(holder -> isTreeFeature(holder.value()));
        }
    }

    private static boolean isTreeFeature(PlacedFeature placedFeature) {
        return placedFeature.getFeatures().anyMatch(fc -> {
            FeatureConfiguration config = fc.config();

            if (config instanceof TreeConfiguration) return true;
            if (config instanceof RootSystemConfiguration) return true;

            if (config instanceof RandomFeatureConfiguration rfc) {
                for (WeightedPlacedFeature wpf : rfc.features) {
                    if (isTreeFeature(wpf.feature.value())) return true;
                }
                return false;
            }

            if (config instanceof SimpleRandomFeatureConfiguration srfc) {
                for (Holder<PlacedFeature> holder : srfc.features) {
                    if (isTreeFeature(holder.value())) return true;
                }
                return false;
            }

            var innerOpt = fc.getFeatures().findFirst();
            if (innerOpt.isPresent()) {
                var inner = innerOpt.get();
                FeatureConfiguration innerConfig = inner.config();
                if (innerConfig instanceof TreeConfiguration) return true;
                if (innerConfig instanceof RootSystemConfiguration) return true;
                if (innerConfig instanceof RandomFeatureConfiguration rfc2) {
                    for (WeightedPlacedFeature wpf : rfc2.features) {
                        if (isTreeFeature(wpf.feature.value())) return true;
                    }
                }
            }

            return false;
        });
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return EcofluxBiomeModifiers.CANCEL_VANILLA_TREES.get();
    }
}
