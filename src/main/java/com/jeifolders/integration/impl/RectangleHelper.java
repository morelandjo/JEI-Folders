package com.jeifolders.integration.impl;

import com.jeifolders.integration.Rectangle2i;
import mezz.jei.common.util.ImmutableRect2i;

/**
 * Helper class that handles conversion between our platform-agnostic Rectangle2i
 * and JEI's ImmutableRect2i implementations.
 */
public class RectangleHelper {
    
    private RectangleHelper() {
        // Utility class, no instantiation
    }
    
    /**
     * Converts a JEI ImmutableRect2i to our Rectangle2i.
     */
    public static Rectangle2i fromJei(ImmutableRect2i rect) {
        if (rect == null || rect.isEmpty()) {
            return Rectangle2i.EMPTY;
        }
        return new Rectangle2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }
    
    /**
     * Converts our Rectangle2i to JEI's ImmutableRect2i.
     */
    public static ImmutableRect2i toJei(Rectangle2i rect) {
        if (rect == null || rect.isEmpty()) {
            return ImmutableRect2i.EMPTY;
        }
        return new ImmutableRect2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }
}