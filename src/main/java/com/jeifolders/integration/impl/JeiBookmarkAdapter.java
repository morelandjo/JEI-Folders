package com.jeifolders.integration.impl;

import com.jeifolders.gui.bookmarks.FolderBookmarkList;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.util.FocusUtil;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter that connects our folder bookmarks to JEI's ingredient grid system.
 * Implements IIngredientGridSource to provide ingredients to JEI's grid.
 */
public class JeiBookmarkAdapter implements IIngredientGridSource {
    private final FolderBookmarkList bookmarkList;
    private ITypedIngredient<?> typedIngredient;
    private List<Runnable> listeners = new ArrayList<>();
    private List<SourceListChangedListener> sourceListChangedListeners = new ArrayList<>();

    /**
     * Creates a new JeiBookmarkAdapter with the specified bookmark list.
     * This constructor is called via reflection from FolderBookmarkOverlay.
     */
    public JeiBookmarkAdapter(FolderBookmarkList bookmarkList) {
        this.bookmarkList = bookmarkList;
        ModLogger.debug("Created JEI bookmark adapter for folder");
    }

    /**
     * Gets the folder bookmark list associated with this adapter.
     */
    public FolderBookmarkList getFolderBookmarkList() {
        return bookmarkList;
    }

    /**
     * Adds a listener that gets called when the bookmark list changes.
     */
    public void addListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Adds a source list changed listener that gets called when the source list changes.
     * This is required by the IIngredientGridSource interface.
     */
    @Override
    public void addSourceListChangedListener(IIngredientGridSource.SourceListChangedListener listener) {
        if (listener != null && !sourceListChangedListeners.contains(listener)) {
            sourceListChangedListeners.add(listener);
        }
    }

    /**
     * Notifies all listeners that the bookmark list has changed.
     */
    public void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                ModLogger.error("Error in bookmark listener: {}", e.getMessage(), e);
            }
        }

        // Also notify the source list changed listeners
        for (SourceListChangedListener listener : sourceListChangedListeners) {
            try {
                // Use the correct method name from the interface: onSourceListChanged()
                listener.onSourceListChanged();
            } catch (Exception e) {
                ModLogger.error("Error in source list changed listener: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Adds a bookmark to the list.
     */
    public boolean addBookmark(ITypedIngredient<?> ingredient) {
        if (ingredient != null) {
            bookmarkList.addBookmark(ingredient);
            return true;
        }
        return false;
    }

    /**
     * Gets the list of ingredients to display in the grid.
     * This is a custom method used by our implementation, not from IIngredientGridSource.
     */
    public List<ITypedIngredient<?>> getIngredients() {
        try {
            if (bookmarkList != null) {
                // Get all objects from the bookmark list
                List<?> bookmarks = bookmarkList.getAllBookmarks();
                if (bookmarks.isEmpty()) {
                    return Collections.emptyList();
                }
                
                // Convert objects to ITypedIngredient
                List<ITypedIngredient<?>> result = new ArrayList<>();
                for (Object bookmark : bookmarks) {
                    if (bookmark instanceof ITypedIngredient<?>) {
                        // Already a typed ingredient
                        result.add((ITypedIngredient<?>) bookmark);
                    } else if (bookmark instanceof BookmarkIngredient) {
                        // Wrapped in our BookmarkIngredient class
                        ITypedIngredient<?> ingredient = ((BookmarkIngredient) bookmark).getTypedIngredient();
                        if (ingredient != null) {
                            result.add(ingredient);
                        }
                    }
                }
                
                if (result.isEmpty() && !bookmarks.isEmpty()) {
                    ModLogger.warn("Failed to convert bookmarks to ingredients: {} bookmarks resulted in 0 ingredients", 
                        bookmarks.size());
                }
                
                return result;
            }
        } catch (Exception e) {
            ModLogger.error("Error getting ingredients from bookmark list: {}", e.getMessage(), e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Implements the required getElements method from IIngredientGridSource.
     * This method returns all elements without filtering by class.
     * This method is critical for JEI to display ingredients in the grid.
     */
    @Override
    public List<IElement<?>> getElements() {
        // Get all ingredients and convert them to IElement instances for JEI
        List<ITypedIngredient<?>> ingredients = getIngredients();
        if (ingredients.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Transform ingredients into elements for JEI's ingredient grid
        List<IElement<?>> elements = new ArrayList<>(ingredients.size());
        for (ITypedIngredient<?> ingredient : ingredients) {
            try {
                // Create an ingredient element for each ingredient
                elements.add(createIngredientElement(ingredient));
            } catch (Exception e) {
                ModLogger.error("Error creating ingredient element: {}", e.getMessage(), e);
            }
        }
        
        return elements;
    }
    
    /**
     * Helper method to create an IElement from a typed ingredient.
     */
    private <T> IElement<T> createIngredientElement(ITypedIngredient<T> ingredient) {
        try {
            // Directly instantiate IngredientElement instead of using reflection
            return new IngredientElement<>(ingredient);
        } catch (Exception e) {
            ModLogger.error("Failed to create ingredient element: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create ingredient element", e);
        }
    }

    /**
     * Gets the ingredient under the cursor to show recipes for.
     */
    public void setClickedIngredient(ITypedIngredient<?> ingredient) {
        this.typedIngredient = ingredient;
    }

    /**
     * Shows recipes for the clicked ingredient.
     */
    public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> ingredientRoles) {
        // Show recipes for this ingredient with the specified roles
        try {
            // Convert the ingredient to a focus and show it in the recipes GUI
            if (recipesGui != null && typedIngredient != null && focusUtil != null) {
                // If no specific roles are provided, use default (INPUT)
                if (ingredientRoles == null || ingredientRoles.isEmpty()) {
                    // Create a default role list with INPUT
                    List<RecipeIngredientRole> defaultRoles = List.of(RecipeIngredientRole.INPUT);
                    // Use createFocuses to create focuses for this ingredient
                    List<IFocus<?>> focuses = focusUtil.createFocuses(typedIngredient, defaultRoles);
                    // Show each focus if available
                    for (IFocus<?> focus : focuses) {
                        recipesGui.show(focus);
                    }
                } else {
                    // Use the provided roles to create focuses
                    List<IFocus<?>> focuses = focusUtil.createFocuses(typedIngredient, ingredientRoles);
                    // Show each focus if available
                    for (IFocus<?> focus : focuses) {
                        recipesGui.show(focus);
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error showing recipes for ingredient: {}", e.getMessage(), e);
        }
    }
}