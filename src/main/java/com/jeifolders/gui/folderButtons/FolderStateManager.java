package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataManager;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized manager for all folder system state.
 * Handles UI state, transient state across GUI rebuilds, and persistent storage.
 */
public class FolderStateManager {
    // Singleton instance
    private static FolderStateManager instance;
    
    // ----- Transient State (preserved across GUI rebuilds) -----
    private static Integer lastActiveFolderId = null;
    private static List<TypedIngredient> lastBookmarkContents = new ArrayList<>();
    private static long lastGuiRebuildTime = 0;
    private static final long GUI_REBUILD_DEBOUNCE_TIME = 500;
    
    // ----- UI State (instance specific) -----
    private FolderButton activeFolder = null;
    private FolderDataRepresentation lastActiveFolder = null;
    private final List<FolderButton> folderButtons = new ArrayList<>();
    private boolean foldersVisible = true;
    
    // ----- Persistent Storage (saved to disk) -----
    private final FolderDataManager folderManager;
    
    // ----- Services -----
    private final FolderEventManager eventManager;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderStateManager() {
        this.folderManager = FolderDataManager.getInstance();
        this.eventManager = FolderEventManager.getInstance();
        ModLogger.debug("FolderStateManager initialized");
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderStateManager getInstance() {
        if (instance == null) {
            instance = new FolderStateManager();
        }
        return instance;
    }
    
    // ----- UI State Management -----
    
    /**
     * Sets a folder as active
     * 
     * @param button The folder button to activate
     */
    public void setActiveFolder(FolderButton button) {
        if (activeFolder != null && activeFolder != button) {
            activeFolder.setActive(false);
        }
        
        if (button != null) {
            button.setActive(true);
            activeFolder = button;
            lastActiveFolder = button.getFolder();
            
            // Update transient state
            lastActiveFolderId = button.getFolder().getId();
            lastGuiRebuildTime = System.currentTimeMillis();
            
            // Fire event
            eventManager.fireFolderActivatedEvent(button);
        }
    }
    
    /**
     * Deactivates the currently active folder
     */
    public void deactivateActiveFolder() {
        if (activeFolder != null) {
            activeFolder.setActive(false);
            activeFolder = null;
            
            // Clear transient state
            lastActiveFolderId = null;
            lastBookmarkContents = new ArrayList<>();
            
            // Fire event
            eventManager.fireFolderDeactivatedEvent();
        }
    }
    
    /**
     * Updates the folder button list
     * 
     * @param buttons List of folder buttons
     */
    public void setFolderButtons(List<FolderButton> buttons) {
        folderButtons.clear();
        if (buttons != null) {
            folderButtons.addAll(buttons);
        }
    }
    
    /**
     * Sets folder visibility
     * 
     * @param visible Whether folders are visible
     */
    public void setFoldersVisible(boolean visible) {
        this.foldersVisible = visible;
    }
    
    /**
     * Gets current folder visibility
     */
    public boolean areFoldersVisible() {
        return foldersVisible;
    }
    
    // ----- Transient State Management -----
    
    /**
     * Updates the bookmark contents cache
     * 
     * @param bookmarkContents The bookmark contents to cache
     */
    public void updateBookmarkContentsCache(List<TypedIngredient> bookmarkContents) {
        lastBookmarkContents = bookmarkContents != null ? 
            new ArrayList<>(bookmarkContents) : new ArrayList<>();
    }
    
    /**
     * Checks if state should be restored from static state
     * 
     * @return true if state restoration is needed
     */
    public boolean shouldRestoreFromStaticState() {
        if (lastActiveFolderId == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        return currentTime - lastGuiRebuildTime < GUI_REBUILD_DEBOUNCE_TIME;
    }
    
    /**
     * Clears all transient state
     */
    public void clearTransientState() {
        lastActiveFolderId = null;
        lastBookmarkContents = new ArrayList<>();
        lastGuiRebuildTime = 0;
    }
    
    // ----- Persistent Storage Management -----
    
    /**
     * Creates a new folder with the given name
     * 
     * @param folderName Name for the new folder
     * @return The newly created folder representation
     */
    public FolderDataRepresentation createFolder(String folderName) {
        FolderDataRepresentation folder = folderManager.createFolder(folderName);
        ModLogger.debug("Created folder: {} (ID: {})", folder.getName(), folder.getId());
        
        // Fire folder created event
        eventManager.fireFolderCreatedEvent(folder);
        
        return folder;
    }
    
    /**
     * Deletes the active folder
     */
    public void deleteActiveFolder() {
        if (activeFolder == null) {
            return;
        }

        int folderId = activeFolder.getFolder().getId();
        String folderName = activeFolder.getFolder().getName();
        ModLogger.debug("Deleting folder: {} (ID: {})", folderName, folderId);

        folderManager.removeFolder(folderId);
        
        // Fire folder deleted event
        eventManager.fireFolderDeletedEvent(folderId, folderName);
        
        // Clear active folder
        activeFolder = null;
    }
    
    /**
     * Loads all folders from persistent storage
     */
    public List<FolderDataRepresentation> loadAllFolders() {
        folderManager.loadData();
        return folderManager.getAllFolders();
    }
    
    /**
     * Gets the folder button at the specified coordinates
     */
    public FolderButton getFolderButtonAt(double mouseX, double mouseY) {
        for (FolderButton button : folderButtons) {
            if (mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
                mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
                return button;
            }
        }
        return null;
    }
    
    /**
     * Updates all folder buttons (for animations, etc.)
     */
    public void tickFolderButtons() {
        for (FolderButton button : folderButtons) {
            button.tick();
        }
    }
    
    // ----- Getters and Setters -----
    
    public FolderDataManager getFolderManager() {
        return folderManager;
    }
    
    public List<FolderButton> getFolderButtons() {
        return folderButtons;
    }
    
    public FolderButton getActiveFolder() {
        return activeFolder;
    }
    
    public boolean hasActiveFolder() {
        return activeFolder != null;
    }
    
    public Integer getLastActiveFolderId() {
        return lastActiveFolderId;
    }
    
    public List<TypedIngredient> getLastBookmarkContents() {
        return lastBookmarkContents;
    }
    
    public FolderDataRepresentation getLastActiveFolder() {
        return lastActiveFolder;
    }
    
    public void setLastActiveFolder(FolderDataRepresentation folder) {
        this.lastActiveFolder = folder;
    }
    
    public FolderEventManager getEventManager() {
        return eventManager;
    }
}