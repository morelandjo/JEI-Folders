package com.jeifolders.gui;

import com.jeifolders.util.ModLogger;
import com.jeifolders.gui.folderButtons.FolderButtonInterface;
import com.jeifolders.gui.folderButtons.FolderRenderingManager;
import com.jeifolders.integration.IngredientDragHandler;
import com.jeifolders.integration.TypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;

/**
 * Manages the detection of dragged ingredients from JEI globally.
 */
public class IngredientDragManager {
    private static IngredientDragManager instance;
    private boolean isDragging = false;
    private int dragStartX = -1;
    private int dragStartY = -1;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    
    // Delegate JEI-specific functionality to the handler in the integration package
    private final IngredientDragHandler ingredientDragHandler = new IngredientDragHandler();

    private IngredientDragManager() {
        // Register for Forge events
        NeoForge.EVENT_BUS.register(this);
    }

    public static IngredientDragManager getInstance() {
        if (instance == null) {
            instance = new IngredientDragManager();
        }
        return instance;
    }

    public void setJeiRuntime(Object jeiRuntime) {
        // Pass the runtime to the ingredient drag handler
        try {
            this.ingredientDragHandler.setJeiRuntime(jeiRuntime);
            ModLogger.info("GlobalIngredientDragManager initialized with JEI runtime");
        } catch (Exception e) {
            ModLogger.error("Failed to initialize drag handler with JEI runtime: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }

        if (event.getButton() == 0) { // Left mouse button
            // Start tracking for potential drag
            dragStartX = (int) event.getMouseX();
            dragStartY = (int) event.getMouseY();
        }
    }

    @SubscribeEvent
    public void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }

        if (event.getButton() == 0 && isDragging) { // Left mouse button
            // Get the currently active folder button interface
            FolderButtonInterface folderButton = FolderRenderingManager.getFolderButton();
            
            if (folderButton == null) {
                ModLogger.debug("No active folder button found for drop processing");
                resetDragState();
                return;
            }

            // Get the dragged ingredient from our wrapper
            Optional<TypedIngredient> typedIngredientOpt = ingredientDragHandler.getDraggedIngredient();
            if (typedIngredientOpt.isPresent()) {
                TypedIngredient typedIngredient = typedIngredientOpt.get();
                Object wrappedObj = typedIngredient.getWrappedIngredient();
                
                // Log the drop coordinates for debugging
                ModLogger.debug("Processing ingredient drop at ({}, {})", event.getMouseX(), event.getMouseY());
                
                try {
                    // First check if the drop should be handled by the folder button
                    // This includes both folder buttons and the active display
                    boolean handled = folderButton.handleIngredientDrop(event.getMouseX(), event.getMouseY(), wrappedObj);
                    
                    if (handled) {
                        ModLogger.info("Ingredient drop successfully handled by folder system");
                        event.setCanceled(true); // Prevent further processing
                    } else {
                        ModLogger.info("Ingredient drop not handled by folder system");
                    }
                } catch (Exception e) {
                    ModLogger.error("Error during ingredient drop handling: {}", e.getMessage(), e);
                }
            } else {
                ModLogger.debug("No ingredient being dragged at drop time");
            }

            resetDragState();
        }
    }

    @SubscribeEvent
    public void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }

        if (event.getMouseButton() == 0) { // Left mouse button
            lastMouseX = (int) event.getMouseX();
            lastMouseY = (int) event.getMouseY();

            if (dragStartX != -1 && !isDragging) {
                double dragDistSq = (dragStartX - lastMouseX) * (dragStartX - lastMouseX)
                        + (dragStartY - lastMouseY) * (dragStartY - lastMouseY);

                if (dragDistSq > 25) { // 5 pixels distance threshold
                    checkForJeiIngredientDrag();
                }
            }
        }
    }

    private void checkForJeiIngredientDrag() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player != null) {
            // Delegate to the JEI-specific handler in the integration package
            boolean dragDetected = ingredientDragHandler.checkForJeiIngredientDrag(player);
            if (dragDetected) {
                isDragging = true;
            }
        }
    }
    
    /**
     * Reset all drag state variables to their default values
     */
    private void resetDragState() {
        isDragging = false;
        dragStartX = -1;
        dragStartY = -1;
        ingredientDragHandler.resetDragState();
    }

    public boolean isDragging() {
        // Only return true if we're actually dragging (not just hovering)
        return isDragging && dragStartX != -1 && ingredientDragHandler.getDraggedIngredient().isPresent();
    }
}