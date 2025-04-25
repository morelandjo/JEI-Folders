package com.jeifolders.gui.folderButtons;

import com.jeifolders.gui.ExclusionHandler;
import com.jeifolders.gui.LayoutConstants;
import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.util.ModLogger;
import com.jeifolders.data.FolderDataRepresentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout manager for the folder system.
 * Responsible for calculating positions and dimensions for UI elements.
 */
public class FolderRenderingManager {
    // Singleton instance
    private static FolderRenderingManager instance;
    
    // ----- Layout Constants -----
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 10;
    private static final int ICON_WIDTH = FolderButtonTextures.ICON_WIDTH;
    private static final int ICON_HEIGHT = FolderButtonTextures.ICON_HEIGHT;
    private static final int FOLDER_SPACING_Y = 30;
    private static final int FOLDER_SPACING_X = 2;
    private static final int EXCLUSION_PADDING = 10;
    
    // ----- Layout Cache -----
    private int cachedScreenWidth = -1;
    private int cachedScreenHeight = -1;
    private int foldersPerRow = 1;
    private int[] cachedPositions = new int[100 * 2]; // Cache for up to 100 folder positions
    private boolean positionsCacheValid = false;
    private long lastCalculationTime = 0;
    private static final int LAYOUT_RECALC_INTERVAL_MS = 1000;
    
    // ----- Additional Cached Values -----
    private int cachedFolderCount = -1;
    private int cachedRows = -1;
    private int cachedGridWidth = -1;
    private int cachedMaxExclusionWidth = -1;
    
    // ----- Calculated Positions -----
    private int calculatedNameY = -1;
    private int calculatedBookmarkDisplayY = -1;
    private int nameYOffset = 0;
    private int[] cachedDeleteButtonPosition = new int[2];
    private boolean deleteButtonCacheValid = false;
    
    // ----- Exclusion Zone Management -----
    private final ExclusionHandler exclusionHandler = new ExclusionHandler();
    private Rect2i exclusionZone = new Rect2i(0, 0, 0, 0);
    private boolean exclusionZoneCacheValid = false;
    private boolean lastFoldersVisible = true;
    private boolean lastHasActiveFolder = false;
    private int lastBookmarkDisplayHeight = 0;
    
    // ----- Rendering State -----
    private boolean needsRebuild = true;
    
    // ----- Reference to the unified folder manager -----
    private final UnifiedFolderManager folderManager;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderRenderingManager() {
        this.folderManager = UnifiedFolderManager.getInstance();
        calculateInitialLayout();
    }
    
    /**
     * Initialize the folder GUI system
     */
    public static void init() {
        getInstance();
        ModLogger.debug("FolderRenderingManager initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderRenderingManager getInstance() {
        if (instance == null) {
            instance = new FolderRenderingManager();
        }
        return instance;
    }
    
    /**
     * Performs initial layout calculations
     */
    private void calculateInitialLayout() {
        calculateFoldersPerRow();
    }
    
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
        }
        
