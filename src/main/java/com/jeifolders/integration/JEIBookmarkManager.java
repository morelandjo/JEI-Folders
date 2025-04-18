package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import com.jeifolders.gui.FolderBookmarkList;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.elements.IngredientElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages bookmark operations in JEI.
 */
public class JEIBookmarkManager {

    /**
     * Gets all JEI bookmarks.
     */
    public static List<ITypedIngredient<?>> getAllBookmarks() {
        Optional<IJeiRuntime> runtimeOptional = JEIIntegration.getJeiRuntime();
        if (runtimeOptional.isEmpty()) {
            ModLogger.error("JEI runtime is not available for fetching bookmarks.");
            return new ArrayList<>();
        }
        
        IJeiRuntime jeiRuntime = runtimeOptional.get();
        IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
        List<ITypedIngredient<?>> bookmarks = new ArrayList<>();

        // Fetch the ingredient under the mouse as a placeholder for full bookmark access
        Optional<ITypedIngredient<?>> ingredient = bookmarkOverlay.getIngredientUnderMouse();
        ingredient.ifPresent(bookmarks::add);

        return bookmarks;
    }

    /**
     * Checks if an ingredient is bookmarked in JEI.
     * This wrapper handles any object type, converting it to a typed ingredient if needed.
     */
    public static boolean isIngredientBookmarkedInJEI(Object ingredient) {
        if (ingredient == null) {
            return false;
        }
        
        try {
            // Get all bookmarks
            List<ITypedIngredient<?>> bookmarks = getAllBookmarks();
            
            // Convert ingredient to typed ingredient if needed
            ITypedIngredient<?> typedIngredient;
            if (ingredient instanceof ITypedIngredient<?>) {
                typedIngredient = (ITypedIngredient<?>) ingredient;
            } else {
                typedIngredient = JEIIngredientManager.getTypedIngredientFromObject(ingredient);
                if (typedIngredient == null) {
                    return false;
                }
            }

            // Get the key for the ingredient
            String key = JEIIngredientManager.getKeyForIngredient(typedIngredient);
            if (key == null || key.isEmpty()) {
                return false;
            }

            // Check if any bookmark matches this key
            for (ITypedIngredient<?> bookmark : bookmarks) {
                String bookmarkKey = JEIIngredientManager.getKeyForIngredient(bookmark);
                if (key.equals(bookmarkKey)) {
                    return true;
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error checking if ingredient is bookmarked: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Converts a list of bookmarks to JEI elements.
     * This is used by FolderBookmarkList to get elements for display.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> convertBookmarksToElements(List<Object> bookmarks) {
        List<Object> elements = new ArrayList<>();
        
        for (Object bookmark : bookmarks) {
            if (bookmark instanceof ITypedIngredient<?>) {
                IElement<?> element = createIngredientElement((ITypedIngredient<?>) bookmark);
                if (element != null) {
                    elements.add(element);
                }
            }
        }
        
        return elements;
    }
    
    /**
     * Notifies JEI source list listeners that the list has changed.
     * Used by FolderBookmarkList to avoid direct JEI dependencies.
     */
    public static void notifySourceListChanged(List<Object> listeners) {
        for (Object listener : listeners) {
            if (listener instanceof IIngredientGridSource.SourceListChangedListener) {
                ((IIngredientGridSource.SourceListChangedListener) listener).onSourceListChanged();
            }
        }
    }

    /**
     * Special implementation of IIngredientGridSource that adapts our generic FolderBookmarkList
     * to the JEI-specific interface.
     */
    public static class JeiBookmarkAdapter implements IIngredientGridSource {
        private final FolderBookmarkList bookmarkList;
        
        public JeiBookmarkAdapter(FolderBookmarkList bookmarkList) {
            this.bookmarkList = bookmarkList;
        }
        
        @Override
        public List<IElement<?>> getElements() {
            // Use a properly typed conversion method instead of an unchecked cast
            return convertToElementList(bookmarkList.getAllBookmarks());
        }
        
        @Override
        public void addSourceListChangedListener(SourceListChangedListener listener) {
            bookmarkList.addSourceListChangedListener(listener);
        }
    }
    
    /**
     * Converts a list of bookmarks to a properly typed list of JEI elements.
     */
    private static List<IElement<?>> convertToElementList(List<Object> bookmarks) {
        List<IElement<?>> elements = new ArrayList<>();
        
        for (Object bookmark : bookmarks) {
            if (bookmark instanceof ITypedIngredient<?>) {
                IElement<?> element = createIngredientElement((ITypedIngredient<?>) bookmark);
                if (element != null) {
                    elements.add(element);
                }
            }
        }
        
        return elements;
    }
    
    /**
     * Creates a JEI element for an ingredient to be used in the bookmark display
     */
    public static IElement<?> createIngredientElement(ITypedIngredient<?> ingredient) {
        try {
            // Use the constructor directly instead of a non-existent create method
            return new IngredientElement<>(ingredient);
        } catch (Exception e) {
            ModLogger.error("Failed to create ingredient element", e);
        }
        return null;
    }
}
