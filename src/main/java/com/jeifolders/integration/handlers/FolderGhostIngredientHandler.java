// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/handlers/FolderGhostIngredientHandler.java
package com.jeifolders.integration.handlers;

import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ghost ingredient handler specifically for folder interactions.
 * Handles the drop of ingredients onto folders and bookmark displays.
 */
public class FolderGhostIngredientHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
    private final Class<T> screenClass;
    
    public FolderGhostIngredientHandler(Class<T> screenClass) {
        this.screenClass = screenClass;
    }
    
    @Override
    @Nonnull
    public <I> List<Target<I>> getTargetsTyped(@Nonnull T screen, @Nonnull ITypedIngredient<I> ingredient, boolean doStart) {
        // Get the controller for the current screen
        FolderUIController controller = FolderUIController.getInstance();
        if (controller == null || !controller.isBookmarkAreaAvailable()) {
            return Collections.emptyList();
        }
        
        // Create targets for all folder buttons and the active bookmark display
        List<Target<I>> targets = new ArrayList<>();
        
        // Add targets for folder buttons
        controller.getFolderButtons().forEach(button -> {
            targets.add(createFolderTarget(button, controller, ingredient));
        });
        
        // Add target for the bookmark area if present
        Rect2i bookmarkArea = controller.getBookmarkDisplayArea();
        if (bookmarkArea != null) {
            targets.add(createBookmarkAreaTarget(bookmarkArea, controller, ingredient));
        }
        
        return targets;
    }
    
    @Override
    public void onComplete() {
        // Nothing to do here - drag state is managed by GhostIngredientHandler
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
}