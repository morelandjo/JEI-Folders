package com.jeifolders.gui.layout;

import com.jeifolders.data.Folder;
import com.jeifolders.gui.common.ExclusionHandler;
import com.jeifolders.gui.common.LayoutConstants;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.buttons.FolderButtonTextures;
import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.core.FolderManager;
import com.jeifolders.ui.display.BookmarkDisplayManager;
import com.jeifolders.ui.interaction.FolderInteractionHandler;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized service for managing all UI layout calculations.
 * Handles positioning of folders, buttons, and other UI elements.
 * Provides a responsive layout that adapts to screen size changes.
 */
public class FolderLayoutService {
    // Singleton instance
    private static FolderLayoutService instance;

    // Layout constants
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 10;
    private static final int ICON_WIDTH = FolderButtonTextures.ICON_WIDTH;
    private static final int ICON_HEIGHT = FolderButtonTextures.ICON_HEIGHT;
    private static final int FOLDER_SPACING_Y = 30;
    private static final int FOLDER_SPACING_X = 2;
    private static final int EXCLUSION_PADDING = 10;
    private static final int LAYOUT_RECALC_INTERVAL_MS = 1000;

    // Screen dimensions caching
    private int cachedScreenWidth = -1;
    private int cachedScreenHeight = -1;
    private long lastCalculationTime = 0;

    // Layout calculations
    private int foldersPerRow = 1;
    private int cachedFolderCount = -1;
    private int cachedRows = -1;
    private int cachedGridWidth = -1;
    private int cachedMaxExclusionWidth = -1;

    // Position caching
    private int[] cachedPositions = new int[100 * 2]; // Cache for up to 100 folder positions
    private boolean positionsCacheValid = false;
    
    // Calculated positions
    private int calculatedNameY = -1;
    private int calculatedBookmarkDisplayY = -1;
    private int nameYOffset = 0;
    private int[] cachedDeleteButtonPosition = new int[2];
    private boolean deleteButtonCacheValid = false;
    
    // Exclusion zone
    private final ExclusionHandler exclusionHandler = new ExclusionHandler();
    private Rect2i exclusionZone = new Rect2i(0, 0, 0, 0);
    private boolean exclusionZoneCacheValid = false;
    private boolean lastFoldersVisible = true;
    private boolean lastHasActiveFolder = false;
    private int lastBookmarkDisplayHeight = 0;
    
    // State
    private boolean needsRebuild = true;
    
