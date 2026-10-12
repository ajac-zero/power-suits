package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Where socket tools fire from and what the pilot is aiming at. Works on both sides. */
public final class SocketAim {
    // Muzzle in the torso's frame, in blocks (measured in Blockbench from the cannon's muzzle_flash bone;
    // the EM projector's tip is within a few pixels): right of centre, up from the feet, forward.
    // The torso pivot is 1.54 up; the torso faces the view and pitches with it (55%).
    private static final double MUZZLE_RIGHT = 0.95, MUZZLE_UP = 1.73, MUZZLE_FORWARD = 1.71, TORSO_PIVOT_UP = 1.54;

    public static Vec3 muzzlePosition(PowerArmorEntity suit, Player pilot) {
        float yaw = pilot.getYRot() * Mth.DEG_TO_RAD;
        float lean = Mth.clamp(pilot.getXRot() * 0.55F, -25F, 25F) * Mth.DEG_TO_RAD; // + = looking down
        double up = MUZZLE_UP - TORSO_PIVOT_UP;
        double leanedForward = MUZZLE_FORWARD * Mth.cos(lean) + up * Mth.sin(lean);
        double leanedUp = up * Mth.cos(lean) - MUZZLE_FORWARD * Mth.sin(lean);
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return suit.position().add(0, TORSO_PIVOT_UP + leanedUp, 0).add(forward.scale(leanedForward)).add(right.scale(MUZZLE_RIGHT));
    }

    /** What's under the pilot's crosshair: the first entity or block along the view, or a far point. */
    public static Vec3 aimPoint(PowerArmorEntity suit, Player pilot, double range) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 blockEnd = blockHit(suit, pilot, range);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(suit.level(), pilot, eye, blockEnd,
                new AABB(eye, blockEnd).inflate(1), e -> e != suit && e != pilot && e.isPickable() && !e.isSpectator());
        return entityHit != null ? entityHit.getLocation() : blockEnd;
    }

    /** The first entity along the view (not behind a wall) that passes the filter. Hit boxes are grown
     *  by {@code inflate} so small things like dropped items are easy to point at. */
    @Nullable
    public static Entity entityInView(PowerArmorEntity suit, Player pilot, double range, Predicate<Entity> filter, float inflate) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 blockEnd = blockHit(suit, pilot, range);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(suit.level(), pilot, eye, blockEnd,
                new AABB(eye, blockEnd).inflate(inflate + 1), e -> e != suit && e != pilot && filter.test(e), inflate);
        return hit == null ? null : hit.getEntity();
    }

    private static Vec3 blockHit(PowerArmorEntity suit, Player pilot, double range) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 end = eye.add(pilot.getViewVector(1F).scale(range));
        return suit.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pilot)).getLocation();
    }

    private SocketAim() {}
}
