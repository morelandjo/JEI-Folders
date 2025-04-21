package com.jeifolders.gui;

import com.jeifolders.data.FolderDataManager;
import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.ImmutableRectangle;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handles bookmark operations and display management.
 * Responsible for bookmark display, ingredient drops, and bookmark operations.
 */
public class BookmarkManager {
    private final FolderDataManager folderManager;
    private FolderBookmarkContentsDisplay bookmarkDisplay;
    private final FolderStateManager stateManager;
    
    // Bookmark display bounds
    private ImmutableRectangle lastBookmarkBounds = null;
    private int calculatedNameY = -1;
    private int calculatedBookmarkDisplayY = -1;

    public BookmarkManager(FolderStateManager stateManager) {
        this.stateManager = stateManager;
        this.folderManager = stateManager.getFolderManager();
        this.bookmarkDisplay = new FolderBookmarkContentsDisplay(folderManager);
        
        // Listen for folder activation/deactivation
        stateManager.addFolderActivationListener(this::onFolderActivationChanged);
    }
    
    /**
     * Called when a folder is activated or deactivated
     */
    private void onFolderActivationChanged(FolderRowButton folderButton) {
        if (folderButton == null) {
            // Folder was deactivated
            bookmarkDisplay.setActiveFolder(null);
        } else {
            // Folder was activated
            bookmarkDisplay.setActiveFolder(folderButton.getFolder());
            updateBookmarkDisplayBounds();
            
            // Update the bookmark contents cache
            safeUpdateBookmarkContents();
        }
    }
    
    /**
     * Updates the static state cache for GUI rebuilds
     */
    private void safeUpdateBookmarkContents() {
        if (bookmarkDisplay != null) {
            // Get ingredients from the bookmark display and convert them
            List<BookmarkIngredient> displayIngredients = bookmarkDisplay.getIngredients();
            
            // Create a new list and convert from BookmarkIngredient to TypedIngredient
            List<TypedIngredient> bookmarkContents = TypedIngredientHelper.extractFromBookmarkIngredients(displayIngredients);
            
            // Update the state manager's cache
            stateManager.updateBookmarkContentsCache(bookmarkContents);
        }
    }
    
    /**
     * Creates a new bookmark display
     */
    public void createBookmarkDisplay() {
        bookmarkDisplay = new FolderBookmarkContentsDisplay(folderManager);
        
        // Make sure the display knows about any active folder and has bounds set
        if (stateManager.hasActiveFolder()) {
            FolderRowButton activeFolder = stateManager.getActiveFolder();
            bookmarkDisplay.setActiveFolder(activeFolder.getFolder());
            updateBookmarkDisplayBounds();
            
            // Apply cached bookmark contents if available
            List<TypedIngredient> lastBookmarkContents = stateManager.getLastBookmarkContents();
            if (!lastBookmarkContents.isEmpty()) {
                ModLogger.debug("Applying {} cached bookmark items during display creation", 
                    lastBookmarkContents.size());
                // Convert TypedIngredient to BookmarkIngredient before passing to setIngredients
                List<BookmarkIngredient> bookmarkIngredients = TypedIngredientHelper.convertToBookmarkIngredients(lastBookmarkContents);
                bookmarkDisplay.setIngredients(bookmarkIngredients);
            }
        }
    }
    
    /**
     * Updates the bookmark display bounds based on calculated positions
     */
    public void updateBookmarkDisplayBounds() {
        if (bookmarkDisplay == null) return;

        int bookmarkDisplayWidth = 200;
        int bookmarkDisplayHeight = 100;

        if (lastBookmarkBounds == null ||
            lastBookmarkBounds.getY() != calculatedBookmarkDisplayY ||
            lastBookmarkBounds.getWidth() != bookmarkDisplayWidth ||
            lastBookmarkBounds.getHeight() != bookmarkDisplayHeight) {

            lastBookmarkBounds = new ImmutableRectangle(0, calculatedBookmarkDisplayY,
                                                bookmarkDisplayWidth, bookmarkDisplayHeight);
            bookmarkDisplay.updateBounds(0, calculatedBookmarkDisplayY,
                                       bookmarkDisplayWidth, bookmarkDisplayHeight);
        }
    }
    
