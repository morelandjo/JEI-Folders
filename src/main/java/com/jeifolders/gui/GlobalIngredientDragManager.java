package com.jeifolders.gui;

import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.JEIService;
import com.jeifolders.integration.IngredientDragHandler;
import com.jeifolders.integration.TypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;

/**
 * Manages the detection of dragged ingredients from JEI globally.
 */
public class GlobalIngredientDragManager {
    private static GlobalIngredientDragManager instance;
    private boolean isDragging = false;
    private int dragStartX = -1;
    private int dragStartY = -1;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    
    // Access the JEI service through the factory
    private final JEIService jeiService = JEIIntegrationFactory.getJEIService();
    
    // Delegate JEI-specific functionality to the handler in the integration package
    private final IngredientDragHandler ingredientDragHandler = new IngredientDragHandler();

    private GlobalIngredientDragManager() {
        // Register for Forge events
        NeoForge.EVENT_BUS.register(this);
    }

    public static GlobalIngredientDragManager getInstance() {
        if (instance == null) {
            instance = new GlobalIngredientDragManager();
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
            FolderButtonInterface folderButton = FolderManagerGUI.getFolderButton();

            // Get the dragged ingredient from our wrapper
            Optional<TypedIngredient> typedIngredientOpt = ingredientDragHandler.getDraggedIngredient();
            if (folderButton != null && typedIngredientOpt.isPresent()) {
                // Unwrap our TypedIngredient to get the JEI ITypedIngredient
                TypedIngredient typedIngredient = typedIngredientOpt.get();
                Object wrappedObj = typedIngredient.getWrappedIngredient();
                
                boolean handled = folderButton.handleIngredientDrop(event.getMouseX(), event.getMouseY(), wrappedObj);
                ModLogger.info("Ingredient drop handled: {}", handled);
            }

            // Reset drag state
            isDragging = false;
            dragStartX = -1;
            dragStartY = -1;
            ingredientDragHandler.resetDragState();
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

    public void renderDraggedIngredient(GuiGraphics graphics, int mouseX, int mouseY) {
        // No special rendering needed as JEI handles it
    }

    public boolean isDragging() {
        // Only return true if we're actually dragging (not just hovering)
        return isDragging && dragStartX != -1 && ingredientDragHandler.getDraggedIngredient().isPresent();
    }
}