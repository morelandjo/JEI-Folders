package com.jeifolders.integration;

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
        Optional<IJeiRuntime> runtimeOptional = JEIIntegration.getJeiRuntime();
        if (runtimeOptional.isEmpty()) {
            ModLogger.error("JEI runtime is not available for rendering ingredients.");
            return;
        }
        
        IJeiRuntime jeiRuntime = runtimeOptional.get();

        try {
            IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
            IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(ingredient.getType());
            renderer.render(graphics, ingredient.getIngredient(), x, y);
        } catch (Exception e) {
            ModLogger.error("Error rendering ingredient: {}", e.getMessage(), e);
        }
    }
}
