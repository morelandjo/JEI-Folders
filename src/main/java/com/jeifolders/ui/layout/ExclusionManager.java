package com.jeifolders.ui.layout;

import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.ui.util.ExclusionHandler;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

/**
 * Manages exclusion zones for JEI integration.
 * Responsible for calculating and updating exclusion zones based on UI state.
 */
public class ExclusionManager {
    private final ExclusionHandler exclusionHandler;
    private final LayoutCalculator layoutCalculator;
    private Rect2i exclusionZone = new Rect2i(0, 0, 0, 0);
    
    // Caching state
    private boolean exclusionZoneCacheValid = false;
    private boolean lastFoldersVisible = true;
    private boolean lastHasActiveFolder = false;
    private int lastFolderCount = -1;
    private int lastBookmarkDisplayHeight = 0;
    
    /**
     * Creates a new exclusion manager.
     * 
     * @param layoutCalculator The layout calculator for dimensions
     */
    public ExclusionManager(LayoutCalculator layoutCalculator) {
        this.exclusionHandler = new ExclusionHandler();
        this.layoutCalculator = layoutCalculator;
    }
    
    /**
     * Updates the exclusion zone dimensions based on current state
     * 
     * @param folderCount Number of folders
     * @param foldersVisible Whether folders are currently visible
     * @param hasActiveFolder Whether a folder is currently active
     * @param bookmarkDisplayHeight Height of bookmark display, or 0 if none
     * @return Updated exclusion zone
     */
    public Rect2i updateExclusionZone(int folderCount, boolean foldersVisible, 
                                     boolean hasActiveFolder, int bookmarkDisplayHeight) {
        // Check if we can use the cached value
        if (exclusionZoneCacheValid && 
            folderCount == lastFolderCount &&
            foldersVisible == lastFoldersVisible && 
            hasActiveFolder == lastHasActiveFolder &&
            bookmarkDisplayHeight == lastBookmarkDisplayHeight) {
            return exclusionZone;
        }
        
        // Update cache tracking variables
        lastFolderCount = folderCount;
        lastFoldersVisible = foldersVisible;
        lastHasActiveFolder = hasActiveFolder;
        lastBookmarkDisplayHeight = bookmarkDisplayHeight;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            exclusionZone = new Rect2i(0, 0, 0, 0);
            exclusionZoneCacheValid = true;
            return exclusionZone;
        }

        // Get the maximum exclusion width from the layout calculator
        int maxExclusionWidth = layoutCalculator.getMaxExclusionWidth();
        int foldersPerRow = layoutCalculator.getFoldersPerRow();
        
        // Calculate width of the exclusion zone
        int exclusionWidth;
        int exclusionPadding = layoutCalculator.getExclusionPadding();
        
        if (foldersVisible && folderCount > 0 && foldersPerRow > 1) {
            // For multiple folders, calculate based on the grid width
            int gridWidth = layoutCalculator.calculateGridWidth();
            
            // Add padding to the calculated width
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    gridWidth + (2 * exclusionPadding));
        } else {
            // For single column or no folders, just use the button width plus padding
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    layoutCalculator.getIconWidth() + (2 * exclusionPadding));
        }

        // Calculate the height of the exclusion zone
        int exclusionHeight;
        
        if (foldersVisible && folderCount > 0) {
            // Calculate rows for the folder buttons
            int rows = layoutCalculator.calculateRows(folderCount);
            
            // Height should include the rows of buttons
            int buttonsHeight = rows * layoutCalculator.getFolderSpacingY();
            
            // Add the height for active folder name and optionally the bookmark display
            if (hasActiveFolder) {
                exclusionHeight = buttonsHeight + 20; // For folder name
                
                // Add bookmark display height if it exists
                if (bookmarkDisplayHeight > 0) {
                    exclusionHeight += bookmarkDisplayHeight + 10; // Add padding
                }
            } else {
                exclusionHeight = buttonsHeight + 10; // Just add some padding
            }
        } else {
            // If folders aren't visible or there are no folders, minimal height
            exclusionHeight = layoutCalculator.getIconHeight() + (2 * exclusionPadding);
        }
        
        // Create the exclusion zone
        exclusionZone = new Rect2i(0, 0, exclusionWidth, exclusionHeight);
        
        // Update the ExclusionHandler
        updateExclusionHandler(exclusionZone);
        
        exclusionZoneCacheValid = true;
        ModLogger.debug("Updated exclusion zone - width: {}, height: {}", 
                       exclusionZone.getWidth(), exclusionZone.getHeight());
        
        return exclusionZone;
    }
    
    /**
     * Gets the current exclusion zone
     * 
     * @return The current exclusion zone as a Rect2i
     */
    public Rect2i getExclusionZone() {
        return exclusionZone;
    }
    
    /**
     * Updates the exclusion handler with the current exclusion zone
     * 
     * @param zone The exclusion zone to update
     */
    private void updateExclusionHandler(Rect2i zone) {
        if (zone.getWidth() <= 0 || zone.getHeight() <= 0) {
            exclusionHandler.clearExclusionAreas();
            return;
        }
        
        exclusionHandler.clearExclusionAreas();
        Rectangle2i rect = new Rectangle2i(zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight());
        exclusionHandler.addExclusionArea(rect);
    }
    
    /**
     * Gets the exclusion handler for JEI integration
     */
    public ExclusionHandler getExclusionHandler() {
        return exclusionHandler;
    }
    
    /**
     * Invalidates the exclusion zone cache
     */
    public void invalidateCache() {
        exclusionZoneCacheValid = false;
    }
}