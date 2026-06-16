package com.cp.ecoflux.network;

/**
 * Server→client packet carrying the full succession overview data for Tab 1.
 *
 * <p>Sent in two scenarios:
 * <ol>
 *   <li>Response to {@link RequestPanelDataPayload} (player opens the panel).</li>
 *   <li>Push after {@code SuccessionEvaluator.evaluate()} when the panel is open
 *       for the current player (acts as delta — only ever one chunk's data).</li>
 * </ol>
 */

import com.cp.ecoflux.EcofluxConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record PanelDataPayload(
        double globalAvgProgress,
        int chunkX,
        int chunkZ,
        @Nullable ResourceLocation currentBiome,
        @Nullable ResourceLocation targetBiome,
        @Nullable ResourceLocation activePathId,
        double progress,
        int contributingPoints,
        int consumingValue,
        int contributingCount,
        int totalVegetationCount,
        boolean successionDisabled) implements CustomPacketPayload {

    public static final Type<PanelDataPayload> TYPE =
            new Type<>(EcofluxConstants.id("panel_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PanelDataPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PanelDataPayload decode(RegistryFriendlyByteBuf buf) {
                    double avg = buf.readDouble();
                    int cx = buf.readVarInt();
                    int cz = buf.readVarInt();
                    ResourceLocation cb = buf.readNullable(b -> b.readResourceLocation());
                    ResourceLocation tb = buf.readNullable(b -> b.readResourceLocation());
                    ResourceLocation ap = buf.readNullable(b -> b.readResourceLocation());
                    double p = buf.readDouble();
                    int cp = buf.readVarInt();
                    int cv = buf.readVarInt();
                    int cc = buf.readVarInt();
                    int tv = buf.readVarInt();
                    boolean sd = buf.readBoolean();
                    return new PanelDataPayload(avg, cx, cz, cb, tb, ap, p, cp, cv, cc, tv, sd);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, PanelDataPayload v) {
                    buf.writeDouble(v.globalAvgProgress());
                    buf.writeVarInt(v.chunkX());
                    buf.writeVarInt(v.chunkZ());
                    buf.writeNullable(v.currentBiome(), (b, rl) -> b.writeResourceLocation(rl));
                    buf.writeNullable(v.targetBiome(), (b, rl) -> b.writeResourceLocation(rl));
                    buf.writeNullable(v.activePathId(), (b, rl) -> b.writeResourceLocation(rl));
                    buf.writeDouble(v.progress());
                    buf.writeVarInt(v.contributingPoints());
                    buf.writeVarInt(v.consumingValue());
                    buf.writeVarInt(v.contributingCount());
                    buf.writeVarInt(v.totalVegetationCount());
                    buf.writeBoolean(v.successionDisabled());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
