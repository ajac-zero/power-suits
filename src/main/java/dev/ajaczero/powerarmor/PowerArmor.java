package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModDataComponents;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import dev.ajaczero.powerarmor.registry.ModMenus;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Entry point. NeoForge finds this class through the @Mod annotation and calls the constructor
 * once at startup. Here we hook our registries and setup events into the "mod event bus".
 */
@Mod(PowerArmor.MODID)
public class PowerArmor {
    public static final String MODID = "powerarmor";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public PowerArmor(IEventBus modBus, ModContainer container) {
        // Balance knobs: config/powerarmor-common.toml (see PowerArmorConfig). LOCAL = read on both sides from the
        // game's config folder, not synced (what 1.21.1 called COMMON; the file keeps its old name so edits survive).
        container.registerConfig(ModConfig.Type.LOCAL, PowerArmorConfig.SPEC, PowerArmor.MODID + "-common.toml");
        // Registries: tell Minecraft our entity type and item exist.
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus); // the fuel/tool data a packed suit item carries
        ModMenus.MENUS.register(modBus);               // the suit's inventory screen
        dev.ajaczero.powerarmor.registry.ModSounds.SOUNDS.register(modBus); // engine + tool loops (sounds.json)

        // Every living entity needs a set of attributes (health, armor, speed...).
        modBus.addListener(PowerArmor::registerAttributes);
        // Show the item in a creative inventory tab.
        modBus.addListener(PowerArmor::addToCreativeTab);
        // Network messages (client -> server: socket tool button (right click) pressed/released).
        modBus.addListener(dev.ajaczero.powerarmor.network.ToolTriggerPayload::register);
        // GameTests and the autopilot live in src/dev/ (not shipped): they register from dev/PowerArmorDev.
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.POWER_ARMOR.get(), PowerArmorEntity.createAttributes().build());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.POWER_ARMOR);
            event.accept(ModItems.CHAINGUN);
            event.accept(ModItems.EM_PROJECTOR);
            event.accept(ModItems.DRILL);
            event.accept(ModItems.CHAINSAW);
            event.accept(ModItems.FLAMETHROWER);
            event.accept(ModItems.TNT_LAUNCHER);
            event.accept(ModItems.RAIL_CANNON);
            event.accept(ModItems.SUIT_HELMET);
            // the styled helmets and their templates (design sec. 4c): in survival these come from structure chests
            for (var style : dev.ajaczero.powerarmor.item.HelmetStyle.values()) {
                net.minecraft.world.item.ItemStack helmet = new net.minecraft.world.item.ItemStack(ModItems.SUIT_HELMET.get());
                helmet.set(dev.ajaczero.powerarmor.registry.ModDataComponents.HELMET_STYLE.get(), style);
                event.accept(helmet);
            }
            event.accept(ModItems.PROTECTOR_TEMPLATE);
            event.accept(ModItems.SPARTAN_TEMPLATE);
            event.accept(ModItems.NOBLE_TEMPLATE);
            event.accept(ModItems.ROBOT_TEMPLATE);
            event.accept(ModItems.HUNTER_TEMPLATE);
            event.accept(ModItems.SUIT_TORSO);
            event.accept(ModItems.SUIT_ARM);
            event.accept(ModItems.SUIT_LEG);
            event.accept(ModItems.FIREBOX);
            event.accept(ModItems.TIDAL_ENGINE);
            event.accept(ModItems.STAR_REACTOR);
        }
    }
}
