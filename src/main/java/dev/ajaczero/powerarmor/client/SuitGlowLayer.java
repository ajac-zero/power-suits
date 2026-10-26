package dev.ajaczero.powerarmor.client;

import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.ClientUtil;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ToolLight;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The suit's lights: the engine (firebox fire, tidal Heart of the Sea, reactor energy), the visor, the chaingun's hot barrels, the EM projector's redstone and the rail cannon's core, drawn on top of the model at full brightness so they glow
 * in the dark. Like GeckoLib's AutoGlowingGeoLayer, the whole model is drawn again with a glowmask texture (black
 * everywhere except the glowing pixels, made by tools/make-glowmasks.py) in vanilla's "eyes" render type (the one
 * spider and enderman eyes use): it ignores light and draws its colour on top (blended by alpha in 26.3, not added:
 * a dimmed mask is a darker colour, fine over the dark glass and coals it sits on). Unlike AutoGlowingGeoLayer, each mask
 * is tinted every frame, so the fire flickers and fades and the visor changes colour (PowerArmorEntity.tickLights). The
 * socket tool's lights ({@link ToolLight}) are worked out by the installed tool's ToolEffects.
 */
public class SuitGlowLayer extends GeoRenderLayer<PowerArmorEntity, Void, LivingEntityRenderState> {
    // The suit's own lights: the firebox's fire, the visor, and the other engines' glass (design sec. 4b: the tidal
    // engine's Heart of the Sea, the star reactor's core + canisters).
    private static final Identifier FIRE = mask("fire");
    private static final Identifier VISOR = mask("visor");
    private static final Identifier TIDAL = mask("tidal");
    private static final Identifier REACTOR = mask("reactor");
    private static final Identifier[] SUIT_MASKS = {FIRE, VISOR, TIDAL, REACTOR};
    /** The socket tools' lights, indexed by ToolLight.ordinal() (also drawn on the cockpit viewmodel). */
    static final Identifier[] TOOL_MASKS = java.util.Arrays.stream(ToolLight.values()).map(SuitGlowLayer::mask).toArray(Identifier[]::new);
    /** All masks, in the order of the tints in SuitRenderData.GLOW: the suit's, then the tool's. */
    private static final Identifier[] MASKS = java.util.stream.Stream.concat(java.util.Arrays.stream(SUIT_MASKS),
            java.util.Arrays.stream(TOOL_MASKS)).toArray(Identifier[]::new);

    private static Identifier mask(String name) {
        return Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor_glow_" + name + ".png");
    }

    /** A tool light's glowmask file (written by tools/make-glowmasks.py). */
    private static Identifier mask(ToolLight light) {
        return mask(switch (light) {
            case HEAT -> "heat";
            case HEAT_BARREL -> "heat_barrel";
            case EM_CORE -> "em_core";                 // the EM projector's lens + vents
            case EM_ARC -> "em_arc";                   // and its prong tips
            case PILOT -> "pilot";                     // the flamethrower's pilot light (the tip of its slate rod)
            case RAIL_FLOW_0 -> "rail_flow_0";         // the rail cannon's core rings, a third each (ring r in mask r mod 3)
            case RAIL_FLOW_1 -> "rail_flow_1";
            case RAIL_FLOW_2 -> "rail_flow_2";
            case RAIL_EMIT -> "rail_emit";             // the emitter in its band (opaque: dark when off)
        });
    }

    /** This frame's tints of the socket tool's lights (ToolLight order), from the installed tool's effects. */
    static int[] toolTints(PowerArmorEntity suit, float time, float partial) {
        float[] lights = suit.socketVisuals().lights(time, partial);
        int[] tints = new int[lights.length];
        ToolLight[] all = ToolLight.values();
        for (int i = 0; i < lights.length; i++) {
            tints[i] = all[i].fadesByAlpha ? energy(lights[i]) : tint(lights[i], 1F, 1F, 1F);
        }
        return tints;
    }

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
        int[] suitTints = {
                tint(fire, 1F, 1F, 1F),
                tint(on, visor[0], visor[1], visor[2]),
                tint(suit.getTidalGlow(time), 1F, 1F, 1F),
                tint(suit.getReactorGlow(time), 1F, 1F, 1F)};
        int[] toolTints = toolTints(suit, time, partialTick);
        int[] tints = java.util.Arrays.copyOf(suitTints, suitTints.length + toolTints.length);
        System.arraycopy(toolTints, 0, tints, suitTints.length, toolTints.length);
        state.addGeckolibData(SuitRenderData.GLOW, tints);
        state.addGeckolibData(SuitRenderData.RAIL_SHAKE, new float[] {suit.socketVisuals().effects().shake(suit), time});
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

    /** Draws the model again in the "eyes" render type for each mask whose tint isn't black. The tint, light
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
            state.addGeckolibData(DataTickets.RENDER_COLOR, tints[i]); // scaling the colour down dims the glow
            state.addGeckolibData(DataTickets.PACKED_LIGHT, LightCoordsUtil.FULL_BRIGHT);
            state.addGeckolibData(DataTickets.PACKED_OVERLAY, OverlayTexture.NO_OVERLAY);
            renderer.submitRenderTasks(info, renderTasks.order(1), RenderTypes.eyes(masks[i]));
        }
        state.addGeckolibData(DataTickets.RENDER_COLOR, colour);
        state.addGeckolibData(DataTickets.PACKED_LIGHT, light);
        state.addGeckolibData(DataTickets.PACKED_OVERLAY, overlay);
    }

    /** Light that fades in over the model or over nothing (the chaingun's heat, the rail cannon's core, see-through
     *  when off: its pixels are cleared from the texture): the mask's own colour, faded by its opacity instead of darkened.
     *  The "eyes" pipeline blends by alpha (it isn't additive), so a dimmed opaque colour would draw a dark core where
     *  there should be air (the chaingun's warming barrels showed a dark smudge until 2026-10-26). */
    static int energy(float brightness) {
        if (brightness <= 0.01F) {
            return 0xFF000000;
        }
        return channel(brightness) << 24 | 0xFFFFFF;
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
