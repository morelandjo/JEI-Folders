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
    
    // ----- Persistent Storage (saved to disk) -----
    private final FolderDataService folderService;
    
    // ----- Event listeners -----
    private final Map<EventType, List<FolderEventListener>> listeners = new HashMap<>();
    
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
            
            // Fire event
            fireEvent(FolderEvent.createFolderActivatedEvent(this, button));
            
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
            fireEvent(FolderEvent.createFolderDeactivatedEvent(this));
            
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
    public void addEventListener(EventType type, FolderEventListener listener) {
        if (listener != null) {
            listeners.get(type).add(listener);
            ModLogger.debug("Added event listener for {}, total: {}", 
                           type, listeners.get(type).size());
        }
    }
    
    /**
     * Add a listener for all event types
     * 
     * @param listener Listener that will be called for all events
     */
    public void addGlobalEventListener(FolderEventListener listener) {
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
    public void removeEventListener(EventType type, FolderEventListener listener) {
        listeners.get(type).remove(listener);
        ModLogger.debug("Removed event listener for {}, remaining: {}", 
                       type, listeners.get(type).size());
    }
    
    /**
     * Remove a listener from all event types
     * 
     * @param listener The listener to remove
     */
    public void removeGlobalEventListener(FolderEventListener listener) {
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
        // Convert from FolderEvent.Type to UnifiedFolderManager.EventType
        FolderEvent.Type eventType = event.getType();
        EventType type = EventType.valueOf(eventType.name());
        
        List<FolderEventListener> typeListeners = listeners.get(type);
        if (typeListeners != null && !typeListeners.isEmpty()) {
            for (FolderEventListener listener : new ArrayList<>(typeListeners)) {
                try {
                    listener.onFolderEvent(event);
                } catch (Exception e) {
                    ModLogger.error("Error in folder event listener: {}", e.getMessage(), e);
                }
            }
            ModLogger.debug("Fired {} event to {} listeners", type, typeListeners.size());
        }
    }
    
    // ----- Helper methods for firing folder UI events -----
    
    public void fireFolderClickedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.createFolderClickedEvent(this, folder));
    }
    
    public void fireFolderActivatedEvent(FolderButton folder) {
        fireEvent(FolderEvent.createFolderActivatedEvent(this, folder));
    }
    
    public void fireFolderDeactivatedEvent() {
        fireEvent(FolderEvent.createFolderDeactivatedEvent(this));
    }
    
    public void fireFolderCreatedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.createFolderCreatedEvent(this, folder));
    }
    
    public void fireFolderDeletedEvent(int folderId, String folderName) {
        fireEvent(FolderEvent.createFolderDeletedEvent(this, folderId, folderName));
    }
    
    public void fireAddButtonClickedEvent() {
        fireEvent(FolderEvent.createAddButtonClickedEvent(this));
    }
    
    public void fireDeleteButtonClickedEvent(int folderId) {
        fireEvent(FolderEvent.createDeleteButtonClickedEvent(this, folderId));
    }
    
    public void fireBookmarkClickedEvent(TypedIngredient ingredient) {
        fireEvent(FolderEvent.createBookmarkClickedEvent(this, ingredient));
    }
    
    public void fireIngredientDroppedEvent(Object ingredient, Integer folderId) {
        fireEvent(FolderEvent.createIngredientDroppedEvent(this, ingredient, folderId));
    }
    
    public void fireBookmarkAddedEvent(FolderDataRepresentation folder, 
                                      BookmarkIngredient ingredient, 
                                      String key) {
        fireEvent(FolderEvent.createBookmarkAddedEvent(this, folder, ingredient, key));
    }
    
    public void fireBookmarkRemovedEvent(FolderDataRepresentation folder, 
                                        BookmarkIngredient ingredient, 
                                        String key) {
        fireEvent(FolderEvent.createBookmarkRemovedEvent(this, folder, ingredient, key));
    }
    
    public void fireBookmarksClearedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.createBookmarksClearedEvent(this, folder));
    }
    
    public void fireFolderContentsChangedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.createFolderContentsChangedEvent(this, folder));
    }
    
    public void fireFolderContentsChangedEvent(int folderId) {
        fireEvent(FolderEvent.createFolderContentsChangedEvent(this, folderId));
    }
    
    public void fireDisplayRefreshedEvent(FolderDataRepresentation folder) {
        fireEvent(FolderEvent.createDisplayRefreshedEvent(this, folder));
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

        folderService.deleteFolder(folderId);
        
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
     * Safely updates the bookmark contents.
     */
    private void safeUpdateBookmarkContents() {
        if (bookmarkDisplay != null && hasActiveFolder()) {
            // Use the helper to get ingredients from the display
            List<TypedIngredient> bookmarkContents = TypedIngredientHelper.getIngredientsFromDisplay(bookmarkDisplay);
            
            // Update cache
            updateBookmarkContentsCache(bookmarkContents);
        }
    }
    
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
     * Initializes folder buttons based on the data from folder service
     * 
     * @param renderingManager The rendering manager to use for positioning
     * @param clickHandler The handler for folder clicks
     * @return The button to activate (if any)
     */
    public FolderButton initializeFolderButtons(FolderRenderingManager renderingManager, Consumer<FolderDataRepresentation> clickHandler) {
        FolderButton buttonToActivate = null;
        
        // Create and position the folder buttons
        List<FolderButton> buttons = renderingManager.createAndPositionFolderButtons();
        
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
        
        return buttonToActivate;
    }
    
    /**
     * Handles a click on a folder
     * 
     * @param folder The folder that was clicked
     */
    public void handleFolderClick(FolderDataRepresentation folder) {
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
     * Handles an ingredient being dropped on a specific folder
     * 
     * @param folder The folder the ingredient was dropped on
     * @param ingredient The ingredient that was dropped
     * @return true if the drop was handled
     */
    public boolean handleIngredientDropOnFolder(FolderDataRepresentation folder, Object ingredient) {
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
            
            // Fire folder contents changed event
            fireFolderContentsChangedEvent(folder);
            
            // Save the data
            folderService.saveData();
            
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
}