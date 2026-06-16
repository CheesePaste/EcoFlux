package com.cp.ecoflux.network;

/**
 * Client→server packet. Indicates the player opened or closed the Ecoflux panel.
 *
 * <p>When {@code open=true}: server marks panel as open, computes global average
 * progress, and responds with a full {@link PanelDataPayload}.
 *
 * <p>When {@code open=false}: server marks panel as closed (stops push deltas).
 */

import com.cp.ecoflux.EcofluxConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestPanelDataPayload(boolean open) implements CustomPacketPayload {

    public static final Type<RequestPanelDataPayload> TYPE =
            new Type<>(EcofluxConstants.id("request_panel_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPanelDataPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestPanelDataPayload decode(RegistryFriendlyByteBuf buf) {
                    return new RequestPanelDataPayload(buf.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RequestPanelDataPayload v) {
                    buf.writeBoolean(v.open());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
