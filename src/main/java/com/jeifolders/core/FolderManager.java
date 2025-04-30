package com.jeifolders.core;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.events.FolderEventDispatcher;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.display.BookmarkDisplayManager;
import com.jeifolders.ui.events.FolderEventType;
import com.jeifolders.ui.interaction.FolderInteractionHandler;
import com.jeifolders.ui.state.FolderUIStateManager;
import com.jeifolders.util.ModLogger;
import java.util.List;

/**
 * Central facade for folder management functionality.
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
        // Create the folder in storage
        Folder folder = createFolderInStorage(folderName);
        
        // Notify system about the new folder
        notifyFolderCreated(folder);
        
        return folder;
    }
    
    /**
     * Creates a folder in the storage service
     * 
     * @param folderName Name for the new folder
     * @return The newly created folder
     */
    private Folder createFolderInStorage(String folderName) {
        Folder folder = storageService.createFolder(folderName);
        ModLogger.debug("Created folder: {} (ID: {})", folder.getName(), folder.getId());
        return folder;
    }
    
    /**
     * Notifies the system that a new folder has been created
     * 
     * @param folder The folder that was created
     */
    private void notifyFolderCreated(Folder folder) {
        // Fire folder created event
        eventDispatcher.fire(FolderEventType.FOLDER_CREATED)
            .withFolder(folder)
            .build();
    }
    
    /**
     * Sets the handler for showing the add folder dialog
     * 
     * @param handler The runnable that will show the add folder dialog
     */
    public void setAddFolderDialogHandler(Runnable handler) {
        interactionHandler.setAddFolderDialogHandler(handler);
    }
    
    /**
     * Sets a folder as active.
     * 
     * @param button The folder button to activate
     */
    public void setActiveFolder(FolderButton button) {
        if (button == null) {
            return;
        }
        
        updateActiveFolder(button);
        notifyFolderActivation(button);
        refreshBookmarksDisplay(button);
    }
    
    /**
     * Updates internal UI state for the newly activated folder
     * 
     * @param button The folder button to set as active
     */
    private void updateActiveFolder(FolderButton button) {
        // Update state in UI state manager
        uiStateManager.setActiveFolder(button);
        
        // Also update the last active folder ID in storage if this is a real folder
        if (button.getFolder() != null) {
            storageService.setLastActiveFolderId(button.getFolder().getId());
        }
    }
    
    /**
     * Notifies the system that a folder has been activated via events
     * 
     * @param button The folder button that was activated
     */
    private void notifyFolderActivation(FolderButton button) {
        // Fire event through the event dispatcher
        eventDispatcher.fire(FolderEventType.FOLDER_ACTIVATED)
            .withButton(button)
            .withFolder(button.getFolder())
            .build();
    }
    
    /**
     * Refreshes the bookmarks display for the activated folder
     * 
     * @param button The folder button that was activated
     */
    private void refreshBookmarksDisplay(FolderButton button) {
        // Update bookmark display if the button has an associated folder
        if (button.getFolder() != null) {
            displayManager.refreshFolderBookmarks(button.getFolder(), true);
        }
    }
}