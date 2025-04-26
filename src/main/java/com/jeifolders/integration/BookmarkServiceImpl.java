package com.jeifolders.integration;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.ITypedIngredient;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of BookmarkService that manages bookmarks for folders
 */
public class BookmarkServiceImpl implements BookmarkService {
    private static final BookmarkServiceImpl INSTANCE = new BookmarkServiceImpl();
    private final IngredientService ingredientService;
    private final FolderStorageService folderService;
    
    private BookmarkServiceImpl() {
        this.ingredientService = IngredientServiceImpl.getInstance();
        this.folderService = FolderStorageService.getInstance();
    }
    
    /**
     * Gets the singleton instance
     */
    public static BookmarkService getInstance() {
        return INSTANCE;
    }
    
    @Override
    public void addBookmarkToFolder(int folderId, Object ingredient) {
        String key = ingredientService.getKeyForIngredient(ingredient);
        if (key == null || key.isEmpty()) {
            ModLogger.warn("Failed to generate key for ingredient: {}", ingredient);
            return;
        }
        
        Optional<Folder> folder = folderService.getFolder(folderId);
        if (folder.isEmpty()) {
            ModLogger.warn("Could not find folder with id {} to add bookmark", folderId);
            return;
        }
        
        folder.get().addBookmarkKey(key);
        ModLogger.debug("Added bookmark {} to folder {}", key, folderId);
    }
    
    @Override
    public void removeBookmarkFromFolder(int folderId, Object ingredient) {
        String key = ingredientService.getKeyForIngredient(ingredient);
        if (key == null || key.isEmpty()) {
            ModLogger.warn("Failed to generate key for ingredient: {}", ingredient);
            return;
        }
        
        Optional<Folder> folder = folderService.getFolder(folderId);
        if (folder.isEmpty()) {
            ModLogger.warn("Could not find folder with id {} to remove bookmark", folderId);
            return;
        }
        
        folder.get().removeBookmarkKey(key);
        ModLogger.debug("Removed bookmark {} from folder {}", key, folderId);
    }
    
    @Override
    public List<ITypedIngredient<?>> getBookmarksForFolder(int folderId) {
        List<String> keys = getBookmarkKeysForFolder(folderId);
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        
        return ingredientService.getIngredientsForKeys(keys);
    }
    
    @Override
    public List<String> getBookmarkKeysForFolder(int folderId) {
        Optional<Folder> folder = folderService.getFolder(folderId);
        if (folder.isEmpty()) {
            ModLogger.warn("Could not find folder with id {}", folderId);
            return Collections.emptyList();
        }
        
        return folder.get().getBookmarkKeys();
    }
    
    @Override
    public boolean folderContainsBookmark(int folderId, Object ingredient) {
        String key = ingredientService.getKeyForIngredient(ingredient);
        if (key == null || key.isEmpty()) {
            return false;
        }
        
        Optional<Folder> folder = folderService.getFolder(folderId);
        return folder.isPresent() && folder.get().containsBookmark(key);
    }
    
    @Override
    public void notifySourceListChanged(List<?> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        
        // Use reflection to safely call JEI's internal methods
        try {
            for (Object listener : listeners) {
                try {
                    // Look for onStateChange method that takes no arguments
                    for (Method method : listener.getClass().getMethods()) {
                        if (method.getName().equals("onStateChange") && method.getParameterCount() == 0) {
                            method.invoke(listener);
                            break;
                        }
                    }
                } catch (Exception e) {
                    ModLogger.error("Error notifying listener {}: {}", 
                            listener.getClass().getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error in notifySourceListChanged: {}", e.getMessage(), e);
        }
    }
}