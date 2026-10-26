package dev.ajaczero.powerarmor.item;



/**
 * The socket tools' lights: each is a glowmask texture (client/SuitGlowLayer maps them to their files, made by
 * tools/make-glowmasks.py from the reserved colours) drawn full-bright over the tool by the suit and the cockpit
 * viewmodel. A tool's ToolEffects sets the brightness of its own lights every frame.
 */
public enum ToolLight {
    /** The chaingun's barrel tips, red- to white-hot with heat. */
    HEAT(true),
    /** The chaingun's barrels behind the tips, only once they're really hot. */
    HEAT_BARREL(true),
    /** The EM projector's lens and vents. */
    EM_CORE(false),
    /** The EM projector's prong tips. */
    EM_ARC(false),
    /** The flamethrower's pilot light. */
    PILOT(false),
    /** The rail cannon's core rings, a third each: lit in turn, the energy runs along the core. */
    RAIL_FLOW_0(true),
    RAIL_FLOW_1(true),
    RAIL_FLOW_2(true),
    /** The emitter in the rail cannon's band. */
    RAIL_EMIT(false);

    /** Fades in by opacity instead of brightness: light over cold metal or over nothing (see SuitGlowLayer.energy). The
     *  others are opaque lights whose colour is dimmed (SuitGlowLayer.tint). */
    public final boolean fadesByAlpha;

    ToolLight(boolean fadesByAlpha) {
        this.fadesByAlpha = fadesByAlpha;
    }
}
