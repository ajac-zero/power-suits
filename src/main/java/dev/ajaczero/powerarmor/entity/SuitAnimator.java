package dev.ajaczero.powerarmor.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import dev.ajaczero.powerarmor.item.PowerTool;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Client: how the suit moves on this client. It measures how fast the suit really goes (and along which way its legs
 * point), keeps track of jumps, falls and landings, eases the model and the camera over steps, and from all that picks
 * the animation every GeckoLib controller plays and how fast the gait steps (so planted feet don't slide). Also the
 * torso's aim blend, the right hand's reach and the gait's hip bob for the cockpit viewmodel.
 *
 * One of the suit's parts (see PowerArmorEntity): {@code suit.animator()}. Only the client ticks it; the server has
 * nothing to draw. The movement itself (riding, walking, water, jumping) stays in PowerArmorEntity: this only watches.
 */
public final class SuitAnimator {
    // ---- Gaits ------------------------------------------------------------------------------
    /** The ground speed each Blockbench animation was authored for (blocks/s, measured from the planted foot). Playing
     *  them at (real speed along the legs) / authored speed keeps the feet from sliding. Walk v6: 1.0 s cycle, 22 px
     *  stance; the game walks faster (SuitDriving.WALK_BLOCKS_PER_SECOND), so the walk steps quicker. */
    private static final double WALK_ANIM_AUTHORED = 2.218, RUN_ANIM_AUTHORED = 6.249;
    /** Step-rate limits: below, a near-stop would freeze mid-step; above, a walk would flail before the run takes over. */
    private static final double MIN_STEP_RATE = 0.25, MAX_STEP_RATE = 2.2;
    /** Smoothed speed along the legs below this (blocks/s, backwards) plays the walk in reverse. */
    private static final double BACKING_UP_SPEED = -0.3;
    /** Average hip height (model px) of the walk / run cycles: yHip mean in a_walk.js / a_run.js. */
    private static final float WALK_HIP_MEAN = -0.29F;
    private static final float RUN_HIP_MEAN = -0.3F;
    // ---- Airborne tuning (client-side animation only) ----------------------------------------
    /** Ticks in the air before switching to the fall pose (stepping off one block shouldn't). */
    private static final int FALL_POSE_AFTER_TICKS = 6;
    /** Ticks in the air needed to play the heavy landing (a jump, or a drop of ~2+ blocks). */
    private static final int LAND_AFTER_TICKS = 10;
    /** Length of the land animation (0.6 s), and how much of it to keep when walking away. */
    private static final int LAND_TICKS = 12;
    private static final int LAND_TICKS_WHILE_MOVING = 5;
    /** How long the jump animation is forced after release, covering the delay + takeoff. */
    private static final int JUMP_ANIM_HOLD_TICKS = 6;
    // ---- Animations (names from the exported Blockbench animation file) --------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.power_suit.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.power_suit.walk");
    /** The walk with its keys reversed in time (made in Blockbench from the walk): backing up steps backwards. */
    private static final RawAnimation WALK_BACK = RawAnimation.begin().thenLoop("animation.power_suit.walk_back");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.power_suit.run");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.power_suit.death");
    private static final RawAnimation HIT = RawAnimation.begin().thenPlay("animation.power_suit.hit");
    // jump plays once (push-off + tuck) and flows straight into the airborne loop
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlay("animation.power_suit.jump").thenLoop("animation.power_suit.fall");
    private static final RawAnimation FALL = RawAnimation.begin().thenLoop("animation.power_suit.fall");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlay("animation.power_suit.land");
    private static final RawAnimation JUMP_CHARGE = RawAnimation.begin().thenPlayAndHold("animation.power_suit.jump_charge");
    // Out of fuel: slump and hold. powered_off is the last frame of shutdown, for suits that were already off.
    private static final RawAnimation SHUTDOWN = RawAnimation.begin().thenPlayAndHold("animation.power_suit.shutdown");
    private static final RawAnimation POWERED_OFF = RawAnimation.begin().thenPlayAndHold("animation.power_suit.powered_off");
    private static final RawAnimation STARTUP = RawAnimation.begin().thenPlay("animation.power_suit.startup");
    // Struck by lightning: convulse (the electrified loop, played once) and then slump like an empty tank. thenPlay would
    // use the JSON's loop setting ("loop": true for electrified) and convulse forever; PLAY_ONCE moves on to the slump.
    private static final RawAnimation ZAPPED = RawAnimation.begin()
            .then("animation.power_suit.electrified", LoopType.PLAY_ONCE)
            .thenPlayAndHold("animation.power_suit.shutdown");
    private static final RawAnimation TOOL_RECOIL = RawAnimation.begin().thenPlay("animation.power_suit.tool_recoil");
    // Right hand (a_hand.js): carry pose while the pilot holds something, quick reach on every use.
    private static final RawAnimation HOLD_ITEM = RawAnimation.begin().thenLoop("animation.power_suit.hold_item");
    private static final RawAnimation USE_ITEM = RawAnimation.begin().thenPlay("animation.power_suit.use_item");

    private final PowerArmorEntity suit;
    /** Client only: smoothed horizontal speed in blocks per second, used to pick walk/run/idle. */
    private double visualSpeed;
    /** Client only: the same along the legs (where the boots point; negative = backing up), for the step rate. */
    private double visualForward;
    /** Client only: vertical offset (blocks) that eases the camera and model over step-ups/downs. */
    private final StepSmoothing steps = new StepSmoothing(SuitDriving.STEP_DOWN);
    /** Client only: airborne bookkeeping for the jump / fall / land animations. */
    private int airTicks;
    private boolean jumped;
    private int landTicks;
    /** Client only: jump bar charge of the local pilot while holding space (set by PilotView). */
    private float clientJumpCharge;
    /** Client only: forces the jump animation for a few ticks after release. */
    private int jumpAnimTicks;
    private int lastJumpCounter;
    /** Client: how far the animated hips are below/above the gait's average this frame (model px). */
    private float hipBob;
    /** Client: when setHipHeight last ran (System.nanoTime), so the easing goes by time, not by frames. */
    private long hipBobAt;
    /** Client: 0..1, how much the torso follows the pilot's view (eases out on shutdown, in after startup). */
    private float aimBlend;
    /** Client: the pilot's arm was mid-swing last tick (to catch the start of each swing). */
    private boolean pilotWasSwinging;

    SuitAnimator(PowerArmorEntity suit) {
        this.suit = suit;
    }

    /** Client, every tick. */
    void tick() {
        // The torso follows the view of whoever drives: the pilot's, or the head of the mind (what it looks at).
        float aimTarget = this.suit.isDriven() && this.suit.canOperate() ? 1F : 0F;
        this.aimBlend += (aimTarget - this.aimBlend) * 0.2F;
        this.tickHandSwing();
        // Measure how fast we actually move (blocks/s) and smooth it to avoid flicker
        // between idle and walk.
        double dx = this.suit.getX() - this.suit.xo, dz = this.suit.getZ() - this.suit.zo;
        double speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
        this.visualSpeed += (speed - this.visualSpeed) * 0.4;
        float legs = this.suit.yBodyRot * Mth.DEG_TO_RAD;
        double forward = (dx * -Mth.sin(legs) + dz * Mth.cos(legs)) * 20.0;
        this.visualForward += (forward - this.visualForward) * 0.4;
        this.tickStepSmoothing();
        this.tickAirborneAnimationState();
    }

    /** Both sides: the pilot rides this much higher or lower while a step is being eased (0 on the server). */
    double stepNow() {
        return this.steps.current();
    }

    /** Client: the pilot released jump (the jump bar): play the jump for a few ticks, covering the launch delay. */
    void jumpStarted() {
        this.jumpAnimTicks = JUMP_ANIM_HOLD_TICKS;
    }

    private void tickAirborneAnimationState() {
        if (this.landTicks > 0) {
            this.landTicks--;
        }
        if (this.jumpAnimTicks > 0) {
            this.jumpAnimTicks--;
        }
        // Another player's suit jumped (the pilot's own client already started it in onPlayerJump).
        int counter = this.suit.getEntityData().get(PowerArmorEntity.JUMP_COUNTER);
        if (this.suit.tickCount < 5) {
            this.lastJumpCounter = counter; // just came into view: don't replay an old jump
        }
        if (counter != this.lastJumpCounter) {
            this.lastJumpCounter = counter;
            if (!this.suit.isLocalInstanceAuthoritative()) {
                this.jumpAnimTicks = JUMP_ANIM_HOLD_TICKS;
            }
        }
        if (!this.suit.isVehicle()) {
            this.clientJumpCharge = 0;
        }
        if (!this.suit.onGround()) {
            if (this.airTicks == 0) {
                // Just left the ground. Rising fast means a jump (stepping up a block keeps us on the
                // ground, so it never gets here).
                this.jumped = this.suit.getY() - this.suit.yo > 0.15;
            }
            this.airTicks++;
        } else {
            if (this.airTicks >= LAND_AFTER_TICKS) {
                this.landTicks = LAND_TICKS;
                SuitStomp.landingDust(this.suit, this.airTicks);
            }
            this.airTicks = 0;
            this.jumped = false;
        }
    }
    private boolean isRunning() {
        if (this.suit.getControllingPassenger() instanceof Player pilot) {
            return pilot.isSprinting();
        }
        return this.visualSpeed > SuitDriving.RUN_THRESHOLD; // the mind has no sprint key: between the walk and the sprint
    }
    private boolean isWalking() {
        return this.visualSpeed > 0.3;
    }
    /** The suit's GeckoLib controllers and what each plays (PowerArmorEntity.registerControllers passes them here). */
    void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Controller 1: full-body locomotion. 5 ticks of blending between animations.
        controllers.add(new AnimationController<>("movement", 5, this::movementAnimation));
        // Controller 2: one-shot upper-body reactions, started from the server with triggerAnim().
        // It is added after "movement", so for the bones it animates it wins; legs keep walking.
        controllers.add(new AnimationController<PowerArmorEntity>("action", 2, state -> PlayState.STOP)
                .triggerableAnim("hit", HIT)
                .triggerableAnim("tool_recoil", TOOL_RECOIL)); // the EM projector's push
        // Controller 3: the socket tool's own loop (drill spin, chainsaw chain) while it runs. It keys only
        // the tool's bones, so the arm keeps following walk/run and the aim.
        controllers.add(new AnimationController<>("tool", 3, this::toolAnimation));
        // Controller 4: the right hand. Added last, so while it plays it owns the right arm (the gait's arm
        // swing pauses, like a person carrying a torch). Stops when there's nothing to hold or no power.
        controllers.add(new AnimationController<>("right_hand", 4, this::handAnimation)
                .triggerableAnim("use_item", USE_ITEM));
    }
    private PlayState movementAnimation(AnimationTest<PowerArmorEntity> state) {
        // GeckoLib 5 advances the animation by (time passed x speed) every frame, so the speed may change any time.
        state.controller().setAnimationSpeed(this.movementAnimationRate());
        if (this.suit.isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (!this.suit.isPowered()) {
            state.controller().setTransitionTicks(this.suit.isStunned() ? 1 : 4); // a strike jolts, it doesn't ease in
            return state.setAndContinue(this.suit.wasOffWhenLoaded() ? POWERED_OFF : this.suit.isStunned() ? ZAPPED : SHUTDOWN);
        }
        if (this.suit.startupTicks() > 0) {
            state.controller().setTransitionTicks(2);
            return state.setAndContinue(STARTUP);
        }
        // Powered means driven: by the pilot, or by the machine mind (same gaits, picked from how fast it moves).
        // Jump states blend fast (the charge crouch ends exactly where the jump starts).
        boolean jumpState = this.jumpAnimTicks > 0 || this.jumped || (this.clientJumpCharge > 0 && this.suit.onGround());
        state.controller().setTransitionTicks(jumpState ? 2 : 5);
        if (this.jumpAnimTicks > 0 || this.jumped) {
            return state.setAndContinue(JUMP);
        }
        if (this.clientJumpCharge > 0 && this.suit.onGround()) {
            return state.setAndContinue(JUMP_CHARGE);
        }
        if (this.airTicks >= FALL_POSE_AFTER_TICKS) {
            return state.setAndContinue(FALL);
        }
        boolean landing = this.landTicks > 0 && !(this.isWalking() && this.landTicks <= LAND_TICKS - LAND_TICKS_WHILE_MOVING);
        if (landing) {
            return state.setAndContinue(LAND);
        }
        if (this.isWalking() || this.suit.driving().isRealigning()) {
            return state.setAndContinue(this.isRunning() ? RUN : this.isBackingUp() ? WALK_BACK : WALK);
        }
        return state.setAndContinue(IDLE);
    }
    private PlayState toolAnimation(AnimationTest<PowerArmorEntity> state) {
        if (this.suit.socket().isRunning() && this.suit.getSocketStack().getItem() instanceof PowerTool tool && tool.runAnimation() != null) {
            return state.setAndContinue(tool.runAnimation());
        }
        return PlayState.STOP;
    }
    private PlayState handAnimation(AnimationTest<PowerArmorEntity> state) {
        if (this.suit.isDeadOrDying() || !this.suit.canOperate() || !(this.suit.getControllingPassenger() instanceof Player pilot)
                || pilot.getMainHandItem().isEmpty()) {
            return PlayState.STOP;
        }
        return state.setAndContinue(HOLD_ITEM);
    }
    /** Client: the pilot swung their hand (placed a block, used an item on something): reach with the suit's hand.
     *  Swings of other players arrive as packets, so everyone sees it. */
    private void tickHandSwing() {
        boolean swinging = this.suit.getControllingPassenger() instanceof Player pilot && pilot.isSwinging()
                && pilot.getCurrentSwing() != null && pilot.getCurrentSwing().hand() == InteractionHand.MAIN_HAND;
        if (swinging && !this.pilotWasSwinging && this.suit.canOperate()) {
            this.suit.triggerAnim("right_hand", "use_item");
        }
        this.pilotWasSwinging = swinging;
    }
    /** Client/dev: the movement animation's playback rate this tick (the gait log compares it with the ground speed). */
    public double getMovementAnimationRate() {
        return this.movementAnimationRate();
    }
    /**
     * The walk and run step at the speed the suit really moves along its legs, so a planted foot stays planted: strafing,
     * diagonals, slowing down, slopes, water, walls and the mind's own speeds all change the ground speed, and a fixed
     * rate slid the feet (runAutopilotGait measured 0.4-1.4 blocks/s of slide, 3.15 when backing up with the walk
     * playing forwards: a moonwalk). Backing up plays walk_back at the backward speed.
     */
    private double movementAnimationRate() {
        if (this.suit.isDeadOrDying() || !this.suit.isDriven() || !this.suit.canOperate() || this.jumped || this.jumpAnimTicks > 0
                || this.airTicks >= FALL_POSE_AFTER_TICKS || this.landTicks > 0) {
            return 1.0;
        }
        if (!this.isWalking()) {
            double water = this.suit.isInWater() ? SuitDriving.UNDERWATER_SPEED : this.suit.isInLava() ? SuitDriving.LAVA_SPEED : 1.0;
            return (this.suit.driving().isRealigning() ? 0.6 : 1.0) * water; // turning on the spot: a slower step cycle
        }
        double authored = this.isRunning() ? RUN_ANIM_AUTHORED : WALK_ANIM_AUTHORED;
        return Mth.clamp(Math.abs(this.visualForward) / authored, MIN_STEP_RATE, MAX_STEP_RATE);
    }
    /** Client/dev: the ground speed along the legs (blocks/s, negative = backwards) the gait the suit picks this tick
     *  steps at: a planted foot slides by the difference to the real speed (GaitScene). GeckoLib's controller still
     *  names the previous animation during the 5-tick blend, so the log asks this instead. */
    public double getSteppedSpeed() {
        if (!this.suit.isDriven() || !this.suit.canOperate() || !(this.isWalking() || this.suit.driving().isRealigning()) || this.jumped || this.jumpAnimTicks > 0
                || this.airTicks >= FALL_POSE_AFTER_TICKS) {
            return 0;
        }
        double authored = this.isRunning() ? RUN_ANIM_AUTHORED : this.isBackingUp() ? -WALK_ANIM_AUTHORED : WALK_ANIM_AUTHORED;
        return authored * this.movementAnimationRate();
    }
    /** Client: moving backwards along the legs (the legs keep facing the view when the pilot backs up). */
    private boolean isBackingUp() {
        return this.visualForward < BACKING_UP_SPEED;
    }
    /** Client/dev: name of the animation the movement controller is playing, or "-". */
    public String getMovementAnimationName() {
        var manager = this.suit.getAnimatableInstanceCache().getManagerForId(this.suit.getId());
        var controller = manager == null ? null : manager.getAnimationControllers().get("movement");
        var current = controller == null ? null : controller.getCurrentAnimationPoint();
        return current == null ? "-" : current.animation().name().replace("animation.power_suit.", "");
    }
    /** Client: how hard the engine works, 0 idle .. 1 sprinting, +0.3 while a tool runs (engine sound). */
    public float engineLoad() {
        float load = this.isWalking() ? (this.isRunning() ? 1F : 0.55F) : 0F;
        return this.suit.socket().isRunning() ? load + 0.3F : load;
    }
    /** Client: smoothed ground speed in blocks/s (for camera effects). */
    public double getVisualSpeed() {
        return this.visualSpeed;
    }
    /**
     * Client: called by the renderer every frame with the root bone's animated height (PowerArmorRenderer.aim). Only the
     * walk and run cycles bob the cockpit viewmodel (CockpitHands.bob), measured against their own average hip height
     * (from the specs).
     *
     * This is the one place where drawing writes back into the entity (GeckoLib 5 otherwise only reads a snapshot of it):
     * the hip height exists only in the posed model, and nothing else needs it. The easing goes by elapsed time: it used
     * to move 35% of the way per call, so it followed the hips faster at 144 fps than at 60 and jumped again whenever the
     * suit was drawn twice in a frame (the menu's preview). Now 35% per 1/60 s, and a second call in a frame does nothing.
     */
    public void setHipHeight(float y) {
        String anim = this.getMovementAnimationName();
        float target = switch (anim) {
            case "walk", "walk_back" -> y - WALK_HIP_MEAN;
            case "run" -> y - RUN_HIP_MEAN;
            default -> 0F;
        };
        long now = System.nanoTime();
        // elapsed time in 60ths of a second (capped: after a pause or with the suit off screen, just catch up)
        float frames = this.hipBobAt == 0L ? 1F : Math.min(30F, (now - this.hipBobAt) * 60F / 1.0E9F);
        this.hipBobAt = now;
        float ease = 1F - (float) Math.pow(0.65, frames);
        this.hipBob += (target - this.hipBob) * ease; // eases in/out when the gait changes
    }
    public float getHipBob() {
        return this.hipBob;
    }
    /** Client: eased step offset in blocks for this frame. */
    public float getStepOffset(float partialTick) {
        return this.steps.at(partialTick);
    }
    /** Client/dev: ticks spent airborne (animation bookkeeping). */
    public int getAirTicks() {
        return this.airTicks;
    }
    /** Client: the local pilot's jump bar charge (0 = not charging). */
    public void setClientJumpCharge(float charge) {
        this.clientJumpCharge = charge;
    }
    /** Client: 0..1 weight of the torso following the pilot's view. */
    public float getAimBlend() {
        return this.aimBlend;
    }
    /** Step-ups (and snapped step-downs) move us a whole block in one tick: ease that (see StepSmoothing). */
    private void tickStepSmoothing() {
        this.steps.tick(this.suit.getY() - this.suit.yo, this.suit.onGround());
    }
}
