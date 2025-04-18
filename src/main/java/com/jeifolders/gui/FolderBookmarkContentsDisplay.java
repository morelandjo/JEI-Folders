package com.jeifolders.gui;


import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderManager;
import com.jeifolders.integration.JEIIngredientManager;
import com.jeifolders.integration.JEIIntegration;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.List;
import java.util.Optional;

/**
 * Displays bookmarks for a specific folder using JEI's bookmark overlay capabilities.
 */
public class FolderBookmarkContentsDisplay {

    private final FolderManager folderManager;
    private Folder activeFolder;
    private int x;
    private int y;
    private int width;
    private int height;
    private FolderBookmarkOverlay bookmarkOverlay;
    private boolean updatingBounds = false;
    
    // Cache for performance
    private boolean needsRefresh = false;
    private final int[] bounds = new int[4]; // x, y, width, height

    public FolderBookmarkContentsDisplay(FolderManager folderManager) {
        this.folderManager = folderManager;
        JEIIntegration.registerRuntimeAvailableCallback(this::initializeWithJeiRuntime);
        folderManager.loadData();
    }
    
    /**
     * Initialize the display with JEI runtime when it becomes available
     */
    private void initializeWithJeiRuntime(IJeiRuntime runtime) {
        if (activeFolder != null) {
            createOverlayForCurrentFolder(runtime);
        }
    }
    
    /**
     * Creates a bookmark overlay for the current folder
     */
    private void createOverlayForCurrentFolder(IJeiRuntime runtime) {
        if (activeFolder != null) {
            Optional<FolderBookmarkOverlay> overlayOpt = FolderBookmarkOverlay.create(runtime, activeFolder);
            overlayOpt.ifPresent(overlay -> {
                this.bookmarkOverlay = overlay;
                bookmarkOverlay.updateBounds(x, y, width, height);
                refreshBookmarks();
            });
        }
    }

    /**
     * Sets the active folder and ensures bookmarks are properly loaded
     */
    public void setActiveFolder(Folder folder) {
        // Start timing for performance tracking
        long startTime = System.currentTimeMillis();

        // Early return if the folder hasn't changed
        if (this.activeFolder == folder) {
            ModLogger.debug("setActiveFolder called with same folder - skipping update");
            return;
        }
        
        this.activeFolder = folder;
        
        if (folder != null) {
            ModLogger.debug("Setting active folder to: {} (ID: {})", folder.getName(), folder.getId());
            // Get JEI runtime if available
            Optional<IJeiRuntime> runtimeOpt = JEIIntegration.getJeiRuntime();
            if (runtimeOpt.isPresent()) {
                createOverlayForCurrentFolder(runtimeOpt.get());
                
                // Use the cached ingredients for this folder - only load once
                loadCachedIngredientsForCurrentFolder();
                
                long totalTime = System.currentTimeMillis() - startTime;
                ModLogger.info("Folder activation completed in {}ms: {} (ID: {})", 
                    totalTime, folder.getName(), folder.getId());
            }
        } else {
            // Clear the overlay if no folder is active
            bookmarkOverlay = null;
            ModLogger.debug("Cleared active folder");
        }
    }

    /**
     * Loads cached ingredients for the current folder
     * Uses optimized batch loading and minimizes renderable updates
     */
    private void loadCachedIngredientsForCurrentFolder() {
        if (activeFolder == null || bookmarkOverlay == null) {
            return;
        }

        // Start timing for performance tracking
        long startTime = System.currentTimeMillis();

        // Use the cached ingredients method - it will handle batch processing efficiently
        List<ITypedIngredient<?>> cachedIngredients = folderManager.getCachedIngredientsForFolder(activeFolder.getId());
        
        if (bookmarkOverlay != null) {
            // Set the ingredients just once to avoid redundant processing
            bookmarkOverlay.setIngredients(cachedIngredients);
            
            long totalTime = System.currentTimeMillis() - startTime;
            ModLogger.info("Loaded {} ingredients for folder: {} in {}ms", 
                cachedIngredients.size(), activeFolder.getName(), totalTime);
        }
    }

    /**
     * Updates the bounds of the bookmark display.
     * Uses cached values to avoid unnecessary updates.
     */
    public void updateBounds(int x, int y, int width, int height) {
        // Check if bounds are actually changing
        if (bounds[0] == x && bounds[1] == y && bounds[2] == width && bounds[3] == height) {
            return; // No change in bounds
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
     * Used for state restoration between UI rebuilds.
     * 
     * @param ingredients The list of typed ingredients to display
     */
    public void setIngredients(List<ITypedIngredient<?>> ingredients) {
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
     * Used for state preservation between UI rebuilds.
     * 
     * @return The list of current typed ingredients
     */
    public List<ITypedIngredient<?>> getIngredients() {
        if (bookmarkOverlay != null) {
            return bookmarkOverlay.getIngredients();
        }
        return List.of(); // Return empty list if overlay is not available
    }

    /**
     * Refreshes bookmarks from the active folder.
     * Uses optimized batch loading and minimizes updates.
     */
    public void refreshBookmarks() {
        if (activeFolder == null || bookmarkOverlay == null) {
            return;
        }

        // Start timing for performance tracking
        long startTime = System.currentTimeMillis();

        // Invalidate the cache for this folder to ensure fresh data
        folderManager.invalidateIngredientsCache(activeFolder.getId());
        
        // Then reload using the batch processing method
        loadCachedIngredientsForCurrentFolder();
        
        long totalTime = System.currentTimeMillis() - startTime;
        ModLogger.debug("Refreshed bookmarks for folder {} in {}ms", 
            activeFolder.getName(), totalTime);
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
                
                // Only draw tooltips if mouse is over for performance
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
     * Uses direct comparison instead of creating new objects.
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
        
        // Only proceed if mouse is over this area - use the fast check
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