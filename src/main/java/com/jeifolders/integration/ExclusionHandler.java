package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles exclusion zones for JEI integration.
 * This prevents JEI from drawing elements on top of the folder UI.
 */
public class ExclusionHandler {
    // List of areas that should be excluded from JEI
    private final List<Rect2i> exclusionZones = new ArrayList<>();

    /**
     * Gets the current exclusion zones
     * 
     * @return List of rectangles representing excluded areas
     */
    public List<Rect2i> getExclusionZones() {
        return new ArrayList<>(exclusionZones);
    }
    
    /**
     * Adds an exclusion zone
     * 
     * @param zone Rectangle to exclude
     */
    public void addExclusionZone(Rect2i zone) {
        if (zone != null && zone.getWidth() > 0 && zone.getHeight() > 0) {
            exclusionZones.add(zone);
            ModLogger.debug("Added exclusion zone: {},{},{},{}", 
                zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight());
        }
    }
    
    /**
     * Clears all exclusion zones
     */
    public void clearExclusionZones() {
        exclusionZones.clear();
        ModLogger.debug("Cleared all exclusion zones");
    }
}