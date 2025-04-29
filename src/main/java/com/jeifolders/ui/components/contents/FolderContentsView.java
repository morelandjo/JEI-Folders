package com.jeifolders.ui.components.contents;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.integration.impl.JeiBookmarkAdapter;
import com.jeifolders.integration.impl.JeiContentsImpl;
import com.jeifolders.ui.display.BookmarkDisplayManager;
import com.jeifolders.ui.events.FolderEventListener;
import com.jeifolders.ui.events.FolderEventType;
import com.jeifolders.events.FolderEventDispatcher;
import com.jeifolders.ui.interaction.FolderInteractionHandler;
import com.jeifolders.ui.util.HitTestable;
import com.jeifolders.ui.util.LayoutConstants;
import com.jeifolders.ui.util.MouseHitUtil;
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
    
    // Component architecture
    private final FolderEventDispatcher eventDispatcher;
    private final BookmarkDisplayManager displayManager;
    private final FolderInteractionHandler interactionHandler;
    
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
    private final FolderEventListener folderChangedListener;

    /**
     * Creates a new unified folder contents display
     */
    private FolderContentsView(
        FolderStorageService folderService, 
        FolderBookmarkList bookmarkList,
        JeiBookmarkAdapter bookmarkAdapter, 
        JeiContentsImpl contentsImpl,
        FolderEventDispatcher eventDispatcher,
        BookmarkDisplayManager displayManager,
        FolderInteractionHandler interactionHandler
    ) {
        this.folderService = folderService;
        this.bookmarkList = bookmarkList;
        this.contentsImpl = contentsImpl;
        this.eventDispatcher = eventDispatcher;
        this.displayManager = displayManager;
        this.interactionHandler = interactionHandler;
        
        // Folder changed listener
        this.folderChangedListener = event -> {
            if (activeFolder != null && event.getFolderId() == activeFolder.getId()) {
                ModLogger.debug("FolderEventManager: Active folder {} was modified, refreshing display", event.getFolderId());
                needsRefresh = true;
            }
        };
        
        // Register with event system
        eventDispatcher.addEventListener(FolderEventType.FOLDER_CONTENTS_CHANGED, folderChangedListener);
    }

    /**
     * Creates a new instance of UnifiedFolderContentsDisplay
     * @param folderService The folder data service
     * @return An optional containing the new display if creation was successful
     */
    public static Optional<FolderContentsView> create(
        FolderStorageService folderService,
        FolderEventDispatcher eventDispatcher,
        BookmarkDisplayManager displayManager,
        FolderInteractionHandler interactionHandler
    ) {
        try {
            // Get the JEI service and runtime directly
            var jeiService = JEIIntegrationFactory.getJEIService();
            var jeiRuntimeOpt = jeiService.getJeiRuntime();
            
            if (jeiRuntimeOpt.isEmpty()) {
                ModLogger.warn("Cannot create display - JEI runtime not available");
                return Optional.empty();
            }
            
            // Create a bookmark list
            var bookmarkList = new FolderBookmarkList(eventDispatcher);
            var bookmarkAdapter = new JeiBookmarkAdapter(bookmarkList);
            var contentsImpl = new JeiContentsImpl(bookmarkAdapter, jeiRuntimeOpt.get());
            
            // Create the display with the JEI components
            var display = new FolderContentsView(
                folderService,
                bookmarkList,
                bookmarkAdapter,
                contentsImpl,
                eventDispatcher,
                displayManager,
                interactionHandler
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
        FolderEventDispatcher eventDispatcher,
        BookmarkDisplayManager displayManager,
        FolderInteractionHandler interactionHandler,
        Folder folder
    ) {
        var displayOpt = create(folderService, eventDispatcher, displayManager, interactionHandler);
        
        displayOpt.ifPresent(display -> {
            display.setActiveFolder(folder);
        });
        
        return displayOpt;
    }

    /**
     * Sets the active folder and ensures bookmarks are properly loaded
     */
    public void setActiveFolder(Folder folder) {
        // Check if the folder is the same
        boolean isSameFolder = (this.activeFolder == folder);
        
        if (isSameFolder) {
            ModLogger.debug("setActiveFolder called with same folder - will still update layout");
        }
        
        this.activeFolder = folder;
        
        if (folder != null) {
            ModLogger.debug("Setting active folder to: {} (ID: {})", folder.getName(), folder.getId());
            this.bookmarkList.setFolder(folder);
            refreshBookmarks();
            
            // Even if it's the same folder, force layout update
            if (isSameFolder) {
                // Force update of the layout positions
                updateBoundsFromCalculatedPositions();
            }
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
     * This now uses displayManager directly instead of FolderStateManager.
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
            // Use displayManager directly with proper method name
            displayManager.refreshFolderBookmarks(activeFolder, true);
        } catch (Exception e) {
            ModLogger.error("Error refreshing bookmarks: {}", e.getMessage(), e);
        } finally {
            refreshingBookmarks = false;
        }
    }
    
    /**
     * Force a refresh of the contents and layout
     */
    public void forceRefresh() {
        // Use displayManager directly
        if (activeFolder != null) {
            displayManager.refreshFolderBookmarks(activeFolder, true);
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
     * Business logic is delegated to the interaction handler
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
        
        // Return false to let the interaction handler handle business logic for bookmark clicks
        return false;
    }
    
    /**
     * Handles an ingredient being dropped onto the display
     * @return true if the drop was handled
     */
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        ModLogger.debug("[DROP-DEBUG] UnifiedFolderContentsDisplay.handleIngredientDrop called with ingredient type: {}", 
            ingredient != null ? ingredient.getClass().getName() : "null");

        if (ingredient == null) {
            ModLogger.debug("[DROP-DEBUG] Ingredient drop rejected: null ingredient");
            return false;
        }

        // Only process drops that are over this display (use extended area for easier drops)
        if (!isMouseOverExtended(mouseX, mouseY)) {
            ModLogger.debug("[DROP-DEBUG] Ingredient drop not over display area: ({}, {}) not in ({}, {}, {}, {})", 
                          mouseX, mouseY, x, y, width, height);
            return false;
        }
        
        // Use the interaction handler directly
        return interactionHandler.handleIngredientDropOnFolder(activeFolder, ingredient);
    }
    
    /**
     * Updates the bounds of the display based on calculated positions
     */
    public void updateBoundsFromCalculatedPositions() {
        ModLogger.debug("[POSITION-DEBUG-FORCE] FolderContentsView.updateBoundsFromCalculatedPositions called");
        
        // If we're already updating bounds, prevent recursion
        if (updatingBounds) {
            ModLogger.debug("[POSITION-DEBUG-FORCE] Preventing recursive bounds update");
            return;
        }
        
        try {
            updatingBounds = true;
            
            // Use the calculated displayY position only if it has been properly set
            // If not properly set, use a better default position (50px from top)
            int displayY = (calculatedDisplayY >= 0) ? calculatedDisplayY : 50;
            ModLogger.debug("[POSITION-DEBUG-FORCE] Using displayY={} (calculatedDisplayY={})", 
                          displayY, calculatedDisplayY);
            
            // Calculate safe width to prevent GUI overlap
            int safeWidth = getSafeDisplayWidth(this.width);
            
            Rectangle2i newBounds = new Rectangle2i(x, displayY, safeWidth, height);
            
            // Check if bounds are actually changing to avoid unnecessary updates
            if (lastCalculatedBounds != null && lastCalculatedBounds.equals(newBounds)) {
                ModLogger.debug("[POSITION-DEBUG-FORCE] Bounds unchanged, skipping update");
                return;
            }
            
            ModLogger.debug("[POSITION-DEBUG-FORCE] New bounds: x={}, y={}, w={}, h={}", 
                          newBounds.getX(), newBounds.getY(), newBounds.getWidth(), newBounds.getHeight());
            
            // Update the bounds
            this.x = newBounds.getX();
            this.y = newBounds.getY();
            this.width = newBounds.getWidth();
            this.height = newBounds.getHeight();
            this.bounds[0] = x;
            this.bounds[1] = y;
            this.bounds[2] = width;
            this.bounds[3] = height;
            this.backgroundArea = newBounds;
            this.lastCalculatedBounds = newBounds;
            
            // Tell the contents implementation about the new bounds
            if (contentsImpl != null) {
                contentsImpl.updateBounds(newBounds);
                ModLogger.debug("[POSITION-DEBUG-FORCE] Updated contentsImpl with new bounds");
            }
        } finally {
            updatingBounds = false;
        }
    }
    
    /**
     * Sets the calculated position values for this view
     * @param nameY Y position for the folder name
     * @param displayY Y position for the bookmark display
     */
    public void setCalculatedPositions(int nameY, int displayY) {
        ModLogger.debug("[POSITION-DEBUG-FORCE] FolderContentsView.setCalculatedPositions received positions: nameY={}, displayY={}", nameY, displayY);
        this.calculatedNameY = nameY;
        this.calculatedDisplayY = displayY;
        
        // Force an update of the bounds when positions change
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