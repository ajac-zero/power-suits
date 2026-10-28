package dev.ajaczero.powersuits;

import com.mojang.logging.LogUtils;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.item.HelmetStyle;
import dev.ajaczero.powersuits.item.ModTooltip;
import dev.ajaczero.powersuits.network.PowerSwitchPayload;
import dev.ajaczero.powersuits.network.ToolTriggerPayload;
import dev.ajaczero.powersuits.registry.ModDataComponents;
import dev.ajaczero.powersuits.registry.ModEntities;
import dev.ajaczero.powersuits.registry.ModItems;
import dev.ajaczero.powersuits.registry.ModMenus;
import dev.ajaczero.powersuits.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

/**
 * Entry point. NeoForge finds this class through the @Mod annotation and calls the constructor
 * once at startup. Here we hook our registries and setup events into the "mod event bus".
 */
@Mod(PowerSuits.MODID)
public class PowerSuits {
    public static final String MODID = "power_suits";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PowerSuits(IEventBus modBus, ModContainer container) {
        // Balance knobs: config/power_suits-common.toml (see PowerSuitsConfig). LOCAL = read on both sides from the
        // game's config folder, not synced (what 1.21.1 called COMMON; the file keeps that name so edits survive).
        container.registerConfig(ModConfig.Type.LOCAL, PowerSuitsConfig.SPEC, PowerSuits.MODID + "-common.toml");
        // Registries: tell Minecraft our entity type and item exist.
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus); // the fuel/tool data a packed suit item carries
        ModMenus.MENUS.register(modBus);               // the suit's inventory screen
        ModSounds.SOUNDS.register(modBus); // engine + tool loops (sounds.json)

        // Every living entity needs a set of attributes (health, armor, speed...).
        modBus.addListener(PowerSuits::registerAttributes);
        // Show the item in a creative inventory tab.
        modBus.addListener(PowerSuits::addToCreativeTab);
        // Network messages (client -> server: socket tool button (right click) pressed/released).
        modBus.addListener(ToolTriggerPayload::register);
        modBus.addListener(PowerSwitchPayload::register); // the pilot's power switch key
        // Our items' own tooltip lines (item/ModTooltip), where vanilla's deprecated appendHoverText used to put them.
        modBus.addListener(PowerSuits::registerTooltips);
        // GameTests and the autopilot live in src/dev/ (not shipped): they register from dev/PowerSuitsDev.
    }

    private static void registerTooltips(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, (stack, context, display, player, flag, tooltip) -> {
            if (stack.getItem() instanceof ModTooltip item) {
                item.addTooltip(stack, context, tooltip, flag);
            }
        });
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.POWER_SUIT.get(), PowerSuitEntity.createAttributes().build());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.POWER_SUIT);
            event.accept(ModItems.CHAINGUN);
            event.accept(ModItems.EM_PROJECTOR);
            event.accept(ModItems.DRILL);
            event.accept(ModItems.CHAINSAW);
            event.accept(ModItems.FLAMETHROWER);
            event.accept(ModItems.TNT_LAUNCHER);
            event.accept(ModItems.RAIL_CANNON);
            event.accept(ModItems.SUIT_HELMET);
            // the styled helmets and their templates (design sec. 4c): in survival these come from structure chests
            for (var style : HelmetStyle.values()) {
                ItemStack helmet = new ItemStack(ModItems.SUIT_HELMET.get());
                helmet.set(ModDataComponents.HELMET_STYLE.get(), style);
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
