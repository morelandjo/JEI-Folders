package com.jeifolders.ui.display;

import com.jeifolders.core.FolderManager;
import com.jeifolders.data.Folder;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.ui.components.contents.FolderContentsView;
import com.jeifolders.ui.util.RefreshCoordinator;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages bookmark display functionality.
 * Handles creating, updating and displaying bookmark contents.
 */
public class BookmarkDisplayManager {
    private final FolderManager folderManager;
    private FolderContentsView bookmarkDisplay;
    
    // Track last refresh time for performance optimization
    private long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL_MS = 250; // Increased from 100ms to 250ms
    
    // Get reference to the refresh coordinator
    private final RefreshCoordinator refreshCoordinator = RefreshCoordinator.getInstance();
    
    // Component ID for refresh coordination
    private static final String COMPONENT_ID = "BookmarkDisplayManager";
    
    // Add recursion guard to prevent stack overflow
    private static boolean updatingBookmarkContents = false;
    
    // Track the last folder ID that was refreshed for additional debouncing
    private Integer lastRefreshedFolderId = null;
    private int consecutiveRefreshes = 0;
    private static final int MAX_CONSECUTIVE_REFRESHES = 3;
    
    /**
     * Constructor for BookmarkDisplayManager
     * 
     * @param folderManager Reference to the central folder manager
     */
    public BookmarkDisplayManager(FolderManager folderManager) {
        this.folderManager = folderManager;
    }
    
    /**
     * Creates a new bookmark display
     * @param preserveIngredients Whether to preserve the current ingredients if possible
     * @return true if the display was successfully created
     */
    public boolean createBookmarkDisplay(boolean preserveIngredients) {
        // Save current ingredients if requested
        List<TypedIngredient> savedIngredients = new ArrayList<>();
        if (preserveIngredients && bookmarkDisplay != null) {
            savedIngredients = TypedIngredientHelper.getIngredientsFromDisplay(bookmarkDisplay);
        }
        
        // Create the unified display
        Optional<FolderContentsView> displayOpt = FolderContentsView.create(
            folderManager.getStorageService(),
            folderManager.getEventDispatcher(),
            this,
            folderManager.getInteractionHandler());
        
        if (displayOpt.isPresent()) {
            bookmarkDisplay = displayOpt.get();
            
            // Make sure the display knows about any active folder and has bounds set
            if (folderManager.getUIStateManager().hasActiveFolder()) {
                var activeFolder = folderManager.getUIStateManager().getActiveFolder();
                bookmarkDisplay.setActiveFolder(activeFolder.getFolder());

                // Get the current layout positions from the layout service
                var layoutService = folderManager.getLayoutService();
                int nameY = layoutService.getFolderNameY();
                int bookmarkDisplayY = layoutService.getBookmarkDisplayY();
                
                // Set positions before updating bounds
                bookmarkDisplay.setCalculatedPositions(nameY, bookmarkDisplayY);
                bookmarkDisplay.updateBoundsFromCalculatedPositions();
                
                // Apply the saved ingredients if we have them
                if (preserveIngredients && !savedIngredients.isEmpty()) {
                    ModLogger.debug("Applying {} preserved ingredients during display creation", 
                        savedIngredients.size());
                    TypedIngredientHelper.setIngredientsOnDisplay(bookmarkDisplay, savedIngredients);
                }
                // Otherwise, apply cached bookmark contents if available
                else {
                    var cachedContents = folderManager.getUIStateManager().getBookmarkContentsCache();
                    if (!cachedContents.isEmpty()) {
                        ModLogger.debug("Applying {} cached bookmark items during display creation", 
                            cachedContents.size());
                        TypedIngredientHelper.setIngredientsOnDisplay(bookmarkDisplay, cachedContents);
                    }
                }
            }
            return true;
        } else {
            ModLogger.error("Failed to create bookmark display");
            return false;
        }
    }
    
    /**
     * Safely updates the bookmark contents.
     */
    public void safeUpdateBookmarkContents() {
        // Prevent recursion
        if (updatingBookmarkContents) {
            return;
        }
        
        try {
            updatingBookmarkContents = true;
            if (bookmarkDisplay != null) {
                // Use the helper to get ingredients from the display
                List<TypedIngredient> bookmarkContents = TypedIngredientHelper.getIngredientsFromDisplay(bookmarkDisplay);
                
                // Update cache
                folderManager.updateBookmarkContentsCache(bookmarkContents);
            }
        } finally {
            updatingBookmarkContents = false;
        }
    }
    
