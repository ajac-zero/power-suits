package dev.ajaczero.powerarmor.client;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * The pilot's own visor settings, saved in {@code config/power_suits-client.toml} (Mods > Power Armor > Config, or edit
 * the file; changes show at once). A CLIENT config: it lives on each player's computer and never reaches a server, so
 * every player picks their own colour and nobody else sees it. (The balance numbers are a different file:
 * PowerArmorConfig.)
 */
public final class VisorConfig {
    /** Classic terminal green (user, 2026-10-27): the colour of the visor's readouts. */
    public static final String DEFAULT_COLOUR = "#5AFF6E";

    /**
     * The visor colours to pick from without typing a hex code (user, 2026-10-27); CUSTOM uses visor_colour. A
     * TranslatableEnum so NeoForge's config screen labels the dropdown with the name drawn in its own colour.
     */
    public enum Preset implements TranslatableEnum {
        GREEN(0x5AFF6E), AMBER(0xFFBA3C), BLUE(0x8EC2FF), WHITE(0xE4ECE8), RED(0xFF5A4A), CUSTOM(-1);

        /** 0xRRGGBB, or -1 for CUSTOM (the hex field). */
        public final int rgb;

        Preset(int rgb) {
            this.rgb = rgb;
        }

        @Override
        public Component getTranslatedName() {
            var name = Component.translatable("power_suits.configuration.visor_preset." + name().toLowerCase(Locale.ROOT));
            return rgb >= 0 ? name.withColor(rgb) : name;
        }
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Preset> PRESET = B
            .comment("The visor's colour: GREEN (terminal green, the default), AMBER, BLUE, WHITE, RED, or CUSTOM (visor_colour below).")
            .translation("power_suits.configuration.visor_preset")
            .defineEnum("visor_preset", Preset.GREEN);

    public static final ModConfigSpec.ConfigValue<String> COLOUR = B
            .comment("Your own visor colour as #RRGGBB, used when visor_preset is CUSTOM: its readouts and the light along the helmet's edge.",
                    "Warnings use it too (inverted). Something bright reads best: #5AFF6E is the default green.")
            .translation("power_suits.configuration.visor_colour")
            .define("visor_colour", DEFAULT_COLOUR, v -> v instanceof String s && parse(s) >= 0);

    /** What the visor can do in the dark (VisorVision). The game uses NIGHT_VISION (or OFF, the key); the lamp is parked
     *  for a later upgrade (user, 2026-10-27), only the dev scene turns it on. */
    public enum Vision { OFF, NIGHT_VISION, LAMP, BOTH }

    /** Whose colour the night vision takes: the visor's (night-vision goggles) or none (plain grey, like the potion). */
    public enum VisionTint { VISOR, WHITE }

    public static final ModConfigSpec.BooleanValue NIGHT_VISION_ON = B
            .comment("Night vision on or off: the powered visor lifts the dark around you when it's dark (caves, nights).",
                    "The night vision key (N) flips it; the HUD shows NV under the compass while it's on. Only on your screen.")
            .translation("power_suits.configuration.visor_night_vision_on")
            .define("visor_night_vision_on", true);

    public static final ModConfigSpec.DoubleValue NIGHT_VISION = B
            .comment("How far the night vision lifts the dark, 0..1 (vanilla's potion is about 0.6: everything bright).")
            .translation("power_suits.configuration.visor_night_vision")
            .defineInRange("visor_night_vision", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.EnumValue<VisionTint> VISION_TINT = B
            .comment("The night vision's colour: VISOR (the visor colour, like night-vision goggles) or WHITE.")
            .translation("power_suits.configuration.visor_vision_tint")
            .defineEnum("visor_vision_tint", VisionTint.VISOR);

    public static final ModConfigSpec SPEC = B.build();

    /** The configured colour as 0xRRGGBB: the preset's, or with CUSTOM the hex field's (the default if unreadable). */
    public static int colour() {
        Preset preset = PRESET.get();
        if (preset != Preset.CUSTOM) {
            return preset.rgb;
        }
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
