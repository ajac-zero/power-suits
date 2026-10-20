package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ToolMining;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * What the pilot's hands can do from inside the suit. The suit's right hand is the pilot's main hand and works as
 * normal: punch, mine, fight, place blocks, open doors (decided 2026-10-13; it used to be blocked so left click
 * could run the tool). The socket tool is the suit's off hand, used with right click when the hand has nothing to do
 * (ToolInput), so:
 *  - the player's real off-hand item isn't used while piloting (the suit has no second hand for it);
 *  - no eating or drinking while the helmet is on: the pilot is sealed in (food, potions, milk, honey, stew, cake). Food
 *    in the hand then counts as "nothing to do", so right click goes on to the socket tool. Without a helmet you eat;
 *  - a powered-down suit's hand doesn't move: no attacking, mining or using items;
 *  - mining isn't slowed down for riding (vanilla mines 5x slower when not standing on the ground) while the suit
 *    stands;
 *  - the suit's long arm reaches 1.5 blocks further;
 *  - and hits harder: every melee hit (bare hand, sword, axe...) gets the configured damage bonus.
 */
@EventBusSubscriber(modid = PowerArmor.MODID)
public final class PilotHands {
    public static final double EXTRA_REACH = 1.5;
    private static final Identifier REACH_ID = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_reach");
    private static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_strength");

    private static PowerArmorEntity suitOf(Player player) {
        return player.getVehicle() instanceof PowerArmorEntity suit ? suit : null;
    }

    // ---- attacking and mining: normal, but a powered-down hand doesn't move --------------------------

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        PowerArmorEntity suit = suitOf(event.getEntity());
        if (suit != null && !suit.canOperate()) {
            event.setCanceled(true);
            tellUnpowered(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void startMining(PlayerInteractEvent.LeftClickBlock event) {
        PowerArmorEntity suit = suitOf(event.getEntity());
        if (suit != null && !suit.canOperate()) {
            event.setCanceled(true);
            if (event.getLevel().isClientSide()) {
                tellUnpowered(event.getEntity());
            }
        }
    }

    /** Server-side backstop for the above. Breaks by the suit's own mining tools (drill, chainsaw) post this event
     *  too, so protection mods can veto them; those only run while powered anyway. */
    @SubscribeEvent
    public static void breakBlock(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        PowerArmorEntity suit = suitOf(event.getPlayer());
        if (suit != null && !suit.canOperate() && !ToolMining.isToolBreak()) {
            event.setCanceled(true);
        }
    }

    /** Vanilla divides mining speed by 5 for a player who isn't on the ground, and a rider never is. The suit's
     *  feet are on the ground, so undo that while it stands (not while it jumps or falls). Both sides compute it. */
    @SubscribeEvent
    public static void miningSpeed(PlayerEvent.BreakSpeed event) {
        PowerArmorEntity suit = suitOf(event.getEntity());
        if (suit != null && suit.onGround() && !event.getEntity().onGround()) {
            event.setNewSpeed(event.getNewSpeed() * 5F);
        }
    }

    // ---- using items: the main hand only, and only with power ------------------------------------

    @SubscribeEvent
    public static void useOnBlock(PlayerInteractEvent.RightClickBlock event) {
        PowerArmorEntity suit = suitOf(event.getEntity());
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (suit != null && suit.hasHelmet() && (block instanceof net.minecraft.world.level.block.CakeBlock || block instanceof net.minecraft.world.level.block.CandleCakeBlock)) {
            // Cake is eaten by right-clicking the block itself. Deny only the block's reaction: the item in hand
            // (a block to place, say) still gets its turn.
            event.setUseBlock(net.minecraft.util.TriState.FALSE);
        }
        checkUse(event, event);
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        PowerArmorEntity suit = suitOf(event.getEntity());
        if (suit != null && suit.hasHelmet() && event.getHand() == InteractionHand.MAIN_HAND && isEatOrDrink(event.getItemStack())) {
            // FAIL (not CONSUME): the click isn't used up, so vanilla moves on to the off hand = the socket tool
            // (ToolInput). Holding bread then works like holding a stick, instead of blocking the tool.
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            if (event.getLevel().isClientSide() && suit.getSocketStack().isEmpty()) {
                event.getEntity().sendOverlayMessage(Component.translatable("message.powerarmor.sealed_in"));
            }
            return;
        }
        checkUse(event, event);
    }

    /** Food (eating) and drinks (potions, milk, honey bottles...): what vanilla plays the eat / drink animation for. */
    public static boolean isEatOrDrink(net.minecraft.world.item.ItemStack stack) {
        var anim = stack.getUseAnimation();
        return anim == net.minecraft.world.item.ItemUseAnimation.EAT || anim == net.minecraft.world.item.ItemUseAnimation.DRINK;
    }

    /** Backstop for anything that starts eating or drinking without a right click (other mods): refuse it. */
    @SubscribeEvent
    public static void startEating(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && suitOf(player) instanceof PowerArmorEntity suit && suit.hasHelmet()
                && isEatOrDrink(event.getItem())) {
            event.setCanceled(true);
        }
    }

    /** Right click on an entity (also covers clicks on a spot of an entity, an armor stand's slot: 1.21.1 had a
     *  separate EntityInteractSpecific event for those). */
    @SubscribeEvent
    public static void useOnEntity(PlayerInteractEvent.EntityInteract event) {
        checkUse(event, event);
    }

    private static void checkUse(PlayerInteractEvent event, ICancellableEvent cancellable) {
        PowerArmorEntity suit = suitOf(event.getEntity());
        if (suit == null) {
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            cancellable.setCanceled(true); // the off hand's turn belongs to the socket tool (ToolInput)
        } else if (!suit.canOperate()) {
            cancellable.setCanceled(true);
            if (event.getLevel().isClientSide()) {
                tellUnpowered(event.getEntity());
            }
        }
    }

    private static void tellUnpowered(Player player) {
        if (player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.powerarmor.hand_unpowered"));
        }
    }

    // ---- reach ---------------------------------------------------------------------------------

    /** Climbing in adds the reach bonus, climbing out removes it. "Transient" modifiers aren't saved;
     *  a player who logs back in while seated gets it again because they mount again on load. */
    @SubscribeEvent
    public static void reach(EntityMountEvent event) {
        if (!(event.getEntityMounting() instanceof Player player) || !(event.getEntityBeingMounted() instanceof PowerArmorEntity)
                || player.level().isClientSide()) {
            return;
        }
        for (var attribute : java.util.List.of(Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(REACH_ID);
            if (event.isMounting()) {
                instance.addTransientModifier(new AttributeModifier(REACH_ID, EXTRA_REACH, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_ID);
            if (event.isMounting()) {
                refreshBonuses(player);
            }
        }
    }

    /**
     * The melee bonus: a modifier on the pilot's ATTACK_DAMAGE attribute. A weapon's damage is also a modifier on that
     * attribute (a diamond sword adds 6 to the base 1), so adding ours there makes every melee hit stronger, armed or not,
     * while bows, tridents thrown and the socket tools stay as they are. Server, on boarding and every tick (the value is
     * a config option that can change while you sit in the suit; replacing it only when it differs is cheap).
     */
    public static void refreshBonuses(Player player) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) {
            return;
        }
        double bonus = dev.ajaczero.powerarmor.PowerArmorConfig.MELEE_BONUS.get();
        AttributeModifier current = damage.getModifier(DAMAGE_ID);
        if (current == null || current.amount() != bonus) {
            damage.removeModifier(DAMAGE_ID);
            damage.addTransientModifier(new AttributeModifier(DAMAGE_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private PilotHands() {}
}
