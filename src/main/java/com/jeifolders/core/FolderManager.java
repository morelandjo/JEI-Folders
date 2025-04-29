package com.jeifolders.core;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.events.FolderEventDispatcher;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.display.BookmarkDisplayManager;
import com.jeifolders.ui.interaction.FolderInteractionHandler;
import com.jeifolders.ui.state.FolderUIStateManager;
import com.jeifolders.util.ModLogger;
import java.util.List;

/**
 * Central facade for folder management functionality.
 * Coordinates between different components of the folder system.
 */
public class FolderManager {
    // Singleton instance
    private static FolderManager instance;
    
    // Component references
    private final FolderUIStateManager uiStateManager;
    private final FolderStorageService storageService;
    private final FolderEventDispatcher eventDispatcher;
    private final BookmarkDisplayManager displayManager;
    private final FolderInteractionHandler interactionHandler;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderManager() {
        // Initialize the component instances
        this.storageService = FolderStorageService.getInstance();
        this.uiStateManager = new FolderUIStateManager();
        this.eventDispatcher = new FolderEventDispatcher();
        this.displayManager = new BookmarkDisplayManager(this);
        this.interactionHandler = new FolderInteractionHandler(this);
        
        // Set up bidirectional connections
        this.storageService.registerCallback(this);
        
        ModLogger.debug("FolderManager initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderManager getInstance() {
        if (instance == null) {
            instance = new FolderManager();
        }
        return instance;
    }
    
    /**
     * Get the UI state manager component
     */
    public FolderUIStateManager getUIStateManager() {
        return uiStateManager;
    }
    
    /**
     * Get the folder storage service component
     */
    public FolderStorageService getStorageService() {
        return storageService;
    }
    
    /**
     * Get the event dispatcher component
     */
    public FolderEventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }
    
    /**
     * Get the bookmark display manager component
     */
    public BookmarkDisplayManager getDisplayManager() {
        return displayManager;
    }
    
    /**
     * Get the folder interaction handler component
     */
    public FolderInteractionHandler getInteractionHandler() {
        return interactionHandler;
    }
    
    /**
     * Gets the layout service from the UI controller
     * @return The layout service
     */
    public com.jeifolders.ui.layout.FolderLayoutService getLayoutService() {
        return com.jeifolders.ui.controllers.FolderUIController.getInstance().getLayoutService();
    }
    
    /**
     * Updates the bookmark contents cache
     * 
     * @param bookmarkContents The bookmark contents to cache
     */
    public void updateBookmarkContentsCache(List<TypedIngredient> bookmarkContents) {
        uiStateManager.updateBookmarkContentsCache(bookmarkContents);
    }
    
    /**
     * Creates a new folder with the given name
     * 
     * @param folderName Name for the new folder
     * @return The newly created folder representation
     */
    public Folder createFolder(String folderName) {
        Folder folder = storageService.createFolder(folderName);
        ModLogger.debug("Created folder: {} (ID: {})", folder.getName(), folder.getId());
        
        // Fire folder created event
        eventDispatcher.fireFolderCreatedEvent(folder);
        
        return folder;
    }
    
    /**
     * Sets the handler for showing the add folder dialog
     * This should be called by the UI system that manages dialogs
     * 
     * @param handler The runnable that will show the add folder dialog
     */
    public void setAddFolderDialogHandler(Runnable handler) {
        interactionHandler.setAddFolderDialogHandler(handler);
    }
    
    /**
     * Sets a folder as active. This is a convenience method that coordinates
     * between the different components.
     * 
     * @param button The folder button to activate
     */
    public void setActiveFolder(FolderButton button) {
        if (button == null) {
            return;
        }
        
        // Update state in UI state manager
        uiStateManager.setActiveFolder(button);
        
        // Fire event through the event dispatcher
        eventDispatcher.fireFolderActivatedEvent(button);
        
        // Update bookmark display
        if (button.getFolder() != null) {
            displayManager.refreshFolderBookmarks(button.getFolder(), true);
        }
    }
}