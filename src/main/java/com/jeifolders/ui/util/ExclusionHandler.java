package com.jeifolders.ui.util;

import com.jeifolders.util.ModLogger;

import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles exclusion areas for folder bookmarks.
 * Uses Minecraft's Rect2i class exclusively.
 */
public class ExclusionHandler {
    // Primary storage uses Minecraft's Rect2i
    private final Set<Rect2i> exclusionAreas = new HashSet<>();
    
    /**
     * Adds a new exclusion area.
     * 
     * @param area The area to exclude
     */
    public void addExclusionArea(Rect2i area) {
        if (area != null && area.getWidth() > 0 && area.getHeight() > 0) {
            ModLogger.debug("Adding exclusion area: x={}, y={}, w={}, h={}", 
                area.getX(), area.getY(), area.getWidth(), area.getHeight());
            exclusionAreas.add(area);
        } else {
            ModLogger.debug("Attempted to add empty exclusion area - ignored");
        }
    }
    
    /**
     * Removes an exclusion area.
     * 
     * @param area The area to remove
     */
    public void removeExclusionArea(Rect2i area) {
        ModLogger.debug("Removing exclusion area: x={}, y={}, w={}, h={}", 
                area.getX(), area.getY(), area.getWidth(), area.getHeight());
        exclusionAreas.remove(area);
    }
    
    /**
     * Clears all exclusion areas.
     */
    public void clearExclusionAreas() {
        ModLogger.debug("Clearing all exclusion areas");
        exclusionAreas.clear();
    }

    /**
     * Gets the current exclusion areas as Rect2i objects.
     * 
     * @return A collection of Minecraft Rect2i objects representing the exclusion areas
     */
    public Collection<Rect2i> getExclusionAreas() {
        if (exclusionAreas.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Return a copy of the collection
        return new ArrayList<>(exclusionAreas);
    }
}
