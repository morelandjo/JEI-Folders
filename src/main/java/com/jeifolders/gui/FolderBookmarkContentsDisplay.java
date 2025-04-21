package com.jeifolders.gui;


import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.data.FolderDataManager;
import com.jeifolders.integration.BookmarkDisplayHelper;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Optional;

/**
 * Displays bookmarks for a specific folder.
 */
public class FolderBookmarkContentsDisplay implements FolderChangeListener {

    private final FolderDataManager folderManager;
    private FolderDataRepresentation activeFolder;
    private int x;
    private int y;
    private int width;
    private int height;
    private FolderContentsDisplay bookmarkOverlay;
    private boolean updatingBounds = false;
    
    // Replace direct JEI references with our helper
    private final BookmarkDisplayHelper displayHelper = new BookmarkDisplayHelper();
    
    // Cache for performance
    private boolean needsRefresh = false;
    private final int[] bounds = new int[4]; // x, y, width, height

    public FolderBookmarkContentsDisplay(FolderDataManager folderManager) {
        this.folderManager = folderManager;
        
        // Register as listener for folder changes
        folderManager.addFolderChangeListener(this);
        
        // Load data on creation
        folderManager.loadData();
    }
    
    @Override
    public void onFolderContentsChanged(int folderId) {
        // Check if this is our active folder
        if (activeFolder != null && activeFolder.getId() == folderId) {
            ModLogger.info("Active folder {} was modified, refreshing display", folderId);
            // Force a refresh on next render
            needsRefresh = true;
        }
    }

    /**
     * Sets the active folder and ensures bookmarks are properly loaded
     */
    public void setActiveFolder(FolderDataRepresentation folder) {
        // Early return if the folder hasn't changed
        if (this.activeFolder == folder) {
            ModLogger.debug("setActiveFolder called with same folder - skipping update");
            return;
        }
        
        this.activeFolder = folder;
        
        if (folder != null) {
            ModLogger.debug("Setting active folder to: {} (ID: {})", folder.getName(), folder.getId());
            createOverlayForCurrentFolder();
            
            // Use the cached ingredients for this folder
            loadCachedIngredientsForCurrentFolder();
            
        } else {
            // Clear the overlay if no folder is active
            bookmarkOverlay = null;
            ModLogger.debug("Cleared active folder");
        }
    }

    /**
     * Creates a bookmark overlay for the current folder
     */
    private void createOverlayForCurrentFolder() {
        if (activeFolder != null) {
            Optional<FolderContentsDisplay> overlayOpt = FolderContentsDisplay.create(displayHelper, activeFolder);
            overlayOpt.ifPresent(overlay -> {
                this.bookmarkOverlay = overlay;
                bookmarkOverlay.updateBounds(x, y, width, height);
                refreshBookmarks();
            });
        }
    }

    /**
     * Loads cached ingredients for the current folder
     */
    private void loadCachedIngredientsForCurrentFolder() {
        if (activeFolder == null || bookmarkOverlay == null) {
            return;
        }

        List<BookmarkIngredient> cachedIngredients = displayHelper.getCachedIngredientsForFolder(activeFolder.getId());
        
        if (bookmarkOverlay != null) {
            bookmarkOverlay.setIngredients(cachedIngredients);
        }
    }

