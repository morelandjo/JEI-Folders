package com.jeifolders.gui.bookmarks;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.folderButtons.FolderChangeListener;
import com.jeifolders.data.FolderDataManager;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.integration.impl.JeiBookmarkAdapter;
import com.jeifolders.integration.impl.JeiContentsImpl;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unified display for folder bookmarks that handles both display management and rendering.
 * This class combines functionality previously split between FolderBookmarkContentsDisplay and FolderContentsDisplay.
 */
public class UnifiedFolderContentsDisplay implements FolderChangeListener {
    // Constants
    private static final int MIN_CONTENT_HEIGHT = 40;
    private static final int MIN_CONTENT_WIDTH = 80;

    // Core components
    private final FolderDataManager folderManager;
    private final IngredientService ingredientService;
    private final FolderBookmarkList bookmarkList;
    private final JeiBookmarkAdapter bookmarkAdapter;
    private final JeiContentsImpl contentsImpl;
    
    // State tracking
    private FolderDataRepresentation activeFolder;
    private Rectangle2i backgroundArea = Rectangle2i.EMPTY;
    private boolean updatingBounds = false;
    private boolean needsRefresh = false;
    private List<BookmarkIngredient> ingredients = new ArrayList<>();
    
    // Layout properties
    private int x;
    private int y;
    private int width;
    private int height;
    private final int[] bounds = new int[4]; // x, y, width, height
    
    // Performance optimization
    private long lastUpdateTimestamp = 0;
    private int updateCounter = 0;
    private Rectangle2i lastCalculatedArea = Rectangle2i.EMPTY;

    /**
     * Creates a new unified folder contents display
     */
    private UnifiedFolderContentsDisplay(
        FolderDataManager folderManager, 
        FolderBookmarkList bookmarkList,
        JeiBookmarkAdapter bookmarkAdapter, 
        JeiContentsImpl contentsImpl
    ) {
        this.folderManager = folderManager;
        this.ingredientService = JEIIntegrationFactory.getIngredientService();
        this.bookmarkList = bookmarkList;
        this.bookmarkAdapter = bookmarkAdapter;
        this.contentsImpl = contentsImpl;
        
        // Register as listener for folder changes
        folderManager.addFolderChangeListener(this);
    }

