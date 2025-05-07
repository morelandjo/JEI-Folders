package com.jeifolders.integration;

import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.DragDropService;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handler for ghost ingredients that allows dropping JEI ingredients onto folders.
 *
 * @param <T> The screen class type
 */
public class FolderGhostIngredientHandler<T extends Screen> implements IGhostIngredientHandler<T> {
    private final Class<T> screenClass;
    private final DragDropService dragDropService = JEIIntegrationAPI.getDragDropService();
    
    /**
     * Creates a new ghost ingredient handler for the specified screen class
     *
     * @param screenClass The class of screen to handle
     */
    public FolderGhostIngredientHandler(Class<T> screenClass) {
        this.screenClass = screenClass;
    }
    
    @Override
    public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> typedIngredient, boolean doStart) {
        try {
            FolderUIController controller = FolderUIController.getInstance();
            if (controller == null) {
                return Collections.emptyList();
            }
            
            // Create a list of targets, one for each folder button
            List<Target<I>> targets = new ArrayList<>();
            
            // Get the folder buttons from the controller
            List<FolderButton> buttons = controller.getFolderButtons();
            for (FolderButton button : buttons) {
                targets.add(createTarget(button, controller, typedIngredient));
            }
            
            return targets;
        } catch (Exception e) {
            ModLogger.error("Error getting ghost ingredient targets: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Creates a target for a folder button
     *
     * @param button The folder button
     * @param controller The folder UI controller
     * @param typedIngredient The typed ingredient
     * @return The target
     */
    private <I> Target<I> createTarget(
            FolderButton button, 
            FolderUIController controller, 
            ITypedIngredient<I> typedIngredient) {
        
        return new Target<I>() {
            @Override
            public Rect2i getArea() {
                return new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight());
            }
            
            @Override
            public void accept(I ingredient) {
                try {
                    ModLogger.debug("Ghost ingredient accepted for folder: {}", button.getFolder().getName());
                    
                    // Get the folder center for better drop position
                    int centerX = button.getX() + button.getWidth() / 2;
                    int centerY = button.getY() + button.getHeight() / 2;
                    
                    // Create a unified ingredient from the JEI ingredient
                    IIngredient unifiedIngredient = JEIIntegrationAPI.getIngredientService().createIngredient(typedIngredient);
                    
                    // Pass to the controller for handling
                    controller.handleIngredientDrop(centerX, centerY, unifiedIngredient);
                    
                    // Ensure any relevant state is updated
                    dragDropService.clearDraggedIngredient();
                } catch (Exception e) {
                    ModLogger.error("Error accepting ghost ingredient: {}", e.getMessage(), e);
                }
            }
        };
    }
    
    @Override
    public void onComplete() {
        // Nothing to do here, the targets handle acceptance
    }
}