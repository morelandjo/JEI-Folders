package com.jeifolders.gui;

import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles exclusion areas for folder bookmarks in JEI.
 */
public class ExclusionHandler {
    private final Set<Rectangle2i> exclusionAreas = new HashSet<>();
    
    public void addExclusionArea(Rectangle2i area) {
        if (!area.isEmpty()) {
            ModLogger.debug("Adding exclusion area: {}", area);
            exclusionAreas.add(area);
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
     * This method is used by JEIExclusionHandler.
     */
    public Collection<Rect2i> getExclusionAreas() {
        if (exclusionAreas.isEmpty()) {
            return Collections.emptyList();
        }
        
        Collection<Rect2i> result = new ArrayList<>(exclusionAreas.size());
        for (Rectangle2i area : exclusionAreas) {
            result.add(new Rect2i(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        }
        return result;
    }
}
