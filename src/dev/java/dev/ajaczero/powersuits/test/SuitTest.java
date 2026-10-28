package dev.ajaczero.powersuits.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method in PowerSuitTests as a GameTest on the test_pad structure. Since 1.21.5 Minecraft has no @GameTest
 * annotation any more (tests are registry entries, like blocks); SuitTests finds these and registers them.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SuitTest {
    /** Fails the test if it hasn't succeeded after this many ticks. */
    int timeoutTicks() default 100;

    /** Tests in another batch run separately (in their own test environment), e.g. when other tests' players would get in the way. */
    String batch() default "";
}
