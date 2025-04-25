package com.jeifolders.gui.bookmarks;

import com.jeifolders.data.FolderDataService;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.folderButtons.FolderButton;
import com.jeifolders.gui.folderButtons.UnifiedFolderManager;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles high-level bookmark operations and display management.
 */
public class BookmarkManager {
    private final FolderDataService folderService;
    private UnifiedFolderContentsDisplay bookmarkDisplay;
    private final UnifiedFolderManager folderManager;

    // Track last refresh time for performance optimization
    private long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL_MS = 100; // Prevent too frequent refreshes

    public BookmarkManager(UnifiedFolderManager folderManager) {
        this.folderManager = folderManager;
        // Use the folder service directly from the folder manager
        this.folderService = folderManager.getFolderService();

        // Create the unified display
        createBookmarkDisplay();

        // Listen for folder activation/deactivation
        setupEventListeners();
    }

    /**
     * Set up listeners for the centralized event system
     */
    private void setupEventListeners() {
        // Listen for folder activation/deactivation
        folderManager.addEventListener(UnifiedFolderManager.EventType.FOLDER_ACTIVATED, event -> {
            FolderButton folderButton = event.getData("folderButton", FolderButton.class);
            onFolderActivationChanged(folderButton);
        });

        folderManager.addEventListener(UnifiedFolderManager.EventType.FOLDER_DEACTIVATED, event -> {
            onFolderActivationChanged(null);
        });

        // Listen for display refresh events to update the cache
        folderManager.addEventListener(UnifiedFolderManager.EventType.DISPLAY_REFRESHED, event -> {
            safeUpdateBookmarkContents();
        });

        // Listen for bookmark added events
        folderManager.addEventListener(UnifiedFolderManager.EventType.BOOKMARK_ADDED, event -> {
            // If this is for our active folder, make sure we update the cache
            Integer folderId = event.getFolderId();
            if (folderManager.hasActiveFolder() && folderId != null &&
                folderManager.getActiveFolder().getFolder().getId() == folderId) {
                ModLogger.debug("BookmarkManager received BOOKMARK_ADDED event, updating cache");
                safeUpdateBookmarkContents();
            }
        });
    }

    /**
     * Called when a folder is activated or deactivated
     */
    private void onFolderActivationChanged(FolderButton folderButton) {
        if (folderButton == null) {
            // Folder was deactivated
            if (bookmarkDisplay != null) {
                bookmarkDisplay.setActiveFolder(null);
            }
        } else {
            // Folder was activated
            if (bookmarkDisplay != null) {
                bookmarkDisplay.setActiveFolder(folderButton.getFolder());
                bookmarkDisplay.updateBoundsFromCalculatedPositions();

                // Update the bookmark contents cache
                safeUpdateBookmarkContents();
            }
        }
    }

