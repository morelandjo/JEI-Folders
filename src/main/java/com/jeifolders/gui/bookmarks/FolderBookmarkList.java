package com.jeifolders.gui.bookmarks;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.folderButtons.UnifiedFolderManager;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.overlay.IIngredientGridSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores and manages the list of bookmarks for the active folder.
 */
public class FolderBookmarkList {
    private FolderDataRepresentation folder;
    private List<BookmarkIngredient> ingredients = new ArrayList<>();
    private final Map<String, BookmarkIngredient> ingredientMap = new HashMap<>();
    private final UnifiedFolderManager eventManager;

    // Access services
    private final IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();

    private boolean batchUpdateMode = false;
    private boolean pendingNotification = false;

    // JEI integration - source list changed listeners
    private final List<IIngredientGridSource.SourceListChangedListener> sourceListChangedListeners = new ArrayList<>();

    public FolderBookmarkList() {
        this.eventManager = UnifiedFolderManager.getInstance();
    }

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
        ingredients.clear();
        ingredientMap.clear();

        if (folder != null) {
            List<String> keys = folder.getBookmarkKeys();
            if (!keys.isEmpty()) {
                // Use ingredient service to get ingredients for bookmark keys
                try {
                    startBatchUpdate();
                    for (String key : keys) {
                        // Handle Optional return type properly
                        Optional<ITypedIngredient<?>> ingredientOpt = ingredientService.getIngredientForKey(key);
                        if (ingredientOpt.isPresent()) {
                            BookmarkIngredient ingredient = new BookmarkIngredient(ingredientOpt.get());
                            ingredients.add(ingredient);
                            ingredientMap.put(key, ingredient);
                        }
                    }
                    finishBatchUpdate();
                } catch (Exception e) {
                    ModLogger.error("Error while refreshing bookmarks: {}", e.getMessage());
                    ingredients.clear();
                    ingredientMap.clear();
                }
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
     * Notify listeners that the bookmark list has changed
     */
    public void notifyListenersOfChange() {
        if (batchUpdateMode) {
            pendingNotification = true;
            return;
        }

        if (folder != null) {
            eventManager.fireFolderContentsChangedEvent(folder);
        }

        // Notify JEI source list changed listeners
        notifySourceListChangedListeners();
    }

    /**
     * Notify all source list changed listeners
     */
    private void notifySourceListChangedListeners() {
        if (sourceListChangedListeners.isEmpty()) {
            return;
        }

        for (IIngredientGridSource.SourceListChangedListener listener : new ArrayList<>(sourceListChangedListeners)) {
            try {
                listener.onSourceListChanged();
            } catch (Exception e) {
                ModLogger.error("Error notifying source list changed listener: {}", e.getMessage(), e);
            }
        }

        ModLogger.debug("Notified {} source list changed listeners", sourceListChangedListeners.size());
    }

    /**
     * Adds a JEI source list changed listener
     */
    public void addSourceListChangedListener(IIngredientGridSource.SourceListChangedListener listener) {
        if (listener != null && !sourceListChangedListeners.contains(listener)) {
            sourceListChangedListeners.add(listener);
            ModLogger.debug("Added source list changed listener to FolderBookmarkList");
        }
    }

    /**
     * Gets all bookmarks in this list
     */
    public List<BookmarkIngredient> getAllBookmarks() {
        return Collections.unmodifiableList(ingredients);
    }

    /**
     * Adds a bookmark ingredient to this list
     * @param ingredient The ingredient to add
     * @param key The key for the ingredient
     * @return true if the ingredient was added
     */
    public boolean addBookmark(BookmarkIngredient ingredient, String key) {
        if (folder == null) {
            return false;
        }

        if (key == null || key.isEmpty()) {
            ModLogger.warn("Cannot add bookmark: invalid key");
            return false;
        }

        if (ingredientMap.containsKey(key)) {
            ModLogger.debug("Bookmark already exists with key: {}", key);
            return false;
        }

        ingredientMap.put(key, ingredient);
        ingredients.add(ingredient);
        folder.addBookmarkKey(key);

        eventManager.fireBookmarkAddedEvent(folder, ingredient, key);

        return true;
    }

    /**
     * Removes a bookmark by key
     * @param key The key of the bookmark to remove
     * @return true if the bookmark was removed
     */
    public boolean removeBookmarkByKey(String key) {
        if (folder == null || key == null || key.isEmpty()) {
            return false;
        }

        BookmarkIngredient ingredient = ingredientMap.remove(key);
        if (ingredient != null) {
            ingredients.remove(ingredient);
            folder.removeBookmarkKey(key);

            eventManager.fireBookmarkRemovedEvent(folder, ingredient, key);

            return true;
        }
        return false;
    }

    /**
     * Removes a bookmark
     * @param ingredient The ingredient to remove
     * @return true if the bookmark was removed
     */
    public boolean removeBookmark(BookmarkIngredient ingredient) {
        if (folder == null || ingredient == null) {
            return false;
        }

        // Find the key for this ingredient
        String keyToRemove = null;
        for (Map.Entry<String, BookmarkIngredient> entry : ingredientMap.entrySet()) {
            if (entry.getValue().equals(ingredient)) {
                keyToRemove = entry.getKey();
                break;
            }
        }

        if (keyToRemove != null) {
            return removeBookmarkByKey(keyToRemove);
        }
        return false;
    }

    /**
     * Clears all bookmarks
     */
    public void clear() {
        ingredients.clear();
        ingredientMap.clear();

        if (folder != null) {
            folder.clearBookmarks();

            eventManager.fireBookmarksClearedEvent(folder);
        }
    }

    /**
     * Gets the number of bookmarks in this list
     */
    public int size() {
        return ingredients.size();
    }

    /**
     * Checks if this list is empty
     */
    public boolean isEmpty() {
        return ingredients.isEmpty();
    }

    /**
     * Gets the current folder
     */
    public FolderDataRepresentation getFolder() {
        return folder;
    }
    
    /**
     * Sets the ingredients for this bookmark list
     * @param bookmarkIngredients The list of bookmark ingredients to set
     */
    public void setIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        if (bookmarkIngredients == null) {
            bookmarkIngredients = new ArrayList<>();
        }
        
        // Clear current ingredients first
        ingredients.clear();
        ingredientMap.clear();
        
        // Add all new ingredients in batch mode
        try {
            startBatchUpdate();
            for (BookmarkIngredient ingredient : bookmarkIngredients) {
                // Try to get a key for this ingredient
                String key = ingredientService.getKeyForIngredient(ingredient.getWrappedIngredient());
                if (key != null && !key.isEmpty() && !ingredientMap.containsKey(key)) {
                    ingredients.add(ingredient);
                    ingredientMap.put(key, ingredient);
                }
            }
            finishBatchUpdate();
            
            ModLogger.debug("Set {} ingredients on bookmark list", ingredients.size());
        } catch (Exception e) {
            ModLogger.error("Error setting ingredients: {}", e.getMessage(), e);
        }
        
        notifyListenersOfChange();
    }
}
