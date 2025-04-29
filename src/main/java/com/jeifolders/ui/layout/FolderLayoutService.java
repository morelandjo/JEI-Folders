package com.jeifolders.ui.layout;

import com.jeifolders.data.Folder;
import com.jeifolders.core.FolderManager;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.display.BookmarkDisplayManager;
import com.jeifolders.ui.interaction.FolderInteractionHandler;
import com.jeifolders.ui.util.ExclusionHandler;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized service for managing all UI layout calculations.
 * Delegates core responsibilities to specialized components:
 * - LayoutCalculator: Core position/dimension calculations
 * - ExclusionManager: Manages JEI exclusion zones
 * - LayoutCacheService: Handles caching of layout data
 */
public class FolderLayoutService {
    // Singleton instance
    private static FolderLayoutService instance;
    
    // Component architecture - new structure
    private final LayoutCalculator layoutCalculator;
    private final ExclusionManager exclusionManager;
    private final LayoutCacheService cacheService;
    
    // Calculated positions
    private int calculatedNameY = -1;
    private int calculatedBookmarkDisplayY = -1;
    private int nameYOffset = 0;
    
    // State
    private boolean needsRebuild = true;
    
    // Component architecture - backward compatibility
    private FolderManager folderManager;
    private BookmarkDisplayManager displayManager;
    private FolderInteractionHandler interactionHandler;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderLayoutService() {
        // Initialize new components
        this.layoutCalculator = new LayoutCalculator();
        this.cacheService = new LayoutCacheService();
        this.exclusionManager = new ExclusionManager(layoutCalculator);
        
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
        layoutCalculator.calculateFoldersPerRow();
    }
    
    /**
     * Checks if screen dimensions have changed requiring a layout recalculation
     * 
     * @return true if screen dimensions have changed or cache is invalid
     */
    public boolean needsRecalculation() {
        return cacheService.needsRecalculation();
    }
    
    /**
     * Invalidates all cached calculations
     */
    public void invalidateAllCaches() {
        cacheService.invalidateAllCaches();
        exclusionManager.invalidateCache();
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
        // Delegate to the layout calculator
        layoutCalculator.calculateFoldersPerRow();
    }
    
    /**
     * Gets the current number of folders per row
     * 
     * @return The current number of folders that can fit in a row
     */
    public int getFoldersPerRow() {
        return layoutCalculator.getFoldersPerRow();
    }
    
    /**
     * Calculates folder button position based on its index in the grid.
     * 
     * @param index The index of the folder (0 is for the add button)
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateFolderPosition(int index) {
        // Use cached value if possible
        int[] cachedPosition = cacheService.getCachedPosition(index);
        if (cachedPosition != null) {
            return cachedPosition;
        }
        
        // Calculate via the layout calculator
        int[] position = layoutCalculator.calculateFolderPosition(index);
        
        // Cache the result
        cacheService.setCachedPosition(index, position[0], position[1]);
        
        return position;
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
        // Use cached value if possible
        if (cacheService.hasRowsCached(folderCount)) {
            return cacheService.getCachedRows();
        }
        
        int rows = layoutCalculator.calculateRows(folderCount);
        cacheService.setCachedRows(folderCount, rows);
        return rows;
    }
    
    /**
     * Updates vertical positions for folder names and bookmark display
     * 
     * @param folderCount Number of folders (excluding the add button)
     */
    public void updateLayoutPositions(int folderCount) {
        int rows = calculateRows(folderCount);
        
        // Calculate vertical positions
        nameYOffset = rows * layoutCalculator.getFolderSpacingY();
        calculatedNameY = layoutCalculator.calculateFolderNameY(rows);
        calculatedBookmarkDisplayY = layoutCalculator.calculateBookmarkDisplayY(calculatedNameY);
        
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
        cacheService.invalidateDeleteButtonCache();
        exclusionManager.invalidateCache();
    }
    
    /**
     * Updates vertical positions for folder names and bookmark display without recalculating rows
     */
    public void updateLayoutPositions() {
        // Calculate folder name and bookmark display positions
        calculatedNameY = layoutCalculator.getPaddingY() + nameYOffset + 5;
        calculatedBookmarkDisplayY = layoutCalculator.calculateBookmarkDisplayY(calculatedNameY);
        
        ModLogger.debug("[POSITION-DEBUG-TRACE] Calculated positions in FolderLayoutService: nameY={}, displayY={}, nameYOffset={}", 
                      calculatedNameY, calculatedBookmarkDisplayY, nameYOffset);
        
        // Propagate these positions to the bookmark display manager
        if (displayManager != null) {
            displayManager.setBookmarkDisplayPositions(calculatedNameY, calculatedBookmarkDisplayY);
        }
        
        // Invalidate dependent caches
        cacheService.invalidateDeleteButtonCache();
        exclusionManager.invalidateCache();
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
            ModLogger.debug("[POSITION-DEBUG-TRACE] Position recalculation completed with current folders per row: {}", layoutCalculator.getFoldersPerRow());
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
        
        // First invalidate all caches and recalculate basic layout parameters
        resetAndRecalculateLayout();
        
        // Update the exclusion zone if possible
        updateExclusionZoneWithCurrentState();
        
        // Refresh active folder display if needed
        refreshActiveFolderDisplayIfNeeded();
        
        // Mark as needing rebuild to ensure full UI refresh
        markNeedsRebuild();
        ModLogger.debug("[POSITION-DEBUG-TRACE] Force layout update completed with folders per row: {}", layoutCalculator.getFoldersPerRow());
    }
    