    /**
     * Updates the static state cache for GUI rebuilds
     */
    private void safeUpdateBookmarkContents() {
        if (bookmarkDisplay != null) {
            // Use the helper to get ingredients from the display
            List<TypedIngredient> bookmarkContents = TypedIngredientHelper.getIngredientsFromDisplay(bookmarkDisplay);

            // Update the folder manager's cache
            folderManager.updateBookmarkContentsCache(bookmarkContents);
        }
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
        Optional<UnifiedFolderContentsDisplay> displayOpt = UnifiedFolderContentsDisplay.create(folderService);

        if (displayOpt.isPresent()) {
            bookmarkDisplay = displayOpt.get();

            // Make sure the display knows about any active folder and has bounds set
            if (folderManager.hasActiveFolder()) {
                FolderButton activeFolder = folderManager.getActiveFolder();
                bookmarkDisplay.setActiveFolder(activeFolder.getFolder());
                bookmarkDisplay.updateBoundsFromCalculatedPositions();

                // Apply the saved ingredients if we have them
                if (preserveIngredients && !savedIngredients.isEmpty()) {
                    ModLogger.debug("Applying {} preserved ingredients during display creation",
                        savedIngredients.size());
                    TypedIngredientHelper.setIngredientsOnDisplay(bookmarkDisplay, savedIngredients);
                }
                // Otherwise, apply cached bookmark contents if available
                else {
                    List<TypedIngredient> lastBookmarkContents = folderManager.getLastBookmarkContents();
                    if (!lastBookmarkContents.isEmpty()) {
                        ModLogger.debug("Applying {} cached bookmark items during display creation",
                            lastBookmarkContents.size());
                        TypedIngredientHelper.setIngredientsOnDisplay(bookmarkDisplay, lastBookmarkContents);
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
     * Legacy method that creates a display without preserving ingredients
     */
    public void createBookmarkDisplay() {
        createBookmarkDisplay(false);
    }

    /**
     * Refreshes the bookmark display with the latest data
     *
     * @return true if the refresh was successful
     */
    public boolean refreshBookmarkDisplay() {
        // Check if we have an active folder
        FolderButton activeFolder = folderManager.getActiveFolder();
        if (activeFolder == null) {
            ModLogger.debug("Cannot refresh bookmark display - no active folder");
            return false;
        }

        // Check if we need to throttle refreshes
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshTime < MIN_REFRESH_INTERVAL_MS) {
            ModLogger.debug("Skipping refresh - too soon since last refresh");
            return false;
        }
        lastRefreshTime = currentTime;

        try {
            // If the display is null, create a new one
            if (bookmarkDisplay == null ) {
                if (!createBookmarkDisplay(true)) {
                    return false;
                }
            }
            // Otherwise just refresh the current display
            else {
                FolderDataRepresentation currentFolder = activeFolder.getFolder();
                TypedIngredientHelper.refreshBookmarkDisplay(bookmarkDisplay, currentFolder, folderService);
                bookmarkDisplay.updateBoundsFromCalculatedPositions();
            }

            // Update the static state cache for GUI rebuilds
            safeUpdateBookmarkContents();

            ModLogger.debug("Bookmark display refresh completed successfully");
            return true;
        } catch (Exception e) {
            ModLogger.error("Error refreshing bookmark display: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handles the dropping of an ingredient on the bookmark system.
     * This method checks if the ingredient was dropped on a folder button or on the active display.
     *
     * @param mouseX The X coordinate of the mouse
     * @param mouseY The Y coordinate of the mouse
     * @param ingredient The ingredient that was dropped
     * @param isFoldersVisible Whether folders are currently visible
     * @return true if the drop was handled, false otherwise
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient, boolean isFoldersVisible) {
        // First check if it's a drop onto a folder button
        if (isFoldersVisible) {
            FolderButton targetButton = folderManager.getFolderButtonAt(mouseX, mouseY);
            if (targetButton != null) {
                // Process folder button drop
                int folderId = targetButton.getFolder().getId();
                ModLogger.info("Adding ingredient to folder {}", folderId);
                try {
                    // Use TypedIngredientHelper to wrap the ingredient
                    TypedIngredient typedIngredient = TypedIngredientHelper.wrapIngredient(ingredient);
                    if (typedIngredient == null) {
                        ModLogger.error("Failed to wrap ingredient");
                        return false;
                    }

                    // Get ingredient key for the bookmark
                    String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
                    if (key == null || key.isEmpty()) {
                        ModLogger.error("Failed to generate key for ingredient");
                        return false;
                    }

                    // Get folder details for logging
                    String folderName = targetButton.getFolder().getName();
                    ModLogger.info("Adding bookmark {} to folder {}", key, folderName);

                    // Add to folder using folderService's addBookmarkToFolder method
                    folderService.addBookmarkToFolder(folderId, key);
                    folderManager.fireFolderContentsChangedEvent(folderId);

                    // Notify JEI of the changes
                    if (bookmarkDisplay != null && bookmarkDisplay.getIngredients().isEmpty()) {
                        safeUpdateBookmarkContents();
                    }
                    return true;
                } catch (Exception e) {
                    ModLogger.error("Failed to add bookmark: {}", e.getMessage());
                    return false;
                }
            }
        }

        // Then check if it's a drop on the active display
        if (bookmarkDisplay != null) {
            ModLogger.debug("Checking if drop at ({}, {}) is on bookmark display", mouseX, mouseY);

            // Get the display bounds for detailed logging
            int displayX = bookmarkDisplay.getX();
            int displayY = bookmarkDisplay.getY();
            int displayWidth = bookmarkDisplay.getWidth();
            int displayHeight = bookmarkDisplay.getHeight();

            // Log detailed information about drop coordinates and display area
            ModLogger.debug("Display bounds: x={}, y={}, width={}, height={}",
                displayX, displayY, displayWidth, displayHeight);

            // Use the display's own isMouseOver method instead of simple bounds check
            // This takes advantage of the extended hit area for drag and drop
            boolean isInBounds = bookmarkDisplay.isMouseOver(mouseX, mouseY);

            ModLogger.debug("Is drop within display bounds (using extended hit detection): {}", isInBounds);

            // If in bounds, handle the drop
            if (isInBounds) {
                // Check if folderManager thinks we have an active folder
                boolean hasActiveFolder = folderManager.hasActiveFolder();

                // If no active folder according to folderManager but we have an active display,
                // try to recover the folder from lastActiveFolderId
                if (!hasActiveFolder) {
                    Integer lastActiveFolderId = folderManager.getLastActiveFolderId();
                    if (lastActiveFolderId != null) {
                        ModLogger.info("No active folder detected but found lastActiveFolderId: {}", lastActiveFolderId);

                        // Get the folder data - properly handle the Optional return type
                        Optional<FolderDataRepresentation> folderDataOpt = folderService.getFolder(lastActiveFolderId);
                        if (folderDataOpt.isPresent()) {
                            FolderDataRepresentation folderData = folderDataOpt.get();
                            // Set this folder as active in the bookmark display
                            bookmarkDisplay.setActiveFolder(folderData);
                            ModLogger.info("Recovered active folder state for drop operation: {}", folderData.getName());

                            // Now we can proceed with the drop
                        } else {
                            ModLogger.warn("Could not recover folder data for ID: {}", lastActiveFolderId);
                        }
                    }
                }

                // Delegate to the bookmark display directly, even if folderManager says there's no active folder
                boolean handled = bookmarkDisplay.handleIngredientDrop(mouseX, mouseY, ingredient);

                if (handled) {
                    // Make sure we update our cache
                    safeUpdateBookmarkContents();
                    ModLogger.info("Bookmark display handled ingredient drop successfully");
                } else {
                    ModLogger.warn("Bookmark display failed to handle ingredient drop");
                }

                return handled;
            } else {
                ModLogger.info("Drop coordinates outside of bookmark display bounds");
            }
        } else {
            ModLogger.debug("No bookmark display available to receive drop");
        }

        ModLogger.info("No suitable target found for ingredient drop");
        return false;
    }

    /**
     * Handles a click on the bookmark display
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

        // Let the display handle the click, which includes pagination buttons
        boolean handled = bookmarkDisplay.handleClick(mouseX, mouseY, button);

        // If a click was handled, it might have been a pagination button
        // Log debug information about pages
        if (handled) {
            ModLogger.debug("Bookmark display click handled. Current page: {}/{}",
                           bookmarkDisplay.getCurrentPageNumber(),
                           bookmarkDisplay.getPageCount());
        }

        return handled;
    }

    /**
     * Restores the bookmark display from the state manager's cache
     */
    public void restoreFromStaticState() {
        Integer lastActiveFolderId = folderManager.getLastActiveFolderId();
        if (lastActiveFolderId == null) return;

        FolderButton activeFolder = folderManager.getActiveFolder();
        if (activeFolder == null) {
            ModLogger.warn("Could not find folder with ID {} to restore", lastActiveFolderId);
            return;
        }

        if (bookmarkDisplay != null) {
            bookmarkDisplay.setActiveFolder(activeFolder.getFolder());
            List<TypedIngredient> lastBookmarkContents = folderManager.getLastBookmarkContents();
            if (!lastBookmarkContents.isEmpty()) {
                // Use the helper to set ingredients on the display
                TypedIngredientHelper.setIngredientsOnDisplay(bookmarkDisplay, lastBookmarkContents);
            }
            bookmarkDisplay.updateBoundsFromCalculatedPositions();
        }
    }

    /**
     * Gets the bookmark display, creating it if needed (lazy initialization)
     */
    public UnifiedFolderContentsDisplay getBookmarkDisplay() {
        if (bookmarkDisplay == null) {
            createBookmarkDisplay(true);
        }
        return bookmarkDisplay;
    }

    /**
     * Sets the calculated positions for the bookmark display
     */
    public void setCalculatedPositions(int nameY, int bookmarkDisplayY) {
        if (bookmarkDisplay != null) {
            bookmarkDisplay.setCalculatedPositions(nameY, bookmarkDisplayY);
        }
    }

    public void onFolderContentsChanged(UnifiedFolderManager.FolderEvent event) {
        // Update bookmarks if the active folder was changed
        int folderId = event.getFolderId();
        if (folderId != -1 && folderManager.hasActiveFolder() && 
            folderManager.getActiveFolder().getFolder().getId() == folderId) {
            
            // Get the folder data
            Optional<FolderDataRepresentation> folderOpt = folderService.getFolder(folderId);
            if (folderOpt.isPresent() && bookmarkDisplay != null) {
                // Refresh the bookmark display with the updated folder contents
                FolderDataRepresentation folder = folderOpt.get();
                TypedIngredientHelper.refreshBookmarkDisplay(bookmarkDisplay, folder, folderService);
                
                // Update the contents cache
                safeUpdateBookmarkContents();
            }
        }
    }
}