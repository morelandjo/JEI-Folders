package com.jeifolders.gui.bookmarks;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.events.BookmarkEvents;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.BookmarkService;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bookmark list for folders with optimized ingredient handling
 */
public class FolderBookmarkList {
    private FolderDataRepresentation folder;
    private final List<Object> bookmarks = new ArrayList<>();
    private final List<Object> listeners = new CopyOnWriteArrayList<>();
    
    // Access services
    private final BookmarkService bookmarkService = JEIIntegrationFactory.getBookmarkService();
    private final IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
    private final BookmarkEvents eventSystem = BookmarkEvents.getInstance();
    
    // Performance optimizations
    private boolean batchUpdateMode = false;
    private boolean pendingNotification = false;

    /**
     * Sets the current folder and refreshes bookmarks
     */
    public void setFolder(FolderDataRepresentation folder) {
        this.folder = folder;
        refreshBookmarks();
    }

    /**
     * Refreshes the bookmarks based on the current folder
     */
    private void refreshBookmarks() {
        bookmarks.clear();
        
        if (folder != null) {
            List<String> keys = folder.getBookmarkKeys();
            if (!keys.isEmpty()) {
                ModLogger.info("Refreshing bookmarks for folder {} with {} keys", folder.getName(), keys.size());
                
                // Use ingredient service to get ingredients for bookmark keys
                try {
                    startBatchUpdate();
                    bookmarks.addAll(ingredientService.getIngredientsForKeys(keys));
                    finishBatchUpdate();
                } catch (Exception e) {
                    ModLogger.error("Error while refreshing bookmarks: {}", e.getMessage());
                    bookmarks.clear();
                }
            } else {
                ModLogger.info("Folder {} has no bookmarks", folder.getName());
            }
        }
        
        notifyListenersOfChange();
    }

    /**
     * Begins a batch update to prevent multiple notifications
     */
    public void startBatchUpdate() {
        batchUpdateMode = true;
        pendingNotification = false;
    }
    
    /**
     * Finishes a batch update and sends a notification if needed
     */
    public void finishBatchUpdate() {
        batchUpdateMode = false;
        if (pendingNotification) {
            notifyListenersOfChange();
            pendingNotification = false;
        }
    }

    /**
     * Add a listener that will be notified when the source list changes
     */
    public void addSourceListChangedListener(Object listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a listener
     */
    public void removeSourceListChangedListener(Object listener) {
        listeners.remove(listener);
    }

    /**
     * Notify listeners that the bookmark list has changed
     */
    public void notifyListenersOfChange() {
        if (batchUpdateMode) {
            pendingNotification = true;
            return;
        }
        
        // Use the bookmark service for legacy notification
        bookmarkService.notifySourceListChanged(listeners);
        
        // Fire an event through our new event system if we have a folder
        if (folder != null) {
            eventSystem.fireFolderContentsChanged(folder);
        }
    }

    /**
     * Gets all bookmarks in this list
     */
    public List<Object> getAllBookmarks() {
        return Collections.unmodifiableList(bookmarks);
    }

    /**
     * Adds a bookmark directly
     */
    public void addBookmark(Object ingredient) {
        // Skip if null or already exists
        if (ingredient == null || containsIngredient(ingredient)) {
            return;
        }
        
        bookmarks.add(ingredient);
        
        // Also add to folder if we have one
        if (folder != null) {
            // Use TypedIngredientHelper to get the key
            String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
            if (key != null && !key.isEmpty() && !folder.containsBookmark(key)) {
                folder.addBookmarkKey(key);
                
                // Fire bookmark added event with our new event system
                if (ingredient instanceof BookmarkIngredient) {
                    eventSystem.fireBookmarkAdded(folder, (BookmarkIngredient)ingredient, key);
                }
            }
        }
        
        // Notify listeners of the change
        notifyListenersOfChange();
    }
    
    /**
     * Add multiple bookmarks at once
     */
    public void addBookmarks(List<?> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }
        
        boolean anyAdded = false;
        startBatchUpdate();
        
        try {
            for (Object ingredient : ingredients) {
                if (ingredient != null && !containsIngredient(ingredient)) {
                    bookmarks.add(ingredient);
                    
                    // Also add to folder if we have one
                    if (folder != null) {
                        String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
                        if (key != null && !key.isEmpty() && !folder.containsBookmark(key)) {
                            folder.addBookmarkKey(key);
                            anyAdded = true;
                        }
                    }
                }
            }
        } finally {
            finishBatchUpdate();
        }
        
        // Fire event for folder contents changed if we actually added anything
        if (anyAdded && folder != null) {
            eventSystem.fireFolderContentsChanged(folder);
        }
    }
    
