package com.jeifolders.ui.interaction;

import com.jeifolders.core.FolderManager;
import com.jeifolders.data.Folder;
import com.jeifolders.integration.ingredient.Ingredient;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.ui.events.FolderEventType;
import com.jeifolders.ui.util.MouseHitUtil;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;

/**
 * Handles user interactions with the folder system.
 * Processes mouse clicks, ingredient drops, and other interactive elements.
 */
public class FolderInteractionHandler {
    private final FolderManager folderManager;
    
    // Reference to the dialog handler (will be set by FolderButtonSystem)
    private Runnable addFolderDialogHandler = null;
    
    /**
     * Constructor for FolderInteractionHandler
     * 
     * @param folderManager Reference to the central folder manager
     */
    public FolderInteractionHandler(FolderManager folderManager) {
        this.folderManager = folderManager;
    }
    
    /**
     * Gets the folder button at the specified coordinates
     */
    public FolderButton getFolderButtonAt(double mouseX, double mouseY) {
        for (FolderButton button : folderManager.getUIStateManager().getFolderButtons()) {
            // Use our standardized hit detection through MouseHitUtil
            if (MouseHitUtil.isMouseOver(mouseX, mouseY, button)) {
                return button;
            }
        }
        return null;
    }
    
    /**
     * Central method to handle all mouse click events in the UI.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button used
     * @param isDeleteButtonHovered Whether the delete button is hovered
     * @param deleteButtonX X position of the delete button
     * @return true if the click was handled
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button, boolean isDeleteButtonHovered, int deleteButtonX) {
        // Check delete button clicks first - highest priority
        if (deleteButtonX >= 0 && button == 0 && isDeleteButtonHovered) {
            // Fire delete button clicked event before deleting
            if (folderManager.getUIStateManager().hasActiveFolder()) {
                folderManager.getEventDispatcher().fire(FolderEventType.DELETE_BUTTON_CLICKED)
                    .withFolderId(folderManager.getUIStateManager().getActiveFolder().getFolder().getId())
                    .build();
            }
            
            deleteActiveFolder();
            return true;
        }

        // Check folder button clicks
        if (folderManager.getUIStateManager().areFoldersVisible()) {
            for (FolderButton folderButton : folderManager.getUIStateManager().getFolderButtons()) {
                // Use our standardized hit detection approach
                if (MouseHitUtil.isMouseOver(mouseX, mouseY, folderButton)) {
                    // Handle different button types
                    switch (folderButton.getButtonType()) {
                        case ADD:
                            // Add button handled directly by the folder manager
                            handleAddFolderButtonClick(null);
                            break;
                        case NORMAL:
                            // Normal folder buttons handled directly by the folder manager
                            if (folderButton.getFolder() != null) {
                                handleFolderClick(folderButton.getFolder());
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
        return folderManager.getUIStateManager().hasActiveFolder() && 
               folderManager.getDisplayManager().getBookmarkDisplay() != null &&
               folderManager.getDisplayManager().handleBookmarkDisplayClick(mouseX, mouseY, button);
    }
    
    /**
     * Handles an ingredient being dropped onto the bookmark system.
     * This method checks if the ingredient was dropped on a folder button or on the active display.
     *
     * @param mouseX The X coordinate of the mouse
     * @param mouseY The Y coordinate of the mouse
     * @param ingredient The ingredient that was dropped
     * @param isFoldersVisible Whether folders are currently visible
     * @return true if the drop was handled, false otherwise
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient, boolean isFoldersVisible) {
        // First check if it's a drop onto a folder button
        if (isFoldersVisible) {
            FolderButton targetButton = getFolderButtonAt(mouseX, mouseY);
            if (targetButton != null) {
                return handleIngredientDropOnFolder(targetButton.getFolder(), ingredient);
            }
        }

        // Then check if it's a drop on the active display
        return handleIngredientDropOnDisplay(mouseX, mouseY, ingredient);
    }
    
    /**
     * Handles an ingredient being dropped on the bookmark display
     * 
     * @param mouseX The x position of the drop
     * @param mouseY The y position of the drop
     * @param ingredient The ingredient that was dropped
     * @return true if the drop was handled
     */
    public boolean handleIngredientDropOnDisplay(double mouseX, double mouseY, Object ingredient) {
        // Check if there's an active folder and a bookmark display
        if (!folderManager.getUIStateManager().hasActiveFolder()) {
            ModLogger.debug("Cannot handle ingredient drop: no active folder");
            return false;
        }
        
        var bookmarkDisplay = folderManager.getDisplayManager().getBookmarkDisplay();
        if (bookmarkDisplay == null) {
            ModLogger.debug("Cannot handle ingredient drop: no bookmark display");
            return false;
        }
        
        // Check if the ingredient was dropped over the display area
        if (!bookmarkDisplay.isMouseOver(mouseX, mouseY)) {
            ModLogger.debug("Ingredient drop not over bookmark display");
            return false;
        }
        
        // Update the bookmark display with the active folder if needed
        if (bookmarkDisplay.getActiveFolder() == null && 
            folderManager.getUIStateManager().getActiveFolder() != null) {
            ModLogger.debug("Setting active folder on bookmark display");
            bookmarkDisplay.setActiveFolder(
                folderManager.getUIStateManager().getActiveFolder().getFolder()
            );
        }
        
        // Delegate to the bookmark display
        boolean result = bookmarkDisplay.handleIngredientDrop(mouseX, mouseY, ingredient);
        ModLogger.debug("Bookmark display handleIngredientDrop result: {}", result);
        return result;
    }
    
