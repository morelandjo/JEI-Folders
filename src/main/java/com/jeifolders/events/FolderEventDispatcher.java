package com.jeifolders.events;

import com.jeifolders.data.Folder;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.events.EventDebouncer;
import com.jeifolders.ui.events.FolderEvent;
import com.jeifolders.ui.events.FolderEventBus;
import com.jeifolders.ui.events.FolderEventBuilder;
import com.jeifolders.ui.events.FolderEventType;
import com.jeifolders.ui.util.RefreshCoordinator;
import com.jeifolders.util.ModLogger;

import java.util.function.Consumer;

/**
 * Component responsible for event handling in the folder system.
 * Manages event registration and event firing through the event bus.
 */
public class FolderEventDispatcher {
    
    // Event bus for event handling
    private final FolderEventBus eventBus = new FolderEventBus();
    
    // Event debouncing
    private final EventDebouncer debouncer = new EventDebouncer(250);
    
    // Component ID for refresh coordination
    private static final String COMPONENT_ID = "FolderEventDispatcher";
    
    // Get reference to the refresh coordinator
    private final RefreshCoordinator refreshCoordinator = RefreshCoordinator.getInstance();
    
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
     * Add a listener for all event types
     * 
     * @param listener Listener that will be called for all events
     */
    public void addGlobalEventListener(Consumer<FolderEvent> listener) {
        eventBus.registerGlobal(listener);
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
    
    // ----- Generic event firing method -----
    
    /**
     * Fire an event using the builder pattern.
     * 
     * @param type The event type
     * @return An event builder to construct and fire the event
     */
    public FolderEventBuilder fire(FolderEventType type) {
        return new FolderEventBuilder(this, type) {
            @Override
            public FolderEvent build() {
                FolderEvent event = super.build();
                eventBus.post(event);
                return event;
            }
        };
    }
    
    // ----- Helper methods for firing folder UI events -----
    // These methods now use the builder pattern internally
    
    public void fireFolderClickedEvent(Folder folder) {
        fire(FolderEventType.FOLDER_CLICKED)
            .withFolder(folder)
            .build();
    }
    
    public void fireFolderActivatedEvent(FolderButton button) {
        FolderEventBuilder builder = fire(FolderEventType.FOLDER_ACTIVATED)
            .withButton(button);
            
        if (button != null && button.getFolder() != null) {
            builder.withFolder(button.getFolder());
        }
        
        builder.build();
    }
    
    public void fireFolderDeactivatedEvent() {
        fire(FolderEventType.FOLDER_DEACTIVATED)
            .build();
    }
    
    public void fireFolderCreatedEvent(Folder folder) {
        fire(FolderEventType.FOLDER_CREATED)
            .withFolder(folder)
            .build();
    }
    
    public void fireFolderDeletedEvent(int folderId, String folderName) {
        fire(FolderEventType.FOLDER_DELETED)
            .withFolderId(folderId)
            .withFolderName(folderName)
            .build();
    }
    
    public void fireAddButtonClickedEvent() {
        fire(FolderEventType.ADD_BUTTON_CLICKED)
            .build();
    }
    
    public void fireDeleteButtonClickedEvent(int folderId) {
        fire(FolderEventType.DELETE_BUTTON_CLICKED)
            .withFolderId(folderId)
            .build();
    }
    
    public void fireBookmarkClickedEvent(TypedIngredient ingredient) {
        fire(FolderEventType.BOOKMARK_CLICKED)
            .withIngredient(ingredient)
            .build();
    }
    
    public void fireIngredientDroppedEvent(Object ingredient, Integer folderId) {
        FolderEventBuilder builder = fire(FolderEventType.INGREDIENT_DROPPED)
            .withIngredient(ingredient);
            
        if (folderId != null) {
            builder.withFolderId(folderId);
        }
        
        builder.build();
    }
    
    public void fireBookmarkAddedEvent(Folder folder, 
                                      BookmarkIngredient ingredient, 
                                      String key) {
        fire(FolderEventType.BOOKMARK_ADDED)
            .withFolder(folder)
            .withIngredient(ingredient)
            .withBookmarkKey(key)
            .build();
    }
    
    public void fireBookmarkRemovedEvent(Folder folder, 
                                       BookmarkIngredient ingredient, 
                                       String key) {
        fire(FolderEventType.BOOKMARK_REMOVED)
            .withFolder(folder)
            .withIngredient(ingredient)
            .withBookmarkKey(key)
            .build();
    }
    
    public void fireBookmarksClearedEvent(Folder folder) {
        fire(FolderEventType.BOOKMARKS_CLEARED)
            .withFolder(folder)
            .build();
    }
    
    /**
     * Fires a folder contents changed event
     * 
     * @param folder The folder whose contents changed
     */
    public void fireFolderContentsChangedEvent(Folder folder) {
        if (folder == null) {
            ModLogger.warn("[EVENT-DEBUG] Attempted to fire folder contents changed event for null folder");
            return;
        }
        
        int folderId = folder.getId();
        fireFolderContentsChangedEventById(folderId);
    }
    
    /**
     * Fires a folder contents changed event by ID
     * 
     * @param folderId The ID of the folder whose contents changed
     */
    public void fireFolderContentsChangedEvent(int folderId) {
        fireFolderContentsChangedEventById(folderId);
    }
    
    /**
     * Internal method to handle folder contents changed events with debouncing
     * 
     * @param folderId The folder ID
     */
    private void fireFolderContentsChangedEventById(int folderId) {
        // Skip if the event should be debounced or if the folder can't be refreshed
        if (!debouncer.shouldProcess(folderId) || !refreshCoordinator.canRefreshFolder(folderId, false)) {
            return;
        }
        
        // Get caller information for tracking event sources
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callerClass = stackTrace.length > 2 ? stackTrace[2].getClassName() : "unknown";
        String callerMethod = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "unknown";
        
        // Notify the refresh coordinator that we're starting a refresh
        refreshCoordinator.beginRefresh();
        
        try {
            ModLogger.debug("[EVENT-DEBUG] Folder contents changed event triggered by {}::{} for folder ID {}", 
                callerClass, callerMethod, folderId);
            
            fire(FolderEventType.FOLDER_CONTENTS_CHANGED)
                .withFolderId(folderId)
                .withData("sourceClass", callerClass)
                .withData("sourceMethod", callerMethod)
                .build();
        } finally {
            // Signal that we're done with the refresh operation
            refreshCoordinator.endRefresh();
        }
    }
    
    public void fireDisplayRefreshedEvent(Folder folder) {
        fire(FolderEventType.DISPLAY_REFRESHED)
            .withFolder(folder)
            .build();
    }
}