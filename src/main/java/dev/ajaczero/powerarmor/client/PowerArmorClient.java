package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point (a second @Mod class, loaded only on the client): registers the player's own visor settings
 * (VisorConfig, config/powerarmor-client.toml) and adds a "Config" button to the mod's entry in the Mods list.
 * NeoForge's ConfigurationScreen builds the sliders and fields from both config files by itself; the labels come from
 * the "powerarmor.configuration.*" lang keys.
 */
@Mod(value = PowerArmor.MODID, dist = Dist.CLIENT)
public final class PowerArmorClient {
    public PowerArmorClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, VisorConfig.SPEC); // powerarmor-client.toml
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
