package com.cp.ecoflux.util.sample;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Mutable single-biome {@link BiomeSource} for batch sampling.
 * The world preset JSON references the biome by ID (e.g. {@code "minecraft:plains"}),
 * resolved via {@link Biome#CODEC} the same way {@code FixedBiomeSource} does.
 * At runtime {@link #setTargetBiome} swaps the biome for batch iteration.
 */
public final class SamplingBiomeSource extends BiomeSource implements BiomeManager.NoiseBiomeSource {

    public static final MapCodec<SamplingBiomeSource> CODEC =
            Biome.CODEC.fieldOf("biome")
                    .xmap(SamplingBiomeSource::new, s -> s.biome)
                    .stable();

    private Holder<Biome> biome;

    public SamplingBiomeSource(Holder<Biome> biome) {
        this.biome = biome;
    }

    /** Runtime biome switch for batch iteration. */
    public void setTargetBiome(Holder<Biome> newBiome) {
        this.biome = newBiome;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(biome);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return biome;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return biome;
    }

    @Nullable
    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
            int x, int y, int z, int radius, int increment,
            Predicate<Holder<Biome>> biomePredicate, RandomSource random,
            boolean findClosest, Climate.Sampler sampler) {
        if (biomePredicate.test(biome)) {
            return findClosest
                    ? Pair.of(new BlockPos(x, y, z), biome)
                    : Pair.of(new BlockPos(x - radius + random.nextInt(radius * 2 + 1), y,
                            z - radius + random.nextInt(radius * 2 + 1)), biome);
        }
        return null;
    }

    @Nullable
    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
            BlockPos pos, int radius, int horizontalStep, int verticalStep,
            Predicate<Holder<Biome>> biomePredicate, Climate.Sampler sampler,
            LevelReader level) {
        return biomePredicate.test(biome) ? Pair.of(pos, biome) : null;
    }

    @Override
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        return Sets.newHashSet(Set.of(biome));
    }
}
