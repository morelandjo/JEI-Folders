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
    
    /**
     * Internal method to handle folder contents changed events with debouncing
     * 
     * @param folderId The folder ID
     */
    public void fireFolderContentsChangedEvent(int folderId) {
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
        fireFolderContentsChangedEvent(folderId);
    }
}