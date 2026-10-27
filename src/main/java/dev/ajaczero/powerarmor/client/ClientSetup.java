package dev.ajaczero.powerarmor.client;

import com.google.common.reflect.TypeToken;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.ClientView;
import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModMenus;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/** Client-only startup (Dist.CLIENT): dedicated servers never load rendering code. */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        SocketAim.visualMuzzle = CockpitHands::visualMuzzle; // muzzle effects start at the cockpit viewmodel's muzzle
        ClientView.inCockpit = PowerArmorRenderer::isCockpitView; // shared code asks the camera
        event.registerEntityRenderer(ModEntities.POWER_ARMOR.get(), PowerArmorRenderer::new);
        event.registerEntityRenderer(ModEntities.BULLET.get(), BulletRenderer::new);
        // The flame stream draws itself with particles (FlameJet.clientTick): nothing to render.
        event.registerEntityRenderer(ModEntities.FLAME_JET.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.TNT_SHELL.get(), TntShellRenderer::new);
    }

    /** Lets PilotView hide pilots: copies "sits in a suit" into every living thing's render state. */
    @SubscribeEvent
    public static void renderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<
                LivingEntity, LivingEntityRenderState, ?>>() {},
                PilotView::markPilot);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.POWER_ARMOR.get(), PowerArmorScreen::new);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, SuitHud.ID, SuitHud::render);
    }

    private ClientSetup() {}
}
