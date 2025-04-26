package com.jeifolders.gui.input;

import com.jeifolders.gui.controller.BookmarkManager;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.render.FolderRenderer;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;
import java.util.function.Consumer;

/**
 * Handles all user input related to the folder system.
 * This includes mouse clicks, ingredient drops, and other UI interactions.
 */
public class FolderInputHandler {
    private final FolderStateManager folderManager;
    private final BookmarkManager bookmarkManager;
    private final FolderRenderer folderRenderer;
    private final Consumer<String> folderNameInputCallback;
    
    public FolderInputHandler(FolderStateManager folderManager, BookmarkManager bookmarkManager, 
                              FolderRenderer folderRenderer, Consumer<String> folderNameInputCallback) {
        this.folderManager = folderManager;
        this.bookmarkManager = bookmarkManager;
        this.folderRenderer = folderRenderer;
        this.folderNameInputCallback = folderNameInputCallback;
    }
    
    /**
     * Handles mouse click events for the folder UI
     * 
     * @param mouseX X position of the mouse
     * @param mouseY Y position of the mouse
     * @param button Button that was clicked (0 = left, 1 = right, 2 = middle)
     * @return true if the click was handled, false otherwise
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (folderRenderer.getCurrentDeleteButtonX() >= 0 && button == 0 && folderRenderer.isDeleteButtonHovered()) {
            // Fire delete button clicked event before deleting
            if (folderManager.hasActiveFolder()) {
                folderManager.fireDeleteButtonClickedEvent(folderManager.getActiveFolder().getFolder().getId());
            }
            
            folderManager.deleteActiveFolder();
            return true;
        }

        if (folderManager.areFoldersVisible()) {
            for (FolderButton folderButton : folderManager.getFolderButtons()) {
                if (folderButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        // Handle bookmark display click
        if (bookmarkManager.handleBookmarkDisplayClick(mouseX, mouseY, button)) {
            return true;
        }

        return false;
    }
    
    /**
     * Handles ingredient drops on the folder UI
     * 
     * @param mouseX X position of the mouse
     * @param mouseY Y position of the mouse
     * @param ingredient The ingredient being dropped
     * @return true if the drop was handled, false otherwise
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        // Add detailed logging about the incoming ingredient
        ModLogger.info("[DROP-DEBUG] handleIngredientDrop called with ingredient type: {}", 
            ingredient != null ? ingredient.getClass().getName() : "null");
        
        // First check if the ingredient is dropped on a folder button
        FolderButton targetFolder = folderManager.getFolderButtonAt(mouseX, mouseY);
        
        if (targetFolder != null) {
            // If dropped on a folder button, activate it and handle the ingredient drop
            ModLogger.info("[DROP-DEBUG] Target folder found: {}", targetFolder.getFolder().getName());
            folderManager.setActiveFolder(targetFolder);
            
            // Delegate ingredient drop handling to FolderStateManager
            boolean result = folderManager.handleIngredientDropOnFolder(targetFolder.getFolder(), ingredient);
            ModLogger.info("[DROP-DEBUG] Folder drop result: {}", result);
            return result;
        }
        
        // If no specific folder was targeted, check if it's a drop on the bookmark display area
        if (folderManager.hasActiveFolder()) {
            ModLogger.info("[DROP-DEBUG] No target folder, checking bookmark display area");
            boolean result = folderManager.handleIngredientDropOnDisplay(mouseX, mouseY, ingredient);
            ModLogger.info("[DROP-DEBUG] Bookmark display drop result: {}", result);
            return result;
        }
        
        ModLogger.info("[DROP-DEBUG] No active folder or target folder, drop failed");
        return false;
    }
    
    /**
     * Get the list of folder buttons from the state manager
     */
    public List<FolderButton> getFolderButtons() {
        return folderManager.getFolderButtons();
    }
    
    /**
     * Check if the bookmark area is available
     */
    public boolean isBookmarkAreaAvailable() {
        return folderManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null;
    }
    
    /**
     * Get the bookmark display area rectangle
     */
    public Rect2i getBookmarkDisplayArea() {
        if (bookmarkManager.getBookmarkDisplay() != null) {
            return new Rect2i(
                bookmarkManager.getBookmarkDisplay().getX(),
                bookmarkManager.getBookmarkDisplay().getY(),
                bookmarkManager.getBookmarkDisplay().getWidth(),
                bookmarkManager.getBookmarkDisplay().getHeight()
            );
        }
        return new Rect2i(0, 0, 0, 0);
    }
}