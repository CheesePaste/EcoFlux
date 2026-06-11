package com.s.ecoflux.plant.tree.morphology;

/**
 * Enum classifying the hierarchy level of a {@link SkeletonNode} in a tree skeleton.
 *
 * <p>Structure: Four levels -- {@code TRUNK} (vertical core), {@code PRIMARY_BRANCH}
 * (first-order branches from trunk), {@code SECONDARY_BRANCH} (branches from primary
 * branch midpoints), {@code TWIG} (fine terminal branches, currently unused for leaf
 * placement but reserved for future detail).
 *
 * <p>Role in Ecoflux: Used by {@link SkeletonGenerator} to tag nodes during generation,
 * by {@link LeafFiller} to distinguish trunk vs. branch distance computation, and by
 * {@link TreeMorphology#growStage} to skip twigs when placing log blocks.
 */

public enum NodeType {
    TRUNK,
    PRIMARY_BRANCH,
    SECONDARY_BRANCH,
    TWIG
}
