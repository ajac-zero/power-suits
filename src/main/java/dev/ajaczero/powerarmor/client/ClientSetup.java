package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import dev.ajaczero.powerarmor.item.PlasmaCannonItem;
import dev.ajaczero.powerarmor.registry.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Client-only startup (Dist.CLIENT): dedicated servers never load rendering code. */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.POWER_ARMOR.get(), PowerArmorRenderer::new);
        event.registerEntityRenderer(ModEntities.PLASMA_BOLT.get(), PlasmaBoltRenderer::new);
    }

    /** Items whose model is "builtin/entity" are drawn by code. Point the plasma cannon at GeckoLib's item renderer. */
    @SubscribeEvent
    public static void registerItemRenderers(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private GeoItemRenderer<PlasmaCannonItem> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoItemRenderer<>(new DefaultedItemGeoModel<PlasmaCannonItem>(
                            ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "plasma_cannon")) {
                        // Same texture as the suit: the cannon's model was cut out of the suit's model.
                        @Override
                        public ResourceLocation getTextureResource(PlasmaCannonItem item) {
                            return ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor.png");
                        }
                    });
                }
                return this.renderer;
            }
        }, ModItems.PLASMA_CANNON.get());
    }

    /** Which screen draws our menu type. The server only knows the menu; clients pick the picture. */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.POWER_ARMOR.get(), PowerArmorScreen::new);
    }

    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, FuelGauge.ID, FuelGauge::render);
    }

    private ClientSetup() {}
}
