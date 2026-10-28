package dev.ajaczero.powersuits.test;

import dev.ajaczero.powersuits.HudAlert;
import dev.ajaczero.powersuits.PilotHands;
import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.PowerSuitsConfig;
import dev.ajaczero.powersuits.entity.Bullet;
import dev.ajaczero.powersuits.entity.FlameJet;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.entity.RogueSpawner;
import dev.ajaczero.powersuits.entity.StepSmoothing;
import dev.ajaczero.powersuits.entity.SuitDriving;
import dev.ajaczero.powersuits.entity.SuitEngine;
import dev.ajaczero.powersuits.entity.SuitFuel;
import dev.ajaczero.powersuits.entity.SuitLights;
import dev.ajaczero.powersuits.entity.SuitMind;
import dev.ajaczero.powersuits.entity.SuitSmash;
import dev.ajaczero.powersuits.entity.TntShell;
import dev.ajaczero.powersuits.entity.Trajectory;
import dev.ajaczero.powersuits.entity.ai.DrillWorker;
import dev.ajaczero.powersuits.entity.ai.GunAttack;
import dev.ajaczero.powersuits.entity.ai.SuitTargets;
import dev.ajaczero.powersuits.item.ChaingunItem;
import dev.ajaczero.powersuits.item.EmProjectorItem;
import dev.ajaczero.powersuits.item.HelmetStyle;
import dev.ajaczero.powersuits.item.HelmetTemplateItem;
import dev.ajaczero.powersuits.item.PowerTool;
import dev.ajaczero.powersuits.item.RailCannonItem;
import dev.ajaczero.powersuits.item.SuitHelmetItem;
import dev.ajaczero.powersuits.item.SuitAmmo;
import dev.ajaczero.powersuits.item.SuitLoadout;
import dev.ajaczero.powersuits.item.TntLauncherItem;
import dev.ajaczero.powersuits.menu.PowerSuitMenu;
import dev.ajaczero.powersuits.registry.ModDataComponents;
import dev.ajaczero.powersuits.registry.ModEntities;
import dev.ajaczero.powersuits.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

/**
 * Automated in-game tests. Run them headless with:  ./gradlew runGameTestServer
 * Each test builds the "test_pad" structure (9x10x9, stone floor), runs, and must call succeed(). GameTest places the
 * structure one block above the test origin, so the floor is at relative y=1 and you stand at y=2.
 * They only register in development / when game tests are enabled, so players never see them (SuitTests).
 */
public final class PowerSuitTests {
    private static final BlockPos SUIT_POS = new BlockPos(4, 2, 4);

    private static PowerSuitEntity spawnSuit(GameTestHelper helper) {
        return suitAt(helper, SUIT_POS);
    }

