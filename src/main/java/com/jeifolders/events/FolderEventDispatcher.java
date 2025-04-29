package com.jeifolders.events;

import com.jeifolders.data.Folder;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.events.FolderEvent;
import com.jeifolders.ui.events.FolderEventBus;
import com.jeifolders.ui.events.FolderEventListener;
import com.jeifolders.ui.events.FolderEventType;
import com.jeifolders.ui.util.RefreshCoordinator;
import com.jeifolders.util.ModLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Component responsible for event handling in the folder system.
 * Manages event registration and event firing through the event bus.
 */
public class FolderEventDispatcher {
    
    // Event bus for event handling
    private final FolderEventBus eventBus = new FolderEventBus();
    
    // Event debouncing
    private final Map<Integer, Long> lastFolderContentEvents = new ConcurrentHashMap<>();
    private static final long EVENT_DEBOUNCE_MS = 250;
    
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
    
    /**
     * Fires a folder contents changed event
     * Uses both the internal debouncing and the global refresh coordinator
     * to prevent excessive refresh events
     * 
     * @param folder The folder whose contents changed
     */
    public void fireFolderContentsChangedEvent(Folder folder) {
        if (folder == null) {
            ModLogger.warn("[EVENT-DEBUG] Attempted to fire folder contents changed event for null folder");
            return;
        }
        
        int folderId = folder.getId();
        
        // Check with both the internal debouncer and the global coordinator
        if (shouldDebounceEvent(folderId) || !refreshCoordinator.canRefreshFolder(folderId, false)) {
            return;
        }
        
        // Get caller information for tracking event sources
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callerClass = stackTrace.length > 2 ? stackTrace[2].getClassName() : "unknown";
        String callerMethod = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "unknown";
        
        // Notify the refresh coordinator that we're starting a refresh
        refreshCoordinator.beginRefresh();
        
        try {
            ModLogger.debug("[EVENT-DEBUG] Folder contents changed event triggered by {}::{} for folder {} ({})", 
                callerClass, callerMethod, folder.getName(), folder.getId());
            
            FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_CONTENTS_CHANGED)
                .with("folder", folder)
                .with("folderId", folderId)
                .with("sourceClass", callerClass)
                .with("sourceMethod", callerMethod);
            eventBus.post(event);
        } finally {
            // Signal that we're done with the refresh operation
            refreshCoordinator.endRefresh();
        }
    }
    
    /**
     * Fires a folder contents changed event by ID
     * Uses both the internal debouncing and the global refresh coordinator
     * to prevent excessive refresh events
     * 
     * @param folderId The ID of the folder whose contents changed
     */
    public void fireFolderContentsChangedEvent(int folderId) {
        // Check with both the internal debouncer and the global coordinator
        if (shouldDebounceEvent(folderId) || !refreshCoordinator.canRefreshFolder(folderId, false)) {
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
            
            FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_CONTENTS_CHANGED)
                .with("folderId", folderId)
                .with("sourceClass", callerClass)
                .with("sourceMethod", callerMethod);
            eventBus.post(event);
        } finally {
            // Signal that we're done with the refresh operation
            refreshCoordinator.endRefresh();
        }
    }
    
    public void fireDisplayRefreshedEvent(Folder folder) {
        FolderEvent event = new FolderEvent(this, FolderEventType.DISPLAY_REFRESHED)
            .with("folder", folder)
            .with("folderId", folder != null ? folder.getId() : null);
        eventBus.post(event);
    }
    
    /**
     * Determines if an event for the given folder should be debounced (skipped)
     * because it was triggered too recently
     * 
     * @param folderId The folder ID
     * @return True if the event should be skipped, false if it should be processed
     */
    private boolean shouldDebounceEvent(int folderId) {
        long currentTime = System.currentTimeMillis();
        Long lastEventTime = lastFolderContentEvents.get(folderId);
        
        if (lastEventTime != null && currentTime - lastEventTime < EVENT_DEBOUNCE_MS) {
            ModLogger.debug("[EVENT-DEBUG] Debouncing folder content event for folder {} - too recent ({} ms)",
                folderId, currentTime - lastEventTime);
            return true;
        }
        
        lastFolderContentEvents.put(folderId, currentTime);
        return false;
    }
}