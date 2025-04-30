package com.jeifolders.ui.util;

import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles exclusion areas for folder bookmarks
 */
public class ExclusionHandler {
    private final Set<Rectangle2i> exclusionAreas = new HashSet<>();
    
    public void addExclusionArea(Rectangle2i area) {
        if (!area.isEmpty()) {
            ModLogger.debug("Adding exclusion area: {}", area);
            exclusionAreas.add(area);
        } else {
            ModLogger.debug("Attempted to add empty exclusion area - ignored");
        }
    }
    
    public void removeExclusionArea(Rectangle2i area) {
        ModLogger.debug("Removing exclusion area: {}", area);
        exclusionAreas.remove(area);
    }
    
    public void clearExclusionAreas() {
        ModLogger.debug("Clearing all exclusion areas");
        exclusionAreas.clear();
    }

    /**
     * Gets the current exclusion areas as Rect2i objects.
     */
    public Collection<Rect2i> getExclusionAreas() {
        if (exclusionAreas.isEmpty()) {
            return Collections.emptyList();
        }
        
        Collection<Rect2i> result = new ArrayList<>(exclusionAreas.size());
        for (Rectangle2i area : exclusionAreas) {
            // Create a Minecraft Rect2i from our Rectangle2i
            Rect2i rect = new Rect2i(area.getX(), area.getY(), area.getWidth(), area.getHeight());
            result.add(rect);
            
            ModLogger.debug("Converting exclusion area to Rect2i: {} -> x={}, y={}, w={}, h={}", 
                area, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        }
        return result;
    }
}
