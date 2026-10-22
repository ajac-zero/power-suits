package dev.ajaczero.powerarmor.client;

import com.geckolib.constant.dataticket.DataTicket;
import java.util.function.Consumer;

/**
 * What the suit's renderer needs to know about the suit this frame, copied from the entity while Minecraft "extracts"
 * the frame (GeckoLib 5 / Minecraft 1.21.2+ render from a snapshot, the render state, not from the entity itself: by the
 * time the model is drawn the entity may have ticked again). Each value travels in the render state under one of these
 * tickets; PowerArmorRenderer.addRenderData fills them, the renderer and its layers read them.
 */
final class SuitRenderData {
    /** Drawn from inside: the local pilot's own suit in first person (only the legs show). */
    static final DataTicket<Boolean> COCKPIT = DataTicket.create("powerarmor_cockpit", Boolean.class);
    /** The installed tool's bone (PowerTool.suitBone), or "" for the bare socket. */
    static final DataTicket<String> TOOL_BONE = DataTicket.create("powerarmor_tool_bone", String.class);
    /** The helmet is on (else its bone isn't drawn). */
    static final DataTicket<Boolean> HELMET = DataTicket.create("powerarmor_helmet", Boolean.class);
    /** The worn helmet's style (HelmetStyle ordinal, -1 = standard): which helmet bone is drawn. */
    static final DataTicket<Integer> HELMET_STYLE = DataTicket.create("powerarmor_helmet_style", Integer.class);
    /** The installed engine (SuitEngine ordinal, -1 = none): which backpack bone is drawn. */
    static final DataTicket<Integer> ENGINE = DataTicket.create("powerarmor_engine", Integer.class);
    /** The plates' paint: a dye id 0..15, or -1 for bare steel (PowerArmorModel picks the texture by it). */
    static final DataTicket<Integer> PAINT = DataTicket.create("powerarmor_paint", Integer.class);
    /** Smooths step-ups/downs (PowerArmorEntity.getStepOffset), blocks. */
    static final DataTicket<Float> STEP_OFFSET = DataTicket.create("powerarmor_step_offset", Float.class);
    /** Driven and alive: the torso aims and the tool arm can raise (parked suits keep the rest pose). */
    static final DataTicket<Boolean> AIMING = DataTicket.create("powerarmor_aiming", Boolean.class);
    /** Fades the aim out while the suit powers down, 0..1. */
    static final DataTicket<Float> AIM_BLEND = DataTicket.create("powerarmor_aim_blend", Float.class);
    /** Tool arm raise 0 (hip carry) .. 1 (aiming), and the recoil kick 0..1, both for this frame. */
    static final DataTicket<Float> TOOL_RAISE = DataTicket.create("powerarmor_tool_raise", Float.class);
    static final DataTicket<Float> RECOIL = DataTicket.create("powerarmor_recoil", Float.class);
    /** The chaingun barrels' angle, degrees. */
    static final DataTicket<Float> BARREL_ANGLE = DataTicket.create("powerarmor_barrel_angle", Float.class);
    /** Hands the gait's hip height (model pixels) back to the suit when the model is posed (the cockpit camera bob). */
    @SuppressWarnings("unchecked")
    static final DataTicket<Consumer<Float>> HIP_SINK = DataTicket.create("powerarmor_hip_sink", (Class<Consumer<Float>>) (Class<?>) Consumer.class);
    /** The lights (SuitGlowLayer): one packed ARGB tint per glowmask, 0 = off. */
    static final DataTicket<int[]> GLOW = DataTicket.create("powerarmor_glow", int[].class);

    private SuitRenderData() {}
}