    /**
     * Centralized method for refreshing bookmark displays.
     * This method is the single source of truth for bookmark refresh operations.
     * 
     * @param folder The folder to refresh contents for
     * @param forceLayout Whether to force layout update
     * @return true if the refresh was successful
     */
    public boolean refreshFolderBookmarks(Folder folder, boolean forceLayout) {
        if (folder == null) {
            ModLogger.debug("[REFRESH-DEBUG] Cannot refresh bookmarks - no folder provided");
            return false;
        }

        int folderId = folder.getId();
        
        // First check with the global refresh coordinator if this refresh is allowed
        if (!refreshCoordinator.canRefreshFolder(folderId, forceLayout)) {
            return false;
        }

        // Signal that we're starting a refresh operation
        refreshCoordinator.beginRefresh();
        
        try {
            // Ensure we have a bookmark display
            if (bookmarkDisplay == null) {
                ModLogger.debug("[REFRESH-DEBUG] Creating new bookmark display during refresh");
                if (!createBookmarkDisplay(true)) {
                    return false;
                }
            }
            
            // Store the current page number before refreshing
            int currentPageNumber = bookmarkDisplay.getCurrentPageNumber();
            
            // Load bookmarks from the folder and convert to ingredients
            List<BookmarkIngredient> bookmarkIngredients = TypedIngredientHelper.convertToBookmarkIngredients(
                TypedIngredientHelper.loadBookmarksFromFolder(
                    folderManager.getStorageService(), folderId, true)
            );
            
            ModLogger.debug("[REFRESH-DEBUG] Loading {} bookmarks from folder ID {}", 
                bookmarkIngredients.size(), folderId);
            
            // Set active folder and ingredients
            bookmarkDisplay.setActiveFolder(folder);
            bookmarkDisplay.setIngredients(bookmarkIngredients);
            
            // Force layout update if requested
            if (forceLayout && bookmarkDisplay.getContentsImpl() != null) {
                bookmarkDisplay.getContentsImpl().updateLayout(true);
                
                // Restore pagination
                if (currentPageNumber > 1 && bookmarkDisplay.getPageCount() >= currentPageNumber) {
                    bookmarkDisplay.goToPage(currentPageNumber);
                    ModLogger.debug("[REFRESH-DEBUG] Restored pagination to page {} after refresh", currentPageNumber);
                }
            }
            
            // Update bounds
            bookmarkDisplay.updateBoundsFromCalculatedPositions();
            
            // Update cache for GUI rebuilds
            safeUpdateBookmarkContents();
            
            // Fire event
            folderManager.getEventDispatcher().fireDisplayRefreshedEvent(folder);
            
            ModLogger.debug("[REFRESH-DEBUG] Folder bookmarks refresh completed successfully for folder: {}", folder.getName());
            return true;
        } catch (Exception e) {
            ModLogger.error("[REFRESH-DEBUG] Error refreshing folder bookmarks: {}", e.getMessage(), e);
            return false;
        } finally {
            // Signal that we're done with the refresh operation
            refreshCoordinator.endRefresh();
        }
    }
    
    /**
     * Refreshes the current active folder's bookmarks
     * 
     * @param forceLayout Whether to force layout update
     * @return true if the refresh was successful
     */
    public boolean refreshActiveFolder(boolean forceLayout) {
        if (!folderManager.getUIStateManager().hasActiveFolder()) {
            ModLogger.debug("Cannot refresh active folder - no active folder");
            return false;
        }
        
        return refreshFolderBookmarks(
            folderManager.getUIStateManager().getActiveFolder().getFolder(), 
            forceLayout
        );
    }

    /**
     * Refreshes the bookmark display with the latest data
     * 
     * @return true if the refresh was successful
     */
    public boolean refreshBookmarkDisplay() {
        // Check if we have an active folder
        if (!folderManager.getUIStateManager().hasActiveFolder()) {
            ModLogger.debug("Cannot refresh bookmark display - no active folder");
            return false;
        }

        // Delegate to our centralized refresh method
        return refreshFolderBookmarks(
            folderManager.getUIStateManager().getActiveFolder().getFolder(), 
            true
        );
    }

