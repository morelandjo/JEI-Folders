package com.jeifolders.util;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Utility class for optimized logging in the mod.
 * Uses lazy evaluation for debug messages to avoid string formatting costs in production.
 */
public class ModLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEI Folders");
    
    // Debug mode can be enabled by system property
    private static final boolean DEBUG_MODE = Boolean.getBoolean("jeifolders.debug");

    private ModLogger() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log an info message.
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * Log an info message with formatting.
     */
    public static void info(String format, Object... args) {
        LOGGER.info(format, args);
    }

    /**
     * Log an error message.
     */
    public static void error(String message) {
        LOGGER.error(message);
    }

    /**
     * Log an error message with formatting.
     */
    public static void error(String format, Object... args) {
        LOGGER.error(format, args);
    }

    /**
     * Log an error with an exception.
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    /**
     * Log a warning message.
     */
    public static void warn(String message) {
        LOGGER.warn(message);
    }

    /**
     * Log a warning message with formatting.
     */
    public static void warn(String format, Object... args) {
        LOGGER.warn(format, args);
    }

    /**
     * Log a debug message IF debug mode is enabled.
     * No-op in production to avoid performance impact.
     */
    public static void debug(String message) {
        if (DEBUG_MODE) {
            LOGGER.debug(message);
        }
    }

    /**
     * Log a debug message with formatting IF debug mode is enabled.
     * Uses lazy evaluation to avoid string formatting costs in production.
     */
    public static void debug(String format, Object... args) {
        if (DEBUG_MODE) {
            LOGGER.debug(format, args);
        }
    }

    /**
     * Log a debug message using a supplier to avoid computation costs when debug is disabled.
     * This is useful for expensive debug logs.
     */
    public static void debugLazy(Supplier<String> messageSupplier) {
        if (DEBUG_MODE) {
            LOGGER.debug(messageSupplier.get());
        }
    }
    
    /**
     * Check if debug logging is enabled.
     */
    public static boolean isDebugEnabled() {
        return DEBUG_MODE;
    }
}