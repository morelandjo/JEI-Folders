package com.jeifolders.gui.screen;

import com.jeifolders.gui.common.FolderNameInputScreen;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/**
 * Manages screens and dialogs for the folder system.
 * Handles transitions between screens and dialog creation.
 */
public class FolderScreenManager {
    private final FolderStateManager folderManager;
    private final Consumer<String> folderCreationCallback;
    
    public FolderScreenManager(FolderStateManager folderManager, Consumer<String> folderCreationCallback) {
        this.folderManager = folderManager;
        this.folderCreationCallback = folderCreationCallback;
        
        // Register the dialog handler with the folder manager
        folderManager.setAddFolderDialogHandler(this::showFolderNameInputScreen);
    }
    
    /**
     * Shows the folder name input screen for creating a new folder
     */
    public void showFolderNameInputScreen() {
        ModLogger.debug("Add folder button clicked");
        Minecraft.getInstance().setScreen(new FolderNameInputScreen(
            Minecraft.getInstance().screen,
            folderName -> {
                folderCreationCallback.accept(folderName);
            }
        ));
    }
    
    /**
     * Creates and shows a custom dialog screen
     * 
     * @param title Dialog title
     * @param message Dialog message
     * @param callback Callback for dialog confirmation
     */
    public void showConfirmDialog(String title, String message, Runnable callback) {
        // Implementation for showing a confirmation dialog
        // This could be expanded as needed for different dialog types
    }
    
    /**
     * Returns to the previous screen
     * 
     * @param currentScreen The current screen
     */
    public void returnToPreviousScreen(Screen currentScreen) {
        // Implementation for returning to previous screen
    }
}