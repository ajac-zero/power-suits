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
    /** Classic terminal green (user, 2026-10-27): the colour of the visor's readouts and of the glass rim's light. */
    public static final String DEFAULT_COLOUR = "#5AFF6E";

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> COLOUR = B
            .comment("The visor's colour as #RRGGBB: its readouts and the light along the helmet's edge. Warnings stay red.",
                    "Try #5AFF6E (terminal green, the default), #E4ECE8 (white), #8EC2FF (blue), #FFBA3C (amber).")
            .translation("powerarmor.configuration.visor_colour")
            .define("visor_colour", DEFAULT_COLOUR, v -> v instanceof String s && parse(s) >= 0);

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
