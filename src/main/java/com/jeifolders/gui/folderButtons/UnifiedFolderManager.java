package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataService;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.FolderNameInputScreen;
import com.jeifolders.gui.bookmarks.UnifiedFolderContentsDisplay;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collection;
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
        
        private FolderEvent(EventType type) {
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
    }
    
    // Event Builder class to simplify event creation
    public static class EventBuilder {
        private final FolderEvent event;
        
        private EventBuilder(EventType type) {
            event = new FolderEvent(type);
        }
        
        public static EventBuilder create(EventType type) {
            return new EventBuilder(type);
        }
        
        public EventBuilder withFolder(FolderDataRepresentation folder) {
            event.withData("folder", folder);
            if (folder != null) {
                event.withData("folderId", folder.getId());
            }
            return this;
        }
        
        public EventBuilder withFolderButton(FolderButton button) {
            event.withData("folderButton", button);
            if (button != null) {
                return withFolder(button.getFolder());
            }
            return this;
        }
        
        public EventBuilder withFolderId(int folderId) {
            event.withData("folderId", folderId);
            return this;
        }
        
        public EventBuilder withFolderName(String name) {
            event.withData("folderName", name);
            return this;
        }
        
        public EventBuilder withIngredient(Object ingredient) {
            event.withData("ingredient", ingredient);
            return this;
        }
        
        public EventBuilder withBookmarkKey(String key) {
            event.withData("bookmarkKey", key);
            return this;
        }
        
        public EventBuilder with(String key, Object value) {
            event.withData(key, value);
            return this;
        }
        
        public FolderEvent build() {
            return event;
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
    
    // Reference to the dialog handler (will be set by FolderButtonSystem)
    private Runnable addFolderDialogHandler = null;
    
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
        this.folderService.registerCallback(this);
        
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
            
            // Fire event using the EventBuilder pattern
            fireEvent(EventBuilder.create(EventType.FOLDER_ACTIVATED)
                .withFolderButton(button)
                .build());
            
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
            
            // Fire event using the EventBuilder pattern
            fireEvent(EventBuilder.create(EventType.FOLDER_DEACTIVATED)
                .build());
            
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
        fireEvent(EventBuilder.create(EventType.FOLDER_CLICKED)
            .withFolder(folder)
            .build());
    }
    
    public void fireFolderActivatedEvent(FolderButton folder) {
        fireEvent(EventBuilder.create(EventType.FOLDER_ACTIVATED)
            .withFolderButton(folder)
            .build());
    }
    
    public void fireFolderDeactivatedEvent() {
        fireEvent(EventBuilder.create(EventType.FOLDER_DEACTIVATED)
            .build());
    }
    
    public void fireFolderCreatedEvent(FolderDataRepresentation folder) {
        fireEvent(EventBuilder.create(EventType.FOLDER_CREATED)
            .withFolder(folder)
            .build());
    }
    
    public void fireFolderDeletedEvent(int folderId, String folderName) {
        fireEvent(EventBuilder.create(EventType.FOLDER_DELETED)
            .withFolderId(folderId)
            .withFolderName(folderName)
            .build());
    }
    
    public void fireAddButtonClickedEvent() {
        fireEvent(EventBuilder.create(EventType.ADD_BUTTON_CLICKED)
            .build());
    }
    
    public void fireDeleteButtonClickedEvent(int folderId) {
        fireEvent(EventBuilder.create(EventType.DELETE_BUTTON_CLICKED)
            .withFolderId(folderId)
            .build());
    }
    
    public void fireBookmarkClickedEvent(TypedIngredient ingredient) {
        fireEvent(EventBuilder.create(EventType.BOOKMARK_CLICKED)
            .withIngredient(ingredient)
            .build());
    }
    
    public void fireIngredientDroppedEvent(Object ingredient, Integer folderId) {
        EventBuilder builder = EventBuilder.create(EventType.INGREDIENT_DROPPED)
            .withIngredient(ingredient);
            
        if (folderId != null) {
            builder.withFolderId(folderId);
        }
        
        fireEvent(builder.build());
    }
    
    public void fireBookmarkAddedEvent(FolderDataRepresentation folder, 
                                      BookmarkIngredient ingredient, 
                                      String key) {
        fireEvent(EventBuilder.create(EventType.BOOKMARK_ADDED)
            .withFolder(folder)
            .withIngredient(ingredient)
            .withBookmarkKey(key)
            .build());
    }
    
    public void fireBookmarkRemovedEvent(FolderDataRepresentation folder, 
                                        BookmarkIngredient ingredient, 
                                        String key) {
        fireEvent(EventBuilder.create(EventType.BOOKMARK_REMOVED)
            .withFolder(folder)
            .withIngredient(ingredient)
            .withBookmarkKey(key)
            .build());
    }
    
    public void fireBookmarksClearedEvent(FolderDataRepresentation folder) {
        fireEvent(EventBuilder.create(EventType.BOOKMARKS_CLEARED)
            .withFolder(folder)
            .build());
    }
    
    public void fireFolderContentsChangedEvent(FolderDataRepresentation folder) {
        fireEvent(EventBuilder.create(EventType.FOLDER_CONTENTS_CHANGED)
            .withFolder(folder)
            .build());
    }
    
    public void fireFolderContentsChangedEvent(int folderId) {
        fireEvent(EventBuilder.create(EventType.FOLDER_CONTENTS_CHANGED)
            .withFolderId(folderId)
            .build());
    }
    
    public void fireDisplayRefreshedEvent(FolderDataRepresentation folder) {
        fireEvent(EventBuilder.create(EventType.DISPLAY_REFRESHED)
            .withFolder(folder)
            .build());
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
        
        // Fire folder created event using the EventBuilder directly
        fireEvent(EventBuilder.create(EventType.FOLDER_CREATED)
            .withFolder(folder)
            .build());
        
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
        
        // Fire folder deleted event using the EventBuilder directly
        fireEvent(EventBuilder.create(EventType.FOLDER_DELETED)
            .withFolderId(folderId)
            .withFolderName(folderName)
            .build());
        
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
     * Handles the dropping of an ingredient on a specific folder.
     * Manages the state changes and database updates.
     * 
     * @param folder The folder to add the ingredient to (null means active folder)
     * @param ingredient The ingredient that was dropped
     * @return true if the drop was handled, false otherwise
     */
    public boolean handleIngredientDropOnFolder(FolderDataRepresentation folder, Object ingredient) {
        if (folder == null && activeFolder != null) {
            folder = activeFolder.getFolder();
        }
        
        if (folder == null) {
            ModLogger.error("No target folder for ingredient drop");
            return false;
        }

        int folderId = folder.getId();
        String folderName = folder.getName();

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
            
            ModLogger.info("Adding bookmark {} to folder {}", key, folderName);
            
            // Add to folder using folderService's addBookmark method
            folderService.addBookmark(folderId, key);
            
            // Send event notification
            fireFolderContentsChangedEvent(folderId);
            fireIngredientDroppedEvent(ingredient, folderId);
            
            // If the display is showing, update cache
            if (bookmarkDisplay != null && hasActiveFolder() && 
                activeFolder.getFolder().getId() == folderId) {
                safeUpdateBookmarkContents();
            }
            
            return true;
        } catch (Exception e) {
            ModLogger.error("Failed to add bookmark: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Handles ingredient drops on the bookmark display area.
     * 
     * @param mouseX X position of the mouse
     * @param mouseY Y position of the mouse
     * @param ingredient The ingredient that was dropped
     * @return true if the drop was handled, false otherwise
     */
    public boolean handleIngredientDropOnDisplay(double mouseX, double mouseY, Object ingredient) {
        ModLogger.info("[DROP-DEBUG] handleIngredientDropOnDisplay called with ingredient type: {}", 
            ingredient != null ? ingredient.getClass().getName() : "null");
        
        // Create the bookmark display on-demand if it doesn't exist
        if (bookmarkDisplay == null) {
            ModLogger.info("[DROP-DEBUG] Bookmark display is null, creating one now");
            if (!createBookmarkDisplay(true)) {
                ModLogger.error("[DROP-DEBUG] Failed to create bookmark display");
                return false;
            }
        }
        
        // Check if the bookmark display can handle this drop
        boolean isInBounds = bookmarkDisplay.isMouseOver(mouseX, mouseY);
        ModLogger.info("[DROP-DEBUG] Is drop within display bounds: {}", isInBounds);
        
        if (!isInBounds) {
            return false;
        }
        
        // Make sure we have an active folder
        if (!hasActiveFolder() && lastActiveFolderId != null) {
            ModLogger.info("[DROP-DEBUG] No active folder detected but found lastActiveFolderId: {}", lastActiveFolderId);
            
            Optional<FolderDataRepresentation> folderDataOpt = folderService.getFolder(lastActiveFolderId);
            if (folderDataOpt.isPresent()) {
                FolderDataRepresentation folderData = folderDataOpt.get();
                bookmarkDisplay.setActiveFolder(folderData);
                ModLogger.info("[DROP-DEBUG] Recovered active folder state for drop operation: {}", folderData.getName());
            } else {
                ModLogger.warn("[DROP-DEBUG] Could not recover folder data for ID: {}", lastActiveFolderId);
                return false;
            }
        } else if (!hasActiveFolder()) {
            // If we don't have an active folder and can't recover one, we can't handle the drop
            ModLogger.warn("[DROP-DEBUG] No active folder and no lastActiveFolderId, cannot handle drop");
            return false;
        }
        
        // Delegate to the bookmark display
        ModLogger.info("[DROP-DEBUG] Delegating to bookmarkDisplay.handleIngredientDrop");
        boolean handled = bookmarkDisplay.handleIngredientDrop(mouseX, mouseY, ingredient);
        
        if (handled) {
            safeUpdateBookmarkContents();
            ModLogger.info("[DROP-DEBUG] Bookmark display handled ingredient drop successfully");
            
            // Fire event with the active folder ID
            if (hasActiveFolder()) {
                fireIngredientDroppedEvent(ingredient, activeFolder.getFolder().getId());
            } else {
                fireIngredientDroppedEvent(ingredient, null);
            }
        } else {
            ModLogger.warn("[DROP-DEBUG] Bookmark display failed to handle ingredient drop");
        }
        
        return handled;
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
        // Fire event for other listeners
        fireAddButtonClickedEvent();
        
        // Delegate dialog handling to the UI system
        // Note: The actual UI object reference will be passed in during button initialization
        if (addFolderDialogHandler != null) {
            addFolderDialogHandler.run();
        } else {
            ModLogger.error("Add folder dialog handler is not set");
        }
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
    
    /**
     * Creates and initializes folder buttons from data
     * 
     * @param renderingManager The rendering manager to use for position calculations
     * @param clickHandler The click handler to attach to normal folders
     * @return FolderButton that should be activated, or null if none
     */
    public FolderButton initializeFolderButtons(FolderRenderingManager renderingManager, Consumer<FolderDataRepresentation> clickHandler) {
        // Clear existing folder buttons first
        folderButtons.clear();
        
        // Force fresh load of folder data from storage
        Collection<FolderDataRepresentation> folders = folderService.getAllFolders();
        Integer lastActiveFolderId = folderService.getLastActiveFolderId();
        FolderButton buttonToActivate = null;
        
        ModLogger.info("Loading {} folders from data service", folders.size());
        
        // Create an "Add Folder" button at index 0
        int[] addPos = renderingManager.calculateAddButtonPosition();
        FolderButton addButton = new FolderButton(addPos[0], addPos[1], FolderButton.ButtonType.ADD);
        addButton.setClickHandler(this::handleAddFolderButtonClick);
        folderButtons.add(addButton);
        
        // Create folder buttons from the loaded data
        int buttonIndex = 1; // Start at 1 because the add button is at index 0
        for (FolderDataRepresentation folder : folders) {
            // Calculate positions using index for proper layout
            int[] position = renderingManager.calculateFolderPosition(buttonIndex);
            int x = position[0];
            int y = position[1];

            FolderButton button = new FolderButton(x, y, folder, clickHandler);
            folderButtons.add(button);

            if (lastActiveFolderId != null && folder.getId() == lastActiveFolderId) {
                buttonToActivate = button;
            }
            
            buttonIndex++;
        }
        
        return buttonToActivate;
    }
    
    /**
     * Handles a folder button click by updating button and folder states
     * 
     * @param folder The folder data representation that was clicked
     * @return The FolderButton that was found and updated, or null if not found
     */
    public FolderButton handleFolderClick(FolderDataRepresentation folder) {
        if (folder == null) {
            return null;
        }
        
        // Find the button for this folder
        FolderButton clickedButton = null;
        for (FolderButton button : folderButtons) {
            if (button.getFolder() == folder) {
                clickedButton = button;
                break;
            }
        }

        if (clickedButton == null) {
            return null;
        }

        // Fire the folder clicked event
        fireFolderClickedEvent(folder);

        // Handle toggle behavior (deactivate if already active)
        if (activeFolder == clickedButton) {
            deactivateActiveFolder();
            return clickedButton;
        }

        // Set as active folder
        setActiveFolder(clickedButton);
        return clickedButton;
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