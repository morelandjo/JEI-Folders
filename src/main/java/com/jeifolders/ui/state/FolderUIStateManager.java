package com.jeifolders.ui.state;

import com.jeifolders.integration.ingredient.Ingredient;
import com.jeifolders.ui.components.buttons.FolderButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages UI state for the folder system.
 * Handles folders visibility, active folder tracking, and other UI-related state.
 */
public class FolderUIStateManager {
    // ----- Transient State (preserved across GUI rebuilds) -----
    private static Integer lastActiveFolderId = null;
    private static List<Ingredient> lastBookmarkContents = new ArrayList<>();
    private static long lastGuiRebuildTime = 0;
    private static final long GUI_REBUILD_DEBOUNCE_TIME = 500;
    
    // ----- UI State (instance specific) -----
    private FolderButton activeFolder = null;
    private final List<FolderButton> folderButtons = new ArrayList<>();
    private boolean foldersVisible = true;
    
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
            
            // Update transient state
            lastActiveFolderId = button.getFolder().getId();
            lastGuiRebuildTime = System.currentTimeMillis();
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
    
    /**
     * Updates the bookmark contents cache
     * 
     * @param bookmarkContents The bookmark contents to cache
     */
    public void updateBookmarkContentsCache(List<Ingredient> bookmarkContents) {
        lastBookmarkContents = bookmarkContents != null ? 
            new ArrayList<>(bookmarkContents) : new ArrayList<>();
    }
    
    /**
     * Gets the cached bookmark contents
     * 
     * @return The cached bookmark contents
     */
    public List<Ingredient> getBookmarkContentsCache() {
        return new ArrayList<>(lastBookmarkContents);
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
     * Gets the last active folder ID
     * 
     * @return The last active folder ID
     */
    public Integer getLastActiveFolderId() {
        return lastActiveFolderId;
    }
    
    /**
     * Clears all transient state
     */
    public void clearTransientState() {
        lastActiveFolderId = null;
        lastBookmarkContents = new ArrayList<>();
        lastGuiRebuildTime = 0;
    }
    
    /**
     * Gets the list of folder buttons
     */
    public List<FolderButton> getFolderButtons() {
        return folderButtons;
    }
    
    /**
     * Gets the currently active folder button
     */
    public FolderButton getActiveFolder() {
        return activeFolder;
    }
    
    /**
     * Checks if there is an active folder
     */
    public boolean hasActiveFolder() {
        return activeFolder != null;
    }
    
    /**
     * Updates all folder buttons (for animations, etc.)
     */
    public void tickFolderButtons() {
        for (FolderButton button : folderButtons) {
            button.tick();
        }
    }
    
    /**
     * Gets the folder button at the specified coordinates
     * 
     * @param mouseX The X coordinate
     * @param mouseY The Y coordinate
     * @return The button at the position, or null if none
     */
    public FolderButton getFolderButtonAt(double mouseX, double mouseY) {
        for (FolderButton button : folderButtons) {
            if (button.isMouseOver(mouseX, mouseY)) {
                return button;
            }
        }
        return null;
    }
}