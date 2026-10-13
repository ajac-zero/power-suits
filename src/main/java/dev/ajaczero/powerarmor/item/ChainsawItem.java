package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Fourth power tool: a chainsaw. Hold use (right click) on a wooden block (anything an axe is for) or leaves to cut it,
 * one block at a time at diamond-axe speed. Cut a log of a natural tree and everything of that tree above
 * the cut comes down too, top first, a few logs per tick. "Natural tree" = connected logs with natural
 * leaves (not placed by a player) attached, so log cabins and wooden builds only lose the block you cut.
 */
public class ChainsawItem extends Item implements PowerTool, GeoItem {
    // Fuel per log, the biggest tree it fells (default 256 logs; the biggest vanilla trees have ~200) and the
    // felling speed: PowerArmorConfig.
    public static final float SPEED = 8F;          // diamond axe
    public static final int BREAK_DELAY = 4;
    /** A tree needs at least this many natural leaf blocks touching its logs. */
    private static final int MIN_LEAVES = 4;
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.power_armor.chainsaw_run");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ChainsawItem(Properties properties) {
        super(properties);
    }

    /** Per suit: break progress + the tree that is coming down. */
    static final class State extends ToolMining.Progress {
        final Deque<BlockPos> falling = new ArrayDeque<>();
        @Nullable
        Player feller;

        State(PowerArmorEntity suit) {
            super(suit);
        }
    }

    public static boolean canCut(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0
                && (state.is(BlockTags.MINEABLE_WITH_AXE) && !state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL) || state.is(BlockTags.LEAVES));
    }

    /** Leaves that grew (vanilla marks player-placed leaves "persistent" so they never decay). */
    private static boolean isNaturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
    }

    /**
     * If the log at {@code cut} belongs to a natural tree: the tree's other logs at or above the cut, highest
     * first. Otherwise (a log cabin, a lone log) an empty list. Logs count as connected diagonally too
     * (26 neighbours), because branches of acacias and oaks often only touch at an edge.
     */
    public static List<BlockPos> treeAbove(Level level, BlockPos cut) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> todo = new ArrayDeque<>();
        List<BlockPos> logs = new ArrayList<>();
        seen.add(cut);
        todo.add(cut);
        int leaves = 0;
        Set<BlockPos> leavesSeen = new HashSet<>();
        while (!todo.isEmpty() && logs.size() < PowerArmorConfig.CHAINSAW_MAX_LOGS.get()) {
            BlockPos pos = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (leaves < MIN_LEAVES && isNaturalLeaves(level.getBlockState(n)) && leavesSeen.add(n)) {
                    leaves++;
                }
            }
            for (BlockPos n : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (n.getY() >= cut.getY() && !seen.contains(n) && level.getBlockState(n).is(BlockTags.LOGS)) {
                    BlockPos copy = n.immutable(); // betweenClosed reuses one mutable position
                    seen.add(copy);
                    todo.add(copy);
                    logs.add(copy);
                }
            }
        }
        if (leaves < MIN_LEAVES) {
            return List.of();
        }
        logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed()); // top first
        return logs;
    }

    // ---- PowerTool -------------------------------------------------------------------------------

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public int energyPerUse() {
        return PowerArmorConfig.CHAINSAW_ENERGY.get();
    }

    @Override
    public int cooldownTicks() {
        return BREAK_DELAY;
    }

    @Override
    public String suitBone() {
        return "chainsaw";
    }

    @Override
    public RawAnimation runAnimation() {
        return RUN;
    }

    @Override
    public net.minecraft.sounds.SoundEvent runSound() {
        return dev.ajaczero.powerarmor.registry.ModSounds.CHAINSAW.get();
    }

    @Override
    public boolean keepsRunning(PowerArmorEntity suit) {
        return !suit.toolData(State.class, () -> new State(suit)).falling.isEmpty();
    }

    @Override
    public boolean use(PowerArmorEntity suit, Player pilot, ItemStack tool) {
        return false; // continuous: see holdTick
    }

    @Override
    public void holdTick(PowerArmorEntity suit, Player pilot, ItemStack tool) {
        if (!(suit.level() instanceof ServerLevel level)) {
            return;
        }
        State state = suit.toolData(State.class, () -> new State(suit));
        state.ticks++;
        BlockHitResult hit = ToolMining.target(suit, pilot);
        if (hit == null || !suit.isToolReady()) {
            state.clear(level);
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState block = level.getBlockState(pos);
        state.retarget(level, pos, hit.getDirection());
        if (!canCut(block, level, pos)) {
            if (state.ticks % 10 == 0) { // stone, metal: the chain skids off
                suit.playSound(SoundEvents.GRINDSTONE_USE, 0.4F, 1.6F);
            }
            return;
        }
        float hardness = block.getDestroySpeed(level, pos);
        state.progress += hardness <= 0 ? 1F : SPEED / hardness / ToolMining.TICKS_PER_HARDNESS;
        ToolMining.grindEffects(level, pos, hit.getLocation(), state.ticks);
        if (state.progress < 1F) {
            state.showCracks(level, List.of(pos));
            return;
        }
        state.clear(level);
        int cost = this.energyPerUse();
        if (!suit.hasEnergyFor(cost)) {
            suit.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F);
            return;
        }
        // Look for the tree before the cut: afterwards the log that joins it all is gone.
        List<BlockPos> tree = block.is(BlockTags.LOGS) ? treeAbove(level, pos) : List.of();
        if (ToolMining.breakBlock(level, pos, pilot, new ItemStack(Items.DIAMOND_AXE))) {
            suit.tryUseEnergy(cost);
            suit.countToolUse();
            suit.setToolCooldown(BREAK_DELAY);
            if (!tree.isEmpty()) {
                state.falling.addAll(tree);
                state.feller = pilot;
                suit.playSound(SoundEvents.WOOD_BREAK, 1.5F, 0.6F);
            }
        }
    }

    @Override
    public void release(PowerArmorEntity suit, @Nullable Player pilot, boolean interrupted) {
        if (suit.level() instanceof ServerLevel level) {
            suit.toolData(State.class, () -> new State(suit)).clear(level);
        }
    }

    /** The tree keeps coming down after the trigger is released, while the suit has power and fuel. */
    @Override
    public void serverTick(PowerArmorEntity suit) {
        State state = suit.toolData(State.class, () -> new State(suit));
        if (state.falling.isEmpty() || !(suit.level() instanceof ServerLevel level)) {
            return;
        }
        Player feller = state.feller;
        if (feller == null || feller.isRemoved() || !suit.canOperate()) {
            state.falling.clear(); // the rest of the tree stays up; cut it again to finish
            return;
        }
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        int cost = PowerArmorConfig.CHAINSAW_ENERGY.get();
        for (int i = 0; i < PowerArmorConfig.CHAINSAW_LOGS_PER_TICK.get() && !state.falling.isEmpty(); i++) {
            BlockPos pos = state.falling.poll();
            if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                continue; // someone else took it meanwhile
            }
            if (!suit.hasEnergyFor(cost)) {
                state.falling.clear();
                suit.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F);
                return;
            }
            if (ToolMining.breakBlock(level, pos, feller, axe)) {
                suit.tryUseEnergy(cost);
            }
        }
    }

    // ---- item --------------------------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.powerarmor.tool_on_foot"), true);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.powerarmor.chainsaw.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.powerarmor.chainsaw.stats", this.energyPerUse()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.powerarmor.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