    /**
     * Removes a bookmark directly
     */
    public void removeBookmark(Object ingredient) {
        boolean removed = false;
        
        // Try to remove by direct equality first
        removed = bookmarks.remove(ingredient);
        
        // If not found, try to find by ingredient key
        if (!removed) {
            String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
            if (key != null && !key.isEmpty()) {
                for (int i = bookmarks.size() - 1; i >= 0; i--) {
                    Object current = bookmarks.get(i);
                    String currentKey = TypedIngredientHelper.getKeyForIngredient(current);
                    if (Objects.equals(key, currentKey)) {
                        bookmarks.remove(i);
                        removed = true;
                        break;
                    }
                }
            }
        }
        
        if (removed) {
            // Also remove from folder if we have one
            if (folder != null) {
                String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
                if (key != null && !key.isEmpty()) {
                    folder.removeBookmark(key);
                    
                    // Fire event for bookmark removed
                    if (ingredient instanceof BookmarkIngredient) {
                        eventSystem.fireBookmarkRemoved(folder, (BookmarkIngredient)ingredient, key);
                    }
                }
            }
            
            // Notify listeners of the change
            notifyListenersOfChange();
        }
    }

    /**
     * Clears all bookmarks
     */
    public void clearBookmarks() {
        if (bookmarks.isEmpty()) {
            return;
        }
        
        bookmarks.clear();
        
        // Also clear folder bookmarks if we have a folder
        if (folder != null) {
            folder.clearBookmarks();
            
            // Fire event for bookmarks cleared
            eventSystem.fireBookmarksCleared(folder);
        }
        
        notifyListenersOfChange();
    }
    
    /**
     * Gets all bookmarks and casts them to BookmarkIngredients
     */
    public List<BookmarkIngredient> getIngredients() {
        // Use TypedIngredientHelper to convert generic objects to BookmarkIngredients
        return TypedIngredientHelper.convertObjectsToBookmarkIngredients(bookmarks);
    }
    
    /**
     * Sets all ingredients in the bookmark list
     */
    public void setIngredients(List<BookmarkIngredient> ingredients) {
        bookmarks.clear();
        
        if (ingredients == null || ingredients.isEmpty()) {
            notifyListenersOfChange();
            return;
        }
        
        bookmarks.addAll(ingredients);
        
        // Update folder bookmarks if we have a folder
        if (folder != null) {
            startBatchUpdate();
            
            try {
                // Clear existing bookmarks
                folder.clearBookmarks();
                
                // Add new bookmarks
                for (Object ingredient : ingredients) {
                    // Use TypedIngredientHelper to get the key
                    String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
                    if (key != null && !key.isEmpty()) {
                        folder.addBookmarkKey(key);
                    }
                }
                
                // Fire event for folder contents changed
                eventSystem.fireFolderContentsChanged(folder);
            } finally {
                finishBatchUpdate();
            }
        }
        
        // Notify listeners of the change
        notifyListenersOfChange();
    }
    
    /**
     * Checks if this list contains an ingredient with the same key
     */
    public boolean containsIngredient(Object ingredient) {
        if (ingredient == null || bookmarks.isEmpty()) {
            return false;
        }
        
        // First check by reference equality (faster)
        if (bookmarks.contains(ingredient)) {
            return true;
        }
        
        // Then check by ingredient key (slower but more accurate)
        return bookmarks.stream()
            .anyMatch(bookmark -> TypedIngredientHelper.areIngredientsEqual(bookmark, ingredient));
    }
    
    /**
     * Gets the number of bookmarks in this list
     */
    public int size() {
        return bookmarks.size();
    }
    
    /**
     * Checks if this list is empty
     */
    public boolean isEmpty() {
        return bookmarks.isEmpty();
    }
    
    /**
     * Gets the current folder
     */
    public FolderDataRepresentation getFolder() {
        return folder;
    }
}
