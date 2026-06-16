package com.cp.ecoflux.plant;

import java.util.concurrent.CopyOnWriteArrayList;

import com.cp.ecoflux.api.adapter.SaplingGrowthInterceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thread-safe registry of {@link SaplingGrowthInterceptor}s.
 */
public final class SaplingGrowthInterceptors {

    private static final CopyOnWriteArrayList<SaplingGrowthInterceptor> INTERCEPTORS =
            new CopyOnWriteArrayList<>();

    private SaplingGrowthInterceptors() {}

    public static void register(SaplingGrowthInterceptor interceptor) {
        INTERCEPTORS.add(interceptor);
    }

    /** Pure query: does any registered interceptor handle this sapling type? */
    public static boolean canHandle(BlockState state) {
        for (SaplingGrowthInterceptor interceptor : INTERCEPTORS) {
            if (interceptor.canHandle(state)) {
                return true;
            }
        }
        return false;
    }

    /** Trigger growth via the first matching interceptor. Called from mixin only. */
    public static boolean tryIntercept(ServerLevel level, BlockPos pos, BlockState state) {
        for (SaplingGrowthInterceptor interceptor : INTERCEPTORS) {
            if (interceptor.tryGrowSapling(level, pos, state)) {
                return true;
            }
        }
        return false;
    }
}
