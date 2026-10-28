package dev.ajaczero.powersuits.entity;

import net.minecraft.util.Mth;

/**
 * Eases the suit over step-ups and snapped step-downs. Those move the suit a whole block (or two) in one
 * tick; this keeps an offset (blocks) that cancels the jump and shrinks back to zero over a few ticks. The
 * rendered model AND the pilot's seat (camera) both add it, so they glide together.
 *
 * <p>Minecraft draws an entity at {@code lerp(partialTick, yo, y)}, which already moves it from the old to
 * the new height during the tick. So the offset only cancels the jump at the END of the tick ({@link #current});
 * the start ({@link #previous}) stays what the last tick ended with. Shifting the start too (as an earlier
 * version did) dropped the model a block below the camera for the first frames of every step: the arm
 * flickered while walking up and down blocks. Pure logic, so GameTests can check it frame by frame.
 */
public final class StepSmoothing {
    /** Each tick the offset keeps this fraction (0.65 -> ~6 ticks to settle). */
    public static final float EASE = 0.65F;
    /** Vertical moves smaller than this are ordinary walking, not a step. */
    private static final double MIN_STEP = 0.2;

    private final double maxStep;
    private float offset, offsetO;
    private boolean wasOnGround;

    public StepSmoothing(double maxStep) {
        this.maxStep = maxStep;
    }

    /** Call once per tick, after the suit moved {@code dy} blocks this tick. */
    public void tick(double dy, boolean onGround) {
        this.offsetO = this.offset;
        if (this.wasOnGround && onGround && Math.abs(dy) > MIN_STEP && Math.abs(dy) <= this.maxStep + 0.1) {
            this.offset -= (float) dy;
        }
        this.wasOnGround = onGround;
        this.offset = Mth.clamp(this.offset * EASE, -2.5F, 2.5F);
        if (Math.abs(this.offset) < 0.01F) {
            this.offset = 0;
        }
    }

    /** Offset at the end of the latest tick (where the seat puts the pilot). */
    public float current() {
        return this.offset;
    }

    /** Offset at the start of the latest tick. */
    public float previous() {
        return this.offsetO;
    }

    /** Offset for a render frame inside the latest tick. */
    public float at(float partialTick) {
        return Mth.lerp(partialTick, this.offsetO, this.offset);
    }
}
