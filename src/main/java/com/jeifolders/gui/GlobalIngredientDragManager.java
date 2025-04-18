package com.jeifolders.gui;

import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.JEIIntegration;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
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
    private IIngredientManager ingredientManager;
    private boolean isDragging = false;
    private int dragStartX = -1;
    private int dragStartY = -1;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private ItemStack lastDraggedItem = ItemStack.EMPTY;

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

    public void setJeiRuntime(IJeiRuntime jeiRuntime) {
        this.ingredientManager = jeiRuntime.getIngredientManager();
        ModLogger.info("GlobalIngredientDragManager initialized with JEI runtime");
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

            if (folderButton != null && JEIIntegration.getDraggedIngredient().isPresent()) {
                boolean handled = folderButton.handleIngredientDrop(event.getMouseX(), event.getMouseY(), JEIIntegration.getDraggedIngredient().get());
                ModLogger.info("Ingredient drop handled: {}", handled);
            }

            // Reset drag state
            isDragging = false;
            dragStartX = -1;
            dragStartY = -1;
            lastDraggedItem = ItemStack.EMPTY;
            JEIIntegration.clearDraggedIngredient();
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
        if (ingredientManager == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player != null) {
            ItemStack carriedStack = player.containerMenu.getCarried();
            if (!carriedStack.isEmpty() && !ItemStack.matches(carriedStack, lastDraggedItem)) {
                lastDraggedItem = carriedStack.copy();
                Optional<ITypedIngredient<ItemStack>> ingredient = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, carriedStack);
                ingredient.ifPresent(JEIIntegration::setDraggedIngredient);
                isDragging = true;
                ModLogger.info("Detected potential JEI ingredient drag: {}", lastDraggedItem);
            }
        }
    }

    public void renderDraggedIngredient(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isDragging && JEIIntegration.getDraggedIngredient().isPresent()) {
            // Let JEI handle the rendering
        }
    }

    public boolean isDragging() {
        // Only return true if we're actually dragging (not just hovering)
        // This will prevent highlights from appearing when just hovering over ingredients
        return isDragging && dragStartX != -1 && JEIIntegration.getDraggedIngredient().isPresent();
    }
}