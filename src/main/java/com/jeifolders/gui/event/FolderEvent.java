package com.jeifolders.gui.event;

import com.jeifolders.data.Folder;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.TypedIngredient;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a folder-related event.
 * Based on Java's standard EventObject but with additional type and data fields.
 */
public class FolderEvent extends EventObject {
    private static final long serialVersionUID = 1L;
    
    /**
     * Event types for folder-related events
     */
    public enum Type {
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

    private final Type type;
    private final Map<String, Object> data = new HashMap<>();
    
    /**
     * Creates a new folder event with the specified source and type.
     * 
     * @param source the object on which the event initially occurred
     * @param type the type of the event
     */
    public FolderEvent(Object source, Type type) {
        super(source);
        this.type = type;
    }
    
    /**
     * Gets the type of this event.
     * 
     * @return the event type
     */
    public Type getType() {
        return type;
    }
    
    /**
     * Adds a data key-value pair to this event.
     * 
     * @param key the key for the data
     * @param value the value to associate with the key
     * @return this event, for chaining
     */
    public FolderEvent withData(String key, Object value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Gets data from this event as the specified type.
     * 
     * @param <T> the expected return type
     * @param key the key for the data
     * @param clazz the class of the expected return type
     * @return the data as the specified type, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> clazz) {
        Object value = data.get(key);
        if (value != null && clazz.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Checks if this event has data for the specified key.
     * 
     * @param key the key to check
     * @return true if data exists for the key, false otherwise
     */
    public boolean hasData(String key) {
        return data.containsKey(key);
    }
    
    /**
     * Gets the folder ID from this event.
     * 
     * @return the folder ID, or null if not present
     */
    public Integer getFolderId() {
        return getData("folderId", Integer.class);
    }
    
    /**
     * Gets the folder from this event.
     * 
     * @return the folder, or null if not present
     */
    public Folder getFolder() {
        return getData("folder", Folder.class);
    }
    
    /**
     * Creates a new folder event with the folder clicked type.
     * 
     * @param source the event source
     * @param folder the clicked folder
     * @return the created event
     */
    public static FolderEvent createFolderClickedEvent(Object source, Folder folder) {
        return new FolderEvent(source, Type.FOLDER_CLICKED)
            .withData("folder", folder)
            .withData("folderId", folder != null ? folder.getId() : null);
    }
    
    /**
     * Creates a new folder event with the folder activated type.
     * 
     * @param source the event source
     * @param button the activated folder button
     * @return the created event
     */
    public static FolderEvent createFolderActivatedEvent(Object source, FolderButton button) {
        FolderEvent event = new FolderEvent(source, Type.FOLDER_ACTIVATED)
            .withData("folderButton", button);
            
        if (button != null && button.getFolder() != null) {
            event.withData("folder", button.getFolder())
                .withData("folderId", button.getFolder().getId());
        }
        
        return event;
    }
    
    /**
     * Creates a new folder event with the folder deactivated type.
     * 
     * @param source the event source
     * @return the created event
     */
    public static FolderEvent createFolderDeactivatedEvent(Object source) {
        return new FolderEvent(source, Type.FOLDER_DEACTIVATED);
    }
    
    /**
     * Creates a new folder event with the folder created type.
     * 
     * @param source the event source
     * @param folder the created folder
     * @return the created event
     */
    public static FolderEvent createFolderCreatedEvent(Object source, Folder folder) {
        return new FolderEvent(source, Type.FOLDER_CREATED)
            .withData("folder", folder)
            .withData("folderId", folder != null ? folder.getId() : null);
    }
    
    /**
     * Creates a new folder event with the folder deleted type.
     * 
     * @param source the event source
     * @param folderId the ID of the deleted folder
     * @param folderName the name of the deleted folder
     * @return the created event
     */
    public static FolderEvent createFolderDeletedEvent(Object source, int folderId, String folderName) {
        return new FolderEvent(source, Type.FOLDER_DELETED)
            .withData("folderId", folderId)
            .withData("folderName", folderName);
    }
    
    /**
     * Creates a new folder event with the add button clicked type.
     * 
     * @param source the event source
     * @return the created event
     */
    public static FolderEvent createAddButtonClickedEvent(Object source) {
        return new FolderEvent(source, Type.ADD_BUTTON_CLICKED);
    }
    
    /**
     * Creates a new folder event with the delete button clicked type.
     * 
     * @param source the event source
     * @param folderId the ID of the folder to delete
     * @return the created event
     */
    public static FolderEvent createDeleteButtonClickedEvent(Object source, int folderId) {
        return new FolderEvent(source, Type.DELETE_BUTTON_CLICKED)
            .withData("folderId", folderId);
    }
    
    /**
     * Creates a new folder event with the bookmark clicked type.
     * 
     * @param source the event source
     * @param ingredient the clicked ingredient
     * @return the created event
     */
    public static FolderEvent createBookmarkClickedEvent(Object source, TypedIngredient ingredient) {
        return new FolderEvent(source, Type.BOOKMARK_CLICKED)
            .withData("ingredient", ingredient);
    }
    
    /**
     * Creates a new folder event with the ingredient dropped type.
     * 
     * @param source the event source
     * @param ingredient the dropped ingredient
     * @param folderId the ID of the folder the ingredient was dropped on
     * @return the created event
     */
    public static FolderEvent createIngredientDroppedEvent(Object source, Object ingredient, Integer folderId) {
        FolderEvent event = new FolderEvent(source, Type.INGREDIENT_DROPPED)
            .withData("ingredient", ingredient);
            
        if (folderId != null) {
            event.withData("folderId", folderId);
        }
        
        return event;
    }
    
    /**
     * Creates a new folder event with the bookmark added type.
     * 
     * @param source the event source
     * @param folder the folder the bookmark was added to
     * @param ingredient the bookmark ingredient
     * @param key the bookmark key
     * @return the created event
     */
    public static FolderEvent createBookmarkAddedEvent(Object source, Folder folder, 
                                                      BookmarkIngredient ingredient, String key) {
        return new FolderEvent(source, Type.BOOKMARK_ADDED)
            .withData("folder", folder)
            .withData("folderId", folder != null ? folder.getId() : null)
            .withData("ingredient", ingredient)
            .withData("bookmarkKey", key);
    }
    
    /**
     * Creates a new folder event with the bookmark removed type.
     * 
     * @param source the event source
     * @param folder the folder the bookmark was removed from
     * @param ingredient the bookmark ingredient
     * @param key the bookmark key
     * @return the created event
     */
    public static FolderEvent createBookmarkRemovedEvent(Object source, Folder folder, 
                                                        BookmarkIngredient ingredient, String key) {
        return new FolderEvent(source, Type.BOOKMARK_REMOVED)
            .withData("folder", folder)
            .withData("folderId", folder != null ? folder.getId() : null)
            .withData("ingredient", ingredient)
            .withData("bookmarkKey", key);
    }
    
    /**
     * Creates a new folder event with the bookmarks cleared type.
     * 
     * @param source the event source
     * @param folder the folder whose bookmarks were cleared
     * @return the created event
     */
    public static FolderEvent createBookmarksClearedEvent(Object source, Folder folder) {
        return new FolderEvent(source, Type.BOOKMARKS_CLEARED)
            .withData("folder", folder)
            .withData("folderId", folder != null ? folder.getId() : null);
    }
    
    /**
     * Creates a new folder event with the folder contents changed type.
     * 
     * @param source the event source
     * @param folder the folder whose contents changed
     * @return the created event
     */
    public static FolderEvent createFolderContentsChangedEvent(Object source, Folder folder) {
        return new FolderEvent(source, Type.FOLDER_CONTENTS_CHANGED)
            .withData("folder", folder)
            .withData("folderId", folder != null ? folder.getId() : null);
    }
    
    /**
     * Creates a new folder event with the folder contents changed type.
     * 
     * @param source the event source
     * @param folderId the ID of the folder whose contents changed
     * @return the created event
     */
    public static FolderEvent createFolderContentsChangedEvent(Object source, int folderId) {
        return new FolderEvent(source, Type.FOLDER_CONTENTS_CHANGED)
            .withData("folderId", folderId);
    }
    
    /**
     * Creates a new folder event with the display refreshed type.
     * 
     * @param source the event source
     * @param folder the folder whose display was refreshed
     * @return the created event
     */
    public static FolderEvent createDisplayRefreshedEvent(Object source, Folder folder) {
        return new FolderEvent(source, Type.DISPLAY_REFRESHED)
            .withData("folder", folder)
            .withData("folderId", folder != null ? folder.getId() : null);
    }
}