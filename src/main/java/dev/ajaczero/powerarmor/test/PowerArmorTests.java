package dev.ajaczero.powerarmor.test;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PlasmaBolt;
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

    @GameTest(template = PAD, timeoutTicks = 80)
    public static void parkedSuitDoesNotBurnAndRefuelRestarts(GameTestHelper helper) {
        PowerArmorEntity suit = spawnSuit(helper);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!suit.isPowered(), "a suit without fuel is off");
            suit.setBurn(500, 1600);
            suit.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        });
        helper.runAfterDelay(4, () -> helper.assertTrue(suit.isPowered(), "fuel should power it back on"));
        helper.runAfterDelay(44, () -> {
            helper.assertTrue(suit.getBurnLeftExact() == 500, "a parked suit must not burn fuel, left " + suit.getBurnLeftExact());
            helper.assertTrue(suit.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).getCount() == 2, "no coal used while parked");
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
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ModItems.PLASMA_CANNON.get())), "socket takes the plasma cannon");
        player.getInventory().setItem(10, new ItemStack(ModItems.PLASMA_CANNON.get()));
        menu.quickMoveStack(player, 3); // menu slot 3 = player inventory slot 10
        helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.PLASMA_CANNON.get()), "shift-click should install the cannon");
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
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.PLASMA_CANNON.get()));
        return suit;
    }

    /** Holding the tool button fires every cooldown (12 ticks by default), paying the configured energy per shot; releasing stops. */
    @GameTest(template = PAD, timeoutTicks = 80)
    public static void cannonFiresOnCooldownAndCostsFuel(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2)); // 9600 energy
        Player pilot = boardedPilot(helper, suit);
        suit.positionRider(pilot);
        helper.runAfterDelay(2, () -> suit.setTriggerHeld(pilot, true));
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "first shot should fire at once, uses " + suit.getToolUses());
            int used = 2 * PowerArmorConfig.BLAZE_ENERGY.get() - suit.getTotalEnergy(), shot = PowerArmorConfig.CANNON_ENERGY.get();
            helper.assertTrue(used >= shot && used < shot + 10, "a shot costs " + shot + " energy (+ idle), used " + used);
        });
        helper.runAfterDelay(12, () -> helper.assertTrue(suit.getToolUses() == 1, "cooldown: no second shot yet"));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.getToolUses() == 2, "second shot after the 12-tick cooldown, uses " + suit.getToolUses());
            suit.setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(suit.getToolUses() == 2, "releasing the button stops firing, uses " + suit.getToolUses());
            helper.succeed();
        });
    }

    /** Using a tool raises the tool arm on every client (synced TOOL_ACTIVE); an empty socket doesn't. */
    @GameTest(template = PAD, timeoutTicks = 40)
    public static void usingAToolRaisesTheArm(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = boardedPilot(helper, suit);
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
        suit.setBurn(100, 1600); // not enough for a shot by itself
        Player pilot = boardedPilot(helper, suit);
        suit.positionRider(pilot);
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "should fire using the next coal");
            helper.assertTrue(suit.getInventory().getItem(PowerArmorEntity.FUEL_SLOT).isEmpty(), "the coal should have been lit");
            float left = suit.getBurnLeftExact(), expected = 100 + PowerArmorConfig.COAL_ENERGY.get() - PowerArmorConfig.CANNON_ENERGY.get();
            helper.assertTrue(left > expected - 10 && left <= expected, "100 + coal - shot = " + expected + " left (minus idle), got " + left);
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
    public static void unpoweredCannonDoesNotFire(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, ItemStack.EMPTY);
        Player pilot = boardedPilot(helper, suit);
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.getToolUses() == 0, "no fuel, no shots");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void onlyThePilotCanPullTheTrigger(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.COAL, 4));
        boardedPilot(helper, suit);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.setTriggerHeld(stranger, true); // e.g. a forged packet from another player
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!suit.isTriggerHeld() && suit.getToolUses() == 0, "a non-pilot must not fire the suit");
            helper.succeed();
        });
    }

    /** The bolt flies from the muzzle to what the pilot looks at and hurts it with plasma damage. */
    @GameTest(template = PAD, timeoutTicks = 60)
    public static void plasmaBoltHitsWhatThePilotAimsAt(GameTestHelper helper) {
        PowerArmorEntity suit = armedSuit(helper, new BlockPos(4, 2, 1), new ItemStack(Items.COAL, 4));
        suit.setYRot(0); // facing +Z (south), toward the pig
        suit.setYBodyRot(0);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 7));
        pig.setNoAi(true);
        Player pilot = boardedPilot(helper, suit);
        suit.positionRider(pilot);
        Vec3 toPig = pig.position().add(0, pig.getBbHeight() / 2, 0).subtract(pilot.getEyePosition());
        pilot.setYRot((float) (Mth.atan2(-toPig.x, toPig.z) * Mth.RAD_TO_DEG));
        pilot.setXRot((float) (-Mth.atan2(toPig.y, toPig.horizontalDistance()) * Mth.RAD_TO_DEG));
        suit.setTriggerHeld(pilot, true);
        helper.runAfterDelay(1, () -> suit.setTriggerHeld(pilot, false));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(suit.getToolUses() == 1, "one shot");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig should be hit, health " + pig.getHealth());
            helper.assertTrue(pig.getLastDamageSource() != null && pig.getLastDamageSource().is(PlasmaBolt.PLASMA),
                    "damage should be plasma, got " + pig.getLastDamageSource());
            helper.assertTrue(pig.getLastDamageSource().getEntity() == pilot, "the pilot gets the credit (the bolt is the direct cause), got " + pig.getLastDamageSource().getEntity());
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "the bolt must not hit its own suit");
            helper.succeed();
        });
    }

    @GameTest(template = PAD)
    public static void cannonDoesNothingOnFoot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        ItemStack cannon = new ItemStack(ModItems.PLASMA_CANNON.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cannon);
        var result = cannon.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!result.getResult().consumesAction(), "using it on foot should do nothing");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(PlasmaBolt.class, new AABB(helper.absolutePos(SUIT_POS)).inflate(5)).isEmpty(),
                "no bolt on foot");
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

    @GameTest(template = PAD)
    public static void emOnlyGrabsItemsAndOrdinaryMobs(GameTestHelper helper) {
        var level = helper.getLevel();
        java.util.function.Predicate<net.minecraft.world.entity.Entity> grab = dev.ajaczero.powerarmor.item.EmProjectorItem::canGrab;
        helper.assertTrue(grab.test(helper.spawnItem(Items.DIAMOND, 2, 2, 2)), "dropped items: yes");
        helper.assertTrue(grab.test(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 6))), "pigs: yes");
        helper.assertTrue(grab.test(helper.spawn(EntityType.IRON_GOLEM, new BlockPos(6, 2, 6))), "iron golems: yes (heavy but fine)");
        helper.assertTrue(!grab.test(helper.makeMockPlayer(GameType.SURVIVAL)), "players: never");
        helper.assertTrue(!grab.test(EntityType.WITHER.create(level)), "bosses: never");
        helper.assertTrue(!grab.test(EntityType.RAVAGER.create(level)), "too heavy: ravager");
        helper.assertTrue(!grab.test(spawnSuit(helper)), "other suits: never");
        Pig statue = helper.spawn(EntityType.PIG, new BlockPos(6, 2, 2));
        statue.setNoAi(true);
        helper.assertTrue(!grab.test(statue), "no-AI mobs (map statues): never");
        helper.succeed();
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
