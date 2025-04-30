package com.jeifolders.integration.impl;

import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.renderer.Rect2i;

/**
 * Helper class that handles conversion between rectangle implementations:
 * - JEI's ImmutableRect2i
 * - Minecraft's native Rect2i
 */
public class RectangleHelper {
    
    private RectangleHelper() {
        // Utility class, no instantiation
    }
    
    /**
     * Converts JEI's ImmutableRect2i to Minecraft's Rect2i.
     */
    public static Rect2i jeiToMinecraft(ImmutableRect2i rect) {
        if (rect == null || rect.isEmpty()) {
            return new Rect2i(0, 0, 0, 0);
        }
        return new Rect2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }
    
    /**
     * Converts Minecraft's Rect2i to JEI's ImmutableRect2i.
     */
    public static ImmutableRect2i minecraftToJei(Rect2i rect) {
        if (rect == null) {
            return ImmutableRect2i.EMPTY;
        }
        return new ImmutableRect2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }
}