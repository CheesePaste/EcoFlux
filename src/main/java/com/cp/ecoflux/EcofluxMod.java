package com.cp.ecoflux;

/**
 * NeoForge {@code @Mod} entry point for Ecoflux.
 *
 * <p>Structure: constructor wires all subsystems&mdash;attachments, chunk events,
 * commands, networking, config reload listeners, and vegetation tracker&mdash;and
 * registers server and client config specs.
 * <p>Role in Ecoflux: bootstrap; everything starts here.
 */

import com.cp.ecoflux.config.EcofluxServerConfig;
import com.cp.ecoflux.config.VisualLifecycleClientConfig;
import com.cp.ecoflux.init.ModAttachments;
import com.cp.ecoflux.init.ModChunkEvents;
import com.cp.ecoflux.init.ModCommands;
import com.cp.ecoflux.init.ModPlayerEvents;
import com.cp.ecoflux.init.ModReloadListeners;
import com.cp.ecoflux.network.ModNetworking;
import com.cp.ecoflux.util.sample.SamplingBiomeSources;
import com.cp.ecoflux.worldgen.EcofluxFeatures;
import com.cp.ecoflux.worldgen.biomemodifier.EcofluxBiomeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

@Mod(EcofluxConstants.MOD_ID)
public final class EcofluxMod {
    public EcofluxMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.register(modEventBus);
        ModNetworking.register(modEventBus);
        ModChunkEvents.register();
        ModPlayerEvents.register();
        ModCommands.register();
        ModReloadListeners.register();
        SamplingBiomeSources.register(modEventBus);
        EcofluxBiomeModifiers.register(modEventBus);
        EcofluxFeatures.register(modEventBus);

        if (net.neoforged.fml.ModList.get().isLoaded("dynamictrees")) {
            com.cp.ecoflux.compat.dynamictrees.DTCompat.init();
        }
        modContainer.registerConfig(ModConfig.Type.SERVER, EcofluxServerConfig.SPEC, "ecoflux-server.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, VisualLifecycleClientConfig.SPEC, "ecoflux-client.toml");
        EcofluxConstants.LOGGER.info("{} 正在初始化", modContainer.getModInfo().getDisplayName());
    }
}
