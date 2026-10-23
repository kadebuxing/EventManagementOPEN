package com.eventmanagement.tests;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Small reflective test runner.
 *
 * <p>The build environment only requires a JDK, so the suite does not depend on
 * JUnit. Every public method annotated with {@link Test} runs on a fresh
 * instance, which keeps the tests isolated from each other.</p>
 */
public final class TestRunner {
    private static final List<Class<?>> SUITE = List.of(
            ValidationTest.class,
            EventLifecycleTest.class,
            RegistrationTest.class,
            ReportTest.class,
            PersistenceTest.class,
            CliSmokeTest.class);

    private TestRunner() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        int total = 0;
        int passed = 0;
        long startedAt = System.nanoTime();

        try {
            for (Class<?> testClass : SUITE) {
                Method[] methods = testClass.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::getName));
                for (Method method : methods) {
                    if (!method.isAnnotationPresent(Test.class)) {
                        continue;
                    }
                    total++;
                    String name = testClass.getSimpleName() + "." + method.getName();
                    try {
                        Object instance = testClass.getDeclaredConstructor().newInstance();
                        method.invoke(instance);
                        passed++;
                        System.out.println("PASS  " + name);
                    } catch (InvocationTargetException ex) {
                        failures.add(name);
                        System.out.println("FAIL  " + name);
                        printCause(ex.getCause());
                    } catch (ReflectiveOperationException ex) {
                        failures.add(name);
                        System.out.println("ERROR " + name);
                        printCause(ex);
                    }
                }
            }
        } finally {
            TempFiles.cleanup();
        }

        long millis = (System.nanoTime() - startedAt) / 1_000_000;
        System.out.println();
        System.out.println("---------------------------------------------");
        System.out.println("Tests run: " + total + ", passed: " + passed
                + ", failed: " + failures.size() + ", time: " + millis + " ms");
        if (!failures.isEmpty()) {
            System.out.println("Failed tests:");
            for (String failure : failures) {
                System.out.println("  - " + failure);
            }
            System.exit(1);
        }
        System.out.println("OK");
    }

    private static void printCause(Throwable cause) {
        if (cause == null) {
            System.out.println("      <no cause>");
            return;
        }
        System.out.println("      " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        for (StackTraceElement element : cause.getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("com.eventmanagement")) {
                System.out.println("        at " + element);
            }
        }
    }
}
