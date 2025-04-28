package com.jeifolders.gui.view.contents;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.gui.common.HitTestable;
import com.jeifolders.gui.common.LayoutConstants;
import com.jeifolders.gui.common.MouseHitUtil;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.event.FolderEventListener;
import com.jeifolders.gui.event.FolderEventType;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.integration.impl.JeiBookmarkAdapter;
import com.jeifolders.integration.impl.JeiContentsImpl;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages folder contents data, layout, and interaction.
 * Rendering is now handled by the ContentViewRenderer for better separation.
 */
public class FolderContentsView implements HitTestable {
    // Constants
    private static final int MIN_CONTENT_HEIGHT = 40;
    private static final int MIN_CONTENT_WIDTH = 80;
    private static final int DEFAULT_DISPLAY_WIDTH = 200;
    private static final int DEFAULT_DISPLAY_HEIGHT = 100;

    // Core components
    private final FolderStorageService folderService;
    private final FolderBookmarkList bookmarkList;
    private final JeiContentsImpl contentsImpl;
    
    // State tracking
    private Folder activeFolder;
    private Rectangle2i backgroundArea = Rectangle2i.EMPTY;
    private boolean updatingBounds = false;
    private boolean needsRefresh = false;
    private List<BookmarkIngredient> ingredients = new ArrayList<>();
    private boolean refreshingBookmarks = false; // Add a refreshing state guard to prevent recursion
    
    // Layout properties
    private int x;
    private int y;
    private int width = DEFAULT_DISPLAY_WIDTH;
    private int height = DEFAULT_DISPLAY_HEIGHT;
    private final int[] bounds = new int[4]; // x, y, width, height
    private Rectangle2i lastCalculatedBounds = null;
    private int calculatedNameY = -1;
    private int calculatedDisplayY = -1;
    
    // Event system
    private final FolderStateManager eventManager = FolderStateManager.getInstance();
    private final FolderEventListener folderChangedListener;

    /**
     * Creates a new unified folder contents display
     */
    private FolderContentsView(
        FolderStorageService folderService, 
        FolderBookmarkList bookmarkList,
        JeiBookmarkAdapter bookmarkAdapter, 
        JeiContentsImpl contentsImpl
    ) {
        this.folderService = folderService;
        this.bookmarkList = bookmarkList;
        this.contentsImpl = contentsImpl;
        
        // Folder changed listener
        this.folderChangedListener = event -> {
            if (activeFolder != null && event.getFolderId() == activeFolder.getId()) {
                ModLogger.info("FolderEventManager: Active folder {} was modified, refreshing display", event.getFolderId());
                needsRefresh = true;
            }
        };
        
        // Register with event system
        eventManager.addEventListener(FolderEventType.FOLDER_CONTENTS_CHANGED, folderChangedListener);
    }

