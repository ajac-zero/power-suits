package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.SuitEngine;
import dev.ajaczero.powerarmor.entity.SuitFuel;
import dev.ajaczero.powerarmor.registry.ModDataComponents;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The suit in item form. Right-click a block to deploy it. The item's durability *is* the suit's
 * health: damage carries over both ways, and you can repair it in an anvil with iron ingots.
 */
public class PowerArmorItem extends Item {
    public PowerArmorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS; // client: just play the arm swing
        }
        PowerArmorEntity suit = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (suit == null) {
            return InteractionResult.FAIL;
        }

        // Stand it on the clicked face, turned to face the player who placed it.
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
        suit.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        suit.setYBodyRot(yaw);
        suit.setYHeadRot(yaw);

        if (!level.noCollision(suit, suit.getBoundingBox())) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("message.powerarmor.no_room"));
            }
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        suit.setHealth(Math.max(1.0F, suit.getMaxHealth() - stack.getDamageValue()));
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            suit.setCustomName(stack.getHoverName()); // name tags / anvil names carry over
        }
        suit.applyLoadout(stack.getOrDefault(ModDataComponents.LOADOUT.get(), SuitLoadout.EMPTY)); // fuel + tool
        suit.setPaint(stack.get(ModDataComponents.PAINT.get()));
        // The machine mind works for whoever deployed it, from the spot it stands on, until a pilot takes over.
        if (player != null) {
            suit.brain().setOwnerId(player.getUUID());
        }
        suit.brain().setHome(pos);
        level.addFreshEntity(suit);
        level.playSound(null, suit.getX(), suit.getY(), suit.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.7F, 0.6F);
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }

    /** Turns a deployed suit back into an item, keeping its damage, name, fuel and socket tool. */
    public static ItemStack fromSuit(PowerArmorEntity suit) {
        ItemStack stack = new ItemStack(ModItems.POWER_ARMOR.get());
        stack.setDamageValue(Math.round(suit.getMaxHealth() - suit.getHealth()));
        if (suit.hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, suit.getCustomName());
        }
        SuitLoadout loadout = suit.getLoadout();
        if (!loadout.equals(SuitLoadout.EMPTY)) {
            stack.set(ModDataComponents.LOADOUT.get(), loadout);
        }
        stack.set(ModDataComponents.PAINT.get(), suit.getPaint()); // null (bare steel) leaves the component off
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.powerarmor.power_armor.tooltip").withStyle(ChatFormatting.GRAY));
        DyeColor paint = stack.get(ModDataComponents.PAINT.get());
        if (paint != null) { // vanilla names every dye colour ("color.minecraft.red" = "Red")
            tooltip.accept(Component.translatable("item.powerarmor.power_armor.paint",
                    Component.translatable("color.minecraft." + paint.getName())).withStyle(ChatFormatting.GRAY));
        }
        SuitLoadout loadout = stack.get(ModDataComponents.LOADOUT.get());
        if (loadout != null) {
            // No engine field = a suit from before the engines: its firebox.
            ItemStack engineStack = loadout.engine().orElse(null);
            SuitEngine engine = engineStack == null ? SuitEngine.FIREBOX
                    : SuitEngine.of(engineStack);
            if (engineStack != null) {
                tooltip.accept(engineStack.isEmpty()
                        ? Component.translatable("item.powerarmor.power_armor.no_engine").withStyle(ChatFormatting.RED)
                        : Component.translatable("item.powerarmor.power_armor.engine", engineStack.getHoverName()).withStyle(ChatFormatting.GRAY));
            }
            int energy = loadout.burnLeft() + loadout.fuel().getCount() * SuitFuel.energyOf(engine, loadout.fuel(), context.level());
            if (!loadout.fuel().isEmpty()) {
                tooltip.accept(Component.translatable("item.powerarmor.power_armor.fuel", loadout.fuel().getCount(), loadout.fuel().getHoverName())
                        .withStyle(ChatFormatting.GOLD));
            }
            if (energy > 0) {
                tooltip.accept(Component.translatable("item.powerarmor.power_armor.range", formatWalkTime(energy)).withStyle(ChatFormatting.GOLD));
            }
            if (loadout.nuggets() > 0) {
                tooltip.accept(Component.translatable("item.powerarmor.power_armor.nuggets", loadout.nuggets()).withStyle(ChatFormatting.GRAY));
            }
            if (!loadout.socket().isEmpty()) {
                tooltip.accept(Component.translatable("item.powerarmor.power_armor.socket", loadout.socket().getHoverName()).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /** Energy -> "m:ss" of walking (by default walking burns 1 energy per tick, 20 per second). */
    public static String formatWalkTime(int energy) {
        int seconds = (int) (energy / (20 * Math.max(0.01, PowerArmorConfig.DRAIN_WALK.get())));
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
