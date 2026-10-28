package dev.ajaczero.powersuits.client;

import dev.ajaczero.powersuits.PowerSuits;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point (a second @Mod class, loaded only on the client): registers the player's own visor settings
 * (VisorConfig, config/power_suits-client.toml) and adds a "Config" button to the mod's entry in the Mods list.
 * NeoForge's ConfigurationScreen builds the sliders and fields from both config files by itself; the labels come from
 * the "power_suits.configuration.*" lang keys.
 */
@Mod(value = PowerSuits.MODID, dist = Dist.CLIENT)
public final class PowerSuitsClient {
    public PowerSuitsClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, VisorConfig.SPEC); // power_suits-client.toml
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
