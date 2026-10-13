package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ChaingunItem;
import dev.ajaczero.powerarmor.item.ChainsawItem;
import dev.ajaczero.powerarmor.item.DrillItem;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PowerArmor.MODID);

    /** The packed-up suit. Its durability bar is the suit's health, so you can repair it in an anvil. */
    public static final DeferredItem<PowerArmorItem> POWER_ARMOR = ITEMS.register("power_armor",
            () -> new PowerArmorItem(new Item.Properties()
                    .durability(PowerArmorEntity.MAX_HEALTH)
                    .rarity(Rarity.UNCOMMON)));

    /** First power tool for the suit's socket: the chaingun (was the plasma cannon, then briefly the rivet gun). */
    public static final DeferredItem<ChaingunItem> CHAINGUN = ITEMS.register("chaingun",
            () -> new ChaingunItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    static {
        // Worlds saved before the renames store "powerarmor:plasma_cannon" or "powerarmor:rivet_gun": load those stacks
        // as chainguns instead of dropping them as unknown items.
        for (String old : new String[] {"plasma_cannon", "rivet_gun"}) {
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, old),
                    ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "chaingun"));
        }
    }

    /** Second power tool: electromagnetic grab / throw for items and mobs. */
    public static final DeferredItem<EmProjectorItem> EM_PROJECTOR = ITEMS.register("em_projector",
            () -> new EmProjectorItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Third: diamond-tipped 3x3 mining drill. */
    public static final DeferredItem<DrillItem> DRILL = ITEMS.register("drill",
            () -> new DrillItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Fourth: chainsaw, fells whole trees. */
    public static final DeferredItem<ChainsawItem> CHAINSAW = ITEMS.register("chainsaw",
            () -> new ChainsawItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {}
}
