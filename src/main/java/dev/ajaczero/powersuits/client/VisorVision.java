package dev.ajaczero.powersuits.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Vector3f;

/**
 * The visor sees in the dark (user, 2026-10-27). Only on the pilot's own screen: nothing in the world changes, no light
 * is placed, the server never hears of it. While it's switched on (VisorConfig.NIGHT_VISION_ON, the night vision key
 * N: SuitKeys) it comes on by itself when the light at the pilot's eyes falls (full at light 4 or less: caves, any
 * night under the open sky; none from light 8), over a second, and goes off with the visor's power, or the key, in a
 * third of one. The HUD says so under the compass ({@link #indicator}).
 * <p>
 * Two kinds were built and filmed side by side (VisionScene); the user picked night vision in the visor colour at 0.3
 * for now. The lamp stays in the code, parked for a later upgrade (night vision and the lamp as separate upgrades):
 * nothing in the game turns it on, only the dev scene's {@link #preview}.
 * <ul>
 *   <li><b>Night vision</b>: the same knob vanilla's potion turns. The game doesn't light blocks itself each frame: it
 *   looks their light level (0-15, block and sky) up in the <i>lightmap</i>, a 16x16 table of brightnesses it rebuilds
 *   every tick. One input of that table is a floor ("nothing darker than this", the potion's
 *   {@code nightVisionEffectIntensity} x colour). We raise the floor part of the way (the potion raises it to 0.6:
 *   everything bright) and colour it like the visor, the way night-vision goggles are green.</li>
 *   <li><b>Lamp</b>: a cone of light from the helmet where you look. The lightmap can't do that (it knows light levels,
 *   not places), so this is a <i>post effect</i>: a shader run over the finished picture
 *   ({@code assets/power_suits/shaders/post/visor_lamp.fsh}), like vanilla's creeper view. It reads each pixel's
 *   distance from the depth buffer and brightens what's near the middle of the view and close by. Its knobs (strength,
 *   field of view, colour) reach the shader as the pixels of a tiny texture we rewrite when they change: a post
 *   effect's own numbers are fixed in its JSON. First person only: from a camera behind the suit the cone would start
 *   in the wrong place.</li>
 * </ul>
 * Shader packs (Iris) replace both the lightmap and the post effects with their own: there, neither shows.
 */
@EventBusSubscriber(modid = PowerSuits.MODID, value = Dist.CLIENT)
public final class VisorVision {
    public static final Identifier LAMP_EFFECT = Identifier.fromNamespaceAndPath(PowerSuits.MODID, "visor_lamp");
    /** Where the post effect's JSON finds its texture input ("location": "power_suits:visor_lamp" -> textures/effect/). */
    private static final Identifier LAMP_KNOBS = Identifier.fromNamespaceAndPath(PowerSuits.MODID, "textures/effect/visor_lamp.png");
    /** The lamp's colour: a warm white (an incandescent bulb, the suit is dieselpunk). */
    private static final int LAMP_COLOUR = 0xFFE6C0;
    /** How far the lamp reaches, in blocks (the shader's brightness falls off well before). */
    private static final float LAMP_RANGE = 28F;
    /** Light at the eyes: full vision at or under DARK, none at or over LIGHT. */
    private static final int DARK = 4, LIGHT = 8;
    /** Per tick: on over a second (and off as slowly when the light comes back), off with the power or the key in a
     *  third of one. */
    private static final float RISE = 1F / 20F, FALL = 1F / 7F;

    /**
     * DEV: what the autopilot's comparison scene asks for (null in the game: VisorConfig). {@code instant} skips the
     * fade, for shots taken right after a switch.
     */
    public record Setting(VisorConfig.Vision mode, float night, float lamp, VisorConfig.VisionTint tint, boolean instant) {}
    public static volatile Setting preview;

    /** 0 (off) .. 1 (full): how far it has come on, this tick and the last (frames blend the two). */
    private static float level, levelO;
    private static DynamicTexture knobs;
    private static int knobsWritten = -1;
    /** For the autopilot's log: what the last frame used. */
    private static volatile float lastLevel, lastNight, lastLamp;
    private static volatile int lastBrightness = -1;

    public static float lastLevel() {
        return lastLevel;
    }

    public static float lastNight() {
        return lastNight;
    }

    public static float lastLamp() {
        return lastLamp;
    }

    public static int lastBrightness() {
        return lastBrightness;
    }

    /** The game's setting: night vision as configured, on or off (the key); no lamp (parked). */
    private static Setting setting() {
        Setting p = preview;
        return p != null ? p : new Setting(VisorConfig.NIGHT_VISION_ON.get() ? VisorConfig.Vision.NIGHT_VISION : VisorConfig.Vision.OFF,
                VisorConfig.NIGHT_VISION.get().floatValue(), 0F, VisorConfig.VISION_TINT.get(), false);
    }

    /** What the HUD shows under the compass: nothing while switched off, NV faint while on but not needed (it's light
     *  around you), NV in the visor colour while it lifts the dark. */
    public enum Indicator { NONE, STANDBY, ACTIVE }