    /**
     * Central method for handling ingredient drops on a folder.
     * 
     * @param folder The folder to add the ingredient to
     * @param ingredient The ingredient being dropped
     * @return true if the drop was handled
     */
    public boolean handleIngredientDropOnFolder(Folder folder, Object ingredient) {
        if (folder == null || ingredient == null) {
            ModLogger.debug("Cannot handle ingredient drop: folder or ingredient is null");
            return false;
        }
        
        // Add debug log to identify ingredient type
        ModLogger.debug("[HOVER-FIX] Ingredient dropped on folder: {}", folder.getName());
        ModLogger.debug("[DROP-DEBUG] Handling ingredient drop on folder with type: {}", ingredient.getClass().getName());
        
        try {
            // First attempt to generate key directly from the original ingredient
            String key = null;
            
            // Try direct approach first (same as bookmark display uses)
            try {
                key = com.jeifolders.integration.TypedIngredientHelper.getKeyForIngredient(ingredient);
            } catch (Exception e) {
                ModLogger.debug("[DROP-DEBUG] Failed to get key from direct ingredient: {}", e.getMessage());
            }
            
            // If direct key generation failed, try unified ingredient approach
            if (key == null || key.isEmpty()) {
                Ingredient unifiedIngredient = IngredientManager.getInstance().createIngredient(ingredient);
                if (unifiedIngredient == null) {
                    ModLogger.debug("Failed to create unified ingredient");
                    return false;
                }
                
                ModLogger.debug("[DROP-DEBUG] Created unified ingredient with type: {}", 
                    unifiedIngredient.getTypedIngredient() != null ? 
                    unifiedIngredient.getTypedIngredient().getClass().getName() : "null");
                
                key = com.jeifolders.integration.TypedIngredientHelper.getKeyForIngredient(unifiedIngredient);
            }
            
            // Final check if key generation succeeded
            if (key == null || key.isEmpty()) {
                ModLogger.debug("Failed to generate key for ingredient");
                return false;
            }
            
            // Check if the folder already has this bookmark
            if (folder.containsBookmark(key)) {
                ModLogger.debug("Folder already contains bookmark with key: {}", key);
                return true;
            }
            
            // Add the bookmark to the folder
            folderManager.getStorageService().addBookmark(folder.getId(), key);
            ModLogger.debug("Added bookmark to folder {} (ID: {}): {}", folder.getName(), folder.getId(), key);
            
            // Fire folder contents changed event
            ModLogger.debug("Ingredient added, firing FOLDER_CONTENTS_CHANGED event");
            folderManager.getEventDispatcher().fireFolderContentsChangedEvent(folder);
            
            // Save the changes
            folderManager.getStorageService().saveData();
            
            // If this is the active folder, refresh the display
            if (folderManager.getUIStateManager().hasActiveFolder() && 
                folderManager.getUIStateManager().getActiveFolder().getFolder().getId() == folder.getId()) {
                folderManager.getDisplayManager().refreshActiveFolder(true);
            }
            
            ModLogger.debug("Successfully added bookmark to folder: {}", folder.getName());
            return true;
        } catch (Exception e) {
            ModLogger.error("Error adding bookmark to folder: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Handles a click on the "Add Folder" button.
     * This method is used as a click handler for the "Add Folder" button.
     * 
     * @param ignored This parameter is ignored since the Add button has no folder
     */
    public void handleAddFolderButtonClick(Folder ignored) {
        // Fire event for other listeners
        folderManager.getEventDispatcher().fire(FolderEventType.ADD_BUTTON_CLICKED)
            .build();
        
        // Delegate dialog handling to the UI system
        if (addFolderDialogHandler != null) {
            addFolderDialogHandler.run();
        } else {
            ModLogger.error("Add folder dialog handler is not set");
        }
    }
    
    /**
     * Handles a click on a folder
     * 
     * @param folder The folder that was clicked
     */
    public void handleFolderClick(Folder folder) {
        if (folder == null) {
            return;
        }
        
        var uiStateManager = folderManager.getUIStateManager();
        
        // Find the button for the clicked folder
        for (FolderButton button : uiStateManager.getFolderButtons()) {
            if (button.getButtonType() == FolderButton.ButtonType.NORMAL && 
                button.getFolder() != null && 
                button.getFolder().getId() == folder.getId()) {
                
                if (button.isActive()) {
                    // If clicking the active folder again, deactivate it
                    uiStateManager.deactivateActiveFolder();
                    folderManager.getEventDispatcher().fire(FolderEventType.FOLDER_DEACTIVATED)
                        .build();
                } else {
                    // Otherwise activate the clicked folder
                    uiStateManager.setActiveFolder(button);
                    folderManager.getEventDispatcher().fire(FolderEventType.FOLDER_ACTIVATED)
                        .withButton(button)
                        .withFolder(button.getFolder())
                        .build();
                    
                    // Update bookmark display
                    var bookmarkDisplay = folderManager.getDisplayManager().getBookmarkDisplay();
                    if (bookmarkDisplay != null) {
                        bookmarkDisplay.setActiveFolder(button.getFolder());
                        bookmarkDisplay.updateBoundsFromCalculatedPositions();
                        folderManager.getDisplayManager().safeUpdateBookmarkContents();
                    }
                }
                break;
            }
        }
    }
    
    /**
     * Deletes the active folder
     */
    public void deleteActiveFolder() {
        var uiStateManager = folderManager.getUIStateManager();
        
        if (!uiStateManager.hasActiveFolder()) {
            return;
        }

        int folderId = uiStateManager.getActiveFolder().getFolder().getId();
        String folderName = uiStateManager.getActiveFolder().getFolder().getName();
        ModLogger.debug("Deleting folder: {} (ID: {})", folderName, folderId);

        folderManager.getStorageService().deleteFolder(folderId);
        
        // Fire folder deleted event
        folderManager.getEventDispatcher().fire(FolderEventType.FOLDER_DELETED)
            .withFolderId(folderId)
            .withFolderName(folderName)
            .build();
        
        // Force a complete UI rebuild through the FolderUIController
        Minecraft.getInstance().execute(() -> {
            if (FolderUIController.isInitialized()) {
                FolderUIController.getInstance().rebuildFolders();
            }
        });
    }
    
    /**
     * Sets the handler for showing the add folder dialog
     * 
     * @param handler The runnable that will show the add folder dialog
     */
    public void setAddFolderDialogHandler(Runnable handler) {
        this.addFolderDialogHandler = handler;
    }
    
    /**
     * Creates a new folder with the given name
     * 
     * @param folderName Name for the new folder
     * @return The newly created folder representation
     */
    public Folder createFolder(String folderName) {
        Folder folder = folderManager.getStorageService().createFolder(folderName);
        ModLogger.debug("Created folder: {} (ID: {})", folder.getName(), folder.getId());
        
        // Fire folder created event
        folderManager.getEventDispatcher().fire(FolderEventType.FOLDER_CREATED)
            .withFolder(folder)
            .build();
        
        return folder;
    }
}