package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModDataComponents;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import dev.ajaczero.powerarmor.registry.ModMenus;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
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

    public PowerArmor(IEventBus modBus) {
        // Registries: tell Minecraft our entity type and item exist.
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus); // the fuel/tool data a packed suit item carries
        ModMenus.MENUS.register(modBus);               // the suit's inventory screen

        // Every living entity needs a set of attributes (health, armor, speed...).
        modBus.addListener(PowerArmor::registerAttributes);
        // Show the item in a creative inventory tab.
        modBus.addListener(PowerArmor::addToCreativeTab);
        // Network messages (client -> server: attack pressed/released for the socket tool).
        modBus.addListener(dev.ajaczero.powerarmor.network.ToolTriggerPayload::register);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.POWER_ARMOR.get(), PowerArmorEntity.createAttributes().build());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.POWER_ARMOR);
            event.accept(ModItems.PLASMA_CANNON);
            event.accept(ModItems.EM_PROJECTOR);
            event.accept(ModItems.DRILL);
            event.accept(ModItems.CHAINSAW);
        }
    }
}
