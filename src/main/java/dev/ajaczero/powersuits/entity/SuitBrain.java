package dev.ajaczero.powersuits.entity;

import com.mojang.authlib.GameProfile;
import dev.ajaczero.powersuits.PowerSuitsConfig;
import dev.ajaczero.powersuits.entity.ai.SuitGoals;
import dev.ajaczero.powersuits.entity.ai.ToolUse;
import dev.ajaczero.powersuits.item.HelmetStyle;
import dev.ajaczero.powersuits.item.PowerTool;
import dev.ajaczero.powersuits.registry.ModDataComponents;
import dev.ajaczero.powersuits.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * The machine mind's state (design sec. 8): who it works for (the last pilot), where home is (where the pilot got out),
 * where it aims, which job the tool in its socket gives it (and that tool's goals), and a rogue's lifetime. The mind's
 * behaviour itself is the goals in entity/ai (SuitGoals: what every mind does; each tool's own: PowerTool.mindGoals);
 * they read and set this.
 *
 * One of the suit's parts (see PowerSuitEntity): {@code suit.brain()}. Server only: none of it is synced (the visor
 * colour comes from the suit's synced SuitMind, friendly or rogue).
 */
public final class SuitBrain {
    /** The FakePlayer UUID for suits nobody owns. */
    private static final UUID WILD_SUIT_ID = UUID.fromString("6d1c1b8e-3f0a-4c55-9d7e-5a2f0b9c1e01");

    private final PowerSuitEntity suit;
    /** Server: the mind's owner (the last pilot, or who deployed it) and home (where the pilot got out). */
    @Nullable
    private UUID ownerId;
    @Nullable
    private BlockPos home;
    /** Server: where the goals want the head (and so the torso and the tool) to point this tick, or null. */
    @Nullable
    private Vec3 aim;
    /** Server: ticks this rogue has been around (saved). */
    private int rogueAge;
    /** Server: the tool whose goals the mind has right now (null: none), and those goals (updateToolGoals). */
    @Nullable
    private PowerTool goalsTool;
    private List<Goal> toolGoals = List.of();

    SuitBrain(PowerSuitEntity suit) {
        this.suit = suit;
    }

    // ---- Owner and home ------------------------------------------------------------------------------------------

    /** Server: the player the mind works for (null: nobody yet, e.g. a rebooted wild suit). */
    @Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }

    public void setOwnerId(@Nullable UUID owner) {
        this.ownerId = owner;
    }

    /** Server: the owner if they're online in this dimension (not spectating), else null. */
    @Nullable
    public Player getOwner() {
        if (this.ownerId == null || !(this.suit.level() instanceof ServerLevel level)) {
            return null;
        }
        Player owner = level.getPlayerByUUID(this.ownerId);
        return owner != null && owner.isAlive() && !owner.isSpectator() ? owner : null;
    }

    /** Server: the block the mind's jobs are anchored to (null: none, e.g. spawned by a command). */
    @Nullable
    public BlockPos getHome() {
        return this.home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home == null ? null : home.immutable();
    }

    /** Server: further than {@code slack} blocks (horizontally) from home. */
    public boolean isAwayFromHome(double slack) {
        if (this.home == null) {
            return false;
        }
        double dx = this.suit.getX() - (this.home.getX() + 0.5), dz = this.suit.getZ() - (this.home.getZ() + 0.5);
        return dx * dx + dz * dz > slack * slack || Math.abs(this.suit.getY() - this.home.getY()) > 1.5;
    }

    /** Server: a pilot climbed in: they own the suit now (every later pilot takes it over: the mind works for the last
     *  one), and the mind lets go of everything. */
    void pilotBoarded(Player pilot) {
        this.ownerId = pilot.getUUID();
    }

    /** Server: the pilot got out: the mind takes over and makes this block its home. */
    void pilotLeft(Player pilot) {
        this.ownerId = pilot.getUUID();
        this.home = this.suit.blockPosition();
    }

    /** Server: a rogue's mind was rebooted by lightning: nobody's (the first pilot claims it), no home. */
    void forget() {
        this.ownerId = null;
        this.home = null;
    }

    // ---- Aim ----------------------------------------------------------------------------------------------------

    /** Server: the point the mind aims the head (and so the torso and the tool) at, or null to let the look goals
     *  wander. A machine: it snaps onto the target instead of easing round like an animal. */
    public void setAim(@Nullable Vec3 target) {
        this.aim = target;
    }

    /** Server/dev: where the mind aims right now (autopilot logs), or null. */
    @Nullable
    public Vec3 getAim() {
        return this.aim;
    }

    /** Server, after the goals and before the look control (PowerSuitEntity.customServerAiStep): vanilla's look control
     *  resets the pitch and eases the head back toward the body every tick unless something asks it to look somewhere,
     *  so an aim set by the mind wins; the tool fires later in the same tick (SuitSocket.tick). */
    void applyAim() {
        if (!this.suit.isAiDriven()) {
            this.aim = null;
        } else if (this.aim != null) {
            this.suit.getLookControl().setLookAt(this.aim.x, this.aim.y, this.aim.z, 180F, 180F);
        }
    }

    // ---- Jobs: what the tool in the socket lets the mind do --------------------------------------------------------

    /**
     * Server, every tick (at the end of it, outside the goal selector's own loop): when the socket tool changed, forget
     * the old tool's goals and learn the new one's (PowerTool.mindGoals). Removing a running goal stops it first (its
     * stop() lets go of the trigger and the aim). A tool in the slot of a suit just loaded counts as a change.
     */
    void updateToolGoals() {
        PowerTool tool = this.suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).getItem() instanceof PowerTool t ? t : null;
        if (tool == this.goalsTool) {
            return;
        }
        this.toolGoals.forEach(this.suit.goals()::removeGoal);
        this.goalsTool = tool;
        this.toolGoals = tool == null ? List.of() : List.copyOf(tool.mindGoals(this.suit));
        this.toolGoals.forEach(goal -> this.suit.goals().addGoal(SuitGoals.TOOL_JOB, goal));
    }

    /** Server/tests: the socket tool's own goals the mind has right now (updateToolGoals). */
    public List<Goal> toolGoals() {
        return this.toolGoals;
    }

    /** Both sides: how the mind uses the tool in the socket (NONE without a power tool). */
    public ToolUse toolUse() {
        PowerTool tool = this.suit.socket().tool();
        return tool == null ? ToolUse.NONE : tool.mindUse();
    }

    /** Both sides: a weapon the mind fights with by aiming and holding the trigger (chaingun, flamethrower). */
    public boolean hasGun() {
        return this.toolUse() == ToolUse.GUN;
    }

    /** Both sides: the gunner's job (design sec. 8): a friendly mind with a gun follows its owner and defends them. */
    public boolean followsOwner() {
        return !this.suit.isRogue() && this.hasGun();
    }

    /** Both sides: the EM guard job (design sec. 8): a friendly mind with an EM projector guards the area round home. */
    public boolean guardsHome() {
        return !this.suit.isRogue() && this.toolUse() == ToolUse.EM;
    }

    /** Both sides: the rail guard job (Phase 11): a friendly mind with a rail cannon shoots from home at what comes near. */
    public boolean snipesFromHome() {
        return !this.suit.isRogue() && this.toolUse() == ToolUse.RAIL;
    }

    /** Both sides: the drill job: a friendly mind with the drill clears an open hall round home (SuitWork). */
    public boolean digsHall() {
        return !this.suit.isRogue() && this.toolUse() == ToolUse.DRILL;
    }

    /** Both sides: the chainsaw job: a friendly mind with the chainsaw fells the natural trees round home. */
    public boolean fellsTrees() {
        return !this.suit.isRogue() && this.toolUse() == ToolUse.SAW;
    }

    /** Server: the mind's bare-handed blow (no bullets left): a pilot's punch, 1 + the melee bonus, with a shove. */
    public void punch(LivingEntity target) {
        float damage = 1F + PowerSuitsConfig.MELEE_BONUS.get().floatValue();
        this.suit.triggerAnim("right_hand", "use_item"); // the right arm reaches out (the hand's use animation)
        this.suit.swingForAttack(InteractionHand.MAIN_HAND);
        DamageSource blow = this.suit.damageSources().mobAttack(this.suit);
        if (target.level() instanceof ServerLevel level && target.hurtServer(level, blow, damage)) {
            Vec3 push = target.position().subtract(this.suit.position()).multiply(1, 0, 1).normalize();
            target.knockback(0.6, -push.x, -push.z, blow, damage);
            this.suit.setLastHurtMob(target);
        }
        this.suit.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.8F);
    }

    /**
     * Server: the player a block is broken as. Breaking needs a player: claim mods veto by who breaks (BreakEvent), and
     * loot tables and XP want one. With the mind driving, that's a FakePlayer (NeoForge's stand-in player that never
     * joins the game) carrying the owner's UUID, so claims treat the suit like its owner.
     */
    public Player blockBreaker(LivingEntity operator) {
        if (operator instanceof Player player) {
            return player;
        }
        UUID id = this.ownerId != null ? this.ownerId : WILD_SUIT_ID;
        Player fake = FakePlayerFactory.get((ServerLevel) this.suit.level(), new GameProfile(id, "[Power Suit]"));
        fake.snapTo(this.suit.getX(), this.suit.getY(), this.suit.getZ(), this.suit.getYHeadRot(), this.suit.getXRot()); // mods may check where it stands
        return fake;
    }

    /** Server/dev: the mind's running goals, e.g. "ReturnHome/LookAtPlayerGoal" (autopilot logs). */
    public String runningGoalNames() {
        return this.suit.goals().getAvailableGoals().stream().filter(g -> g.isRunning())
                .map(g -> g.getGoal().getClass().getSimpleName().isEmpty() ? g.getGoal().getClass().getSuperclass().getSimpleName() : g.getGoal().getClass().getSimpleName())
                .reduce((a, b) -> a + "/" + b).orElse("-");
    }

    // ---- Rogues (design sec. 8) -----------------------------------------------------------------------------------

    /** Server, every tick (at the end of it): a rogue leaves after a while. */
    void tick() {
        if (!this.suit.isRogue()) {
            this.rogueAge = 0;
            return;
        }
        this.rogueAge++;
        if (this.mayRogueLeave()) {
            // like the wandering trader: gone once nobody is near enough to watch it go
            if (this.suit.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, this.suit.getX(), this.suit.getY() + 1.5, this.suit.getZ(), 20, 0.5, 1, 0.5, 0.02);
            }
            this.suit.discard();
        }
    }

    /** Server, every tick (first thing, before the engine looks for power): a rogue always wears a helmet and a
     *  backpack, whatever made it. */
    void equipRogue() {
        if (!this.suit.isRogue()) {
            return;
        }
        SimpleContainer slots = this.suit.getInventory();
        if (slots.getItem(PowerSuitEntity.HELMET_SLOT).isEmpty()) {
            // Rogues always wear one (/summon, or a rogue from before the helmet existed): its mind lives in it. A
            // defeated rogue still drops only its tool; one rebooted by lightning keeps it.
            ItemStack helmet = new ItemStack(ModItems.SUIT_HELMET.get());
            if (this.suit.getRandom().nextInt(4) == 0) {
                // one rogue in four wears a styled helmet (design sec. 4c): only a look, and not loot (rogues drop their tool only)
                HelmetStyle[] styles = HelmetStyle.values();
                helmet.set(ModDataComponents.HELMET_STYLE.get(), styles[this.suit.getRandom().nextInt(styles.length)]);
            }
            slots.setItem(PowerSuitEntity.HELMET_SLOT, helmet);
        }
        if (slots.getItem(PowerSuitEntity.ENGINE_SLOT).isEmpty()) {
            // Suits come without an engine now, and a rogue needs a backpack to look the part (it runs on nothing
            // anyway). Not loot either (a defeated rogue drops its tool only), but one rebooted by lightning keeps it,
            // so its coal can burn.
            slots.setItem(PowerSuitEntity.ENGINE_SLOT, new ItemStack(ModItems.FIREBOX.get()));
        }
    }

    /** Server: old enough, and no player within 64 blocks (checked once a second). */
    public boolean mayRogueLeave() {
        return this.suit.isRogue() && this.rogueAge >= PowerSuitsConfig.ROGUE_LIFETIME.get() && this.suit.tickCount % 20 == 0
                && this.suit.level().getNearestPlayer(this.suit, 64) == null;
    }

    public void setRogueAge(int ticks) {
        this.rogueAge = ticks;
    }

    // ---- Saving ---------------------------------------------------------------------------------------------------

    void load(ValueInput tag) {
        this.ownerId = tag.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.home = tag.read("Home", BlockPos.CODEC).orElse(null);
        this.rogueAge = tag.getIntOr("RogueAge", 0);
    }

    void save(ValueOutput tag) {
        tag.putInt("RogueAge", this.rogueAge);
        tag.storeNullable("Owner", UUIDUtil.CODEC, this.ownerId); // int array, as before
        tag.storeNullable("Home", BlockPos.CODEC, this.home);
    }
}
