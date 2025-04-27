package com.jeifolders.gui.controller;

import com.jeifolders.data.FolderStorageService;
import com.jeifolders.data.Folder;
import com.jeifolders.gui.common.MouseHitUtil;
import com.jeifolders.gui.event.FolderEvent;
import com.jeifolders.gui.event.FolderEventBus;
import com.jeifolders.gui.event.FolderEventListener;
import com.jeifolders.gui.event.FolderEventType;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.contents.FolderContentsView;
import com.jeifolders.gui.layout.FolderLayoutService;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Unified manager for folder system functionality, combining:
 * - FolderStateManager - State management
 * - FolderEventManager - Event management
 * - BookmarkManager - Bookmark operations
 * 
 * This consolidation reduces the number of manager classes and clarifies responsibilities.
 */
public class FolderStateManager {
    // Singleton instance
    private static FolderStateManager instance;
    
    // ----- Transient State (preserved across GUI rebuilds) -----
    private static Integer lastActiveFolderId = null;
    private static List<TypedIngredient> lastBookmarkContents = new ArrayList<>();
    private static long lastGuiRebuildTime = 0;
    private static final long GUI_REBUILD_DEBOUNCE_TIME = 500;
    
    // ----- UI State (instance specific) -----
    private FolderButton activeFolder = null;
    private final List<FolderButton> folderButtons = new ArrayList<>();
    private boolean foldersVisible = true;
    
    // ----- Bookmark Display -----
    private FolderContentsView bookmarkDisplay;
    
    // ----- Persistent Storage (saved to disk) -----
    private final FolderStorageService folderService;
    
    // ----- Event system -----
    private final FolderEventBus eventBus = new FolderEventBus();
    
    // Reference to the dialog handler (will be set by FolderButtonSystem)
    private Runnable addFolderDialogHandler = null;
    
    // Track last refresh time for performance optimization (from BookmarkManager)
    private long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL_MS = 100; // Prevent too frequent refreshes
    
