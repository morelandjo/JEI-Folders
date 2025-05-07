package com.jeifolders.integration;

import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.DragDropService;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Handles JEI-specific ingredient drag operations.
 * Contains all the JEI API functionality that was previously in GlobalIngredientDragManager.
 */
public class IngredientDragHandler {
    // Reference to the drag drop service
    private final DragDropService dragDropService = JEIIntegrationAPI.getDragDropService();
    private IIngredientManager ingredientManager;
    private ItemStack lastDraggedItem = ItemStack.EMPTY;

    /**
     * Initialize with the JEI runtime
     * 
     * @param jeiRuntime The JEI runtime object, which will be cast to IJeiRuntime
     */
    public void setJeiRuntime(Object jeiRuntime) {
        if (jeiRuntime instanceof mezz.jei.api.runtime.IJeiRuntime typedRuntime) {
            this.ingredientManager = typedRuntime.getIngredientManager();
            ModLogger.debug("IngredientDragHandler initialized with JEI runtime");
        } else {
            ModLogger.error("Failed to initialize IngredientDragHandler: Runtime object is not an IJeiRuntime");
        }
    }
    
    /**
     * Check if the player is carrying an item that can be converted to a JEI ingredient
     * 
     * @param player The Minecraft player
     * @return true if a dragged ingredient was detected and set
     */
    public boolean checkForJeiIngredientDrag(Player player) {
        if (ingredientManager == null || player == null) {
            return false;
        }

        ItemStack carriedStack = player.containerMenu.getCarried();
        if (!carriedStack.isEmpty() && !ItemStack.matches(carriedStack, lastDraggedItem)) {
            lastDraggedItem = carriedStack.copy();
            Optional<ITypedIngredient<ItemStack>> ingredient = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, carriedStack);
            if (ingredient.isPresent()) {
                dragDropService.setDraggedIngredient(ingredient.get());
                dragDropService.setActuallyDragging(true);
                ModLogger.debug("Detected potential JEI ingredient drag: {}", lastDraggedItem);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Reset drag state and clear any dragged ingredients
     */
    public void resetDragState() {
        lastDraggedItem = ItemStack.EMPTY;
        dragDropService.clearDraggedIngredient();
    }
    
    /**
     * Get the currently dragged ingredient, if any
     * 
     * @return The typed ingredient if available
     */
    public Optional<ITypedIngredient<?>> getDraggedIngredient() {
        // Use the DragDropService's method to directly get the ITypedIngredient
        return dragDropService.getDraggedITypedIngredient();
    }
    
    /**
     * Check if an ingredient is actually being dragged (not just hover)
     * 
     * @return true if an ingredient is being actively dragged
     */
    public boolean isActuallyDragging() {
        return dragDropService.isActuallyDragging();
    }
}