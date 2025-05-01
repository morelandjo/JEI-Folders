package com.jeifolders.integration;

import com.jeifolders.integration.ingredient.Ingredient;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Optional;

/**
 * Handles rendering for JEI ingredients.
 */
public class JEIRenderHelper {

    /**
     * Renders an ingredient at the specified position.
     * @param ingredient The ingredient to render.
     * @param graphics The graphics context.
     * @param x The x-coordinate for rendering.
     * @param y The y-coordinate for rendering.
     */
    public static <T> void renderIngredient(ITypedIngredient<T> ingredient, GuiGraphics graphics, int x, int y) {
        Optional<IJeiRuntime> jeiRuntime = getJeiRuntime();
        if (jeiRuntime.isEmpty()) {
            ModLogger.error("JEI runtime is not available for rendering ingredients.");
            return;
        }

        try {
            IIngredientManager ingredientManager = jeiRuntime.get().getIngredientManager();
            IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(ingredient.getType());
            renderer.render(graphics, ingredient.getIngredient(), x, y);
        } catch (Exception e) {
            ModLogger.error("Error rendering ingredient: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Renders a unified Ingredient at the specified position.
     * @param unifiedIngredient The unified Ingredient to render.
     * @param graphics The graphics context.
     * @param x The x-coordinate for rendering.
     * @param y The y-coordinate for rendering.
     */
    public static void renderIngredient(Ingredient unifiedIngredient, GuiGraphics graphics, int x, int y) {
        if (unifiedIngredient == null) {
            ModLogger.error("Cannot render null unified ingredient");
            return;
        }
        
        ITypedIngredient<?> typedIngredient = unifiedIngredient.getTypedIngredient();
        if (typedIngredient == null) {
            ModLogger.error("Cannot render unified ingredient without a valid typed ingredient");
            return;
        }
        
        renderIngredient(typedIngredient, graphics, x, y);
    }
    
    /**
     * Renders a generic object as an ingredient at the specified position.
     * @param ingredient The object to render as an ingredient.
     * @param graphics The graphics context.
     * @param x The x-coordinate for rendering.
     * @param y The y-coordinate for rendering.
     * @return true if the ingredient was rendered successfully
     */
    public static boolean renderObject(Object ingredient, GuiGraphics graphics, int x, int y) {
        if (ingredient == null) {
            return false;
        }
        
        try {
            if (ingredient instanceof Ingredient) {
                renderIngredient((Ingredient) ingredient, graphics, x, y);
                return true;
            } else if (ingredient instanceof ITypedIngredient<?>) {
                renderIngredient((ITypedIngredient<?>) ingredient, graphics, x, y);
                return true;
            } else if (ingredient instanceof BookmarkIngredient) {
                BookmarkIngredient bookmarkIngredient = (BookmarkIngredient) ingredient;
                renderIngredient(bookmarkIngredient.getTypedIngredient(), graphics, x, y);
                return true;
            } else {
                // Try to get a typed ingredient from the object
                Optional<ITypedIngredient<?>> typedIngredientOpt = 
                    JEIIntegrationFactory.getIngredientService().getTypedIngredientFromObject(ingredient);
                
                if (typedIngredientOpt.isPresent()) {
                    renderIngredient(typedIngredientOpt.get(), graphics, x, y);
                    return true;
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error rendering object as ingredient: {}", e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * Helper method to get the JEI runtime
     */
    private static Optional<IJeiRuntime> getJeiRuntime() {
        // Use JEIIntegrationFactory.getJEIRuntime() instead of IngredientService
        return JEIIntegrationFactory.getJEIRuntime().getJeiRuntime();
    }
}
