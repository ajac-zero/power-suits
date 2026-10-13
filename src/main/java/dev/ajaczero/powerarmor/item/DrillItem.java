package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Third power tool: a diamond-tipped 3x3 mining drill.
 * Hold attack on a block: it digs the block you look at plus the 8 around it, in the plane facing you
 * (a wall gives a 3x3 tunnel face, the floor a 3x3 pit). Digs like a diamond pickaxe *and* shovel
 * (stone, ores, obsidian, dirt, sand, gravel...), never bedrock or other unbreakable blocks; wood and other
 * axe blocks are the chainsaw's job. The 9 blocks break together, as slowly as the hardest of them.
 */
public class DrillItem extends Item implements PowerTool, GeoItem {
    public static final int ENERGY_PER_BLOCK = 60;
    /** Mining speed of a diamond pickaxe / shovel (no enchantments). */
    public static final float SPEED = 8F;
    /** Pause after each 3x3 before the next one starts (vanilla mining has a 5-tick delay between blocks). */
    public static final int BREAK_DELAY = 4;
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.power_armor.drill_run");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

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
    public boolean isContinuous() {
        return true;
    }

    @Override
    public int energyPerUse() {
        return ENERGY_PER_BLOCK;
    }

    @Override
    public int cooldownTicks() {
        return BREAK_DELAY;
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
    public boolean use(PowerArmorEntity suit, Player pilot, ItemStack tool) {
        return false; // continuous: see holdTick
    }

    @Override
    public void holdTick(PowerArmorEntity suit, Player pilot, ItemStack tool) {
        if (!(suit.level() instanceof ServerLevel level)) {
            return;
        }
        ToolMining.Progress state = suit.toolData(ToolMining.Progress.class, () -> new ToolMining.Progress(suit));
        state.ticks++;
        BlockHitResult hit = ToolMining.target(suit, pilot);
        if (hit == null || !suit.isToolReady()) {
            state.clear(level); // spinning in the air, or catching its breath after a 3x3
            return;
        }
        BlockPos center = hit.getBlockPos();
        state.retarget(level, center, hit.getDirection());
        if (!canDrill(level.getBlockState(center), level, center)) {
            if (state.ticks % 10 == 0) { // wood, bedrock...: the bit just skids
                suit.playSound(SoundEvents.ANVIL_LAND, 0.25F, 1.8F);
            }
            return;
        }
        List<BlockPos> blocks = area(center, hit.getDirection()).stream()
                .filter(p -> canDrill(level.getBlockState(p), level, p)).toList();
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
        // Done: break them all, 60 fuel each, as far as the fuel goes.
        ItemStack bit = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        int broken = 0;
        for (BlockPos p : blocks) {
            if (!suit.hasEnergyFor(ENERGY_PER_BLOCK)) {
                suit.playSound(SoundEvents.DISPENSER_FAIL, 0.6F, 0.6F);
                break;
            }
            // Loot tables check the tool: dig dirt "with a shovel" so e.g. snow layers drop snowballs.
            ItemStack as = level.getBlockState(p).is(BlockTags.MINEABLE_WITH_PICKAXE) ? bit : shovel;
            if (ToolMining.breakBlock(level, p, pilot, as)) {
                suit.tryUseEnergy(ENERGY_PER_BLOCK);
                broken++;
            }
        }
        state.clear(level);
        if (broken > 0) {
            suit.countToolUse();
            suit.setToolCooldown(BREAK_DELAY);
        }
    }

    @Override
    public void release(PowerArmorEntity suit, @Nullable Player pilot, boolean interrupted) {
        if (suit.level() instanceof ServerLevel level) {
            suit.toolData(ToolMining.Progress.class, () -> new ToolMining.Progress(suit)).clear(level);
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
        tooltip.add(Component.translatable("item.powerarmor.drill.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.powerarmor.drill.stats", ENERGY_PER_BLOCK).withStyle(ChatFormatting.DARK_AQUA));
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