    /** A suit as it's normally used: with a firebox (suits come without an engine) and its helmet on (it seals the
     *  suit and lets the mind run). The helmet and engine tests take them off. */
    private static PowerSuitEntity suitAt(GameTestHelper helper, BlockPos pos) {
        PowerSuitEntity suit = helper.spawn(ModEntities.POWER_SUIT.get(), pos);
        suit.getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, new ItemStack(ModItems.FIREBOX.get()));
        suit.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
        return suit;
    }

    private static Player boardedPilot(GameTestHelper helper, PowerSuitEntity suit) {
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        pilot.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(pilot, InteractionHand.MAIN_HAND, suit.position());
        return pilot;
    }

    @SuitTest
    public static void pilotClimbsInAndOut(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
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

    @SuitTest
    public static void suitAbsorbsDamageForPilot(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        Player pilot = boardedPilot(helper, suit);
        pilot.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 8.0F);
        helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth(), "pilot should take no damage, health " + pilot.getHealth());
        helper.assertTrue(suit.getHealth() < suit.getMaxHealth(), "suit should take the hit");
        helper.succeed();
    }

    @SuitTest
    public static void packingKeepsDamage(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        suit.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 30.0F);
        int expectedDamage = Math.round(suit.getMaxHealth() - suit.getHealth());

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.packUp(player); // what the menu's Pack up button does

        helper.assertTrue(suit.isRemoved(), "suit entity should be gone");
        ItemStack packed = packedSuit(player);
        helper.assertTrue(!packed.isEmpty(), "player should receive the suit item");
        helper.assertTrue(packed.getDamageValue() == expectedDamage, "item damage " + packed.getDamageValue() + " != " + expectedDamage);
        helper.succeed();
    }

    @SuitTest
    public static void deployingRestoresHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.POWER_SUIT.get());
        stack.setDamageValue(30);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        InteractionResult result = useOnFloor(helper, player, stack, new BlockPos(4, 1, 4));
        helper.assertTrue(result.consumesAction(), "deploying should succeed, got " + result);
        List<PowerSuitEntity> suits = suitsInTest(helper);
        helper.assertTrue(suits.size() == 1, "expected one suit, found " + suits.size());
        helper.assertTrue(suits.get(0).getHealth() == 70.0F, "health should be 100 - 30, got " + suits.get(0).getHealth());
        helper.assertTrue(stack.isEmpty(), "item should be used up");
        helper.succeed();
    }

    @SuitTest
    public static void deployingNeedsRoom(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.STONE); // ceiling 2 blocks up; the suit is 3 tall
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.POWER_SUIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        InteractionResult result = useOnFloor(helper, player, stack, new BlockPos(4, 1, 4));
        helper.assertTrue(!result.consumesAction(), "should refuse to deploy under a low ceiling");
        helper.assertTrue(suitsInTest(helper).isEmpty(), "no suit should spawn");
        helper.assertTrue(!stack.isEmpty(), "item should be kept");
        helper.succeed();
    }

    @SuitTest(timeoutTicks = 120)
    public static void destroyedSuitEjectsPilotAndDropsScrap(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 3));
        Player pilot = boardedPilot(helper, suit);
        suit.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
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
    @SuitTest
    public static void loadedSuitGetsCurrentStats(GameTestHelper helper) {
        PowerSuitEntity old = spawnSuit(helper);
        double current = old.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        old.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.036); // the v1 speed
        CompoundTag saved = saveTag(helper, old);

        PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, loaded, saved);
        double speed = loaded.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(speed - current) < 1e-9, "loaded suit should get the current speed " + current + ", got " + speed);
        helper.succeed();
    }

    /** Fallout-style water: an empty suit sinks to the bottom of a pool and doesn't drown. */
    @SuitTest(timeoutTicks = 200)
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
        PowerSuitEntity suit = suitAt(helper, new BlockPos(4, 5, 4));
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

    private static PowerSuitEntity fueledSuit(GameTestHelper helper, ItemStack fuel) {
        PowerSuitEntity suit = spawnSuit(helper);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, fuel);
        return suit;
    }

    /** The firebox burns like a furnace: what a furnace takes, for as many ticks as a furnace burns it. */
    @SuitTest
    public static void fireboxBurnsWhatAFurnaceBurns(GameTestHelper helper) {
        var level = helper.getLevel();
        var firebox = SuitEngine.FIREBOX;
        int[] expected = {1600, 1600, 20000, 2400, 300, 100, 16000};
        Item[] items = {Items.COAL, Items.CHARCOAL, Items.LAVA_BUCKET, Items.BLAZE_ROD, Items.OAK_PLANKS,
                Items.STICK, Items.COAL_BLOCK};
        for (int i = 0; i < items.length; i++) {
            int energy = SuitFuel.energyOf(firebox, new ItemStack(items[i]), level);
            helper.assertTrue(energy == expected[i], items[i] + " should burn " + expected[i] + " like in a furnace, got " + energy);
            helper.assertTrue(SuitFuel.isFuel(firebox, new ItemStack(items[i])), items[i] + " should fit the fuel slot");
        }
        helper.assertTrue(!SuitFuel.isFuel(firebox, new ItemStack(Items.IRON_INGOT)), "iron isn't fuel");
        helper.assertTrue(SuitFuel.energyOf(firebox, new ItemStack(Items.IRON_INGOT), level) == 0, "iron burns for 0");
        helper.succeed();
    }

    private static int fireboxEnergy(GameTestHelper helper, Item item) {
        return SuitFuel.energyOf(SuitEngine.FIREBOX, new ItemStack(item), helper.getLevel());
    }

    /** Within 10% (+-1): tick timing in tests is a tick or so off either way. */
    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) <= expected * 0.1 + 1;
    }

    /** Furnace-style: one item at a time is burnt; idle, walking and sprinting drain 0.25 / 1 / 2 per tick. */
    @SuitTest(timeoutTicks = 120)
    public static void pilotedSuitBurnsOneItemAtATime(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 3));
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 2, "one coal should be burning, two waiting");
            float idle = fireboxEnergy(helper, Items.COAL) - suit.power().getBurnLeftExact();
            helper.assertTrue(near(idle, 20 * PowerSuitsConfig.DRAIN_IDLE.get()), "idle should burn ~0.25/tick (5 in 20 ticks), burnt " + idle);
            float before = suit.power().getBurnLeftExact();
            pilot.zza = 1; // holding W
            helper.runAfterDelay(20, () -> {
                float walk = before - suit.power().getBurnLeftExact();
                helper.assertTrue(near(walk, 20 * PowerSuitsConfig.DRAIN_WALK.get()), "walking should burn ~1/tick (20), burnt " + walk);
                float before2 = suit.power().getBurnLeftExact();
                pilot.setSprinting(true);
                helper.runAfterDelay(20, () -> {
                    float sprint = before2 - suit.power().getBurnLeftExact();
                    helper.assertTrue(near(sprint, 20 * PowerSuitsConfig.DRAIN_SPRINT.get()), "sprinting should burn ~2/tick (40), burnt " + sprint);
                    helper.assertTrue(suit.isPowered(), "suit should still be powered");
                    helper.succeed();
                });
            });
        });
    }

    @SuitTest
    public static void lavaLeavesItsBucket(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.LAVA_BUCKET));
        boardedPilot(helper, suit);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).is(Items.BUCKET), "an empty bucket should stay in the fuel slot");
            helper.assertTrue(suit.power().getBurnLeftExact() > fireboxEnergy(helper, Items.LAVA_BUCKET) - 10, "lava should give its energy, got " + suit.power().getBurnLeftExact());
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 60)
    public static void emptyTankShutsDown(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        suit.power().setBurn(10, 1600); // half a second of walking left, nothing in the slot
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
    @SuitTest(timeoutTicks = 80)
    public static void pilotlessSuitRunsOnItsFuelAndRefuelRestarts(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!suit.isPowered(), "a suit without fuel is off");
            helper.assertTrue(!suit.isAiDriven() && suit.toolOperator() == null, "an unpowered suit has no mind at work");
            suit.power().setBurn(500, 1600);
            suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        });
        helper.runAfterDelay(4, () -> helper.assertTrue(suit.isPowered(), "fuel should power it back on"));
        helper.runAfterDelay(44, () -> {
            double idle = PowerSuitsConfig.DRAIN_IDLE.get();
            float used = 500 - suit.power().getBurnLeftExact();
            helper.assertTrue(used > idle * 30 && used < idle * 45, "the mind's suit burns the idle rate (~40 ticks), used " + used);
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 2, "no new coal needed yet");
            helper.succeed();
        });
    }

    @SuitTest
    public static void saveAndLoadKeepFuelAndSocket(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.CHARCOAL, 5));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(Items.STICK)); // stand-in tool
        suit.power().setBurn(123.5F, 1600);
        CompoundTag saved = saveTag(helper, suit);

        PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, loaded, saved);
        helper.assertTrue(loaded.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 5, "fuel slot should survive saving");
        helper.assertTrue(loaded.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).is(Items.STICK), "socket should survive saving");
        helper.assertTrue(loaded.power().getBurnLeftExact() == 123.5F && loaded.power().getBurnTotal() == 1600, "burning item should survive saving");
        helper.assertTrue(loaded.isPowered(), "a loaded suit with fuel is powered");
        helper.succeed();
    }

    @SuitTest
    public static void packAndDeployKeepLoadout(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 7));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(Items.STICK));
        suit.power().setBurn(900, 1600);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.packUp(player);
        ItemStack packed = packedSuit(player);
        SuitLoadout loadout = packed.get(ModDataComponents.LOADOUT.get());
        helper.assertTrue(loadout != null && loadout.fuel().getCount() == 7 && loadout.burnLeft() == 900, "item should carry the fuel, got " + loadout);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(SUIT_POS)).inflate(4)).isEmpty(), "packing must not drop the fuel on the floor");

        player.getInventory().removeItem(packed);
        player.setItemInHand(InteractionHand.MAIN_HAND, packed);
        useOnFloor(helper, player, packed, new BlockPos(4, 1, 4));
        PowerSuitEntity deployed = suitsInTest(helper).stream().filter(s -> s != suit).findFirst().orElseThrow();
        helper.assertTrue(deployed.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 7, "deployed suit should get its coal back");
        helper.assertTrue(deployed.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).is(Items.STICK), "deployed suit should get its tool back");
        helper.assertTrue(deployed.power().getBurnLeftExact() == 900, "deployed suit should keep its half-burnt coal");
        helper.succeed();
    }

    @SuitTest
    public static void sneakRightClickDoesNotClimbIn(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 4.5)));
        player.setShiftKeyDown(true);
        InteractionResult result = suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
        // (A mock player can't receive the open-screen packet; the autopilot run checks the real screen.)
        helper.assertTrue(result.consumesAction(), "sneak + right-click should be handled by the suit");
        helper.assertTrue(player.getVehicle() == null, "sneak + right-click must not climb in");
        helper.assertTrue(!suit.isRemoved(), "sneak + right-click no longer packs the suit (the menu button does)");
        helper.succeed();
    }

    /** Our tooltip lines arrive through NeoForge's tooltip appender (item/ModTooltip), right after the item's name, and a
     *  styled helmet adds its flavour line after the helmet's own. */
    @SuitTest
    public static void itemTooltipsShowTheirLines(GameTestHelper helper) {
        var context = Item.TooltipContext.of(helper.getLevel());
        List<String> chaingun = tooltipKeys(new ItemStack(ModItems.CHAINGUN.get()), context);
        helper.assertTrue(chaingun.size() >= 4 && chaingun.get(1).equals("item.power_suits.chaingun.tooltip")
                && chaingun.contains("item.power_suits.power_tool.install"), "the chaingun's lines, got " + chaingun);
        ItemStack spartan = new ItemStack(ModItems.SUIT_HELMET.get());
        spartan.set(ModDataComponents.HELMET_STYLE.get(), HelmetStyle.SPARTAN);
        List<String> helmet = tooltipKeys(spartan, context);
        helper.assertTrue(helmet.contains("item.power_suits.suit_helmet.spartan.flavour"), "the style's flavour line, got " + helmet);
        helper.succeed();
    }

    /** The translation keys of a stack's tooltip lines, in order ("" for lines that aren't one translated text). */
    private static List<String> tooltipKeys(ItemStack stack, Item.TooltipContext context) {
        return stack.getTooltipLines(context, null, TooltipFlag.NORMAL).stream()
                .map(line -> line.getContents() instanceof TranslatableContents t ? t.getKey() : "").toList();
    }

    @SuitTest
    public static void menuSlotsAndPackButton(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 4.5)));
        PowerSuitMenu menu = new PowerSuitMenu(1, player.getInventory(), suit.getInventory(), suit);
        helper.assertTrue(menu.stillValid(player), "menu should be usable next to a parked suit");
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_FUEL).mayPlace(new ItemStack(Items.BLAZE_ROD)), "fuel slot takes blaze rods");
        helper.assertTrue(!menu.getSlot(PowerSuitMenu.MENU_FUEL).mayPlace(new ItemStack(Items.DIRT)), "fuel slot refuses dirt");
        helper.assertTrue(!menu.getSlot(PowerSuitMenu.MENU_SOCKET).mayPlace(new ItemStack(Items.COAL)), "socket only takes power tools");
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_SOCKET).mayPlace(new ItemStack(ModItems.CHAINGUN.get())), "socket takes the chaingun");
        player.getInventory().setItem(10, new ItemStack(ModItems.CHAINGUN.get()));
        menu.quickMoveStack(player, PowerSuitMenu.INV_START + 1); // the player's inventory slot 10
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_SOCKET).getItem().is(ModItems.CHAINGUN.get()), "shift-click should install the chaingun");
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_FUEL).getItem().getCount() == 4, "menu shows the suit's fuel");

        // Shift-click coal from the player's inventory: it joins the coal in the fuel slot.
        player.getInventory().setItem(9, new ItemStack(Items.COAL, 10));
        menu.quickMoveStack(player, PowerSuitMenu.INV_START + 0); // the player's inventory slot 9
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_FUEL).getItem().getCount() == 14, "shift-click should move coal into the fuel slot, got " + menu.getSlot(PowerSuitMenu.MENU_FUEL).getItem());

        // The helmet slot (2026-10-19) takes only the suit's helmet, one; shift-click puts a helmet there.
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_HELMET).getItem().is(ModItems.SUIT_HELMET.get()), "menu shows the suit's helmet");
        helper.assertTrue(!menu.getSlot(PowerSuitMenu.MENU_HELMET).mayPlace(new ItemStack(Items.IRON_HELMET)), "helmet slot refuses an iron helmet");
        menu.getSlot(PowerSuitMenu.MENU_HELMET).set(ItemStack.EMPTY);
        helper.assertTrue(!suit.hasHelmet(), "taking it out of the menu takes it off the suit");
        player.getInventory().setItem(11, new ItemStack(ModItems.SUIT_HELMET.get()));
        menu.quickMoveStack(player, PowerSuitMenu.INV_START + 2); // the player's inventory slot 11
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_HELMET).getItem().is(ModItems.SUIT_HELMET.get()) && suit.hasHelmet(), "shift-click should put the helmet on");

        // The empty bucket burnt lava leaves may sit in the fuel slot, but a shift-click never sends one there: it hops to
        // the hotbar like any other item. And a shift-click on a suit slot takes the item out to the player.
        player.getInventory().setItem(12, new ItemStack(Items.BUCKET));
        menu.quickMoveStack(player, PowerSuitMenu.INV_START + 3); // the player's inventory slot 12
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_FUEL).getItem().is(Items.COAL), "the bucket stays out of the fuel slot");
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 1 && player.getInventory().getItem(12).isEmpty(), "it moved to the hotbar");
        menu.quickMoveStack(player, PowerSuitMenu.MENU_SOCKET);
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_SOCKET).getItem().isEmpty() && player.getInventory().countItem(ModItems.CHAINGUN.get()) == 1,
                "shift-clicking the socket takes the tool out");

        Player pilot = boardedPilot(helper, suit);
        helper.assertTrue(!menu.stillValid(player), "menu closes when someone climbs in");
        pilot.stopRiding();

        menu.clickMenuButton(player, PowerSuitMenu.BUTTON_PACK);
        helper.assertTrue(suit.isRemoved(), "Pack up should remove the suit");
        helper.assertTrue(!packedSuit(player).isEmpty(), "Pack up should give the item");
        helper.succeed();
    }

    /** Inside the suit the inventory key (E) opens the suit's menu (vanilla asks the vehicle: HasCustomInventoryScreen):
     *  usable while piloting, no Pack up from inside, and its power button flips the switch (user, 2026-10-27). */
    @SuitTest(timeoutTicks = 20)
    public static void inventoryKeyInsideOpensTheSuitMenu(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        ServerPlayer pilot = mockServerPlayer(helper);
        pilot.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(pilot, InteractionHand.MAIN_HAND, suit.position());
        helper.assertTrue(pilot.getVehicle() == suit, "boarded");
        suit.openCustomInventoryScreen(pilot);
        helper.assertTrue(pilot.containerMenu instanceof PowerSuitMenu m && m.getSuit() == suit && m.stillValid(pilot) && m.isPiloting(pilot),
                "the suit's menu, usable from inside, got " + pilot.containerMenu);
        PowerSuitMenu menu = (PowerSuitMenu) pilot.containerMenu;
        helper.assertTrue(!menu.clickMenuButton(pilot, PowerSuitMenu.BUTTON_PACK) && !suit.isRemoved(), "no packing up from inside");
        menu.clickMenuButton(pilot, PowerSuitMenu.BUTTON_POWER);
        helper.assertTrue(!suit.power().isSwitchedOn(), "the power button switches it off");
        pilot.closeContainer();
        pilot.stopRiding();
        dismiss(helper, pilot);
        helper.succeed();
    }

    /** The swap-hands key (F) and the socket, the suit's off hand (user, 2026-10-27): in the menu, F over a slot swaps it
     *  with the socket (power tools only; the player's real off hand isn't touched); while piloting, F swaps the item in
     *  hand with the socket tool (an empty hand takes it out). */
    @SuitTest
    public static void swapHandsKeyMovesTheSocketTool(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 4.5)));
        PowerSuitMenu menu = new PowerSuitMenu(1, player.getInventory(), suit.getInventory(), suit);
        player.getInventory().setItem(0, new ItemStack(ModItems.DRILL.get())); // hotbar slot 0
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        menu.clicked(PowerSuitMenu.HOTBAR_START, Inventory.SLOT_OFFHAND, ContainerInput.SWAP, player);
        helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).is(ModItems.DRILL.get())
                && player.getInventory().getItem(0).is(ModItems.CHAINGUN.get()), "F in the menu swaps the drill in, the chaingun out");
        helper.assertTrue(player.getOffhandItem().is(Items.TORCH), "the real off hand is left alone");
        player.getInventory().setItem(1, new ItemStack(Items.DIRT));
        menu.clicked(PowerSuitMenu.HOTBAR_START + 1, Inventory.SLOT_OFFHAND, ContainerInput.SWAP, player);
        helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).is(ModItems.DRILL.get())
                && player.getInventory().getItem(1).is(Items.DIRT), "dirt doesn't go in");

        Player pilot = boardedPilot(helper, suit);
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CHAINSAW.get()));
        var event = CommonHooks.onLivingSwapHandItems(pilot);
        helper.assertTrue(event.isCanceled(), "the suit takes over the swap");
        helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).is(ModItems.CHAINSAW.get())
                && pilot.getMainHandItem().is(ModItems.DRILL.get()), "F while piloting swaps the chainsaw in, the drill into the hand");
        pilot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CommonHooks.onLivingSwapHandItems(pilot);
        helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).isEmpty()
                && pilot.getMainHandItem().is(ModItems.CHAINSAW.get()), "an empty hand takes the tool out");
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 5));
        CommonHooks.onLivingSwapHandItems(pilot);
        helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).isEmpty()
                && pilot.getMainHandItem().is(Items.STONE), "stone stays in the hand");
        helper.succeed();
    }

    // =========================================================================================
    // Socket tools (Phase 2)
    // =========================================================================================

    private static PowerSuitEntity armedSuit(GameTestHelper helper, BlockPos pos, ItemStack fuel) {
        PowerSuitEntity suit = suitAt(helper, pos);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, fuel);
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        return suit;
    }

    /** A pilot carrying bullets (iron nuggets) for the chaingun. */
    private static Player armedPilot(GameTestHelper helper, PowerSuitEntity suit) {
        Player pilot = boardedPilot(helper, suit);
        pilot.getInventory().add(new ItemStack(Items.IRON_NUGGET, 64));
        return pilot;
    }

    /** A creative pilot (free bullets). Mock players don't take their game mode's abilities by themselves. */
    private static Player creativePilot(GameTestHelper helper, PowerSuitEntity suit) {
        Player pilot = helper.makeMockPlayer(GameType.CREATIVE);
        pilot.getAbilities().instabuild = true;
        pilot.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(pilot, InteractionHand.MAIN_HAND, suit.position());
        return pilot;
    }

    /** A tap fires one heavy shot at once: one nugget, and only a tick of slow spin's fuel (bullets cost no fuel). */
    @SuitTest(timeoutTicks = 40)
    public static void chaingunTapFiresOneHeavyShot(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2)); // 4800 energy
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        helper.runAfterDelay(2, () -> suit.socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(3, () -> suit.socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.socket().getUses() == 1, "a tap fires exactly one shot, got " + suit.socket().getUses());
            helper.assertTrue(pilot.getInventory().countItem(Items.IRON_NUGGET) == 63, "one nugget used");
            int used = 2 * fireboxEnergy(helper, Items.BLAZE_ROD) - suit.power().getTotalEnergy();
            int most = (int) Math.ceil(2 * ChaingunItem.spinBurn(0.05F)) + 6;
            helper.assertTrue(used <= most, "a tap burns a tick or two of slow spin (+ idle): at most " + most + ", used " + used);
            helper.succeed();
        });
    }

    /** The barrels' fuel follows the spin (user, 2026-10-27: the burn embers show it): a second at full spin burns
     *  full_spin_energy_per_tick a tick, the first second of the spin-up much less. */
    @SuitTest(timeoutTicks = 140)
    public static void chaingunBurnFollowsTheSpin(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = creativePilot(helper, suit);
        suit.positionRider(pilot);
        int spinUp = PowerSuitsConfig.CHAINGUN_SPIN_UP.get();
        int[] energy = new int[3];
        helper.runAfterDelay(2, () -> {
            energy[0] = suit.power().getTotalEnergy();
            suit.socket().setTriggerHeld(pilot, true);
        });
        helper.runAfterDelay(22, () -> energy[1] = suit.power().getTotalEnergy());
        helper.runAfterDelay(spinUp + 10, () -> energy[2] = suit.power().getTotalEnergy());
        helper.runAfterDelay(spinUp + 30, () -> {
            float idle = PowerSuitsConfig.DRAIN_IDLE.get().floatValue() * 20;
            float early = energy[0] - energy[1], full = energy[2] - suit.power().getTotalEnergy();
            float fullRate = PowerSuitsConfig.CHAINGUN_FULL_SPIN_ENERGY.get().floatValue() * 20;
            helper.assertTrue(Math.abs(full - idle - fullRate) <= 3, "a second at full spin burns " + fullRate + " (+ idle), burnt " + full);
            helper.assertTrue(early < full - 10, "the spin-up's first second burns less: " + early + " vs " + full);
            suit.socket().setTriggerHeld(pilot, false);
            helper.succeed();
        });
    }

    /** Held, the barrels spin up and fire faster and faster; let go, they coast down to rest. */
    @SuitTest(timeoutTicks = 140)
    public static void chaingunSpinsUpWhenHeld(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = creativePilot(helper, suit);
        suit.positionRider(pilot);
        int hold = PowerSuitsConfig.CHAINGUN_SPIN_UP.get() + 20;
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(hold, () -> {
            int atSlowRate = hold / PowerSuitsConfig.CHAINGUN_SLOW_INTERVAL.get() + 1;
            helper.assertTrue(suit.socket().getUses() >= 2 * atSlowRate, "spin-up should at least double the slow rate: "
                    + suit.socket().getUses() + " shots in " + hold + " ticks (slow rate: " + atSlowRate + ")");
            helper.assertTrue(suit.socket().getSpin() > 0.99F, "full spin after the spin-up time, got " + suit.socket().getSpin());
            suit.socket().setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(hold + 40, () -> {
            helper.assertTrue(suit.socket().getSpin() == 0F, "the barrels should have coasted to rest, spin " + suit.socket().getSpin());
            helper.succeed();
        });
    }

    /** Each bullet uses one iron nugget; without nuggets the gun only clicks (the barrels still burn their spin). */
    @SuitTest(timeoutTicks = 80)
    public static void chaingunUsesOneNuggetPerBullet(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = boardedPilot(helper, suit);
        pilot.getInventory().add(new ItemStack(Items.IRON_NUGGET, 3));
        suit.positionRider(pilot);
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(suit.socket().getUses() == 3, "three nuggets, three bullets, got " + suit.socket().getUses());
            helper.assertTrue(pilot.getInventory().countItem(Items.IRON_NUGGET) == 0, "all nuggets used");
            int used = 2 * fireboxEnergy(helper, Items.BLAZE_ROD) - suit.power().getTotalEnergy();
            int most = (int) Math.ceil(61 * ChaingunItem.spinBurn(1F)) + 20;
            helper.assertTrue(used <= most, "only the spin (+ idle) costs fuel: at most " + most + ", used " + used);
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 30)
    public static void creativePilotNeedsNoBullets(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = creativePilot(helper, suit);
        suit.positionRider(pilot);
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.socket().getUses() == 1, "creative fires without nuggets, uses " + suit.socket().getUses());
            helper.succeed();
        });
    }

    /** Barrels overheat after the configured number of bullets in a row, stay locked while they cool until the heat is
     *  back to 0 (the HUD keeps OVERHEAT up that long: ChaingunEffects follows the same rule), and fire again once cold.
     *  (Skipped when overheat is off in the config.) */
    @SuitTest(timeoutTicks = 600)
    public static void chaingunOverheatsAndCools(GameTestHelper helper) {
        if (!PowerSuitsConfig.CHAINGUN_OVERHEAT.get()) {
            helper.succeed();
            return;
        }
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 8));
        Player pilot = creativePilot(helper, suit);
        suit.positionRider(pilot);
        suit.socket().setTriggerHeld(pilot, true);
        int limit = PowerSuitsConfig.CHAINGUN_OVERHEAT_SHOTS.get();
        int[] stopped = {-1};
        boolean[] cold = {false};
        helper.onEachTick(() -> {
            if (stopped[0] >= 0 && !cold[0]) {
                // from the overheat until the barrels are cold: locked exactly while there's heat left
                float heat = suit.socket().getHeat();
                helper.assertTrue(ChaingunItem.isOverheated(suit) == heat > 0F, "locked while hot, heat " + heat);
                cold[0] = heat <= 0F;
            }
            if (stopped[0] < 0 && suit.socket().getHeat() >= 1F) {
                stopped[0] = suit.socket().getUses();
                helper.assertTrue(stopped[0] == limit, "overheats after " + limit + " bullets, got " + stopped[0]);
                helper.runAfterDelay(20, () -> {
                    helper.assertTrue(suit.socket().getUses() == stopped[0], "an overheated gun must not fire");
                    suit.socket().setTriggerHeld(pilot, false);
                });
                helper.runAfterDelay(30 + PowerSuitsConfig.CHAINGUN_COOL_TICKS.get(), () -> suit.socket().setTriggerHeld(pilot, true));
                helper.runAfterDelay(40 + PowerSuitsConfig.CHAINGUN_COOL_TICKS.get(), () -> {
                    helper.assertTrue(cold[0], "the barrels cooled all the way down");
                    helper.assertTrue(suit.socket().getUses() > stopped[0], "cooled barrels fire again");
                    helper.succeed();
                });
            }
        });
    }

    /** The visor's alert levels (user, 2026-10-27): orange from half gone, red in the last 15%, both ways round. */
    @SuitTest(timeoutTicks = 5)
    public static void hudAlertLevels(GameTestHelper helper) {
        helper.assertTrue(HudAlert.ofLeft(1F) == HudAlert.NONE && HudAlert.ofLeft(0.51F) == HudAlert.NONE, "most of the hull: fine");
        helper.assertTrue(HudAlert.ofLeft(HudAlert.WARNING_AT) == HudAlert.WARNING && HudAlert.ofLeft(0.16F) == HudAlert.WARNING, "half: warning");
        helper.assertTrue(HudAlert.ofLeft(HudAlert.CRITICAL_AT) == HudAlert.CRITICAL && HudAlert.ofLeft(0F) == HudAlert.CRITICAL, "last 15%: critical");
        helper.assertTrue(HudAlert.ofFill(0.49F) == HudAlert.NONE && HudAlert.ofFill(0.5F) == HudAlert.WARNING, "heat from half: warning");
        helper.assertTrue(HudAlert.ofFill(0.85F) == HudAlert.CRITICAL && HudAlert.ofFill(1F) == HudAlert.CRITICAL, "heat's last 15%: critical");
        helper.assertTrue(HudAlert.WARNING.max(HudAlert.CRITICAL) == HudAlert.CRITICAL && HudAlert.WARNING.max(HudAlert.NONE) == HudAlert.WARNING, "max");
        helper.succeed();
    }

    /** Using a tool raises the tool arm on every client (synced TOOL_ACTIVE); an empty socket doesn't. */
    @SuitTest(timeoutTicks = 40)
    public static void usingAToolRaisesTheArm(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.BLAZE_ROD, 2));
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        helper.runAfterDelay(2, () -> suit.socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(suit.socket().isActive(), "tool held: the arm should be raised");
            suit.socket().setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!suit.socket().isActive(), "released: the arm should lower");
            suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, ItemStack.EMPTY);
            suit.socket().setTriggerHeld(pilot, true);
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(!suit.socket().isActive(), "an empty socket has nothing to raise");
            helper.succeed();
        });
    }

    @SuitTest
    public static void shotLightsTheNextFuelItem(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.COAL, 1));
        suit.power().setBurn(1, 1600); // not enough to turn the barrels for long by itself
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.socket().getUses() == 1, "should fire using the next coal");
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).isEmpty(), "the coal should have been lit");
            helper.succeed();
        });
    }

    @SuitTest
    public static void emptySocketDoesNothing(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.socket().getUses() == 0, "no tool, no shots");
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 3, "only the idle burn uses fuel");
            helper.succeed();
        });
    }

    @SuitTest
    public static void unpoweredChaingunDoesNotFire(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, ItemStack.EMPTY);
        Player pilot = armedPilot(helper, suit);
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.socket().getUses() == 0, "no fuel, no shots");
            helper.succeed();
        });
    }

    @SuitTest
    public static void onlyThePilotCanPullTheTrigger(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, SUIT_POS, new ItemStack(Items.COAL, 4));
        armedPilot(helper, suit);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.socket().setTriggerHeld(stranger, true); // e.g. a forged packet from another player
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!suit.socket().isTriggerHeld() && suit.socket().getUses() == 0, "a non-pilot must not fire the suit");
            helper.succeed();
        });
    }

    /** The bullet flies from the muzzle to what the pilot looks at and hurts it with bullet damage. */
    @SuitTest(timeoutTicks = 60)
    public static void bulletHitsWhatThePilotAimsAt(GameTestHelper helper) {
        PowerSuitEntity suit = armedSuit(helper, new BlockPos(4, 2, 1), new ItemStack(Items.COAL, 4));
        suit.setYRot(0); // facing +Z (south), toward the pig
        suit.setYBodyRot(0);
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 7));
        pig.setNoAi(true);
        Player pilot = armedPilot(helper, suit);
        suit.positionRider(pilot);
        lookAt(pilot, pig.position().add(0, pig.getBbHeight() / 2, 0));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(1, () -> suit.socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(suit.socket().getUses() == 1, "one shot");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig should be hit, health " + pig.getHealth());
            helper.assertTrue(pig.getLastDamageSource() != null && pig.getLastDamageSource().is(Bullet.BULLET),
                    "damage should be bullet, got " + pig.getLastDamageSource());
            helper.assertTrue(pig.getLastDamageSource().getEntity() == pilot, "the pilot gets the credit (the bullet is the direct cause), got " + pig.getLastDamageSource().getEntity());
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "the bullet must not hit its own suit");
            helper.succeed();
        });
    }

    /** Bullets ignore the half-second hit immunity: two bullets a tick apart both hurt. */
    @SuitTest(timeoutTicks = 30)
    public static void bulletsIgnoreHitImmunity(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 6));
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

    @SuitTest
    public static void chaingunDoesNothingOnFoot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        ItemStack gun = new ItemStack(ModItems.CHAINGUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        var result = gun.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!result.consumesAction(), "using it on foot should do nothing");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(Bullet.class, new AABB(helper.absolutePos(SUIT_POS)).inflate(5)).isEmpty(),
                "no bullet on foot");
        helper.succeed();
    }

    // =========================================================================================
    // The hand (Phase 3; since 2026-10-13 the right hand, fully normal: left click attacks and mines)
    // =========================================================================================

    /** Decided 2026-10-13: the hand works as normal, so the pilot fights with what they hold. */
    @SuitTest(timeoutTicks = 40)
    public static void pilotFightsWithTheHand(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 6));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.canOperate(), "suit should be running");
            pilot.attack(pig);
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pilot's sword should hurt the pig, health " + pig.getHealth());
            helper.succeed();
        });
    }

    /** Mining from inside: allowed, and not slowed down 5x for "not standing on the ground" while the suit stands. */
    @SuitTest(timeoutTicks = 40)
    public static void pilotMinesAtNormalSpeed(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.canOperate() && suit.onGround(), "suit should be running and standing");
            var event = CommonHooks.onLeftClickBlock(pilot, block, Direction.UP,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(!event.isCanceled(), "mining from inside a running suit is allowed");
            var state = helper.getLevel().getBlockState(block);
            float bare = pilot.getInventory().getSelectedItem().getDestroySpeed(state); // what the empty hand digs at, before penalties
            float speed = pilot.getDestroySpeed(state, block);
            helper.assertTrue(Math.abs(speed - bare) < 1e-4, "hand mining speed " + speed + ", expected the on-ground " + bare);
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 20)
    public static void unpoweredHandCannotFightOrMine(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, ItemStack.EMPTY); // no fuel: powered down
        Player pilot = boardedPilot(helper, suit);
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 6));
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        helper.runAfterDelay(2, () -> {
            pilot.attack(pig);
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "a powered-down hand doesn't swing, pig health " + pig.getHealth());
            var event = CommonHooks.onLeftClickBlock(pilot, block, Direction.UP,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            helper.assertTrue(event.isCanceled(), "a powered-down hand doesn't mine");
            helper.succeed();
        });
    }

    /** The socket tool is the suit's off hand: the player's real off-hand item isn't used from inside. */
    @SuitTest(timeoutTicks = 20)
    public static void offHandBelongsToTheTool(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP, block, false);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(CommonHooks.onRightClickBlock(pilot, InteractionHand.OFF_HAND, block, hit).isCanceled(),
                    "off-hand use is cancelled while piloting");
            helper.assertTrue(!CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, block, hit).isCanceled(),
                    "the main hand still uses things");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 40)
    public static void handNeedsPower(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, ItemStack.EMPTY); // no fuel: powered down
        Player pilot = boardedPilot(helper, suit);
        BlockPos block = helper.absolutePos(new BlockPos(4, 1, 6));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(block).add(0, 0.5, 0), Direction.UP, block, false);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, block, hit).isCanceled(),
                    "a powered-down suit's hand can't use things");
            suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL));
        });
        helper.runAfterDelay(35, () -> { // fuel -> startup (1.4 s) -> hand works
            helper.assertTrue(suit.canOperate(), "suit should be running again");
            helper.assertTrue(!CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, block, hit).isCanceled(),
                    "a running suit's hand uses items normally");
            helper.succeed();
        });
    }

    @SuitTest
    public static void suitArmReachesFurther(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double blocks = player.blockInteractionRange(), entities = player.entityInteractionRange();
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
        helper.assertTrue(player.getVehicle() == suit, "should be inside");
        helper.assertTrue(Math.abs(player.blockInteractionRange() - blocks - PilotHands.EXTRA_REACH) < 1e-6,
                "block reach should grow by 1.5, got " + player.blockInteractionRange());
        helper.assertTrue(Math.abs(player.entityInteractionRange() - entities - PilotHands.EXTRA_REACH) < 1e-6,
                "entity reach should grow by 1.5");
        player.stopRiding();
        helper.assertTrue(player.blockInteractionRange() == blocks && player.entityInteractionRange() == entities, "climbing out removes the bonus");
        helper.succeed();
    }

    // =========================================================================================
    // EM projector (Phase 4)
    // =========================================================================================

    /** A fuelled suit at (4,2,1) facing +Z with the EM projector, and a pilot looking at {@code at}. */
    private static Player emPilotLookingAt(GameTestHelper helper, PowerSuitEntity suit, Vec3 at) {
        suit.setYRot(0);
        suit.setYBodyRot(0);
        Player pilot = boardedPilot(helper, suit);
        suit.positionRider(pilot);
        lookAt(pilot, at);
        return pilot;
    }

    /**
     * Turns a (mock) pilot to look at a point (world coordinates), the way a real player's client does: body yaw, head
     * yaw and pitch together, this tick and last. A living thing looks where its HEAD points (getViewVector uses yHeadRot),
     * and a mock player's head starts at a random yaw; setting only setYRot left the view off by up to 5 degrees, so a
     * shot at a pig 5 blocks out missed about one run in five (bulletHitsWhatThePilotAimsAt, 2026-10-26).
     */
    static void lookAt(Player pilot, Vec3 at) {
        Vec3 to = at.subtract(pilot.getEyePosition());
        turn(pilot, (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG), (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    /** Sets a (mock) pilot's view: yaw and pitch, body and head, now and last tick (see lookAt). */
    static void turn(Player pilot, float yaw, float pitch) {
        pilot.setYRot(yaw);
        pilot.yRotO = yaw;
        pilot.setYHeadRot(yaw);
        pilot.yHeadRotO = yaw;
        pilot.setXRot(pitch);
        pilot.xRotO = pitch;
    }

    private static PowerSuitEntity emSuit(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(4, 2, 1));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.EM_PROJECTOR.get()));
        return suit;
    }

    /** A normal pig (with AI: Minecraft only moves mobs by velocity while their AI runs). */
    private static Pig floatingPigTarget(GameTestHelper helper) {
        return helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 7));
    }

    @SuitTest(timeoutTicks = 100)
    public static void emPullsItemsIntoThePilotsInventory(GameTestHelper helper) {
        PowerSuitEntity suit = emSuit(helper);
        var item = helper.spawnItem(Items.DIAMOND, 4.5F, 2.2F, 7.5F);
        Player pilot = emPilotLookingAt(helper, suit, item.position());
        int before = suit.power().getTotalEnergy();
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(2, () -> helper.assertTrue(suit.socket().getBeamTarget() == item, "the beam should lock onto the diamond"));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(item.isRemoved(), "the diamond should have been collected");
            helper.assertTrue(pilot.getInventory().countItem(Items.DIAMOND) == 1, "the diamond should be in the pilot's inventory");
            int used = before - suit.power().getTotalEnergy();
            int max = 60 * (PowerSuitsConfig.EM_HOLD_ENERGY.get() + 1);
            helper.assertTrue(used > 0 && used < max, "pulling costs energy per tick (+ idle), used " + used);
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 80)
    public static void emHoldsAMobThenThrowsIt(GameTestHelper helper) {
        PowerSuitEntity suit = emSuit(helper);
        Pig pig = floatingPigTarget(helper);
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.socket().getBeamTarget() == pig && pig.isNoGravity(), "the pig should float in the beam");
            helper.assertTrue(pig.distanceTo(suit) < 5.5, "the pig should be pulled in close, distance " + pig.distanceTo(suit));
            pilot.setXRot(0); // look straight ahead, then let go
            int before = suit.power().getTotalEnergy();
            suit.socket().setTriggerHeld(pilot, false);
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(suit.socket().getBeamTarget() == null && !pig.isNoGravity(), "released: gravity back on");
                helper.assertTrue(before - suit.power().getTotalEnergy() >= PowerSuitsConfig.EM_THROW_ENERGY.get(), "a throw costs the throw energy");
                helper.assertTrue(suit.socket().getUses() == 1, "one throw");
                helper.succeed();
            });
        });
    }

    /** Thrown into the test pad's wall: the slam hurts. */
    @SuitTest(timeoutTicks = 80)
    public static void emThrownMobSlamsIntoTheWall(GameTestHelper helper) {
        PowerSuitEntity suit = emSuit(helper);
        Pig pig = floatingPigTarget(helper);
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(25, () -> {
            pilot.setXRot(0);
            suit.socket().setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < pig.getMaxHealth(), "the slam should hurt, health " + pig.getHealth());
            helper.assertTrue(pig.getLastDamageSource() != null && pig.getLastDamageSource().is(EmProjectorItem.EM_SLAM),
                    "damage should be em_slam, got " + pig.getLastDamageSource());
            helper.succeed();
        });
    }

    /** A thrown mob bowls through the mobs in its way (2026-10-27): each takes the impact (a full-speed throw's: the
     *  config's slam damage) as "bowled over", credited to the pilot, and so does the thrown one, which flies on to the
     *  next even if the first hit killed it. Two husks (no AI) stand in its path, 1 and 2 blocks on. */
    @SuitTest(timeoutTicks = 80)
    public static void emThrownMobBowlsThroughMobs(GameTestHelper helper) {
        PowerSuitEntity suit = emSuit(helper);
        Pig pig = floatingPigTarget(helper);
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.socket().setTriggerHeld(pilot, true);
        net.minecraft.world.entity.monster.zombie.Husk[] husks = new net.minecraft.world.entity.monster.zombie.Husk[2];
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(suit.socket().getBeamTarget() == pig, "holding the pig");
            Vec3 rel = helper.relativeVec(pig.position());
            // standing on the floor in its path (it's held low: the pilot looked down at it)
            husks[0] = statueHusk(helper, new Vec3(rel.x, 2, rel.z + 1.0));
            husks[1] = statueHusk(helper, new Vec3(rel.x, 2, rel.z + 2.0)); // (the pad's barrier wall is at z 9)
            turn(pilot, 0, 0); // look straight ahead along +Z, then let go
            suit.socket().setTriggerHeld(pilot, false);
        });
        // checked a few ticks after the throw: later, the knocked-back husks suffocate in the test's barrier wall
        helper.runAfterDelay(29, () -> {
            for (int i = 0; i < 2; i++) {
                var h = husks[i];
                helper.assertTrue(h.getHealth() < h.getMaxHealth() || !h.isAlive(), "husk " + (i + 1) + " was hit, health " + h.getHealth());
                var src = h.getLastDamageSource();
                helper.assertTrue(src != null && src.is(EmProjectorItem.EM_STRUCK) && src.getEntity() == pilot,
                        "husk " + (i + 1) + " bowled over, by the pilot: " + src + " at " + helper.relativeVec(h.position()) + ", pig at " + helper.relativeVec(pig.position()));
            }
            helper.assertTrue(husks[0].getMaxHealth() - Math.max(0, husks[0].getHealth()) >= PowerSuitsConfig.EM_SLAM_DAMAGE.get() * 0.7,
                    "a near full-speed hit does most of the slam damage, husk health " + husks[0].getHealth());
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth() || !pig.isAlive(), "the pig takes it too");
            helper.succeed();
        });
    }

    /** The visor's throw arc (EmProjectorItem.predictThrow, drawn by client/hud/EmProjectorHud) is where a thrown mob
     *  lands: a pig sent off at a throw's speed from 2 blocks up lands where and when the arc says. (Slowed so it
     *  doesn't walk in the air: a mob's own steering is the one thing the arc can't know.) */
    @SuitTest(timeoutTicks = 60)
    public static void emThrowArcPredictsWhereItLands(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(4.5, 4, 3.5));
        pig.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 10));
        Vec3 velocity = new Vec3(0, 0.1, 0.6);
        pig.setDeltaMovement(velocity);
        Trajectory arc = EmProjectorItem.predictThrow(helper.getLevel(), pig, velocity);
        int[] ticks = {0, -1};
        Vec3[] landed = {null};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (landed[0] == null && pig.onGround()) {
                landed[0] = pig.position();
                ticks[1] = ticks[0];
            }
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(arc.hit(), "the arc ends on the floor, ends at " + arc.end());
            helper.assertTrue(landed[0] != null, "the pig landed");
            helper.assertTrue(landed[0].distanceTo(arc.end()) < 0.3, "it lands where the arc says: " + landed[0] + " vs " + arc.end());
            helper.assertTrue(arc.end().z - arc.path().get(0).z > 2, "a real throw (lands 2+ blocks out): " + arc.end());
            helper.assertTrue(Math.abs(ticks[1] - arc.ticks()) <= 1, "and when (tick " + ticks[1] + " vs " + arc.ticks() + ")");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 40)
    public static void emTapShovesAMobAway(GameTestHelper helper) {
        PowerSuitEntity suit = emSuit(helper);
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 4)); // room to fly before the pad's wall
        double startZ = pig.getZ();
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(1, () -> suit.socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.socket().getUses() == 1, "a tap should shove");
            helper.assertTrue(pig.getZ() - startZ > 1.5, "the pig should fly away from the suit, moved " + (pig.getZ() - startZ));
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 40)
    public static void emLetsGoWhenThePilotLeaves(GameTestHelper helper) {
        PowerSuitEntity suit = emSuit(helper);
        Pig pig = floatingPigTarget(helper);
        Player pilot = emPilotLookingAt(helper, suit, pig.position().add(0, 0.45, 0));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(pig.isNoGravity(), "held");
            pilot.stopRiding();
        });
        helper.runAfterDelay(13, () -> {
            helper.assertTrue(!pig.isNoGravity() && suit.socket().getBeamTarget() == null, "the pig must drop when the pilot climbs out");
            helper.assertTrue(suit.socket().getUses() == 0, "climbing out drops, it doesn't throw");
            helper.succeed();
        });
    }

    /** Decided 2026-10-13: everything but spectators, creative players and statues can be caught (players where PvP is
     *  on); suits and the em_heavy tag (bosses, iron golems, ravagers, wardens) are heavy: held in place, not lifted. */
    @SuitTest
    public static void emGrabRules(GameTestHelper helper) {
        var level = helper.getLevel();
        Predicate<Entity> grab = EmProjectorItem::canGrab;
        Predicate<Entity> heavy = EmProjectorItem::isHeavy;
        helper.assertTrue(grab.test(helper.spawnItem(Items.DIAMOND, 2, 2, 2)), "dropped items: yes");
        helper.assertTrue(grab.test(helper.spawn(EntityTypes.PIG, new BlockPos(2, 2, 6))), "pigs: yes");
        var golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(6, 2, 6));
        helper.assertTrue(grab.test(golem) && heavy.test(golem), "iron golems: heavy");
        var warden = EntityTypes.WARDEN.create(level, EntitySpawnReason.LOAD);
        helper.assertTrue(grab.test(warden) && heavy.test(warden), "wardens: heavy (thin, but no beam lifts a warden)");
        helper.assertTrue(!heavy.test(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.LOAD)) && !heavy.test(EntityTypes.COW.create(level, EntitySpawnReason.LOAD)), "ordinary mobs: light");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(grab.test(player) && !heavy.test(player), "players: yes");
        helper.assertTrue(!grab.test(helper.makeMockPlayer(GameType.CREATIVE)), "creative players: no");
        var wither = EntityTypes.WITHER.create(level, EntitySpawnReason.LOAD);
        var ravager = EntityTypes.RAVAGER.create(level, EntitySpawnReason.LOAD);
        PowerSuitEntity other = spawnSuit(helper);
        helper.assertTrue(grab.test(wither) && heavy.test(wither), "bosses: heavy");
        helper.assertTrue(grab.test(ravager) && heavy.test(ravager), "ravagers: heavy");
        helper.assertTrue(grab.test(other) && heavy.test(other), "other suits: heavy");
        Pig statue = helper.spawn(EntityTypes.PIG, new BlockPos(6, 2, 2));
        statue.setNoAi(true);
        helper.assertTrue(!grab.test(statue), "no-AI mobs (map statues): never");
        PowerSuitEntity em = emSuit(helper);
        boolean pvp = level.getGameRules().get(GameRules.PVP);
        helper.assertTrue(EmProjectorItem.canHold(em, player) == pvp, "a pilot holds players only with PvP on");
        RogueSpawner.makeRogue(em, false);
        helper.assertTrue(EmProjectorItem.canHold(em, player), "a rogue doesn't ask");
        helper.succeed();
    }

    /** A suit in the beam fights it: stays on the ground, slowed, dragged only a little. */
    @SuitTest(timeoutTicks = 60)
    public static void heavyThingsFightTheField(GameTestHelper helper) {
        PowerSuitEntity suit = emSuit(helper);
        PowerSuitEntity heavy = suitAt(helper, new BlockPos(4, 2, 7)); // unpowered: parked
        Vec3 start = heavy.position();
        Player pilot = emPilotLookingAt(helper, suit, heavy.position().add(0, 1.5, 0));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.socket().getBeamTarget() == heavy, "caught in the beam");
            helper.assertTrue(!heavy.isNoGravity() && Math.abs(heavy.getY() - start.y) < 0.01, "not lifted");
            double moved = heavy.position().distanceTo(start);
            helper.assertTrue(moved > 0.1 && moved < 1.5, "dragged a little (1.2 blocks/s at most, slowed), moved " + moved);
            suit.socket().setTriggerHeld(pilot, false);
            helper.succeed();
        });
    }

    // =========================================================================================
    // Mining tools (Phase 5): 3x3 drill, chainsaw
    // =========================================================================================

    /** Protection-mod stand-in: cancels BreakEvents at these positions (empty in normal play). A set, not one field:
     *  copies of a test can run side by side (runGameTestOnly -Prepeat), each protecting its own block. */
    @EventBusSubscriber(modid = PowerSuits.MODID)
    public static final class ProtectedBlock {
        static final Set<BlockPos> POSITIONS = ConcurrentHashMap.newKeySet();

        @SubscribeEvent
        public static void veto(BreakBlockEvent event) {
            if (POSITIONS.contains(event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    /** A suit at (4,2,1) facing +Z with this tool and 4 blaze rods (19200 energy), the pilot looking at {@code at}. */
    private static Player toolPilot(GameTestHelper helper, PowerSuitEntity suit, ItemStack tool, Vec3 at) {
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, tool);
        return emPilotLookingAt(helper, suit, helper.absoluteVec(at));
    }

    /** Fills the 3x3 wall x 3..5, y 2..4 at z=5 (relative) with a block. */
    private static void wall(GameTestHelper helper, Block block) {
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

    private static int dropped(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(helper.absolutePos(new BlockPos(4, 3, 5))).inflate(4))
                .stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static PowerSuitEntity toolSuit(GameTestHelper helper) {
        return suitAt(helper, new BlockPos(4, 2, 1));
    }

    @SuitTest(timeoutTicks = 60)
    public static void drillDigsA3x3Face(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 6), Blocks.OBSIDIAN); // behind: slow, so it's still there when we check
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        int before = suit.power().getTotalEnergy();
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(4, () -> helper.assertTrue(wallLeft(helper) == 9, "stone takes 6 ticks with a diamond tool, not instant"));
        helper.runAfterDelay(25, () -> {
            suit.socket().setTriggerHeld(pilot, false);
            helper.assertTrue(wallLeft(helper) == 0, "all 9 blocks of the face should be gone, left " + wallLeft(helper));
            helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(4, 3, 6));
            helper.assertTrue(dropped(helper, Items.COBBLESTONE) == 9, "9 cobblestone should drop, got " + dropped(helper, Items.COBBLESTONE));
            int used = before - suit.power().getTotalEnergy(), face = 9 * PowerSuitsConfig.DRILL_ENERGY.get();
            helper.assertTrue(used >= face && used < face + 40, "9 blocks' fuel (+ idle), used " + used);
            helper.succeed();
        });
    }

    /** Diamond tier: obsidian yes (slowly: the face waits for its hardest block), bedrock never, wood is the chainsaw's. */
    @SuitTest(timeoutTicks = 300)
    public static void drillDigsObsidianButNotBedrockOrWood(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(4, 3, 5), Blocks.OBSIDIAN);
        helper.setBlock(new BlockPos(3, 3, 5), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.OAK_PLANKS);
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(150, () -> helper.assertTrue(wallLeft(helper) == 9, "obsidian takes ~9.4 s and the stone waits for it"));
        helper.runAfterDelay(220, () -> {
            suit.socket().setTriggerHeld(pilot, false);
            helper.assertBlockNotPresent(Blocks.OBSIDIAN, new BlockPos(4, 3, 5));
            helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 3, 5));
            helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 3, 5));
            helper.assertTrue(wallLeft(helper) == 2, "only bedrock and planks should be left, left " + wallLeft(helper));
            helper.assertTrue(dropped(helper, Items.OBSIDIAN) == 1, "obsidian should drop");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 60)
    public static void drillSkidsOnWood(GameTestHelper helper) {
        wall(helper, Blocks.OAK_PLANKS);
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(wallLeft(helper) == 9 && suit.socket().getUses() == 0, "the drill must not cut wood");
            helper.succeed();
        });
    }

    /** Every block posts a BreakEvent for the pilot, so protection mods can say no. The hand still can't break anything. */
    @SuitTest(timeoutTicks = 60)
    public static void drillRespectsProtection(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 5));
        ProtectedBlock.POSITIONS.add(center);
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        var handBreak = new BreakBlockEvent(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 5)), Blocks.STONE.defaultBlockState(), pilot);
        NeoForge.EVENT_BUS.post(handBreak);
        helper.assertTrue(handBreak.isCanceled(), "a pilot's own (hand) break must still be cancelled");
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(14, () -> {
            suit.socket().setTriggerHeld(pilot, false);
            ProtectedBlock.POSITIONS.remove(center);
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 5));
            helper.assertTrue(wallLeft(helper) == 1, "the 8 unprotected blocks should break, left " + wallLeft(helper));
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 80)
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
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.CHAINSAW.get()), new Vec3(4.5, 2.5, 5));
        int before = suit.power().getTotalEnergy();
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(30, () -> {
            suit.socket().setTriggerHeld(pilot, false);
            for (int y = 2; y <= 6; y++) {
                helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(4, y, 5));
            }
            helper.assertTrue(dropped(helper, Items.OAK_LOG) == 5, "5 logs should drop, got " + dropped(helper, Items.OAK_LOG));
            int used = before - suit.power().getTotalEnergy(), tree = 5 * PowerSuitsConfig.CHAINSAW_ENERGY.get();
            helper.assertTrue(used >= tree && used < tree + 30, "fuel per log x 5 (+ idle), used " + used);
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 60)
    public static void chainsawSparesLogCabins(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(4, y, 5), Blocks.OAK_LOG); // a log wall: no leaves
        }
        helper.setBlock(new BlockPos(4, 5, 5), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.CHAINSAW.get()), new Vec3(4.5, 2.5, 5));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(25, () -> {
            suit.socket().setTriggerHeld(pilot, false);
            helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(4, 2, 5));
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 3, 5));
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 4, 5));
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 60)
    public static void chainsawSkidsOnStone(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.CHAINSAW.get()), new Vec3(4.5, 3.5, 5));
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(wallLeft(helper) == 9 && suit.socket().getUses() == 0, "the chainsaw must not cut stone");
            helper.succeed();
        });
    }

    /** TOOL_RUNNING (synced) drives the tool's loop animation and sound on every client. */
    @SuitTest(timeoutTicks = 40)
    public static void runningToolIsSyncedForAnimationAndSound(GameTestHelper helper) {
        PowerSuitEntity suit = toolSuit(helper);
        Player pilot = toolPilot(helper, suit, new ItemStack(ModItems.DRILL.get()), new Vec3(4.5, 3.5, 5));
        PowerTool drill = (PowerTool) ModItems.DRILL.get();
        helper.assertTrue(drill.runAnimation() != null && drill.runSound() != null, "the drill has a loop animation and sound");
        helper.assertTrue(((PowerTool) ModItems.CHAINSAW.get()).runSound() != null, "so does the chainsaw");
        suit.socket().setTriggerHeld(pilot, true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(suit.socket().isRunning(), "held: running (spinning even in the air)");
            suit.socket().setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!suit.socket().isRunning(), "released: stopped");
            helper.succeed();
        });
    }

    @SuitTest
    public static void miningToolsDoNothingOnFoot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (ItemStack tool : List.of(new ItemStack(ModItems.DRILL.get()), new ItemStack(ModItems.CHAINSAW.get()))) {
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            helper.assertTrue(!tool.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction(), "no use on foot");
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
    @SuitTest
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

    @SuitTest(timeoutTicks = 40)
    public static void sealKeepsFireOutForFuel(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(5, () -> {
            var sources = helper.getLevel().damageSources();
            float before = suit.power().getBurnLeftExact();
            pilot.hurtServer(helper.getLevel(), sources.onFire(), 1F); // redirected to the suit (PilotProtection), where the seal pays for it
            float paid = before - suit.power().getBurnLeftExact();
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "fire must not hurt a powered suit, health " + suit.getHealth());
            helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth(), "nor its pilot");
            helper.assertTrue(Math.abs(paid - PowerSuitsConfig.SEAL_ENERGY_PER_DAMAGE.get()) < 0.01, "one point of fire should cost its energy, paid " + paid);
            float before2 = suit.power().getBurnLeftExact();
            suit.hurtServer(helper.getLevel(), sources.inFire(), 1F); // the next tick of fire within the half second is already paid for
            helper.assertTrue(suit.power().getBurnLeftExact() == before2, "fire within the half-second immunity is free");
            suit.hurtServer(helper.getLevel(), sources.lava(), 4F);
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

    @SuitTest(timeoutTicks = 20)
    public static void unpoweredSuitBurns(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> suit.igniteForSeconds(5));
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(suit.isOnFire(), "with no power the suit catches fire like anything else");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 20)
    public static void unpoweredSuitIsNotSealed(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, ItemStack.EMPTY);
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(2, () -> {
            pilot.hurtServer(helper.getLevel(), helper.getLevel().damageSources().onFire(), 2F);
            helper.assertTrue(suit.getHealth() < suit.getMaxHealth(), "without power, fire hurts the hull");
            pilot.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            helper.assertTrue(pilot.hasEffect(MobEffects.POISON), "without power, poison reaches the pilot");
            var breathe = new LivingBreatheEvent(pilot, false, 1, 4);
            NeoForge.EVENT_BUS.post(breathe);
            helper.assertTrue(!breathe.canBreathe(), "without power, the pilot can't breathe under water");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 20)
    public static void sealBlocksHarmfulEffectsAndFeedsAir(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        helper.runAfterDelay(2, () -> {
            float before = suit.power().getBurnLeftExact();
            pilot.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            helper.assertTrue(!pilot.hasEffect(MobEffects.POISON), "poison should be kept out");
            helper.assertTrue(Math.abs(before - suit.power().getBurnLeftExact() - PowerSuitsConfig.SEAL_EFFECT_ENERGY.get()) < 0.01, "and cost fuel");
            pilot.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100));
            helper.assertTrue(pilot.hasEffect(MobEffects.REGENERATION), "good effects get through");
            var breathe = new LivingBreatheEvent(pilot, false, 1, 4);
            NeoForge.EVENT_BUS.post(breathe);
            helper.assertTrue(breathe.canBreathe(), "a powered suit feeds its pilot air");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 220)
    public static void lightningKnocksTheSuitOutThenItRestarts(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        int stun = PowerSuitsConfig.LIGHTNING_STUN_TICKS.get();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(suit.canOperate(), "suit should be running");
            var bolt = EntityTypes.LIGHTNING_BOLT.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
            bolt.snapTo(suit.position());
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

    @SuitTest(timeoutTicks = 20)
    public static void lightningOnAnEmptyTankDoesNotStun(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            var bolt = EntityTypes.LIGHTNING_BOLT.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
            suit.thunderHit(helper.getLevel(), bolt);
            helper.assertTrue(!suit.isStunned(), "nothing to knock out without fuel");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 30)
    public static void stompHurtsEverythingAroundButThePilot(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        Pig near = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 6));   // 2 blocks from the suit's centre
        Pig far = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 8));    // 4 blocks: outside the shock wave
        helper.runAfterDelay(3, () -> {
            var fall = helper.getLevel().damageSources().fall();
            suit.causeFallDamage(2.3F, 1F, fall); // a jump on flat ground: just a thud
            helper.assertTrue(near.getHealth() == near.getMaxHealth(), "a normal jump must not stomp");
            suit.causeFallDamage(6F, 1F, fall);
            float expected = (float) Math.min(PowerSuitsConfig.STOMP_MAX_DAMAGE.get(), PowerSuitsConfig.STOMP_DAMAGE_PER_BLOCK.get() * 6);
            float dealt = near.getMaxHealth() - near.getHealth();
            helper.assertTrue(dealt > 0 && (near.isDeadOrDying() || dealt >= expected * 0.5F - 0.01F), "the near pig should be hurt, took " + dealt);
            helper.assertTrue(near.getLastDamageSource() != null && near.getLastDamageSource().getEntity() == pilot, "credited to the pilot");
            helper.assertTrue(far.getHealth() == far.getMaxHealth(), "the far pig is out of reach");
            helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth() && suit.getHealth() == suit.getMaxHealth(), "the suit and its pilot are spared");
            helper.assertTrue(near.getDeltaMovement().y > 0 || near.isDeadOrDying(), "the shock wave throws it up");
            helper.succeed();
        });
    }

    @SuitTest
    public static void pilotHitsHarder(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
        double bonus = PowerSuitsConfig.MELEE_BONUS.get();
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - base - bonus) < 1e-6,
                "melee damage should grow by " + bonus + ", got " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        player.stopRiding();
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == base, "climbing out removes the bonus");
        helper.succeed();
    }

    /** A suit at (4,2,2) facing +Z: its hitbox spans x 3.8..5.2, z 1.8..3.2; walking reaches 0.6 further (z 3.8), sprinting 1.1 (z 4.3). */
    @SuitTest(timeoutTicks = 20)
    public static void walkingSmashesPanesAndLeavesSprintingAlsoGlass(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(4, 2, 2));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 4));
        suit.setYRot(0);
        Player pilot = boardedPilot(helper, suit);
        turn(pilot, 0F, 0F);
        BlockPos pane = new BlockPos(4, 3, 3), leaves = new BlockPos(5, 4, 3), glass = new BlockPos(4, 2, 4), wall = new BlockPos(4, 2, 7);
        helper.setBlock(pane, Blocks.GLASS_PANE);
        helper.setBlock(leaves, Blocks.OAK_LEAVES);
        helper.setBlock(glass, Blocks.GLASS);
        helper.setBlock(wall, Blocks.GLASS_PANE); // out of reach
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(SuitSmash.tick(suit, pilot) == 0, "standing still breaks nothing");
            pilot.zza = 1;
            SuitSmash.tick(suit, pilot);
            helper.assertBlockNotPresent(Blocks.GLASS_PANE, pane);
            helper.assertBlockNotPresent(Blocks.OAK_LEAVES, leaves);
            helper.assertBlockPresent(Blocks.GLASS, glass); // full glass needs a sprint
            helper.assertBlockPresent(Blocks.GLASS_PANE, wall);
            pilot.setSprinting(true);
            SuitSmash.tick(suit, pilot);
            helper.assertBlockNotPresent(Blocks.GLASS, glass);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(glass)).inflate(4)).isEmpty(), "smashed blocks drop nothing");
            helper.succeed();
        });
    }

    /** Sealed in: no eating or drinking. The refused click passes on (FAIL) so the socket tool can take it. */
    @SuitTest(timeoutTicks = 20)
    public static void pilotCannotEatOrDrink(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player pilot = boardedPilot(helper, suit);
        pilot.getFoodData().setFoodLevel(10);
        helper.runAfterDelay(2, () -> {
            for (ItemStack stack : List.of(new ItemStack(Items.BREAD), new ItemStack(Items.POTION), new ItemStack(Items.MILK_BUCKET),
                    new ItemStack(Items.HONEY_BOTTLE), new ItemStack(Items.SUSPICIOUS_STEW))) {
                pilot.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var result = CommonHooks.onItemRightClick(pilot, InteractionHand.MAIN_HAND);
                helper.assertTrue(result == InteractionResult.FAIL, stack + " should be refused (and passed on), got " + result);
                pilot.startUsingItem(InteractionHand.MAIN_HAND);
                helper.assertTrue(!pilot.isUsingItem(), "the pilot must not start eating/drinking " + stack);
            }
            pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            helper.assertTrue(CommonHooks.onItemRightClick(pilot, InteractionHand.MAIN_HAND) == null,
                    "other items are used as normal");
            BlockPos cake = helper.absolutePos(new BlockPos(4, 2, 6));
            helper.setBlock(new BlockPos(4, 2, 6), Blocks.CAKE);
            var hit = new BlockHitResult(Vec3.atCenterOf(cake), Direction.UP, cake, false);
            var event = CommonHooks.onRightClickBlock(pilot, InteractionHand.MAIN_HAND, cake, hit);
            helper.assertTrue(event.getUseBlock() == TriState.FALSE, "no cake from inside either");
            pilot.stopRiding();
            pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
            pilot.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(pilot.isUsingItem(), "on foot the player eats as normal");
            helper.succeed();
        });
    }

    /** Effects already inside the pilot (food poisoning from before climbing in) hurt the pilot, not the suit. */
    @SuitTest(timeoutTicks = 20)
    public static void poisonAlreadyInsideHurtsThePilot(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200)); // ate a spider eye
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(player.hasEffect(MobEffects.POISON), "the poison came in with the pilot");
            // A poison tick (NeoForge's own damage type, neoforge:poison), then a wither tick.
            float fuel = suit.power().getBurnLeftExact();
            var types = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
            player.damageCooldownTime = 0;
            player.hurtServer(helper.getLevel(), new DamageSource(types.getOrThrow(NeoForgeMod.POISON_DAMAGE)), 1F);
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "poison must not reach the suit");
            player.damageCooldownTime = 0;
            player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().wither(), 1F);
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "poison hurts the pilot, health " + player.getHealth());
            helper.assertTrue(suit.getHealth() == suit.getMaxHealth(), "not the suit, health " + suit.getHealth() + " from "
                    + (suit.getLastDamageSource() == null ? "-" : suit.getLastDamageSource().getMsgId()));
            helper.assertTrue(player.getLastDamageSource() != null && player.getLastDamageSource().is(DamageTypes.WITHER),
                    "the wither tick reached the pilot");
            helper.assertTrue(suit.power().getBurnLeftExact() == fuel, "and costs no fuel");
            helper.succeed();
        });
    }

    // =========================================================================================
    // The machine mind (Phase 8 step 1): owner, home, who drives, visor, tools without a pilot
    // =========================================================================================

    @SuitTest
    public static void pilotOwnsTheSuitAndExitMarksHome(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        helper.runAfterDelay(2, () -> { // the engine lights on the suit's first tick
            Player pilot = boardedPilot(helper, suit);
            helper.assertTrue(pilot.getUUID().equals(suit.brain().getOwnerId()), "climbing in claims the suit");
            helper.assertTrue(!suit.isAiDriven() && suit.toolOperator() == pilot, "the pilot drives, not the mind");
            pilot.stopRiding();
            helper.assertTrue(pilot.getUUID().equals(suit.brain().getOwnerId()), "the last pilot stays the owner");
            helper.assertTrue(suit.blockPosition().equals(suit.brain().getHome()), "home is where the pilot got out, got " + suit.brain().getHome());
            helper.assertTrue(suit.isAiDriven() && suit.toolOperator() == suit, "powered and empty: the mind drives");
            helper.succeed();
        });
    }

    @SuitTest
    public static void deployedSuitWorksForWhoeverPlacedIt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.POWER_SUIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        useOnFloor(helper, player, stack, new BlockPos(4, 1, 4));
        PowerSuitEntity suit = suitsInTest(helper).get(0);
        helper.assertTrue(player.getUUID().equals(suit.brain().getOwnerId()), "the placer owns it");
        helper.assertTrue(helper.absolutePos(new BlockPos(4, 2, 4)).equals(suit.brain().getHome()), "home is where it was placed, got " + suit.brain().getHome());
        helper.succeed();
    }

    @SuitTest
    public static void mindOwnerAndHomeSurviveSaving(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        UUID owner = UUID.randomUUID();
        suit.setMind(SuitMind.ROGUE);
        suit.brain().setOwnerId(owner);
        suit.brain().setHome(new BlockPos(10, 64, -3));
        CompoundTag saved = saveTag(helper, suit);
        PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, loaded, saved);
        helper.assertTrue(loaded.isRogue() && owner.equals(loaded.brain().getOwnerId()) && new BlockPos(10, 64, -3).equals(loaded.brain().getHome()),
                "mind, owner and home should survive saving");
        CompoundTag old = saveTag(helper, spawnSuit(helper)); // a suit saved before the AI existed: friendly, nobody's, no home
        old.remove("Mind");
        PowerSuitEntity legacy = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, legacy, old);
        helper.assertTrue(!legacy.isRogue() && legacy.brain().getOwnerId() == null && legacy.brain().getHome() == null, "old saves load as friendly, unowned, homeless");
        helper.succeed();
    }

    /** Pushed 6 blocks from home, the idle mind walks back at a pilot's walk (vanilla mobs move at speed squared; see
     *  travel) and onto the block itself (vanilla's path for a 2-block-wide mob ended 1.7 blocks short). */
    @SuitTest(timeoutTicks = 140)
    public static void idleSuitWalksBackHome(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(7, 2, 4));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        BlockPos home = helper.absolutePos(new BlockPos(1, 2, 4));
        suit.brain().setHome(home);
        double[] fastest = {0};
        Vec3[] last = {suit.position()};
        helper.onEachTick(() -> { // blocks moved per tick (the velocity field already has the next tick's drag in it)
            fastest[0] = Math.max(fastest[0], suit.position().subtract(last[0]).horizontalDistance() * 20);
            last[0] = suit.position();
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(!suit.brain().isAwayFromHome(0.5), "should be back home after 5 s, at " + suit.position().subtract(Vec3.atBottomCenterOf(home)));
            double walk = SuitDriving.WALK_BLOCKS_PER_SECOND; // the mind's speed 1.0 = the pilot's walk
            helper.assertTrue(fastest[0] > walk * 0.88 && fastest[0] < walk * 1.27, "walks (~" + walk + " blocks/s), fastest " + fastest[0]);
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 40)
    public static void unpoweredSuitStaysPut(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(2, 2, 2));
        suit.brain().setHome(helper.absolutePos(new BlockPos(6, 2, 6)));
        Vec3 start = suit.position();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.position().distanceTo(start) < 0.05, "no fuel, no mind: it must not walk home");
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 40)
    public static void boardingStopsTheMind(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(2, 2, 2));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        suit.brain().setHome(helper.absolutePos(new BlockPos(6, 2, 6)));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!suit.getNavigation().isDone(), "on its way home");
            Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
            suit.interact(pilot, InteractionHand.MAIN_HAND, suit.position());
            helper.assertTrue(pilot.getVehicle() == suit, "boarded mid-walk");
            helper.assertTrue(suit.getNavigation().isDone() && !suit.isAiDriven(), "the pilot takes over at once");
            helper.succeed();
        });
    }

    @SuitTest
    public static void rogueLetsNobodyIn(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 2));
        suit.setMind(SuitMind.ROGUE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!suit.interact(player, InteractionHand.MAIN_HAND, suit.position()).consumesAction() && player.getVehicle() == null,
                "a rogue can't be boarded");
        helper.succeed();
    }

    /** Amber whenever piloted (no more red for tool use or hits), green for the friendly mind, red only for a rogue. */
    @SuitTest
    public static void visorShowsWhoIsInControl(GameTestHelper helper) {
        var friendly = SuitMind.FRIENDLY;
        var rogue = SuitMind.ROGUE;
        float[] piloted = SuitLights.visorColorFor(true, friendly);
        float[] green = SuitLights.visorColorFor(false, friendly);
        float[] red = SuitLights.visorColorFor(false, rogue);
        helper.assertTrue(piloted[0] > 0.9F && piloted[1] > 0.5F && piloted[2] < 0.4F, "piloted = amber");
        helper.assertTrue(SuitLights.visorColorFor(true, rogue) == piloted, "a pilot's visor is amber, whatever the mind");
        helper.assertTrue(green[1] > green[0] && green[1] > green[2], "friendly mind = green");
        helper.assertTrue(red[0] > 0.9F && red[1] < 0.3F, "rogue = red");
        helper.succeed();
    }

    /** Who broke the watched positions (the BreakEvent's player), for checking who a tool breaks blocks as. Per
     *  position: copies of a test can run side by side (runGameTestOnly -Prepeat). */
    @EventBusSubscriber(modid = PowerSuits.MODID)
    public static final class BreakWatch {
        /** Watched position -> who broke it (NOBODY until it breaks). */
        static final Map<BlockPos, UUID> BREAKERS = new ConcurrentHashMap<>();
        static final UUID NOBODY = new UUID(0, 0);

        @SubscribeEvent
        public static void seen(BreakBlockEvent event) {
            BREAKERS.computeIfPresent(event.getPos(), (pos, old) -> event.getPlayer().getUUID());
        }
    }

    /** The tool operator refactor: with nobody inside, the mind aims the head and pulls the trigger; the drill digs the
     *  3x3 face and every block is broken as the owner (a FakePlayer with their UUID, so claim mods see the owner). */
    @SuitTest(timeoutTicks = 60)
    public static void mindDrillsAsItsOwner(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 5));
        BreakWatch.BREAKERS.put(center, BreakWatch.NOBODY);
        PowerSuitEntity suit = toolSuit(helper);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.DRILL.get()));
        UUID owner = UUID.randomUUID();
        suit.brain().setOwnerId(owner);
        suit.brain().setHome(suit.blockPosition()); // the mind only drills its hall round home (the wall is in it)
        helper.runAfterDelay(2, () -> { // once the engine runs (an unpowered mind forgets its aim and trigger)
            suit.brain().setAim(Vec3.atCenterOf(center));
            suit.socket().setAiTrigger(true);
        });
        helper.runAfterDelay(5, () -> helper.assertTrue(suit.socket().isActive(), "the mind's trigger runs the tool"));
        helper.runAfterDelay(30, () -> {
            suit.socket().setAiTrigger(false);
            UUID breaker = BreakWatch.BREAKERS.remove(center);
            helper.assertTrue(wallLeft(helper) == 0, "the face should be gone, left " + wallLeft(helper));
            helper.assertTrue(dropped(helper, Items.COBBLESTONE) == 9, "9 cobblestone should drop, got " + dropped(helper, Items.COBBLESTONE));
            helper.assertTrue(owner.equals(breaker), "broken as the owner, got " + breaker);
            helper.succeed();
        });
    }

    /** The chaingun with the mind at the trigger: it spins up and shoots, needing no nuggets (2026-10-27: the mind's
     *  tools never run out). */
    @SuitTest(timeoutTicks = 40)
    public static void mindChaingunNeedsNoAmmo(GameTestHelper helper) {
        PowerSuitEntity suit = toolSuit(helper);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        helper.runAfterDelay(2, () -> {
            suit.brain().setAim(helper.absoluteVec(new Vec3(4.5, 3, 7)));
            suit.socket().setAiTrigger(true);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit.socket().isActive() && suit.socket().getUses() > 0, "spins and shoots without nuggets, shots " + suit.socket().getUses());
            helper.succeed();
        });
    }

    // =========================================================================================
    // Phase 8 step 2: ammo (the hidden nugget store until 2026-10-27), the chaingun job (follow + defend the owner)
    // =========================================================================================

    /** Since 2026-10-27 the suit holds no ammo (SuitAmmo): an old suit's hidden nugget store and ammo slot, and an item
     *  packed with them, hand it all back as items at the suit's feet; new packed items carry none. */
    @SuitTest(timeoutTicks = 60)
    public static void oldSuitsHandBackTheirAmmo(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        CompoundTag saved = saveTag(helper, suit);
        saved.putInt("Nuggets", 100);
        saved.remove("UUID"); // a second suit, not the same one again (the level refuses a duplicate)
        PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, loaded, saved);
        loaded.getInventory().setItem(PowerSuitEntity.OLD_AMMO_SLOT, new ItemStack(Items.TNT, 7)); // as an old save loads it
        loaded.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 6.5)));
        helper.getLevel().addFreshEntity(loaded);
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag oldItem = new CompoundTag();
        oldItem.putInt("nuggets", 30);
        SuitLoadout old = SuitLoadout.CODEC.parse(ops, oldItem).getOrThrow();
        helper.assertTrue(old.nuggets() == 30, "an old packed item still reads its nuggets");
        PowerSuitEntity deployed = suitAt(helper, new BlockPos(6, 2, 6));
        deployed.applyLoadout(old);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(dropped(helper, Items.IRON_NUGGET) == 130 && dropped(helper, Items.TNT) == 7,
                    "all of it dropped: nuggets " + dropped(helper, Items.IRON_NUGGET) + ", TNT " + dropped(helper, Items.TNT));
            helper.assertTrue(loaded.getInventory().getItem(PowerSuitEntity.OLD_AMMO_SLOT).isEmpty(), "the old slot is empty");
            SuitLoadout repacked = deployed.getLoadout();
            helper.assertTrue(repacked.nuggets() == 0 && repacked.ammo().isEmpty(), "packed again it carries none, got " + repacked);
            helper.succeed();
        });
    }

    /** A pilot's ammo comes from their own inventory, the off hand first (like a bow's arrows); none, no shot. The
     *  machine mind (an operator that isn't a player) never runs out (user, 2026-10-27). */
    @SuitTest
    public static void pilotAmmoComesFromTheirInventory(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        pilot.getInventory().setItem(3, new ItemStack(Items.IRON_NUGGET, 2));
        pilot.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_NUGGET, 1));
        helper.assertTrue(ChaingunItem.countBullets(pilot) == 3, "counts every nugget carried, got " + ChaingunItem.countBullets(pilot));
        helper.assertTrue(ChaingunItem.takeBullet(suit, pilot) && pilot.getOffhandItem().isEmpty(), "the off hand first");
        helper.assertTrue(ChaingunItem.takeBullet(suit, pilot) && ChaingunItem.takeBullet(suit, pilot), "then the inventory");
        helper.assertTrue(!ChaingunItem.takeBullet(suit, pilot) && ChaingunItem.countBullets(pilot) == 0, "then nothing");
        helper.assertTrue(ChaingunItem.takeBullet(suit, suit), "the mind's gun never runs dry");
        pilot.getAbilities().instabuild = true;
        helper.assertTrue(ChaingunItem.takeBullet(suit, pilot) && ChaingunItem.countBullets(pilot) == SuitAmmo.ENDLESS, "creative: free");
        helper.succeed();
    }

    /** An owner who is really in the level (the mind looks its owner up by UUID): a creative mock server player. */
    @SuppressWarnings("removal")
    private static ServerPlayer ownerAt(GameTestHelper helper, PowerSuitEntity suit, Vec3 at) {
        ServerPlayer owner = mockServerPlayer(helper);
        Vec3 abs = helper.absoluteVec(at);
        owner.teleportTo(helper.getLevel(), abs.x, abs.y, abs.z, Set.of(), 0F, 0F, true);
        suit.brain().setOwnerId(owner.getUUID());
        return owner;
    }

    private static void dismiss(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** A husk that stands still (no AI) and doesn't burn by day: a monster target that stays where it was put. */
    private static Husk statueHusk(GameTestHelper helper, Vec3 at) {
        var husk = helper.spawn(EntityTypes.HUSK, at);
        husk.setNoAi(true);
        return husk;
    }

    private static PowerSuitEntity chaingunMind(GameTestHelper helper, BlockPos at) {
        PowerSuitEntity suit = suitAt(helper, at);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        return suit;
    }

    @SuitTest(timeoutTicks = 100)
    public static void chaingunMindShootsMonstersNearItsOwner(GameTestHelper helper) {
        PowerSuitEntity suit = chaingunMind(helper, new BlockPos(4, 2, 1));
        var owner = ownerAt(helper, suit, new Vec3(1.5, 2, 1.5));
        var husk = statueHusk(helper, new Vec3(4.5, 2, 7.5));
        helper.runAfterDelay(80, () -> {
            dismiss(helper, owner);
            helper.assertTrue(!husk.isAlive(), "the husk should be shot down, health " + husk.getHealth());
            helper.assertTrue(husk.getLastHurtByMob() == suit, "killed by the suit itself (it owns its bullets)");
            helper.assertTrue(suit.socket().getUses() > 0, "with bullets that never run out, shots " + suit.socket().getUses());
            helper.succeed();
        });
    }

    /** Never through the owner: the line of fire is checked before every burst. (With the owner in the way the suit also
     *  walks for a clear shot, e.g. over their head, so the hold is checked over the first ticks, before it has moved.) */
    @SuitTest(timeoutTicks = 60)
    public static void chaingunMindHoldsFireWithTheOwnerInTheWay(GameTestHelper helper) {
        PowerSuitEntity suit = chaingunMind(helper, new BlockPos(4, 2, 1));
        var owner = ownerAt(helper, suit, new Vec3(4.5, 2, 3.5));
        var husk = statueHusk(helper, new Vec3(4.5, 2, 6.5));
        helper.assertTrue(!SuitTargets.lineOfFireClear(suit, husk, husk.getBoundingBox().getCenter()),
                "the owner blocks the line");
        boolean[] blockedShot = {false};
        helper.onEachTick(() -> blockedShot[0] |= suit.socket().getUses() > 0 && suit.tickCount <= 6);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(suit.getTarget() == husk, "it has picked the husk");
            helper.assertTrue(!blockedShot[0] && husk.getHealth() == husk.getMaxHealth(), "but must not fire through its owner");
            Vec3 aside = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
            owner.teleportTo(helper.getLevel(), aside.x, aside.y, aside.z, Set.of(), 0F, 0F, true);
        });
        helper.runAfterDelay(50, () -> {
            dismiss(helper, owner);
            helper.assertTrue(!husk.isAlive(), "then it fires, health " + husk.getHealth());
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 90)
    public static void chaingunMindFollowsItsOwner(GameTestHelper helper) {
        PowerSuitEntity suit = chaingunMind(helper, new BlockPos(1, 2, 1));
        var owner = ownerAt(helper, suit, new Vec3(7.5, 2, 7.5));
        helper.runAfterDelay(70, () -> {
            double d = suit.distanceTo(owner);
            dismiss(helper, owner);
            helper.assertTrue(d < 5.5, "it should catch up with its owner (stops at 4), distance " + d);
            helper.succeed();
        });
    }

    /** Who the mind may shoot: monsters, and anything that started it; never the owner, their pets, villagers or friendly suits. */
    @SuitTest
    public static void mindTargetRules(GameTestHelper helper) {
        PowerSuitEntity suit = chaingunMind(helper, new BlockPos(4, 2, 1));
        Player ownerPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.brain().setOwnerId(ownerPlayer.getUUID());
        var rules = (BiPredicate<Entity, Boolean>) (e, provoked) ->
                SuitTargets.canAttack(suit, e, provoked);
        var husk = statueHusk(helper, new Vec3(2.5, 2, 6.5));
        var villager = helper.spawn(EntityTypes.VILLAGER, new Vec3(6.5, 2, 6.5));
        var cow = helper.spawn(EntityTypes.COW, new Vec3(1.5, 2, 4.5));
        var wolf = helper.spawn(EntityTypes.WOLF, new Vec3(7.5, 2, 4.5));
        wolf.tame(ownerPlayer);
        PowerSuitEntity friend = suitAt(helper, new BlockPos(7, 2, 1));
        PowerSuitEntity rogue = suitAt(helper, new BlockPos(1, 2, 7));
        rogue.setMind(SuitMind.ROGUE);
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

    private static PowerSuitEntity emGuard(GameTestHelper helper, BlockPos at) {
        PowerSuitEntity suit = suitAt(helper, at);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.EM_PROJECTOR.get()));
        suit.brain().setHome(helper.absolutePos(at));
        return suit;
    }

    /** The pad's invisible ceiling (10 up) stops the fling early; the fall from it still hurts. In the open the throw is
     *  ~24 blocks up (autopilot scene ap_ai_em_*). */
    @SuitTest(timeoutTicks = 100)
    public static void emMindLiftsAndFlingsAMonster(GameTestHelper helper) {
        PowerSuitEntity suit = emGuard(helper, new BlockPos(4, 2, 2));
        var husk = helper.spawn(EntityTypes.HUSK, new Vec3(4.5, 2, 6.5));
        boolean[] caught = {false};
        double[] highest = {0};
        helper.onEachTick(() -> {
            caught[0] |= suit.socket().getBeamTarget() == husk;
            highest[0] = Math.max(highest[0], husk.getY() - helper.absoluteVec(Vec3.ZERO).y);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(caught[0], "the beam should catch the husk");
            helper.assertTrue(highest[0] > 5.5, "lifted overhead, highest " + highest[0]);
            helper.assertTrue(suit.socket().getUses() >= 1, "and thrown (a throw counts as a use)");
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the fall hurts, health " + husk.getHealth());
            helper.succeed();
        });
    }

    @SuitTest(timeoutTicks = 60)
    public static void emMindLeavesAnimalsAndVillagersAlone(GameTestHelper helper) {
        PowerSuitEntity suit = emGuard(helper, new BlockPos(4, 2, 2));
        helper.spawn(EntityTypes.COW, new Vec3(2.5, 2, 6.5));
        helper.spawn(EntityTypes.VILLAGER, new Vec3(6.5, 2, 6.5));
        boolean[] grabbed = {false};
        helper.onEachTick(() -> grabbed[0] |= suit.socket().getBeamTarget() != null || suit.socket().isAiTriggerHeld());
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(!grabbed[0], "only monsters get grabbed");
            helper.succeed();
        });
    }

    /** A creeper must not come down at home: it's thrown up and away instead. */
    @SuitTest(timeoutTicks = 100)
    public static void emMindThrowsCreepersAwayFromHome(GameTestHelper helper) {
        PowerSuitEntity suit = emGuard(helper, new BlockPos(4, 2, 1));
        var creeper = helper.spawn(EntityTypes.CREEPER, new Vec3(4.5, 2, 5.5));
        double[] furthest = {0};
        helper.onEachTick(() -> {
            if (suit.socket().getUses() > 0) {
                furthest[0] = Math.max(furthest[0], creeper.getZ() - helper.absoluteVec(Vec3.ZERO).z);
            }
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(suit.socket().getUses() >= 1, "the creeper should be thrown");
            helper.assertTrue(furthest[0] > 7.0, "away from home (+z, to the far wall), got to z " + furthest[0]);
            helper.succeed();
        });
    }

    // =========================================================================================
    // Phase 8 step 4: the drill and chainsaw workers
    // =========================================================================================

    private static PowerSuitEntity worker(GameTestHelper helper, BlockPos at, ItemStack tool) {
        PowerSuitEntity suit = suitAt(helper, at);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 8));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, tool);
        suit.brain().setHome(helper.absolutePos(at));
        return suit;
    }

    /**
     * A stone mass (z 3..8, 4 high) in front of home: the mind clears the hall (home level, 3 high) and leaves the floor,
     * the ceiling, the blocks touching a lava pocket, and a column over a hole in the floor.
     */
    @SuitTest(timeoutTicks = 1500)
    public static void mindDrillsAnOpenHallAtHomeLevel(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 3), new BlockPos(8, 5, 8))) {
            helper.setBlock(p, Blocks.STONE);
        }
        helper.setBlock(new BlockPos(8, 3, 8), Blocks.LAVA);
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.AIR); // a hole in the floor under the column x=2, z=6
        PowerSuitEntity suit = worker(helper, new BlockPos(4, 2, 1), new ItemStack(ModItems.DRILL.get()));
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
                    + suit.position().subtract(helper.absoluteVec(Vec3.ZERO)) + " goals " + suit.brain().runningGoalNames());
        });
    }

    @SuitTest(timeoutTicks = 300)
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
        PowerSuitEntity suit = worker(helper, new BlockPos(3, 2, 1), new ItemStack(ModItems.CHAINSAW.get()));
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

    private static PowerSuitEntity rogue(GameTestHelper helper, BlockPos at, boolean chaingun) {
        PowerSuitEntity suit = suitAt(helper, at);
        RogueSpawner.makeRogue(suit, chaingun);
        return suit;
    }

    /** No fuel, no nuggets: a rogue runs anyway, and shoots any mob it sees (a cow here). */
    @SuitTest(timeoutTicks = 120)
    public static void rogueRunsOnNothingAndShootsAnyMob(GameTestHelper helper) {
        PowerSuitEntity suit = rogue(helper, new BlockPos(4, 2, 1), true);
        var cow = helper.spawn(EntityTypes.COW, new Vec3(4.5, 2, 6.5));
        helper.runAfterDelay(3, () -> helper.assertTrue(suit.isPowered() && suit.isAiDriven(), "a rogue needs no fuel"));
        helper.succeedWhen(() -> {
            helper.assertTrue(!cow.isAlive(), "the cow should be shot");
            helper.assertTrue(suit.power().getTotalEnergy() == 0 && suit.socket().getUses() > 0,
                    "fired without fuel or nuggets");
        });
    }

    @SuitTest(timeoutTicks = 120)
    public static void defeatedRogueDropsOnlyItsTool(GameTestHelper helper) {
        PowerSuitEntity suit = rogue(helper, SUIT_POS, false);
        suit.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000F);
        helper.runAfterDelay(70, () -> {
            helper.assertItemEntityPresent(ModItems.EM_PROJECTOR.get());
            helper.assertItemEntityNotPresent(Items.IRON_INGOT);
            helper.assertItemEntityNotPresent(ModItems.POWER_SUIT.get());
            helper.assertItemEntityNotPresent(ModItems.SUIT_HELMET.get()); // a rogue's helmet isn't loot either
            helper.succeed();
        });
    }

    /**
     * A real bolt, not a hand-called thunderHit: vanilla strikes everything within 3 blocks on every tick the bolt lives
     * (two or three ticks, plus up to three flashes), and lights fire blocks round it. A running suit next to it is
     * knocked out once and takes about one hit (vanilla's half-second hit immunity swallows the repeats), then restarts.
     */
    @SuitTest(timeoutTicks = 200)
    public static void aRealBoltNextToASuitCostsOneHit(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.BLAZE_ROD, 16));
        helper.runAfterDelay(5, () -> {
            var bolt = EntityTypes.LIGHTNING_BOLT.create(helper.getLevel(), EntitySpawnReason.COMMAND);
            // 2.6 blocks out: inside the bolt's 3-block damage box, but its fire (its block and random ones up to a block
            // round it) can't touch the suit's box (0.7 from its middle): at 2 a fire block sometimes did, and the
            // knocked-out (unpowered) suit burned for a second hit.
            bolt.snapTo(suit.position().add(2.6, 0, 0));
            helper.getLevel().addFreshEntity(bolt);
        });
        helper.runAfterDelay(15, () -> helper.assertTrue(suit.isStunned(), "the strike knocked it out"));
        helper.runAfterDelay(PowerSuitsConfig.LIGHTNING_STUN_TICKS.get() + 50, () -> {
            helper.assertTrue(suit.isAlive() && suit.getHealth() > suit.getMaxHealth() - 10, "about one hit, health " + suit.getHealth());
            helper.assertTrue(suit.isPowered(), "and it restarted");
            helper.succeed();
        });
    }

    /** Lightning reboots the mind friendly: nobody's, no home, one coal's worth of fuel; the first pilot claims it. */
    @SuitTest(timeoutTicks = 200)
    public static void lightningRebootsARogueFriendly(GameTestHelper helper) {
        PowerSuitEntity suit = rogue(helper, SUIT_POS, true);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.isRogue() && suit.isPowered(), "a running rogue");
            var bolt = EntityTypes.LIGHTNING_BOLT.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
            bolt.snapTo(suit.position());
            suit.thunderHit(helper.getLevel(), bolt);
            helper.assertTrue(!suit.isRogue() && suit.brain().getOwnerId() == null && suit.brain().getHome() == null, "rebooted friendly, nobody's");
            helper.assertTrue(suit.isStunned() && !suit.isPowered(), "knocked out first");
        });
        helper.runAfterDelay(PowerSuitsConfig.LIGHTNING_STUN_TICKS.get() + 40, () -> {
            helper.assertTrue(suit.isAiDriven() && suit.hasHelmet(), "back up as a friendly mind on its last fuel, helmet still on");
            suit.clearFire();
            Player pilot = boardedPilot(helper, suit);
            helper.assertTrue(pilot.getVehicle() == suit && pilot.getUUID().equals(suit.brain().getOwnerId()), "the first pilot claims it");
            helper.succeed();
        });
    }

    /** It leaves after its lifetime, but only unwatched. Own batch: other tests' mock players must not be around. */
    @SuppressWarnings("removal")
    @SuitTest(timeoutTicks = 100, batch = "rogue_lifetime")
    public static void rogueLeavesAfterItsTimeWhenUnwatched(GameTestHelper helper) {
        PowerSuitEntity suit = rogue(helper, SUIT_POS, true);
        suit.brain().setRogueAge(PowerSuitsConfig.ROGUE_LIFETIME.get());
        var watcher = mockServerPlayer(helper);
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        watcher.teleportTo(helper.getLevel(), near.x, near.y, near.z, Set.of(), 0F, 0F, true);
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
    @SuitTest(timeoutTicks = 80)
    public static void friendlyChaingunShootsRogues(GameTestHelper helper) {
        PowerSuitEntity friend = chaingunMind(helper, new BlockPos(4, 2, 1));
        var owner = ownerAt(helper, friend, new Vec3(1.5, 2, 1.5));
        PowerSuitEntity rogue = rogue(helper, new BlockPos(4, 2, 7), false);
        helper.runAfterDelay(60, () -> {
            dismiss(helper, owner);
            helper.assertTrue(friend.getTarget() == rogue || rogue.getHealth() < rogue.getMaxHealth(), "it should take on the rogue");
            helper.assertTrue(rogue.getHealth() < rogue.getMaxHealth(), "and hit it, health " + rogue.getHealth());
            helper.succeed();
        });
    }

    /** The /summon line in the README makes a chaingun rogue. */
    @SuitTest
    public static void readmeSummonMakesARogue(GameTestHelper helper) throws Exception {
        CompoundTag tag = TagParser.parseCompoundFully(
                "{id:\"power_suits:power_suit\",Mind:\"ROGUE\",Inventory:{Items:[{Slot:1b,id:\"power_suits:chaingun\",count:1}]}}");
        var entity = EntityType.loadEntityRecursive(tag, helper.getLevel(), new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> e);
        helper.assertTrue(entity instanceof PowerSuitEntity suit && suit.isRogue()
                && suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT).is(ModItems.CHAINGUN.get()), "a chaingun rogue");
        PowerSuitEntity rogue = (PowerSuitEntity) entity;
        helper.getLevel().addFreshEntity(rogue);
        rogue.snapTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        // Rogues always wear a helmet, even when the command gives none (its mind lives in it).
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(rogue.hasHelmet() && rogue.isPowered(), "the summoned rogue should put its helmet on and run");
            rogue.discard();
            helper.succeed();
        });
    }

    /**
     * A mock player who is "in the level" (other code sees it as a connected player). Its fake connection never did
     * the network handshake, and since 1.21.x NeoForge refuses to send mod packets on a connection that didn't agree to
     * them: GeckoLib's animation triggers (the EM projector's recoil, a flinch) crashed the test server. Tell the
     * connection it speaks GeckoLib's entity packets, as a real client with GeckoLib would.
     */
    @SuppressWarnings("removal") // vanilla marked it for removal with no replacement yet (26.3): adapt when it goes
    private static ServerPlayer mockServerPlayer(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var channels = ChannelAttributes.getOrCreateCommonChannels(
                player.connection.getConnection(), ConnectionProtocol.PLAY);
        for (String id : new String[] {"entity_anim_trigger", "stop_triggered_entity_anim", "stateless_entity_play_anim", "stateless_entity_stop_anim"}) {
            channels.add(Identifier.fromNamespaceAndPath("geckolib", id));
        }
        channels.add(Identifier.fromNamespaceAndPath("neoforge", "advanced_open_screen")); // a menu with extra data (the suit's)
        return player;
    }

    /** An entity's save data, as the world would write it (since 1.21.6 entities save through ValueOutput). */
    private static CompoundTag saveTag(GameTestHelper helper, Entity entity) {
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        entity.saveWithoutId(out);
        return out.buildResult();
    }

    /** Loads save data into an entity, as the world would. */
    private static void loadTag(GameTestHelper helper, Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), tag));
    }

    private static ItemStack packedSuit(Player player) {
        return player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(ModItems.POWER_SUIT.get())).findFirst().orElse(ItemStack.EMPTY);
    }

    private static InteractionResult useOnFloor(GameTestHelper helper, Player player, ItemStack stack, BlockPos floor) {
        BlockPos abs = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static List<PowerSuitEntity> suitsInTest(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(PowerSuitEntity.class, new AABB(helper.absolutePos(SUIT_POS)).inflate(5));
    }

    /**
     * The suit is crafted from parts (2026-10-19): helmet, torso, arm and leg from iron blocks, copper blocks (lapis
     * until 2026-10-23), redstone blocks and vanilla parts, no furnace; torso, arms and legs are assembled like a body (the helmet goes in the suit's slot). Crafts every step through the server's real recipe list (the JSON files), so
     * a typo in a recipe or an arm pattern that also matches the leg fails here.
     */
    @SuitTest
    public static void partsAssembleIntoTheSuit(GameTestHelper helper) {
        ItemStack i = new ItemStack(Items.IRON_BLOCK), c = new ItemStack(Items.COPPER_BLOCK.weathering().unaffected()), p = new ItemStack(Items.PISTON);
        ItemStack r = new ItemStack(Items.REDSTONE_BLOCK), e = ItemStack.EMPTY;
        checkCraft(helper, 3, 3, List.of(i, c, i, c, r, c, i, c, i), ModItems.SUIT_TORSO.get());
        // the visor's compass and clock come from a vanilla compass and clock (2026-10-27)
        checkCraft(helper, 3, 2, List.of(i, r, i, new ItemStack(Items.COMPASS), new ItemStack(Items.GLASS), new ItemStack(Items.CLOCK)),
                ModItems.SUIT_HELMET.get());
        checkCraft(helper, 3, 1, List.of(c, p, i), ModItems.SUIT_ARM.get());
        checkCraft(helper, 3, 1, List.of(i, p, c), ModItems.SUIT_ARM.get()); // mirrored: the other arm
        checkCraft(helper, 1, 3, List.of(c, p, i), ModItems.SUIT_LEG.get());
        ItemStack b = new ItemStack(Items.IRON_BLOCK), n = new ItemStack(Items.IRON_INGOT);
        checkCraft(helper, 3, 3, List.of(b, n, b, n, new ItemStack(Items.BLAST_FURNACE), n, b, n, b), ModItems.FIREBOX.get());
        // no lapis or furnaces in the suit any more (2026-10-23; the firebox keeps its blast furnace)
        ItemStack l = new ItemStack(Items.LAPIS_BLOCK);
        helper.assertTrue(craft(helper, 3, 3, List.of(i, l, i, l, new ItemStack(Items.FURNACE), l, i, l, i)).isEmpty(), "the old lapis torso is gone");
        ItemStack h = new ItemStack(ModItems.SUIT_HELMET.get()), t = new ItemStack(ModItems.SUIT_TORSO.get());
        ItemStack a = new ItemStack(ModItems.SUIT_ARM.get()), g = new ItemStack(ModItems.SUIT_LEG.get());
        checkCraft(helper, 3, 2, List.of(a, t, a, g, e, g), ModItems.POWER_SUIT.get()); // the helmet goes on afterwards
        helper.assertTrue(craft(helper, 3, 3, List.of(e, h, e, a, t, a, g, e, g)).isEmpty(), "the helmet isn't part of the assembly");
        // the old recipe (iron armour + pistons) is gone
        ItemStack ib = new ItemStack(Items.IRON_BLOCK);
        helper.assertTrue(craft(helper, 3, 3, List.of(ib, new ItemStack(Items.IRON_HELMET), ib, p, new ItemStack(Items.IRON_CHESTPLATE), p,
                new ItemStack(Items.IRON_BOOTS), e, new ItemStack(Items.IRON_BOOTS))).isEmpty(), "the old iron-armour recipe should be gone");
        helper.succeed();
    }

    // =========================================================================================
    // Paint (2026-10-20): the suit is bare steel; a dye paints its plates, a wet sponge washes them
    // =========================================================================================

    /** Right-click with a dye paints the suit and uses the dye; the same colour again does nothing; another dye paints
     *  over it; creative players keep their dye; a wet sponge washes it off and stays wet; nobody climbs in meanwhile. */
    @SuitTest
    public static void dyePaintsTheSuitAndAWetSpongeWashesIt(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        helper.assertTrue(suit.getPaint() == null, "a new suit is bare steel");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.red(), 3));
        helper.assertTrue(suit.interact(player, InteractionHand.MAIN_HAND, suit.position()).consumesAction(), "the suit takes the dye");
        helper.assertTrue(suit.getPaint() == DyeColor.RED && player.getMainHandItem().getCount() == 2,
                "red dye paints it red and is used up, got " + suit.getPaint() + " / " + player.getMainHandItem());
        suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "the same colour again keeps the dye");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.blue()));
        suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
        helper.assertTrue(suit.getPaint() == DyeColor.BLUE && player.getMainHandItem().isEmpty(), "blue paints over red");

        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true; // mock players don't get their game mode's abilities
        creative.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.yellow()));
        suit.interact(creative, InteractionHand.MAIN_HAND, suit.position());
        helper.assertTrue(suit.getPaint() == DyeColor.YELLOW && creative.getMainHandItem().getCount() == 1,
                "creative paints without using the dye");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WET_SPONGE));
        suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
        helper.assertTrue(suit.getPaint() == null && player.getMainHandItem().is(Items.WET_SPONGE), "the wet sponge washes it, and stays");
        helper.assertTrue(player.getVehicle() == null && creative.getVehicle() == null, "painting and washing never climb in");
        helper.succeed();
    }

    /** The paint survives saving and packing the suit up (the item shows it and puts it back on). */
    @SuitTest
    public static void paintSurvivesSavingAndPacking(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        suit.setPaint(DyeColor.LIME);
        PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, loaded, saveTag(helper, suit));
        helper.assertTrue(loaded.getPaint() == DyeColor.LIME, "saved paint, got " + loaded.getPaint());
        PowerSuitEntity bare = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, bare, saveTag(helper, spawnSuitAtCorner(helper)));
        helper.assertTrue(bare.getPaint() == null, "a bare suit loads bare");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        suit.packUp(player);
        ItemStack packed = packedSuit(player);
        helper.assertTrue(packed.get(ModDataComponents.PAINT.get()) == DyeColor.LIME, "the packed suit keeps its paint");
        player.getInventory().removeItem(packed);
        player.setItemInHand(InteractionHand.MAIN_HAND, packed);
        useOnFloor(helper, player, packed, new BlockPos(4, 1, 4));
        PowerSuitEntity deployed = suitsInTest(helper).stream().filter(s -> s != suit && s.getPaint() != null).findFirst().orElseThrow();
        helper.assertTrue(deployed.getPaint() == DyeColor.LIME, "and comes back painted");
        helper.succeed();
    }

    private static PowerSuitEntity spawnSuitAtCorner(GameTestHelper helper) {
        return suitAt(helper, new BlockPos(7, 2, 7));
    }

    // =========================================================================================
    // The power switch (user, 2026-10-27): off = unpowered with the fuel kept
    // =========================================================================================

    /** A running suit switched off goes down and burns nothing with its fuel in; it stays off through saving, packing and
     *  deploying (and an item from before the switch comes out on); switched on again it runs. */
    @SuitTest(timeoutTicks = 80)
    public static void powerSwitchParksASuitWithItsFuel(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 2));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.isPowered() && suit.power().isSwitchedOn(), "fuel and a helmet: it runs");
            suit.flipPowerSwitch();
            helper.assertTrue(!suit.power().isSwitchedOn() && !suit.isPowered(), "switched off: down at once");
        });
        int[] energy = new int[1];
        helper.runAfterDelay(5, () -> energy[0] = suit.power().getTotalEnergy());
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(!suit.isPowered() && suit.power().getTotalEnergy() == energy[0],
                    "off, nothing burns: " + energy[0] + " -> " + suit.power().getTotalEnergy());
            CompoundTag saved = saveTag(helper, suit);
            PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
            loadTag(helper, loaded, saved);
            helper.assertTrue(!loaded.power().isSwitchedOn() && !loaded.isPowered(), "saved switched off");
            helper.assertTrue(!suit.getLoadout().switchedOn(), "packed switched off");
            var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            helper.assertTrue(SuitLoadout.CODEC.parse(ops, new CompoundTag()).getOrThrow().switchedOn(), "an old item: on");
            PowerSuitEntity deployed = suitAt(helper, new BlockPos(6, 2, 6));
            deployed.applyLoadout(suit.getLoadout());
            helper.assertTrue(!deployed.power().isSwitchedOn() && !deployed.isPowered(), "deployed switched off");
            suit.flipPowerSwitch();
        });
        helper.runAfterDelay(48, () -> {
            helper.assertTrue(suit.power().isSwitchedOn() && suit.isPowered(), "switched on: it runs again");
            helper.succeed();
        });
    }

    // =========================================================================================
    // The helmet (2026-10-19): seals the suit and lets the machine mind run
    // =========================================================================================

    /** An empty suit without its helmet stays down whatever its fuel (no mind, no idle burn); right-click it with a
     *  helmet and it starts up; the helmet survives packing and saving. */
    @SuitTest(timeoutTicks = 80)
    public static void emptySuitNeedsItsHelmetToRun(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 3));
        suit.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, ItemStack.EMPTY);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!suit.hasHelmet() && !suit.isPowered() && !suit.isAiDriven(), "no helmet: powered down, no mind");
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 3 && suit.power().getBurnLeft() == 0,
                    "and it burns nothing");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 4.5)));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SUIT_HELMET.get()));
            suit.interact(player, InteractionHand.MAIN_HAND, suit.position());
            helper.assertTrue(suit.hasHelmet() && player.getMainHandItem().isEmpty(), "right-click with the helmet puts it on");
            helper.assertTrue(player.getVehicle() == null, "and doesn't climb in");
        });
        helper.runAfterDelay(20, () -> helper.assertTrue(suit.isPowered() && suit.power().getBurnLeft() > 0, "with its helmet on it runs"));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(suit.isAiDriven(), "and its mind drives it");
            SuitLoadout packed = suit.getLoadout();
            helper.assertTrue(packed.helmet().is(ModItems.SUIT_HELMET.get()), "a packed suit keeps its helmet");
            PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
            loadTag(helper, loaded, saveTag(helper, suit));
            helper.assertTrue(loaded.getInventory().getItem(PowerSuitEntity.HELMET_SLOT).is(ModItems.SUIT_HELMET.get()) && loaded.hasHelmet(),
                    "the helmet survives saving");
            helper.succeed();
        });
    }

    /** Without the helmet a pilot can still drive the suit, but it's open: harmful effects reach them, and they can eat. */
    @SuitTest(timeoutTicks = 30)
    public static void helmetlessSuitRunsUnsealed(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        suit.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, ItemStack.EMPTY);
        Player pilot = boardedPilot(helper, suit);
        pilot.getFoodData().setFoodLevel(10);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.isPowered() && !suit.isSealed(), "piloted it runs, unsealed");
            pilot.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            helper.assertTrue(pilot.hasEffect(MobEffects.POISON), "no seal: the poison reaches the pilot");
            pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
            helper.assertTrue(CommonHooks.onItemRightClick(pilot, InteractionHand.MAIN_HAND) == null,
                    "bread isn't refused");
            pilot.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(pilot.isUsingItem(), "no helmet: the pilot can eat");
            helper.succeed();
        });
    }

    private static ItemStack craft(GameTestHelper helper, int w, int h, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(w, h, grid);
        return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    private static void checkCraft(GameTestHelper helper, int w, int h, List<ItemStack> grid, Item expected) {
        ItemStack out = craft(helper, w, h, grid);
        helper.assertTrue(out.is(expected), "expected " + expected + " from " + grid + ", got " + out);
    }

    // =========================================================================================
    // Engines (Phase 9, design sec. 4b): firebox, tidal engine, star reactor
    // =========================================================================================

    /** A stone-walled pool on the pad, water from y=2 to y=6 inside x/z 2..6 (as in suitSinksAndDoesNotDrown); with
     *  {@code water} false only the walls (an empty pool, ready to be filled). */
    private static void pool(GameTestHelper helper, boolean water) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                for (int y = 2; y <= 6; y++) {
                    boolean wall = x == 1 || x == 7 || z == 1 || z == 7;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : water ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    private static PowerSuitEntity suitWithEngine(GameTestHelper helper, BlockPos pos, ItemStack engine, ItemStack fuel) {
        PowerSuitEntity suit = suitAt(helper, pos);
        suit.getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, engine);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, fuel);
        return suit;
    }

    /** A new suit has no engine (nothing burns until one goes in); taking the engine out kills the power and the
     *  half-burnt item goes with it. */
    @SuitTest(timeoutTicks = 60)
    public static void engineSlotPowersTheSuit(GameTestHelper helper) {
        PowerSuitEntity bare = helper.spawn(ModEntities.POWER_SUIT.get(), new BlockPos(1, 2, 7));
        bare.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
        bare.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 4));
        helper.assertTrue(bare.getInventory().getItem(PowerSuitEntity.ENGINE_SLOT).isEmpty(), "a new suit has no engine");
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 4));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.isPowered() && suit.power().getBurnLeftExact() > 0, "the firebox burns its coal");
            helper.assertTrue(!bare.isPowered() && bare.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 4,
                    "no engine: the coal just sits there");
            suit.getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, ItemStack.EMPTY);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!suit.isPowered() && suit.getEngine() == null, "no engine, no power");
            helper.assertTrue(suit.power().getBurnLeftExact() == 0, "the half-burnt coal went out with the engine, " + suit.power().getBurnLeftExact());
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 3, "the coal in the slot stays");
            suit.getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, new ItemStack(ModItems.FIREBOX.get()));
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(suit.isPowered(), "a firebox back in powers it again");
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 2, "lighting the next coal");
            helper.succeed();
        });
    }

    /** The menu: the engine slot takes engines only; the fuel slot takes any engine's fuel, like a furnace's. */
    @SuitTest
    public static void engineAndFuelSlotsInTheMenu(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 4.5)));
        PowerSuitMenu menu = new PowerSuitMenu(1, player.getInventory(), suit.getInventory(), suit);
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_ENGINE).getItem().is(ModItems.FIREBOX.get()), "menu slot 3 shows the engine");
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_ENGINE).mayPlace(new ItemStack(ModItems.TIDAL_ENGINE.get())), "engine slot takes a tidal engine");
        helper.assertTrue(!menu.getSlot(PowerSuitMenu.MENU_ENGINE).mayPlace(new ItemStack(Items.FURNACE)), "engine slot refuses a furnace");
        for (ItemStack fuel : List.of(new ItemStack(Items.COAL), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.NETHER_STAR),
                PotionContents.createItemStack(Items.POTION, Potions.WATER),
                new ItemStack(Items.BUCKET), new ItemStack(Items.GLASS_BOTTLE))) {
            helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_FUEL).mayPlace(fuel), "fuel slot should take " + fuel);
        }
        helper.assertTrue(!menu.getSlot(PowerSuitMenu.MENU_FUEL).mayPlace(new ItemStack(Items.HEART_OF_THE_SEA)), "the heart goes into the recipe, not the slot");
        menu.getSlot(PowerSuitMenu.MENU_ENGINE).set(ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(ModItems.STAR_REACTOR.get()));
        player.getInventory().setItem(10, new ItemStack(Items.NETHER_STAR));
        menu.quickMoveStack(player, PowerSuitMenu.INV_START + 0); // the player's inventory slot 9
        menu.quickMoveStack(player, PowerSuitMenu.INV_START + 1); // the player's inventory slot 10
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_ENGINE).getItem().is(ModItems.STAR_REACTOR.get()), "shift-click installs the engine");
        helper.assertTrue(menu.getSlot(PowerSuitMenu.MENU_FUEL).getItem().is(Items.NETHER_STAR), "shift-click puts the star in the fuel slot");
        helper.succeed();
    }

    /** Water over the backpack puts a firebox out: power and the half-burnt item are lost, it won't relight or pack up
     *  under water, and it lights its next item once the water is gone. */
    @SuitTest(timeoutTicks = 80)
    public static void fireboxFloodsUnderWater(GameTestHelper helper) {
        pool(helper, false);
        PowerSuitEntity suit = suitWithEngine(helper, SUIT_POS, new ItemStack(ModItems.FIREBOX.get()), new ItemStack(Items.COAL, 4));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(suit.isPowered() && !suit.power().isFlooded(), "a dry firebox runs");
            pool(helper, true);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(suit.power().isEngineUnderWater() && suit.power().isFlooded(), "5 blocks of water cover the backpack");
            helper.assertTrue(!suit.isPowered(), "a flooded firebox shuts the suit down");
            helper.assertTrue(suit.power().getBurnLeftExact() == 0, "the burning coal is lost, " + suit.power().getBurnLeftExact());
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 3, "the coal in the slot can't light under water");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(!suit.packUp(player) && !suit.isRemoved() && packedSuit(player).isEmpty(), "no pack-up while flooded");
            helper.assertTrue(!suit.power().hasEnergyFor(1), "no energy for tools either");
            pool(helper, false); // drain it
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!suit.power().isFlooded() && suit.isPowered(), "drained, the firebox lights again");
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).getCount() == 2, "with its next coal");
            helper.succeed();
        });
    }

    /** Wading is fine: water below the backpack (2 deep on the 2.95-high suit) doesn't flood it. */
    @SuitTest(timeoutTicks = 40)
    public static void fireboxWadesThroughShallowWater(GameTestHelper helper) {
        pool(helper, false);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = 2; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        PowerSuitEntity suit = suitWithEngine(helper, SUIT_POS, new ItemStack(ModItems.FIREBOX.get()), new ItemStack(Items.COAL, 4));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(suit.isInWater() && !suit.power().isEngineUnderWater(), "the suit stands in the water, its backpack above it");
            helper.assertTrue(suit.isPowered(), "a firebox above the water keeps running");
            helper.succeed();
        });
    }

    /** The tidal engine: free power while wet, a short grace on leaving the water, then water from the slot, then off. */
    @SuitTest(timeoutTicks = 160)
    public static void tidalEngineRunsWhileWet(GameTestHelper helper) {
        pool(helper, true);
        PowerSuitEntity suit = suitWithEngine(helper, SUIT_POS, new ItemStack(ModItems.TIDAL_ENGINE.get()), ItemStack.EMPTY);
        int grace = PowerSuitsConfig.TIDAL_DRY_GRACE.get();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.isPowered() && suit.power().hasFreePower(), "under water the tidal engine runs on nothing");
            helper.assertTrue(suit.power().getBurnLeftExact() == 0 && suit.power().hasEnergyFor(100_000), "and pays for anything");
            pool(helper, false);
        });
        helper.runAfterDelay(30 + grace / 2, () -> helper.assertTrue(suit.isPowered(), "a short grace out of the water"));
        helper.runAfterDelay(30 + grace + 5, () -> {
            helper.assertTrue(!suit.isPowered() && !suit.power().hasFreePower(), "dry and nothing to drink: off");
            suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.WATER_BUCKET));
        });
        helper.runAfterDelay(30 + grace + 8, () -> {
            helper.assertTrue(suit.isPowered(), "a water bucket runs it on land");
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).is(Items.BUCKET), "the empty bucket stays");
            float left = suit.power().getBurnLeftExact();
            helper.assertTrue(left > PowerSuitsConfig.WATER_BUCKET_ENERGY.get() - 10 && left < PowerSuitsConfig.WATER_BUCKET_ENERGY.get(),
                    "it drinks the bucket's energy, " + left);
            helper.succeed();
        });
    }

    /** A water bottle is a smaller drink, and leaves its glass. A firebox can't use water, and a tidal engine no coal. */
    @SuitTest(timeoutTicks = 40)
    public static void eachEngineBurnsOnlyItsOwnFuel(GameTestHelper helper) {
        ItemStack bottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        PowerSuitEntity tidal = suitWithEngine(helper, new BlockPos(2, 2, 4), new ItemStack(ModItems.TIDAL_ENGINE.get()), bottle);
        PowerSuitEntity fire = suitWithEngine(helper, new BlockPos(6, 2, 4), new ItemStack(ModItems.FIREBOX.get()), new ItemStack(Items.WATER_BUCKET));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(tidal.isPowered() && tidal.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).is(Items.GLASS_BOTTLE),
                    "the tidal engine drinks the bottle and leaves the glass");
            helper.assertTrue(tidal.power().getBurnTotal() == PowerSuitsConfig.WATER_BOTTLE_ENERGY.get(), "a bottle's worth, " + tidal.power().getBurnTotal());
            helper.assertTrue(!fire.isPowered() && fire.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).is(Items.WATER_BUCKET),
                    "a firebox can't burn water: it just sits in the slot");
            tidal.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL));
            tidal.power().setBurn(0, 0);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!tidal.isPowered() && tidal.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).is(Items.COAL),
                    "a dry tidal engine can't burn coal");
            helper.succeed();
        });
    }

    /** The star reactor: a Nether Star in the fuel slot runs everything for free, forever (it's never used up). */
    @SuitTest(timeoutTicks = 60)
    public static void starReactorRunsForever(GameTestHelper helper) {
        PowerSuitEntity suit = suitWithEngine(helper, SUIT_POS, new ItemStack(ModItems.STAR_REACTOR.get()), new ItemStack(Items.NETHER_STAR));
        Player pilot = boardedPilot(helper, suit);
        pilot.zza = 1;
        pilot.setSprinting(true);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(suit.isPowered() && suit.power().hasFreePower(), "the reactor runs");
            helper.assertTrue(suit.power().getBurnLeftExact() == 0 && suit.power().tryUseEnergy(100_000), "it burns nothing and pays for any tool");
            helper.assertTrue(suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).is(Items.NETHER_STAR), "the star stays");
            helper.assertTrue(!suit.power().isLowOnFuel(), "it never runs low");
            suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 4));
        });
        helper.runAfterDelay(33, () -> {
            helper.assertTrue(!suit.isPowered(), "without its star the reactor is dead (it doesn't burn coal)");
            helper.succeed();
        });
    }

    /** The HUD's fuel bar (SuitPower.fuelGauge): what's on board against a full fuel slot of it; the last item alone
     *  against a stack of its kind (or one bucket); a free engine reads full. Read without a pilot: no power needed. */
    @SuitTest(timeoutTicks = 40)
    public static void fuelGaugeReadsAgainstAFullSlot(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, SUIT_POS);
        suit.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, ItemStack.EMPTY); // no mind: the suit stays parked
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 32));
        int coal = fireboxEnergy(helper, Items.COAL);
        helper.runAfterDelay(2, () -> {
            float half = suit.power().fuelGauge();
            helper.assertTrue(Math.abs(half - 0.5F) < 0.02F, "32 coal of a stack of 64 read half, got " + half);
            suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, ItemStack.EMPTY);
            suit.power().setBurn(coal / 2F, coal);
        });
        helper.runAfterDelay(4, () -> {
            float last = suit.power().fuelGauge();
            helper.assertTrue(Math.abs(last - 0.5F / 64) < 0.002F, "half the last coal is half a 64th, got " + last);
            int lava = fireboxEnergy(helper, Items.LAVA_BUCKET);
            suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BUCKET)); // what a burnt lava bucket leaves
            suit.power().setBurn(lava / 4F, lava);
        });
        helper.runAfterDelay(6, () -> {
            float bucket = suit.power().fuelGauge();
            helper.assertTrue(Math.abs(bucket - 0.25F) < 0.01F, "a quarter of a lava bucket (one per slot) reads a quarter, got " + bucket);
            suit.getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, new ItemStack(ModItems.STAR_REACTOR.get()));
            suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.NETHER_STAR));
            suit.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(suit.power().fuelGauge() == 1F, "a star reactor reads full, got " + suit.power().fuelGauge());
            helper.succeed();
        });
    }

    /** Engines survive saving and packing; suits and packed items from before the engines get their firebox. */
    @SuitTest
    public static void enginesSurviveSavesAndPacking(GameTestHelper helper) {
        PowerSuitEntity suit = suitWithEngine(helper, SUIT_POS, new ItemStack(ModItems.TIDAL_ENGINE.get()), new ItemStack(Items.WATER_BUCKET));
        PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, loaded, saveTag(helper, suit));
        helper.assertTrue(loaded.getEngine() == SuitEngine.TIDAL, "the engine survives saving");

        suit.getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, ItemStack.EMPTY);
        CompoundTag taken = saveTag(helper, suit);
        PowerSuitEntity emptied = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, emptied, taken);
        helper.assertTrue(emptied.getEngine() == null, "an engine taken out stays out after loading");
        taken.remove("HasEngineSlot"); // as saved before the engines
        PowerSuitEntity old = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, old, taken);
        helper.assertTrue(old.getEngine() == SuitEngine.FIREBOX, "a suit from before the engines has its firebox");

        suit.getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, new ItemStack(ModItems.STAR_REACTOR.get()));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(suit.packUp(player), "a dry suit packs up");
        SuitLoadout loadout = packedSuit(player).get(ModDataComponents.LOADOUT.get());
        helper.assertTrue(loadout != null && loadout.engine().isPresent() && loadout.engine().get().is(ModItems.STAR_REACTOR.get()),
                "the packed item carries its engine, got " + loadout);
        // A loadout's codec must keep "taken out" (an empty stack) apart from "from before the engines" (no field).
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        SuitLoadout noEngine = new SuitLoadout(ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, 0, ItemStack.EMPTY, Optional.of(ItemStack.EMPTY), ItemStack.EMPTY, true);
        SuitLoadout back = SuitLoadout.CODEC.parse(ops, SuitLoadout.CODEC.encodeStart(ops, noEngine).getOrThrow()).getOrThrow();
        helper.assertTrue(back.engine().isPresent() && back.engine().get().isEmpty(), "an empty engine slot survives the codec, got " + back);
        SuitLoadout before = SuitLoadout.CODEC.parse(ops, SuitLoadout.CODEC.encodeStart(ops, SuitLoadout.EMPTY).getOrThrow()).getOrThrow();
        helper.assertTrue(before.engine().isEmpty(), "no field stays no field");
        PowerSuitEntity deployed = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        deployed.applyLoadout(SuitLoadout.EMPTY);
        helper.assertTrue(deployed.getEngine() == SuitEngine.FIREBOX, "a suit item from before the engines deploys with a firebox");
        deployed.applyLoadout(noEngine);
        helper.assertTrue(deployed.getEngine() == null, "one packed without an engine deploys without one");
        helper.succeed();
    }

    /** Rain counts as wet for the tidal engine; a firebox doesn't mind rain. Own batch: it sets the weather. */
    @SuitTest(timeoutTicks = 200, batch = "rain")
    public static void tidalEngineRunsInTheRain(GameTestHelper helper) {
        // Tests are placed at random spots of a world with real biomes: it doesn't rain in a desert (or snows in the
        // cold). Make the pad plains, as /fillbiome would.
        var plains = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.PLAINS);
        FillBiomeCommand.fill(helper.getLevel(), helper.absolutePos(new BlockPos(0, 0, 0)),
                helper.absolutePos(new BlockPos(8, 10, 8)), plains);
        PowerSuitEntity tidal = suitWithEngine(helper, new BlockPos(2, 2, 4), new ItemStack(ModItems.TIDAL_ENGINE.get()), ItemStack.EMPTY);
        PowerSuitEntity fire = suitWithEngine(helper, new BlockPos(6, 2, 4), new ItemStack(ModItems.FIREBOX.get()), new ItemStack(Items.COAL, 2));
        // The rain fades in over a few seconds (the level's rain strength must pass 0.2 before it counts).
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().isRaining(), "waiting for rain");
            BlockPos top = BlockPos.containing(tidal.getX(), tidal.getBoundingBox().maxY, tidal.getZ());
            helper.assertTrue(tidal.isPowered() && tidal.power().hasFreePower(), "the tidal engine runs in the rain (rain level "
                    + helper.getLevel().getRainLevel(1F) + ", sky " + helper.getLevel().canSeeSky(top) + ", raining at its top "
                    + helper.getLevel().isRainingAt(top) + ", biome " + helper.getLevel().getBiome(top).getRegisteredName() + ")");
            helper.assertTrue(fire.isPowered(), "a firebox runs in the rain too");
        });
    }

    // ---- helmet styles (design sec. 4c) -----------------------------------------------------------------------------

    private static ItemStack styled(HelmetStyle style) {
        ItemStack helmet = new ItemStack(ModItems.SUIT_HELMET.get());
        helmet.set(ModDataComponents.HELMET_STYLE.get(), style);
        return helmet;
    }

    /** What the smithing table makes of (template, base, addition), or EMPTY when no recipe matches. */
    private static ItemStack smith(GameTestHelper helper, ItemStack template, ItemStack base, ItemStack addition) {
        var input = new SmithingRecipeInput(template, base, addition);
        return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.SMITHING, input, helper.getLevel())
                .map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** Each template + a helmet + its style object gives the styled helmet (keeping its name); iron takes a style off. */
    @SuitTest
    public static void helmetStylesComeFromTheSmithingTable(GameTestHelper helper) {
        var objects = Map.of(
                HelmetStyle.PROTECTOR, new ItemStack(Items.TURTLE_HELMET),
                HelmetStyle.SPARTAN, new ItemStack(Items.TOTEM_OF_UNDYING),
                HelmetStyle.NOBLE, new ItemStack(Items.PHANTOM_MEMBRANE),
                HelmetStyle.ROBOT, new ItemStack(Items.MUSIC_DISC_CAT),
                HelmetStyle.HUNTER, new ItemStack(Items.SLIME_BALL));
        var templates = Map.of(
                HelmetStyle.PROTECTOR, ModItems.PROTECTOR_TEMPLATE.get(),
                HelmetStyle.SPARTAN, ModItems.SPARTAN_TEMPLATE.get(),
                HelmetStyle.NOBLE, ModItems.NOBLE_TEMPLATE.get(),
                HelmetStyle.ROBOT, ModItems.ROBOT_TEMPLATE.get(),
                HelmetStyle.HUNTER, ModItems.HUNTER_TEMPLATE.get());
        ItemStack named = new ItemStack(ModItems.SUIT_HELMET.get());
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Bucket"));
        for (var style : HelmetStyle.values()) {
            ItemStack out = smith(helper, new ItemStack(templates.get(style)), named, objects.get(style));
            helper.assertTrue(SuitHelmetItem.style(out) == style, style + ": got " + out);
            helper.assertTrue(out.has(DataComponents.CUSTOM_NAME), style + ": the helmet keeps its name");
            // and a styled helmet can be restyled
            ItemStack again = smith(helper, new ItemStack(templates.get(style)), styled(HelmetStyle.NOBLE), objects.get(style));
            helper.assertTrue(SuitHelmetItem.style(again) == style, style + ": restyling, got " + again);
            // the template copies like vanilla's trims: 7 diamonds round the template and the style object
            ItemStack d = new ItemStack(Items.DIAMOND);
            var grid = CraftingInput.of(3, 3, List.of(
                    d, new ItemStack(templates.get(style)), d, d, objects.get(style), d, d, d, d));
            ItemStack copies = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
                    .map(r -> r.value().assemble(grid)).orElse(ItemStack.EMPTY);
            helper.assertTrue(copies.is(templates.get(style)) && copies.getCount() == 2, style + ": copying the template, got " + copies);
        }
        helper.assertTrue(named.getHoverName().getString().equals("Bucket"), "an anvil name still wins over the style's");
        ItemStack spartan = styled(HelmetStyle.SPARTAN);
        helper.assertTrue(spartan.getHoverName().getContents() instanceof TranslatableContents t
                && t.getKey().equals("item.power_suits.suit_helmet.spartan"), "a styled helmet is named after its style, got " + spartan.getHoverName());
        helper.assertTrue(smith(helper, new ItemStack(ModItems.SPARTAN_TEMPLATE.get()), new ItemStack(ModItems.SUIT_HELMET.get()),
                new ItemStack(Items.TURTLE_HELMET)).isEmpty(), "a template only takes its own style object");
        helper.assertTrue(smith(helper, new ItemStack(ModItems.SPARTAN_TEMPLATE.get()), new ItemStack(Items.IRON_HELMET),
                new ItemStack(Items.TOTEM_OF_UNDYING)).isEmpty(), "templates are for the suit's helmet only");
        ItemStack plain = smith(helper, ItemStack.EMPTY, spartan, new ItemStack(Items.IRON_BLOCK));
        helper.assertTrue(plain.is(ModItems.SUIT_HELMET.get()) && SuitHelmetItem.style(plain) == null,
                "helmet + iron block (no template) gives the standard helmet back, got " + plain);
        helper.succeed();
    }

    /** The suit shows the worn helmet's style (synced), and keeps it through saving and packing up. */
    @SuitTest(timeoutTicks = 40)
    public static void helmetStyleShowsOnTheSuitAndSurvivesPacking(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, SUIT_POS);
        helper.assertTrue(suit.hasHelmet() && suit.getHelmetStyle() == null, "the standard helmet has no style");
        suit.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, styled(HelmetStyle.HUNTER));
        helper.assertTrue(suit.getHelmetStyle() == HelmetStyle.HUNTER, "a styled helmet shows its style");
        PowerSuitEntity loaded = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        loadTag(helper, loaded, saveTag(helper, suit));
        helper.assertTrue(loaded.getHelmetStyle() == HelmetStyle.HUNTER, "the style survives saving");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(suit.packUp(player), "the suit packs up");
        SuitLoadout loadout = packedSuit(player).get(ModDataComponents.LOADOUT.get());
        helper.assertTrue(loadout != null && SuitHelmetItem.style(loadout.helmet()) == HelmetStyle.HUNTER,
                "the packed suit keeps the styled helmet, got " + loadout);
        PowerSuitEntity deployed = ModEntities.POWER_SUIT.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        deployed.applyLoadout(loadout);
        helper.assertTrue(deployed.getHelmetStyle() == HelmetStyle.HUNTER, "and deploys with it");
        helper.succeed();
    }

    /** The templates turn up in the chests of their structures (loot modifiers), about one chest in ten. */
    @SuitTest
    public static void helmetTemplatesHideInStructureChests(GameTestHelper helper) {
        var chests = Map.of(
                BuiltInLootTables.UNDERWATER_RUIN_SMALL, ModItems.PROTECTOR_TEMPLATE.get(),
                BuiltInLootTables.SHIPWRECK_TREASURE, ModItems.PROTECTOR_TEMPLATE.get(),
                BuiltInLootTables.TRIAL_CHAMBERS_REWARD, ModItems.SPARTAN_TEMPLATE.get(),
                BuiltInLootTables.END_CITY_TREASURE, ModItems.NOBLE_TEMPLATE.get(),
                BuiltInLootTables.ANCIENT_CITY, ModItems.ROBOT_TEMPLATE.get(),
                BuiltInLootTables.DESERT_PYRAMID, ModItems.HUNTER_TEMPLATE.get(),
                BuiltInLootTables.JUNGLE_TEMPLE, ModItems.HUNTER_TEMPLATE.get());
        var params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(LootContextParamSets.CHEST);
        int rolls = 400;
        for (var chest : chests.entrySet()) {
            var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(chest.getKey());
            int found = 0, others = 0;
            for (int i = 0; i < rolls; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.is(chest.getValue())) {
                        found++;
                    } else if (stack.getItem() instanceof HelmetTemplateItem) {
                        others++;
                    }
                }
            }
            // 1 in 10 (1 in 8 in trial vaults): 40-50 expected in 400; 12..100 leaves no room for a fluke
            helper.assertTrue(found >= 12 && found <= 100, chest.getKey().identifier() + ": " + found + " " + chest.getValue() + " in " + rolls + " chests");
            helper.assertTrue(others == 0, chest.getKey().identifier() + " holds another style's template");
        }
        helper.succeed();
    }

    // =========================================================================================
    // Flamethrower (Phase 11, 2026-10-25)
    // =========================================================================================

    /** A suit at (4, 2, 1) facing +Z with the flamethrower, fuel and a pilot looking at {@code target} (relative). */
    private static Player flamePilot(GameTestHelper helper, PowerSuitEntity[] out, Vec3 target, ItemStack fuel) {
        return flamePilot(helper, out, new BlockPos(4, 2, 1), target, fuel);
    }

    private static Player flamePilot(GameTestHelper helper, PowerSuitEntity[] out, BlockPos at, Vec3 target, ItemStack fuel) {
        PowerSuitEntity suit = suitAt(helper, at);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, fuel);
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.FLAMETHROWER.get()));
        suit.setYRot(0);
        suit.setYBodyRot(0);
        Player pilot = boardedPilot(helper, suit);
        suit.positionRider(pilot);
        aim(helper, pilot, target);
        out[0] = suit;
        return pilot;
    }

    private static void aim(GameTestHelper helper, Player pilot, Vec3 target) {
        lookAt(pilot, helper.absoluteVec(target));
    }

    /** Held, it throws a gob of flame every tick (fuel per tick), and the stream reaches a pig 4 blocks out: hurt with
     *  flamethrower damage (credited to the pilot) and set alight. */
    @SuitTest(timeoutTicks = 80)
    public static void flamethrowerSetsAMobAlight(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(4, 2, 5));
        pig.setNoAi(true);
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = flamePilot(helper, suit, new Vec3(4.5, 2.5, 5.5), new ItemStack(Items.BLAZE_ROD, 2));
        int hold = 20;
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(2 + hold, () -> suit[0].socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(2 + hold + 2, () -> {
            helper.assertTrue(near(suit[0].socket().getUses(), hold), "a gob a tick: " + suit[0].socket().getUses() + " in " + hold + " ticks");
            int used = 2 * fireboxEnergy(helper, Items.BLAZE_ROD) - suit[0].power().getTotalEnergy();
            int expected = hold * PowerSuitsConfig.FLAMETHROWER_ENERGY.get();
            helper.assertTrue(used >= expected * 0.85 && used < expected * 1.15 + 15, "fuel per tick of flame: used " + used + ", expected ~" + expected);
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig should be burnt, health " + pig.getHealth());
            helper.assertTrue(pig.isOnFire(), "and burning");
            var src = pig.getLastDamageSource();
            helper.assertTrue(src != null && (src.is(FlameJet.FLAMETHROWER) || src.is(DamageTypeTags.IS_FIRE)),
                    "fire damage, got " + src);
            helper.assertTrue(suit[0].getHealth() == suit[0].getMaxHealth(), "the stream must not burn its own suit");
            helper.assertTrue(!pilot.isOnFire(), "nor its pilot");
            helper.succeed();
        });
    }

    /** Fire-proof mobs (a blaze) shrug the flame off. */
    @SuitTest(timeoutTicks = 60)
    public static void flamethrowerSparesFireproofMobs(GameTestHelper helper) {
        var blaze = helper.spawn(EntityTypes.BLAZE, new BlockPos(4, 2, 5));
        blaze.setNoAi(true);
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = flamePilot(helper, suit, new Vec3(4.5, 3.0, 5.5), new ItemStack(Items.BLAZE_ROD, 2));
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(30, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(suit[0].socket().getUses() > 20, "it sprayed");
            helper.assertTrue(blaze.getHealth() == blaze.getMaxHealth(), "a blaze takes no harm, health " + blaze.getHealth());
            helper.succeed();
        });
    }

    /** Under water there's no flame: no gobs, bubbles only. (A tidal engine runs there.) */
    @SuitTest(timeoutTicks = 60)
    public static void flamethrowerIsDeadUnderWater(GameTestHelper helper) {
        pool(helper, true);
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = flamePilot(helper, suit, new BlockPos(4, 2, 3), new Vec3(4.5, 2.5, 6.0), ItemStack.EMPTY);
        suit[0].getInventory().setItem(PowerSuitEntity.ENGINE_SLOT, new ItemStack(ModItems.TIDAL_ENGINE.get()));
        helper.runAfterDelay(10, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(30, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(suit[0].isPowered(), "the tidal engine runs under water");
            helper.assertTrue(suit[0].socket().getUses() == 0, "no flame under water, got " + suit[0].socket().getUses());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(FlameJet.class,
                    new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).isEmpty(), "and no gobs");
            helper.succeed();
        });
    }

    /** Where the stream lands on wood, the wood catches fire. */
    @SuitTest(timeoutTicks = 80)
    public static void flamethrowerSetsBlocksOnFire(GameTestHelper helper) {
        for (int x = 3; x <= 5; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 6), Blocks.OAK_PLANKS);
            }
        }
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = flamePilot(helper, suit, new Vec3(4.5, 3.5, 6.0), new ItemStack(Items.BLAZE_ROD, 2));
        boolean[] fire = {false};
        helper.onEachTick(() -> {
            for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(2, 2, 3)), helper.absolutePos(new BlockPos(6, 6, 7)))) {
                fire[0] |= helper.getLevel().getBlockState(p).is(BlockTags.FIRE);
            }
        });
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(60, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            if (PowerSuitsConfig.FLAMETHROWER_IGNITES_BLOCKS.get()) {
                helper.assertTrue(fire[0], "the planks should catch fire");
            }
            helper.succeed();
        });
    }

    /** Ice in the stream melts into water; an unlit campfire lights. */
    @SuitTest(timeoutTicks = 80)
    public static void flamethrowerMeltsIceAndLightsCampfires(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 2, 5), Blocks.ICE);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = flamePilot(helper, suit, new Vec3(4.5, 2.9, 5.2), new ItemStack(Items.BLAZE_ROD, 2));
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!helper.getBlockState(new BlockPos(4, 2, 5)).is(Blocks.ICE), "the ice should have melted");
            aim(helper, pilot, new Vec3(5.5, 2.3, 4.5));
        });
        helper.runAfterDelay(50, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 4)).getValue(CampfireBlock.LIT),
                    "the campfire should be lit");
            helper.succeed();
        });
    }

    /** Raw beef on the ground cooks in the stream (the whole stack), and doesn't burn up. */
    @SuitTest(timeoutTicks = 100)
    public static void flamethrowerCooksDroppedFood(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.1, 4.5));
        var beef = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.BEEF, 3));
        beef.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(beef);
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = flamePilot(helper, suit, new Vec3(4.5, 2.2, 4.5), new ItemStack(Items.BLAZE_ROD, 2));
        helper.runAfterDelay(5, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(70, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(beef.isAlive(), "the food must not burn up");
            helper.assertTrue(beef.getItem().is(Items.COOKED_BEEF) && beef.getItem().getCount() == 3,
                    "3 raw beef should be 3 steaks, got " + beef.getItem());
            helper.succeed();
        });
    }

    /** The mind learns a tool's goals when it goes into the socket and forgets them when it comes out
     *  (PowerTool.mindGoals, SuitBrain.updateToolGoals). */
    @SuitTest(timeoutTicks = 40)
    public static void toolGoalsComeAndGoWithTheTool(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.DRILL.get()));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(suit.brain().toolGoals().size() == 1 && suit.brain().toolGoals().get(0) instanceof DrillWorker,
                    "the drill brings its worker, got " + suit.brain().toolGoals());
            suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(suit.brain().toolGoals().size() == 1 && suit.brain().toolGoals().get(0) instanceof GunAttack,
                    "the chaingun's attack replaced it, got " + suit.brain().toolGoals());
            helper.assertTrue(suit.goals().getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof DrillWorker),
                    "and the drill's worker is gone from the mind");
            suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, ItemStack.EMPTY);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(suit.brain().toolGoals().isEmpty(), "no tool, no tool goals");
            helper.assertTrue(suit.goals().getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof GunAttack),
                    "the attack is gone too");
            helper.succeed();
        });
    }

    /** A slot stack changed in place without telling the container (shrink, no setChanged) still reaches the clients'
     *  copy within a tick (PowerSuitEntity.checkSlotCopies). */
    @SuitTest(timeoutTicks = 20)
    public static void slotCopiesCatchUpWithInPlaceChanges(GameTestHelper helper) {
        PowerSuitEntity suit = spawnSuit(helper);
        // glass bottles: they may sit in the fuel slot (what burnt fuel leaves) but never burn, so only the shrink counts
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.GLASS_BOTTLE, 8));
        suit.getInventory().getItem(PowerSuitEntity.FUEL_SLOT).shrink(3);
        helper.assertTrue(suit.getFuelStack().getCount() == 8, "the copy is stale right after the in-place change");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(suit.getFuelStack().getCount() == 5, "the copy caught up, got " + suit.getFuelStack());
            helper.succeed();
        });
    }

    /** The machine mind fights with it like with the chaingun, from close up: a friendly suit burns a husk near its owner,
     *  and never sets blocks on fire (it guards you, not your house). */
    @SuitTest(timeoutTicks = 160)
    public static void flamethrowerMindBurnsMonstersNearItsOwner(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(4, 2, 1));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.FLAMETHROWER.get()));
        var owner = ownerAt(helper, suit, new Vec3(1.5, 2, 1.5));
        var husk = statueHusk(helper, new Vec3(4.5, 2, 7.5));
        boolean[] fire = {false};
        helper.onEachTick(() -> {
            for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(8, 4, 8)))) {
                fire[0] |= helper.getLevel().getBlockState(p).is(BlockTags.FIRE);
            }
        });
        helper.runAfterDelay(140, () -> {
            dismiss(helper, owner);
            helper.assertTrue(suit.brain().followsOwner(), "a flamethrower suit takes the chaingun's job");
            helper.assertTrue(suit.socket().getUses() > 0, "it sprayed");
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth() || !husk.isAlive(), "the husk should be burnt, health " + husk.getHealth());
            helper.assertTrue(!fire[0], "a friendly mind sets no blocks on fire");
            helper.succeed();
        });
    }

    // =========================================================================================
    // TNT launcher (Phase 11, 2026-10-25; its TNT from the pilot's inventory since 2026-10-27)
    // =========================================================================================

    /** A suit at (4, 2, 1) facing +Z with the TNT launcher ({@code drum} rounds loaded), fuel, and a survival pilot
     *  carrying {@code tnt} blocks of TNT, looking at {@code target} (relative). */
    private static Player tntPilot(GameTestHelper helper, PowerSuitEntity[] out, Vec3 target, int drum, int tnt) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(4, 2, 1));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.COAL, 4));
        ItemStack launcher = new ItemStack(ModItems.TNT_LAUNCHER.get());
        if (drum > 0) {
            launcher.set(ModDataComponents.LOADED_ROUNDS.get(), drum);
        }
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, launcher);
        suit.setYRot(0);
        suit.setYBodyRot(0);
        Player pilot = boardedPilot(helper, suit);
        if (tnt > 0) {
            pilot.getInventory().setItem(9, new ItemStack(Items.TNT, tnt));
        }
        suit.positionRider(pilot);
        aim(helper, pilot, target);
        out[0] = suit;
        return pilot;
    }

    /** Rounds over this test's own structure (+2 blocks): copies of the tests run side by side ~14 blocks apart, and
     *  the old 12-block box round the centre reached a neighbour's round still in the air (2026-10-27, flaky x5). */
    private static List<TntShell> shellsInTest(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(TntShell.class, helper.getBounds().inflate(2));
    }

    /** The TNT and iron ingots the suit's pilot carries (0 without one). */
    private static int ammoLeft(PowerSuitEntity suit) {
        return suit.getControllingPassenger() instanceof Player pilot
                ? pilot.getInventory().countItem(Items.TNT) + pilot.getInventory().countItem(Items.IRON_INGOT) : 0;
    }

    private static int drumOf(PowerSuitEntity suit) {
        return TntLauncherItem.loaded(suit.getInventory().getItem(PowerSuitEntity.SOCKET_SLOT));
    }

    /** With an empty drum and no TNT the trigger only clicks: no round, nothing used. */
    @SuitTest(timeoutTicks = 40)
    public static void tntLauncherClicksWithoutTnt(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 1.5, 6.5), 0, 0);
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(8, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(suit[0].socket().getUses() == 0, "no TNT, no shot: " + suit[0].socket().getUses());
            helper.assertTrue(shellsInTest(helper).isEmpty(), "no round in the air");
            helper.succeed();
        });
    }

    /** A tap fires one round from the drum; it flies to the floor 5 blocks ahead and blows a hole in it (credited to
     *  the pilot), and the suit that fired it isn't the thing it hits. */
    @SuitTest(timeoutTicks = 60, batch = "tnt")
    public static void tntLauncherFiresARoundThatExplodesOnHit(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 1.9, 6.5), 1, 3);
        BlockPos floor = new BlockPos(4, 1, 6);
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(3, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(suit[0].socket().getUses() == 1, "a tap fires one round, got " + suit[0].socket().getUses());
            helper.assertTrue(drumOf(suit[0]) == 0, "the drum's round, left " + drumOf(suit[0]));
            helper.assertTrue(TntLauncherItem.loaded(suit[0].getSocketStack()) == 0, "the synced copy (HUD) follows");
            helper.assertTrue(ammoLeft(suit[0]) == 3, "the pilot's TNT is only touched by the reload, left " + ammoLeft(suit[0]));
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(shellsInTest(helper).isEmpty(), "the round has gone off");
            if (PowerSuitsConfig.TNT_LAUNCHER_BREAKS_BLOCKS.get()) {
                helper.assertTrue(helper.getBlockState(floor).isAir(), "it blew a hole in the floor where it hit, found " + helper.getBlockState(floor));
            }
            helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth(), "the suit shields its pilot");
            helper.succeed();
        });
    }

    /** The drum (2026-10-27): held, it fires its 4 rounds one shot interval apart, then only clicks; let go, it loads
     *  the chambers back one per reload_ticks with the pilot's TNT, starting reload_delay_ticks after the last shot. */
    @SuitTest(timeoutTicks = 200, batch = "tnt")
    public static void tntLauncherEmptiesItsDrumThenReloadsChamberByChamber(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 1.9, 7.5), TntLauncherItem.DRUM, 10);
        int interval = PowerSuitsConfig.TNT_LAUNCHER_SHOT_INTERVAL.get();
        int reload = PowerSuitsConfig.TNT_LAUNCHER_RELOAD.get(), delay = PowerSuitsConfig.TNT_LAUNCHER_RELOAD_DELAY.get();
        int lastShot = 2 + 3 * interval;
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(lastShot - interval + 1, () -> helper.assertTrue(suit[0].socket().getUses() == 3,
                "one shot an interval apart: 3 by now, got " + suit[0].socket().getUses()));
        helper.runAfterDelay(lastShot + interval + 2, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(suit[0].socket().getUses() == 4, "the whole drum, then dry clicks: 4 rounds, got " + suit[0].socket().getUses());
            helper.assertTrue(drumOf(suit[0]) == 0 && ammoLeft(suit[0]) == 10, "an empty drum, the TNT untouched while firing: drum "
                    + drumOf(suit[0]) + ", ammo " + ammoLeft(suit[0]));
        });
        helper.runAfterDelay(lastShot + delay + 2 * reload + reload / 2, () -> {
            helper.assertTrue(drumOf(suit[0]) == 2 && ammoLeft(suit[0]) == 8, "two chambers loaded from the pilot's TNT by now: drum "
                    + drumOf(suit[0]) + ", ammo " + ammoLeft(suit[0]));
        });
        helper.runAfterDelay(lastShot + delay + 4 * reload + reload / 2, () -> {
            helper.assertTrue(drumOf(suit[0]) == TntLauncherItem.DRUM && ammoLeft(suit[0]) == 6, "full again and it stops: drum "
                    + drumOf(suit[0]) + ", ammo " + ammoLeft(suit[0]));
            helper.succeed();
        });
    }

    /** A shot restarts the chamber being loaded (and the reload waits for the trigger to rest). */
    @SuitTest(timeoutTicks = 120, batch = "tnt")
    public static void tntLauncherShotRestartsTheChamberBeingLoaded(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 1.9, 7.5), 1, 5);
        int reload = PowerSuitsConfig.TNT_LAUNCHER_RELOAD.get(), delay = PowerSuitsConfig.TNT_LAUNCHER_RELOAD_DELAY.get();
        int shot = Math.max(3, reload - 4); // most of the way through loading the second chamber
        helper.runAfterDelay(shot, () -> {
            helper.assertTrue(drumOf(suit[0]) == 1 && suit[0].socket().getSpin() > 0.5F, "loading the next chamber: drum "
                    + drumOf(suit[0]) + ", progress " + suit[0].socket().getSpin());
            suit[0].socket().setTriggerHeld(pilot, true);
        });
        helper.runAfterDelay(shot + 1, () -> suit[0].socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(shot + delay + reload - 3, () -> helper.assertTrue(drumOf(suit[0]) == 0 && ammoLeft(suit[0]) == 5,
                "the half-loaded chamber started over: drum " + drumOf(suit[0]) + ", ammo " + ammoLeft(suit[0])));
        helper.runAfterDelay(shot + delay + reload + 4, () -> {
            helper.assertTrue(drumOf(suit[0]) == 1 && ammoLeft(suit[0]) == 4, "then loads it: drum " + drumOf(suit[0]) + ", ammo " + ammoLeft(suit[0]));
            helper.succeed();
        });
    }

    /** The rounds in the drum stay on the launcher: packed up with the suit and back when it's deployed. */
    @SuitTest
    public static void tntDrumStaysWithTheLauncher(GameTestHelper helper) {
        PowerSuitEntity suit = fueledSuit(helper, new ItemStack(Items.COAL, 2));
        ItemStack launcher = new ItemStack(ModItems.TNT_LAUNCHER.get());
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, launcher);
        TntLauncherItem.setLoaded(suit, launcher, 3);
        helper.assertTrue(TntLauncherItem.loaded(suit.getSocketStack()) == 3, "the synced copy has the drum");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 2, 4.5)));
        suit.packUp(player);
        SuitLoadout loadout = packedSuit(player).get(ModDataComponents.LOADOUT.get());
        helper.assertTrue(loadout != null && TntLauncherItem.loaded(loadout.socket()) == 3, "packed with 3 rounds in its drum, got " + loadout);
        helper.succeed();
    }

    /** A round that hits a mob goes off on it, and the blast is the pilot's (kill credit). */
    @SuitTest(timeoutTicks = 60, batch = "tnt")
    public static void tntLauncherBlastIsThePilots(GameTestHelper helper) {
        var husk = statueHusk(helper, new Vec3(4.5, 2, 6.5));
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 3, 6.5), 1, 0);
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(3, () -> suit[0].socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth() || !husk.isAlive(), "the husk took the blast, health " + husk.getHealth());
            var src = husk.getLastDamageSource();
            helper.assertTrue(src != null && src.is(DamageTypeTags.IS_EXPLOSION), "explosion damage, got " + src);
            helper.assertTrue(src.getEntity() == pilot, "credited to the pilot, got " + src.getEntity());
            helper.succeed();
        });
    }

    /** The visor's TNT arc (TntShell.predict from TntLauncherItem.launch, drawn by client/hud/TntLauncherHud) is the
     *  round's flight: a round fired from where the launcher would fire it passes every point of the arc, tick by tick,
     *  and goes off on the floor on the arc's last tick. */
    @SuitTest(timeoutTicks = 60, batch = "tnt")
    public static void tntArcPredictsTheRound(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 2.6, 8.5), 0, 0);
        TntShell[] shell = new TntShell[1];
        Trajectory[] flight = new Trajectory[1];
        int[] lastSeen = {-1};
        String[] off = {null};
        helper.runAfterDelay(2, () -> {
            TntLauncherItem.Launch launch = TntLauncherItem.launch(suit[0], pilot, 1F);
            flight[0] = TntShell.predict(helper.getLevel(), pilot, launch.from(), launch.velocity());
            // the launcher's own round, without the blast's damage to the pad (power 1, no blocks broken)
            shell[0] = new TntShell(helper.getLevel(), pilot, launch.from(), launch.velocity(), 1F, false);
            helper.getLevel().addFreshEntity(shell[0]);
        });
        helper.onEachTick(() -> {
            TntShell s = shell[0];
            if (s == null || s.isRemoved()) {
                return;
            }
            int t = s.tickCount;
            List<Vec3> path = flight[0].path();
            if (off[0] == null && (t >= path.size() || s.position().distanceTo(path.get(t)) > 1e-6)) {
                off[0] = "tick " + t + ": at " + s.position() + ", the arc says " + (t < path.size() ? path.get(t) : "gone off");
            }
            lastSeen[0] = Math.max(lastSeen[0], t);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(off[0] == null, "the round follows the arc: " + off[0]);
            helper.assertTrue(flight[0].hit() && flight[0].entity() == null, "the arc ends on a block");
            double floor = helper.absoluteVec(new Vec3(0, 2, 0)).y;
            helper.assertTrue(Math.abs(flight[0].end().y - floor) < 0.2, "on the floor's top (y " + floor + "), ends at " + flight[0].end());
            helper.assertTrue(flight[0].ticks() > 3, "a real arc, " + flight[0].ticks() + " ticks");
            helper.assertTrue(shell[0].isRemoved(), "the round has gone off");
            helper.assertTrue(lastSeen[0] == flight[0].ticks() - 1, "on the arc's last tick (" + flight[0].ticks()
                    + "): last seen in the air at tick " + lastSeen[0]);
            helper.succeed();
        });
    }

    /** A creative pilot loads the drum without TNT (it still takes the reload's time). */
    @SuitTest(timeoutTicks = 80, batch = "tnt")
    public static void tntLauncherIsFreeInCreative(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 1.9, 7.5), 0, 0);
        pilot.getAbilities().instabuild = true;
        int reload = PowerSuitsConfig.TNT_LAUNCHER_RELOAD.get();
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(reload + PowerSuitsConfig.TNT_LAUNCHER_SHOT_INTERVAL.get() + 4, () -> {
            suit[0].socket().setTriggerHeld(pilot, false);
            helper.assertTrue(suit[0].socket().getUses() >= 1, "creative loads and fires without TNT, got " + suit[0].socket().getUses());
            helper.succeed();
        });
    }

    /** With the tnt_explodes game rule off, a round is a dud: nothing breaks. Its own batch, whose environment turns the
     *  rule off and back on around the batch (SuitTests): the rule is world-wide. (Setting and restoring it in the test
     *  broke every later TNT test when two copies ran at once: the second one "restored" the first one's off.) */
    @SuitTest(timeoutTicks = 60, batch = "tntrule")
    public static void tntLauncherObeysTheTntGameRule(GameTestHelper helper) {
        helper.assertTrue(!helper.getLevel().getGameRules().get(GameRules.TNT_EXPLODES), "the batch turns tnt_explodes off");
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = tntPilot(helper, suit, new Vec3(4.5, 1.9, 6.5), 1, 0);
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(3, () -> suit[0].socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(suit[0].socket().getUses() == 1, "it still fires");
            helper.assertTrue(shellsInTest(helper).isEmpty(), "the round is gone");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 6)).is(Blocks.SMOOTH_STONE), "but breaks nothing (the pad's smooth stone floor)");
            helper.succeed();
        });
    }

    /** The machine mind never fires it (user decision): a friendly suit with the launcher, TNT and a monster near its
     *  owner doesn't shoot. */
    @SuitTest(timeoutTicks = 120)
    public static void tntLauncherMindNeverFires(GameTestHelper helper) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(4, 2, 1));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.TNT_LAUNCHER.get()));
        var owner = ownerAt(helper, suit, new Vec3(1.5, 2, 1.5));
        statueHusk(helper, new Vec3(4.5, 2, 7.5));
        helper.runAfterDelay(100, () -> {
            dismiss(helper, owner);
            helper.assertTrue(!suit.brain().followsOwner() && !suit.brain().hasGun(), "the launcher is no weapon for the mind");
            // (and with nobody inside its drum doesn't load: there's no pilot's TNT to load it with)
            helper.assertTrue(suit.socket().getUses() == 0 && drumOf(suit) == 0, "it never fired, uses "
                    + suit.socket().getUses() + ", drum " + drumOf(suit));
            helper.assertTrue(shellsInTest(helper).isEmpty(), "no rounds");
            helper.succeed();
        });
    }

    // =========================================================================================
    // Rail cannon (Phase 11, 2026-10-26)
    // =========================================================================================

    /** A suit at (4, 2, 1) facing +Z with the rail cannon, fuel, and a survival pilot carrying {@code ingots} iron
     *  ingots, looking at {@code target} (relative). */
    private static Player railPilot(GameTestHelper helper, PowerSuitEntity[] out, Vec3 target, int ingots) {
        PowerSuitEntity suit = suitAt(helper, new BlockPos(4, 2, 1));
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 2));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.RAIL_CANNON.get()));
        suit.setYRot(0);
        suit.setYBodyRot(0);
        Player pilot = boardedPilot(helper, suit);
        if (ingots > 0) {
            pilot.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, ingots));
        }
        suit.positionRider(pilot);
        aim(helper, pilot, target);
        out[0] = suit;
        return pilot;
    }

    /** Let go early and nothing fires (no ingot spent; the charge's fuel is burnt); hold to a full charge and let go:
     *  one shot, one ingot. The fuel goes while the trigger is held (charge_energy_per_tick), none at the release. */
    @SuitTest(timeoutTicks = 120, batch = "rail")
    public static void railCannonFiresOnlyAtFullCharge(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = railPilot(helper, suit, new Vec3(4.5, 3, 7.5), 3);
        int full = PowerSuitsConfig.RAIL_CHARGE.get();
        int[] energy = new int[2];
        int perTick = PowerSuitsConfig.RAIL_CHARGE_ENERGY.get();
        helper.runAfterDelay(2, () -> {
            energy[0] = suit[0].power().getTotalEnergy();
            suit[0].socket().setTriggerHeld(pilot, true);
        });
        helper.runAfterDelay(2 + full - 5, () -> {
            helper.assertTrue(suit[0].socket().getSpin() > 0.5F && suit[0].socket().getSpin() < 1F, "charging, got " + suit[0].socket().getSpin());
            int burnt = energy[0] - suit[0].power().getTotalEnergy();
            helper.assertTrue(burnt >= (full - 6) * perTick && burnt <= (full - 4) * perTick + 10, "charging burns " + perTick + " a tick, burnt " + burnt);
            suit[0].socket().setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(2 + full + 5, () -> {
            helper.assertTrue(suit[0].socket().getUses() == 0 && ammoLeft(suit[0]) == 3, "an early release fires nothing, uses " + suit[0].socket().getUses());
            helper.assertTrue(suit[0].socket().getSpin() < 0.5F, "and the charge winds down, " + suit[0].socket().getSpin());
            suit[0].socket().setTriggerHeld(pilot, true);
        });
        helper.runAfterDelay(2 + 2 * full + 15, () -> {
            helper.assertTrue(suit[0].socket().getSpin() >= 0.999F, "fully charged, " + suit[0].socket().getSpin());
            helper.assertTrue(suit[0].socket().getUses() == 0, "it holds the charge while the trigger is down");
            energy[1] = suit[0].power().getTotalEnergy();
            suit[0].socket().setTriggerHeld(pilot, false);
        });
        helper.runAfterDelay(2 + 2 * full + 17, () -> {
            helper.assertTrue(suit[0].socket().getUses() == 1, "let go at full charge: one shot, got " + suit[0].socket().getUses());
            helper.assertTrue(ammoLeft(suit[0]) == 2, "one ingot used, left " + ammoLeft(suit[0]));
            int used = energy[1] - suit[0].power().getTotalEnergy();
            helper.assertTrue(used <= perTick + 2, "the release itself burns no fuel (at most the last held tick + idle), used " + used);
            helper.assertTrue(suit[0].socket().getSpin() == 0F, "the charge is spent");
            helper.succeed();
        });
    }

    /** The beam goes through every mob on the line: two husks one behind the other both take the hit (and die: 40 >
     *  20), credited to the pilot, with rail damage. */
    @SuitTest(timeoutTicks = 80, batch = "rail")
    public static void railCannonPiercesEveryMobInLine(GameTestHelper helper) {
        var near = statueHusk(helper, new Vec3(4.5, 2, 4.5));
        var far = statueHusk(helper, new Vec3(4.5, 2, 7.5));
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = railPilot(helper, suit, new Vec3(4.5, 3.0, 7.5), 1);
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(4 + PowerSuitsConfig.RAIL_CHARGE.get(), () -> suit[0].socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(6 + PowerSuitsConfig.RAIL_CHARGE.get(), () -> {
            var shot = RailCannonItem.lastShot(suit[0]);
            helper.assertTrue(shot != null && shot.hit().size() == 2, "both husks on the beam, got " + (shot == null ? null : shot.hit()));
            for (var husk : List.of(near, far)) {
                float expected = PowerSuitsConfig.RAIL_DAMAGE.get().floatValue();
                helper.assertTrue(expected >= husk.getMaxHealth() ? !husk.isAlive() || husk.getHealth() <= 0 : husk.getHealth() <= husk.getMaxHealth() - expected + 0.01F,
                        "each takes the whole hit, health " + husk.getHealth());
                var src = husk.getLastDamageSource();
                helper.assertTrue(src != null && src.is(RailCannonItem.RAIL), "rail damage, got " + src);
                helper.assertTrue(src.getEntity() == pilot, "credited to the pilot, got " + src.getEntity());
            }
            helper.assertTrue(pilot.getHealth() == pilot.getMaxHealth() && suit[0].getHealth() == suit[0].getMaxHealth(), "the shooter isn't hit");
            helper.succeed();
        });
    }

    /** It breaks what a sprinting suit smashes (glass) and goes on, and stops at stone: a husk behind the stone is safe. */
    @SuitTest(timeoutTicks = 80, batch = "rail")
    public static void railCannonBreaksGlassAndStopsAtStone(GameTestHelper helper) {
        for (int x = 2; x <= 6; x++) {
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(x, y, 4), Blocks.GLASS);
                helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
            }
        }
        var husk = statueHusk(helper, new Vec3(4.5, 2, 7.5));
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = railPilot(helper, suit, new Vec3(4.5, 3.5, 7.5), 1);
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(4 + PowerSuitsConfig.RAIL_CHARGE.get(), () -> suit[0].socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(6 + PowerSuitsConfig.RAIL_CHARGE.get(), () -> {
            int glass = 0, stone = 0;
            for (int x = 2; x <= 6; x++) {
                for (int y = 2; y <= 5; y++) {
                    glass += helper.getBlockState(new BlockPos(x, y, 4)).is(Blocks.GLASS) ? 1 : 0;
                    stone += helper.getBlockState(new BlockPos(x, y, 6)).is(Blocks.STONE) ? 1 : 0;
                }
            }
            var shot = RailCannonItem.lastShot(suit[0]);
            helper.assertTrue(suit[0].socket().getUses() == 1 && shot != null, "it fired");
            if (PowerSuitsConfig.RAIL_BREAKS_BLOCKS.get()) {
                helper.assertTrue(glass >= 18 && glass <= 19 && shot.blocksBroken() == 20 - glass, "it broke the glass it went through, left " + glass);
                helper.assertTrue(shot.to().z - helper.absoluteVec(Vec3.ZERO).z > 5.9, "and went on to the stone, stopped at z " + shot.to().z);
            }
            helper.assertTrue(stone == 20, "the stone stops it, intact " + stone);
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "the husk behind the stone is safe");
            helper.succeed();
        });
    }

    /** Without iron ingots it doesn't even charge: a click and a message, nothing used. A creative pilot needs none. */
    @SuitTest(timeoutTicks = 100, batch = "rail")
    public static void railCannonNeedsIronIngots(GameTestHelper helper) {
        PowerSuitEntity[] suit = new PowerSuitEntity[1];
        Player pilot = railPilot(helper, suit, new Vec3(4.5, 3, 7.5), 0);
        int full = PowerSuitsConfig.RAIL_CHARGE.get();
        helper.runAfterDelay(2, () -> suit[0].socket().setTriggerHeld(pilot, true));
        helper.runAfterDelay(4 + full, () -> {
            helper.assertTrue(suit[0].socket().getSpin() == 0F, "no ingot, no charge: " + suit[0].socket().getSpin());
            suit[0].socket().setTriggerHeld(pilot, false);
            pilot.getAbilities().instabuild = true;
            suit[0].socket().setTriggerHeld(pilot, true);
        });
        helper.runAfterDelay(8 + 2 * full, () -> suit[0].socket().setTriggerHeld(pilot, false));
        helper.runAfterDelay(10 + 2 * full, () -> {
            helper.assertTrue(suit[0].socket().getUses() == 1, "creative fires without ingots, got " + suit[0].socket().getUses());
            helper.succeed();
        });
    }

    private static PowerSuitEntity railGuard(GameTestHelper helper, BlockPos at) {
        PowerSuitEntity suit = suitAt(helper, at);
        suit.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
        suit.getInventory().setItem(PowerSuitEntity.SOCKET_SLOT, new ItemStack(ModItems.RAIL_CANNON.get()));
        suit.brain().setHome(helper.absolutePos(at));
        return suit;
    }

    /** The machine mind guards home with it: a husk across the pad gets shot (charge, aim, let go), needing no ingots; the
     *  suit stays home. */
    @SuitTest(timeoutTicks = 140)
    public static void railCannonMindShootsMonstersFromHome(GameTestHelper helper) {
        PowerSuitEntity suit = railGuard(helper, new BlockPos(4, 2, 1));
        var husk = statueHusk(helper, new Vec3(5.5, 2, 7.5));
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(suit.socket().getUses() >= 1, "it should have fired, uses " + suit.socket().getUses());
            helper.assertTrue(!husk.isAlive() || husk.getHealth() < husk.getMaxHealth(), "and hit the husk, health " + husk.getHealth());
            helper.assertTrue(!suit.brain().isAwayFromHome(1.0), "from home");
            helper.assertTrue(!suit.brain().followsOwner() && suit.brain().snipesFromHome(), "the rail guard job, not the chaingun's");
            helper.succeed();
        });
    }

    /** The beam goes on past the target, so a villager behind the husk (on the line) holds the mind's fire. */
    @SuitTest(timeoutTicks = 140)
    public static void railCannonMindHoldsFireWithAFriendOnTheBeam(GameTestHelper helper) {
        PowerSuitEntity suit = railGuard(helper, new BlockPos(4, 2, 1));
        statueHusk(helper, new Vec3(4.5, 2, 5.5));
        // right behind it: the beam aimed down at the husk would reach the floor further back, past a villager there
        var villager = helper.spawn(EntityTypes.VILLAGER, new Vec3(4.5, 2, 6.7));
        villager.setNoAi(true);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(suit.socket().getUses() == 0, "no shot through a villager, uses " + suit.socket().getUses());
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager is fine");
            helper.succeed();
        });
    }

    private PowerSuitTests() {}
}
