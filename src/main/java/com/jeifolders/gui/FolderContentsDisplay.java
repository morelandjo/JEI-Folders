package com.jeifolders.gui;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.integration.BookmarkDisplayHelper;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.integration.impl.JeiBookmarkAdapter;
import com.jeifolders.integration.impl.JeiContentsImpl;
import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.IngredientService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class handles displaying ingredients/contents from a folder
 */
public class FolderContentsDisplay {
    
    private static final int MIN_CONTENT_HEIGHT = 40;
    private static final int MIN_CONTENT_WIDTH = 80;

    private long lastUpdateTimestamp = 0;
    private int updateCounter = 0;

    private final FolderBookmarkList bookmarkList;
    private final JeiContentsImpl contentsImpl;

    private FolderDataRepresentation currentFolder;
    private Rectangle2i backgroundArea = Rectangle2i.EMPTY;

    private Rectangle2i lastCalculatedArea = Rectangle2i.EMPTY;
    private boolean updatingBounds = false;
    private List<BookmarkIngredient> ingredients = new ArrayList<>();
    
    // Book adapter to handle JEI-specific bookmark operations
    private final JeiBookmarkAdapter bookmarkAdapter;

    /**
     * Private constructor
     */
    private FolderContentsDisplay(
        FolderBookmarkList bookmarkList,
        JeiBookmarkAdapter bookmarkAdapter,
        JeiContentsImpl contentsImpl
    ) {
        this.bookmarkList = bookmarkList;
        this.bookmarkAdapter = bookmarkAdapter;
        this.contentsImpl = contentsImpl;
    }

