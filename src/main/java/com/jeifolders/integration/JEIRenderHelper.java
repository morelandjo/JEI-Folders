package com.jeifolders.integration;

import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.IngredientService;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Optional;

/**
 * Helper class for JEI-related rendering operations.
 */
public class JEIRenderHelper {
    
    /**
     * Renders an ingredient at the specified position.
     *
     * @param graphics The GuiGraphics context
     * @param ingredient The ingredient to render
     * @param x The x position
     * @param y The y position
     * @param size The size to render at
     * @param mouseX The current mouse x position
     * @param mouseY The current mouse y position
     */
    public static void renderIngredient(GuiGraphics graphics, Object ingredient, int x, int y, int size, int mouseX, int mouseY) {
        if (ingredient == null) {
            return;
        }
        
        try {
            IJeiRuntime jeiRuntime = getJeiRuntime().orElse(null);
            if (jeiRuntime == null) {
                ModLogger.warn("Cannot render ingredient: JEI runtime not available");
                return;
            }
            
            // Get the wrapped ingredient type if available
            ITypedIngredient<?> typedIngredient = getTypedIngredient(ingredient);
            if (typedIngredient != null) {
                // Use the correct generic method for rendering with the current JEI API
                var renderer = jeiRuntime.getIngredientManager()
                    .getIngredientRenderer(typedIngredient.getType());
                
                // Set up the transformation for the ingredient position
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0);
                
                // Use the correctly typed value by getting the specific type
                var ingredientValue = typedIngredient.getIngredient();
                
                // Just call render directly with unchecked cast, since we trust JEI's type system
                renderIngredientUnchecked(graphics, ingredientValue, renderer);
                
                graphics.pose().popPose();
            } else {
                ModLogger.error("Unable to render unknown ingredient type: {}", 
                    ingredient.getClass().getSimpleName());
            }
        } catch (Exception e) {
            ModLogger.error("Error rendering ingredient: {}", e.getMessage());
        }
    }
    
    /**
     * Get the JEI ITypedIngredient wrapper for an object, handling different supported types.
     *
     * @param ingredient The ingredient object
     * @return The ITypedIngredient wrapper or null if not available
     */
    public static ITypedIngredient<?> getTypedIngredient(Object ingredient) {
        if (ingredient == null) {
            return null;
        }
        
        try {
            // Get the ingredient service to use for conversion
            IngredientService ingredientService = JEIIntegrationAPI.getIngredientService();
            
            // If it's already an ITypedIngredient, return it directly
            if (ingredient instanceof ITypedIngredient) {
                return (ITypedIngredient<?>) ingredient;
            }
            
            // If it's a BookmarkIngredient, extract the typed ingredient
            if (ingredient instanceof BookmarkIngredient) {
                return ((BookmarkIngredient) ingredient).getTypedIngredient();
            }
            
            // If it's a TypedIngredient wrapper, extract the wrapped ingredient
            if (ingredient instanceof TypedIngredient) {
                Object wrapped = ((TypedIngredient) ingredient).getWrappedIngredient();
                if (wrapped instanceof ITypedIngredient) {
                    return (ITypedIngredient<?>) wrapped;
                }
            }
            
            // For other types, create a unified ingredient and get its typed ingredient
            return ingredientService.createIngredient(ingredient).getTypedIngredient();
        } catch (Exception e) {
            ModLogger.error("Error getting typed ingredient: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the JEI runtime if available.
     *
     * @return An Optional containing the IJeiRuntime if available
     */
    public static Optional<IJeiRuntime> getJeiRuntime() {
        return JEIIntegrationAPI.getIntegrationService().getJeiRuntime();
    }

    /**
     * Renders an ingredient using an unchecked cast.
     * This avoids generic type issues when dealing with wildcard types.
     *
     * @param graphics The GuiGraphics context
     * @param ingredientValue The ingredient value
     * @param renderer The renderer for this ingredient type
     */
    @SuppressWarnings("unchecked")
    private static void renderIngredientUnchecked(GuiGraphics graphics, 
                                               Object ingredientValue, 
                                               mezz.jei.api.ingredients.IIngredientRenderer<?> renderer) {
        try {
            // Use unchecked cast since we trust that JEI's API has already validated type compatibility
            ((mezz.jei.api.ingredients.IIngredientRenderer<Object>)renderer).render(graphics, ingredientValue);
        } catch (Exception e) {
            ModLogger.error("Error rendering ingredient: {}", e.getMessage());
        }
    }
}
