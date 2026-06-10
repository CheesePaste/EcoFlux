package com.s.ecoflux.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EcofluxServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue GRADUAL_TREE_GROWTH;
    private static final ModConfigSpec.BooleanValue GRADUAL_PLANT_GROWTH;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("tree_growth");
        GRADUAL_TREE_GROWTH = builder
                .comment("为 true 时树木分阶段逐渐生长；为 false 时树苗瞬间长成原版树木，但死亡仍由 Ecoflux 控制。")
                .define("gradual_tree_growth", true);
        builder.pop();
        builder.push("plant_growth");
        GRADUAL_PLANT_GROWTH = builder
                .comment("为 true 时植物经历完整的生命周期阶段（出生→生长→成熟→衰老），点值和视觉缩放逐渐变化；为 false 时植物立即成熟，跳过逐渐生长阶段，性能更友好，但死亡仍由 Ecoflux 控制。")
                .define("gradual_plant_growth", true);
        builder.pop();
        SPEC = builder.build();
    }

    private EcofluxServerConfig() {
    }

    public static boolean gradualTreeGrowth() {
        return GRADUAL_TREE_GROWTH.get();
    }

    public static boolean gradualPlantGrowth() {
        return GRADUAL_PLANT_GROWTH.get();
    }
}
