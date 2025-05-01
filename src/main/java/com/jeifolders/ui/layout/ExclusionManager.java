package com.jeifolders.ui.layout;

import com.jeifolders.ui.util.ExclusionHandler;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

/**
 * Manages exclusion zones for JEI integration.
 * Responsible for calculating and updating exclusion zones based on UI state.
 * This class has been updated to use Minecraft's Rect2i exclusively.
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
    
    // Constants for ingredient grid coverage
    private static final int BASE_FOLDER_NAME_HEIGHT = 25;
    private static final int ACTIVE_FOLDER_EXTRA_PADDING = 60;
    private static final int MINIMUM_INGREDIENT_GRID_HEIGHT = 200;
    
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
            updateExclusionHandler(exclusionZone);
            return exclusionZone;
        }

        // Get the maximum exclusion width from the layout calculator
        int maxExclusionWidth = layoutCalculator.getMaxExclusionWidth();
        int foldersPerRow = layoutCalculator.getFoldersPerRow();
        
        // Calculate width of the exclusion zone
        int exclusionWidth;
        int exclusionPadding = layoutCalculator.getExclusionPadding();
        
        // Calculate the grid width to ensure it accommodates all folders
        int gridWidth = layoutCalculator.calculateGridWidth();
        
        // Use a more conservative width calculation that better fits the actual UI
        exclusionWidth = Math.min(maxExclusionWidth, gridWidth + exclusionPadding);
        
        // For a single folder, ensure minimum width but keep it compact
        if (!foldersVisible || folderCount == 0) {
            exclusionWidth = Math.min(maxExclusionWidth,
                          layoutCalculator.getIconWidth() + exclusionPadding);
        }
        
        // Ensure the width is adequate for the bookmark display when a folder is active
        if (hasActiveFolder) {
            // Use a more precise width calculation for the ingredient GUI
            // This ensures we don't take up excessive horizontal space
            int ingredientGuiWidth = Math.min(maxExclusionWidth, gridWidth + 40);
            exclusionWidth = Math.min(maxExclusionWidth, ingredientGuiWidth);
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
                exclusionHeight = buttonsHeight + BASE_FOLDER_NAME_HEIGHT;
                
                // When a folder is active, ensure we have enough height for the ingredient grid
                int ingredientGridHeight = Math.max(
                    MINIMUM_INGREDIENT_GRID_HEIGHT, 
                    bookmarkDisplayHeight * 2         
                );
                
                // Add the ingredient grid height plus extra padding
                exclusionHeight += ingredientGridHeight + ACTIVE_FOLDER_EXTRA_PADDING;
                
                
            } else {
                exclusionHeight = buttonsHeight + 10;
            }
        } else {
            // If folders aren't visible or there are no folders, minimal height
            exclusionHeight = layoutCalculator.getIconHeight() + (2 * exclusionPadding);
        }
        
        // Ensure we have positive dimensions
        if (exclusionWidth <= 0) exclusionWidth = 1;
        if (exclusionHeight <= 0) exclusionHeight = 1;
        
        // Create the exclusion zone
        exclusionZone = new Rect2i(0, 0, exclusionWidth, exclusionHeight);
        
        // Update the ExclusionHandler using Minecraft's Rect2i directly
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
        // Use the Rect2i directly with the updated ExclusionHandler
        exclusionHandler.addExclusionArea(zone);
        
        ModLogger.debug("Updated exclusion handler with zone: x={}, y={}, width={}, height={}", 
                       zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight());
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