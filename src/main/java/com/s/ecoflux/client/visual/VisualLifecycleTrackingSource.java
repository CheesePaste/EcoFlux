package com.s.ecoflux.client.visual;

/**
 * Identifies the origin of a visual tracking entry.
 *
 * <p>Structure: a two-value enum: {@code MANUAL} for entries created by debug
 * commands ({@code /ecoflux visual start}), and {@code VEGETATION_SYSTEM} for
 * entries synced from the server-side vegetation tracker via
 * {@code VegetationVisualChunkSyncPayload}.
 * <p>Role in Ecoflux: prevents server chunk sync from removing manually-tracked
 * entries. During {@code syncVegetationChunk()}, only {@code VEGETATION_SYSTEM}
 * entries are pruned; {@code MANUAL} entries persist independently.
 */

public enum VisualLifecycleTrackingSource {
    MANUAL,
    VEGETATION_SYSTEM,
    /** Shader test mode — cyclic scale animation driven by client tick, no server sync needed. */
    DEMO
}
