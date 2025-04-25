package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataService;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.bookmarks.UnifiedFolderContentsDisplay;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Unified manager for folder system functionality, combining:
 * - FolderStateManager - State management
 * - FolderEventManager - Event management
 * - Parts of BookmarkManager - Bookmark operations
 * 
 * This consolidation reduces the number of manager classes and clarifies responsibilities.
 */
public class UnifiedFolderManager {
    // Singleton instance
    private static UnifiedFolderManager instance;
    
    // ----- Event Types -----
    public enum EventType {
        // Folder UI events
        FOLDER_CLICKED,
        FOLDER_ACTIVATED,
        FOLDER_DEACTIVATED,
        FOLDER_CREATED,
        FOLDER_DELETED,
        ADD_BUTTON_CLICKED,
        DELETE_BUTTON_CLICKED,
        BOOKMARK_CLICKED,
        INGREDIENT_DROPPED,
        
        // Bookmark operation events
        BOOKMARK_ADDED,
        BOOKMARK_REMOVED,
        BOOKMARKS_CLEARED,
        FOLDER_CONTENTS_CHANGED,
        DISPLAY_REFRESHED
    }
    
    // ----- Event class -----
    public static class FolderEvent {
        private final EventType type;
        private final Map<String, Object> data = new HashMap<>();
        
        public FolderEvent(EventType type) {
            this.type = type;
        }
        
        public EventType getType() {
            return type;
        }
        
        public FolderEvent withData(String key, Object value) {
            data.put(key, value);
            return this;
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getData(String key, Class<T> clazz) {
            Object value = data.get(key);
            if (value != null && clazz.isAssignableFrom(value.getClass())) {
                return (T) value;
            }
            return null;
        }
        
        public boolean hasData(String key) {
            return data.containsKey(key);
        }
        
        public Integer getFolderId() {
            return getData("folderId", Integer.class);
        }
        
        public FolderDataRepresentation getFolder() {
            return getData("folder", FolderDataRepresentation.class);
        }
        
        // Factory methods for events
        public static FolderEvent folderClicked(FolderDataRepresentation folder) {
            return new FolderEvent(EventType.FOLDER_CLICKED)
                .withData("folder", folder);
        }
        
        public static FolderEvent folderActivated(FolderButton folder) {
            return new FolderEvent(EventType.FOLDER_ACTIVATED)
                .withData("folderButton", folder)
                .withData("folder", folder.getFolder())
                .withData("folderId", folder.getFolder().getId());
        }
        
        public static FolderEvent folderDeactivated() {
            return new FolderEvent(EventType.FOLDER_DEACTIVATED);
        }
        
        public static FolderEvent folderCreated(FolderDataRepresentation folder) {
            return new FolderEvent(EventType.FOLDER_CREATED)
                .withData("folder", folder)
                .withData("folderId", folder.getId());
        }
        
        public static FolderEvent folderDeleted(int folderId, String folderName) {
            return new FolderEvent(EventType.FOLDER_DELETED)
                .withData("folderId", folderId)
                .withData("folderName", folderName);
        }
        
        public static FolderEvent addButtonClicked() {
            return new FolderEvent(EventType.ADD_BUTTON_CLICKED);
        }
        
        public static FolderEvent deleteButtonClicked(int folderId) {
            return new FolderEvent(EventType.DELETE_BUTTON_CLICKED)
                .withData("folderId", folderId);
        }
        
        public static FolderEvent bookmarkClicked(TypedIngredient ingredient) {
            return new FolderEvent(EventType.BOOKMARK_CLICKED)
                .withData("ingredient", ingredient);
        }
        
        public static FolderEvent ingredientDropped(Object ingredient, Integer folderId) {
            FolderEvent event = new FolderEvent(EventType.INGREDIENT_DROPPED)
                .withData("ingredient", ingredient);
            
            if (folderId != null) {
                event.withData("folderId", folderId);
            }
            
            return event;
        }
        
        public static FolderEvent bookmarkAdded(FolderDataRepresentation folder, 
                                               BookmarkIngredient ingredient, 
                                               String bookmarkKey) {
            return new FolderEvent(EventType.BOOKMARK_ADDED)
                .withData("folder", folder)
                .withData("ingredient", ingredient)
                .withData("bookmarkKey", bookmarkKey)
                .withData("folderId", folder.getId());
        }
        
        public static FolderEvent bookmarkRemoved(FolderDataRepresentation folder, 
                                                BookmarkIngredient ingredient, 
                                                String bookmarkKey) {
            return new FolderEvent(EventType.BOOKMARK_REMOVED)
                .withData("folder", folder)
                .withData("ingredient", ingredient)
                .withData("bookmarkKey", bookmarkKey)
                .withData("folderId", folder.getId());
        }
        
        public static FolderEvent bookmarksCleared(FolderDataRepresentation folder) {
            return new FolderEvent(EventType.BOOKMARKS_CLEARED)
                .withData("folder", folder)
                .withData("folderId", folder.getId());
        }
        
        public static FolderEvent folderContentsChanged(FolderDataRepresentation folder) {
            return new FolderEvent(EventType.FOLDER_CONTENTS_CHANGED)
                .withData("folder", folder)
                .withData("folderId", folder.getId());
        }
        
        public static FolderEvent folderContentsChanged(int folderId) {
            return new FolderEvent(EventType.FOLDER_CONTENTS_CHANGED)
                .withData("folderId", folderId);
        }
        
        public static FolderEvent displayRefreshed(FolderDataRepresentation folder) {
            return new FolderEvent(EventType.DISPLAY_REFRESHED)
                .withData("folder", folder)
                .withData("folderId", folder.getId());
        }
    }
    
