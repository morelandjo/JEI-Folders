package com.jeifolders.gui.input;

import com.jeifolders.data.Folder;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.layout.FolderLayoutService;
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
    private final FolderRenderer folderRenderer;
    private final Consumer<String> folderCreator;
    
    public FolderInputHandler(FolderStateManager folderManager, 
                             FolderRenderer folderRenderer,
                             Consumer<String> folderCreator) {
        this.folderManager = folderManager;
        this.folderRenderer = folderRenderer;
        this.folderCreator = folderCreator;
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
                if (isMouseOver(mouseX, mouseY, folderButton)) {
                    // Handle different button types
                    switch (folderButton.getButtonType()) {
                        case ADD:
                            // Add button handled by the folder manager
                            folderManager.handleAddFolderButtonClick(null);
                            break;
                        case NORMAL:
                            // Normal folder buttons handled by the folder manager
                            if (folderButton.getFolder() != null) {
                                folderManager.handleFolderClick(folderButton.getFolder());
                            }
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            }
        }

        // Check if the click should be handled by the bookmark display
        return folderManager.hasActiveFolder() && 
               folderManager.getBookmarkDisplay() != null &&
               folderManager.handleBookmarkDisplayClick(mouseX, mouseY, button);
    }
    
    /**
     * Handles ingredients being dropped on the folder system
     * 
     * @param mouseX X position of the drop
     * @param mouseY Y position of the drop
     * @param ingredient The ingredient being dropped
     * @return true if the drop was handled, false otherwise
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        if (ingredient == null) {
            ModLogger.debug("Cannot handle ingredient drop: ingredient is null");
            return false;
        }
        
        if (!folderManager.areFoldersVisible()) {
            ModLogger.debug("Cannot handle ingredient drop: folders not visible");
            return false;
        }
        
        return folderManager.handleIngredientDrop(mouseX, mouseY, ingredient, folderManager.areFoldersVisible());
    }
    
    /**
     * Gets the folder buttons for the interface
     */
    public List<FolderButton> getFolderButtons() {
        return folderManager.getFolderButtons();
    }
    
    /**
     * Checks if the bookmark area is available for drag/drop
     */
    public boolean isBookmarkAreaAvailable() {
        return folderManager.hasActiveFolder() && folderManager.getBookmarkDisplay() != null;
    }
    
    /**
     * Helper method to check if the mouse is over a button
     */
    private boolean isMouseOver(double mouseX, double mouseY, FolderButton button) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
               mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }
    
    /**
     * Get the bookmark display area rectangle
     */
    public Rect2i getBookmarkDisplayArea() {
        if (folderManager.getBookmarkDisplay() != null) {
            return new Rect2i(
                folderManager.getBookmarkDisplay().getX(),
                folderManager.getBookmarkDisplay().getY(),
                folderManager.getBookmarkDisplay().getWidth(),
                folderManager.getBookmarkDisplay().getHeight()
            );
        }
        return new Rect2i(0, 0, 0, 0);
    }
}