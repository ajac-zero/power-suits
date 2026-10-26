package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.PowerTool;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The left-arm socket and the power tool in it, as the suit works it: the trigger (the pilot's right click, or the
 * machine mind's), the cooldown between uses, continuous tools held and released, each tool's per-suit state, and the
 * synced values clients draw the tool from (raised, running, spin, heat, shots, the EM beam's catch, the rail beam's end).
 *
 * One of the suit's parts (see PowerArmorEntity): {@code suit.socket()}. What a tool does lives in its item
 * (item/PowerTool and its implementations); this class only decides when it runs. The server ticks it ({@link #tick});
 * the synced getters work on both sides.
 */
public final class SuitSocket {
    private final PowerArmorEntity suit;
    /** Server: the pilot is holding the tool's button (right click, sent by the client, see ToolInput / ToolTriggerPayload). */
    private boolean triggerHeld;
    /** Server: the machine mind holds the tool's trigger (its goals aim the suit first). */
    private boolean aiTrigger;
    /** Server: ticks until the socket tool may be used again. */
    private int cooldown;
    /** Server: how many times the socket tool has been used (tests and dev logs). */
    private int uses;
    /** Server: the continuous tool currently held down (to release it when anything changes), and who started it (a pilot
     *  taking over from the mind mid-use interrupts it). */
    @Nullable
    private PowerTool activeContinuousTool;
    @Nullable
    private LivingEntity activeOperator;
    /** Server: a tool's per-suit state (items are shared by every stack, so state can't live on the item). */
    @Nullable
    private Object toolData;
    /** Server: the tool was in use last tick (for the raise sound). */
    private boolean wasActive;

    SuitSocket(PowerArmorEntity suit) {
        this.suit = suit;
    }

    /** Both sides: the power tool in the socket, or null (empty, or something else). */
    @Nullable
    public PowerTool tool() {
        return this.suit.getSocketStack().getItem() instanceof PowerTool tool ? tool : null;
    }

    // ---- Server: the trigger and the tool's tick --------------------------------------------------------------------

    /** Server: the pilot pressed or released the tool's button (right click). Anyone else (or a forged packet) is ignored. */
    public void setTriggerHeld(Player who, boolean down) {
        if (who != this.suit.getControllingPassenger()) {
            return;
        }
        if (down && !this.triggerHeld && !(this.serverTool() != null && this.suit.canOperate())) {
            // Nothing in the socket, or no power: the trigger just clicks.
            this.suit.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F);
        }
        this.triggerHeld = down;
    }

    public boolean isTriggerHeld() {
        return this.triggerHeld;
    }

    /** Server: the mind pulls or releases the tool's trigger (it aims the suit's head first: setAiAim). */
    public void setAiTrigger(boolean down) {
        this.aiTrigger = down;
    }

    public boolean isAiTriggerHeld() {
        return this.aiTrigger;
    }

    /** Server: the tool in the real slot (the synced copy can lag a tick behind). */
    @Nullable
    private PowerTool serverTool() {
        return this.suit.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT).getItem() instanceof PowerTool tool ? tool : null;
    }

    /** Server: run the socket tool. Repeating tools fire each time they're ready while the trigger is held;
     *  continuous tools get a tick while held and a release when the trigger goes up or anything interrupts. */
    void tick() {
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        PowerTool tool = this.serverTool();
        // The pilot works the tool with their trigger; with nobody inside, the machine mind with its own.
        LivingEntity operator = this.suit.toolOperator();
        boolean trigger = operator instanceof Player ? this.triggerHeld : this.aiTrigger;
        if (tool != null) {
            tool.serverTick(this.suit);
        }
        boolean active = operator != null && trigger && tool != null && this.suit.canOperate();
        this.suit.getEntityData().set(PowerArmorEntity.TOOL_RUNNING, tool != null && tool.isContinuous() && (active || tool.keepsRunning(this.suit)));
        this.suit.getEntityData().set(PowerArmorEntity.TOOL_ACTIVE, active);
        if (active && !this.wasActive) {
            // The arm swings up into the aiming pose (PowerArmorRenderer.raiseTool): a heavy mechanical clunk and a hiss.
            this.suit.playSound(SoundEvents.PISTON_CONTRACT, 0.5F, 0.7F);
            this.suit.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.15F, 1.9F);
        }
        this.wasActive = active;
        if (this.activeContinuousTool != null && (this.activeContinuousTool != tool || !active)) {
            boolean interrupted = this.activeContinuousTool != tool || operator == null || operator != this.activeOperator
                    || !this.suit.canOperate();
            this.activeContinuousTool.release(this.suit, operator == this.activeOperator ? operator : null, interrupted);
            this.activeContinuousTool = null;
            this.activeOperator = null;
        }
        if (!(operator instanceof Player)) {
            this.triggerHeld = false; // the pilot left with the button down
        }
        if (operator != this.suit) {
            this.aiTrigger = false; // the mind lost control (pilot, power) with its trigger down
        }
        if (!active) {
            return;
        }
        var stack = this.suit.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT);
        if (tool.isContinuous()) {
            this.activeContinuousTool = tool;
            this.activeOperator = operator;
            tool.holdTick(this.suit, operator, stack);
            return;
        }
        if (this.cooldown > 0) {
            return;
        }
        int cost = tool.energyPerUse();
        if (!this.suit.power().hasEnergyFor(cost)) {
            this.suit.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F); // not enough fuel for a shot
            this.cooldown = tool.cooldownTicks();
            return;
        }
        if (tool.use(this.suit, operator, stack)) {
            this.suit.power().consume(cost);
            this.uses++;
        }
        this.cooldown = tool.cooldownTicks(); // also after a dry click: one click per cooldown
    }

    /** Server: the suit is leaving the world (unloaded, packed, destroyed): let go of a held tool, or the EM beam's catch
     *  would float forever. */
    void onRemoved() {
        if (this.activeContinuousTool != null) {
            this.activeContinuousTool.release(this.suit, null, true);
            this.activeContinuousTool = null;
            this.activeOperator = null;
        }
    }

    /** Server: a tool's state for this suit, created on first use (another tool's state is replaced). */
    @SuppressWarnings("unchecked")
    public <T> T toolData(Class<T> type, Supplier<T> create) {
        if (!type.isInstance(this.toolData)) {
            this.toolData = create.get();
        }
        return (T) this.toolData;
    }

    public boolean isReady() {
        return this.cooldown == 0;
    }

    public void setCooldown(int ticks) {
        this.cooldown = ticks;
    }

    /** Server: count a use made by a continuous tool (the suit counts repeating tools' uses itself). */
    public void countUse() {
        this.uses++;
    }

    public int getUses() {
        return this.uses;
    }

    // ---- Synced: what clients draw the tool from ----------------------------------------------------------------------

    /** Both sides: someone is using the socket tool (synced): the tool arm raises. */
    public boolean isActive() {
        return this.suit.getEntityData().get(PowerArmorEntity.TOOL_ACTIVE);
    }

    /** Both sides: a continuous socket tool is running (synced): its loop sound and animation. */
    public boolean isRunning() {
        return this.suit.getEntityData().get(PowerArmorEntity.TOOL_RUNNING);
    }

    /** Server: the tool's spin (chaingun barrels; the rail cannon's charge) and heat, both 0..1, for every client. */
    public void setSpin(float spin, float heat) {
        this.suit.getEntityData().set(PowerArmorEntity.TOOL_SPIN, spin);
        this.suit.getEntityData().set(PowerArmorEntity.TOOL_HEAT, heat);
    }

    /** Both sides: the tool's spin, 0 (at rest) .. 1 (full speed); the rail cannon sends its charge in it. */
    public float getSpin() {
        return this.suit.getEntityData().get(PowerArmorEntity.TOOL_SPIN);
    }

    /** Both sides: the chaingun's barrel heat, 0 (cold) .. 1 (overheated). */
    public float getHeat() {
        return this.suit.getEntityData().get(PowerArmorEntity.TOOL_HEAT);
    }

    /** Server: a shot was fired (bumps the synced counter: recoil, muzzle burst, the rail beam). */
    public void countShot() {
        this.suit.getEntityData().set(PowerArmorEntity.TOOL_SHOTS, this.getShots() + 1);
    }

    /** Both sides: shots fired so far (synced; clients watch it change). */
    public int getShots() {
        return this.suit.getEntityData().get(PowerArmorEntity.TOOL_SHOTS);
    }

    /** Server: where the rail cannon's beam ended (set before countShot, so clients get both in one update). */
    public void setRailBeamEnd(Vec3 end) {
        this.suit.getEntityData().set(PowerArmorEntity.RAIL_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
    }

    /** Both sides: where the rail cannon's last beam ended. */
    public Vec3 getRailBeamEnd() {
        Vector3fc end = this.suit.getEntityData().get(PowerArmorEntity.RAIL_END);
        return new Vec3(end.x(), end.y(), end.z());
    }

    /** Server: what the EM beam holds (synced for the beam effect). */
    public void setBeamTarget(@Nullable Entity target) {
        this.suit.getEntityData().set(PowerArmorEntity.BEAM_TARGET, target == null ? -1 : target.getId());
    }

    /** Both sides: the entity in the EM beam, if any. */
    @Nullable
    public Entity getBeamTarget() {
        int id = this.suit.getEntityData().get(PowerArmorEntity.BEAM_TARGET);
        return id < 0 ? null : this.suit.level().getEntity(id);
    }
}
