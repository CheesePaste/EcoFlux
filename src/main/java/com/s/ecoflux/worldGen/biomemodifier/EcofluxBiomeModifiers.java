package com.s.ecoflux.worldGen.biomemodifier;

import com.mojang.serialization.MapCodec;
import com.s.ecoflux.EcofluxConstants;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class EcofluxBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, EcofluxConstants.MOD_ID);

    public static final Supplier<MapCodec<CancelVanillaTreesBiomeModifier>> CANCEL_VANILLA_TREES =
            BIOME_MODIFIER_SERIALIZERS.register("cancel_vanilla_trees",
                    () -> MapCodec.unit(CancelVanillaTreesBiomeModifier::new));

    public static final Supplier<MapCodec<AddEcofluxTreesBiomeModifier>> ADD_ECoflUX_TREES =
            BIOME_MODIFIER_SERIALIZERS.register("add_ecoflux_trees",
                    () -> MapCodec.unit(AddEcofluxTreesBiomeModifier::new));

    private EcofluxBiomeModifiers() {}

    public static void register(IEventBus modEventBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
