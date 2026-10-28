package dev.ajaczero.powersuits.item;

import com.geckolib.animation.RawAnimation;
import dev.ajaczero.powersuits.PowerSuitsConfig;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.entity.ai.DrillWorker;
import dev.ajaczero.powersuits.entity.ai.SuitWork;
import dev.ajaczero.powersuits.entity.ai.ToolUse;
import dev.ajaczero.powersuits.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Third power tool: a diamond-tipped 3x3 mining drill.
 * Hold use (right click) on a block: it digs the block you look at plus the 8 around it, in the plane facing you
 * (a wall gives a 3x3 tunnel face, the floor a 3x3 pit). Digs like a diamond pickaxe *and* shovel
 * (stone, ores, obsidian, dirt, sand, gravel...), never bedrock or other unbreakable blocks; wood and other
 * axe blocks are the chainsaw's job. The 9 blocks break together, as slowly as the hardest of them.
 */
public class DrillItem extends Item implements PowerTool, ModTooltip {
    // Fuel per block and the pause after each 3x3: PowerSuitsConfig.
    /** Mining speed of a diamond pickaxe / shovel (no enchantments). */
    public static final float SPEED = 8F;
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.power_suit.drill_run");


    public DrillItem(Properties properties) {
        super(properties);
    }

    /** Pickaxe and shovel blocks a diamond tool can harvest (so obsidian yes, bedrock no). */
    public static boolean canDrill(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0
                && (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))
                && !state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
    }

    /** The 3x3 around {@code center} in the plane of the face that was hit, centre first. */
    public static List<BlockPos> area(BlockPos center, Direction face) {
        List<BlockPos> out = new ArrayList<>(9);
        out.add(center);
        Direction.Axis axis = face.getAxis();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                out.add(switch (axis) {
                    case X -> center.offset(0, a, b);   // wall facing east/west
                    case Y -> center.offset(a, 0, b);   // floor or ceiling
                    case Z -> center.offset(a, b, 0);   // wall facing north/south
                });
            }
        }
        return out;
    }

    // ---- PowerTool -------------------------------------------------------------------------------

    @Override
    public ToolUse mindUse() {
        return ToolUse.DRILL;
    }

    @Override
    public List<Goal> mindGoals(PowerSuitEntity suit) {
        return List.of(new DrillWorker(suit));
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public int energyPerUse() {
        return PowerSuitsConfig.DRILL_ENERGY.get();
    }

    @Override
    public int cooldownTicks() {
        return PowerSuitsConfig.DRILL_PAUSE.get();
    }

    @Override
    public String suitBone() {
        return "drill";
    }

    @Override
    public RawAnimation runAnimation() {
        return RUN;
    }

    @Override
    public SoundEvent runSound() {
        return ModSounds.DRILL.get();
    }

    @Override
    public boolean use(PowerSuitEntity suit, LivingEntity operator, ItemStack tool) {
        return false; // continuous: see holdTick
    }

    @Override
    public void holdTick(PowerSuitEntity suit, LivingEntity operator, ItemStack tool) {
        if (!(suit.level() instanceof ServerLevel level)) {
            return;
        }
        ToolMining.Progress state = suit.socket().toolData(ToolMining.Progress.class, () -> new ToolMining.Progress(suit));
        state.ticks++;
        BlockHitResult hit = ToolMining.target(suit, operator);
        if (hit == null || !suit.socket().isReady()) {
            state.clear(level); // spinning in the air, or catching its breath after a 3x3
            return;
        }
        BlockPos center = hit.getBlockPos();
        state.retarget(level, center, hit.getDirection());
        // The machine mind only digs its hall (home level, 3 high, no liquids next door, floor below): SuitWork.mayDig.
        Predicate<BlockPos> allowed = operator instanceof Player ? p -> canDrill(level.getBlockState(p), level, p)
                : p -> SuitWork.mayDig(suit, p);
        if (!allowed.test(center)) {
            if (state.ticks % 10 == 0) { // wood, bedrock...: the bit just skids
                suit.playSound(SoundEvents.ANVIL_LAND, 0.25F, 1.8F);
            }
            return;
        }
        List<BlockPos> blocks = area(center, hit.getDirection()).stream().filter(allowed).toList();
        float hardest = 0;
        for (BlockPos p : blocks) {
            hardest = Math.max(hardest, level.getBlockState(p).getDestroySpeed(level, p));
        }
        state.progress += hardest <= 0 ? 1F : SPEED / hardest / ToolMining.TICKS_PER_HARDNESS;
        ToolMining.grindEffects(level, center, hit.getLocation(), state.ticks);
        if (state.progress < 1F) {
            state.showCracks(level, blocks);
            return;
        }
        // Done: break them all, paying per block, as far as the fuel goes.
        int cost = PowerSuitsConfig.DRILL_ENERGY.get();
        ItemStack bit = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        int broken = 0;
        Player breaker = suit.brain().blockBreaker(operator);
        for (BlockPos p : blocks) {
            if (!suit.power().hasEnergyFor(cost)) {
                suit.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F);
                break;
            }
            // Loot tables check the tool: dig dirt "with a shovel" so e.g. snow layers drop snowballs.
            ItemStack as = level.getBlockState(p).is(BlockTags.MINEABLE_WITH_PICKAXE) ? bit : shovel;
            if (ToolMining.breakBlock(level, p, breaker, as)) {
                suit.power().tryUseEnergy(cost);
                broken++;
            }
        }
        state.clear(level);
        if (broken > 0) {
            suit.socket().countUse();
            suit.socket().setCooldown(PowerSuitsConfig.DRILL_PAUSE.get()); // vanilla mining pauses 5 ticks between blocks
        }
    }

    @Override
    public void release(PowerSuitEntity suit, @Nullable LivingEntity operator, boolean interrupted) {
        if (suit.level() instanceof ServerLevel level) {
            suit.socket().toolData(ToolMining.Progress.class, () -> new ToolMining.Progress(suit)).clear(level);
        }
    }

    // ---- item --------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.power_suits.tool_on_foot"));
        }
        return InteractionResult.PASS;
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.power_suits.drill.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.power_suits.drill.stats", this.energyPerUse()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.power_suits.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
