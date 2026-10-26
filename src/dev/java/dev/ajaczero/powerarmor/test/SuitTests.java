package dev.ajaczero.powerarmor.test;

import dev.ajaczero.powerarmor.PowerArmor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the @SuitTest methods of PowerArmorTests as GameTests. Since Minecraft 1.21.5 a GameTest is two registry
 * entries: the test function (the code: a Consumer of GameTestHelper) and the test instance (which structure to build,
 * the time limit, the environment). Only when game tests are enabled (dev runs, runGameTestServer), so players never
 * see them.
 */
public final class SuitTests {
    private static final Identifier PAD = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "test_pad");
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, PowerArmor.MODID);
    private static final Map<Method, ResourceKey<Consumer<GameTestHelper>>> TESTS = new HashMap<>();

    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        for (Method method : PowerArmorTests.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SuitTest.class) && Modifier.isStatic(method.getModifiers())) {
                String name = method.getName().toLowerCase(Locale.ROOT); // registry ids are lower case
                FUNCTIONS.register(name, () -> helper -> invoke(method, helper));
                TESTS.put(method, ResourceKey.create(Registries.TEST_FUNCTION, Identifier.fromNamespaceAndPath(PowerArmor.MODID, name)));
            }
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(SuitTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        TESTS.forEach((method, function) -> {
            SuitTest spec = method.getAnnotation(SuitTest.class);
            String batch = spec.batch().isEmpty() ? "default" : spec.batch();
            // One environment per batch: tests are batched by environment.
            // The "rain" batch (the tidal engine runs in rain) sets the weather and leaves the pad open to the sky: by
            // default the framework puts a barrier ceiling over each test, which keeps rain off.
            boolean rain = batch.equals("rain");
            Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(batch, b -> event.registerEnvironment(
                    Identifier.fromNamespaceAndPath(PowerArmor.MODID, b), rain
                            ? new TestEnvironmentDefinition.Weather(TestEnvironmentDefinition.Weather.Type.RAIN)
                            : new TestEnvironmentDefinition.AllOf(List.of())));
            event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment,
                    Level.OVERWORLD, PAD, spec.timeoutTicks(), 0, true, Rotation.NONE,
                    false, 1, 1, rain, 0)));
        });
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            // Assertion failures are thrown as exceptions: rethrow the real one so the test reports its message.
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private SuitTests() {}
}
