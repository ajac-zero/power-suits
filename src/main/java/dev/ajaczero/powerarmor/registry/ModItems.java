package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import dev.ajaczero.powerarmor.item.PlasmaCannonItem;
import dev.ajaczero.powerarmor.item.PowerArmorItem;
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

    /** First power tool for the suit's socket. */
    public static final DeferredItem<PlasmaCannonItem> PLASMA_CANNON = ITEMS.register("plasma_cannon",
            () -> new PlasmaCannonItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Second power tool: electromagnetic grab / throw for items and mobs. */
    public static final DeferredItem<EmProjectorItem> EM_PROJECTOR = ITEMS.register("em_projector",
            () -> new EmProjectorItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {}
}