    // Add recursion guard to prevent stack overflow (from BookmarkManager)
    private static boolean updatingBookmarkContents = false;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderStateManager() {
        this.folderService = FolderStorageService.getInstance();
        
        // Set up bidirectional connection
        this.folderService.registerCallback(this);
        
        // Don't create the bookmark display in constructor to avoid circular dependency
        // The display will be created when needed via getBookmarkDisplay()
        
        ModLogger.debug("FolderStateManager initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderStateManager getInstance() {
        if (instance == null) {
            instance = new FolderStateManager();
        }
        return instance;
    }
    
    // ----- UI State Management -----
    
    /**
     * Sets a folder as active
     * 
     * @param button The folder button to activate
     */
    public void setActiveFolder(FolderButton button) {
        if (activeFolder != null && activeFolder != button) {
            activeFolder.setActive(false);
        }
        
        if (button != null) {
            button.setActive(true);
            activeFolder = button;
            
            // Update transient state
            lastActiveFolderId = button.getFolder().getId();
            lastGuiRebuildTime = System.currentTimeMillis();
            
            // Fire event
            fireFolderActivatedEvent(button);
            
            // Update bookmark display
            if (bookmarkDisplay != null) {
                bookmarkDisplay.setActiveFolder(button.getFolder());
                bookmarkDisplay.updateBoundsFromCalculatedPositions();
                safeUpdateBookmarkContents();
            }
        }
    }
    
    /**
     * Deactivates the currently active folder
     */
    public void deactivateActiveFolder() {
        if (activeFolder != null) {
            activeFolder.setActive(false);
            activeFolder = null;
            
            // Clear transient state
            lastActiveFolderId = null;
            lastBookmarkContents = new ArrayList<>();
            
            // Fire event
            fireFolderDeactivatedEvent();
            
            // Update bookmark display
            if (bookmarkDisplay != null) {
                bookmarkDisplay.setActiveFolder(null);
            }
        }
    }
    
    /**
     * Updates the folder button list
     * 
     * @param buttons List of folder buttons
     */
    public void setFolderButtons(List<FolderButton> buttons) {
        folderButtons.clear();
        if (buttons != null) {
            folderButtons.addAll(buttons);
        }
    }
    
    /**
     * Sets folder visibility
     * 
     * @param visible Whether folders are visible
     */
    public void setFoldersVisible(boolean visible) {
        this.foldersVisible = visible;
    }
    
    /**
     * Gets current folder visibility
     */
    public boolean areFoldersVisible() {
        return foldersVisible;
    }
    
    // ----- Transient State Management -----
    
    /**
     * Updates the bookmark contents cache
     * 
     * @param bookmarkContents The bookmark contents to cache
     */
    public void updateBookmarkContentsCache(List<TypedIngredient> bookmarkContents) {
        lastBookmarkContents = bookmarkContents != null ? 
            new ArrayList<>(bookmarkContents) : new ArrayList<>();
    }
    
    /**
     * Checks if state should be restored from static state
     * 
     * @return true if state restoration is needed
     */
    public boolean shouldRestoreFromStaticState() {
        if (lastActiveFolderId == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        return currentTime - lastGuiRebuildTime < GUI_REBUILD_DEBOUNCE_TIME;
    }
    
    /**
     * Clears all transient state
     */
    public void clearTransientState() {
        lastActiveFolderId = null;
        lastBookmarkContents = new ArrayList<>();
        lastGuiRebuildTime = 0;
    }
    
    // ----- Event System -----
    
    /**
     * Add a listener for a specific event type
     * 
     * @param type Event type to listen for
     * @param listener Listener that will be called when event occurs
     */
    public void addEventListener(FolderEventType type, Consumer<FolderEvent> listener) {
        eventBus.register(type, listener);
    }
    
    /**
     * Add a legacy listener for backward compatibility
     * 
     * @param type Event type to listen for
     * @param listener Legacy listener that will be called when event occurs
     */
    public void addEventListener(FolderEventType type, FolderEventListener listener) {
        if (listener != null) {
            eventBus.register(type, event -> listener.onFolderEvent(event));
        }
    }
    
    /**
     * Add a listener for all event types
     * 
     * @param listener Listener that will be called for all events
     */
    public void addGlobalEventListener(Consumer<FolderEvent> listener) {
        eventBus.registerGlobal(listener);
    }
    
    /**
     * Add a legacy listener for all event types (backward compatibility)
     * 
     * @param listener Listener that will be called for all events
     */
    public void addGlobalEventListener(FolderEventListener listener) {
        if (listener != null) {
            eventBus.registerGlobal(event -> listener.onFolderEvent(event));
        }
    }
    
    /**
     * Remove a listener for a specific event type
     * 
     * @param type Event type to remove listener from
     * @param listener The listener to remove
     */
    public void removeEventListener(FolderEventType type, Consumer<FolderEvent> listener) {
        eventBus.unregister(type, listener);
    }
    
    /**
     * Remove a listener from all event types
     * 
     * @param listener The listener to remove
     */
    public void removeGlobalEventListener(Consumer<FolderEvent> listener) {
        eventBus.unregisterGlobal(listener);
    }
    
    // ----- Helper methods for firing folder UI events -----
    
    public void fireFolderClickedEvent(Folder folder) {
        FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_CLICKED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null);
        eventBus.post(event);
    }
    
    public void fireFolderActivatedEvent(FolderButton button) {
        FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_ACTIVATED)
            .with("folderButton", button);
            
        if (button != null && button.getFolder() != null) {
            event.with("folder", button.getFolder())
                .with("folderId", button.getFolder().getId());
        }
        
        eventBus.post(event);
    }
    
