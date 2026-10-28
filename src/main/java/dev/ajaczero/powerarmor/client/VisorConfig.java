package dev.ajaczero.powerarmor.client;

import java.util.Locale;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The pilot's own visor settings, saved in {@code config/powerarmor-client.toml} (Mods > Power Armor > Config, or edit
 * the file; changes show at once). A CLIENT config: it lives on each player's computer and never reaches a server, so
 * every player picks their own colour and nobody else sees it. (The balance numbers are a different file:
 * PowerArmorConfig.)
 */
public final class VisorConfig {
    /** Classic terminal green (user, 2026-10-27): the colour of the visor's readouts. */
    public static final String DEFAULT_COLOUR = "#5AFF6E";

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> COLOUR = B
            .comment("The visor's colour as #RRGGBB: its readouts and the light along the helmet's edge. Warnings use it too (inverted).",
                    "Try #5AFF6E (terminal green, the default), #E4ECE8 (white), #8EC2FF (blue), #FFBA3C (amber).")
            .translation("powerarmor.configuration.visor_colour")
            .define("visor_colour", DEFAULT_COLOUR, v -> v instanceof String s && parse(s) >= 0);

    /** What the visor can do in the dark (VisorVision). The game uses NIGHT_VISION (or OFF, the key); the lamp is parked
     *  for a later upgrade (user, 2026-10-27), only the dev scene turns it on. */
    public enum Vision { OFF, NIGHT_VISION, LAMP, BOTH }

    /** Whose colour the night vision takes: the visor's (night-vision goggles) or none (plain grey, like the potion). */
    public enum VisionTint { VISOR, WHITE }

    public static final ModConfigSpec.BooleanValue NIGHT_VISION_ON = B
            .comment("Night vision on or off: the powered visor lifts the dark around you when it's dark (caves, nights).",
                    "The night vision key (N) flips it; the HUD shows NV under the compass while it's on. Only on your screen.")
            .translation("powerarmor.configuration.visor_night_vision_on")
            .define("visor_night_vision_on", true);

    public static final ModConfigSpec.DoubleValue NIGHT_VISION = B
            .comment("How far the night vision lifts the dark, 0..1 (vanilla's potion is about 0.6: everything bright).")
            .translation("powerarmor.configuration.visor_night_vision")
            .defineInRange("visor_night_vision", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.EnumValue<VisionTint> VISION_TINT = B
            .comment("The night vision's colour: VISOR (the visor colour, like night-vision goggles) or WHITE.")
            .translation("powerarmor.configuration.visor_vision_tint")
            .defineEnum("visor_vision_tint", VisionTint.VISOR);

    public static final ModConfigSpec SPEC = B.build();

    /** The configured colour as 0xRRGGBB (the default if the file holds something unreadable). */
    public static int colour() {
        int rgb = parse(COLOUR.get());
        return rgb >= 0 ? rgb : parse(DEFAULT_COLOUR);
    }

    /** The same colour scaled toward black: the unlit segments of a bar (about a third as bright reads as empty). */
    public static int dim(int rgb, float k) {
        return ARGB.color(0, (int) (ARGB.red(rgb) * k), (int) (ARGB.green(rgb) * k), (int) (ARGB.blue(rgb) * k));
    }

    /** "#RRGGBB" (the # optional) -> 0xRRGGBB, or -1 if it isn't one. */
    static int parse(String s) {
        String hex = s.trim().toUpperCase(Locale.ROOT);
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("[0-9A-F]{6}")) {
            return -1;
        }
        return Integer.parseInt(hex, 16);
    }

    private VisorConfig() {}
}
