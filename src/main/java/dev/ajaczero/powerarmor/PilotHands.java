package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.ToolMining;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * normal: punch, mine, fight, place blocks, eat, open doors (decided 2026-10-13; it used to be blocked so left click
 * could run the tool). The socket tool is the suit's off hand, used with right click when the hand has nothing to do
 * (ToolInput), so:
 *  - the player's real off-hand item isn't used while piloting (the suit has no second hand for it);
 *  - a powered-down suit's hand doesn't move: no attacking, mining or using items;
 *  - mining isn't slowed down for riding (vanilla mines 5x slower when not standing on the ground) while the suit
 *    stands;
 *  - the suit's long arm reaches 1.5 blocks further.
 */
@EventBusSubscriber(modid = PowerArmor.MODID)
public final class PilotHands {
    public static final double EXTRA_REACH = 1.5;
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "suit_reach");

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
    public static void breakBlock(BlockEvent.BreakEvent event) {
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
        checkUse(event, event);
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        checkUse(event, event);
    }

    @SubscribeEvent
    public static void useOnEntity(PlayerInteractEvent.EntityInteract event) {
        checkUse(event, event);
    }

    @SubscribeEvent
    public static void useOnEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
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
            player.displayClientMessage(Component.translatable("message.powerarmor.hand_unpowered"), true);
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
    }

    private PilotHands() {}
}
