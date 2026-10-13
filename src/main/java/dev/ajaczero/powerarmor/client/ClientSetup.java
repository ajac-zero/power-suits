package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.item.SocketAim;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import dev.ajaczero.powerarmor.item.ChainsawItem;
import dev.ajaczero.powerarmor.item.DrillItem;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import dev.ajaczero.powerarmor.item.ChaingunItem;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
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
        SocketAim.visualMuzzle = CockpitHands::visualMuzzle; // muzzle effects start at the cockpit viewmodel's muzzle
        event.registerEntityRenderer(ModEntities.POWER_ARMOR.get(), PowerArmorRenderer::new);
        event.registerEntityRenderer(ModEntities.BULLET.get(), BulletRenderer::new);
    }

    /** Items whose model is "builtin/entity" are drawn by code. Point the socket tools at GeckoLib's item renderer. */
    @SubscribeEvent
    public static void registerItemRenderers(RegisterClientExtensionsEvent event) {
        event.registerItem(ClientSetup.<ChaingunItem>geoItem("chaingun"), ModItems.CHAINGUN.get());
        event.registerItem(ClientSetup.<EmProjectorItem>geoItem("em_projector"), ModItems.EM_PROJECTOR.get());
        event.registerItem(ClientSetup.<DrillItem>geoItem("drill"), ModItems.DRILL.get());
        event.registerItem(ClientSetup.<ChainsawItem>geoItem("chainsaw"), ModItems.CHAINSAW.get());
    }

    /** A GeckoLib item renderer for geo/item/<name>.geo.json, using the suit's texture (tool models are cut from the suit). */
    private static <T extends Item & GeoItem> IClientItemExtensions geoItem(String name) {
        return new IClientItemExtensions() {
            private GeoItemRenderer<T> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoItemRenderer<>(new DefaultedItemGeoModel<T>(ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, name)) {
                        @Override
                        public ResourceLocation getTextureResource(T item) {
                            return ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor.png");
                        }
                    });
                }
                return this.renderer;
            }
        };
    }

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
