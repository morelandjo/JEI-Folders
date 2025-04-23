package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataManager;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages folder state and persistence.
 * Handles folder selection/activation and maintains state across UI rebuilds.
 */
public class FolderButtonStateManager {
    // Static state for persistence across GUI rebuilds
    private static Integer lastActiveFolderId = null;
    private static List<TypedIngredient> lastBookmarkContents = new ArrayList<>();
    private static long lastGuiRebuildTime = 0;
    private static final long GUI_REBUILD_DEBOUNCE_TIME = 500;
    
    // Instance fields
    private final FolderDataManager folderManager;
    private final List<FolderButton> folderButtons = new ArrayList<>();
    private FolderButton activeFolder = null;
    private FolderDataRepresentation lastActiveFolder = null;
    private final List<Consumer<FolderButton>> folderActivationListeners = new ArrayList<>();
    
    public FolderButtonStateManager() {
        this.folderManager = FolderDataManager.getInstance();
    }
    
    /**
     * Loads all folders from the data manager and initializes folder buttons
     * 
     * @param startX The starting X position for folder buttons
     * @param startY The starting Y position for folder buttons
     * @param foldersPerRow The number of folders per row
     * @param folderSpacingX The horizontal spacing between folders
     * @param folderSpacingY The vertical spacing between folders
     * @return The list of folder buttons that were created
     */
    public List<FolderButton> loadFolders(int startX, int startY, int foldersPerRow, 
                                            int folderSpacingX, int folderSpacingY) {
        FolderDataRepresentation folderToRestore = lastActiveFolder;
        Integer folderIdToRestore = folderToRestore != null ? folderToRestore.getId() : null;

        if (activeFolder != null) {
            folderToRestore = activeFolder.getFolder();
            folderIdToRestore = folderToRestore.getId();
            lastActiveFolder = folderToRestore;
        }

        if (folderIdToRestore == null && lastActiveFolderId != null) {
            folderIdToRestore = lastActiveFolderId;
        }

        activeFolder = null;
        folderButtons.clear();
        folderManager.loadData();

        List<FolderDataRepresentation> folders = folderManager.getAllFolders();
        FolderButton buttonToActivate = null;

        for (int i = 0; i < folders.size(); i++) {
            FolderDataRepresentation folder = folders.get(i);
            int gridPosition = i + 1; // +1 to leave index 0 for Add button
            int row = gridPosition / foldersPerRow;
            int col = gridPosition % foldersPerRow;
            int x = startX + col * (folderSpacingX);
            int y = startY + row * folderSpacingY;

            FolderButton button = new FolderButton(x, y, folder, this::onFolderClicked);
            folderButtons.add(button);

            if (folderIdToRestore != null && folder.getId() == folderIdToRestore) {
                buttonToActivate = button;
            }
        }

        if (buttonToActivate != null) {
            buttonToActivate.setActive(true);
            activeFolder = buttonToActivate;
            lastActiveFolderId = buttonToActivate.getFolder().getId();
        } else if (folderIdToRestore != null) {
            lastActiveFolder = null;
            if (lastActiveFolderId != null && lastActiveFolderId.equals(folderIdToRestore)) {
                lastActiveFolderId = null;
                lastBookmarkContents = new ArrayList<>();
            }
        }
        
        return folderButtons;
    }
    
    /**
     * Creates a new folder with the given name
     * 
     * @param folderName Name for the new folder
     * @return The newly created folder representation
     */
    public FolderDataRepresentation createFolder(String folderName) {
        FolderDataRepresentation folder = folderManager.createFolder(folderName);
        ModLogger.debug("Created folder: {} (ID: {})", folder.getName(), folder.getId());
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
        ModLogger.debug("Deleting folder: {} (ID: {})", activeFolder.getFolder().getName(), folderId);

        folderManager.removeFolder(folderId);
        activeFolder = null;
    }
    
    /**
     * Called when a folder is clicked
     */
    private void onFolderClicked(FolderDataRepresentation folder) {

        ModLogger.debug("Folder clicked: {}", folder.getName());

        FolderButton clickedButton = null;
        for (FolderButton button : folderButtons) {
            if (button.getFolder() == folder) {
                clickedButton = button;
                break;
            }
        }

        if (clickedButton == null) {
            return;
        }

        if (activeFolder == clickedButton) {
            activeFolder.setActive(false);
            activeFolder = null;
            lastActiveFolderId = null;
            lastBookmarkContents = new ArrayList<>();

            // Notify listeners about deactivation
            for (Consumer<FolderButton> listener : folderActivationListeners) {
                listener.accept(null);
            }

            ModLogger.debug("Folder deactivated, static state cleared");
            return;
        }

        // Deactivate any previous folder first
        if (activeFolder != null) {
            activeFolder.setActive(false);
        }

        // Set the new folder as active in the UI
        clickedButton.setActive(true);
        activeFolder = clickedButton;

        // Update static tracking for GUI rebuilds
        lastActiveFolderId = folder.getId();
        lastGuiRebuildTime = System.currentTimeMillis();

        // Notify listeners about activation
        for (Consumer<FolderButton> listener : folderActivationListeners) {
            listener.accept(activeFolder);
        }
    }
    
    /**
     * Updates the bookmark contents cache
     */
    public void updateBookmarkContentsCache(List<TypedIngredient> bookmarkContents) {
        lastBookmarkContents = bookmarkContents;
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
     * Tick update for all folder buttons
     */
    public void tickFolderButtons() {
        for (FolderButton button : folderButtons) {
            button.tick();
        }
    }
    
    /**
     * Adds a listener for folder activation/deactivation events
     */
    public void addFolderActivationListener(Consumer<FolderButton> listener) {
        folderActivationListeners.add(listener);
    }
    
    // Getters and setters
    
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
    
    public boolean shouldRestoreFromStaticState() {
        if (lastActiveFolderId == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        return currentTime - lastGuiRebuildTime < GUI_REBUILD_DEBOUNCE_TIME;
    }
}