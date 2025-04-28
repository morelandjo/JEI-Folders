package com.jeifolders.gui.layout;

import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;

/**
 * Handles caching of layout data for improved performance.
 * Centralizes all caching logic for positions, dimensions and UI state.
 */
public class LayoutCacheService {
    // Interval between forced recalculations in milliseconds
    private static final int LAYOUT_RECALC_INTERVAL_MS = 1000;
    
    // Screen dimensions caching
    private int cachedScreenWidth = -1;
    private int cachedScreenHeight = -1;
    private long lastCalculationTime = 0;
    
    // Layout calculations caching
    private int cachedFolderCount = -1;
    private int cachedRows = -1;
    private int cachedGridWidth = -1;
    
    // Position caching
    private int[] cachedPositions = new int[100 * 2]; // Cache for up to 100 folder positions
    private boolean positionsCacheValid = false;
    
    // Delete button position caching
    private int[] cachedDeleteButtonPosition = new int[2];
    private boolean deleteButtonCacheValid = false;
    
    /**
     * Checks if screen dimensions have changed requiring a layout recalculation
     * 
     * @return true if screen dimensions have changed or cache is invalid
     */
    public boolean needsRecalculation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) return true;
        
        int currentWidth = minecraft.getWindow().getGuiScaledWidth();
        int currentHeight = minecraft.getWindow().getGuiScaledHeight();
        long currentTime = System.currentTimeMillis();
        
        boolean changed = currentWidth != cachedScreenWidth || 
                         currentHeight != cachedScreenHeight ||
                         currentTime - lastCalculationTime > LAYOUT_RECALC_INTERVAL_MS;
        
        if (changed) {
            cachedScreenWidth = currentWidth;
            cachedScreenHeight = currentHeight;
            lastCalculationTime = currentTime;
            invalidateAllCaches();
            
            ModLogger.debug("[CACHE] Screen size changed or timer expired - invalidating caches");
        }
        
        return changed;
    }
    
    /**
     * Invalidates all cached calculations
     */
    public void invalidateAllCaches() {
        positionsCacheValid = false;
        deleteButtonCacheValid = false;
        cachedRows = -1;
        cachedGridWidth = -1;
        
        ModLogger.debug("[CACHE] All caches invalidated");
    }
    
    /**
     * Gets cached position for a folder button
     * 
     * @param index The index of the folder
     * @return int[] array with [x, y] coordinates or null if not cached
     */
    public int[] getCachedPosition(int index) {
        if (positionsCacheValid && index * 2 < cachedPositions.length) {
            int x = cachedPositions[index * 2];
            int y = cachedPositions[index * 2 + 1];
            
            // Check if this position has been calculated
            if (x > 0 || y > 0) {
                return new int[] {x, y};
            }
        }
        return null;
    }
    
    /**
     * Caches a calculated position
     * 
     * @param index The index of the folder
     * @param x The x coordinate
     * @param y The y coordinate
     */
    public void setCachedPosition(int index, int x, int y) {
        if (index * 2 < cachedPositions.length) {
            cachedPositions[index * 2] = x;
            cachedPositions[index * 2 + 1] = y;
        }
    }
    
    /**
     * Sets the positions cache validity
     */
    public void setPositionsCacheValid(boolean valid) {
        this.positionsCacheValid = valid;
    }
    
    /**
     * Gets the cached number of rows
     * 
     * @return The cached number of rows or -1 if not cached
     */
    public int getCachedRows() {
        return cachedRows;
    }
    
    /**
     * Caches the calculated number of rows
     * 
     * @param folderCount The number of folders
     * @param rows The calculated number of rows
     */
    public void setCachedRows(int folderCount, int rows) {
        this.cachedFolderCount = folderCount;
        this.cachedRows = rows;
    }
    
    /**
     * Checks if rows are cached for the given folder count
     * 
     * @param folderCount The folder count to check
     * @return true if rows are cached for this folder count
     */
    public boolean hasRowsCached(int folderCount) {
        return cachedRows >= 0 && folderCount == cachedFolderCount;
    }
    
    /**
     * Gets the cached grid width
     * 
     * @return The cached grid width or -1 if not cached
     */
    public int getCachedGridWidth() {
        return cachedGridWidth;
    }
    
    /**
     * Sets the cached grid width
     * 
     * @param gridWidth The grid width to cache
     */
    public void setCachedGridWidth(int gridWidth) {
        this.cachedGridWidth = gridWidth;
    }
    
    /**
     * Gets the cached delete button position
     * 
     * @return The cached position or null if not cached
     */
    public int[] getCachedDeleteButtonPosition() {
        if (deleteButtonCacheValid) {
            return cachedDeleteButtonPosition;
        }
        return null;
    }
    
    /**
     * Sets the cached delete button position
     * 
     * @param x The x coordinate
     * @param y The y coordinate
     */
    public void setCachedDeleteButtonPosition(int x, int y) {
        this.cachedDeleteButtonPosition[0] = x;
        this.cachedDeleteButtonPosition[1] = y;
        this.deleteButtonCacheValid = true;
    }
    
    /**
     * Invalidates the delete button cache
     */
    public void invalidateDeleteButtonCache() {
        this.deleteButtonCacheValid = false;
    }
    
    /**
     * Gets the current cached screen width
     */
    public int getCachedScreenWidth() {
        return cachedScreenWidth;
    }
    
    /**
     * Gets the current cached screen height
     */
    public int getCachedScreenHeight() {
        return cachedScreenHeight;
    }
}