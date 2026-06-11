package com.s.ecoflux.plant;

/**
 * Classification enum for vegetation types used by adapters and records.
 *
 * <p>Structure: six categories — {@link #GROUND_COVER} (grass, ferns, dead
 * bushes), {@link #FLOWER} (small and tall flowers), {@link #MUSHROOM}
 * (brown/red mushrooms), {@link #SAPLING} (tree saplings/propagules),
 * {@link #TREE} (mature logs and leaves), and {@link #OTHER} (catch-all for
 * unclassified plants).
 *
 * <p>Role in Ecoflux: stored in {@link ActiveVegetationRecord} and used to
 * differentiate lifecycle behavior, point values, visual treatment, and
 * succession weighting across plant types. Derived by adapters (e.g.
 * {@link SimplePlantAdapter#category()} inspects block tags to distinguish
 * FLOWER from GROUND_COVER) and propagated through transformations.
 */

public enum VegetationCategory {
    GROUND_COVER,
    FLOWER,
    MUSHROOM,
    SAPLING,
    TREE,
    OTHER
}
