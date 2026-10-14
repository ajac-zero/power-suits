package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ChainsawItem;
import dev.ajaczero.powerarmor.item.DrillItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where the working minds may dig and cut (design sec. 8). Server side.
 *
 * <p>Drill: an open hall at home level. The hall is the 3 block layers from the home block's floor up (the suit's
 * height), within the work radius of home. The mind may only remove blocks inside it, so it never digs down, never
 * raises the ceiling and leaves a wall wherever the next block touches lava or water, and it leaves whole columns
 * standing where the floor below is missing (it would step into the hole). An open hall rather than tunnels: allays
 * collect the drops, and they only see items in line of sight.
 *
 * <p>Chainsaw: natural trees (logs with natural leaves, ChainsawItem.treeAbove) whose trunk stands within the radius.
 */
public final class SuitWork {
    /** The hall is this many blocks high: the suit's height. */
    public static final int HALL_HEIGHT = 3;

    public static double radius() {
        return PowerArmorConfig.WORK_RADIUS.get();
    }

    /** Inside the hall's volume: home level to 2 above, within the radius (horizontally) of home. */
    public static boolean inHall(BlockPos home, BlockPos pos) {
        int dy = pos.getY() - home.getY();
        double dx = pos.getX() - home.getX(), dz = pos.getZ() - home.getZ();
        double r = radius();
        return dy >= 0 && dy < HALL_HEIGHT && dx * dx + dz * dz <= r * r;
    }

    /** May the drilling mind remove this block? In the hall, drillable, not next to a liquid, and with floor under it. */
    public static boolean mayDig(PowerArmorEntity suit, BlockPos pos) {
        BlockPos home = suit.getHome();
        Level level = suit.level();
        if (home == null || !inHall(home, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!DrillItem.canDrill(state, level, pos) || nextToLiquid(level, pos)) {
            return false;
        }
        BlockPos floor = new BlockPos(pos.getX(), home.getY() - 1, pos.getZ());
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP); // a hole below: leave the column
    }

    private static boolean nextToLiquid(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(pos.relative(d)).isEmpty()) {
                return true;
            }
        }
        return !level.getFluidState(pos).isEmpty();
    }

    /**
     * The next block to drill: the diggable block nearest home (so the hall grows outward from home in rings) that is
     * exposed (an open block beside it at the same height, where the drill can get at it). The middle layer is preferred:
     * a sideways 3x3 face centred there clears the whole height of the hall. Null: nothing left to dig.
     */
    @Nullable
    public static BlockPos nextDigTarget(PowerArmorEntity suit, Set<BlockPos> skip) {
        BlockPos home = suit.getHome();
        if (home == null) {
            return null;
        }
        int r = (int) Math.ceil(radius());
        List<BlockPos> columns = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= radius() * radius()) {
                    columns.add(home.offset(dx, 0, dz));
                }
            }
        }
        columns.sort(Comparator.comparingDouble((BlockPos c) -> c.distSqr(home)).thenComparingDouble(c -> c.distToCenterSqr(suit.position())));
        Level level = suit.level();
        for (BlockPos column : columns) {
            for (int dy : new int[] {1, 0, 2}) {
                BlockPos p = column.above(dy);
                if (!skip.contains(p) && mayDig(suit, p) && exposed(level, p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean exposed(Level level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(d)).getCollisionShape(level, pos.relative(d)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** May the sawing mind cut this? Natural leaves, or a log of a natural tree (not a log cabin's wall). */
    public static boolean mayCut(Level level, BlockPos pos, BlockState state) {
        if (ChainsawItem.isNaturalLeaves(state)) {
            return true;
        }
        return state.is(BlockTags.LOGS) && !ChainsawItem.treeAbove(level, pos).isEmpty();
    }

    /** The trunk base (lowest log, standing on solid ground) of the natural tree nearest the suit within the radius of
     *  home, a few blocks above or below home level. Null: no tree left. */
    @Nullable
    public static BlockPos nextTrunk(PowerArmorEntity suit, Set<BlockPos> skip) {
        BlockPos home = suit.getHome();
        if (home == null) {
            return null;
        }
        Level level = suit.level();
        int r = (int) Math.ceil(radius());
        List<BlockPos> bases = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-r, -3, -r), home.offset(r, 4, r))) {
            double dx = p.getX() - home.getX(), dz = p.getZ() - home.getZ();
            if (dx * dx + dz * dz > radius() * radius() || !level.getBlockState(p).is(BlockTags.LOGS)) {
                continue;
            }
            BlockPos below = p.below();
            BlockState ground = level.getBlockState(below);
            if (!ground.is(BlockTags.LOGS) && ground.isFaceSturdy(level, below, Direction.UP) && !skip.contains(p)) {
                bases.add(p.immutable());
            }
        }
        bases.sort(Comparator.comparingDouble(b -> b.distToCenterSqr(suit.position())));
        for (BlockPos base : bases) {
            if (!ChainsawItem.treeAbove(level, base).isEmpty()) {
                return base;
            }
        }
        return null;
    }

    /** The first block the suit's eye sees on the way to {@code aim} (what the tool would hit), or null. */
    @Nullable
    public static BlockPos firstBlockToward(PowerArmorEntity suit, Vec3 aim) {
        Vec3 eye = suit.getEyePosition();
        Vec3 end = aim.add(aim.subtract(eye).normalize().scale(0.5));
        BlockHitResult hit = suit.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, suit));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private SuitWork() {}
}