    // ----- Transient State (preserved across GUI rebuilds) -----
    private static Integer lastActiveFolderId = null;
    private static List<TypedIngredient> lastBookmarkContents = new ArrayList<>();
    private static long lastGuiRebuildTime = 0;
    private static final long GUI_REBUILD_DEBOUNCE_TIME = 500;
    
    // ----- UI State (instance specific) -----
    private FolderButton activeFolder = null;
    private FolderDataRepresentation lastActiveFolder = null;
    private final List<FolderButton> folderButtons = new ArrayList<>();
    private boolean foldersVisible = true;
    
    // ----- Bookmark Display -----
    private UnifiedFolderContentsDisplay bookmarkDisplay;
    private long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL_MS = 100; // Prevent too frequent refreshes
    
    // ----- Persistent Storage (saved to disk) -----
    private final FolderDataService folderService;
    
    // ----- Event listeners -----
    private final Map<EventType, List<Consumer<FolderEvent>>> listeners = new HashMap<>();
    
    /**
     * Private constructor for singleton pattern
     */
    private UnifiedFolderManager() {
        this.folderService = FolderDataService.getInstance();
        
        // Initialize event listeners map
        for (EventType type : EventType.values()) {
            listeners.put(type, new ArrayList<>());
        }
        
        // Set up bidirectional connection
        this.folderService.setFolderManager(this);
        
        // Don't create the bookmark display in constructor to avoid circular dependency
        // The display will be created when needed via getBookmarkDisplay()
        
        ModLogger.debug("UnifiedFolderManager initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized UnifiedFolderManager getInstance() {
        if (instance == null) {
            instance = new UnifiedFolderManager();
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
            lastActiveFolder = button.getFolder();
            
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
     * @param listener Consumer that will be called when event occurs
     */
    public void addEventListener(EventType type, Consumer<FolderEvent> listener) {
        if (listener != null) {
            listeners.get(type).add(listener);
            ModLogger.debug("Added event listener for {}, total: {}", 
                           type, listeners.get(type).size());
        }
    }
    
    /**
     * Add a listener for all event types
     * 
     * @param listener Consumer that will be called for all events
     */
    public void addGlobalEventListener(Consumer<FolderEvent> listener) {
        if (listener != null) {
            for (EventType type : EventType.values()) {
                listeners.get(type).add(listener);
            }
            ModLogger.debug("Added global event listener");
        }
    }
    
    /**
     * Remove a listener for a specific event type
     * 
     * @param type Event type to remove listener from
     * @param listener The listener to remove
     */
    public void removeEventListener(EventType type, Consumer<FolderEvent> listener) {
        listeners.get(type).remove(listener);
        ModLogger.debug("Removed event listener for {}, remaining: {}", 
                       type, listeners.get(type).size());
    }
    
    /**
     * Remove a listener from all event types
     * 
     * @param listener The listener to remove
     */
    public void removeGlobalEventListener(Consumer<FolderEvent> listener) {
        for (EventType type : EventType.values()) {
            listeners.get(type).remove(listener);
        }
        ModLogger.debug("Removed global event listener");
    }
    
    /**
     * Fire an event to all registered listeners
     * 
     * @param event The event to fire
     */
    public void fireEvent(FolderEvent event) {
        List<Consumer<FolderEvent>> typeListeners = listeners.get(event.getType());
        if (typeListeners != null && !typeListeners.isEmpty()) {
            for (Consumer<FolderEvent> listener : new ArrayList<>(typeListeners)) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    ModLogger.error("Error in folder event listener: {}", e.getMessage(), e);
                }
            }
            ModLogger.debug("Fired {} event to {} listeners", event.getType(), typeListeners.size());
        }
    }
    
    // ----- Helper methods for firing folder UI events -----
    
    public void fireFolderClickedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.folderClicked(folder));
    }
    
