package com.cp.ecoflux.api.data;

/**
 * Enum defining the lifecycle stages every tracked plant progresses through.
 *
 * <p>Structure: seven stages — {@link #BORN} (just spawned, minimal points),
 * {@link #JUVENILE} (early sapling stage), {@link #GROWING} (actively
 * gaining points), {@link #MATURE} (peak contribution, full points),
 * {@link #AGING} (declining, triggers aging gate for succession),
 * {@link #DEAD} (removed from tracking), and {@link #TRANSFORMED}
 * (sapling-to-tree conversion in progress).
 *
 * <p>Role in Ecoflux: stored in {@link ActiveVegetationRecord#lifeStage()}
 * and used by adapters' {@code observe} methods to compute current point
 * values, by {@link com.cp.ecoflux.succession.SuccessionEvaluator} to gate
 * progress on aging vegetation, and by the client visual system to select
 * render scale/tint.
 */

public enum VegetationLifecycleStage {
    BORN,
    JUVENILE,
    GROWING,
    MATURE,
    AGING,
    DEAD,
    TRANSFORMED
}
