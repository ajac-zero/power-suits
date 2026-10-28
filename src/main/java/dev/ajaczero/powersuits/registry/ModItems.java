package dev.ajaczero.powersuits.registry;

import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.entity.SuitEngine;
import dev.ajaczero.powersuits.item.ChaingunItem;
import dev.ajaczero.powersuits.item.ChainsawItem;
import dev.ajaczero.powersuits.item.DrillItem;
import dev.ajaczero.powersuits.item.EmProjectorItem;
import dev.ajaczero.powersuits.item.EngineItem;
import dev.ajaczero.powersuits.item.FlamethrowerItem;
import dev.ajaczero.powersuits.item.HelmetStyle;
import dev.ajaczero.powersuits.item.HelmetTemplateItem;
import dev.ajaczero.powersuits.item.PowerSuitItem;
import dev.ajaczero.powersuits.item.RailCannonItem;
import dev.ajaczero.powersuits.item.SuitHelmetItem;
import dev.ajaczero.powersuits.item.SuitPartItem;
import dev.ajaczero.powersuits.item.TntLauncherItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    // registerItem(name, constructor, properties): since 1.21.2 an item's Properties must carry its id, which
    // registerItem sets before calling the constructor.
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PowerSuits.MODID);

    /** The packed-up suit. Its durability bar is the suit's health, so you can repair it in an anvil. */
    public static final DeferredItem<PowerSuitItem> POWER_SUIT = ITEMS.registerItem("power_suit", PowerSuitItem::new, props -> props
                    .durability(PowerSuitEntity.MAX_HEALTH)
                    .repairable(Items.IRON_INGOT) // anvil repair with iron ingots
                    .rarity(Rarity.UNCOMMON));

    /** First power tool for the suit's socket: the chaingun (was the plasma cannon, then briefly the rivet gun). */
    public static final DeferredItem<ChaingunItem> CHAINGUN = ITEMS.registerItem("chaingun", ChaingunItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    static {
        // Worlds saved before the renames store "power_suits:plasma_cannon" or "power_suits:rivet_gun": load those stacks
        // as chainguns instead of dropping them as unknown items.
        for (String old : new String[] {"plasma_cannon", "rivet_gun"}) {
            ITEMS.addAlias(Identifier.fromNamespaceAndPath(PowerSuits.MODID, old),
                    Identifier.fromNamespaceAndPath(PowerSuits.MODID, "chaingun"));
        }
    }

    /** Second power tool: electromagnetic grab / throw for items and mobs. */
    public static final DeferredItem<EmProjectorItem> EM_PROJECTOR = ITEMS.registerItem("em_projector", EmProjectorItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Third: diamond-tipped 3x3 mining drill. */
    public static final DeferredItem<DrillItem> DRILL = ITEMS.registerItem("drill", DrillItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Fourth: chainsaw, fells whole trees. */
    public static final DeferredItem<ChainsawItem> CHAINSAW = ITEMS.registerItem("chainsaw", ChainsawItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Fifth (Phase 11, 2026-10-25): a stream of fire. */
    public static final DeferredItem<FlamethrowerItem> FLAMETHROWER = ITEMS.registerItem("flamethrower", FlamethrowerItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<TntLauncherItem> TNT_LAUNCHER = ITEMS.registerItem("tnt_launcher",
            TntLauncherItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));
    /** Phase 11 (2026-10-26): the chaingun's opposite, one charged piercing shot. */
    public static final DeferredItem<RailCannonItem> RAIL_CANNON = ITEMS.registerItem("rail_cannon",
            RailCannonItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    // The suit's parts (2026-10-19): crafted one by one; torso, two arms and two legs are assembled into the suit (an
    // arm and a leg are crafted twice: both sides are the same piece), the helmet goes in the suit's helmet slot.
    // Stack to 1 like vanilla armour.
    public static final DeferredItem<SuitPartItem> SUIT_TORSO = ITEMS.registerItem("suit_torso",
            props -> new SuitPartItem(props, "item.power_suits.suit_part.tooltip"), props -> props.stacksTo(1));
    public static final DeferredItem<SuitPartItem> SUIT_ARM = ITEMS.registerItem("suit_arm",
            props -> new SuitPartItem(props, "item.power_suits.suit_part.tooltip"), props -> props.stacksTo(1));
    public static final DeferredItem<SuitPartItem> SUIT_LEG = ITEMS.registerItem("suit_leg",
            props -> new SuitPartItem(props, "item.power_suits.suit_part.tooltip"), props -> props.stacksTo(1));
    /** The helmet: its look (design sec. 4c) is a data component, set at a smithing table (SuitHelmetItem). */
    public static final DeferredItem<SuitHelmetItem> SUIT_HELMET = ITEMS.registerItem("suit_helmet", SuitHelmetItem::new, props -> props.stacksTo(1));

    // The helmet styles' smithing templates (design sec. 4c, 2026-10-22): Easter eggs found in structure chests.
    public static final DeferredItem<HelmetTemplateItem> PROTECTOR_TEMPLATE = template(HelmetStyle.PROTECTOR);
    public static final DeferredItem<HelmetTemplateItem> SPARTAN_TEMPLATE = template(HelmetStyle.SPARTAN);
    public static final DeferredItem<HelmetTemplateItem> NOBLE_TEMPLATE = template(HelmetStyle.NOBLE);
    public static final DeferredItem<HelmetTemplateItem> ROBOT_TEMPLATE = template(HelmetStyle.ROBOT);
    public static final DeferredItem<HelmetTemplateItem> HUNTER_TEMPLATE = template(HelmetStyle.HUNTER);

    // The engines (design sec. 4b, 2026-10-21): the suit's backpack, swapped in the menu's engine slot. Every suit comes
    // with a firebox; the other two are crafted.
    public static final DeferredItem<EngineItem> FIREBOX = ITEMS.registerItem("firebox",
            props -> new EngineItem(props, SuitEngine.FIREBOX), props -> props.stacksTo(1));
    public static final DeferredItem<EngineItem> TIDAL_ENGINE = ITEMS.registerItem("tidal_engine",
            props -> new EngineItem(props, SuitEngine.TIDAL), props -> props.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<EngineItem> STAR_REACTOR = ITEMS.registerItem("star_reactor",
            props -> new EngineItem(props, SuitEngine.REACTOR), props -> props.stacksTo(1).rarity(Rarity.EPIC));

    private static DeferredItem<HelmetTemplateItem> template(HelmetStyle style) {
        return ITEMS.registerItem(style.getSerializedName() + "_template", props -> new HelmetTemplateItem(props, style),
                props -> props.rarity(Rarity.RARE));
    }

    private ModItems() {}
}
