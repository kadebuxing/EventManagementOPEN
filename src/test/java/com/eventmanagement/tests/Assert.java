package com.eventmanagement.tests;

import java.util.Objects;

/** Minimal assertions so the project stays free of external test libraries. */
public final class Assert {
    private Assert() {
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            fail(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    public static void assertEquals(int expected, int actual, String message) {
        assertEquals(Integer.valueOf(expected), Integer.valueOf(actual), message);
    }

    public static void assertContains(String haystack, String needle, String message) {
        if (haystack == null || !haystack.contains(needle)) {
            fail(message + " (missing: " + needle + ")");
        }
    }

    public static void assertNotContains(String haystack, String needle, String message) {
        if (haystack != null && haystack.contains(needle)) {
            fail(message + " (unexpected: " + needle + ")");
        }
    }

    public static <T extends Throwable> T assertThrows(
            Class<T> expectedType, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return expectedType.cast(thrown);
            }
            fail(message + " (expected " + expectedType.getSimpleName()
                    + " but got " + thrown.getClass().getSimpleName() + ": "
                    + thrown.getMessage() + ")");
        }
        fail(message + " (expected " + expectedType.getSimpleName() + " but nothing was thrown)");
        return null;
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