    public void fireFolderActivatedEvent(FolderButton folder) {
        fireEvent(FolderEvent.folderActivated(folder));
    }
    
    public void fireFolderDeactivatedEvent() {
        fireEvent(FolderEvent.folderDeactivated());
    }
    
    public void fireFolderCreatedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.folderCreated(folder));
    }
    
    public void fireFolderDeletedEvent(int folderId, String folderName) {
        fireEvent(FolderEvent.folderDeleted(folderId, folderName));
    }
    
    public void fireAddButtonClickedEvent() {
        fireEvent(FolderEvent.addButtonClicked());
    }
    
    public void fireDeleteButtonClickedEvent(int folderId) {
        fireEvent(FolderEvent.deleteButtonClicked(folderId));
    }
    
    public void fireBookmarkClickedEvent(TypedIngredient ingredient) {
        fireEvent(FolderEvent.bookmarkClicked(ingredient));
    }
    
    public void fireIngredientDroppedEvent(Object ingredient, Integer folderId) {
        fireEvent(FolderEvent.ingredientDropped(ingredient, folderId));
    }
    
    public void fireBookmarkAddedEvent(FolderDataRepresentation folder, 
                                     BookmarkIngredient ingredient, 
                                     String key) {
        fireEvent(FolderEvent.bookmarkAdded(folder, ingredient, key));
    }
    
    public void fireBookmarkRemovedEvent(FolderDataRepresentation folder, 
                                       BookmarkIngredient ingredient, 
                                       String key) {
        fireEvent(FolderEvent.bookmarkRemoved(folder, ingredient, key));
    }
    
    public void fireBookmarksClearedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.bookmarksCleared(folder));
    }
    
    public void fireFolderContentsChangedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.folderContentsChanged(folder));
    }
    
    public void fireFolderContentsChangedEvent(int folderId) {
        fireEvent(FolderEvent.folderContentsChanged(folderId));
    }
    
    public void fireDisplayRefreshedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.displayRefreshed(folder));
    }
    
    // ----- Persistent Storage Management -----
    
    /**
     * Creates a new folder with the given name
     * 
     * @param folderName Name for the new folder
     * @return The newly created folder representation
     */
    public FolderDataRepresentation createFolder(String folderName) {
        FolderDataRepresentation folder = folderService.createFolder(folderName);
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

        folderService.removeFolder(folderId);
        
        // Fire folder deleted event
        fireFolderDeletedEvent(folderId, folderName);
        
        // Clear active folder
        activeFolder = null;
    }
    
    /**
     * Loads all folders from persistent storage
     */
    public List<FolderDataRepresentation> loadAllFolders() {
        folderService.loadData();
        return folderService.getAllFolders();
    }
    
    /**
     * Gets the folder button at the specified coordinates
     */
    public FolderButton getFolderButtonAt(double mouseX, double mouseY) {
        for (FolderButton button : folderButtons) {
            if (mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
                mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
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
        Optional<UnifiedFolderContentsDisplay> displayOpt = UnifiedFolderContentsDisplay.create(folderService);
        
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
     * Legacy method that creates a display without preserving ingredients
     */
    public void createBookmarkDisplay() {
        createBookmarkDisplay(false);
    }
    
    /**
     * Updates the static state cache for GUI rebuilds
     */
    private void safeUpdateBookmarkContents() {
        if (bookmarkDisplay != null) {
            // Use the helper to get ingredients from the display
            List<TypedIngredient> bookmarkContents = TypedIngredientHelper.getIngredientsFromDisplay(bookmarkDisplay);
            
            // Update cache
            updateBookmarkContentsCache(bookmarkContents);
        }
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
     * @return true if the drop was handled, false otherwise
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        // First check if it's a drop onto a folder button
        if (foldersVisible) {
            FolderButton targetButton = getFolderButtonAt(mouseX, mouseY);
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
                    fireFolderContentsChangedEvent(folderId);
                    
                    // If the display is empty, update cache
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
            
            // Check if the bookmark display can handle this drop
            boolean isInBounds = bookmarkDisplay.isMouseOver(mouseX, mouseY);
            ModLogger.debug("Is drop within display bounds: {}", isInBounds);
            
            if (isInBounds) {
                // Check if we need to recover the active folder
                if (activeFolder == null && lastActiveFolderId != null) {
                    ModLogger.info("No active folder detected but found lastActiveFolderId: {}", lastActiveFolderId);
                    
                    Optional<FolderDataRepresentation> folderDataOpt = folderService.getFolder(lastActiveFolderId);
                    if (folderDataOpt.isPresent()) {
                        FolderDataRepresentation folderData = folderDataOpt.get();
                        bookmarkDisplay.setActiveFolder(folderData);
                        ModLogger.info("Recovered active folder state for drop operation: {}", folderData.getName());
                    } else {
                        ModLogger.warn("Could not recover folder data for ID: {}", lastActiveFolderId);
                    }
                }
                
                // Delegate to the bookmark display
                boolean handled = bookmarkDisplay.handleIngredientDrop(mouseX, mouseY, ingredient);
                
                if (handled) {
                    safeUpdateBookmarkContents();
                    ModLogger.info("Bookmark display handled ingredient drop successfully");
                } else {
                    ModLogger.warn("Bookmark display failed to handle ingredient drop");
                }
                
                return handled;
            }
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
        if (handled) {
            ModLogger.debug("Bookmark display click handled. Current page: {}/{}", 
                           bookmarkDisplay.getCurrentPageNumber(), 
                           bookmarkDisplay.getPageCount());
        }
        
        return handled;
    }
    
    /**
     * Restores the bookmark display from the cached state
     */
    public void restoreFromStaticState() {
        if (lastActiveFolderId == null) return;

        if (activeFolder == null) {
            ModLogger.warn("Could not find folder with ID {} to restore", lastActiveFolderId);
            return;
        }

        if (bookmarkDisplay != null) {
            bookmarkDisplay.setActiveFolder(activeFolder.getFolder());
            if (!lastBookmarkContents.isEmpty()) {
                // Use the helper to set ingredients on the display
                TypedIngredientHelper.setIngredientsOnDisplay(bookmarkDisplay, lastBookmarkContents);
            }
            bookmarkDisplay.updateBoundsFromCalculatedPositions();
        }
    }
    
    /**
     * Sets the calculated positions for the bookmark display
     */
    public void setBookmarkDisplayPositions(int nameY, int bookmarkDisplayY) {
        if (bookmarkDisplay != null) {
            bookmarkDisplay.setCalculatedPositions(nameY, bookmarkDisplayY);
        }
    }
    
    /**
     * Notifies listeners that folder contents have changed for a specific folder ID.
     * 
     * @param folderId The ID of the folder whose contents changed
     */
    public void notifyFolderContentsChanged(int folderId) {
        // Use our existing event firing mechanism
        fireFolderContentsChangedEvent(folderId);
    }
    
    /**
     * Handles a click on the "Add Folder" button.
     * This method is used as a click handler for the "Add Folder" button.
     * Since the button has no associated folder, the parameter is ignored.
     * 
     * @param ignored This parameter is ignored since the Add button has no folder
     */
    public void handleAddFolderButtonClick(FolderDataRepresentation ignored) {
        // Simply fires the add button clicked event
        fireAddButtonClickedEvent();
    }
    
    // ----- Getters -----
    
    /**
     * Gets the folder data service instance
     */
    public FolderDataService getFolderService() {
        return folderService;
    }
    
    /**
     * Legacy method for backward compatibility.
     * @deprecated Use getFolderService() instead
     */
    @Deprecated
    public FolderDataService getFolderManager() {
        return folderService;
    }
    
    public List<FolderButton> getFolderButtons() {
        return folderButtons;
    }
    
    public FolderButton getActiveFolder() {
        return activeFolder;
    }
    
    public boolean hasActiveFolder() {
        return activeFolder != null;
    }
    
    public Integer getLastActiveFolderId() {
        return lastActiveFolderId;
    }
    
    public List<TypedIngredient> getLastBookmarkContents() {
        return lastBookmarkContents;
    }
    
    public FolderDataRepresentation getLastActiveFolder() {
        return lastActiveFolder;
    }
    
    public void setLastActiveFolder(FolderDataRepresentation folder) {
        this.lastActiveFolder = folder;
    }
    
    public UnifiedFolderContentsDisplay getBookmarkDisplay() {
        if (bookmarkDisplay == null) {
            createBookmarkDisplay(true);
        }
        return bookmarkDisplay;
    }
}