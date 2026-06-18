package com.cp.ecoflux.api;

import com.cp.ecoflux.api.adapter.VegetationTypeAdapter;

/**
 * Capabilities optionally declared by a {@link VegetationTypeAdapter}.
 *
 * <p>The observer pipeline checks these capabilities instead of comparing adapter
 * type IDs, allowing external mod adapters to participate without modifying core code.
 */
public enum VegetationAdapterCapability {
    /** Sapling → triggers the age-check → interceptor/growth branch. */
    IS_SAPLING,

    /** Multi-block structure (tree) → BFS rebuild + progressive decay on death. */
    HAS_STRUCTURE,

    /** 1×1 simple plant → trivial lifecycle, no structural decay. */
    IS_SIMPLE_PLANT,

    /** Long lifespan (decayTicks &gt; 36000) → relaxed advance-stage thresholds. */
    LONG_LIFECYCLE,
}
