package com.jeifolders.ui.events;

import com.jeifolders.data.Folder;
import com.jeifolders.ui.components.buttons.FolderButton;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a folder-related event.
 * Simplified event model with a flexible data structure.
 */
public class FolderEvent extends EventObject {
    private static final long serialVersionUID = 1L;
    
    private final FolderEventType type;
    private final Map<String, Object> data = new HashMap<>();
    
    /**
     * Creates a new folder event with the specified source and type.
     * 
     * @param source the object on which the event initially occurred
     * @param type the type of the event
     */
    public FolderEvent(Object source, FolderEventType type) {
        super(source);
        this.type = type;
    }
    
    /**
     * Gets the type of this event.
     * 
     * @return the event type
     */
    public FolderEventType getType() {
        return type;
    }
    
    /**
     * Adds a data key-value pair to this event.
     * 
     * @param key the key for the data
     * @param value the value to associate with the key
     * @return this event, for chaining
     */
    public FolderEvent with(String key, Object value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Gets data from this event as the specified type.
     * 
     * @param <T> the expected return type
     * @param key the key for the data
     * @return the data as the specified type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }
    
    /**
     * Checks if this event has data for the specified key.
     * 
     * @param key the key to check
     * @return true if data exists for the key, false otherwise
     */
    public boolean has(String key) {
        return data.containsKey(key);
    }
    
    // Convenience getter methods to maintain compatibility
    
    /**
     * Gets the folder ID from this event.
     * 
     * @return the folder ID, or null if not present
     */
    public Integer getFolderId() {
        return get("folderId");
    }
    
    /**
     * Gets the folder from this event.
     * 
     * @return the folder, or null if not present
     */
    public Folder getFolder() {
        return get("folder");
    }
    
    /**
     * Gets the folder button from this event.
     * 
     * @return the folder button, or null if not present
     */
    public FolderButton getFolderButton() {
        return get("folderButton");
    }
    
    /**
     * Gets the ingredient from this event.
     * 
     * @return the ingredient, or null if not present
     */
    public Object getIngredient() {
        return get("ingredient");
    }
    
    /**
     * Gets the bookmark key from this event.
     * 
     * @return the bookmark key, or null if not present
     */
    public String getBookmarkKey() {
        return get("bookmarkKey");
    }
    
    /**
     * Gets the folder name from this event.
     * 
     * @return the folder name, or null if not present
     */
    public String getFolderName() {
        return get("folderName");
    }
}