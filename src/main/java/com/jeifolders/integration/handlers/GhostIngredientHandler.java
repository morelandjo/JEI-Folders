package com.jeifolders.integration.handlers;

import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.DragDropService;
import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Ghost ingredient handler for folder drag-and-drop operations.
 * Handles interaction between JEI's drag system and our folder UI.
 */
public class GhostIngredientHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
    // Track if we're in an actual drag (not just hover) using mouse state
    private boolean mouseButtonDown = false;
    private final DragDropService dragDropService;
    
    public GhostIngredientHandler() {
        this.dragDropService = JEIIntegrationAPI.getDragDropService();
    }
    
    @Override
    public <I> List<Target<I>> getTargetsTyped(@Nonnull T gui, @Nonnull ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        
        // If doStart is true, JEI is informing us that a drag operation has started
        boolean dragStarting = doStart;
        
        // For additional precision, we also check mouse state directly
        Minecraft minecraft = Minecraft.getInstance();
        boolean leftMouseDown = minecraft.mouseHandler.isLeftPressed();
        
        // Detect start of drag through either doStart or mouse state
        if (dragStarting || leftMouseDown) {
            // This is either the start of a drag or an ongoing drag
            if (!mouseButtonDown) {
                // First detection of this drag operation
                mouseButtonDown = true;
                ModLogger.debug("[DRAG-DEBUG] Ingredient drag detected, targets will be created");
                
                // Set the dragged ingredient for the actual drag
                dragDropService.setDraggedIngredient(ingredient);
                dragDropService.setActuallyDragging(true);
            }
            
            // Now add the targets for the drag operation
            if (FolderUIController.isInitialized()) {
                FolderUIController controller = FolderUIController.getInstance();
                
                // Add targets for folder buttons
                controller.getFolderButtons().forEach(buttonInstance -> {
                    targets.add(createFolderTarget(buttonInstance, controller, ingredient));
                });

                // Add target for bookmark display area if available
                if (controller.isBookmarkAreaAvailable()) {
                    Rect2i bookmarkArea = controller.getBookmarkDisplayArea();
                    targets.add(createBookmarkAreaTarget(bookmarkArea, controller, ingredient));
                }
            }
        } else if (mouseButtonDown) {
            // Mouse button was down but now it's up - reset the state
            mouseButtonDown = false;
            dragDropService.setActuallyDragging(false);
            dragDropService.clearDraggedIngredient();
            ModLogger.debug("[DRAG-DEBUG] Drag operation ended");
        }
        
        return targets;
    }

    private <I> Target<I> createFolderTarget(
            FolderButton button,
            FolderUIController controller,
            ITypedIngredient<I> ingredient) {

        return new Target<I>() {
            @Override
            public Rect2i getArea() {
                // Use button's area directly instead of creating a new Rect2i
                return new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight());
            }

            @Override
            public void accept(I ingredientObj) {
                ModLogger.debug("[HOVER-FIX] Ingredient dropped on folder: {}", button.getFolder().getName());
                
                // Get the center of the button for the drop position
                int centerX = button.getX() + button.getWidth() / 2;
                int centerY = button.getY() + button.getHeight() / 2;
                
                // Create and pass the unified ingredient to the controller
                IIngredient unifiedIngredient = IngredientManager.getInstance().createIngredient(ingredient);
                controller.handleIngredientDrop(centerX, centerY, unifiedIngredient);
            }
        };
    }

    private <I> Target<I> createBookmarkAreaTarget(
            Rect2i bookmarkArea,
            FolderUIController controller,
            ITypedIngredient<I> ingredient) {

        return new Target<I>() {
            @Override
            public Rect2i getArea() {
                // Return the bookmark area directly
                return bookmarkArea;
            }

            @Override
            public void accept(I ingredientObj) {
                ModLogger.debug("[HOVER-FIX] Ingredient dropped on bookmark display");
                
                // Calculate center of bookmark area for better drop accuracy
                int centerX = bookmarkArea.getX() + bookmarkArea.getWidth() / 2;
                int centerY = bookmarkArea.getY() + bookmarkArea.getHeight() / 2;
                
                // Create and pass the unified ingredient to the controller
                IIngredient unifiedIngredient = IngredientManager.getInstance().createIngredient(ingredient);
                controller.handleIngredientDrop(centerX, centerY, unifiedIngredient);
            }
        };
    }

    @Override
    public void onComplete() {
        ModLogger.debug("[HOVER-FIX] Ghost ingredient drag completed");
        mouseButtonDown = false;
        dragDropService.setActuallyDragging(false);
        dragDropService.clearDraggedIngredient();
    }
}