package com.jeifolders.events;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Centralized event system for bookmark-related events
 */
public class BookmarkEvents {

    // Event types
    public enum EventType {
        BOOKMARK_ADDED,
        BOOKMARK_REMOVED,
        BOOKMARKS_CLEARED,
        FOLDER_CONTENTS_CHANGED,
        DISPLAY_REFRESHED
    }
    
    /**
     * Event data container for bookmark events
     */
    public static class BookmarkEvent {
        private final EventType type;
        private final FolderDataRepresentation folder;
        private final BookmarkIngredient ingredient;
        private final String bookmarkKey;
        private final int folderId;
        
        private BookmarkEvent(EventType type, FolderDataRepresentation folder, 
                             BookmarkIngredient ingredient, String bookmarkKey, 
                             int folderId) {
            this.type = type;
            this.folder = folder;
            this.ingredient = ingredient;
            this.bookmarkKey = bookmarkKey;
            this.folderId = folderId;
        }
        
        public EventType getType() { return type; }
        public FolderDataRepresentation getFolder() { return folder; }
        public BookmarkIngredient getIngredient() { return ingredient; }
        public String getBookmarkKey() { return bookmarkKey; }
        public int getFolderId() { return folderId; }
        
        // Factory methods for different event types
        
        public static BookmarkEvent bookmarkAdded(FolderDataRepresentation folder, 
                                                 BookmarkIngredient ingredient, 
                                                 String bookmarkKey) {
            return new BookmarkEvent(EventType.BOOKMARK_ADDED, folder, ingredient, bookmarkKey, folder.getId());
        }
        
        public static BookmarkEvent bookmarkRemoved(FolderDataRepresentation folder, 
                                                   BookmarkIngredient ingredient, 
                                                   String bookmarkKey) {
            return new BookmarkEvent(EventType.BOOKMARK_REMOVED, folder, ingredient, bookmarkKey, folder.getId());
        }
        
        public static BookmarkEvent bookmarksCleared(FolderDataRepresentation folder) {
            return new BookmarkEvent(EventType.BOOKMARKS_CLEARED, folder, null, null, folder.getId());
        }
        
        public static BookmarkEvent folderContentsChanged(FolderDataRepresentation folder) {
            return new BookmarkEvent(EventType.FOLDER_CONTENTS_CHANGED, folder, null, null, folder.getId());
        }
        
        public static BookmarkEvent folderContentsChanged(int folderId) {
            return new BookmarkEvent(EventType.FOLDER_CONTENTS_CHANGED, null, null, null, folderId);
        }
        
        public static BookmarkEvent displayRefreshed(FolderDataRepresentation folder) {
            return new BookmarkEvent(EventType.DISPLAY_REFRESHED, folder, null, null, folder.getId());
        }
    }
    
    // Singleton instance
    private static BookmarkEvents instance;
    
    // Event listeners registered by type
    private final Map<EventType, List<Consumer<BookmarkEvent>>> listeners = new HashMap<>();
    
    /**
     * Gets the singleton instance
     */
    public static BookmarkEvents getInstance() {
        if (instance == null) {
            instance = new BookmarkEvents();
        }
        return instance;
    }
    
    /**
     * Private constructor for singleton
     */
    private BookmarkEvents() {
        // Initialize listener lists for all event types
        for (EventType type : EventType.values()) {
            listeners.put(type, new ArrayList<>());
        }
    }
    
    /**
     * Registers a listener for a specific event type
     */
    public void addListener(EventType type, Consumer<BookmarkEvent> listener) {
        listeners.get(type).add(listener);
    }
    
    /**
     * Registers a listener for all event types
     */
    public void addGlobalListener(Consumer<BookmarkEvent> listener) {
        for (EventType type : EventType.values()) {
            listeners.get(type).add(listener);
        }
    }
    
    /**
     * Removes a listener for a specific event type
     */
    public void removeListener(EventType type, Consumer<BookmarkEvent> listener) {
        listeners.get(type).remove(listener);
    }
    
    /**
     * Removes a listener from all event types
     */
    public void removeGlobalListener(Consumer<BookmarkEvent> listener) {
        for (EventType type : EventType.values()) {
            listeners.get(type).remove(listener);
        }
    }
    
    /**
     * Fires an event of the specified type
     */
    public void fireEvent(BookmarkEvent event) {
        List<Consumer<BookmarkEvent>> eventListeners = listeners.get(event.getType());
        
        if (eventListeners.isEmpty()) {
            ModLogger.debug("No listeners registered for event type: {}", event.getType());
            return;
        }
        
        ModLogger.debug("Firing bookmark event: {} for folder ID: {}", 
                       event.getType(), event.getFolderId());
        
        for (Consumer<BookmarkEvent> listener : new ArrayList<>(eventListeners)) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                ModLogger.error("Error in bookmark event listener: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Convenience methods for firing common events
     */
    
    public void fireBookmarkAdded(FolderDataRepresentation folder, BookmarkIngredient ingredient, String key) {
        fireEvent(BookmarkEvent.bookmarkAdded(folder, ingredient, key));
    }
    
    public void fireBookmarkRemoved(FolderDataRepresentation folder, BookmarkIngredient ingredient, String key) {
        fireEvent(BookmarkEvent.bookmarkRemoved(folder, ingredient, key));
    }
    
    public void fireBookmarksCleared(FolderDataRepresentation folder) {
        fireEvent(BookmarkEvent.bookmarksCleared(folder));
    }
    
    public void fireFolderContentsChanged(FolderDataRepresentation folder) {
        fireEvent(BookmarkEvent.folderContentsChanged(folder));
    }
    
    public void fireFolderContentsChanged(int folderId) {
        fireEvent(BookmarkEvent.folderContentsChanged(folderId));
    }
    
    public void fireDisplayRefreshed(FolderDataRepresentation folder) {
        fireEvent(BookmarkEvent.displayRefreshed(folder));
    }
}