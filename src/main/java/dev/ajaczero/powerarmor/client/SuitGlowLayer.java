package dev.ajaczero.powerarmor.client;

import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.ClientUtil;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The suit's lights: the engine (firebox fire, tidal Heart of the Sea, reactor energy), the visor, the chaingun's hot barrels and the EM projector's redstone, drawn on top of the model at full brightness so they glow
 * in the dark. Like GeckoLib's AutoGlowingGeoLayer, the whole model is drawn again with a glowmask texture (black
 * everywhere except the glowing pixels, made by tools/make-glowmasks.py) in vanilla's "eyes" render type (the one
 * spider and enderman eyes use): it ignores light and adds its colour on top. Unlike AutoGlowingGeoLayer, each mask
 * is tinted every frame, so the fire flickers and fades and the visor changes colour (PowerArmorEntity.tickLights).
 */
public class SuitGlowLayer extends GeoRenderLayer<PowerArmorEntity, Void, LivingEntityRenderState> {
    private static final Identifier FIRE = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_fire.png");
    private static final Identifier VISOR = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_visor.png");
    static final Identifier HEAT = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_heat.png");
    static final Identifier HEAT_BARREL = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_heat_barrel.png");
    /** The EM projector's redstone: lens + vents, and the prong tips. */
    static final Identifier EM_CORE = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_em_core.png");
    static final Identifier EM_ARC = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_em_arc.png");
    /** The engine backpacks' glass (design sec. 4b): the tidal engine's Heart of the Sea, the star reactor's core + canisters. */
    static final Identifier TIDAL = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_tidal.png");
    static final Identifier REACTOR = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_reactor.png");
    /** The flamethrower's pilot light (the tip of its slate rod by the nozzle). */
    static final Identifier PILOT = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_pilot.png");
    /** The masks, in the order of the tints in SuitRenderData.GLOW. */
    private static final Identifier[] MASKS = {FIRE, VISOR, HEAT, HEAT_BARREL, EM_CORE, EM_ARC, TIDAL, REACTOR, PILOT};

    public SuitGlowLayer(GeoRenderer<PowerArmorEntity, Void, LivingEntityRenderState> renderer) {
        super(renderer);
    }

    /** Extract: work out this frame's tint of every light while we still have the suit. */
    @Override
    public void addRenderData(PowerArmorEntity suit, @Nullable Void ignored, LivingEntityRenderState state, float partialTick) {
        float time = (float) ClientUtil.getCurrentTick(partialTick);
        // Coals: a slow uneven flicker (two sines that never line up), deeper when the engine works hard.
        float fire = suit.getFireGlow() * (0.86F + 0.08F * Mth.sin(time * 0.37F + suit.getId()) + 0.06F * Mth.sin(time * 1.31F));
        float[] visor = suit.getVisorColor();
        float on = suit.getVisorFlicker(time);
        float heat = suit.getToolHeat();
        state.addGeckolibData(SuitRenderData.GLOW, new int[] {
                tint(fire, 1F, 1F, 1F),
                tint(on, visor[0], visor[1], visor[2]),
                tint(heatGlow(heat), 1F, 1F, 1F),
                tint(barrelGlow(heat), 1F, 1F, 1F),
                tint(suit.getEmCoreGlow(time), 1F, 1F, 1F),
                tint(suit.getEmArcGlow(time), 1F, 1F, 1F),
                tint(suit.getTidalGlow(time), 1F, 1F, 1F),
                tint(suit.getReactorGlow(time), 1F, 1F, 1F),
                tint(suit.getPilotGlow(time), 1F, 1F, 1F)});
    }

    /** Render: draw the model once per lit mask, after (on top of) the suit itself. */
    @Override
    public void submitRenderTask(RenderPassInfo<LivingEntityRenderState> info, SubmitNodeCollector renderTasks) {
        if (!info.willRender() || info.getOrDefaultGeckolibData(SuitRenderData.COCKPIT, false)) {
            return; // the upper body isn't drawn from inside
        }
        int[] tints = info.getGeckolibData(SuitRenderData.GLOW);
        if (tints != null) {
            drawMasks(this.renderer, info, renderTasks, MASKS, tints);
        }
    }

    /** Draws the model again in the additive "eyes" render type for each mask whose tint isn't black. The tint, light
     *  and overlay are read from the render state when a pass is submitted, so they're swapped in for each pass and put
     *  back afterwards. */
    static <R extends GeoRenderState> void drawMasks(GeoRenderer<?, ?, R> renderer, RenderPassInfo<R> info, SubmitNodeCollector renderTasks,
                                                     Identifier[] masks, int[] tints) {
        R state = info.renderState();
        int colour = info.renderColor(), light = info.packedLight(), overlay = info.packedOverlay();
        for (int i = 0; i < masks.length; i++) {
            if ((tints[i] & 0xFFFFFF) == 0) {
                continue;
            }
            state.addGeckolibData(DataTickets.RENDER_COLOR, tints[i]); // additive: scaling the colour down dims the glow
            state.addGeckolibData(DataTickets.PACKED_LIGHT, LightCoordsUtil.FULL_BRIGHT);
            state.addGeckolibData(DataTickets.PACKED_OVERLAY, OverlayTexture.NO_OVERLAY);
            renderer.submitRenderTasks(info, renderTasks.order(1), RenderTypes.eyes(masks[i]));
        }
        state.addGeckolibData(DataTickets.RENDER_COLOR, colour);
        state.addGeckolibData(DataTickets.PACKED_LIGHT, light);
        state.addGeckolibData(DataTickets.PACKED_OVERLAY, overlay);
    }

    /** Chaingun barrel tips: nothing while cool, a dull red glow from a third of the way, white-hot when overheated. */
    static float heatGlow(float heat) {
        float h = Math.max(0F, (heat - 0.3F) / 0.7F);
        return h * h * (3 - 2 * h);
    }

    /** The barrels behind the tips: only once they're really hot (from 55%), so the glow creeps back from the tips. */
    static float barrelGlow(float heat) {
        float h = Math.max(0F, (heat - 0.55F) / 0.45F);
        return h * h * (3 - 2 * h);
    }

    /** Opaque ARGB of (r, g, b) x brightness; black (= nothing to draw) below 1%. */
    static int tint(float brightness, float r, float g, float b) {
        if (brightness <= 0.01F) {
            return 0xFF000000;
        }
        return 0xFF000000 | channel(r * brightness) << 16 | channel(g * brightness) << 8 | channel(b * brightness);
    }

    private static int channel(float v) {
        return Mth.clamp((int) (v * 255F), 0, 255);
    }
}
