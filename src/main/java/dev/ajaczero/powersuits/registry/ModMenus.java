package dev.ajaczero.powersuits.registry;

import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.menu.PowerSuitMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PowerSuits.MODID);

    /** IMenuTypeExtension.create lets the server send extra data when opening (here: which suit). */
    public static final DeferredHolder<MenuType<?>, MenuType<PowerSuitMenu>> POWER_SUIT =
            MENUS.register("power_suit", () -> IMenuTypeExtension.create(PowerSuitMenu::fromNetwork));

    private ModMenus() {}
}
