package com.s.ecoflux.plant.tree.morphology;

public final class MorphologyPresets {

    private MorphologyPresets() {}

    public static MorphologyParams oak() {
        return new MorphologyParams(
                8, 13, 3600,
                false,
                5f,
                5, 9, 0.32f, 0.85f,
                30f, 55f, 0.60f,
                true, 0.5f, 3,
                CanopyEnvelope.CanopyType.ELLIPSOID,
                0.30, 0.48, -0.08,
                1.14, 0.25, 0.55,
                0, 3.0, 4
        );
    }

    public static MorphologyParams birch() {
        return new MorphologyParams(
                10, 16, 2400,
                false,
                3f,
                0, 3, 0.72f, 0.92f,
                55f, 70f, 0.30f,
                false, 0, 0,
                CanopyEnvelope.CanopyType.TALL_ELLIPSOID,
                1.6, 4.5, -0.12,
                0.85, 0.30, 0.78,
                0, 1.8, 4
        );
    }

    public static MorphologyParams spruce() {
        return new MorphologyParams(
                13, 22, 4800,
                false,
                0f,
                12, 20, 0.18f, 1.0f,
                5f, 20f, 0.48f,
                false, 0, 0,
                CanopyEnvelope.CanopyType.CONE,
                0.32, 0, 0,
                1.14, 0.10, 0.18,
                0, 2.2, 4
        );
    }

    public static MorphologyParams jungle() {
        return new MorphologyParams(
                15, 22, 4800,
                true,
                3f,
                7, 12, 0.48f, 0.90f,
                20f, 45f, 0.65f,
                true, 0.5f, 4,
                CanopyEnvelope.CanopyType.CLUSTERED_ELLIPSOID,
                0.32, 0.42, -0.05,
                1.08, 0.20, 0.62,
                5, 3.0, 5
        );
    }

    public static MorphologyParams darkOak() {
        return new MorphologyParams(
                9, 14, 3600,
                true,
                2f,
                6, 10, 0.30f, 0.78f,
                25f, 45f, 0.50f,
                true, 0.45f, 3,
                CanopyEnvelope.CanopyType.FLAT_CYLINDER,
                2.0, 2.8, 0.05,
                1.26, 0.10, 0.28,
                0, 2.8, 4
        );
    }

    public static MorphologyParams jungle1x1() {
        return new MorphologyParams(
                12, 18, 4200,
                false,
                3f,
                5, 9, 0.42f, 0.88f,
                20f, 45f, 0.60f,
                true, 0.45f, 3,
                CanopyEnvelope.CanopyType.CLUSTERED_ELLIPSOID,
                0.28, 0.38, -0.05,
                1.05, 0.22, 0.60,
                4, 2.8, 4
        );
    }

    public static MorphologyParams cherry() {
        return new MorphologyParams(
                8, 14, 3600,
                false,
                3f,
                4, 8, 0.40f, 0.88f,
                35f, 60f, 0.55f,
                true, 0.45f, 3,
                CanopyEnvelope.CanopyType.ELLIPSOID,
                0.35, 0.28, 0.05,
                1.10, 0.28, 0.50,
                0, 3.5, 4
        );
    }

    public static MorphologyParams mangrove() {
        return new MorphologyParams(
                6, 10, 3200,
                false,
                5f,
                3, 6, 0.35f, 0.82f,
                25f, 50f, 0.48f,
                true, 0.35f, 3,
                CanopyEnvelope.CanopyType.ELLIPSOID,
                0.30, 0.35, 0.02,
                0.95, 0.25, 0.55,
                0, 2.5, 3
        );
    }

    public static MorphologyParams acacia() {
        return new MorphologyParams(
                8, 14, 3600,
                false,
                18f,
                4, 7, 0.48f, 0.88f,
                15f, 40f, 0.52f,
                true, 0.40f, 3,
                CanopyEnvelope.CanopyType.FLAT_DISC_CLUSTERED,
                2.8, 1.4, 0,
                0.78, 0.35, 0.82,
                6, 3.0, 3
        );
    }
}
