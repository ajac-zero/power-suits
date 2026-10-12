package dev.ajaczero.powerarmor.test;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Automated in-game tests. Run them headless with:  ./gradlew runGameTestServer
 * Each test builds the "test_pad" structure, runs, and must call succeed(). GameTest places the
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
        player.setShiftKeyDown(true); // sneak + right-click packs it up
        suit.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(suit.isRemoved(), "suit entity should be gone");
        ItemStack packed = player.getInventory().items.stream().filter(s -> s.is(ModItems.POWER_ARMOR.get())).findFirst().orElse(ItemStack.EMPTY);
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
        Player pilot = boardedPilot(helper, suit);
        suit.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);
        helper.assertTrue(pilot.getVehicle() == null, "pilot should be thrown out when the suit dies");
        helper.runAfterDelay(30, () -> helper.assertTrue(!suit.isRemoved(), "suit should linger for the death animation"));
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(suit.isRemoved(), "suit should be removed after the death animation");
            helper.assertItemEntityPresent(Items.IRON_INGOT);
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