    // Component architecture
    private FolderManager folderManager;
    private BookmarkDisplayManager displayManager;
    private FolderInteractionHandler interactionHandler;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderLayoutService() {
        calculateInitialLayout();
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderLayoutService getInstance() {
        if (instance == null) {
            instance = new FolderLayoutService();
        }
        return instance;
    }
    
    /**
     * Initialize the layout service with the component architecture
     */
    public static void init(FolderManager folderManager) {
        getInstance().setComponents(folderManager);
        ModLogger.debug("FolderLayoutService initialized with component architecture");
    }
    
    /**
     * Backwards compatibility init
     */
    public static void init() {
        getInstance();
        ModLogger.debug("FolderLayoutService initialized (legacy mode)");
    }
    
    /**
     * Sets the component architecture dependencies
     */
    public void setComponents(FolderManager folderManager) {
        this.folderManager = folderManager;
        this.displayManager = folderManager.getDisplayManager();
        this.interactionHandler = folderManager.getInteractionHandler();
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
        // Reduce the vertical offset from 35 to 25 to position the bookmarks closer to the folder name
        calculatedBookmarkDisplayY = calculatedNameY + 25;
        
        // Debug logging for position calculation
        ModLogger.debug("[POSITION-DEBUG] FolderLayoutService calculated positions: rows={}, nameYOffset={}, nameY={}, bookmarkDisplayY={}", 
                      rows, nameYOffset, calculatedNameY, calculatedBookmarkDisplayY);
        
        // Update the bookmark display positions directly in the display manager
        if (displayManager != null) {
            displayManager.setBookmarkDisplayPositions(calculatedNameY, calculatedBookmarkDisplayY);
            ModLogger.debug("[POSITION-DEBUG] Propagated positions to BookmarkDisplayManager");
        } else {
            ModLogger.debug("[POSITION-DEBUG] Cannot update display positions - displayManager is null");
        }
        
        // Invalidate caches that depend on these positions
        deleteButtonCacheValid = false;
        exclusionZoneCacheValid = false;
    }
    
    /**
     * Updates vertical positions for folder names and bookmark display without recalculating rows
     */
    public void updateLayoutPositions() {
        // Calculate folder name and bookmark display positions
        calculatedNameY = PADDING_Y + nameYOffset + 5;
        // Reduce the vertical offset from 35 to 25 to position the bookmarks closer to the folder name
        calculatedBookmarkDisplayY = calculatedNameY + 25;
        
        ModLogger.debug("[POSITION-DEBUG-TRACE] Calculated positions in FolderLayoutService: nameY={}, displayY={}, nameYOffset={}", 
                      calculatedNameY, calculatedBookmarkDisplayY, nameYOffset);
        
        // Propagate these positions to the bookmark display manager
        if (displayManager != null) {
            displayManager.setBookmarkDisplayPositions(calculatedNameY, calculatedBookmarkDisplayY);
        }
        
        // Invalidate dependent caches
        deleteButtonCacheValid = false;
        exclusionZoneCacheValid = false;
    }
    
    /**
     * Forces a recalculation of all layout positions and propagates them to all components
     */
    public void forcePositionRecalculation() {
        ModLogger.debug("[POSITION-DEBUG-TRACE] Force position recalculation requested");
        invalidateAllCaches();
        calculateFoldersPerRow();
        updateLayoutPositions();
        
        // Also ensure the BookmarkDisplayManager updates its display
        if (displayManager != null && folderManager != null && folderManager.getUIStateManager().hasActiveFolder()) {
            displayManager.refreshActiveFolder(true);
            ModLogger.debug("[POSITION-DEBUG-TRACE] Forced refresh of active folder display");
            ModLogger.debug("[POSITION-DEBUG-TRACE] Position recalculation completed with current folders per row: {}", foldersPerRow);
        } else {
            ModLogger.debug("[POSITION-DEBUG-TRACE] No active folder to refresh or components are null");
        }
    }
    
    /**
     * Forces a complete layout update including:
     * - Cache invalidation
     * - Recalculating folders per row
     * - Updating positions
     * - Refreshing the exclusion zone
     * - Refreshing the active folder display
     * 
     * Use this method when the UI needs a complete refresh
     */
    public void forceLayoutUpdate() {
        ModLogger.debug("[POSITION-DEBUG-TRACE] Force layout update requested");
        
        // First invalidate all caches
        invalidateAllCaches();
        
        // Recalculate fundamental layout parameters
        calculateFoldersPerRow();
        
        // Update the positions for UI elements
        updateLayoutPositions();
        
        // Update the exclusion zone
        if (folderManager != null) {
            // Get the number of folder buttons (excluding the add button)
            int folderCount = folderManager.getStorageService().getAllFolders().size();
            boolean foldersVisible = folderManager.getUIStateManager().areFoldersVisible();
            boolean hasActiveFolder = folderManager.getUIStateManager().hasActiveFolder();
            
            int bookmarkDisplayHeight = 0;
            if (displayManager != null && displayManager.getBookmarkDisplay() != null) {
                bookmarkDisplayHeight = displayManager.getBookmarkDisplay().getHeight();
            }
            
            // Update the exclusion zone
            updateExclusionZone(folderCount, foldersVisible, hasActiveFolder, bookmarkDisplayHeight);
            ModLogger.debug("[POSITION-DEBUG-TRACE] Updated exclusion zone - width: {}, height: {}", 
                           exclusionZone.getWidth(), exclusionZone.getHeight());
        } else {
            ModLogger.debug("[POSITION-DEBUG-TRACE] Could not update exclusion zone - folderManager is null");
        }
        
        // Refresh active folder display if needed
        if (displayManager != null && folderManager != null && folderManager.getUIStateManager().hasActiveFolder()) {
            displayManager.refreshActiveFolder(true);
            ModLogger.debug("[POSITION-DEBUG-TRACE] Refreshed active folder display");
        }
        
        // Mark as needing rebuild to ensure full UI refresh
        markNeedsRebuild();
        ModLogger.debug("[POSITION-DEBUG-TRACE] Force layout update completed with folders per row: {}", foldersPerRow);
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
     * Complete update of the exclusion zone and related UI elements.
     * This consolidates all exclusion zone management in one place.
     * 
     * @return The updated exclusion zone
     */
    public Rect2i updateExclusionZoneAndUI() {
        // Calculate button count and state information
        int buttonCount = 0;
        boolean foldersVisible = true;
        boolean hasActiveFolder = false;
        int bookmarkDisplayHeight = 0;
        
        if (folderManager != null) {
            // Access folder buttons through UIStateManager
            buttonCount = folderManager.getUIStateManager().getFolderButtons().size();
            foldersVisible = folderManager.getUIStateManager().areFoldersVisible();
            hasActiveFolder = folderManager.getUIStateManager().getActiveFolder() != null;
            
            if (hasActiveFolder && displayManager != null) {
                // Access the bookmark display height through the display manager
                var bookmarkDisplay = displayManager.getBookmarkDisplay();
                if (bookmarkDisplay != null) {
                    bookmarkDisplayHeight = bookmarkDisplay.getHeight();
                }
            }
        }
        
        // Update the exclusion zone
        Rect2i lastDrawnArea = updateExclusionZone(
            buttonCount, 
            foldersVisible, 
            hasActiveFolder,
            bookmarkDisplayHeight
        );
        
        // Update bookmark display bounds if active
        if (hasActiveFolder && displayManager != null && displayManager.getBookmarkDisplay() != null) {
            var bookmarkDisplay = displayManager.getBookmarkDisplay();
            bookmarkDisplay.updateBoundsFromCalculatedPositions();
        }
        
        return lastDrawnArea;
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
        
        if (folderManager == null) {
            ModLogger.warn("Cannot create folder buttons: folderManager is null");
            return buttons;
        }
        
        // Get all folders from the folder manager
        List<Folder> folders = folderManager.getStorageService().getAllFolders();
        
        // Create an "Add Folder" button at index 0
        int[] addPos = calculateAddButtonPosition();
        FolderButton addButton = new FolderButton(addPos[0], addPos[1], FolderButton.ButtonType.ADD);
        
        // Use the interaction handler directly
        // Add button needs to pass null as folder since it doesn't have one
        addButton.setClickHandler(folder -> {
            if (interactionHandler != null) {
                interactionHandler.handleAddFolderButtonClick(null);
            }
        });
        buttons.add(addButton);
        
        // Create and position normal folder buttons
        int buttonIndex = 1;
        for (Folder folder : folders) {
            int[] pos = calculateFolderPosition(buttonIndex);
            FolderButton button = new FolderButton(pos[0], pos[1], folder);
            
            // Use the interaction handler directly
            button.setClickHandler(folderClicked -> {
                if (interactionHandler != null) {
                    interactionHandler.handleFolderClick(folderClicked);
                }
            });
            buttons.add(button);
            buttonIndex++;
        }
        
        // Update layout positions based on folder count
        updateLayoutPositions(folders.size());
        
        // Update the folder manager with the new buttons
        folderManager.getUIStateManager().setFolderButtons(buttons);
        
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