    public static Indicator indicator() {
        if (setting().mode() == VisorConfig.Vision.OFF) {
            return Indicator.NONE;
        }
        return lastLevel > 0.02F ? Indicator.ACTIVE : Indicator.STANDBY;
    }

    /** The key (SuitKeys): flip night vision on or off and keep the choice (power_suits-client.toml). */
    public static void toggle() {
        VisorConfig.NIGHT_VISION_ON.set(!VisorConfig.NIGHT_VISION_ON.get());
        VisorConfig.NIGHT_VISION_ON.save();
    }

    /** The suit whose visor the player looks through: they pilot it and it wears a helmet (null otherwise). */
    private static PowerSuitEntity visorOf(Minecraft mc) {
        if (mc.player == null || mc.player.isSpectator() || !(mc.player.getVehicle() instanceof PowerSuitEntity suit)
                || suit.getControllingPassenger() != mc.player || !suit.hasHelmet()) {
            return null;
        }
        return suit;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        levelO = level;
        PowerSuitEntity suit = visorOf(mc);
        if (suit == null || mc.level == null) {
            level = 0F;
            levelO = 0F;
            lastBrightness = -1;
            return;
        }
        // the light where the pilot's eyes are: block light, or the sky's (dimmed at night), whichever is brighter
        int light = mc.level.getMaxLocalRawBrightness(BlockPos.containing(mc.player.getEyePosition()));
        lastBrightness = light;
        boolean on = suit.isPowered() && setting().mode() != VisorConfig.Vision.OFF;
        float target = on ? Mth.clamp((LIGHT - light) / (float) (LIGHT - DARK), 0F, 1F) : 0F;
        if (setting().instant()) {
            level = levelO = target;
        } else if (target > level) {
            level = Math.min(target, level + RISE);
        } else {
            level = Math.max(target, level - (on ? RISE : FALL));
        }
    }

    /**
     * Every frame, after vanilla filled in the frame's lightmap inputs and before anything is drawn: raise the floor and
     * ask for the lamp.
     */
    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Setting s = setting();
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float v = Mth.smoothstep(Mth.lerp(partial, levelO, level));
        // switched off (the key), it still fades out at the configured strength
        boolean nightOn = s.mode() != VisorConfig.Vision.LAMP;
        boolean lampOn = s.mode() == VisorConfig.Vision.LAMP || s.mode() == VisorConfig.Vision.BOTH;
        float night = nightOn ? s.night() * v : 0F;
        PowerSuitEntity suit = visorOf(mc);
        float lamp = lampOn && suit != null && PowerSuitRenderer.isCockpitView(suit) ? s.lamp() * v : 0F;
        lastLevel = v;
        lastNight = night;
        lastLamp = lamp;

        // The lightmap is rebuilt once a tick (needsUpdate); only then are its inputs read, and vanilla has just reset
        // them. Writing on the other frames would stack on our own last value.
        LightmapRenderState lm = mc.gameRenderer.gameRenderState().lightmapRenderState;
        if (lm.needsUpdate && night > 0F) {
            int rgb = s.tint() == VisorConfig.VisionTint.VISOR ? VisorConfig.colour() : 0xFFFFFF;
            Vector3f tint = ARGB.vector3fFromRGB24(rgb);
            float max = Math.max(tint.x, Math.max(tint.y, tint.z));
            tint.div(Math.max(max, 0.01F)); // the floor's brightness is the strength; the colour only tints it
            // a night-vision potion that already lifts it further wins
            var c = lm.nightVisionColor;
            float vanilla = lm.nightVisionEffectIntensity * Math.max(c.x(), Math.max(c.y(), c.z()));
            if (vanilla < night) {
                lm.nightVisionEffectIntensity = night;
                lm.nightVisionColor = tint;
            }
        }

        if (lamp > 0.004F) {
            writeKnobs(lamp, event.getRenderState().cameraRenderState.projectionMatrix.m11());
            mc.gameRenderer.getRequestedPostEffects().add(LAMP_EFFECT);
        }
    }

    /**
     * The lamp shader's inputs, as the pixels of a 2x1 texture (a post effect's uniforms are fixed in its JSON):
     * pixel 0 = strength, tan(half the vertical field of view) / 2, range / 64; pixel 1 = the lamp's colour.
     */
    private static void writeKnobs(float strength, float projectionM11) {
        Minecraft mc = Minecraft.getInstance();
        if (knobs == null) {
            knobs = new DynamicTexture(() -> "visor lamp knobs", 2, 1, true);
            mc.getTextureManager().register(LAMP_KNOBS, knobs);
        }
        float tanHalf = projectionM11 > 0.01F ? 1F / projectionM11
                : (float) Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        int p0 = ARGB.color(255, byteOf(strength), byteOf(tanHalf / 2F), byteOf(LAMP_RANGE / 64F));
        if (p0 == knobsWritten) {
            return;
        }
        knobsWritten = p0;
        NativeImage px = knobs.getPixels();
        px.setPixel(0, 0, p0);
        px.setPixel(1, 0, 0xFF000000 | LAMP_COLOUR);
        knobs.upload();
    }

    private static int byteOf(float v) {
        return Mth.clamp(Math.round(v * 255F), 0, 255);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        level = levelO = 0F;
    }

    private VisorVision() {}
}