    /**
     * Create a FolderContentsDisplay instance
     */
    public static Optional<FolderContentsDisplay> create(
        BookmarkDisplayHelper helper,
        FolderDataRepresentation folder
    ) {
        try {
            // Get the JEI runtime
            Optional<Object> jeiRuntimeOpt = helper.getJeiRuntime();
            if (jeiRuntimeOpt.isEmpty()) {
                ModLogger.warn("Cannot create display - JEI runtime not available");
                return Optional.empty();
            }
            
            // Create a bookmark list for this folder
            FolderBookmarkList bookmarkList = new FolderBookmarkList();
            bookmarkList.setFolder(folder);
            
            JeiBookmarkAdapter bookmarkAdapter = new JeiBookmarkAdapter(bookmarkList);
            
            JeiContentsImpl contentsImpl = new JeiContentsImpl(bookmarkAdapter, jeiRuntimeOpt.get());
            
            // Create the display with the JEI components
            FolderContentsDisplay display = new FolderContentsDisplay(
                bookmarkList,
                bookmarkAdapter,
                contentsImpl
            );
            
            display.setFolder(folder);
            return Optional.of(display);
        } catch (Exception e) {
            ModLogger.error("Failed to create folder contents display: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Sets the current folder for this display
     */
    public void setFolder(FolderDataRepresentation folder) {
        this.currentFolder = folder;
        this.bookmarkList.setFolder(folder);
    }

    /**
     * Sets the ingredients to be displayed in the display.
     */
    public void setIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        updateIngredientsList(bookmarkIngredients, false, false);
    }

    /**
     * Central method for ingredient management
     * @param newIngredients The new ingredients to set
     * @param forceUpdate Force update even if ingredients haven't changed
     * @param clearBookmarks Whether to clear bookmarks before setting ingredients
     * @return true if ingredients were updated, false otherwise
     */
    private boolean updateIngredientsList(List<BookmarkIngredient> newIngredients, boolean forceUpdate, boolean clearBookmarks) {
        if (newIngredients == null) {
            newIngredients = new ArrayList<>();
        }
        
        String folderInfo = currentFolder != null ? 
            currentFolder.getName() + " (ID: " + currentFolder.getId() + ")" : "null";
            
        // Check if update is needed
        boolean needsUpdate = forceUpdate;
        if (!needsUpdate) {
            if (newIngredients.size() != this.ingredients.size()) {
                needsUpdate = true;
            } else if (!newIngredients.isEmpty()) {
                // Check contents for differences
                for (int i = 0; i < newIngredients.size(); i++) {
                    if (!newIngredients.get(i).toString().equals(this.ingredients.get(i).toString())) {
                        needsUpdate = true;
                        break;
                    }
                }
            }
        }
        
        // Update ingredients if needed
        if (needsUpdate || this.ingredients.isEmpty()) {
            ModLogger.debug("Updating ingredients in display: {} items for folder: {}", 
                newIngredients.size(), folderInfo);
                
            // Store the new ingredients
            this.ingredients = new ArrayList<>(newIngredients);
            
            try {
                // Clear bookmarks if requested
                if (clearBookmarks) {
                    bookmarkList.clearBookmarks();
                }
                
                // Set ingredients in the contents implementation
                contentsImpl.setIngredients(newIngredients);
                
                return true;
            } catch (Exception e) {
                ModLogger.error("Error updating ingredients: {}", e.getMessage(), e);
                return false;
            }
        } else {
            ModLogger.debug("Skipped ingredient update - no changes detected for folder: {}", folderInfo);
            return false;
        }
    }

    /**
     * Update bookmarks that need refreshing
     * 
     * @param currentIngredients The current ingredients to evaluate
     * @return True if any changes were made
     */
    private boolean refreshBookmarksSelectively(List<BookmarkIngredient> currentIngredients) {
        if (currentIngredients == null || currentIngredients.isEmpty()) {
            return false;
        }
        
        boolean changesApplied = false;
        ModLogger.debug("Selectively refreshing bookmarks...");
        
        try {
            // Get the ingredient service
            IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
            
            // Get current state from display implementation
            List<Object> existingBookmarks = bookmarkList.getAllBookmarks();
            List<BookmarkIngredient> existingIngredients = bookmarkList.getIngredients();
            
            // Create maps
            java.util.Map<String, BookmarkIngredient> currentMap = new java.util.HashMap<>();
            java.util.Map<String, BookmarkIngredient> existingMap = new java.util.HashMap<>();
            
            // Populate maps with ingredient key -> ingredient
            for (BookmarkIngredient ingredient : currentIngredients) {
                String key = ingredientService.getKeyForIngredient(ingredient);
                if (key != null && !key.isEmpty()) {
                    currentMap.put(key, ingredient);
                }
            }
            
            for (BookmarkIngredient ingredient : existingIngredients) {
                String key = ingredientService.getKeyForIngredient(ingredient);
                if (key != null && !key.isEmpty()) {
                    existingMap.put(key, ingredient);
                }
            }
            
            // Identify ingredients to add, remove, or keep
            java.util.Set<String> toAdd = new java.util.HashSet<>(currentMap.keySet());
            toAdd.removeAll(existingMap.keySet());
            
            java.util.Set<String> toRemove = new java.util.HashSet<>(existingMap.keySet());
            toRemove.removeAll(currentMap.keySet());
            
            // Apply selective changes
            for (String key : toAdd) {
                BookmarkIngredient ingredient = currentMap.get(key);
                bookmarkList.addBookmark(ingredient);
                changesApplied = true;
            }
            
            for (String key : toRemove) {
                BookmarkIngredient ingredient = existingMap.get(key);
                bookmarkList.removeBookmark(ingredient);
                changesApplied = true;
            }
            
            // Apply rendering update if we made changes
            if (changesApplied) {
                // Force content update in JEI content display
                contentsImpl.setIngredients(currentIngredients);
            }
            
            ModLogger.debug("Selective refresh complete - added {} items, removed {} items", 
                toAdd.size(), toRemove.size());
                
            return changesApplied;
            
        } catch (Exception e) {
            ModLogger.error("Error during selective bookmark refresh: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Draws the ingredients in the display.
     */
    public void draw(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (ingredients.isEmpty()) {
            ModLogger.debug("Skipping display draw - no ingredients to display");
            return;
        }

        try {
            // Check if bookmark content appears valid before drawing
            if (backgroundArea.isEmpty() || backgroundArea.getWidth() <= 0 || backgroundArea.getHeight() <= 0) {
                ModLogger.warn("Invalid background area for display: {}, cannot render", backgroundArea);
                return;
            }
            
            contentsImpl.draw(minecraft, graphics, mouseX, mouseY, partialTick);
        } catch (Exception e) {
            ModLogger.error("Error rendering contents display: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the bounds of the display
     */
    public void updateBounds(int x, int y, int width, int height) {
        // Check if we should skip this update
        if (shouldSkipUpdate()) {
            return;
        }
        
        updatingBounds = true;
        
        try {
            // Calculate new bounds based on input parameters
            Rectangle2i expectedArea = calculateNewBounds(x, y, width, height);
            
            // Check if the change is significant enough to update
            if (!hasSignificantBoundsChange(expectedArea)) {
                ModLogger.debug("Skipping bounds update - no significant change");
                return;
            }
            
            // Update tracking fields
            lastCalculatedArea = expectedArea;
            
            // Apply the bounds update
            applyBoundsUpdate(expectedArea);
        } finally {
            updatingBounds = false;
        }
    }

    /**
     * Determines if the update should be skipped
     * @return true if the update should be skipped
     */
    private boolean shouldSkipUpdate() {
        // Avoid recursive calls
        if (updatingBounds) {
            ModLogger.debug("Recursive updateBounds call prevented");
            return true;
        }
        
        // Rate limiting
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTimestamp < 250) {
            updateCounter++;
            if (updateCounter > 4) {
                return true;
            }
        } else {
            updateCounter = 0;
        }
        lastUpdateTimestamp = currentTime;
        
        return false;
    }

    /**
     * Calculates new bounds based on input parameters
     * @return The calculated bounds rectangle
     */
    private Rectangle2i calculateNewBounds(int x, int y, int width, int height) {
        x = 0;
        
        // Ensure minimum dimensions
        int availableWidth = Math.max(MIN_CONTENT_WIDTH, width);
        int availableHeight = Math.max(MIN_CONTENT_HEIGHT, height);
        
        // Create the expected area
        return new Rectangle2i(x, y, availableWidth, availableHeight);
    }

    /**
     * Determines if the bounds change is significant enough to update
     * @param newBounds The new calculated bounds
     * @return true if the change is significant
     */
    private boolean hasSignificantBoundsChange(Rectangle2i newBounds) {
        // Skip if background area is empty (first initialization)
        if (backgroundArea.isEmpty()) {
            return true;
        }
        
        // Check dimension tolerance
        boolean dimensionsEqual = Math.abs(newBounds.getWidth() - lastCalculatedArea.getWidth()) <= 2 &&
                                 Math.abs(newBounds.getHeight() - lastCalculatedArea.getHeight()) <= 2;
                                 
        // Check exact position equality
        boolean positionEqual = newBounds.getX() == lastCalculatedArea.getX() && 
                               newBounds.getY() == lastCalculatedArea.getY();
                               
        // If both dimensions and position are nearly the same, no need to update
        return !(dimensionsEqual && positionEqual);
    }

    /**
     * Applies the bounds update to the contents and background
     * @param newBounds The new bounds to apply
     */
    private void applyBoundsUpdate(Rectangle2i newBounds) {
        // Update the bounds with our calculated values
        boolean contentsBoundsUpdated = contentsImpl.updateBounds(newBounds);
        
        // Only update layout if dimensions actually changed AND bounds update succeeded
        boolean dimensionsChanged = Math.abs(newBounds.getWidth() - backgroundArea.getWidth()) > 2 ||
                                   Math.abs(newBounds.getHeight() - backgroundArea.getHeight()) > 2;
                                   
        if (dimensionsChanged && contentsBoundsUpdated) {
            contentsImpl.updateLayout(true);
        }
        
        // Update background area
        Rectangle2i newBackground = contentsImpl.getBackgroundArea();
        
        if (newBackground != null && !newBackground.isEmpty()) {
            this.backgroundArea = newBackground;
        } else {
            this.backgroundArea = newBounds;
            ModLogger.warn("Content's background area was empty, using expected area instead");
        }
        
        ModLogger.debug("Contents display updated to: x={}, y={}, width={}, height={}", 
            backgroundArea.getX(), backgroundArea.getY(),
            backgroundArea.getWidth(), backgroundArea.getHeight());
    }

    /**
     * Re-applies the current ingredients to refresh the display
     */
    public void refreshContents() {
        ModLogger.debug("Refreshing display contents, current ingredients: {}", ingredients.size());
        if (!ingredients.isEmpty()) {
            // Make a copy of ingredients to refresh the display
            List<BookmarkIngredient> currentIngredients = new ArrayList<>(ingredients);
            
            // Try selective refresh
            boolean selectively = refreshBookmarksSelectively(currentIngredients);
            
            // If selective refresh didn't work, fall back to full refresh
            if (!selectively) {
                ModLogger.debug("Selective refresh failed, falling back to full refresh");
                updateIngredientsList(currentIngredients, true, true);
            }
            
            try {
                // Force a layout update
                contentsImpl.updateLayout(true);
            } catch (Exception e) {
                ModLogger.error("Error during refresh: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Returns true if the display has content to display and is ready to be drawn
     */
    public boolean isReadyToDraw() {
        boolean hasIngredients = !ingredients.isEmpty();
        boolean hasValidArea = !backgroundArea.isEmpty() && backgroundArea.getWidth() > 0 && backgroundArea.getHeight() > 0;
        
        if (!hasIngredients || !hasValidArea) {
            ModLogger.debug("Display not ready to draw - hasIngredients: {}, hasValidArea: {}", 
                hasIngredients, hasValidArea);
        }
        
        return hasIngredients && hasValidArea;
    }

    /**
     * Draw tooltips for the display
     */
    public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        contentsImpl.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
    }

    /**
     * Check if the mouse is over the display
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.backgroundArea.contains(mouseX, mouseY);
    }

    /**
     * Gets the bookmark key at the given mouse coordinates, if any.
     */
    public Optional<String> getBookmarkKeyAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return Optional.empty();
        }

        return contentsImpl.getBookmarkKeyAt(mouseX, mouseY);
    }

    /**
     * Gets the current ingredients displayed in the display.
     * @return The list of ingredients currently displayed
     */
    public List<BookmarkIngredient> getIngredients() {
        return new ArrayList<>(this.ingredients);
    }

    /**
     * Navigate to the next page
     */
    public void nextPage() {
        contentsImpl.nextPage();
    }

    /**
     * Navigate to the previous page
     */
    public void previousPage() {
        contentsImpl.previousPage();
    }

    /**
     * Get the current page number (1-indexed for display)
     */
    public int getCurrentPageNumber() {
        return contentsImpl.getCurrentPageNumber();
    }

    /**
     * Get the total number of pages
     */
    public int getPageCount() {
        return contentsImpl.getPageCount();
    }

    /**
     * Checks if a point is over the next button
     */
    public boolean isNextButtonClicked(double mouseX, double mouseY) {
        return contentsImpl.isNextButtonClicked(mouseX, mouseY);
    }

    /**
     * Checks if a point is over the back button
     */
    public boolean isBackButtonClicked(double mouseX, double mouseY) {
        return contentsImpl.isBackButtonClicked(mouseX, mouseY);
    }
}