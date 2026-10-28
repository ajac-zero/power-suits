package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PowerArmor.MODID);

    /** IMenuTypeExtension.create lets the server send extra data when opening (here: which suit). */
    public static final DeferredHolder<MenuType<?>, MenuType<PowerArmorMenu>> POWER_ARMOR =
            MENUS.register("power_suit", () -> IMenuTypeExtension.create(PowerArmorMenu::fromNetwork));

    private ModMenus() {}
}
