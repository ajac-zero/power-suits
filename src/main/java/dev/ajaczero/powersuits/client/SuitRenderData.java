package dev.ajaczero.powersuits.client;

import com.geckolib.constant.dataticket.DataTicket;
import java.util.function.Consumer;

/**
 * What the suit's renderer needs to know about the suit this frame, copied from the entity while Minecraft "extracts"
 * the frame (GeckoLib 5 / Minecraft 1.21.2+ render from a snapshot, the render state, not from the entity itself: by the
 * time the model is drawn the entity may have ticked again). Each value travels in the render state under one of these
 * tickets; PowerSuitRenderer.addRenderData fills them, the renderer and its layers read them.
 */
final class SuitRenderData {
    /** Drawn from inside: the local pilot's own suit in first person (only the legs show). */
    static final DataTicket<Boolean> COCKPIT = DataTicket.create("power_suits_cockpit", Boolean.class);
    /** The installed tool's bone (PowerTool.suitBone), or "" for the bare socket. */
    static final DataTicket<String> TOOL_BONE = DataTicket.create("power_suits_tool_bone", String.class);
    /** The helmet is on (else its bone isn't drawn). */
    static final DataTicket<Boolean> HELMET = DataTicket.create("power_suits_helmet", Boolean.class);
    /** The worn helmet's style (HelmetStyle ordinal, -1 = standard): which helmet bone is drawn. */
    static final DataTicket<Integer> HELMET_STYLE = DataTicket.create("power_suits_helmet_style", Integer.class);
    /** The installed engine (SuitEngine ordinal, -1 = none): which backpack bone is drawn. */
    static final DataTicket<Integer> ENGINE = DataTicket.create("power_suits_engine", Integer.class);
    /** The plates' paint: a dye id 0..15, or -1 for bare steel (PowerSuitModel picks the texture by it). */
    static final DataTicket<Integer> PAINT = DataTicket.create("power_suits_paint", Integer.class);
    /** Smooths step-ups/downs (PowerSuitEntity.getStepOffset), blocks. */
    static final DataTicket<Float> STEP_OFFSET = DataTicket.create("power_suits_step_offset", Float.class);
    /** Driven and alive: the torso aims and the tool arm can raise (parked suits keep the rest pose). */
    static final DataTicket<Boolean> AIMING = DataTicket.create("power_suits_aiming", Boolean.class);
    /** Fades the aim out while the suit powers down, 0..1. */
    static final DataTicket<Float> AIM_BLEND = DataTicket.create("power_suits_aim_blend", Float.class);
    /** Tool arm raise 0 (hip carry) .. 1 (aiming), and the recoil kick 0..1, both for this frame. */
    static final DataTicket<Float> TOOL_RAISE = DataTicket.create("power_suits_tool_raise", Float.class);
    static final DataTicket<Float> RECOIL = DataTicket.create("power_suits_recoil", Float.class);
    /** The chaingun barrels' angle, degrees. */
    static final DataTicket<Float> BARREL_ANGLE = DataTicket.create("power_suits_barrel_angle", Float.class);
    /** Hands the gait's hip height (model pixels) back to the suit when the model is posed, for the cockpit viewmodel's
     *  bob. The only write-back from drawing into the entity (see PowerSuitEntity.setHipHeight); keep it the only one. */
    @SuppressWarnings("unchecked")
    static final DataTicket<Consumer<Float>> HIP_SINK = DataTicket.create("power_suits_hip_sink", (Class<Consumer<Float>>) (Class<?>) Consumer.class);
    /** The lights (SuitGlowLayer): one packed ARGB tint per glowmask, 0 = off. */
    static final DataTicket<int[]> GLOW = DataTicket.create("power_suits_glow", int[].class);
    /** The rail cannon's charge (0..1) and the time in ticks: the cannon shakes as it charges (ToolRotors.railShake). */
    static final DataTicket<float[]> RAIL_SHAKE = DataTicket.create("power_suits_rail_shake", float[].class);

    private SuitRenderData() {}
}
