package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ChaingunItem;
import dev.ajaczero.powerarmor.item.ChainsawItem;
import dev.ajaczero.powerarmor.item.DrillItem;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import dev.ajaczero.powerarmor.item.SuitPartItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    // registerItem(name, constructor, properties): since 1.21.2 an item's Properties must carry its id, which
    // registerItem sets before calling the constructor.
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PowerArmor.MODID);

    /** The packed-up suit. Its durability bar is the suit's health, so you can repair it in an anvil. */
    public static final DeferredItem<PowerArmorItem> POWER_ARMOR = ITEMS.registerItem("power_armor", PowerArmorItem::new, props -> props
                    .durability(PowerArmorEntity.MAX_HEALTH)
                    .repairable(net.minecraft.world.item.Items.IRON_INGOT) // anvil repair with iron ingots
                    .rarity(Rarity.UNCOMMON));

    /** First power tool for the suit's socket: the chaingun (was the plasma cannon, then briefly the rivet gun). */
    public static final DeferredItem<ChaingunItem> CHAINGUN = ITEMS.registerItem("chaingun", ChaingunItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    static {
        // Worlds saved before the renames store "powerarmor:plasma_cannon" or "powerarmor:rivet_gun": load those stacks
        // as chainguns instead of dropping them as unknown items.
        for (String old : new String[] {"plasma_cannon", "rivet_gun"}) {
            ITEMS.addAlias(Identifier.fromNamespaceAndPath(PowerArmor.MODID, old),
                    Identifier.fromNamespaceAndPath(PowerArmor.MODID, "chaingun"));
        }
    }

    /** Second power tool: electromagnetic grab / throw for items and mobs. */
    public static final DeferredItem<EmProjectorItem> EM_PROJECTOR = ITEMS.registerItem("em_projector", EmProjectorItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Third: diamond-tipped 3x3 mining drill. */
    public static final DeferredItem<DrillItem> DRILL = ITEMS.registerItem("drill", DrillItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Fourth: chainsaw, fells whole trees. */
    public static final DeferredItem<ChainsawItem> CHAINSAW = ITEMS.registerItem("chainsaw", ChainsawItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    // The suit's parts (2026-10-19): crafted one by one, then assembled into the suit (an arm and a leg are crafted twice:
    // both sides are the same piece). Stack to 1 like vanilla armour.
    public static final DeferredItem<SuitPartItem> SUIT_TORSO = ITEMS.registerItem("suit_torso", SuitPartItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<SuitPartItem> SUIT_ARM = ITEMS.registerItem("suit_arm", SuitPartItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<SuitPartItem> SUIT_LEG = ITEMS.registerItem("suit_leg", SuitPartItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<SuitPartItem> SUIT_HELMET = ITEMS.registerItem("suit_helmet", SuitPartItem::new, props -> props.stacksTo(1));

    private ModItems() {}
}
