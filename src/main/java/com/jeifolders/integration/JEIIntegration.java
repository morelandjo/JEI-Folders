package com.jeifolders.integration;

import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.IngredientService;
import com.jeifolders.integration.api.JEIIntegrationService;
import com.jeifolders.integration.api.DragDropService;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Central class for managing JEI integration.
 */
public class JEIIntegration {
    // Singleton instance
    private static JEIIntegration instance;

    // Dependencies
    private final FolderStorageService folderService;
    private final IngredientService ingredientService = JEIIntegrationAPI.getIngredientService();
    private final JEIIntegrationService integrationService = JEIIntegrationAPI.getIntegrationService();
    private final DragDropService dragDropService = JEIIntegrationAPI.getDragDropService();
    
    /**
     * Private constructor for singleton pattern
     */
    private JEIIntegration(FolderStorageService folderService) {
        this.folderService = folderService;
    }
    
    /**
     * Gets the singleton instance
     * 
     * @param folderService The folder storage service
     * @return The JEI integration instance
     */
    public static synchronized JEIIntegration getInstance(FolderStorageService folderService) {
        if (instance == null) {
            instance = new JEIIntegration(folderService);
        }
        return instance;
    }
    
    /**
     * Called when a screen is displayed to register any JEI handlers
     * 
     * @param screen The screen being displayed
     * @return true if the screen is handled
     */
    public boolean onScreenDisplay(Screen screen) {
        // No specific logic needed here currently
        return false;
    }
    
    /**
     * Checks if drag is allowed for the given ingredient
     * 
     * @param object The object being dragged
     * @return true if dragging is allowed
     */
    public boolean isDragAllowed(Object object) {
        // Implemented in subclasses
        return true;
    }
    
    /**
     * Checks if JEI is available.
     * 
     * @return true if JEI is available, false otherwise
     */
    public static boolean isJeiAvailable() {
        return JEIIntegrationAPI.getIntegrationService().isJeiRuntimeAvailable();
    }
    
    /**
     * Called when JEI runtime becomes available.
     */
    public void onJeiStarted() {
        ModLogger.debug("JEI started - initializing folders");
        // Load the folder data when JEI becomes available
        FolderUIController.getInstance().loadData();
    }
    
    /**
     * Registers a callback for when the JEI runtime becomes available
     * 
     * @param callback The callback to run
     */
    public static void registerRuntimeCallback(Consumer<IJeiRuntime> callback) {
        JEIIntegrationAPI.getIntegrationService().registerRuntimeCallback(callback);
    }
    
    /**
     * Registers a callback to initialize folder integration
     */
    public void registerFolderIntegrationCallback() {
        JEIIntegrationAPI.getIntegrationService().registerRuntimeCallback(r -> {
            onJeiStarted();
        });
    }
    
    /**
     * Gets the current JEI runtime, if available.
     * 
     * @return The JEI runtime
     */
    public Optional<IJeiRuntime> getJeiRuntime() {
        return JEIIntegrationAPI.getIntegrationService().getJeiRuntime();
    }
    
    /**
     * Sets an ingredient as being dragged
     */
    public void setDraggedIngredient(IIngredient ingredient) {
        if (ingredient == null) {
            clearDraggedIngredient();
            return;
        }
        
        // Get the ITypedIngredient from the IIngredient
        ITypedIngredient<?> typedIngredient = ingredient.getTypedIngredient();
        if (typedIngredient != null) {
            JEIIntegrationAPI.getDragDropService().setDraggedIngredient(typedIngredient);
        } else {
            ModLogger.warn("Cannot set dragged ingredient - no typed ingredient available");
            clearDraggedIngredient();
        }
    }
    
    /**
     * Clears any currently dragged ingredient
     */
    public void clearDraggedIngredient() {
        JEIIntegrationAPI.getDragDropService().clearDraggedIngredient();
    }
    
    /**
     * Gets the currently dragged ingredient, if any
     */
    public Optional<IIngredient> getDraggedIngredient() {
        Optional<IIngredient> unifiedIngredientOpt = JEIIntegrationAPI.getDragDropService().getDraggedIngredient();
        return unifiedIngredientOpt;
    }
    
    /**
     * Checks if an ingredient is currently being dragged
     * 
     * @return true if dragging is active
     */
    public boolean isDraggingIngredient() {
        return getDraggedIngredient().isPresent();
    }
    
    /**
     * Sets whether a drag operation is in progress
     */
    public void setActuallyDragging(boolean isDragging) {
        JEIIntegrationAPI.getDragDropService().setActuallyDragging(isDragging);
    }
    
    /**
     * Handles the preview drag operation to show where an ingredient will be dropped
     * 
     * @param x The x position
     * @param y The y position
     * @return true if the preview was handled
     */
    public boolean handleDragPreview(int x, int y) {
        // No default implementation
        return false;
    }
    
    /**
     * Handles ingredient drop operation
     * 
     * @param x The x position
     * @param y The y position
     * @return true if the drop was handled
     */
    public boolean handleDrop(int x, int y) {
        // No default implementation
        return false;
    }
    
    /**
     * Shows a tooltip for a hovered ingredient
     * 
     * @param graphics The graphics context
     * @param ingredient The ingredient being hovered
     * @param x The x position
     * @param y The y position
     */
    public void showTooltip(Object graphics, Object ingredient, int x, int y) {
        // Implemented in subclasses
    }
    
    /**
     * Registers handlers for JEI integration
     */
    public void registerHandlers() {
        // Each integration implementation can register its own handlers
        registerFolderIntegrationCallback();
    }
    
    /**
     * Handles a click on an ingredient
     * 
     * @param ingredient The ingredient being clicked
     * @param mouseButton The mouse button used
     * @return true if the click was handled
     */
    public boolean handleClick(Object ingredient, int mouseButton) {
        try {
            if (ingredientService == null) {
                ModLogger.debug("Ingredient service not available");
                return false;
            }
            
            // Handle different click behaviors based on mouse button
            if (mouseButton == 0) { // Left click - focus on ingredient
                if (ingredient instanceof ITypedIngredient) {
                    // Create a unified ingredient from the JEI ingredient
                    IIngredient unifiedIngredient = JEIIntegrationAPI.getIngredientService().createIngredient((ITypedIngredient<?>) ingredient);
                    
                    // Hand off to UI controller for focus handling
                    FolderUIController controller = FolderUIController.getInstance();
                    if (controller != null) {
                        return controller.handleIngredientFocus(unifiedIngredient);
                    }
                }
            } else if (mouseButton == 1) { // Right click - add to current folder
                if (ingredient instanceof ITypedIngredient) {
                    // Create a unified ingredient from the JEI ingredient
                    IIngredient unifiedIngredient = JEIIntegrationAPI.getIngredientService().createIngredient((ITypedIngredient<?>) ingredient);
                    
                    // Hand off to UI controller for addition handling
                    FolderUIController controller = FolderUIController.getInstance();
                    if (controller != null) {
                        return controller.handleAddToCurrentFolder(unifiedIngredient);
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error handling ingredient click: {}", e.getMessage(), e);
        }
        return false;
    }
}