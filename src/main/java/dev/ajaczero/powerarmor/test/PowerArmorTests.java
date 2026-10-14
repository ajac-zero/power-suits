package dev.ajaczero.powerarmor.test;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.Bullet;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.StepSmoothing;
import dev.ajaczero.powerarmor.entity.SuitFuel;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.item.SuitLoadout;
import dev.ajaczero.powerarmor.menu.PowerArmorMenu;
import dev.ajaczero.powerarmor.registry.ModDataComponents;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automated in-game tests. Run them headless with:  ./gradlew runGameTestServer
 * Each test builds the "test_pad" structure (9x10x9, stone floor), runs, and must call succeed(). GameTest places the
 * structure one block above the test origin, so the floor is at relative y=1 and you stand at y=2.
 * They only register in development / when game tests are enabled, so players never see them.
 */
@GameTestHolder(PowerArmor.MODID)
@PrefixGameTestTemplate(false)
public final class PowerArmorTests {
    private static final String PAD = "test_pad";
    private static final BlockPos SUIT_POS = new BlockPos(4, 2, 4);

    private static PowerArmorEntity spawnSuit(GameTestHelper helper) {
        return helper.spawn(ModEntities.POWER_ARMOR.get(), SUIT_POS);
    }

    private static Player boardedPilot(GameTestHelper helper, PowerArmorEntity suit) {
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        pilot.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(pilot, InteractionHand.MAIN_HAND);
        return pilot;
    }

    @GameTest(template = PAD)
    public static void pilotClimbsInAndOut(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        Player pilot = boardedPilot(helper, suit);
        helper.assertTrue(pilot.getVehicle() == suit, "right-click should put the pilot inside");
        helper.assertTrue(suit.getControllingPassenger() == pilot, "pilot should drive the suit");
        helper.assertTrue(!suit.isPickable(), "an occupied suit must not block the pilot's crosshair");

        suit.positionRider(pilot);
        double eye = pilot.getEyeY() - suit.getY();
        helper.assertTrue(eye > 2.4 && eye < 2.8, "pilot's eyes should be at the visor, got " + eye);

        pilot.stopRiding();
        helper.assertTrue(pilot.getVehicle() == null, "pilot should be out");
        helper.assertTrue(pilot.getY() < suit.getY() + 1.0, "pilot should step out at ground level, got " + pilot.position().subtract(suit.position()));
        helper.assertTrue(pilot.distanceTo(suit) > 0.9, "pilot should step out beside/behind the suit");
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void suitAbsorbsDamageForPilot(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        Player pilot = boardedPilot(helper, suit);
        pilot.hurt(helper.getLevel().damageSources().generic(), 8.0F);
        helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth(), "pilot should take no damage, health " + pilot.getHealth());
        helper.assertTrue(suit.getHealth() < suit.getMaxHealth(), "suit should take the hit");
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void packingKeepsDamage(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        suit.hurt(helper.getLevel().damageSources().generic(), 30.0F);
        int expectedDamage = Math.round(suit.getMaxHealth() - suit.getHealth());

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.packUp(player); // what the menu's Pack up button does

        helper.assertTrue(suit.isRemoved(), "suit entity should be gone");
        ItemStack packed = packedSuit(player);
        helper.assertTrue(!packed.isEmpty(), "player should receive the suit item");
        helper.assertTrue(packed.getDamageValue() == expectedDamage, "item damage " + packed.getDamageValue() + " != " + expectedDamage);
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void deployingRestoresHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.POWER_ARMOR.get());
        stack.setDamageValue(30);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        InteractionResult result = useOnFloor(helper, player, stack, new BlockPos(4, 1, 4));
        helper.assertTrue(result.consumesAction(), "deploying should succeed, got " + result);
        List<PowerArmorEntity> suits = suitsInTest(helper);
        helper.assertTrue(suits.size() == 1, "expected one suit, found " + suits.size());
        helper.assertTrue(suits.get(0).getHealth() == 70.0F, "health should be 100 - 30, got " + suits.get(0).getHealth());
        helper.assertTrue(stack.isEmpty(), "item should be used up");
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void deployingNeedsRoom(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.STONE); // ceiling 2 blocks up; the suit is 3 tall
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.POWER_ARMOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        InteractionResult result = useOnFloor(helper, player, stack, new BlockPos(4, 1, 4));
        helper.assertTrue(!result.consumesAction(), "should refuse to deploy under a low ceiling");
        helper.assertTrue(suitsInTest(helper).isEmpty(), "no suit should spawn");
        helper.assertTrue(!stack.isEmpty(), "item should be kept");
        helper.succeed();
    }

