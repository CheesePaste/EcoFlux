package com.s.ecoflux.test.sample;

import net.minecraft.world.level.biome.BiomeSource;

/** Duck interface for {@code ChunkGeneratorMixin} — allows type-safe access to the swap method. */
public interface ChunkGeneratorAccessor {
    void ecoflux$swapBiomeSourceForSampling(BiomeSource newSource);
}
