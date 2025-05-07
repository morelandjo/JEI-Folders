package com.jeifolders.ui.components.contents;

import com.jeifolders.data.Folder;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.IngredientService;
import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.events.FolderEventDispatcher;
import com.jeifolders.ui.events.FolderEventType;
import com.jeifolders.util.ModLogger;
import mezz.jei.gui.overlay.IIngredientGridSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores and manages the list of ingredients for the active folder.
 */
public class FolderBookmarkList {
    private Folder folder;
    private List<IIngredient> ingredients = new ArrayList<>();
    private final Map<String, IIngredient> ingredientMap = new HashMap<>();
    private final FolderEventDispatcher eventDispatcher;

    // Access services
    private final IngredientService ingredientService = JEIIntegrationAPI.getIngredientService();

    private boolean batchUpdateMode = false;
    private boolean pendingNotification = false;

    // Add a notification state tracking variable
    private boolean notifyingListeners = false;

    // JEI integration - source list changed listeners
    private final List<IIngredientGridSource.SourceListChangedListener> sourceListChangedListeners = new ArrayList<>();

    /**
     * Creates a new bookmark list with the provided event dispatcher
     * 
     * @param eventDispatcher The folder event dispatcher to use for notifications
     */
    public FolderBookmarkList(FolderEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    /**
     * Sets the current folder and refreshes bookmarks
     */
    public void setFolder(Folder folder) {
        this.folder = folder;
        refreshBookmarks();
    }

    /**
     * Refreshes the bookmarks based on the current folder
     */
    private void refreshBookmarks() {
        ModLogger.info("FolderBookmarkList.refreshBookmarks() called");
        ingredients.clear();
        ingredientMap.clear();

        if (folder != null) {
            List<String> keys = folder.getBookmarkKeys();
            ModLogger.info("Processing {} bookmark keys", keys.size());
            
            if (!keys.isEmpty()) {
                // Use ingredient service to get ingredients for bookmark keys
                try {
                    startBatchUpdate();
                    for (String key : keys) {
                        ModLogger.info("Looking up ingredient for key: {}", key);
                        
                        // Get the ingredient via the service
                        Optional<IIngredient> ingredientOpt = ingredientService.getIngredientForKey(key);
                        
                        if (ingredientOpt.isPresent()) {
                            IIngredient ingredient = ingredientOpt.get();
                            ModLogger.info("Found ingredient for key: {}", key);
                            
                            ingredients.add(ingredient);
                            ingredientMap.put(key, ingredient);
                        } else {
                            ModLogger.info("No ingredient found for key: {}", key);
                        }
                    }
                    finishBatchUpdate();
                    
                    ModLogger.info("Refreshed bookmarks. Found {} ingredients out of {} keys", 
                        ingredients.size(), keys.size());
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
        
        // Prevent recursive notifications
        if (notifyingListeners) {
            ModLogger.debug("Preventing recursive listener notification in FolderBookmarkList");
            return;
        }

        try {
            notifyingListeners = true;
            
            if (folder != null) {
                eventDispatcher.fire(FolderEventType.FOLDER_CONTENTS_CHANGED)
                    .withFolder(folder)
                    .build();
            }

            // Notify JEI source list changed listeners
            notifySourceListChangedListeners();
        } finally {
            notifyingListeners = false;
        }
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
     * Gets all ingredients in this list
     */
    public List<IIngredient> getAllIngredients() {
        return Collections.unmodifiableList(ingredients);
    }

    /**
     * Adds an ingredient to this list
     * @param ingredient The ingredient to add
     * @param key The key for the ingredient
     * @return true if the ingredient was added
     */
    public boolean addIngredient(IIngredient ingredient, String key) {
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

        eventDispatcher.fire(FolderEventType.BOOKMARK_ADDED)
            .withFolder(folder)
            .withUnifiedIngredient(ingredient)
            .withBookmarkKey(key)
            .build();

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

        IIngredient ingredient = ingredientMap.remove(key);
        if (ingredient != null) {
            ingredients.remove(ingredient);
            folder.removeBookmarkKey(key);

            eventDispatcher.fire(FolderEventType.BOOKMARK_REMOVED)
                .withFolder(folder)
                .withUnifiedIngredient(ingredient)
                .withBookmarkKey(key)
                .build();

            return true;
        }
        return false;
    }

    /**
     * Removes an ingredient
     * @param ingredient The ingredient to remove
     * @return true if the ingredient was removed
     */
    public boolean removeIngredient(IIngredient ingredient) {
        if (folder == null || ingredient == null) {
            return false;
        }

        // Find the key for this ingredient
        String keyToRemove = null;
        for (Map.Entry<String, IIngredient> entry : ingredientMap.entrySet()) {
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

            eventDispatcher.fire(FolderEventType.BOOKMARKS_CLEARED)
                .withFolder(folder)
                .build();
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
    public Folder getFolder() {
        return folder;
    }
    
    /**
     * Sets the unified ingredients for this bookmark list
     * @param unifiedIngredients The list of unified ingredients to set
     */
    public void setUnifiedIngredients(List<IIngredient> unifiedIngredients) {
        ModLogger.info("setUnifiedIngredients called with {} ingredients", 
            unifiedIngredients != null ? unifiedIngredients.size() : 0);

        if (unifiedIngredients == null) {
            unifiedIngredients = new ArrayList<>();
        }
        
        // If the passed ingredient list is empty but we already have ingredients, keep our existing ingredients
        if (unifiedIngredients.isEmpty() && !ingredients.isEmpty()) {
            ModLogger.info("Empty ingredient list passed, but we have {} loaded ingredients - keeping them",
                ingredients.size());
            
            // Show the folder details for diagnosis
            if (folder != null) {
                ModLogger.info("Active folder: {} (ID: {}), has {} bookmark keys: {}", 
                    folder.getName(), folder.getId(), folder.getBookmarkKeys().size(), folder.getBookmarkKeys());
            }
            
            // Skip the clearing operation, keep the ingredients we already loaded
            notifyListenersOfChange();
            return;
        }
        
        // Don't clear if we're being given the same list as we already have (recursive call protection)
        if (!ingredients.isEmpty() && ingredients.size() == unifiedIngredients.size() && 
            ingredients.containsAll(unifiedIngredients)) {
            ModLogger.info("Same ingredients passed, skipping reprocessing");
            return;
        }
        
        // If the ingredient list is not empty, process it normally
        ModLogger.info("Processing {} ingredients", unifiedIngredients.size());
        
        // Clear current ingredients first, unless we're in a recursive call
        ingredients.clear();
        ingredientMap.clear();
        
        // Add all new ingredients in batch mode
        try {
            startBatchUpdate();
            int validCount = 0;
            for (IIngredient ingredient : unifiedIngredients) {
                if (ingredient == null || ingredient.getTypedIngredient() == null) {
                    ModLogger.warn("Skipping null/invalid ingredient");
                    continue;
                }
                
                // Try to get a key for this ingredient
                String key = ingredientService.getKeyForIngredient(ingredient.getTypedIngredient());
                if (key != null && !key.isEmpty() && !ingredientMap.containsKey(key)) {
                    ingredients.add(ingredient);
                    ingredientMap.put(key, ingredient);
                    validCount++;
                }
            }
            finishBatchUpdate();
            
            ModLogger.info("Set {} ingredients (out of {}) on bookmark list", 
                validCount, unifiedIngredients.size());
        } catch (Exception e) {
            ModLogger.error("Error setting ingredients: {}", e.getMessage(), e);
        }
        
        notifyListenersOfChange();
    }

    /**
     * Gets an ingredient by its key
     * 
     * @param key The key of the ingredient to retrieve
     * @return The ingredient, or null if not found
     */
    public IIngredient getIngredient(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return ingredientMap.get(key);
    }
}