    @GameTest(template = PAD, timeoutTicks = 120)
    public static void destroyedSuitEjectsPilotAndDropsScrap(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 3));
        Player pilot = boardedPilot(helper, suit);
        suit.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);
        helper.assertTrue(pilot.getVehicle() == null, "pilot should be thrown out when the suit dies");
        helper.runAfterDelay(30, () -> helper.assertTrue(!suit.isRemoved(), "suit should linger for the death animation"));
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(suit.isRemoved(), "suit should be removed after the death animation");
            helper.assertItemEntityPresent(Items.IRON_INGOT);
            helper.assertItemEntityPresent(Items.BLAZE_ROD); // the fuel survives the wreck
            helper.succeed();
        });
    }

    /** Regression: suits saved by older versions kept their old (slow) movement speed. */
    @GameTest(template = PAD)
    public static void loadedSuitGetsCurrentStats(GameTestHelper helper) {
        PowerArmorEntity old = spawnSuit(helper);
        double current = old.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        old.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.036); // the v1 speed
        CompoundTag saved = new CompoundTag();
        old.saveWithoutId(saved);

        PowerArmorEntity loaded = ModEntities.POWER_ARMOR.get().create(helper.getLevel());
        loaded.load(saved);
        double speed = loaded.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(speed - current) < 1e-9, "loaded suit should get the current speed " + current + ", got " + speed);
        helper.succeed();
    }

    /** Fallout-style water: an empty suit sinks to the bottom of a pool and doesn't drown. */
    @GameTest(template = PAD, timeoutTicks = 200)
    public static void suitSinksAndDoesNotDrown(GameTestHelper helper) {
        // Stone-walled pool on the pad: water from y=2 to y=6 inside x/z 2..6 (5 deep).
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                for (int y = 2; y <= 6; y++) {
                    boolean wall = x == 1 || x == 7 || z == 1 || z == 7;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(4, 5, 4));
        helper.runAfterDelay(150, () -> {
            double depth = suit.getY() - helper.absolutePos(new BlockPos(4, 2, 4)).getY();
            helper.assertTrue(suit.isInWater(), "suit should be in the water");
            helper.assertTrue(depth < 0.1, "suit should rest on the pool floor, " + depth + " above it");
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "suit must not drown, health " + suit.getHealth());
            helper.succeed();
        });
    }

    // =========================================================================================
    // Fuel (Phase 1)
    // =========================================================================================

    private static PowerArmorEntity fueledSuit(GameTestHelper helper, ItemStack fuel) {
        PowerArmorEntity suit = spawnSuit(helper);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, fuel);
        return suit;
    }

    @GameTest(template = PAD)
    public static void fuelTiersCoalShortestBlazeRodLongest(GameTestHelper helper) {
        int coal = SuitFuel.energyOf(new ItemStack(Items.COAL));
        int lava = SuitFuel.energyOf(new ItemStack(Items.LAVA_BUCKET));
        int blaze = SuitFuel.energyOf(new ItemStack(Items.BLAZE_ROD));
        helper.assertTrue(coal > 0 && coal < lava && lava < blaze, "expected coal < lava < blaze rod, got " + coal + " " + lava + " " + blaze);
        helper.assertTrue(SuitFuel.energyOf(new ItemStack(Items.CHARCOAL)) == coal, "charcoal should match coal");
        helper.assertTrue(!SuitFuel.isFuel(new ItemStack(Items.OAK_PLANKS)), "planks are furnace fuel but not suit fuel");
        helper.succeed();
    }

    /** Within 10% (+-1): tick timing in tests is a tick or so off either way. */
    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) <= expected * 0.1 + 1;
    }

    /** Furnace-style: one item at a time is burnt; idle, walking and sprinting drain 0.25 / 1 / 2 per tick. */
    @GameTest(template = PAD, timeoutTicks = 120)
    public static void pilotedSuitBurnsOneItemAtATime(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 3));
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).getCount() == 2, "one coal should be burning, two waiting");
            float idle = PowerArmorConfig.COAL_ENERGY.get() - suit.getBurnLeftExact();
            helper.assertTrue(near(idle, 20 * PowerArmorConfig.DRAIN_IDLE.get()), "idle should burn ~0.25/tick (5 in 20 ticks), burnt " + idle);
            float before = suit.getBurnLeftExact();
            pilot.zza = 1; // holding W
            helper.runAfterDelay(20, () -> {
                float walk = before - suit.getBurnLeftExact();
                helper.assertTrue(near(walk, 20 * PowerArmorConfig.DRAIN_WALK.get()), "walking should burn ~1/tick (20), burnt " + walk);
                float before2 = suit.getBurnLeftExact();
                pilot.setSprinting(true);
                helper.runAfterDelay(20, () -> {
                    float sprint = before2 - suit.getBurnLeftExact();
                    helper.assertTrue(near(sprint, 20 * PowerArmorConfig.DRAIN_SPRINT.get()), "sprinting should burn ~2/tick (40), burnt " + sprint);
                    helper.assertTrue(suit.isPowered(), "suit should still be powered");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = PAD)
    public static void lavaLeavesItsBucket(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.LAVA_BUCKET));
        boardedPilot(helper, suit);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(suit.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).is(Items.BUCKET), "an empty bucket should stay in the fuel slot");
            helper.assertTrue(suit.getBurnLeftExact() > PowerArmorConfig.LAVA_ENERGY.get() - 10, "lava should give its energy, got " + suit.getBurnLeftExact());
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 60)
    public static void emptyTankShutsDown(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        suit.setBurn(10, 1600); // half a second of walking left, nothing in the slot
        Player pilot = boardedPilot(helper, suit);
        pilot.zza = 1;
        helper.runAfterDelay(2, () -> helper.assertTrue(suit.isPowered(), "should still run on the last bit of fuel"));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(!suit.isPowered(), "suit should power down when the fuel runs out");
            helper.assertTrue(!suit.canOperate() && !suit.canJump(), "a powered-down suit can't move or jump");
            pilot.stopRiding();
            helper.assertTrue(pilot.getVehicle() == null, "the pilot can still climb out");
            helper.succeed();
        });
    }

    /** Design sec. 8: a suit with fuel always runs (its machine mind drives it when nobody is inside), so it burns the
     *  idle rate even without a pilot; to park one for good, take the fuel out. Refuelling powers it back on. */
    @GameTest(template = PAD, timeoutTicks = 80)
    public static void pilotlessSuitRunsOnItsFuelAndRefuelRestarts(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!suit.isPowered(), "a suit without fuel is off");
            helper.assertTrue(!suit.isAiDriven() && suit.toolOperator() == null, "an unpowered suit has no mind at work");
            suit.setBurn(500, 1600);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        });
        helper.runAfterDelay(4, () -> helper.assertTrue(suit.isPowered(), "fuel should power it back on"));
        helper.runAfterDelay(44, () -> {
            double idle = PowerArmorConfig.DRAIN_IDLE.get();
            float used = 500 - suit.getBurnLeftExact();
            helper.assertTrue(used > idle * 30 && used < idle * 45, "the mind's suit burns the idle rate (~40 ticks), used " + used);
            helper.assertTrue(suit.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).getCount() == 2, "no new coal needed yet");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void saveAndLoadKeepFuelAndSocket(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.CHARCOAL, 5));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(Items.STICK)); // stand-in tool
        suit.setBurn(123.5F, 1600);
        CompoundTag saved = new CompoundTag();
        suit.saveWithoutId(saved);

        PowerArmorEntity loaded = ModEntities.POWER_ARMOR.get().create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).getCount() == 5, "fuel slot should survive saving");
        helper.assertTrue(loaded.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT).is(Items.STICK), "socket should survive saving");
        helper.assertTrue(loaded.getBurnLeftExact() == 123.5F && loaded.getBurnTotal() == 1600, "burning item should survive saving");
        helper.assertTrue(loaded.isPowered(), "a loaded suit with fuel is powered");
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void packAndDeployKeepLoadout(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 7));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(Items.STICK));
        suit.setBurn(900, 1600);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.packUp(player);
        ItemStack packed = packedSuit(player);
        SuitLoadout loadout = packed.get(ModDataComponents.LOADOUT.get());
        helper.assertTrue(loadout != null && loadout.fuel().getCount() == 7 && loadout.burnLeft() == 900, "item should carry the fuel, got " + loadout);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(helper.absolutePos(SUIT_POS)).inflate(4)).isEmpty(), "packing must not drop the fuel on the floor");

        player.getInventory().removeItem(packed);
        player.setItemInHand(InteractionHand.MAIN_HAND, packed);
        useOnFloor(helper, player, packed, new BlockPos(4, 1, 4));
        PowerArmorEntity deployed = suitsInTest(helper).stream().filter(s -> s != suit).findFirst().orElseThrow();
        helper.assertTrue(deployed.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).getCount() == 7, "deployed suit should get its coal back");
        helper.assertTrue(deployed.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT).is(Items.STICK), "deployed suit should get its tool back");
        helper.assertTrue(deployed.getBurnLeftExact() == 900, "deployed suit should keep its half-burnt coal");
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void sneakRightClickDoesNotClimbIn(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2, 4.5)));
        player.setShiftKeyDown(true);
        InteractionResult result = suit.interact(player, InteractionHand.MAIN_HAND);
        // (A mock player can't receive the open-screen packet; the autopilot run checks the real screen.)
        helper.assertTrue(result.consumesAction(), "sneak + right-click should be handled by the suit");
        helper.assertTrue(player.getVehicle() == null, "sneak + right-click must not climb in");
        helper.assertTrue(!suit.isRemoved(), "sneak + right-click no longer packs the suit (the menu button does)");
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void menuSlotsAndPackButton(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2, 4.5)));
        PowerArmorMenu menu = new PowerArmorMenu(1, player.getInventory(), suit.getInventory(), suit);
        helper.assertTrue(menu.stillValid(player), "menu should be usable next to a parked suit");
        helper.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(Items.BLAZE_ROD)), "fuel slot takes blaze rods");
        helper.assertTrue(!menu.getSlot(1).mayPlace(new ItemStack(Items.DIRT)), "fuel slot refuses dirt");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.COAL)), "socket only takes power tools");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ModItems.CHAINGUN.get())), "socket takes the chaingun");
        player.getInventory().setItem(10, new ItemStack(ModItems.CHAINGUN.get()));
        menu.quickMoveStack(player, 3); // menu slot 3 = player inventory slot 10
        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.CHAINGUN.get()), "shift-click should install the chaingun");
        helper.assertTrue(menu.getSlot(1).getItem().getCount() == 4, "menu shows the suit's fuel");

        // Shift-click coal from the player's inventory: it joins the coal in the fuel slot.
        player.getInventory().setItem(9, new ItemStack(Items.COAL, 10));
        menu.quickMoveStack(player, 2); // menu slot 2 = player inventory slot 9
        helper.assertTrue(menu.getSlot(1).getItem().getCount() == 14, "shift-click should move coal into the fuel slot, got " + menu.getSlot(1).getItem());

        Player pilot = boardedPilot(helper, suit);
        helper.assertTrue(!menu.stillValid(player), "menu closes when someone climbs in");
        pilot.stopRiding();

        menu.clickMenuButton(player, PowerArmorMenu.BUTTON_PACK);
        helper.assertTrue(suit.isRemoved(), "Pack up should remove the suit");
        helper.assertTrue(!packedSuit(player).isEmpty(), "Pack up should give the item");
        helper.succeed();
    }

    // =========================================================================================
    // Socket tools (Phase 2)
    // =========================================================================================

    private static PowerArmorEntity armedSuit(GameTestHelper helper, BlockPos pos, ItemStack fuel) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), pos);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, fuel);
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        return suit;
    }

    /** A pilot carrying bullets (iron nuggets) for the chaingun. */
    private static Player armedPilot(GameTestHelper helper, PowerArmorEntity suit) {
        Player pilot = boardedPilot(helper, suit);
        pilot.getInventory().add(new ItemStack(Items.IRON_NUGGET, 64));
        return pilot;
    }

    /** A creative pilot (free bullets). Mock players don't take their game mode's abilities by themselves. */
    private static Player creativePilot(GameTestHelper helper, PowerArmorEntity suit) {
        Player pilot = helper.makeMockPlayer(GameType.CREATIVE);
        pilot.getAbilities().instabuild = true;
        pilot.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(pilot, InteractionHand.MAIN_HAND);
        return pilot;
    }

    /** A tap fires one heavy shot at once: one nugget, one bullet's fuel plus a tick of spin. */
    @GameTest(template = PAD, timeoutTicks = 40)
    public static void chaingunTapFiresOneHeavyShot(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2)); // 9600 energy
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        helper.runAfterDelay(2, () -> suit.setTriggerHeld(pilot, true));
        helper.runAfterDelay(3, () -> suit.setTriggerHeld(pilot, false));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "a tap fires exactly one shot, got " + suit.getToolUses());
            helper.assertTrue(pilot.getInventory().countItem(Items.IRON_NUGGET) == 63, "one nugget used");
            int used = 2 * PowerArmorConfig.BLAZE_ENERGY.get() - suit.getTotalEnergy(), shot = PowerArmorConfig.CHAINGUN_ENERGY.get();
            helper.assertTrue(used >= shot && used < shot + 10, "one bullet costs " + shot + " energy (+ spin, idle), used " + used);
            helper.succeed();
        });
    }

    /** Held, the barrels spin up and fire faster and faster; let go, they coast down to rest. */
    @GameTest(template = PAD, timeoutTicks = 140)
    public static void chaingunSpinsUpWhenHeld(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = creativePilot(helper, suit);
        suit.positionRider(pilot);
        int hold = PowerArmorConfig.CHAINGUN_SPIN_UP.get() + 20;
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(hold, () -> {
            int atSlowRate = hold / PowerArmorConfig.CHAINGUN_SLOW_INTERVAL.get() + 1;
            helper.assertTrue(suit.getToolUses() >= 2 * atSlowRate, "spin-up should at least double the slow rate: "
                    + suit.getToolUses() + " shots in " + hold + " ticks (slow rate: " + atSlowRate + ")");
            helper.assertTrue(suit.getToolSpin() > 0.99F, "full spin after the spin-up time, got " + suit.getToolSpin());
            suit.setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(hold + 40, () -> {
            helper.assertTrue(suit.getToolSpin() == 0F, "the barrels should have coasted to rest, spin " + suit.getToolSpin());
            helper.succeed();
        });
    }

    /** Each bullet uses one iron nugget; without nuggets the gun only clicks and burns no fuel for bullets. */
    @GameTest(template = PAD, timeoutTicks = 80)
    public static void chaingunUsesOneNuggetPerBullet(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = boardedPilot(helper, suit);
        pilot.getInventory().add(new ItemStack(Items.IRON_NUGGET, 3));
        suit.positionRider(pilot);
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(suit.getToolUses() == 3, "three nuggets, three bullets, got " + suit.getToolUses());
            helper.assertTrue(pilot.getInventory().countItem(Items.IRON_NUGGET) == 0, "all nuggets used");
            int used = 2 * PowerArmorConfig.BLAZE_ENERGY.get() - suit.getTotalEnergy();
            int most = 3 * PowerArmorConfig.CHAINGUN_ENERGY.get() + 61 * PowerArmorConfig.CHAINGUN_SPIN_ENERGY.get() + 20;
            helper.assertTrue(used <= most, "only three bullets (+ spin, idle) cost fuel: at most " + most + ", used " + used);
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 30)
    public static void creativePilotNeedsNoBullets(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = creativePilot(helper, suit);
        suit.positionRider(pilot);
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "creative fires without nuggets, uses " + suit.getToolUses());
            helper.succeed();
        });
    }

    /** Barrels overheat after the configured number of bullets in a row, and fire again once cooled. (Skipped when
     *  overheat is off in the config.) */
    @GameTest(template = PAD, timeoutTicks = 600)
    public static void chaingunOverheatsAndCools(GameTestHelper helper) {
        if (!PowerArmorConfig.CHAINGUN_OVERHEAT.get()) {
            helper.succeed();
            return;
        }
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 8));
        Player pilot = creativePilot(helper, suit);
        suit.positionRider(pilot);
        suit.setTriggerHeld(pilot, true);
        int limit = PowerArmorConfig.CHAINGUN_OVERHEAT_SHOTS.get();
        int[] stopped = {-1};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && suit.getToolHeat() >= 1F) {
                stopped[0] = suit.getToolUses();
                helper.assertTrue(stopped[0] == limit, "overheats after " + limit + " bullets, got " + stopped[0]);
                helper.runAfterDelay(20, () -> {
                    helper.assertTrue(suit.getToolUses() == stopped[0], "an overheated gun must not fire");
                    suit.setTriggerHeld(pilot, false);
                });
                helper.runAfterDelay(30 + PowerArmorConfig.CHAINGUN_COOL_TICKS.get(), () -> suit.setTriggerHeld(pilot, true));
                helper.runAfterDelay(40 + PowerArmorConfig.CHAINGUN_COOL_TICKS.get(), () -> {
                    helper.assertTrue(suit.getToolUses() > stopped[0], "cooled barrels fire again");
                    helper.succeed();
                });
            }
        });
    }

    /** Using a tool raises the tool arm on every client (synced TOOL_ACTIVE); an empty socket doesn't. */
    @GameTest(template = PAD, timeoutTicks = 40)
    public static void usingAToolRaisesTheArm(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        helper.runAfterDelay(2, () -> suit.setTriggerHeld(pilot, true));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(suit.isToolActive(), "tool held: the arm should be raised");
            suit.setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!suit.isToolActive(), "released: the arm should lower");
            suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, ItemStack.EMPTY);
            suit.setTriggerHeld(pilot, true);
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(!suit.isToolActive(), "an empty socket has nothing to raise");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void shotLightsTheNextFuelItem(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.COAL, 1));
        int start = PowerArmorConfig.CHAINGUN_ENERGY.get() / 2;
        suit.setBurn(start, 1600); // not enough for a bullet by itself
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "should fire using the next coal");
            helper.assertTrue(suit.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).isEmpty(), "the coal should have been lit");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void emptySocketDoesNothing(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.getToolUses() == 0, "no tool, no shots");
            helper.assertTrue(suit.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).getCount() == 3, "only the idle burn uses fuel");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void unpoweredChaingunDoesNotFire(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, ItemStack.EMPTY);
        Player pilot = armedPilot(helper, suit);
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.getToolUses() == 0, "no fuel, no shots");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void onlyThePilotCanPullTheTrigger(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.COAL, 4));
        armedPilot(helper, suit);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.setTriggerHeld(stranger, true); // e.g. a forged packet from another player
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!suit.isTriggerHeld() && suit.getToolUses() == 0, "a non-pilot must not fire the suit");
            helper.succeed();
        });
    }

    /** The bullet flies from the muzzle to what the pilot looks at and hurts it with bullet damage. */
    @GameTest(template = PAD, timeoutTicks = 60)
    public static void bulletHitsWhatThePilotAimsAt(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, new BlockPos(4, 2, 1), new ItemStack(Items.COAL, 4));
        suit.setYRot(0); // facing +Z (south), toward the pig
        suit.setYBodyRot(0);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 7));
        pig.setNoAi(true);
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        Vec3 toPig = pig.position().add(0, pig.getBbHeight() / 2, 0).subtract(pilot.getEyePosition());
        pilot.setYRot((float) (Mth.atan2(-toPig.x, toPig.z) * Mth.RAD_TO_DEG));
        pilot.setXRot((float) (-Mth.atan2(toPig.y, toPig.horizontalDistance()) * Mth.RAD_TO_DEG));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(1, () -> suit.setTriggerHeld(pilot, false));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "one shot");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig should be hit, health " + pig.getHealth());
            helper.assertTrue(pig.getLastDamageSource() != null && pig.getLastDamageSource().is(Bullet.BULLET),
                    "damage should be bullet, got " + pig.getLastDamageSource());
            helper.assertTrue(pig.getLastDamageSource().getEntity() == pilot, "the pilot gets the credit (the bullet is the direct cause), got " + pig.getLastDamageSource().getEntity());
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "the bullet must not hit its own suit");
            helper.succeed();
        });
    }

    /** Bullets ignore the half-second hit immunity: two bullets a tick apart both hurt. */
    @GameTest(template = PAD, timeoutTicks = 30)
    public static void bulletsIgnoreHitImmunity(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 6));
        pig.setNoAi(true);
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 2.5, 1.5));
        float start = pig.getHealth();
        helper.getLevel().addFreshEntity(new Bullet(helper.getLevel(), null, from, new Vec3(0, 0, Bullet.SPEED), 2F, 0F));
        helper.runAfterDelay(1, () -> helper.getLevel().addFreshEntity(new Bullet(helper.getLevel(), null, from, new Vec3(0, 0, Bullet.SPEED), 2F, 0F)));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(Math.abs(start - pig.getHealth() - 4F) < 0.01F, "both bullets should count: health " + start + " -> " + pig.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void chaingunDoesNothingOnFoot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        ItemStack gun = new ItemStack(ModItems.CHAINGUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var result = gun.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!result.getResult().consumesAction(), "using it on foot should do nothing");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(Bullet.class, new AABB(helper.absolutePos(SUIT_POS)).inflate(5)).isEmpty(),
                "no bullet on foot");
        helper.succeed();
    }

    // =========================================================================================
    // The hand (Phase 3; since 2026-10-13 the right hand, fully normal: left click attacks and mines)
    // =========================================================================================

    /** Decided 2026-10-13: the hand works as normal, so the pilot fights with what they hold. */
    @GameTest(template = PAD, timeoutTicks = 40)
    public static void pilotFightsWithTheHand(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 6));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.canOperate(), "suit should be running");
            pilot.attack(pig);
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pilot's sword should hurt the pig, health " + pig.getHealth());
            helper.succeed();
        });
    }

    /** Mining from inside: allowed, and not slowed down 5x for "not standing on the ground" while the suit stands. */
    @GameTest(template = PAD, timeoutTicks = 40)
    public static void pilotMinesAtNormalSpeed(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.canOperate() && suit.onGround(), "suit should be running and standing");
            var event = net.neoforged.neoforge.common.CommonHooks.onLeftClickBlock(pilot, block, Direction.UP,
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(!event.isCanceled(), "mining from inside a running suit is allowed");
            var state = helper.getLevel().getBlockState(block);
            float bare = pilot.getInventory().getDestroySpeed(state); // what the empty hand digs at, before penalties
            float speed = pilot.getDigSpeed(state, block);
            helper.assertTrue(Math.abs(speed - bare) < 1e-4, "hand mining speed " + speed + ", expected the on-ground " + bare);
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 20)
    public static void unpoweredHandCannotFightOrMine(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, ItemStack.EMPTY); // no fuel: powered down
        Player pilot = boardedPilot(helper, suit);
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 6));
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        helper.runAfterDelay(2, () -> {
            pilot.attack(pig);
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "a powered-down hand doesn't swing, pig health " + pig.getHealth());
            var event = net.neoforged.neoforge.common.CommonHooks.onLeftClickBlock(pilot, block, Direction.UP,
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(event.isCanceled(), "a powered-down hand doesn't mine");
            helper.succeed();
        });
    }

    /** The socket tool is the suit's off hand: the player's real off-hand item isn't used from inside. */
    @GameTest(template = PAD, timeoutTicks = 20)
    public static void offHandBelongsToTheTool(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP, block, false);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(pilot, InteractionHand.OFF_HAND, block, hit).isCanceled(),
                    "off-hand use is cancelled while piloting");
            helper.assertTrue(!net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, block, hit).isCanceled(),
                    "the main hand still uses things");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 40)
    public static void handNeedsPower(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, ItemStack.EMPTY); // no fuel: powered down
        Player pilot = boardedPilot(helper, suit);
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP, block, false);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, block, hit).isCanceled(),
                    "a powered-down suit's hand can't use things");
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL));
        });
        helper.runAfterDelay(35, () -> { // fuel -> startup (1.4 s) -> hand works
            helper.assertTrue(suit.canOperate(), "suit should be running again");
            helper.assertTrue(!net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, block, hit).isCanceled(),
                    "a running suit's hand uses items normally");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void suitArmReachesFurther(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double blocks = player.blockInteractionRange(), entities = player.entityInteractionRange();
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getVehicle() == suit, "should be inside");
        helper.assertTrue(Math.abs(player.blockInteractionRange() - blocks - dev.ajaczero.powerarmor.PilotHands.EXTRA_REACH) < 1e-6,
                "block reach should grow by 1.5, got " + player.blockInteractionRange());
        helper.assertTrue(Math.abs(player.entityInteractionRange() - entities - dev.ajaczero.powerarmor.PilotHands.EXTRA_REACH) < 1e-6,
                "entity reach should grow by 1.5");
        player.stopRiding();
        helper.assertTrue(player.blockInteractionRange() == blocks && player.entityInteractionRange() == entities, "climbing out removes the bonus");
        helper.succeed();
    }

    // =========================================================================================
    // EM projector (Phase 4)
    // =========================================================================================

    /** A fuelled suit at (4,2,1) facing +Z with the EM projector, and a pilot looking at {@code at}. */
    private static Player emPilotLookingAt(GameTestHelper helper, PowerArmorEntity suit, Vec3 at) {
        suit.setYRot(0);
        suit.setYBodyRot(0);
        Player pilot = boardedPilot(helper, suit);
        suit.positionRider(pilot);
        Vec3 to = at.subtract(pilot.getEyePosition());
        pilot.setYRot((float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG));
        pilot.setXRot((float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
        return pilot;
    }

    private static PowerArmorEntity emSuit(GameTestHelper helper) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(4, 2, 1));
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.EM_PROJECTOR.get()));
        return suit;
    }

    /** A normal pig (with AI: Minecraft only moves mobs by velocity while their AI runs). */
    private static Pig floatingPigTarget(GameTestHelper helper) {
        return helper.spawn(EntityType.PIG, new BlockPos(4, 2, 7));
    }

    @GameTest(template = PAD, timeoutTicks = 100)
    public static void emPullsItemsIntoThePilotsInventory(GameTestHelper helper) {
        PowerArmorEntity suit = emSuit(helper);
        var item = helper.spawnItem(Items.DIAMOND, 4.5F, 2.2F, 7.5F);
        Player pilot = emPilotLookingAt(helper, suit, item.position());
        int before = suit.getTotalEnergy();
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(2, () -> helper.assertTrue(suit.getBeamTarget() == item, "the beam should lock onto the diamond"));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(item.isRemoved(), "the diamond should have been collected");
            helper.assertTrue(pilot.getInventory().countItem(Items.DIAMOND) == 1, "the diamond should be in the pilot's inventory");
            int used = before - suit.getTotalEnergy();
            int max = 60 * (PowerArmorConfig.EM_HOLD_ENERGY.get() + 1);
            helper.assertTrue(used > 0 && used < max, "pulling costs energy per tick (+ idle), used " + used);
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 80)
    public static void emHoldsAMobThenThrowsIt(GameTestHelper helper) {
        PowerArmorEntity suit = emSuit(helper);
        Pig pig = floatingPigTarget(helper);
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.getBeamTarget() == pig && pig.isNoGravity(), "the pig should float in the beam");
            helper.assertTrue(pig.distanceTo(suit) < 5.5, "the pig should be pulled in close, distance " + pig.distanceTo(suit));
            pilot.setXRot(0); // look straight ahead, then let go
            int before = suit.getTotalEnergy();
            suit.setTriggerHeld(pilot, false);
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(suit.getBeamTarget() == null && !pig.isNoGravity(), "released: gravity back on");
                helper.assertTrue(before - suit.getTotalEnergy() >= PowerArmorConfig.EM_THROW_ENERGY.get(), "a throw costs the throw energy");
                helper.assertTrue(suit.getToolUses() == 1, "one throw");
                helper.succeed();
            });
        });
    }

    /** Thrown into the test pad's wall: the slam hurts. */
    @GameTest(template = PAD, timeoutTicks = 80)
    public static void emThrownMobSlamsIntoTheWall(GameTestHelper helper) {
        PowerArmorEntity suit = emSuit(helper);
        Pig pig = floatingPigTarget(helper);
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(25, () -> {
            pilot.setXRot(0);
            suit.setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < pig.getMaxHealth(), "the slam should hurt, health " + pig.getHealth());
            helper.assertTrue(pig.getLastDamageSource() != null && pig.getLastDamageSource().is(dev.ajaczero.powerarmor.item.EmProjectorItem.EM_SLAM),
                    "damage should be em_slam, got " + pig.getLastDamageSource());
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 40)
    public static void emTapShovesAMobAway(GameTestHelper helper) {
        PowerArmorEntity suit = emSuit(helper);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 4)); // room to fly before the pad's wall
        double startZ = pig.getZ();
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(1, () -> suit.setTriggerHeld(pilot, false));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "a tap should shove");
            helper.assertTrue(pig.getZ() - startZ > 1.5, "the pig should fly away from the suit, moved " + (pig.getZ() - startZ));
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 40)
    public static void emLetsGoWhenThePilotLeaves(GameTestHelper helper) {
        PowerArmorEntity suit = emSuit(helper);
        Pig pig = floatingPigTarget(helper);
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(pig.isNoGravity(), "held");
            pilot.stopRiding();
        });
        helper.runAfterDelay(13, () -> {
            helper.assertTrue(!pig.isNoGravity() && suit.getBeamTarget() == null, "the pig must drop when the pilot climbs out");
            helper.assertTrue(suit.getToolUses() == 0, "climbing out drops, it doesn't throw");
            helper.succeed();
        });
    }

    /** Decided 2026-10-13: everything but spectators, creative players and statues can be caught (players where PvP is
     *  on); suits and the em_heavy tag (bosses, iron golems, ravagers, wardens) are heavy: held in place, not lifted. */
    @GameTest(template = PAD)
    public static void emGrabRules(GameTestHelper helper) {
        var level = helper.getLevel();
        java.util.function.Predicate<net.minecraft.world.entity.Entity> grab = dev.ajaczero.powerarmor.item.EmProjectorItem::canGrab;
        java.util.function.Predicate<net.minecraft.world.entity.Entity> heavy = dev.ajaczero.powerarmor.item.EmProjectorItem::isHeavy;
        helper.assertTrue(grab.test(helper.spawnItem(Items.DIAMOND, 2, 2, 2)), "dropped items: yes");
        helper.assertTrue(grab.test(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 6))), "pigs: yes");
        var golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(6, 2, 6));
        helper.assertTrue(grab.test(golem) && heavy.test(golem), "iron golems: heavy");
        var warden = EntityType.WARDEN.create(level);
        helper.assertTrue(grab.test(warden) && heavy.test(warden), "wardens: heavy (thin, but no beam lifts a warden)");
        helper.assertTrue(!heavy.test(EntityType.ZOMBIE.create(level)) && !heavy.test(EntityType.COW.create(level)), "ordinary mobs: light");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(grab.test(player) && !heavy.test(player), "players: yes");
        helper.assertTrue(!grab.test(helper.makeMockPlayer(GameType.CREATIVE)), "creative players: no");
        var wither = EntityType.WITHER.create(level);
        var ravager = EntityType.RAVAGER.create(level);
        PowerArmorEntity other = spawnSuit(helper);
        helper.assertTrue(grab.test(wither) && heavy.test(wither), "bosses: heavy");
        helper.assertTrue(grab.test(ravager) && heavy.test(ravager), "ravagers: heavy");
        helper.assertTrue(grab.test(other) && heavy.test(other), "other suits: heavy");
        Pig statue = helper.spawn(EntityType.PIG, new BlockPos(6, 2, 2));
        statue.setNoAi(true);
        helper.assertTrue(!grab.test(statue), "no-AI mobs (map statues): never");
        PowerArmorEntity em = emSuit(helper);
        boolean pvp = level.getServer().isPvpAllowed();
        helper.assertTrue(dev.ajaczero.powerarmor.item.EmProjectorItem.canHold(em, player) == pvp, "a pilot holds players only with PvP on");
        dev.ajaczero.powerarmor.entity.RogueSpawner.makeRogue(em, false);
        helper.assertTrue(dev.ajaczero.powerarmor.item.EmProjectorItem.canHold(em, player), "a rogue doesn't ask");
        helper.succeed();
    }

    /** A suit in the beam fights it: stays on the ground, slowed, dragged only a little. */
    @GameTest(template = PAD, timeoutTicks = 60)
    public static void heavyThingsFightTheField(GameTestHelper helper) {
        PowerArmorEntity suit = emSuit(helper);
        PowerArmorEntity heavy = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(4, 2, 7)); // unpowered: parked
        Vec3 start = heavy.position();
        Player pilot = emPilotLookingAt(helper, suit, heavy.position().add(0, 1.5, 0));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.getBeamTarget() == heavy, "caught in the beam");
            helper.assertTrue(!heavy.isNoGravity() && Math.abs(heavy.getY() - start.y) < 0.01, "not lifted");
            double moved = heavy.position().distanceTo(start);
            helper.assertTrue(moved > 0.1 && moved < 1.5, "dragged a little (1.2 blocks/s at most, slowed), moved " + moved);
            suit.setTriggerHeld(pilot, false);
            helper.succeed();
        });
    }

    // =========================================================================================
    // Mining tools (Phase 5): 3x3 drill, chainsaw
    // =========================================================================================

    /** Protection-mod stand-in: cancels BreakEvents at this one position (null = off, as in normal play). */
    @EventBusSubscriber(modid = PowerArmor.MODID)
    public static final class ProtectedBlock {
        static volatile BlockPos pos;

        @SubscribeEvent
        public static void veto(BlockEvent.BreakEvent event) {
            if (event.getPos().equals(pos)) {
                event.setCanceled(true);
            }
        }
    }

    /** A suit at (4,2,1) facing +Z with this tool and 4 blaze rods (19200 energy), the pilot looking at {@code at}. */
    private static Player toolPilot(GameTestHelper helper, PowerArmorEntity suit, ItemStack tool, Vec3 at) {
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, tool);
        return emPilotLookingAt(helper, suit, helper.absoluteVec(at));
    }

    /** Fills the 3x3 wall x 3..5, y 2..4 at z=5 (relative) with a block. */
    private static void wall(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        for (int x = 3; x <= 5; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 5), block);
            }
        }
    }

    private static int wallLeft(GameTestHelper helper) {
        int n = 0;
        for (int x = 3; x <= 5; x++) {
            for (int y = 2; y <= 4; y++) {
                n += helper.getBlockState(new BlockPos(x, y, 5)).isAir() ? 0 : 1;
            }
        }
        return n;
    }

    private static int dropped(GameTestHelper helper, net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new AABB(helper.absolutePos(new BlockPos(4, 3, 5))).inflate(4))
                .stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static PowerArmorEntity toolSuit(GameTestHelper helper) {
        return helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(4, 2, 1));
    }

    @GameTest(template = PAD, timeoutTicks = 60)
    public static void drillDigsA3x3Face(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 6), Blocks.OBSIDIAN); // behind: slow, so it's still there when we check
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        int before = suit.getTotalEnergy();
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(4, () -> helper.assertTrue(wallLeft(helper) == 9, "stone takes 6 ticks with a diamond tool, not instant"));
        helper.runAfterDelay(25, () -> {
            suit.setTriggerHeld(pilot, false);
            helper.assertTrue(wallLeft(helper) == 0, "all 9 blocks of the face should be gone, left " + wallLeft(helper));
            helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(4, 3, 6));
            helper.assertTrue(dropped(helper, Items.COBBLESTONE) == 9, "9 cobblestone should drop, got " + dropped(helper, Items.COBBLESTONE));
            int used = before - suit.getTotalEnergy(), face = 9 * PowerArmorConfig.DRILL_ENERGY.get();
            helper.assertTrue(used >= face && used < face + 40, "9 blocks' fuel (+ idle), used " + used);
            helper.succeed();
        });
    }

    /** Diamond tier: obsidian yes (slowly: the face waits for its hardest block), bedrock never, wood is the chainsaw's. */
    @GameTest(template = PAD, timeoutTicks = 300)
    public static void drillDigsObsidianButNotBedrockOrWood(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 5), Blocks.OBSIDIAN);
        helper.setBlock(new BlockPos(3, 3, 5), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.OAK_PLANKS);
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(150, () -> helper.assertTrue(wallLeft(helper) == 9, "obsidian takes ~9.4 s and the stone waits for it"));
        helper.runAfterDelay(220, () -> {
            suit.setTriggerHeld(pilot, false);
            helper.assertBlockNotPresent(Blocks.OBSIDIAN, new BlockPos(4, 3, 5));
            helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 3, 5));
            helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 3, 5));
            helper.assertTrue(wallLeft(helper) == 2, "only bedrock and planks should be left, left " + wallLeft(helper));
            helper.assertTrue(dropped(helper, Items.OBSIDIAN) == 1, "obsidian should drop");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 60)
    public static void drillSkidsOnWood(GameTestHelper helper) {
        wall(helper, Blocks.OAK_PLANKS);
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(wallLeft(helper) == 9 && suit.getToolUses() == 0, "the drill must not cut wood");
            helper.succeed();
        });
    }

    /** Every block posts a BreakEvent for the pilot, so protection mods can say no. The hand still can't break anything. */
    @GameTest(template = PAD, timeoutTicks = 60)
    public static void drillRespectsProtection(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 5));
        ProtectedBlock.pos = center;
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        var handBreak = new BlockEvent.BreakEvent(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 5)), Blocks.STONE.defaultBlockState(), pilot);
        NeoForge.EVENT_BUS.post(handBreak);
        helper.assertTrue(handBreak.isCanceled(), "a pilot's own (hand) break must still be cancelled");
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(14, () -> {
            suit.setTriggerHeld(pilot, false);
            ProtectedBlock.pos = null;
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 5));
            helper.assertTrue(wallLeft(helper) == 1, "the 8 unprotected blocks should break, left " + wallLeft(helper));
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 80)
    public static void chainsawFellsANaturalTree(GameTestHelper helper) {
        for (int y = 2; y <= 6; y++) {
            helper.setBlock(new BlockPos(4, y, 5), Blocks.OAK_LOG);
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = 5; y <= 7; y++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LEAVES); // natural (not persistent)
                    }
                }
            }
        }
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.CHAINSAW.get()), new Vec3(4.5, 2.5, 5));
        int before = suit.getTotalEnergy();
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(30, () -> {
            suit.setTriggerHeld(pilot, false);
            for (int y = 2; y <= 6; y++) {
                helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(4, y, 5));
            }
            helper.assertTrue(dropped(helper, Items.OAK_LOG) == 5, "5 logs should drop, got " + dropped(helper, Items.OAK_LOG));
            int used = before - suit.getTotalEnergy(), tree = 5 * PowerArmorConfig.CHAINSAW_ENERGY.get();
            helper.assertTrue(used >= tree && used < tree + 30, "fuel per log x 5 (+ idle), used " + used);
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 60)
    public static void chainsawSparesLogCabins(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(4, y, 5), Blocks.OAK_LOG); // a log wall: no leaves
        }
        helper.setBlock(new BlockPos(4, 5, 5), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.CHAINSAW.get()), new Vec3(4.5, 2.5, 5));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(25, () -> {
            suit.setTriggerHeld(pilot, false);
            helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(4, 2, 5));
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 3, 5));
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 4, 5));
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 60)
    public static void chainsawSkidsOnStone(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.CHAINSAW.get()), new Vec3(4.5, 3.5, 5));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(wallLeft(helper) == 9 && suit.getToolUses() == 0, "the chainsaw must not cut stone");
            helper.succeed();
        });
    }

    /** TOOL_RUNNING (synced) drives the tool's loop animation and sound on every client. */
    @GameTest(template = PAD, timeoutTicks = 40)
    public static void runningToolIsSyncedForAnimationAndSound(GameTestHelper helper) {
        PowerArmorEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        PowerTool drill = (PowerTool) ModItems.DRILL.get();
        helper.assertTrue(drill.runAnimation() != null && drill.runSound() != null, "the drill has a loop animation and sound");
        helper.assertTrue(((PowerTool) ModItems.CHAINSAW.get()).runSound() != null, "so does the chainsaw");
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(suit.isToolRunning(), "held: running (spinning even in the air)");
            suit.setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!suit.isToolRunning(), "released: stopped");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void miningToolsDoNothingOnFoot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (ItemStack tool : List.of(new ItemStack(ModItems.DRILL.get()), new ItemStack(ModItems.CHAINSAW.get()))) {
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            helper.assertTrue(!tool.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "no use on foot");
            helper.assertTrue(tool.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 1F
                    && tool.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) == 1F, "in your hand it mines like a fist");
        }
        helper.succeed();
    }

    /**
     * Walking up and down blocks: the model and the pilot's camera must glide together. Feeds a staircase (two
     * step-ups, then a 1- and a 2-block snapped step-down) through StepSmoothing exactly as the game uses it:
     * the renderer draws the model at lerp(partial, yo, y) + offset(partial), the seat puts the camera at
     * y + EYE + offset at the end of each tick and Minecraft lerps the camera between ticks. At 5 render frames
     * per tick, the model must never jump and must stay at the same height below the camera (from the cockpit,
     * anything else looks like the arm flickering up and down). The old version dropped the model a block at
     * the start of every step tick.
     */
    @GameTest(template = PAD)
    public static void stepsDoNotJerkTheModelAgainstTheCamera(GameTestHelper helper) {
        final double eye = 2.6;
        double[] heights = {0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        StepSmoothing steps = new StepSmoothing(2.1);
        double lastModel = heights[0], lastCamera = heights[0] + eye, maxJump = 0, maxDrift = 0;
        for (int t = 1; t < heights.length; t++) {
            double yo = heights[t - 1], y = heights[t];
            double cameraStart = lastCamera;                       // where last tick left the pilot
            steps.tick(y - yo, true);
            double cameraEnd = y + eye + steps.current();
            for (int f = 0; f <= 4; f++) {
                float partial = f / 4F;
                double model = Mth.lerp(partial, yo, y) + steps.at(partial);
                double camera = Mth.lerp(partial, cameraStart, cameraEnd);
                maxJump = Math.max(maxJump, Math.abs(model - lastModel));
                maxDrift = Math.max(maxDrift, Math.abs(camera - model - eye));
                lastModel = model;
            }
            lastCamera = cameraEnd;
        }
        // A 1-block step eases over ~6 ticks; the biggest quarter-tick move is the first one of a 2-block step.
        helper.assertTrue(maxJump < 0.3, "model jumped " + maxJump + " blocks between two frames");
        helper.assertTrue(maxDrift < 0.01, "model moved " + maxDrift + " blocks against the camera");
        helper.assertTrue(Math.abs(steps.current()) < 0.05, "step offset should settle, got " + steps.current());
        helper.succeed();
    }

    // =========================================================================================
    // The suit's own mechanics: sealing, stomp, lightning, melee bonus, smashing through blocks
    // =========================================================================================

    @GameTest(template = PAD, timeoutTicks = 40)
    public static void sealKeepsFireOutForFuel(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(5, () -> {
            var sources = helper.getLevel().damageSources();
            float before = suit.getBurnLeftExact();
            pilot.hurt(sources.onFire(), 1F); // redirected to the suit (PilotProtection), where the seal pays for it
            float paid = before - suit.getBurnLeftExact();
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "fire must not hurt a powered suit, health " + suit.getHealth());
            helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth(), "nor its pilot");
            helper.assertTrue(Math.abs(paid - PowerArmorConfig.SEAL_ENERGY_PER_DAMAGE.get()) < 0.01, "one point of fire should cost its energy, paid " + paid);
            float before2 = suit.getBurnLeftExact();
            suit.hurt(sources.inFire(), 1F); // the next tick of fire within the half second is already paid for
            helper.assertTrue(suit.getBurnLeftExact() == before2, "fire within the half-second immunity is free");
            suit.hurt(sources.lava(), 4F);
            helper.assertTrue(suit.getHealth() < suit.getMaxHealth(), "lava still eats the hull");
            suit.igniteForSeconds(5);   // a flaming arrow, say
            pilot.igniteForSeconds(5);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!suit.isOnFire(), "a running suit doesn't burn");
            helper.assertTrue(!pilot.isOnFire(), "nor does its pilot");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 20)
    public static void unpoweredSuitBurns(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> suit.igniteForSeconds(5));
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(suit.isOnFire(), "with no power the suit catches fire like anything else");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 20)
    public static void unpoweredSuitIsNotSealed(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, ItemStack.EMPTY);
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(2, () -> {
            pilot.hurt(helper.getLevel().damageSources().onFire(), 2F);
            helper.assertTrue(suit.getHealth() < suit.getMaxHealth(), "without power, fire hurts the hull");
            pilot.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100));
            helper.assertTrue(pilot.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "without power, poison reaches the pilot");
            var breathe = new net.neoforged.neoforge.event.entity.living.LivingBreatheEvent(pilot, false, 1, 4);
            NeoForge.EVENT_BUS.post(breathe);
            helper.assertTrue(!breathe.canBreathe(), "without power, the pilot can't breathe under water");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 20)
    public static void sealBlocksHarmfulEffectsAndFeedsAir(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(2, () -> {
            float before = suit.getBurnLeftExact();
            pilot.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100));
            helper.assertTrue(!pilot.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "poison should be kept out");
            helper.assertTrue(Math.abs(before - suit.getBurnLeftExact() - PowerArmorConfig.SEAL_EFFECT_ENERGY.get()) < 0.01, "and cost fuel");
            pilot.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100));
            helper.assertTrue(pilot.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "good effects get through");
            var breathe = new net.neoforged.neoforge.event.entity.living.LivingBreatheEvent(pilot, false, 1, 4);
            NeoForge.EVENT_BUS.post(breathe);
            helper.assertTrue(breathe.canBreathe(), "a powered suit feeds its pilot air");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 220)
    public static void lightningKnocksTheSuitOutThenItRestarts(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        int stun = PowerArmorConfig.LIGHTNING_STUN_TICKS.get();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.canOperate(), "suit should be running");
            var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
            bolt.moveTo(suit.position());
            suit.thunderHit(helper.getLevel(), bolt);
            helper.assertTrue(suit.isStunned() && !suit.isPowered() && !suit.canOperate(), "a strike knocks the suit out");
            helper.assertTrue(suit.getRemainingFireTicks() <= 0, "but doesn't set the running suit alight by itself");
            helper.assertTrue(!suit.reactsToHits(), "a knocked-out suit doesn't flinch (the convulsion plays undisturbed)");
            helper.assertTrue(suit.getHealth() < suit.getMaxHealth(), "and hurts it");
            helper.assertTrue(pilot.getVehicle() == suit, "the pilot stays inside");
        });
        helper.runAfterDelay(5 + stun / 2, () -> helper.assertTrue(!suit.isPowered(), "still out halfway through"));
        helper.runAfterDelay(5 + stun + 3, () -> helper.assertTrue(suit.isPowered() && !suit.isStunned(), "it restarts by itself"));
        helper.runAfterDelay(5 + stun + 3, () -> helper.assertTrue(!suit.reactsToHits(), "nor while it stands back up"));
        helper.runAfterDelay(5 + stun + 40, () -> {
            helper.assertTrue(suit.canOperate() && suit.reactsToHits(), "and runs (and flinches) again after the startup");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 20)
    public static void lightningOnAnEmptyTankDoesNotStun(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
            suit.thunderHit(helper.getLevel(), bolt);
            helper.assertTrue(!suit.isStunned(), "nothing to knock out without fuel");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 30)
    public static void stompHurtsEverythingAroundButThePilot(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        Pig near = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 6));   // 2 blocks from the suit's centre
        Pig far = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 8));    // 4 blocks: outside the shock wave
        helper.runAfterDelay(3, () -> {
            var fall = helper.getLevel().damageSources().fall();
            suit.causeFallDamage(2.3F, 1F, fall); // a jump on flat ground: just a thud
            helper.assertTrue(near.getHealth() == near.getMaxHealth(), "a normal jump must not stomp");
            suit.causeFallDamage(6F, 1F, fall);
            float expected = (float) Math.min(PowerArmorConfig.STOMP_MAX_DAMAGE.get(), PowerArmorConfig.STOMP_DAMAGE_PER_BLOCK.get() * 6);
            float dealt = near.getMaxHealth() - near.getHealth();
            helper.assertTrue(dealt > 0 && (near.isDeadOrDying() || dealt >= expected * 0.5F - 0.01F), "the near pig should be hurt, took " + dealt);
            helper.assertTrue(near.getLastDamageSource() != null && near.getLastDamageSource().getEntity() == pilot, "credited to the pilot");
            helper.assertTrue(far.getHealth() == far.getMaxHealth(), "the far pig is out of reach");
            helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth() && suit.getHealth() == suit.getMaxHealth(), "the suit and its pilot are spared");
            helper.assertTrue(near.getDeltaMovement().y > 0 || near.isDeadOrDying(), "the shock wave throws it up");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void pilotHitsHarder(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(player, InteractionHand.MAIN_HAND);
        double bonus = PowerArmorConfig.MELEE_BONUS.get();
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - base - bonus) < 1e-6,
                "melee damage should grow by " + bonus + ", got " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        player.stopRiding();
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == base, "climbing out removes the bonus");
        helper.succeed();
    }

    /** A suit at (4,2,2) facing +Z: its hitbox spans x 3.8..5.2, z 1.8..3.2; walking reaches 0.6 further (z 3.8), sprinting 1.1 (z 4.3). */
    @GameTest(template = PAD, timeoutTicks = 20)
    public static void walkingSmashesPanesAndLeavesSprintingAlsoGlass(GameTestHelper helper) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(4, 2, 2));
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 4));
        suit.setYRot(0);
        Player pilot = boardedPilot(helper, suit);
        pilot.setYRot(0);
        BlockPos pane = new BlockPos(4, 3, 3), leaves = new BlockPos(5, 4, 3), glass = new BlockPos(4, 2, 4), wall = new BlockPos(4, 2, 7);
        helper.setBlock(pane, Blocks.GLASS_PANE);
        helper.setBlock(leaves, Blocks.OAK_LEAVES);
        helper.setBlock(glass, Blocks.GLASS);
        helper.setBlock(wall, Blocks.GLASS_PANE); // out of reach
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(dev.ajaczero.powerarmor.entity.SuitSmash.tick(suit, pilot) == 0, "standing still breaks nothing");
            pilot.zza = 1;
            dev.ajaczero.powerarmor.entity.SuitSmash.tick(suit, pilot);
            helper.assertBlockNotPresent(Blocks.GLASS_PANE, pane);
            helper.assertBlockNotPresent(Blocks.OAK_LEAVES, leaves);
            helper.assertBlockPresent(Blocks.GLASS, glass); // full glass needs a sprint
            helper.assertBlockPresent(Blocks.GLASS_PANE, wall);
            pilot.setSprinting(true);
            dev.ajaczero.powerarmor.entity.SuitSmash.tick(suit, pilot);
            helper.assertBlockNotPresent(Blocks.GLASS, glass);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(glass)).inflate(4)).isEmpty(), "smashed blocks drop nothing");
            helper.succeed();
        });
    }

    /** Sealed in: no eating or drinking. The refused click passes on (FAIL) so the socket tool can take it. */
    @GameTest(template = PAD, timeoutTicks = 20)
    public static void pilotCannotEatOrDrink(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        pilot.getFoodData().setFoodLevel(10);
        helper.runAfterDelay(2, () -> {
            for (ItemStack stack : List.of(new ItemStack(Items.BREAD), new ItemStack(Items.POTION), new ItemStack(Items.MILK_BUCKET),
                    new ItemStack(Items.HONEY_BOTTLE), new ItemStack(Items.SUSPICIOUS_STEW))) {
                pilot.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var result = net.neoforged.neoforge.common.CommonHooks.onItemRightClick(pilot, InteractionHand.MAIN_HAND);
                helper.assertTrue(result == InteractionResult.FAIL, stack + " should be refused (and passed on), got " + result);
                pilot.startUsingItem(InteractionHand.MAIN_HAND);
                helper.assertTrue(!pilot.isUsingItem(), "the pilot must not start eating/drinking " + stack);
            }
            pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            helper.assertTrue(net.neoforged.neoforge.common.CommonHooks.onItemRightClick(pilot, InteractionHand.MAIN_HAND) == null,
                    "other items are used as normal");
            BlockPos cake = helper.absolutePos(new BlockPos(4, 2, 6));
            helper.setBlock(new BlockPos(4, 2, 6), Blocks.CAKE);
            var hit = new BlockHitResult(Vec3.atCenterOf(cake), Direction.UP, cake, false);
            var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, cake, hit);
            helper.assertTrue(event.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE, "no cake from inside either");
            pilot.stopRiding();
            pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
            pilot.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(pilot.isUsingItem(), "on foot the player eats as normal");
            helper.succeed();
        });
    }

    /** Effects already inside the pilot (food poisoning from before climbing in) hurt the pilot, not the suit. */
    @GameTest(template = PAD, timeoutTicks = 20)
    public static void poisonAlreadyInsideHurtsThePilot(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200)); // ate a spider eye
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "the poison came in with the pilot");
            // A poison tick (NeoForge's own damage type, neoforge:poison), then a wither tick.
            float fuel = suit.getBurnLeftExact();
            var types = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
            player.invulnerableTime = 0;
            player.hurt(new net.minecraft.world.damagesource.DamageSource(types.getHolderOrThrow(net.neoforged.neoforge.common.NeoForgeMod.POISON_DAMAGE)), 1F);
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "poison must not reach the suit");
            player.invulnerableTime = 0;
            player.hurt(helper.getLevel().damageSources().wither(), 1F);
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "poison hurts the pilot, health " + player.getHealth());
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "not the suit, health " + suit.getHealth() + " from "
                    + (suit.getLastDamageSource() == null ? "-" : suit.getLastDamageSource().getMsgId()));
            helper.assertTrue(player.getLastDamageSource() != null && player.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.WITHER),
                    "the wither tick reached the pilot");
            helper.assertTrue(suit.getBurnLeftExact() == fuel, "and costs no fuel");
            helper.succeed();
        });
    }

    // =========================================================================================
    // The machine mind (Phase 8 step 1): owner, home, who drives, visor, tools without a pilot
    // =========================================================================================

    @GameTest(template = PAD)
    public static void pilotOwnsTheSuitAndExitMarksHome(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        helper.runAfterDelay(2, () -> { // the engine lights on the suit's first tick
            Player pilot = boardedPilot(helper, suit);
            helper.assertTrue(pilot.getUUID().equals(suit.getOwnerId()), "climbing in claims the suit");
            helper.assertTrue(!suit.isAiDriven() && suit.toolOperator() == pilot, "the pilot drives, not the mind");
            pilot.stopRiding();
            helper.assertTrue(pilot.getUUID().equals(suit.getOwnerId()), "the last pilot stays the owner");
            helper.assertTrue(suit.blockPosition().equals(suit.getHome()), "home is where the pilot got out, got " + suit.getHome());
            helper.assertTrue(suit.isAiDriven() && suit.toolOperator() == suit, "powered and empty: the mind drives");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void deployedSuitWorksForWhoeverPlacedIt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.POWER_ARMOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        useOnFloor(helper, player, stack, new BlockPos(4, 1, 4));
        PowerArmorEntity suit = suitsInTest(helper).get(0);
        helper.assertTrue(player.getUUID().equals(suit.getOwnerId()), "the placer owns it");
        helper.assertTrue(helper.absolutePos(new BlockPos(4, 2, 4)).equals(suit.getHome()), "home is where it was placed, got " + suit.getHome());
        helper.succeed();
    }

    @GameTest(template = PAD)
    public static void mindOwnerAndHomeSurviveSaving(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        java.util.UUID owner = java.util.UUID.randomUUID();
        suit.setMind(dev.ajaczero.powerarmor.entity.SuitMind.ROGUE);
        suit.setOwnerId(owner);
        suit.setHome(new BlockPos(10, 64, -3));
        CompoundTag saved = new CompoundTag();
        suit.saveWithoutId(saved);
        PowerArmorEntity loaded = ModEntities.POWER_ARMOR.get().create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.isRogue() && owner.equals(loaded.getOwnerId()) && new BlockPos(10, 64, -3).equals(loaded.getHome()),
                "mind, owner and home should survive saving");
        CompoundTag old = new CompoundTag(); // a suit saved before the AI existed: friendly, nobody's, no home
        spawnSuit(helper).saveWithoutId(old);
        old.remove("Mind");
        PowerArmorEntity legacy = ModEntities.POWER_ARMOR.get().create(helper.getLevel());
        legacy.load(old);
        helper.assertTrue(!legacy.isRogue() && legacy.getOwnerId() == null && legacy.getHome() == null, "old saves load as friendly, unowned, homeless");
        helper.succeed();
    }

    /** Pushed 6 blocks from home, the idle mind walks back at a pilot's jog (vanilla mobs move at speed squared; see
     *  travel) and onto the block itself (vanilla's path for a 2-block-wide mob ended 1.7 blocks short). */
    @GameTest(template = PAD, timeoutTicks = 100)
    public static void idleSuitWalksBackHome(GameTestHelper helper) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(7, 2, 4));
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        BlockPos home = helper.absolutePos(new BlockPos(1, 2, 4));
        suit.setHome(home);
        double[] fastest = {0};
        Vec3[] last = {suit.position()};
        helper.onEachTick(() -> { // blocks moved per tick (the velocity field already has the next tick's drag in it)
            fastest[0] = Math.max(fastest[0], suit.position().subtract(last[0]).horizontalDistance() * 20);
            last[0] = suit.position();
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!suit.isAwayFromHome(0.5), "should be back home after 3 s, at " + suit.position().subtract(Vec3.atBottomCenterOf(home)));
            helper.assertTrue(fastest[0] > 4.3 && fastest[0] < 6, "walks at a jog (~5 blocks/s), fastest " + fastest[0]);
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 40)
    public static void unpoweredSuitStaysPut(GameTestHelper helper) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(2, 2, 2));
        suit.setHome(helper.absolutePos(new BlockPos(6, 2, 6)));
        Vec3 start = suit.position();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.position().distanceTo(start) < 0.05, "no fuel, no mind: it must not walk home");
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 40)
    public static void boardingStopsTheMind(GameTestHelper helper) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(2, 2, 2));
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        suit.setHome(helper.absolutePos(new BlockPos(6, 2, 6)));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!suit.getNavigation().isDone(), "on its way home");
            Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
            suit.interact(pilot, InteractionHand.MAIN_HAND);
            helper.assertTrue(pilot.getVehicle() == suit, "boarded mid-walk");
            helper.assertTrue(suit.getNavigation().isDone() && !suit.isAiDriven(), "the pilot takes over at once");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void rogueLetsNobodyIn(GameTestHelper helper) {
        PowerArmorEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 2));
        suit.setMind(dev.ajaczero.powerarmor.entity.SuitMind.ROGUE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!suit.interact(player, InteractionHand.MAIN_HAND).consumesAction() && player.getVehicle() == null,
                "a rogue can't be boarded");
        helper.succeed();
    }

    /** Amber whenever piloted (no more red for tool use or hits), green for the friendly mind, red only for a rogue. */
    @GameTest(template = PAD)
    public static void visorShowsWhoIsInControl(GameTestHelper helper) {
        var friendly = dev.ajaczero.powerarmor.entity.SuitMind.FRIENDLY;
        var rogue = dev.ajaczero.powerarmor.entity.SuitMind.ROGUE;
        float[] piloted = PowerArmorEntity.visorColorFor(true, friendly);
        float[] green = PowerArmorEntity.visorColorFor(false, friendly);
        float[] red = PowerArmorEntity.visorColorFor(false, rogue);
        helper.assertTrue(piloted[0] > 0.9F && piloted[1] > 0.5F && piloted[2] < 0.4F, "piloted = amber");
        helper.assertTrue(PowerArmorEntity.visorColorFor(true, rogue) == piloted, "a pilot's visor is amber, whatever the mind");
        helper.assertTrue(green[1] > green[0] && green[1] > green[2], "friendly mind = green");
        helper.assertTrue(red[0] > 0.9F && red[1] < 0.3F, "rogue = red");
        helper.succeed();
    }

    /** Breaks seen at this position (the BreakEvent's player), for checking who a tool breaks blocks as. */
    @EventBusSubscriber(modid = PowerArmor.MODID)
    public static final class BreakWatch {
        static volatile BlockPos pos;
        static volatile java.util.UUID breaker;

        @SubscribeEvent
        public static void seen(BlockEvent.BreakEvent event) {
            if (event.getPos().equals(pos)) {
                breaker = event.getPlayer().getUUID();
            }
        }
    }

    /** The tool operator refactor: with nobody inside, the mind aims the head and pulls the trigger; the drill digs the
     *  3x3 face and every block is broken as the owner (a FakePlayer with their UUID, so claim mods see the owner). */
    @GameTest(template = PAD, timeoutTicks = 60)
    public static void mindDrillsAsItsOwner(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 5));
        BreakWatch.pos = center;
        BreakWatch.breaker = null;
        PowerArmorEntity suit = toolSuit(helper);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.DRILL.get()));
        java.util.UUID owner = java.util.UUID.randomUUID();
        suit.setOwnerId(owner);
        suit.setHome(suit.blockPosition()); // the mind only drills its hall round home (the wall is in it)
        helper.runAfterDelay(2, () -> { // once the engine runs (an unpowered mind forgets its aim and trigger)
            suit.setAiAim(Vec3.atCenterOf(center));
            suit.setAiTrigger(true);
        });
        helper.runAfterDelay(5, () -> helper.assertTrue(suit.isToolActive(), "the mind's trigger runs the tool"));
        helper.runAfterDelay(30, () -> {
            suit.setAiTrigger(false);
            BreakWatch.pos = null;
            helper.assertTrue(wallLeft(helper) == 0, "the face should be gone, left " + wallLeft(helper));
            helper.assertTrue(dropped(helper, Items.COBBLESTONE) == 9, "9 cobblestone should drop, got " + dropped(helper, Items.COBBLESTONE));
            helper.assertTrue(owner.equals(BreakWatch.breaker), "broken as the owner, got " + BreakWatch.breaker);
            helper.succeed();
        });
    }

    /** The chaingun with the mind at the trigger: it spins up, but without nuggets it can't shoot. */
    @GameTest(template = PAD, timeoutTicks = 40)
    public static void mindChaingunNeedsAmmo(GameTestHelper helper) {
        PowerArmorEntity suit = toolSuit(helper);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        helper.runAfterDelay(2, () -> {
            suit.setAiAim(helper.absoluteVec(new Vec3(4.5, 3, 7)));
            suit.setAiTrigger(true);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.isToolActive() && suit.getToolUses() == 0, "spins, but no nuggets: no shots");
            helper.succeed();
        });
    }

    // =========================================================================================
    // Phase 8 step 2: the hidden nugget store, the chaingun job (follow + defend the owner, fists without ammo)
    // =========================================================================================

    @GameTest(template = PAD)
    public static void nuggetsLoadIntoTheHiddenStore(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET, 64));
        helper.assertTrue(suit.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "right-click with nuggets loads them");
        helper.assertTrue(suit.getStoredNuggets() == 64 && player.getMainHandItem().isEmpty(), "the whole stack goes in, got " + suit.getStoredNuggets());
        helper.assertTrue(player.getVehicle() == null, "loading ammo doesn't climb in");
        int max = PowerArmorConfig.NUGGET_STORE_MAX.get();
        suit.setStoredNuggets(max - 10);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET, 64));
        suit.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(suit.getStoredNuggets() == max && player.getMainHandItem().getCount() == 54, "fills up to the store size, keeps the rest");
        helper.succeed();
    }

    @GameTest(template = PAD, timeoutTicks = 120)
    public static void nuggetStoreSurvivesSavingPackingAndTheWreck(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        suit.setStoredNuggets(100);
        CompoundTag saved = new CompoundTag();
        suit.saveWithoutId(saved);
        PowerArmorEntity loaded = ModEntities.POWER_ARMOR.get().create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.getStoredNuggets() == 100, "saved with the suit");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.packUp(player);
        ItemStack packed = packedSuit(player);
        helper.assertTrue(packed.get(ModDataComponents.LOADOUT.get()).nuggets() == 100, "kept on the packed item");
        player.setItemInHand(InteractionHand.MAIN_HAND, packed);
        useOnFloor(helper, player, packed, new BlockPos(4, 1, 4));
        PowerArmorEntity deployed = suitsInTest(helper).get(0);
        helper.assertTrue(deployed.getStoredNuggets() == 100, "back in the deployed suit");
        deployed.hurt(helper.getLevel().damageSources().genericKill(), 1000F);
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(dropped(helper, Items.IRON_NUGGET) == 100, "the wreck drops its ammo, got " + dropped(helper, Items.IRON_NUGGET));
            helper.succeed();
        });
    }

    /** Decided 2026-10-13: a pilot's chaingun takes their own nuggets first, then the suit's store. */
    @GameTest(template = PAD)
    public static void pilotChaingunUsesOwnNuggetsBeforeTheStore(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        suit.setStoredNuggets(5);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        pilot.getInventory().setItem(3, new ItemStack(Items.IRON_NUGGET, 1));
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(dev.ajaczero.powerarmor.item.ChaingunItem.takeBullet(suit, pilot), "a bullet each time");
        }
        helper.assertTrue(pilot.getInventory().countItem(Items.IRON_NUGGET) == 0 && suit.getStoredNuggets() == 3,
                "1 from the pocket, then 2 from the store; store " + suit.getStoredNuggets());
        helper.succeed();
    }

    /** An owner who is really in the level (the mind looks its owner up by UUID): a creative mock server player. */
    @SuppressWarnings("removal")
    private static net.minecraft.server.level.ServerPlayer ownerAt(GameTestHelper helper, PowerArmorEntity suit, Vec3 at) {
        net.minecraft.server.level.ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        Vec3 abs = helper.absoluteVec(at);
        owner.teleportTo(helper.getLevel(), abs.x, abs.y, abs.z, 0F, 0F);
        suit.setOwnerId(owner.getUUID());
        return owner;
    }

    private static void dismiss(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** A husk that stands still (no AI) and doesn't burn by day: a monster target that stays where it was put. */
    private static net.minecraft.world.entity.monster.Husk statueHusk(GameTestHelper helper, Vec3 at) {
        var husk = helper.spawn(EntityType.HUSK, at);
        husk.setNoAi(true);
        return husk;
    }

    private static PowerArmorEntity chaingunMind(GameTestHelper helper, BlockPos at, int nuggets) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), at);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        suit.setStoredNuggets(nuggets);
        return suit;
    }

    @GameTest(template = PAD, timeoutTicks = 100)
    public static void chaingunMindShootsMonstersNearItsOwner(GameTestHelper helper) {
        PowerArmorEntity suit = chaingunMind(helper, new BlockPos(4, 2, 1), 64);
        var owner = ownerAt(helper, suit, new Vec3(1.5, 2, 1.5));
        var husk = statueHusk(helper, new Vec3(4.5, 2, 7.5));
        helper.runAfterDelay(80, () -> {
            dismiss(helper, owner);
            helper.assertTrue(!husk.isAlive(), "the husk should be shot down, health " + husk.getHealth());
            helper.assertTrue(husk.getLastHurtByMob() == suit, "killed by the suit itself (it owns its bullets)");
            helper.assertTrue(suit.getStoredNuggets() < 64 && suit.getToolUses() == 64 - suit.getStoredNuggets(),
                    "one nugget from the store per bullet, store " + suit.getStoredNuggets() + " shots " + suit.getToolUses());
            helper.succeed();
        });
    }

    /** Never through the owner: the line of fire is checked before every burst. (With the owner in the way the suit also
     *  walks for a clear shot, e.g. over their head, so the hold is checked over the first ticks, before it has moved.) */
    @GameTest(template = PAD, timeoutTicks = 60)
    public static void chaingunMindHoldsFireWithTheOwnerInTheWay(GameTestHelper helper) {
        PowerArmorEntity suit = chaingunMind(helper, new BlockPos(4, 2, 1), 64);
        var owner = ownerAt(helper, suit, new Vec3(4.5, 2, 3.5));
        var husk = statueHusk(helper, new Vec3(4.5, 2, 6.5));
        helper.assertTrue(!dev.ajaczero.powerarmor.entity.ai.SuitTargets.lineOfFireClear(suit, husk, husk.getBoundingBox().getCenter()),
                "the owner blocks the line");
        boolean[] blockedShot = {false};
        helper.onEachTick(() -> blockedShot[0] |= suit.getToolUses() > 0 && suit.tickCount <= 6);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(suit.getTarget() == husk, "it has picked the husk");
            helper.assertTrue(!blockedShot[0] && husk.getHealth() == husk.getMaxHealth(), "but must not fire through its owner");
            Vec3 aside = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
            owner.teleportTo(helper.getLevel(), aside.x, aside.y, aside.z, 0F, 0F);
        });
        helper.runAfterDelay(50, () -> {
            dismiss(helper, owner);
            helper.assertTrue(!husk.isAlive(), "then it fires, health " + husk.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 90)
    public static void chaingunMindFollowsItsOwner(GameTestHelper helper) {
        PowerArmorEntity suit = chaingunMind(helper, new BlockPos(1, 2, 1), 64);
        var owner = ownerAt(helper, suit, new Vec3(7.5, 2, 7.5));
        helper.runAfterDelay(70, () -> {
            double d = suit.distanceTo(owner);
            dismiss(helper, owner);
            helper.assertTrue(d < 5.5, "it should catch up with its owner (stops at 4), distance " + d);
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 100)
    public static void chaingunMindPunchesWhenOutOfNuggets(GameTestHelper helper) {
        PowerArmorEntity suit = chaingunMind(helper, new BlockPos(4, 2, 1), 0);
        var owner = ownerAt(helper, suit, new Vec3(1.5, 2, 1.5));
        var husk = statueHusk(helper, new Vec3(4.5, 2, 5.5));
        helper.runAfterDelay(80, () -> {
            dismiss(helper, owner);
            float lost = husk.getMaxHealth() - husk.getHealth();
            helper.assertTrue(suit.getToolUses() == 0, "no nuggets: no shots");
            helper.assertTrue(lost >= 1F + PowerArmorConfig.MELEE_BONUS.get() - 0.01, "walks up and punches (1 + melee bonus), lost " + lost);
            helper.succeed();
        });
    }

    /** Who the mind may shoot: monsters, and anything that started it; never the owner, their pets, villagers or friendly suits. */
    @GameTest(template = PAD)
    public static void mindTargetRules(GameTestHelper helper) {
        PowerArmorEntity suit = chaingunMind(helper, new BlockPos(4, 2, 1), 0);
        Player ownerPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.setOwnerId(ownerPlayer.getUUID());
        var rules = (java.util.function.BiPredicate<net.minecraft.world.entity.Entity, Boolean>) (e, provoked) ->
                dev.ajaczero.powerarmor.entity.ai.SuitTargets.canAttack(suit, e, provoked);
        var husk = statueHusk(helper, new Vec3(2.5, 2, 6.5));
        var villager = helper.spawn(EntityType.VILLAGER, new Vec3(6.5, 2, 6.5));
        var cow = helper.spawn(EntityType.COW, new Vec3(1.5, 2, 4.5));
        var wolf = helper.spawn(EntityType.WOLF, new Vec3(7.5, 2, 4.5));
        wolf.tame(ownerPlayer);
        PowerArmorEntity friend = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(7, 2, 1));
        PowerArmorEntity rogue = helper.spawn(ModEntities.POWER_ARMOR.get(), new BlockPos(1, 2, 7));
        rogue.setMind(dev.ajaczero.powerarmor.entity.SuitMind.ROGUE);
        helper.assertTrue(rules.test(husk, false), "monsters: always");
        helper.assertTrue(!rules.test(cow, false) && rules.test(cow, true), "animals: only if they started it");
        helper.assertTrue(!rules.test(villager, true), "villagers: never");
        helper.assertTrue(!rules.test(wolf, true), "the owner's pets: never");
        helper.assertTrue(!rules.test(ownerPlayer, true), "the owner: never");
        helper.assertTrue(!rules.test(friend, true) && rules.test(rogue, false), "friendly suits never, rogues always");
        helper.succeed();
    }

    // =========================================================================================
    // Phase 8 step 3: the EM guard job (grab monsters near home, lift them overhead, fling them up / creepers away)
    // =========================================================================================

    private static PowerArmorEntity emGuard(GameTestHelper helper, BlockPos at) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), at);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.EM_PROJECTOR.get()));
        suit.setHome(helper.absolutePos(at));
        return suit;
    }

    /** The pad's invisible ceiling (10 up) stops the fling early; the fall from it still hurts. In the open the throw is
     *  ~24 blocks up (autopilot scene ap_ai_em_*). */
    @GameTest(template = PAD, timeoutTicks = 100)
    public static void emMindLiftsAndFlingsAMonster(GameTestHelper helper) {
        PowerArmorEntity suit = emGuard(helper, new BlockPos(4, 2, 2));
        var husk = helper.spawn(EntityType.HUSK, new Vec3(4.5, 2, 6.5));
        boolean[] caught = {false};
        double[] highest = {0};
        helper.onEachTick(() -> {
            caught[0] |= suit.getBeamTarget() == husk;
            highest[0] = Math.max(highest[0], husk.getY() - helper.absoluteVec(Vec3.ZERO).y);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(caught[0], "the beam should catch the husk");
            helper.assertTrue(highest[0] > 5.5, "lifted overhead, highest " + highest[0]);
            helper.assertTrue(suit.getToolUses() >= 1, "and thrown (a throw counts as a use)");
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the fall hurts, health " + husk.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = PAD, timeoutTicks = 60)
    public static void emMindLeavesAnimalsAndVillagersAlone(GameTestHelper helper) {
        PowerArmorEntity suit = emGuard(helper, new BlockPos(4, 2, 2));
        helper.spawn(EntityType.COW, new Vec3(2.5, 2, 6.5));
        helper.spawn(EntityType.VILLAGER, new Vec3(6.5, 2, 6.5));
        boolean[] grabbed = {false};
        helper.onEachTick(() -> grabbed[0] |= suit.getBeamTarget() != null || suit.isAiTriggerHeld());
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(!grabbed[0], "only monsters get grabbed");
            helper.succeed();
        });
    }

    /** A creeper must not come down at home: it's thrown up and away instead. */
    @GameTest(template = PAD, timeoutTicks = 100)
    public static void emMindThrowsCreepersAwayFromHome(GameTestHelper helper) {
        PowerArmorEntity suit = emGuard(helper, new BlockPos(4, 2, 1));
        var creeper = helper.spawn(EntityType.CREEPER, new Vec3(4.5, 2, 5.5));
        double[] furthest = {0};
        helper.onEachTick(() -> {
            if (suit.getToolUses() > 0) {
                furthest[0] = Math.max(furthest[0], creeper.getZ() - helper.absoluteVec(Vec3.ZERO).z);
            }
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(suit.getToolUses() >= 1, "the creeper should be thrown");
            helper.assertTrue(furthest[0] > 7.0, "away from home (+z, to the far wall), got to z " + furthest[0]);
            helper.succeed();
        });
    }

    // =========================================================================================
    // Phase 8 step 4: the drill and chainsaw workers
    // =========================================================================================

    private static PowerArmorEntity worker(GameTestHelper helper, BlockPos at, ItemStack tool) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), at);
        suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 8));
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, tool);
        suit.setHome(helper.absolutePos(at));
        return suit;
    }

    /**
     * A stone mass (z 3..8, 4 high) in front of home: the mind clears the hall (home level, 3 high) and leaves the floor,
     * the ceiling, the blocks touching a lava pocket, and a column over a hole in the floor.
     */
    @GameTest(template = PAD, timeoutTicks = 1500)
    public static void mindDrillsAnOpenHallAtHomeLevel(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 3), new BlockPos(8, 5, 8))) {
            helper.setBlock(p, Blocks.STONE);
        }
        helper.setBlock(new BlockPos(8, 3, 8), Blocks.LAVA);
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.AIR); // a hole in the floor under the column x=2, z=6
        PowerArmorEntity suit = worker(helper, new BlockPos(4, 2, 1), new ItemStack(ModItems.DRILL.get()));
        List<BlockPos> kept = List.of(new BlockPos(7, 3, 8), new BlockPos(8, 3, 7), new BlockPos(8, 2, 8), new BlockPos(8, 4, 8),
                new BlockPos(2, 2, 6), new BlockPos(2, 3, 6), new BlockPos(2, 4, 6));
        helper.succeedWhen(() -> {
            for (BlockPos p : kept) {
                helper.assertBlockPresent(Blocks.STONE, p);
            }
            for (int x = 0; x <= 8; x++) {
                for (int z = 3; z <= 8; z++) {
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 5, z)); // the ceiling stays
                    if (!(x == 2 && z == 6)) {
                        helper.assertTrue(!helper.getBlockState(new BlockPos(x, 1, z)).isAir(), "the floor stays at " + x + ",1," + z);
                    }
                }
            }
            int left = 0;
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 3), new BlockPos(8, 4, 8))) {
                left += helper.getBlockState(p).is(Blocks.STONE) && !kept.contains(p) ? 1 : 0;
            }
            helper.assertTrue(left == 0, "the hall should be clear, " + left + " stone left; suit at "
                    + suit.position().subtract(helper.absoluteVec(Vec3.ZERO)) + " goals " + suit.runningGoalNames());
        });
    }

    @GameTest(template = PAD, timeoutTicks = 300)
    public static void mindFellsNaturalTreesButNotCabins(GameTestHelper helper) {
        for (int y = 2; y <= 6; y++) {
            helper.setBlock(new BlockPos(5, y, 6), Blocks.OAK_LOG);
        }
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 5, 5), new BlockPos(6, 7, 7))) {
            if (helper.getBlockState(p).isAir()) {
                helper.setBlock(p, Blocks.OAK_LEAVES); // natural (not persistent)
            }
        }
        helper.setBlock(new BlockPos(1, 2, 6), Blocks.OAK_LOG); // a cabin wall: logs with no leaves
        helper.setBlock(new BlockPos(1, 3, 6), Blocks.OAK_LOG);
        PowerArmorEntity suit = worker(helper, new BlockPos(3, 2, 1), new ItemStack(ModItems.CHAINSAW.get()));
        helper.succeedWhen(() -> {
            for (int y = 2; y <= 6; y++) {
                helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(5, y, 6));
            }
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, 2, 6));
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, 3, 6));
            helper.assertTrue(dropped(helper, Items.OAK_LOG) == 5, "the tree's 5 logs drop");
        });
    }

    // =========================================================================================
    // Phase 8 step 5: rogue suits
    // =========================================================================================

    private static PowerArmorEntity rogue(GameTestHelper helper, BlockPos at, boolean chaingun) {
        PowerArmorEntity suit = helper.spawn(ModEntities.POWER_ARMOR.get(), at);
        dev.ajaczero.powerarmor.entity.RogueSpawner.makeRogue(suit, chaingun);
        return suit;
    }

    /** No fuel, no nuggets: a rogue runs anyway, and shoots any mob it sees (a cow here). */
    @GameTest(template = PAD, timeoutTicks = 120)
    public static void rogueRunsOnNothingAndShootsAnyMob(GameTestHelper helper) {
        PowerArmorEntity suit = rogue(helper, new BlockPos(4, 2, 1), true);
        var cow = helper.spawn(EntityType.COW, new Vec3(4.5, 2, 6.5));
        helper.runAfterDelay(3, () -> helper.assertTrue(suit.isPowered() && suit.isAiDriven(), "a rogue needs no fuel"));
        helper.succeedWhen(() -> {
            helper.assertTrue(!cow.isAlive(), "the cow should be shot");
            helper.assertTrue(suit.getTotalEnergy() == 0 && suit.getStoredNuggets() == 0 && suit.getToolUses() > 0,
                    "fired without fuel or nuggets");
        });
    }

    @GameTest(template = PAD, timeoutTicks = 120)
    public static void defeatedRogueDropsOnlyItsTool(GameTestHelper helper) {
        PowerArmorEntity suit = rogue(helper, SUIT_POS, false);
        suit.hurt(helper.getLevel().damageSources().genericKill(), 1000F);
        helper.runAfterDelay(70, () -> {
            helper.assertItemEntityPresent(ModItems.EM_PROJECTOR.get());
            helper.assertItemEntityNotPresent(Items.IRON_INGOT);
            helper.assertItemEntityNotPresent(ModItems.POWER_ARMOR.get());
            helper.succeed();
        });
    }

    /** Lightning reboots the mind friendly: nobody's, no home, one coal's worth of fuel; the first pilot claims it. */
    @GameTest(template = PAD, timeoutTicks = 200)
    public static void lightningRebootsARogueFriendly(GameTestHelper helper) {
        PowerArmorEntity suit = rogue(helper, SUIT_POS, true);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.isRogue() && suit.isPowered(), "a running rogue");
            var bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
            bolt.moveTo(suit.position());
            suit.thunderHit(helper.getLevel(), bolt);
            helper.assertTrue(!suit.isRogue() && suit.getOwnerId() == null && suit.getHome() == null, "rebooted friendly, nobody's");
            helper.assertTrue(suit.isStunned() && !suit.isPowered(), "knocked out first");
        });
        helper.runAfterDelay(PowerArmorConfig.LIGHTNING_STUN_TICKS.get() + 40, () -> {
            helper.assertTrue(suit.isAiDriven(), "back up as a friendly mind on its last fuel");
            suit.clearFire();
            Player pilot = boardedPilot(helper, suit);
            helper.assertTrue(pilot.getVehicle() == suit && pilot.getUUID().equals(suit.getOwnerId()), "the first pilot claims it");
            helper.succeed();
        });
    }

    /** It leaves after its lifetime, but only unwatched. Own batch: other tests' mock players must not be around. */
    @SuppressWarnings("removal")
    @GameTest(template = PAD, timeoutTicks = 100, batch = "rogue_lifetime")
    public static void rogueLeavesAfterItsTimeWhenUnwatched(GameTestHelper helper) {
        PowerArmorEntity suit = rogue(helper, SUIT_POS, true);
        suit.setRogueAge(PowerArmorConfig.ROGUE_LIFETIME.get());
        var watcher = helper.makeMockServerPlayerInLevel();
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        watcher.teleportTo(helper.getLevel(), near.x, near.y, near.z, 0F, 0F);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!suit.isRemoved(), "not while a player watches");
            dismiss(helper, watcher);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(suit.isRemoved(), "gone once nobody is near");
            helper.succeed();
        });
    }

    /** A friendly chaingun mind defends against rogues like against monsters. */
    @GameTest(template = PAD, timeoutTicks = 80)
    public static void friendlyChaingunShootsRogues(GameTestHelper helper) {
        PowerArmorEntity friend = chaingunMind(helper, new BlockPos(4, 2, 1), 64);
        var owner = ownerAt(helper, friend, new Vec3(1.5, 2, 1.5));
        PowerArmorEntity rogue = rogue(helper, new BlockPos(4, 2, 7), false);
        helper.runAfterDelay(60, () -> {
            dismiss(helper, owner);
            helper.assertTrue(friend.getTarget() == rogue || rogue.getHealth() < rogue.getMaxHealth(), "it should take on the rogue");
            helper.assertTrue(rogue.getHealth() < rogue.getMaxHealth(), "and hit it, health " + rogue.getHealth());
            helper.succeed();
        });
    }

    /** The /summon line in the README makes a chaingun rogue. */
    @GameTest(template = PAD)
    public static void readmeSummonMakesARogue(GameTestHelper helper) throws Exception {
        CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(
                "{id:\"powerarmor:power_armor\",Mind:\"ROGUE\",Inventory:{Items:[{Slot:1b,id:\"powerarmor:chaingun\",count:1}]}}");
        var entity = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
        helper.assertTrue(entity instanceof PowerArmorEntity suit && suit.isRogue()
                && suit.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT).is(ModItems.CHAINGUN.get()), "a chaingun rogue");
        helper.succeed();
    }

    private static ItemStack packedSuit(Player player) {
        return player.getInventory().items.stream().filter(s -> s.is(ModItems.POWER_ARMOR.get())).findFirst().orElse(ItemStack.EMPTY);
    }

    private static InteractionResult useOnFloor(GameTestHelper helper, Player player, ItemStack stack, BlockPos floor) {
        BlockPos abs = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static List<PowerArmorEntity> suitsInTest(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(PowerArmorEntity.class, new AABB(helper.absolutePos(SUIT_POS)).inflate(5));
    }

    private PowerArmorTests() {}
}