        return changed;
    }
    
    /**
     * Invalidates all cached calculations
     */
    public void invalidateAllCaches() {
        positionsCacheValid = false;
        deleteButtonCacheValid = false;
        exclusionZoneCacheValid = false;
        cachedRows = -1;
        cachedGridWidth = -1;
        cachedMaxExclusionWidth = -1;
    }
    
    /**
     * Mark that the UI needs to be rebuilt
     */
    public void markNeedsRebuild() {
        this.needsRebuild = true;
    }
    
    /**
     * Check if the UI needs rebuilding
     */
    public boolean needsRebuild() {
        return needsRebuild;
    }
    
    /**
     * Resets the rebuild flag
     */
    public void clearRebuildFlag() {
        this.needsRebuild = false;
    }
    
    /**
     * Calculates the maximum number of folders that can fit in a row
     * based on the current screen width
     */
    public void calculateFoldersPerRow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            foldersPerRow = 1;
            return;
        }

        // Only recalculate if screen dimensions changed
        if (!needsRecalculation() && foldersPerRow > 0) {
            return;
        }

        // Calculate available width and determine folders per row
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiLeft = LayoutConstants.calculateGuiLeft(screenWidth);

        int availableWidth = Math.max(1, guiLeft - PADDING_X);
        int folderWidth = ICON_WIDTH + (2 * FOLDER_SPACING_X);
        int newFoldersPerRow = Math.max(1, availableWidth / folderWidth);
        
        if (newFoldersPerRow != foldersPerRow) {
            foldersPerRow = newFoldersPerRow;
            invalidateAllCaches();
        }
        
        // Calculate and cache maxExclusionWidth
        cachedMaxExclusionWidth = LayoutConstants.calculateMaxWidthBeforeGui(screenWidth);
        
        ModLogger.debug("Layout calculation: screen width: {}, available width: {}, folders per row: {}", 
                      screenWidth, availableWidth, foldersPerRow);
    }
    
    /**
     * Gets the current number of folders per row
     * 
     * @return The current number of folders that can fit in a row
     */
    public int getFoldersPerRow() {
        return foldersPerRow;
    }
    
    /**
     * Calculates folder button position based on its index in the grid.
     * 
     * @param index The index of the folder (0 is for the add button)
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateFolderPosition(int index) {
        // Use cached value if possible
        if (positionsCacheValid && index * 2 < cachedPositions.length) {
            int x = cachedPositions[index * 2];
            int y = cachedPositions[index * 2 + 1];
            
            // Check if this position has been calculated
            if (x > 0 || y > 0) {
                return new int[] {x, y};
            }
        }
        
        // Calculate position
        int row = index / foldersPerRow;
        int col = index % foldersPerRow;
        
        int x = PADDING_X + col * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
        int y = PADDING_Y + row * FOLDER_SPACING_Y;
        
        // Cache result if possible
        if (index * 2 < cachedPositions.length) {
            cachedPositions[index * 2] = x;
            cachedPositions[index * 2 + 1] = y;
        }
        
        return new int[] {x, y};
    }
    
    /**
     * Calculates the position for the add button
     * 
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateAddButtonPosition() {
        // Add button is always at position 0
        return calculateFolderPosition(0);
    }
    
    /**
     * Calculates and caches the number of rows needed for the given folder count
     * 
     * @param folderCount Number of folders (excluding the add button)
     * @return Number of rows including the add button
     */
    private int calculateRows(int folderCount) {
        if (cachedRows >= 0 && folderCount == cachedFolderCount) {
            return cachedRows;
        }
        
        // Calculate how many rows we need, including the Add button
        int effectiveButtonCount = folderCount + 1;
        cachedRows = (int)Math.ceil((double)effectiveButtonCount / foldersPerRow);
        cachedFolderCount = folderCount;
        return cachedRows;
    }
    
    /**
     * Updates vertical positions for folder names and bookmark display
     * 
     * @param folderCount Number of folders (excluding the add button)
     */
    public void updateLayoutPositions(int folderCount) {
        int rows = calculateRows(folderCount);
        
        nameYOffset = rows * FOLDER_SPACING_Y;
        calculatedNameY = PADDING_Y + nameYOffset + 5;
        calculatedBookmarkDisplayY = calculatedNameY + 10;
        
        // Update the bookmark display positions in the unified manager
        folderManager.setBookmarkDisplayPositions(calculatedNameY, calculatedBookmarkDisplayY);
        
        // Invalidate caches that depend on these positions
        deleteButtonCacheValid = false;
        exclusionZoneCacheValid = false;
        
        ModLogger.debug("Updated layout positions: nameY={}, bookmarkDisplayY={}", 
                      calculatedNameY, calculatedBookmarkDisplayY);
    }
    
    /**
     * Calculate the grid width based on current folders per row
     * 
     * @return Width of the folder grid
     */
    private int calculateGridWidth() {
        if (cachedGridWidth > 0) {
            return cachedGridWidth;
        }
        
        cachedGridWidth = foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
        return cachedGridWidth;
    }
    
    /**
     * Calculates the delete button position based on screen dimensions
     * 
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateDeleteButtonPosition() {
        if (deleteButtonCacheValid) {
            return cachedDeleteButtonPosition;
        }
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            cachedDeleteButtonPosition[0] = -1;
            cachedDeleteButtonPosition[1] = -1;
            return cachedDeleteButtonPosition;
        }
        
        // Use cached maxExclusionWidth if available
        int maxExclusionWidth = cachedMaxExclusionWidth > 0 ? 
            cachedMaxExclusionWidth : LayoutConstants.calculateMaxWidthBeforeGui(minecraft.getWindow().getGuiScaledWidth());
            
        maxExclusionWidth = Math.max(40, maxExclusionWidth - 10);
        
        // Calculate exclusion width
        int exclusionWidth = Math.min(maxExclusionWidth, ICON_WIDTH + (EXCLUSION_PADDING * 2));
        if (foldersPerRow > 1) {
            int gridWidth = calculateGridWidth();
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    Math.max(exclusionWidth, gridWidth + (EXCLUSION_PADDING * 2)));
        }
        
        // Position the delete button at the right edge of the exclusion zone
        cachedDeleteButtonPosition[0] = 5 + exclusionWidth - 16 - 5;
        cachedDeleteButtonPosition[1] = calculatedNameY - 4;
        
        deleteButtonCacheValid = true;
        return cachedDeleteButtonPosition;
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
            folderCount == cachedFolderCount &&
            foldersVisible == lastFoldersVisible && 
            hasActiveFolder == lastHasActiveFolder &&
            bookmarkDisplayHeight == lastBookmarkDisplayHeight) {
            return exclusionZone;
        }
        
        // Update cache tracking variables
        lastFoldersVisible = foldersVisible;
        lastHasActiveFolder = hasActiveFolder;
        lastBookmarkDisplayHeight = bookmarkDisplayHeight;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            exclusionZone = new Rect2i(0, 0, 0, 0);
            exclusionZoneCacheValid = true;
            return exclusionZone;
        }

        // Use cached max exclusion width if available
        int maxExclusionWidth = cachedMaxExclusionWidth > 0 ? 
            cachedMaxExclusionWidth : LayoutConstants.calculateMaxWidthBeforeGui(minecraft.getWindow().getGuiScaledWidth());
        
        // Calculate width of the exclusion zone
        int exclusionWidth;
        
        if (foldersVisible && folderCount > 0 && foldersPerRow > 1) {
            // For multiple folders, calculate based on the grid width
            int gridWidth = calculateGridWidth();
            
            // Add padding to the calculated width
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    gridWidth + (2 * EXCLUSION_PADDING));
        } else {
            // For single column or no folders, just use the button width plus padding
            exclusionWidth = Math.min(maxExclusionWidth, 
                                    ICON_WIDTH + (2 * EXCLUSION_PADDING));
        }

        // Calculate the height of the exclusion zone
        int exclusionHeight;
        
        if (foldersVisible && folderCount > 0) {
            // Use cached rows if available
            int rows = calculateRows(folderCount);
            
            // Height should include the rows of buttons
            int buttonsHeight = rows * FOLDER_SPACING_Y;
            
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
            exclusionHeight = ICON_HEIGHT + (2 * EXCLUSION_PADDING);
        }
        
        // Create the exclusion zone
        exclusionZone = new Rect2i(0, 0, exclusionWidth, exclusionHeight);
        
        // Update the ExclusionHandler
        updateExclusionHandler(exclusionZone);
        
        exclusionZoneCacheValid = true;
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
     * Creates and positions folder buttons based on the data from folder manager
     * 
     * @return List of created and positioned folder buttons
     */
    public List<FolderButton> createAndPositionFolderButtons() {
        List<FolderButton> buttons = new ArrayList<>();
        
        // Get all folders from the folder manager
        List<FolderDataRepresentation> folders = folderManager.loadAllFolders();
        
        // Create an "Add Folder" button at index 0
        int[] addPos = calculateAddButtonPosition();
        FolderButton addButton = new FolderButton(addPos[0], addPos[1], FolderButton.ButtonType.ADD);
        addButton.setClickHandler(folderManager::handleAddFolderButtonClick);
        buttons.add(addButton);
        
        // Create and position normal folder buttons
        int buttonIndex = 1;
        for (FolderDataRepresentation folder : folders) {
            int[] pos = calculateFolderPosition(buttonIndex);
            FolderButton button = new FolderButton(pos[0], pos[1], folder);
            button.setClickHandler(folderManager::fireFolderClickedEvent);
            buttons.add(button);
            buttonIndex++;
        }
        
        // Update layout positions based on folder count
        updateLayoutPositions(folders.size());
        
        // Update the unified folder manager with the new buttons
        folderManager.setFolderButtons(buttons);
        
        return buttons;
    }
    
    /**
     * Gets the position for the folder name display
     * 
     * @return The Y coordinate for the folder name
     */
    public int getFolderNameY() {
        return calculatedNameY;
    }
    
    /**
     * Gets the position for the bookmark display
     * 
     * @return The Y coordinate for the bookmark display
     */
    public int getBookmarkDisplayY() {
        return calculatedBookmarkDisplayY;
    }
}