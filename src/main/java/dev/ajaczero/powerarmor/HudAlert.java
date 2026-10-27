package dev.ajaczero.powerarmor;

/**
 * How urgent a readout on the pilot's visor is (user, 2026-10-27): the visor colour while all's well, orange as a
 * warning (half gone), red when critical (the last 15%, or the thing can't be used: an overheated chaingun). The
 * readouts' colours come from {@code client/hud/HudView}; the levels live here, on the shared side, so the tools'
 * numbers ({@code ToolEffects.hud}) and the tests can name them.
 */
public enum HudAlert {
    NONE, WARNING, CRITICAL;

    /** Warning from half gone, critical in the last 15%. */
    public static final float WARNING_AT = 0.5F, CRITICAL_AT = 0.15F;

    /** Something that runs out (the hull): how much is left, 1 = all. */
    public static HudAlert ofLeft(float left) {
        return left <= CRITICAL_AT ? CRITICAL : left <= WARNING_AT ? WARNING : NONE;
    }

    /** Something that fills up towards trouble (the chaingun's heat): how full it is, 1 = the limit. */
    public static HudAlert ofFill(float fill) {
        return ofLeft(1F - fill);
    }

    /** The more urgent of the two. */
    public HudAlert max(HudAlert other) {
        return other.ordinal() > this.ordinal() ? other : this;
    }

    public boolean atLeast(HudAlert other) {
        return this.ordinal() >= other.ordinal();
    }
}