    /**
     * Handles a click on the bookmark display
     * This method processes business logic and events after the view handles UI interactions
     *
     * @param mouseX The mouse X position
     * @param mouseY The mouse Y position
     * @param button The mouse button
     * @return true if the click was handled
     */
    public boolean handleBookmarkDisplayClick(double mouseX, double mouseY, int button) {
        if (bookmarkDisplay == null) {
            return false;
        }

        // First let the display handle the view-specific aspects like pagination
        // If it returns true, it means a UI element was clicked (like a pagination button)
        boolean handledByDisplay = bookmarkDisplay.handleClick(mouseX, mouseY, button);
        
        // If the UI handled it, we're done
        if (handledByDisplay) {
            return true;
        }
        
        // Check if the mouse is over the display area
        if (!bookmarkDisplay.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        
        // Check for ingredient clicks that need business logic processing
        Optional<String> bookmarkKey = bookmarkDisplay.getBookmarkKeyAt(mouseX, mouseY);
        if (bookmarkKey.isPresent() && folderManager.getUIStateManager().hasActiveFolder()) {
            String key = bookmarkKey.get();
            
            // Get the typed ingredient from the bookmark
            var typedIngredient = TypedIngredientHelper.getTypedIngredientForKey(key);
            if (typedIngredient != null) {
                // Fire bookmark clicked event
                folderManager.getEventDispatcher().fireBookmarkClickedEvent(typedIngredient);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Handles an ingredient being dropped on the bookmark display
     * 
     * @param mouseX The x position of the drop
     * @param mouseY The y position of the drop
     * @param ingredient The ingredient that was dropped
     * @return true if the drop was handled
     */
    public boolean handleIngredientDropOnDisplay(double mouseX, double mouseY, Object ingredient) {
        if (bookmarkDisplay == null) {
            ModLogger.debug("Cannot handle ingredient drop - no bookmark display");
            return false;
        }
        
        if (!bookmarkDisplay.isMouseOver(mouseX, mouseY)) {
            ModLogger.debug("Ingredient drop not over bookmark display");
            return false;
        }
        
        return bookmarkDisplay.handleIngredientDrop(mouseX, mouseY, ingredient);
    }

    /**
     * Sets the positions for the bookmark display
     * 
     * @param nameY The y position for the folder name
     * @param bookmarkDisplayY The y position for the bookmark display
     */
    public void setBookmarkDisplayPositions(int nameY, int bookmarkDisplayY) {
        ModLogger.debug("Setting bookmark display positions: nameY={}, bookmarkDisplayY={}", nameY, bookmarkDisplayY);
        
        if (bookmarkDisplay != null) {
            ModLogger.debug("Setting calculated positions on FolderContentsView");
            bookmarkDisplay.setCalculatedPositions(nameY, bookmarkDisplayY);
            
            // Force an immediate update of bounds after setting calculated positions
            bookmarkDisplay.updateBoundsFromCalculatedPositions();
            
            // Ensure the display is refreshed with the new positions
            // Only refresh if we have an active folder
            if (folderManager != null && folderManager.getUIStateManager().hasActiveFolder()) {
                ModLogger.debug("Refreshing active folder with new positions");
                refreshActiveFolder(true);
            }
            ModLogger.debug("Position update complete");
        } else {
            ModLogger.debug("Could not update positions - bookmarkDisplay is null");
            // Try to create the bookmark display since it's null
            createBookmarkDisplay(true);
            
            // If creation was successful, try setting positions again
            if (bookmarkDisplay != null) {
                ModLogger.debug("Created bookmarkDisplay, now setting positions");
                bookmarkDisplay.setCalculatedPositions(nameY, bookmarkDisplayY);
                bookmarkDisplay.updateBoundsFromCalculatedPositions();
            }
        }
    }
    
    /**
     * Gets the bookmark display, creating it if necessary
     */
    public FolderContentsView getBookmarkDisplay() {
        if (bookmarkDisplay == null) {
            createBookmarkDisplay(true);
        }
        return bookmarkDisplay;
    }
}