    /**
     * Refreshes the bookmark display with the latest data from the folder manager
     */
    public void forceFullRefresh() {
        FolderRowButton activeFolder = stateManager.getActiveFolder();
        if (bookmarkDisplay == null || activeFolder == null) {
            return;
        }
        
        ModLogger.info("Refreshing folder display to show updated bookmarks");
        
        try {
            // Get the current folder
            FolderDataRepresentation currentFolder = activeFolder.getFolder();
            
            // Use the centralized helper to refresh the bookmark display
            List<TypedIngredient> ingredients = TypedIngredientHelper.refreshBookmarkDisplay(
                bookmarkDisplay, 
                currentFolder, 
                folderManager
            );
            
            // Update the display bounds
            updateBookmarkDisplayBounds();
            
            // Update the static state cache for GUI rebuilds
            safeUpdateBookmarkContents();
            
            ModLogger.info("Bookmark display refresh completed successfully with {} ingredients", ingredients.size());
        } catch (Exception e) {
            ModLogger.error("Error refreshing bookmark display: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Explicitly reloads the bookmark display for the active folder
     */
    public void reloadBookmarkDisplay() {
        if (!stateManager.hasActiveFolder()) return;
        
        ModLogger.info("Explicitly reloading bookmark display");
        
        // Save the current ingredients if any
        List<TypedIngredient> savedIngredients = new ArrayList<>();
        if (bookmarkDisplay != null) {
            // Get the current ingredients from bookmarkDisplay and extract the TypedIngredient objects
            savedIngredients = TypedIngredientHelper.extractFromBookmarkIngredients(
                bookmarkDisplay.getIngredients()
            );
        }
        
        // Create a new display
        createBookmarkDisplay();
        
        // If we had ingredients before, restore them
        if (!savedIngredients.isEmpty()) {
            // Convert TypedIngredient to BookmarkIngredient using the helper class
            List<BookmarkIngredient> bookmarkIngredients = TypedIngredientHelper.convertToBookmarkIngredients(savedIngredients);
            bookmarkDisplay.setIngredients(bookmarkIngredients);
        }
        // Otherwise use any cached ones
        else {
            List<TypedIngredient> lastBookmarkContents = stateManager.getLastBookmarkContents();
            if (!lastBookmarkContents.isEmpty()) {
                // Convert TypedIngredient to BookmarkIngredient using the helper class
                List<BookmarkIngredient> bookmarkIngredients = TypedIngredientHelper.convertToBookmarkIngredients(lastBookmarkContents);
                bookmarkDisplay.setIngredients(bookmarkIngredients);
            }
        }
        
        updateBookmarkDisplayBounds();
    }
    
    /**
     * Handles an ingredient drop at the specified coordinates
     * 
     * @param mouseX The mouse X coordinate
     * @param mouseY The mouse Y coordinate
     * @param ingredient The ingredient being dropped
     * @param isFoldersVisible Whether folders are currently visible
     * @return true if the ingredient was handled, false otherwise
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient, boolean isFoldersVisible) {
        if (ingredient == null) {
            ModLogger.warn("Received null ingredient for drop");
            return false;
        }

        // Log more details about the ingredient being dropped
        ModLogger.info("Handling ingredient drop: {}", ingredient.getClass().getName());
        
        // Use the IngredientService instead of direct JEIIngredientManager reference
        IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
        String key = ingredientService.getKeyForIngredient(ingredient);
        
        if (key == null || key.isEmpty()) {
            ModLogger.warn("Failed to generate key for ingredient: {}", ingredient);
            return false;
        }
        
        ModLogger.info("Generated ingredient key: {}", key);

        if (isFoldersVisible) {
            FolderRowButton targetButton = stateManager.getFolderButtonAt(mouseX, mouseY);
            if (targetButton != null) {
                FolderDataRepresentation folder = targetButton.getFolder();
                ModLogger.info("Adding bookmark {} to folder {}", key, folder.getName());
                
                // Check if the folder already contains this bookmark
                if (folder.containsBookmark(key)) {
                    ModLogger.info("Folder {} already contains this bookmark", folder.getName());
                } else {
                    // Add the bookmark to the folder
                    folderManager.addBookmarkToFolder(folder.getId(), key);
                    targetButton.playSuccessAnimation();
                    
                    // Get the folder's bookmark keys to confirm the bookmark was added
                    List<String> bookmarkKeys = folderManager.getFolderBookmarkKeys(folder.getId());
                    ModLogger.info("Folder {} now has {} bookmarks", folder.getName(), bookmarkKeys.size());
                    
                    // If the current folder is active, refresh its display
                    FolderRowButton activeFolder = stateManager.getActiveFolder();
                    if (activeFolder != null && activeFolder.getFolder().getId() == folder.getId()) {
                        ModLogger.info("Refreshing bookmark display for active folder");
                        // Force a complete refresh cycle to ensure the UI updates
                        forceFullRefresh();
                    }
                    
                    // Force a save to ensure the bookmark is persisted
                    folderManager.saveData();
                }
                
                return true;
            }
        }

        FolderRowButton activeFolder = stateManager.getActiveFolder();
        if (activeFolder != null && bookmarkDisplay != null && bookmarkDisplay.isMouseOver(mouseX, mouseY)) {
            ModLogger.info("Adding bookmark to active folder: {}", activeFolder.getFolder().getName());
            
            // Check if the folder already contains this bookmark
            if (activeFolder.getFolder().containsBookmark(key)) {
                ModLogger.info("Active folder already contains this bookmark");
            } else {
                // Add the bookmark to the folder
                folderManager.addBookmarkToFolder(activeFolder.getFolder().getId(), key);
                
                // Log the number of bookmarks after adding
                List<String> bookmarkKeys = folderManager.getFolderBookmarkKeys(activeFolder.getFolder().getId());
                ModLogger.info("Active folder now has {} bookmarks", bookmarkKeys.size());
                
                // Force a complete refresh cycle to ensure the UI updates immediately
                forceFullRefresh();
                
                // Force a save to ensure the bookmark is persisted
                folderManager.saveData();
            }
            
            return true;
        }

        ModLogger.info("No suitable target found for ingredient drop");
        return false;
    }
    
    /**
     * Check if a mouse click on the bookmark display was handled
     */
    public boolean handleBookmarkDisplayClick(double mouseX, double mouseY, int button) {
        if (stateManager.hasActiveFolder() && bookmarkDisplay != null) {
            // Add page navigation handling
            if (button == 0) {
                // Check if next page button is clicked
                if (bookmarkDisplay.isNextButtonClicked(mouseX, mouseY)) {
                    bookmarkDisplay.nextPage();
                    return true;
                }

                // Check if back button is clicked
                if (bookmarkDisplay.isBackButtonClicked(mouseX, mouseY)) {
                    bookmarkDisplay.previousPage();
                    return true;
                }
            }

            Optional<String> clickedBookmarkKey = bookmarkDisplay.getBookmarkKeyAt(mouseX, mouseY);
            return clickedBookmarkKey.isPresent();
        }
        return false;
    }
    
    /**
     * Restores the bookmark display from the state manager's cache
     */
    public void restoreFromStaticState() {
        Integer lastActiveFolderId = stateManager.getLastActiveFolderId();
        if (lastActiveFolderId == null) return;

        FolderRowButton activeFolder = stateManager.getActiveFolder();
        if (activeFolder == null) {
            ModLogger.warn("Could not find folder with ID {} to restore", lastActiveFolderId);
            return;
        }

        if (bookmarkDisplay != null) {
            bookmarkDisplay.setActiveFolder(activeFolder.getFolder());
            List<TypedIngredient> lastBookmarkContents = stateManager.getLastBookmarkContents();
            if (!lastBookmarkContents.isEmpty()) {
                // Convert TypedIngredient to BookmarkIngredient using the helper class
                List<BookmarkIngredient> bookmarkIngredients = TypedIngredientHelper.convertToBookmarkIngredients(lastBookmarkContents);
                bookmarkDisplay.setIngredients(bookmarkIngredients);
            }
            updateBookmarkDisplayBounds();
        }
    }
    
    // Getters and setters
    
    public FolderBookmarkContentsDisplay getBookmarkDisplay() {
        return bookmarkDisplay;
    }
    
    public void setCalculatedPositions(int nameY, int bookmarkDisplayY) {
        this.calculatedNameY = nameY;
        this.calculatedBookmarkDisplayY = bookmarkDisplayY;
    }
}