    /**
     * Updates the bounds of the bookmark display.
     */
    public void updateBounds(int x, int y, int width, int height) {
        // Check if bounds are actually changing
        if (bounds[0] == x && bounds[1] == y && bounds[2] == width && bounds[3] == height) {
            return;
        }
        
        // Store the dimensions in both class fields and cache array
        this.x = bounds[0] = x;
        this.y = bounds[1] = y;
        this.width = bounds[2] = width;
        this.height = bounds[3] = height;
        
        // Guard against recursive calls
        if (updatingBounds) {
            return;
        }
        
        updatingBounds = true;
        try {
            // Update the current overlay's bounds if available
            if (bookmarkOverlay != null) {
                bookmarkOverlay.updateBounds(x, y, width, height);
            }
        } finally {
            updatingBounds = false;
        }
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    
    /**
     * Sets the ingredients directly in the bookmark overlay.
     * 
     * @param ingredients The list of typed ingredients to display
     */
    public void setIngredients(List<BookmarkIngredient> ingredients) {
        if (bookmarkOverlay != null) {
            try {
                bookmarkOverlay.setIngredients(ingredients);
            } catch (Exception e) {
                ModLogger.error("Error setting ingredients in bookmark overlay: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Gets the current ingredients from the bookmark overlay.
     * 
     * @return The list of current typed ingredients
     */
    public List<BookmarkIngredient> getIngredients() {
        if (bookmarkOverlay != null) {
            return bookmarkOverlay.getIngredients();
        }
        return List.of(); // Return empty list if overlay is not available
    }

    /**
     * Refreshes bookmarks from the active folder.
     */
    public void refreshBookmarks() {
        if (activeFolder == null) {
            ModLogger.debug("Cannot refresh bookmarks - no active folder");
            return;
        }

        // Invalidate the cache for this folder to ensure fresh data
        displayHelper.invalidateIngredientsCache(activeFolder.getId());
        
        // Log the folder's current bookmarks
        List<String> bookmarkKeys = folderManager.getFolderBookmarkKeys(activeFolder.getId());
        ModLogger.info("Refreshing bookmarks for folder {} with {} bookmarks", 
            activeFolder.getName(), bookmarkKeys.size());

        if (bookmarkKeys.isEmpty()) {
            ModLogger.info("No bookmarks to display for folder: {}", activeFolder.getName());
            if (bookmarkOverlay != null) {
                // Clear the overlay to show empty state
                bookmarkOverlay.setIngredients(List.of());
            }
            return;
        }
        
        // Then reload using the batch processing method
        loadCachedIngredientsForCurrentFolder();
        
        // Force update the overlay if it exists
        if (bookmarkOverlay != null) {
            bookmarkOverlay.refreshContents();
        }
        
    }

    /**
     * Mark that bookmarks need refreshing on next render
     */
    public void scheduleRefresh() {
        needsRefresh = true;
    }

    /**
     * Renders the bookmarks for the active folder
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Check if we need to do a deferred refresh
        if (needsRefresh) {
            refreshBookmarks();
            needsRefresh = false;
        }
        
        if (bookmarkOverlay != null && activeFolder != null) {
            try {
                bookmarkOverlay.draw(Minecraft.getInstance(), graphics, mouseX, mouseY, partialTick);
                
                // Only draw tooltips if mouse is over
                if (isMouseOver(mouseX, mouseY)) {
                    bookmarkOverlay.drawTooltips(Minecraft.getInstance(), graphics, mouseX, mouseY);
                }
            } catch (Exception e) {
                ModLogger.error("Error rendering bookmark overlay: {}", e.getMessage());
            }
        }
    }

    /**
     * Fast method to check if the given coordinates are over this display.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && 
               mouseY >= y && mouseY < y + height;
    }

    /**
     * Gets the bookmark key at the given coordinates, if any.
     */
    public Optional<String> getBookmarkKeyAt(double mouseX, double mouseY) {
        if (activeFolder == null || bookmarkOverlay == null) {
            return Optional.empty();
        }
        
        // Only proceed if mouse is over this area
        if (!isMouseOver(mouseX, mouseY)) {
            return Optional.empty();
        }
        
        return bookmarkOverlay.getBookmarkKeyAt(mouseX, mouseY);
    }

    /**
     * Add a method to get the current page number
     */
    public int getCurrentPageNumber() {
        if (bookmarkOverlay != null) {
            return bookmarkOverlay.getCurrentPageNumber();
        }
        return 1;
    }

    /**
     * Add a method to get the total number of pages
     */
    public int getPageCount() {
        if (bookmarkOverlay != null) {
            return bookmarkOverlay.getPageCount();
        }
        return 1;
    }

    /**
     * Add a method to navigate to the next page
     */
    public void nextPage() {
        if (bookmarkOverlay != null) {
            bookmarkOverlay.nextPage();
        }
    }

    /**
     * Add a method to navigate to the previous page
     */
    public void previousPage() {
        if (bookmarkOverlay != null) {
            bookmarkOverlay.previousPage();
        }
    }

    /**
     * Add a method to check if the next button is clicked
     */
    public boolean isNextButtonClicked(double mouseX, double mouseY) {
        if (bookmarkOverlay != null) {
            return bookmarkOverlay.isNextButtonClicked(mouseX, mouseY);
        }
        return false;
    }

    /**
     * Add a method to check if the back button is clicked
     */
    public boolean isBackButtonClicked(double mouseX, double mouseY) {
        if (bookmarkOverlay != null) {
            return bookmarkOverlay.isBackButtonClicked(mouseX, mouseY);
        }
        return false;
    }
}