package com.cp.ecoflux.client.key;

import com.cp.ecoflux.EcofluxConstants;
import com.cp.ecoflux.client.ui.EcofluxPanelScreen;
import com.cp.ecoflux.network.PanelDataPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side glue: keybinding → toggle Screen, and stores the most recent
 * {@link PanelDataPayload} for the open Screen to render Tab 1.
 */

@EventBusSubscriber(modid = EcofluxConstants.MOD_ID, value = Dist.CLIENT)
public final class EcofluxPanelClientEvents {

    private static volatile PanelDataPayload latestPanelData;

    private EcofluxPanelClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (EcofluxPanelKeyBindings.OPEN_PANEL.consumeClick()) {
            if (mc.screen instanceof EcofluxPanelScreen) {
                mc.screen.onClose();
            } else if (mc.screen == null) {
                mc.setScreen(new EcofluxPanelScreen());
            }
        }
    }

    /** Called from network thread handler; stores latest data for Screen. */
    public static void handlePanelData(PanelDataPayload payload) {
        latestPanelData = payload;
    }

    /** Called from Screen render to read the most recent payload. */
    public static PanelDataPayload getLatestPanelData() {
        return latestPanelData;
    }
}