    /**
     * Creates a new instance of UnifiedFolderContentsDisplay
     * @param folderManager The folder data manager
     * @return An optional containing the new display if creation was successful
     */
    public static Optional<UnifiedFolderContentsDisplay> create(FolderDataManager folderManager) {
        try {
            // Get the JEI service and runtime directly
            var jeiService = JEIIntegrationFactory.getJEIService();
            var jeiRuntimeOpt = jeiService.getJeiRuntime();
            
            if (jeiRuntimeOpt.isEmpty()) {
                ModLogger.warn("Cannot create display - JEI runtime not available");
                return Optional.empty();
            }
            
            // Create a bookmark list
            var bookmarkList = new FolderBookmarkList();
            var bookmarkAdapter = new JeiBookmarkAdapter(bookmarkList);
            var contentsImpl = new JeiContentsImpl(bookmarkAdapter, jeiRuntimeOpt.get());
            
            // Create the display with the JEI components
            var display = new UnifiedFolderContentsDisplay(
                folderManager,
                bookmarkList,
                bookmarkAdapter,
                contentsImpl
            );
            
            return Optional.of(display);
        } catch (Exception e) {
            ModLogger.error("Failed to create unified folder contents display: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Creates a new instance for a specific folder
     * @param folderManager The folder data manager
     * @param folder The folder to display
     * @return An optional containing the new display if creation was successful
     */
    public static Optional<UnifiedFolderContentsDisplay> createForFolder(
        FolderDataManager folderManager,
        FolderDataRepresentation folder
    ) {
        var displayOpt = create(folderManager);
        
        displayOpt.ifPresent(display -> {
            display.setActiveFolder(folder);
        });
        
        return displayOpt;
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
            this.bookmarkList.setFolder(folder);
            refreshBookmarks();
        } else {
            // Clear the current state if no folder is active
            ingredients.clear();
            ModLogger.debug("Cleared active folder");
        }
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
        ingredientService.invalidateIngredientsCache(activeFolder.getId());
        
        // Log the folder's current bookmarks
        List<String> bookmarkKeys = folderManager.getFolderBookmarkKeys(activeFolder.getId());
        ModLogger.info("Refreshing bookmarks for folder {} with {} bookmarks", 
            activeFolder.getName(), bookmarkKeys.size());

        if (bookmarkKeys.isEmpty()) {
            ModLogger.info("No bookmarks to display for folder: {}", activeFolder.getName());
            setIngredients(List.of());
            return;
        }
        
        // Load ingredients from keys
        var cachedIngredients = ingredientService.getCachedBookmarkIngredientsForFolder(activeFolder.getId());
        setIngredients(cachedIngredients);
        
        // Force layout update - use updateLayout instead of refreshContents
        if (contentsImpl != null) {
            contentsImpl.updateLayout(true);
        }
    }

    /**
     * Sets the ingredients to be displayed.
     */
    public void setIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        if (bookmarkIngredients == null) {
            bookmarkIngredients = new ArrayList<>();
        }
        
        // Store the new ingredients
        this.ingredients = new ArrayList<>(bookmarkIngredients);
        
        try {
            // Set ingredients in the bookmarks list
            bookmarkList.setIngredients(bookmarkIngredients);
            
            // Set ingredients in the contents implementation
            contentsImpl.setIngredients(bookmarkIngredients);
        } catch (Exception e) {
            ModLogger.error("Error setting ingredients: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the current ingredients.
     */
    public List<BookmarkIngredient> getIngredients() {
        return new ArrayList<>(this.ingredients);
    }

    /**
     * Updates the bounds of the display.
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
            // Calculate content area
            int availableWidth = Math.max(MIN_CONTENT_WIDTH, width);
            int availableHeight = Math.max(MIN_CONTENT_HEIGHT, height);
            Rectangle2i newBounds = new Rectangle2i(x, y, availableWidth, availableHeight);
            
            // Update the contents implementation bounds
            boolean contentsBoundsUpdated = contentsImpl.updateBounds(newBounds);
            
            // Update layout if needed
            if (contentsBoundsUpdated) {
                contentsImpl.updateLayout(true);
            }
            
            // Update background area
            Rectangle2i newBackground = contentsImpl.getBackgroundArea();
            if (newBackground != null && !newBackground.isEmpty()) {
                this.backgroundArea = newBackground;
            } else {
                this.backgroundArea = newBounds;
            }
            
            lastCalculatedArea = newBounds;
        } finally {
            updatingBounds = false;
        }
    }

    /**
     * Renders the bookmarks display
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Check if we need to do a deferred refresh
        if (needsRefresh) {
            refreshBookmarks();
            needsRefresh = false;
        }
        
        if (activeFolder != null) {
            try {
                contentsImpl.draw(Minecraft.getInstance(), graphics, mouseX, mouseY, partialTick);
                
                // Only draw tooltips if mouse is over
                if (isMouseOver(mouseX, mouseY)) {
                    contentsImpl.drawTooltips(Minecraft.getInstance(), graphics, mouseX, mouseY);
                }
            } catch (Exception e) {
                ModLogger.error("Error rendering bookmark display: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Checks if mouse coordinates are over this display.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && 
               mouseY >= y && mouseY < y + height;
    }

    /**
     * Gets the bookmark key at the given coordinates, if any.
     */
    public Optional<String> getBookmarkKeyAt(double mouseX, double mouseY) {
        if (activeFolder == null || !isMouseOver(mouseX, mouseY)) {
            return Optional.empty();
        }
        
        return contentsImpl.getBookmarkKeyAt(mouseX, mouseY);
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
    
    // Getters for coordinates
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Rectangle2i getBackgroundArea() { return backgroundArea; }
    
    /**
     * Force a refresh of the contents and layout
     */
    public void forceRefresh() {
        refreshBookmarks();
        if (contentsImpl != null) {
            contentsImpl.updateLayout(true);
        }
    }
}