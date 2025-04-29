package com.jeifolders.integration;

import com.jeifolders.ui.components.contents.FolderBookmarkList;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.elements.IngredientElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Manages bookmark operations in JEI.
 */
public class JEIBookmarkManager {
    private static final JEIService jeiService = JEIIntegrationFactory.getJEIService();
    private static final IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
    private static final BookmarkService bookmarkService = JEIIntegrationFactory.getBookmarkService();

    /**
     * Gets all JEI bookmarks.
     */
    public static List<ITypedIngredient<?>> getAllBookmarks() {
        Optional<Object> runtimeOptional = jeiService.getJeiRuntime();
        if (runtimeOptional.isEmpty()) {
            ModLogger.error("JEI runtime is not available for fetching bookmarks.");
            return new ArrayList<>();
        }
        
        Object runtimeObj = runtimeOptional.get();
        if (!(runtimeObj instanceof IJeiRuntime)) {
            ModLogger.error("JEI runtime object is not of expected type");
            return new ArrayList<>();
        }
        
        IJeiRuntime jeiRuntime = (IJeiRuntime) runtimeObj;
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
                Optional<ITypedIngredient<?>> typedIngredientOpt = ingredientService.getTypedIngredientFromObject(ingredient);
                if (typedIngredientOpt.isEmpty()) {
                    return false;
                }
                typedIngredient = typedIngredientOpt.get();
            }

            // Get the key for the ingredient
            String key = ingredientService.getKeyForIngredient(typedIngredient);
            if (key == null || key.isEmpty()) {
                return false;
            }

            // Check if any bookmark matches this key
            for (ITypedIngredient<?> bookmark : bookmarks) {
                String bookmarkKey = ingredientService.getKeyForIngredient(bookmark);
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
        bookmarkService.notifySourceListChanged(listeners);
    }

    /**
     * Implementation of IIngredientGridSource that adapts our generic FolderBookmarkList to the JEI-specific interface.
     */
    public static class JeiBookmarkAdapter implements IIngredientGridSource {
        private final FolderBookmarkList bookmarkList;
        
        public JeiBookmarkAdapter(FolderBookmarkList bookmarkList) {
            this.bookmarkList = bookmarkList;
        }
        
        @Override
        public List<IElement<?>> getElements() {
            // Convert BookmarkIngredient list to typed ingredients before calling convertToElementList
            List<Object> ingredientObjects = new ArrayList<>();
            for (BookmarkIngredient bookmark : bookmarkList.getAllBookmarks()) {
                if (bookmark != null) {
                    ITypedIngredient<?> typedIngredient = bookmark.getTypedIngredient();
                    if (typedIngredient != null) {
                        ingredientObjects.add(typedIngredient);
                    }
                }
            }
            return convertToElementList(ingredientObjects);
        }
        
        @Override
        public void addSourceListChangedListener(@Nonnull SourceListChangedListener listener) {
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
            if (ingredient == null) {
                ModLogger.error("Cannot create ingredient element from null ingredient");
                return null;
            }
            
            return createIngredientElementSafe(ingredient);
        } catch (Exception e) {
            ModLogger.error("Failed to create ingredient element", e);
        }
        return null;
    }

    /**
     * Type-safe helper method to create ingredient elements
     */
    private static <T> IElement<T> createIngredientElementSafe(ITypedIngredient<?> ingredient) {
        try {
            @SuppressWarnings("unchecked")
            ITypedIngredient<T> typedIngredient = (ITypedIngredient<T>) ingredient;
            return new IngredientElement<>(typedIngredient);
        } catch (ClassCastException e) {
            ModLogger.error("Type mismatch creating ingredient element: {}", e.getMessage());
            return null;
        }
    }
}
