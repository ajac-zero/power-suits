package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * What the pilot's hands can do from inside the suit. The suit's left hand holds the selected item and
 * right-click uses it as normal (place blocks, eat, open doors). But:
 *  - left click belongs to the socket tool: no punching mobs, no mining (decided: not even with a sword);
 *  - a powered-down suit's hand doesn't move: no using items on blocks / entities / the air;
 *  - the suit's long arm reaches 1.5 blocks further.
 * The client already blocks the attack key (ToolInput); these server-side checks make it impossible to cheat.
 */
@EventBusSubscriber(modid = PowerArmor.MODID)
public final class PilotHands {
    public static final double EXTRA_REACH = 1.5;
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "suit_reach");

    private static PowerArmorEntity suitOf(Player player) {
        return player.getVehicle() instanceof PowerArmorEntity suit ? suit : null;
    }

    @SubscribeEvent
    public static void noPunching(AttackEntityEvent event) {
        if (suitOf(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void noMining(PlayerInteractEvent.LeftClickBlock event) {
        if (suitOf(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void noBreaking(BlockEvent.BreakEvent event) {
        if (suitOf(event.getPlayer()) != null) {
            event.setCanceled(true);
        }
    }

    // ---- a powered-down suit's hand can't use anything ------------------------------------------

    @SubscribeEvent
    public static void useOnBlock(PlayerInteractEvent.RightClickBlock event) {
        blockIfUnpowered(event, event);
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        blockIfUnpowered(event, event);
    }

    @SubscribeEvent
    public static void useOnEntity(PlayerInteractEvent.EntityInteract event) {
        blockIfUnpowered(event, event);
    }

    @SubscribeEvent
    public static void useOnEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        blockIfUnpowered(event, event);
    }

    private static void blockIfUnpowered(PlayerInteractEvent event, ICancellableEvent cancellable) {
        PowerArmorEntity suit = suitOf(event.getEntity());
        if (suit != null && !suit.canOperate()) {
            cancellable.setCanceled(true);
            if (event.getLevel().isClientSide()) {
                event.getEntity().displayClientMessage(Component.translatable("message.powerarmor.hand_unpowered"), true);
            }
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