    /**
     * Creates a new instance of UnifiedFolderContentsDisplay
     * @param folderService The folder data service
     * @return An optional containing the new display if creation was successful
     */
    public static Optional<FolderContentsView> create(FolderStorageService folderService) {
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
            var display = new FolderContentsView(
                folderService,
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
     * @param folderService The folder data service
     * @param folder The folder to display
     * @return An optional containing the new display if creation was successful
     */
    public static Optional<FolderContentsView> createForFolder(
        FolderStorageService folderService,
        Folder folder
    ) {
        var displayOpt = create(folderService);
        
        displayOpt.ifPresent(display -> {
            display.setActiveFolder(folder);
        });
        
        return displayOpt;
    }

    /**
     * Sets the active folder and ensures bookmarks are properly loaded
     */
    public void setActiveFolder(Folder folder) {
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
     * Gets the active folder
     * @return The active folder or null if none is active
     */
    public Folder getActiveFolder() {
        return activeFolder;
    }

    /**
     * Refreshes bookmarks from the active folder.
     * This now delegates to the central refresh method in FolderStateManager.
     */
    public void refreshBookmarks() {
        if (activeFolder == null) {
            ModLogger.debug("Cannot refresh bookmarks - no active folder");
            return;
        }
        
        // Prevent recursion
        if (refreshingBookmarks) {
            ModLogger.debug("Preventing recursive bookmark refresh");
            return;
        }

        try {
            refreshingBookmarks = true;
            // Delegate to the centralized method in FolderStateManager
            eventManager.refreshFolderBookmarks(activeFolder, true);
        } finally {
            refreshingBookmarks = false;
        }
    }
    
    /**
     * Force a refresh of the contents and layout
     */
    public void forceRefresh() {
        // Delegate to the centralized method in FolderStateManager
        if (activeFolder != null) {
            eventManager.refreshFolderBookmarks(activeFolder, true);
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
        // Calculate safe width to prevent GUI overlap
        int safeWidth = getSafeDisplayWidth(width);
        
        // Check if bounds are actually changing
        if (bounds[0] == x && bounds[1] == y && bounds[2] == safeWidth && bounds[3] == height) {
            return;
        }
        
        int currentPage = getCurrentPageNumber();
        
        // Store the dimensions in both class fields and cache array
        this.x = bounds[0] = x;
        this.y = bounds[1] = y;
        this.width = bounds[2] = safeWidth;
        this.height = bounds[3] = height;
        
        // Guard against recursive calls
        if (updatingBounds) {
            return;
        }
        
        updatingBounds = true;
        try {
            // Calculate content area
            int availableWidth = Math.max(MIN_CONTENT_WIDTH, safeWidth);
            int availableHeight = Math.max(MIN_CONTENT_HEIGHT, height);
            Rectangle2i newBounds = new Rectangle2i(x, y, availableWidth, availableHeight);
            
            // Update the contents implementation bounds
            boolean contentsBoundsUpdated = contentsImpl.updateBounds(newBounds);
            
            // Update layout if needed - update with preservePageState=true
            if (contentsBoundsUpdated) {
                // Tell JEI layout updater not to reset pagination
                contentsImpl.updateLayout(true);
                
                // If we were on a page other than page 1, restore it
                if (currentPage > 1 && getPageCount() >= currentPage) {
                    // Navigate back to the previous page
                    for (int i = 1; i < currentPage; i++) {
                        contentsImpl.nextPage();
                    }
                    ModLogger.debug("Restored pagination to page {} after bounds update", currentPage);
                }
            }
            
            // Update background area
            Rectangle2i newBackground = contentsImpl.getBackgroundArea();
            if (newBackground != null && !newBackground.isEmpty()) {
                this.backgroundArea = newBackground;
            } else {
                this.backgroundArea = newBounds;
            }
            
        } finally {
            updatingBounds = false;
        }
    }

    /**
     * Calculates a safe width for the display to prevent overlap with the game GUI
     */
    private int getSafeDisplayWidth(int requestedWidth) {
        // Get the screen dimensions
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            return requestedWidth;
        }
        
        // Use the utility method from LayoutConstants
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int maxAllowedWidth = LayoutConstants.calculateMaxWidthBeforeGui(screenWidth);
        return Math.min(requestedWidth, maxAllowedWidth);
    }
    
    /**
     * Gets the JEI contents implementation for rendering
     * @return The JEI contents implementation
     */
    public JeiContentsImpl getContentsImpl() {
        return contentsImpl;
    }
    
    /**
     * Checks if this view needs to refresh its content
     * @return true if a refresh is needed
     */
    public boolean needsRefresh() {
        return needsRefresh;
    }
    
    /**
     * Clears the refresh flag after handling
     */
    public void clearRefreshFlag() {
        needsRefresh = false;
    }

    /**
     * Checks if mouse coordinates are over this display.
     * Implementation of the HitTestable interface.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @return true if the mouse is over this component
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Basic hit test using the primary dimensions
        return MouseHitUtil.isMouseOverRect(mouseX, mouseY, x, y, width, height);
    }
    
    /**
     * Checks if mouse coordinates are over this display's extended area.
     * For drag operations, we use a more generous hit area.
     * This overrides the default implementation in HitTestable.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @return true if the mouse is over the extended hit area
     */
    @Override
    public boolean isMouseOverExtended(double mouseX, double mouseY) {
        boolean result = MouseHitUtil.isMouseOverContentView(
            mouseX, mouseY, x, y, width, height, backgroundArea);
        
        if (result) {
            ModLogger.debug("Mouse is over folder contents view (extended)");
        }
        
        return result;
    }
    
    /**
     * Gets the bookmark key at the given coordinates, if any.
     */
    public Optional<String> getBookmarkKeyAt(double mouseX, double mouseY) {
        if (activeFolder == null || !isMouseOverExtended(mouseX, mouseY)) {
            return Optional.empty();
        }
        
        return contentsImpl.getBookmarkKeyAt(mouseX, mouseY);
    }

    /**
     * Handles a click on the bookmark display
     * Only handles view-specific aspects like pagination
     * All business logic is delegated to FolderStateManager
     * 
     * @return true if the click was handled by pagination controls
     */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        // First check pagination buttons which are always part of the view logic
        if (contentsImpl.isNextButtonClicked(mouseX, mouseY)) {
            contentsImpl.nextPage();
            return true;
        }
        
        if (contentsImpl.isBackButtonClicked(mouseX, mouseY)) {
            contentsImpl.previousPage();
            return true;
        }
        
        // Return false to let FolderStateManager handle business logic for bookmark clicks
        return false;
    }
    
    /**
     * Handles an ingredient being dropped onto the display
     * @return true if the drop was handled
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        ModLogger.info("[DROP-DEBUG] UnifiedFolderContentsDisplay.handleIngredientDrop called with ingredient type: {}", 
            ingredient != null ? ingredient.getClass().getName() : "null");

        if (ingredient == null) {
            ModLogger.info("[DROP-DEBUG] Ingredient drop rejected: null ingredient");
            return false;
        }

        // Only process drops that are over this display (use extended area for easier drops)
        if (!isMouseOverExtended(mouseX, mouseY)) {
            ModLogger.info("[DROP-DEBUG] Ingredient drop not over display area: ({}, {}) not in ({}, {}, {}, {})", 
                          mouseX, mouseY, x, y, width, height);
            return false;
        }
        
        // After validation, delegate to the central method in FolderStateManager
        // which now handles all the shared logic, event firing, and refresh operations
        return eventManager.handleIngredientDropOnFolder(activeFolder, ingredient);
    }
    
    /**
     * Updates the bounds of the display based on calculated positions
     */
    public void updateBoundsFromCalculatedPositions() {
        // If positions haven't been calculated yet, use defaults
        int displayY = calculatedDisplayY >= 0 ? calculatedDisplayY : y;
        
        // Check if bounds are actually changing
        Rectangle2i newBounds = new Rectangle2i(x, displayY, width, height);
        if (lastCalculatedBounds != null && 
            lastCalculatedBounds.getX() == newBounds.getX() && 
            lastCalculatedBounds.getY() == newBounds.getY() && 
            lastCalculatedBounds.getWidth() == newBounds.getWidth() && 
            lastCalculatedBounds.getHeight() == newBounds.getHeight()) {
            return;
        }
        
        // Update the bounds
        updateBounds(newBounds.getX(), newBounds.getY(), newBounds.getWidth(), newBounds.getHeight());
        
        // Store the calculated bounds
        lastCalculatedBounds = newBounds;
    }
    
    /**
     * Sets the calculated positions for the display
     */
    public void setCalculatedPositions(int nameY, int displayY) {
        this.calculatedNameY = nameY;
        this.calculatedDisplayY = displayY;
        
        // Update bounds based on new positions
        updateBoundsFromCalculatedPositions();
    }
    
    /**
     * Gets the calculated name Y position
     */
    public int getCalculatedNameY() {
        return calculatedNameY;
    }
    
    /**
     * Gets the calculated display Y position
     */
    public int getCalculatedDisplayY() {
        return calculatedDisplayY;
    }
    
    // Getters for coordinates
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Rectangle2i getBackgroundArea() { return backgroundArea; }
    
    /**
     * Gets the current page number (1-indexed).
     * 
     * @return The current page number
     */
    public int getCurrentPageNumber() {
        if (contentsImpl == null) {
            return 1;
        }
        return contentsImpl.getCurrentPageNumber();
    }
    
    /**
     * Gets the total number of pages.
     * 
     * @return The total number of pages
     */
    public int getPageCount() {
        if (contentsImpl == null) {
            return 1;
        }
        return contentsImpl.getPageCount();
    }
    
    /**
     * Navigates to a specific page number.
     * 
     * @param pageNumber The page number to navigate to (1-indexed)
     */
    public void goToPage(int pageNumber) {
        if (contentsImpl == null) {
            return;
        }
        
        int currentPage = getCurrentPageNumber();
        int targetPage = Math.max(1, Math.min(pageNumber, getPageCount()));
        
        if (currentPage < targetPage) {
            for (int i = currentPage; i < targetPage; i++) {
                contentsImpl.nextPage();
            }
        } else if (currentPage > targetPage) {
            for (int i = currentPage; i > targetPage; i--) {
                contentsImpl.previousPage();
            }
        }
    }
}