    /**
     * Resets caches and recalculates basic layout parameters
     */
    private void resetAndRecalculateLayout() {
        // First invalidate all caches
        invalidateAllCaches();
        
        // Recalculate fundamental layout parameters
        calculateFoldersPerRow();
        
        // Update the positions for UI elements
        updateLayoutPositions();
    }
    
    /**
     * Updates the exclusion zone using current UI state
     */
    private void updateExclusionZoneWithCurrentState() {
        if (folderManager == null) {
            ModLogger.debug("[POSITION-DEBUG-TRACE] Could not update exclusion zone - folderManager is null");
            return;
        }
        
        // Get the number of folder buttons (excluding the add button)
        int folderCount = folderManager.getStorageService().getAllFolders().size();
        boolean foldersVisible = folderManager.getUIStateManager().areFoldersVisible();
        boolean hasActiveFolder = folderManager.getUIStateManager().hasActiveFolder();
        
        int bookmarkDisplayHeight = getBookmarkDisplayHeight();
        
        // Update the exclusion zone
        exclusionManager.updateExclusionZone(folderCount, foldersVisible, hasActiveFolder, bookmarkDisplayHeight);
        
        ModLogger.debug("[POSITION-DEBUG-TRACE] Updated exclusion zone - width: {}, height: {}", 
                       exclusionManager.getExclusionZone().getWidth(), 
                       exclusionManager.getExclusionZone().getHeight());
    }
    
    /**
     * Gets the current bookmark display height if available, or 0 if not
     * 
     * @return The height of the bookmark display, or 0 if not available
     */
    private int getBookmarkDisplayHeight() {
        if (displayManager != null && displayManager.getBookmarkDisplay() != null) {
            return displayManager.getBookmarkDisplay().getHeight();
        }
        return 0;
    }
    
    /**
     * Refreshes the active folder display if there is an active folder
     */
    private void refreshActiveFolderDisplayIfNeeded() {
        if (displayManager == null || folderManager == null) {
            return;
        }
        
        if (folderManager.getUIStateManager().hasActiveFolder()) {
            displayManager.refreshActiveFolder(true);
            ModLogger.debug("[POSITION-DEBUG-TRACE] Refreshed active folder display");
        }
    }
    
    /**
     * Calculate the grid width based on current folders per row
     * 
     * @return Width of the folder grid
     */
    private int calculateGridWidth() {
        // Use cached value if possible
        int cachedGridWidth = cacheService.getCachedGridWidth();
        if (cachedGridWidth > 0) {
            return cachedGridWidth;
        }
        
        int gridWidth = layoutCalculator.calculateGridWidth();
        cacheService.setCachedGridWidth(gridWidth);
        return gridWidth;
    }
    
    /**
     * Calculates the delete button position based on screen dimensions
     * 
     * @return int[] array with [x, y] coordinates
     */
    public int[] calculateDeleteButtonPosition() {
        // Use cached value if possible
        int[] cachedPosition = cacheService.getCachedDeleteButtonPosition();
        if (cachedPosition != null) {
            return cachedPosition;
        }
        
        // Calculate through the layout calculator
        int gridWidth = calculateGridWidth();
        int[] position = layoutCalculator.calculateDeleteButtonPosition(calculatedNameY, gridWidth);
        
        // Cache the result
        cacheService.setCachedDeleteButtonPosition(position[0], position[1]);
        
        return position;
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
        // Delegate to the exclusion manager
        return exclusionManager.updateExclusionZone(
            folderCount, foldersVisible, hasActiveFolder, bookmarkDisplayHeight);
    }
    
    /**
     * Complete update of the exclusion zone and related UI elements.
     * This consolidates all exclusion zone management in one place.
     * 
     * @return The updated exclusion zone
     */
    public Rect2i updateExclusionZoneAndUI() {
        // Gather current UI state information
        UIStateInfo stateInfo = gatherUIStateInfo();
        
        // Update the exclusion zone via the manager
        Rect2i lastDrawnArea = exclusionManager.updateExclusionZone(
            stateInfo.buttonCount, 
            stateInfo.foldersVisible, 
            stateInfo.hasActiveFolder,
            stateInfo.bookmarkDisplayHeight
        );
        
        // Update bookmark display bounds if necessary
        updateBookmarkDisplayBoundsIfNeeded(stateInfo.hasActiveFolder);
        
        return lastDrawnArea;
    }
    
