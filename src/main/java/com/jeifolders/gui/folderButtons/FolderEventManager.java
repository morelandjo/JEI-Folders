package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Centralized event management for the folder button system.
 * Handles all UI events and notifications to listeners.
 */
public class FolderEventManager {
    // Singleton instance
    private static FolderEventManager instance;
    
    // Event types enum
    public enum EventType {
        FOLDER_CLICKED,
        FOLDER_ACTIVATED,
        FOLDER_DEACTIVATED,
        FOLDER_CREATED,
        FOLDER_DELETED,
        ADD_BUTTON_CLICKED,
        DELETE_BUTTON_CLICKED,
        BOOKMARK_CLICKED,
        INGREDIENT_DROPPED
    }
    
    // Event data class
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
    }
    
    // Event listeners
    private final Map<EventType, List<Consumer<FolderEvent>>> listeners = new HashMap<>();
    
    // Private constructor for singleton
    private FolderEventManager() {
        for (EventType type : EventType.values()) {
            listeners.put(type, new ArrayList<>());
        }
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderEventManager getInstance() {
        if (instance == null) {
            instance = new FolderEventManager();
        }
        return instance;
    }
    
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
     * Fire an event to all registered listeners
     * 
     * @param event The event to fire
     */
    public void fireEvent(FolderEvent event) {
        List<Consumer<FolderEvent>> typeListeners = listeners.get(event.getType());
        if (typeListeners != null && !typeListeners.isEmpty()) {
            for (Consumer<FolderEvent> listener : typeListeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    ModLogger.error("Error in folder event listener: {}", e.getMessage(), e);
                }
            }
            ModLogger.debug("Fired {} event to {} listeners", event.getType(), typeListeners.size());
        }
    }
    
    /**
     * Helper method to fire a folder clicked event
     * 
     * @param folder The folder that was clicked
     */
    public void fireFolderClickedEvent(FolderDataRepresentation folder) {
        FolderEvent event = new FolderEvent(EventType.FOLDER_CLICKED)
            .withData("folder", folder);
        fireEvent(event);
    }
    
    /**
     * Helper method to fire a folder activated event
     * 
     * @param folder The folder button that was activated
     */
    public void fireFolderActivatedEvent(FolderButton folder) {
        FolderEvent event = new FolderEvent(EventType.FOLDER_ACTIVATED)
            .withData("folderButton", folder)
            .withData("folder", folder.getFolder());
        fireEvent(event);
    }
    
    /**
     * Helper method to fire a folder deactivated event
     */
    public void fireFolderDeactivatedEvent() {
        FolderEvent event = new FolderEvent(EventType.FOLDER_DEACTIVATED);
        fireEvent(event);
    }
    
    /**
     * Helper method to fire a folder created event
     * 
     * @param folder The folder that was created
     */
    public void fireFolderCreatedEvent(FolderDataRepresentation folder) {
        FolderEvent event = new FolderEvent(EventType.FOLDER_CREATED)
            .withData("folder", folder);
        fireEvent(event);
    }
    
    /**
     * Helper method to fire a folder deleted event
     * 
     * @param folderId The ID of the folder that was deleted
     * @param folderName The name of the folder that was deleted
     */
    public void fireFolderDeletedEvent(int folderId, String folderName) {
        FolderEvent event = new FolderEvent(EventType.FOLDER_DELETED)
            .withData("folderId", folderId)
            .withData("folderName", folderName);
        fireEvent(event);
    }
    
    /**
     * Helper method to fire an add button clicked event
     */
    public void fireAddButtonClickedEvent() {
        FolderEvent event = new FolderEvent(EventType.ADD_BUTTON_CLICKED);
        fireEvent(event);
    }
    
    /**
     * Helper method to fire a delete button clicked event
     * 
     * @param folderId The ID of the folder to be deleted
     */
    public void fireDeleteButtonClickedEvent(int folderId) {
        FolderEvent event = new FolderEvent(EventType.DELETE_BUTTON_CLICKED)
            .withData("folderId", folderId);
        fireEvent(event);
    }
    
    /**
     * Helper method to fire a bookmark clicked event
     * 
     * @param ingredient The ingredient that was clicked
     */
    public void fireBookmarkClickedEvent(TypedIngredient ingredient) {
        FolderEvent event = new FolderEvent(EventType.BOOKMARK_CLICKED)
            .withData("ingredient", ingredient);
        fireEvent(event);
    }
    
    /**
     * Helper method to fire an ingredient dropped event
     * 
     * @param ingredient The ingredient that was dropped
     * @param folderId The ID of the folder it was dropped on, or null
     */
    public void fireIngredientDroppedEvent(Object ingredient, Integer folderId) {
        FolderEvent event = new FolderEvent(EventType.INGREDIENT_DROPPED)
            .withData("ingredient", ingredient);
        
        if (folderId != null) {
            event.withData("folderId", folderId);
        }
        
        fireEvent(event);
    }
}