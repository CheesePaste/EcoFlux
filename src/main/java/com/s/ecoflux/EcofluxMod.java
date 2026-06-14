package com.s.ecoflux;

/**
 * NeoForge {@code @Mod} entry point for Ecoflux.
 *
 * <p>Structure: constructor wires all subsystems&mdash;attachments, chunk events,
 * commands, networking, config reload listeners, and vegetation tracker&mdash;and
 * registers server and client config specs.
 * <p>Role in Ecoflux: bootstrap; everything starts here.
 */

import com.s.ecoflux.client.visual.EcofluxShaders;
import com.s.ecoflux.config.EcofluxServerConfig;
import com.s.ecoflux.config.VisualLifecycleClientConfig;
import com.s.ecoflux.init.ModAttachments;
import com.s.ecoflux.init.ModChunkEvents;
import com.s.ecoflux.init.ModCommands;
import com.s.ecoflux.init.ModPlayerEvents;
import com.s.ecoflux.init.ModReloadListeners;
import com.s.ecoflux.network.ModNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(EcofluxConstants.MOD_ID)
public final class EcofluxMod {
    public EcofluxMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.register(modEventBus);
        ModNetworking.register(modEventBus);
        ModChunkEvents.register();
        ModPlayerEvents.register();
        ModCommands.register();
        ModReloadListeners.register();
        modContainer.registerConfig(ModConfig.Type.SERVER, EcofluxServerConfig.SPEC, "ecoflux-server.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, VisualLifecycleClientConfig.SPEC, "ecoflux-client.toml");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EcofluxShaders.register(modEventBus);
        }
        EcofluxConstants.LOGGER.info("{} 正在初始化", modContainer.getModInfo().getDisplayName());
    }
}