    /**
     * Gathers current UI state information needed for exclusion zone calculations
     * 
     * @return A UIStateInfo object containing current state
     */
    private UIStateInfo gatherUIStateInfo() {
        UIStateInfo info = new UIStateInfo();
        
        if (folderManager != null) {
            // Access folder buttons through UIStateManager
            info.buttonCount = folderManager.getUIStateManager().getFolderButtons().size();
            info.foldersVisible = folderManager.getUIStateManager().areFoldersVisible();
            info.hasActiveFolder = folderManager.getUIStateManager().getActiveFolder() != null;
            
            // Get bookmark display height if available
            if (info.hasActiveFolder) {
                info.bookmarkDisplayHeight = getBookmarkDisplayHeight();
            }
        }
        
        return info;
    }
    
    /**
     * Updates bookmark display bounds if there is an active folder with a display
     * 
     * @param hasActiveFolder Whether there's an active folder
     */
    private void updateBookmarkDisplayBoundsIfNeeded(boolean hasActiveFolder) {
        if (!hasActiveFolder || displayManager == null) {
            return;
        }
        
        var bookmarkDisplay = displayManager.getBookmarkDisplay();
        if (bookmarkDisplay != null) {
            bookmarkDisplay.updateBoundsFromCalculatedPositions();
        }
    }
    
    /**
     * Simple data class to hold UI state information for exclusion zone calculations
     */
    private static class UIStateInfo {
        int buttonCount = 0;
        boolean foldersVisible = true;
        boolean hasActiveFolder = false;
        int bookmarkDisplayHeight = 0;
    }
    
    /**
     * Gets the current exclusion zone
     * 
     * @return The current exclusion zone as a Rect2i
     */
    public Rect2i getExclusionZone() {
        return exclusionManager.getExclusionZone();
    }
    
    /**
     * Gets the exclusion handler for JEI integration
     */
    public ExclusionHandler getExclusionHandler() {
        return exclusionManager.getExclusionHandler();
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
        
        // Create and add buttons
        createAddButton(buttons);
        createFolderButtons(folders, buttons);
        
        // Update layout positions based on folder count
        updateLayoutPositions(folders.size());
        
        // Update the folder manager with the new buttons
        folderManager.getUIStateManager().setFolderButtons(buttons);
        
        return buttons;
    }
    
    /**
     * Creates and adds the "Add Folder" button to the buttons list
     * 
     * @param buttons The list to add the button to
     */
    private void createAddButton(List<FolderButton> buttons) {
        int[] addPos = calculateAddButtonPosition();
        FolderButton addButton = new FolderButton(addPos[0], addPos[1], FolderButton.ButtonType.ADD);
        
        // Set up click handler for the add button
        configureAddButtonClickHandler(addButton);
        
        buttons.add(addButton);
    }
    
    /**
     * Configures the click handler for the Add Folder button
     * 
     * @param addButton The Add Folder button to configure
     */
    private void configureAddButtonClickHandler(FolderButton addButton) {
        // Add button needs to pass null as folder since it doesn't have one
        addButton.setClickHandler(folder -> {
            if (interactionHandler != null) {
                interactionHandler.handleAddFolderButtonClick(null);
            }
        });
    }
    
    /**
     * Creates and adds buttons for each folder to the buttons list
     * 
     * @param folders The folders to create buttons for
     * @param buttons The list to add the buttons to
     */
    private void createFolderButtons(List<Folder> folders, List<FolderButton> buttons) {
        int buttonIndex = 1;
        
        for (Folder folder : folders) {
            // Create a button for this folder
            FolderButton button = createFolderButton(folder, buttonIndex);
            buttons.add(button);
            buttonIndex++;
        }
    }
    
    /**
     * Creates a button for a specific folder
     * 
     * @param folder The folder to create a button for
     * @param buttonIndex The index for position calculation
     * @return The created FolderButton
     */
    private FolderButton createFolderButton(Folder folder, int buttonIndex) {
        int[] pos = calculateFolderPosition(buttonIndex);
        FolderButton button = new FolderButton(pos[0], pos[1], folder);
        
        // Set up click handler for the folder button
        configureFolderButtonClickHandler(button);
        
        return button;
    }
    
    /**
     * Configures the click handler for a folder button
     * 
     * @param button The folder button to configure
     */
    private void configureFolderButtonClickHandler(FolderButton button) {
        button.setClickHandler(folderClicked -> {
            if (interactionHandler != null) {
                interactionHandler.handleFolderClick(folderClicked);
            }
        });
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
    
    /**
     * Gets the layout calculator
     */
    public LayoutCalculator getLayoutCalculator() {
        return layoutCalculator;
    }
    
    /**
     * Gets the exclusion manager
     */
    public ExclusionManager getExclusionManager() {
        return exclusionManager;
    }
    
    /**
     * Gets the cache service
     */
    public LayoutCacheService getCacheService() {
        return cacheService;
    }
}