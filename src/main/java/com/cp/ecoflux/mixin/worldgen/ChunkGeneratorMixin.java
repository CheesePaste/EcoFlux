package com.cp.ecoflux.mixin.worldgen;

import com.cp.ecoflux.util.sample.ChunkGeneratorAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Mixin to allow runtime replacement of {@code biomeSource} for batch sampling. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin implements ChunkGeneratorAccessor {

    @Shadow
    @Final
    @Mutable
    private BiomeSource biomeSource;

    @Unique
    @Override
    public void ecoflux$swapBiomeSourceForSampling(BiomeSource newSource) {
        this.biomeSource = newSource;
    }
}
