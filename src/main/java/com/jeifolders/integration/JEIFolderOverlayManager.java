package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import com.jeifolders.data.Folder;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.Collections;
import java.util.List;

/**
 * Utility methods for working with folder overlays and JEI integration.
 */
public final class JEIFolderOverlayManager {
    private JEIFolderOverlayManager() {
        // Utility class, no instantiation
    }

    /**
     * Gets ingredients for a folder by its bookmark keys
     */
    public static List<ITypedIngredient<?>> getIngredientsForFolder(Folder folder) {
        if (folder == null) {
            return Collections.emptyList();
        }
        
        List<String> bookmarkKeys = folder.getBookmarkKeys();
        return JEIIngredientManager.getIngredientsForKeys(bookmarkKeys);
    }
    
    /**
     * Logs diagnostic information about a folder's bookmarks
     */
    public static void logFolderBookmarkInfo(Folder folder) {
        if (folder == null) {
            ModLogger.debug("No folder provided for logging");
            return;
        }
        
        List<String> bookmarkKeys = folder.getBookmarkKeys();
        ModLogger.debug("Bookmark keys for folder '{}': {}", folder.getName(), bookmarkKeys);
        
        List<ITypedIngredient<?>> ingredients = JEIIngredientManager.getIngredientsForKeys(bookmarkKeys);
        ModLogger.debug("Found {} ingredients for folder '{}'", ingredients.size(), folder.getName());
    }
}
