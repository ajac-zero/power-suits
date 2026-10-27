package dev.ajaczero.powerarmor;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The suit is sealed while it has power and its helmet on: fire, frost and other "environmental" harm, harmful effects
 * and drowning don't reach the pilot (or the hull); keeping them out costs fuel instead. Without power or helmet the
 * seal is gone and
 * everything works as before: the hull takes the hits that are redirected to it (PilotProtection), effects reach the
 * pilot, and the pilot's air runs out under water. Lava is not sealed out: it still eats the hull.
 *
 * Which damage counts as "environmental" is a data-driven <b>tag</b>, {@code data/powerarmor/tags/damage_type/sealed_out.json}.
 * A tag is a named list of registry entries (blocks, items, damage types...) in a JSON file; code asks "is this in the
 * list?" instead of hard-coding it, and datapacks can add to it.
 */
@EventBusSubscriber(modid = PowerArmor.MODID)
public final class SuitSeal {
    public static final TagKey<DamageType> SEALED_OUT = TagKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "sealed_out"));

    /** Minecraft gives a hit entity half a second (10 ticks) of immunity to weaker hits. Absorbed hits never reach that
     *  code, so the suit keeps its own clock: a fire block "hits" every tick, but is paid for once per 10 ticks. */
    public static final int SEAL_COOLDOWN_TICKS = 10;

    /** The sealed suit (running, helmet on) the entity sits in (or the entity itself if it is a suit), else null. */
    @Nullable
    private static PowerArmorEntity sealedSuit(LivingEntity entity) {
        PowerArmorEntity suit = entity instanceof PowerArmorEntity s ? s
                : entity.getVehicle() instanceof PowerArmorEntity s && entity instanceof Player ? s : null;
        return suit != null && suit.isAlive() && suit.isSealed() ? suit : null;
    }

    /**
     * Server, called from {@link PowerArmorEntity#hurt}: should this hit on the suit (its own, or one redirected from
     * the pilot) be kept out by the seal? Pays for it if so. Returns false when the seal can't help (wrong kind of damage,
     * no power, not enough fuel): the hull takes it as usual.
     */
    public static boolean absorbs(PowerArmorEntity suit, DamageSource source, float amount) {
        if (!source.is(SEALED_OUT) || !suit.isSealed()) {
            return false;
        }
        if (suit.tickCount - suit.lastSealTick < SEAL_COOLDOWN_TICKS && suit.lastSealTick >= 0) {
            return true; // already paid for this half second
        }
        int cost = (int) Math.ceil(amount * PowerArmorConfig.SEAL_ENERGY_PER_DAMAGE.get());
        if (!suit.power().tryUseEnergy(cost)) {
            return false;
        }
        suit.lastSealTick = suit.tickCount;
        return true;
    }

    /** Server, after each tick of a player: the pilot of a powered suit neither burns nor freezes (the suit pays for
     *  touching fire or powder snow itself, and doesn't burn either while running: PowerArmorEntity.tick). Clears the
     *  pilot's burning / frozen screen overlays.
     *  At the END of the player's tick: a fire block at the suit's feet re-ignites the pilot during their tick, and
     *  clearing it before that (in the suit's tick) left the "on fire" flag set for the client every tick. */
    @SubscribeEvent
    public static void afterPlayerTick(PlayerTickEvent.Post event) {
        Player pilot = event.getEntity();
        if (sealedSuit(pilot) instanceof PowerArmorEntity suit) { // both sides: the client keeps its own fire ticks too
            tickPilot(suit, pilot);
        }
    }

    /** And at the start: fire that caught between ticks must be out before the player's baseTick sets the "on fire" flag
     *  clients see (same reason as PowerArmorEntity.putOutFire). */
    @SubscribeEvent
    public static void beforePlayerTick(PlayerTickEvent.Pre event) {
        Player pilot = event.getEntity();
        if (sealedSuit(pilot) instanceof PowerArmorEntity suit) {
            tickPilot(suit, pilot);
        }
    }

    static void tickPilot(PowerArmorEntity suit, Player pilot) {
        if (pilot.isOnFire()) {
            pilot.clearFire();
        }
        if (pilot.getTicksFrozen() > 0) {
            pilot.setTicksFrozen(0);
        }
    }

    /** True if the suit feeds this pilot air (their head is under water in a sealed suit). */
    public static boolean feedsAir(Player pilot) {
        return pilot.getVehicle() instanceof PowerArmorEntity suit && suit.isSealed() && pilot.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
    }

    /** Every living thing asks every tick whether it can breathe. A sealed suit's pilot always can (the fuel for it is
     *  taken in the suit's engine tick, see {@link #feedsAir}). Runs on both sides; power and helmet are synced, so they agree. */
    @SubscribeEvent
    public static void breathe(LivingBreatheEvent event) {
        if (event.getEntity() instanceof Player pilot && sealedSuit(pilot) != null) {
            event.setCanBreathe(true);
        }
    }

    /** A harmful effect (poison, wither, slowness, darkness...) about to land on the pilot or the suit: keep it out if
     *  the fuel allows. Beneficial and neutral effects (regeneration, bad omen...) get through. */
    @SubscribeEvent
    public static void effect(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || event.getEffectInstance().getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        PowerArmorEntity suit = sealedSuit(entity);
        if (suit != null && suit.power().tryUseEnergy(PowerArmorConfig.SEAL_EFFECT_ENERGY.get())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private SuitSeal() {}
}
