package com.cp.ecoflux.compat.dynamictrees;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.config.SuccessionSpeedConfig;
import com.cp.ecoflux.plant.SaplingGrowthInterceptors;
import com.cp.ecoflux.plant.adapters.dynamictrees.DTTreeAdapter;
import com.cp.ecoflux.plant.VegetationTracker;
import com.dtteam.dynamictrees.config.DTConfigs;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Dynamic Trees compatibility entry point.
 * All DT class references are confined to this package for safe class loading
 * when DT is not installed.
 */
public final class DTCompat {

    private static boolean initialized;
    private static boolean speedSynced;
    /** DT's original treeGrowthMultiplier, stored before Ecoflux overrides it. */
    private static double dtBaseGrowthMultiplier = -1.0;

    private DTCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("dynamictrees");
    }

    public static void init() {
        if (initialized || !isLoaded()) return;
        initialized = true;

        EcofluxConstants.LOGGER.info("[Ecoflux] Dynamic Trees detected — enabling compatibility mode");

        // Register sapling growth interceptor (DT handles vanilla sapling → tree)
        SaplingGrowthInterceptors.register(DTSaplingHandler.INSTANCE);

        // Register DT-specific vegetation adapter
        VegetationTracker.INSTANCE.addAdapter(DTTreeAdapter.INSTANCE);

        // Register DT event listeners (worldgen + sapling tracking)
        DTEventHandler.register();

        // Defer speed sync until DT config is loaded (server about to start)
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            if (!speedSynced) {
                speedSynced = true;
                SuccessionSpeedConfig.addListener(DTCompat::syncGrowthSpeed);
            }
            syncGrowthSpeed();
        });
    }

    /**
     * Adjusts DT's {@code treeGrowthMultiplier} so DT tree growth scales with
     * Ecoflux's {@link SuccessionSpeedConfig}. Stores the original DT value on
     * first call and restores it when Ecoflux speed is 1.0.
     */
    public static void syncGrowthSpeed() {
        if (!initialized) return;

        double dtCurrent = DTConfigs.SERVER.treeGrowthMultiplier.get();
        if (dtBaseGrowthMultiplier < 0) {
            dtBaseGrowthMultiplier = dtCurrent;
        }

        float ecofluxSpeed = SuccessionSpeedConfig.getSpeedMultiplier();
        double target = dtBaseGrowthMultiplier * ecofluxSpeed;

        if (Math.abs(dtCurrent - target) > 0.001) {
            DTConfigs.SERVER.treeGrowthMultiplier.set(target);
            EcofluxConstants.LOGGER.info("[Ecoflux-DT] Synced DT treeGrowthMultiplier: {} → {} (Ecoflux speed={}x)",
                    String.format("%.2f", dtCurrent),
                    String.format("%.2f", target),
                    String.format("%.1f", ecofluxSpeed));
        }
    }
}
