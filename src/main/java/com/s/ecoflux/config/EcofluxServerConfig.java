package com.s.ecoflux.config;

/**
 * Server-side mod configuration via the NeoForge config system.
 *
 * <p>Structure: defines a {@link net.neoforged.neoforge.common.ModConfigSpec} with
 * server-wide toggles for gradual tree growth and gradual plant growth, plus
 * performance tuning intervals. Config values are read via static accessor methods.
 * <p>Role in Ecoflux: provides server operators with a standard NeoForge config file
 * to toggle between gradual lifecycle growth and instant-mature modes at runtime,
 * and to tune processing intervals for performance.
 */

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EcofluxServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue GRADUAL_TREE_GROWTH;
    private static final ModConfigSpec.BooleanValue GRADUAL_PLANT_GROWTH;
    private static final ModConfigSpec.IntValue PRUNE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue OBSERVE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue SPAWN_INTERVAL_MIN_TICKS;
    private static final ModConfigSpec.IntValue SPAWN_INTERVAL_MAX_TICKS;

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
        builder.push("spawn");
        SPAWN_INTERVAL_MIN_TICKS = builder
                .comment("植物生成间隔的最小 tick 数。每次生成后，下次生成的间隔在 min 到 max 之间随机选取。应用于所有群系的所有演替路径。")
                .defineInRange("spawn_interval_min_ticks", 600, 20, 72000);
        SPAWN_INTERVAL_MAX_TICKS = builder
                .comment("植物生成间隔的最大 tick 数。实际间隔在 min 和 max 之间随机均匀分布。")
                .defineInRange("spawn_interval_max_ticks", 1800, 40, 72000);
        builder.pop();
        builder.push("performance");
        PRUNE_INTERVAL_TICKS = builder
                .comment("清理无效植物（已死亡、被破坏、被替换）的间隔 tick 数。值越小清理越及时但 CPU 开销越大。")
                .defineInRange("prune_interval_ticks", 120, 5, 1200);
        OBSERVE_INTERVAL_TICKS = builder
                .comment("重新观察每个植物生命周期阶段的间隔 tick 数。值越小阶段变化越流畅但 CPU 开销越大。")
                .defineInRange("observe_interval_ticks", 120, 5, 1200);
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

    public static int pruneIntervalTicks() {
        return PRUNE_INTERVAL_TICKS.get();
    }

    public static int observeIntervalTicks() {
        return OBSERVE_INTERVAL_TICKS.get();
    }

    public static int spawnIntervalMinTicks() {
        return SPAWN_INTERVAL_MIN_TICKS.get();
    }

    public static int spawnIntervalMaxTicks() {
        return SPAWN_INTERVAL_MAX_TICKS.get();
    }
}
