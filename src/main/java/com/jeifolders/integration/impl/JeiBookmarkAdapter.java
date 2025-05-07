package com.jeifolders.integration.impl;

import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.ui.components.contents.FolderBookmarkList;
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
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Adapter that connects our folder bookmarks to JEI's ingredient grid system.
 * Implements IIngredientGridSource to provide ingredients to JEI's grid.
 */
public class JEIBookmarkAdapter implements IIngredientGridSource {
    private final FolderBookmarkList bookmarkList;
    private ITypedIngredient<?> typedIngredient;
    private List<Runnable> listeners = new ArrayList<>();
    private List<SourceListChangedListener> sourceListChangedListeners = new ArrayList<>();

    /**
     * Creates a new JEIBookmarkAdapter with the specified bookmark list.
     * This constructor is called via reflection from FolderBookmarkOverlay.
     */
    public JEIBookmarkAdapter(FolderBookmarkList bookmarkList) {
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
    public void addSourceListChangedListener(@Nonnull IIngredientGridSource.SourceListChangedListener listener) {
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
     * Adds an ingredient to the list.
     */
    public boolean addIngredient(ITypedIngredient<?> ingredient) {
        if (ingredient != null) {
            try {
                // Convert ITypedIngredient to unified Ingredient
                IIngredient unifiedIngredient = JEIIntegrationAPI.createIngredient(ingredient);
                
                // Generate a key for the ingredient
                String key = JEIIntegrationAPI.getKeyForIngredient(ingredient);
                if (key == null || key.isEmpty()) {
                    ModLogger.warn("Failed to generate key for ingredient");
                    return false;
                }
                
                // Add the ingredient with the generated key
                if (bookmarkList.addIngredient(unifiedIngredient, key)) {
                    // Notification is handled by the bookmark list
                    return true;
                }
            } catch (Exception e) {
                ModLogger.error("Error adding ingredient: {}", e.getMessage(), e);
            }
        }
        return false;
    }
    
    /**
     * Clears all ingredients from the list.
     */
    public void clearIngredients() {
        bookmarkList.clear();
    }

    /**
     * Gets the list of JEI typed ingredients to display in the grid.
     */
    public List<ITypedIngredient<?>> getIngredients() {
        try {
            if (bookmarkList != null) {
                // Get all unified ingredients from the bookmark list
                List<IIngredient> unifiedIngredients = bookmarkList.getAllIngredients();
                if (unifiedIngredients.isEmpty()) {
                    return Collections.emptyList();
                }
                
                // Extract the JEI typed ingredients from the unified ingredients
                List<ITypedIngredient<?>> result = new ArrayList<>();
                for (IIngredient ingredient : unifiedIngredients) {
                    ITypedIngredient<?> typedIngredient = ingredient.getTypedIngredient();
                    if (typedIngredient != null) {
                        result.add(typedIngredient);
                    }
                }
                
                if (result.isEmpty() && !unifiedIngredients.isEmpty()) {
                    ModLogger.warn("Failed to extract typed ingredients: {} ingredients yielded 0 typed ingredients", 
                        unifiedIngredients.size());
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
        // Get all typed ingredients and convert them to IElement instances for JEI
        List<ITypedIngredient<?>> typedIngredients = getIngredients();
        if (typedIngredients.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Transform ingredients into elements for JEI's ingredient grid
        List<IElement<?>> elements = new ArrayList<>(typedIngredients.size());
        for (ITypedIngredient<?> ingredient : typedIngredients) {
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

    /**
     * Gets the ingredient at the specified coordinates, if any.
     *
     * @param mouseX X coordinate
     * @param mouseY Y coordinate
     * @return Optional containing the unified Ingredient if found
     */
    public Optional<IIngredient> getIngredientAt(double mouseX, double mouseY) {
        try {
            List<IElement<?>> elements = getElements();
            for (IElement<?> element : elements) {
                // Check if the element contains the mouse coordinates
                if (element instanceof IngredientElement<?> ingredientElement) {
                    // Get the ingredient from the element
                    ITypedIngredient<?> typedIngredient = ingredientElement.getTypedIngredient();
                    
                    // Since we can't directly check bounds, we'll look up the ingredient in our list
                    // and consider any ingredient in the list as potentially at this position.
                    // For proper position checking, we'd need to enhance this with JEI positional data.
                    List<IIngredient> allIngredients = bookmarkList.getAllIngredients();
                    for (IIngredient ingredient : allIngredients) {
                        if (ingredient.getTypedIngredient() == typedIngredient) {
                            return Optional.of(ingredient);
                        }
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error getting ingredient at mouse position: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }
}