    public void fireFolderDeactivatedEvent() {
        FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_DEACTIVATED);
        eventBus.post(event);
    }
    
    public void fireFolderCreatedEvent(Folder folder) {
        FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_CREATED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null);
        eventBus.post(event);
    }
    
    public void fireFolderDeletedEvent(int folderId, String folderName) {
        FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_DELETED)
            .with("folderId", folderId)
            .with("folderName", folderName);
        eventBus.post(event);
    }
    
    public void fireAddButtonClickedEvent() {
        FolderEvent event = new FolderEvent(this, FolderEventType.ADD_BUTTON_CLICKED);
        eventBus.post(event);
    }
    
    public void fireDeleteButtonClickedEvent(int folderId) {
        FolderEvent event = new FolderEvent(this, FolderEventType.DELETE_BUTTON_CLICKED)
            .with("folderId", folderId);
        eventBus.post(event);
    }
    
    public void fireBookmarkClickedEvent(TypedIngredient ingredient) {
        FolderEvent event = new FolderEvent(this, FolderEventType.BOOKMARK_CLICKED)
            .with("ingredient", ingredient);
        eventBus.post(event);
    }
    
    public void fireIngredientDroppedEvent(Object ingredient, Integer folderId) {
        FolderEvent event = new FolderEvent(this, FolderEventType.INGREDIENT_DROPPED)
            .with("ingredient", ingredient);
            
        if (folderId != null) {
            event.with("folderId", folderId);
        }
        
        eventBus.post(event);
    }
    
    public void fireBookmarkAddedEvent(Folder folder, 
                                      BookmarkIngredient ingredient, 
                                      String key) {
        FolderEvent event = new FolderEvent(this, FolderEventType.BOOKMARK_ADDED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null)
            .with("ingredient", ingredient)
            .with("bookmarkKey", key);
        eventBus.post(event);
    }
    
    public void fireBookmarkRemovedEvent(Folder folder, 
                                        BookmarkIngredient ingredient, 
                                        String key) {
        FolderEvent event = new FolderEvent(this, FolderEventType.BOOKMARK_REMOVED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null)
            .with("ingredient", ingredient)
            .with("bookmarkKey", key);
        eventBus.post(event);
    }
    
    public void fireBookmarksClearedEvent(Folder folder) {
        FolderEvent event = new FolderEvent(this, FolderEventType.BOOKMARKS_CLEARED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null);
        eventBus.post(event);
    }
    
    public void fireFolderContentsChangedEvent(Folder folder) {
        FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_CONTENTS_CHANGED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null);
        eventBus.post(event);
    }
    
    public void fireFolderContentsChangedEvent(int folderId) {
        FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_CONTENTS_CHANGED)
            .with("folderId", folderId);
        eventBus.post(event);
    }
    
    public void fireDisplayRefreshedEvent(Folder folder) {
        FolderEvent event = new FolderEvent(this, FolderEventType.DISPLAY_REFRESHED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null);
        eventBus.post(event);
    }
    
    // ----- Persistent Storage Management -----
    
    /**
     * Creates a new folder with the given name
     * 
     * @param folderName Name for the new folder
     * @return The newly created folder representation
     */
    public Folder createFolder(String folderName) {
        Folder folder = folderService.createFolder(folderName);
        ModLogger.debug("Created folder: {} (ID: {})", folder.getName(), folder.getId());
        
        // Fire folder created event
        fireFolderCreatedEvent(folder);
        
        return folder;
    }
    
    /**
     * Deletes the active folder
     */
    public void deleteActiveFolder() {
        if (activeFolder == null) {
            return;
        }

        int folderId = activeFolder.getFolder().getId();
        String folderName = activeFolder.getFolder().getName();
        ModLogger.debug("Deleting folder: {} (ID: {})", folderName, folderId);

        folderService.deleteFolder(folderId);
        
        // Fire folder deleted event
        fireFolderDeletedEvent(folderId, folderName);
        
        // Clear active folder
        activeFolder = null;
        
        // Force a complete UI rebuild through the FolderUIController
        Minecraft.getInstance().execute(() -> {
            if (FolderUIController.isInitialized()) {
                ModLogger.info("Rebuilding folder UI after folder deletion");
                FolderUIController.getInstance().rebuildFolders();
            }
        });
    }
    
    /**
     * Loads all folders from persistent storage
     */
    public List<Folder> loadAllFolders() {
        folderService.loadData();
        return folderService.getAllFolders();
    }
    
    /**
     * Gets the folder button at the specified coordinates
     */
    public FolderButton getFolderButtonAt(double mouseX, double mouseY) {
        for (FolderButton button : folderButtons) {
            if (MouseHitUtil.isMouseOverButton(mouseX, mouseY, button)) {
                return button;
            }
        }
        return null;
    }
    
    /**
     * Updates all folder buttons (for animations, etc.)
     */
    public void tickFolderButtons() {
        for (FolderButton button : folderButtons) {
            button.tick();
        }
    }
    
    // ----- Bookmark Display Management -----
    
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
        Optional<FolderContentsView> displayOpt = FolderContentsView.create(folderService);
        
        if (displayOpt.isPresent()) {
            bookmarkDisplay = displayOpt.get();
            
            // Make sure the display knows about any active folder and has bounds set
            if (hasActiveFolder()) {
                FolderButton activeFolder = getActiveFolder();
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
                updateBookmarkContentsCache(bookmarkContents);
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
            ModLogger.debug("Cannot refresh bookmarks - no folder provided");
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
            // Ensure we have a bookmark display
            if (bookmarkDisplay == null) {
                if (!createBookmarkDisplay(true)) {
                    return false;
                }
            }
            
            // Store the current page number before refreshing
            int currentPageNumber = bookmarkDisplay.getCurrentPageNumber();
            
            // Load bookmarks from the folder and convert to ingredients
            List<BookmarkIngredient> bookmarkIngredients = TypedIngredientHelper.convertToBookmarkIngredients(
                TypedIngredientHelper.loadBookmarksFromFolder(folderService, folder.getId(), true)
            );
            
            // Set active folder and ingredients
            bookmarkDisplay.setActiveFolder(folder);
            bookmarkDisplay.setIngredients(bookmarkIngredients);
            
            // Force layout update if requested
            if (forceLayout && bookmarkDisplay.getContentsImpl() != null) {
                bookmarkDisplay.getContentsImpl().updateLayout(true);
                
                // Restore pagination
                if (currentPageNumber > 1 && bookmarkDisplay.getPageCount() >= currentPageNumber) {
                    bookmarkDisplay.goToPage(currentPageNumber);
                    ModLogger.debug("Restored pagination to page {} after refresh", currentPageNumber);
                }
            }
            
            // Update bounds
            bookmarkDisplay.updateBoundsFromCalculatedPositions();
            
            // Update cache for GUI rebuilds
            safeUpdateBookmarkContents();
            
            // Fire event
            fireDisplayRefreshedEvent(folder);
            
            ModLogger.debug("Folder bookmarks refresh completed successfully for folder: {}", folder.getName());
            return true;
        } catch (Exception e) {
            ModLogger.error("Error refreshing folder bookmarks: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Refreshes the current active folder's bookmarks
     * 
     * @param forceLayout Whether to force layout update
     * @return true if the refresh was successful
     */
    public boolean refreshActiveFolder(boolean forceLayout) {
        if (!hasActiveFolder()) {
            ModLogger.debug("Cannot refresh active folder - no active folder");
            return false;
        }
        
        return refreshFolderBookmarks(activeFolder.getFolder(), forceLayout);
    }

    /**
     * Refreshes the bookmark display with the latest data
     * 
     * @return true if the refresh was successful
     */
    public boolean refreshBookmarkDisplay() {
        // Check if we have an active folder
        if (activeFolder == null) {
            ModLogger.debug("Cannot refresh bookmark display - no active folder");
            return false;
        }

        // Delegate to our centralized refresh method
        return refreshFolderBookmarks(activeFolder.getFolder(), true);
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
        if (bookmarkKey.isPresent() && activeFolder != null) {
            String key = bookmarkKey.get();
            
            // Get the typed ingredient from the bookmark
            var typedIngredient = TypedIngredientHelper.getTypedIngredientForKey(key);
            if (typedIngredient != null) {
                // Fire bookmark clicked event
                fireBookmarkClickedEvent(typedIngredient);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Handles an ingredient being dropped onto the bookmark system.
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
            FolderButton targetButton = getFolderButtonAt(mouseX, mouseY);
            if (targetButton != null) {
                return handleIngredientDropOnFolder(targetButton.getFolder(), ingredient);
            }
        }

        // Then check if it's a drop on the active display
        return handleIngredientDropOnDisplay(mouseX, mouseY, ingredient);
    }
    
    /**
     * Central method for handling ingredient drops on a folder.
     * This consolidates the logic previously duplicated across multiple classes.
     * 
     * @param folder The folder to add the ingredient to
     * @param ingredient The ingredient being dropped
     * @return true if the drop was handled
     */
    public boolean handleIngredientDropOnFolder(Folder folder, Object ingredient) {
        if (folder == null || ingredient == null) {
            ModLogger.debug("Cannot handle ingredient drop: folder or ingredient is null");
            return false;
        }
        
        try {
            // Generate ingredient key
            String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
            if (key == null || key.isEmpty()) {
                ModLogger.debug("Failed to generate key for ingredient");
                return false;
            }
            
            // Check if the folder already has this bookmark
            if (folder.containsBookmark(key)) {
                ModLogger.debug("Folder already contains bookmark with key: {}", key);
                return true;
            }
            
            // Add the bookmark to the folder
            folderService.addBookmark(folder.getId(), key);
            
            // Fire appropriate event based on ingredient type
            if (ingredient instanceof BookmarkIngredient) {
                ModLogger.debug("Ingredient is a BookmarkIngredient, firing BOOKMARK_ADDED event");
                fireBookmarkAddedEvent(
                    folder,
                    (BookmarkIngredient)ingredient,
                    key
                );
            } else {
                // For non-BookmarkIngredient, fire a folder contents changed event
                ModLogger.debug("Ingredient is not a BookmarkIngredient, firing FOLDER_CONTENTS_CHANGED event");
                fireFolderContentsChangedEvent(folder);
            }
            
            // Save the changes
            folderService.saveData();
            
            // If this is the active folder, refresh the display
            if (hasActiveFolder() && activeFolder.getFolder().getId() == folder.getId()) {
                refreshActiveFolder(true);
            }
            
            ModLogger.debug("Successfully added bookmark to folder: {}", folder.getName());
            return true;
        } catch (Exception e) {
            ModLogger.error("Error handling ingredient drop on folder: {}", e.getMessage(), e);
            return false;
        }
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
        // Add debug logging
        ModLogger.debug("[DROP-DEBUG] handleIngredientDropOnDisplay called. activeFolder: {}, bookmarkDisplay: {}",
                        hasActiveFolder() ? activeFolder.getFolder().getName() : "null",
                        bookmarkDisplay != null ? "present" : "null");

        if (!hasActiveFolder()) {
            ModLogger.debug("[DROP-DEBUG] Cannot handle ingredient drop: no active folder");
            return false;
        }
        
        // Ensure we have a bookmark display
        if (bookmarkDisplay == null) {
            ModLogger.debug("[DROP-DEBUG] Bookmark display was null, creating it now");
            if (!createBookmarkDisplay(true)) {
                ModLogger.error("[DROP-DEBUG] Failed to create bookmark display");
                return false;
            }
        }
        
        // Update the bookmark display with the active folder if needed
        if (bookmarkDisplay.getActiveFolder() == null && activeFolder != null) {
            ModLogger.debug("[DROP-DEBUG] Setting active folder on bookmark display");
            bookmarkDisplay.setActiveFolder(activeFolder.getFolder());
        }
        
        // Delegate to the bookmark display
        boolean result = bookmarkDisplay.handleIngredientDrop(mouseX, mouseY, ingredient);
        ModLogger.debug("[DROP-DEBUG] Bookmark display handleIngredientDrop result: {}", result);
        return result;
    }
    
    /**
     * Sets the positions for the bookmark display
     * 
     * @param nameY The y position for the folder name
     * @param bookmarkDisplayY The y position for the bookmark display
     */
    public void setBookmarkDisplayPositions(int nameY, int bookmarkDisplayY) {
        if (bookmarkDisplay != null) {
            bookmarkDisplay.setCalculatedPositions(nameY, bookmarkDisplayY);
        }
    }

    /**
     * Central method to handle all mouse click events in the UI.
     * This consolidates all click handling in one place for better maintainability.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button used
     * @param isDeleteButtonHovered Whether the delete button is hovered
     * @param deleteButtonX X position of the delete button
     * @return true if the click was handled
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button, boolean isDeleteButtonHovered, int deleteButtonX) {
        // Check delete button clicks first - highest priority
        if (deleteButtonX >= 0 && button == 0 && isDeleteButtonHovered) {
            // Fire delete button clicked event before deleting
            if (hasActiveFolder()) {
                fireDeleteButtonClickedEvent(getActiveFolder().getFolder().getId());
            }
            
            deleteActiveFolder();
            return true;
        }

        // Check folder button clicks
        if (areFoldersVisible()) {
            for (FolderButton folderButton : getFolderButtons()) {
                if (MouseHitUtil.isMouseOverButton(mouseX, mouseY, folderButton)) {
                    // Handle different button types
                    switch (folderButton.getButtonType()) {
                        case ADD:
                            // Add button handled directly by the folder manager
                            handleAddFolderButtonClick(null);
                            break;
                        case NORMAL:
                            // Normal folder buttons handled directly by the folder manager
                            if (folderButton.getFolder() != null) {
                                handleFolderClick(folderButton.getFolder());
                            }
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            }
        }

        // Check if the click should be handled by the bookmark display
        return hasActiveFolder() && 
               getBookmarkDisplay() != null &&
               handleBookmarkDisplayClick(mouseX, mouseY, button);
    }

    /**
     * Gets the folder data service instance
     */
    public FolderStorageService getFolderService() {
        return folderService;
    }
    
    /**
     * Gets the list of folder buttons
     */
    public List<FolderButton> getFolderButtons() {
        return folderButtons;
    }
    
    /**
     * Gets the currently active folder button
     */
    public FolderButton getActiveFolder() {
        return activeFolder;
    }
    
    /**
     * Checks if there is an active folder
     */
    public boolean hasActiveFolder() {
        return activeFolder != null;
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
    
    /**
     * Handles a click on the "Add Folder" button.
     * This method is used as a click handler for the "Add Folder" button.
     * Since the button has no associated folder, the parameter is ignored.
     * 
     * @param ignored This parameter is ignored since the Add button has no folder
     */
    public void handleAddFolderButtonClick(Folder ignored) {
        // Fire event for other listeners
        fireAddButtonClickedEvent();
        
        // Delegate dialog handling to the UI system
        if (addFolderDialogHandler != null) {
            addFolderDialogHandler.run();
        } else {
            ModLogger.error("Add folder dialog handler is not set");
        }
    }
    
    /**
     * Handles a click on a folder
     * 
     * @param folder The folder that was clicked
     */
    public void handleFolderClick(Folder folder) {
        if (folder == null) {
            return;
        }
        
        // Find the button for the clicked folder
        for (FolderButton button : folderButtons) {
            if (button.getButtonType() == FolderButton.ButtonType.NORMAL && 
                button.getFolder() != null && 
                button.getFolder().getId() == folder.getId()) {
                
                if (button.isActive()) {
                    // If clicking the active folder again, deactivate it
                    deactivateActiveFolder();
                } else {
                    // Otherwise activate the clicked folder
                    setActiveFolder(button);
                }
                break;
            }
        }
    }

    /**
     * Initializes folder buttons based on the data from folder service
     * 
     * @param layoutService The layout service to use for positioning
     * @param clickHandler The handler for folder clicks
     * @return The button to activate (if any)
     */
    public FolderButton initializeFolderButtons(FolderLayoutService layoutService, Consumer<Folder> clickHandler) {
        FolderButton buttonToActivate = null;
        
        // Create and position the folder buttons
        List<FolderButton> buttons = layoutService.createAndPositionFolderButtons();
        
        // Find button to activate based on the last active folder ID
        if (lastActiveFolderId != null) {
            for (FolderButton button : buttons) {
                if (button.getButtonType() == FolderButton.ButtonType.NORMAL && 
                    button.getFolder() != null && 
                    button.getFolder().getId() == lastActiveFolderId) {
                    buttonToActivate = button;
                    break;
                }
            }
        }
        
        // Update folder button references and click handlers
        for (FolderButton button : buttons) {
            if (button.getButtonType() != FolderButton.ButtonType.ADD) {
                button.setClickHandler(clickHandler);
            }
        }
        
        // Set the buttons in the state manager
        setFolderButtons(buttons);
        
        return buttonToActivate;
    }

    /**
     * Sets the handler for showing the add folder dialog
     * This should be called by the UI system that manages dialogs
     * 
     * @param handler The runnable that will show the add folder dialog
     */
    public void setAddFolderDialogHandler(Runnable handler) {
        this.addFolderDialogHandler = handler;
    }
}