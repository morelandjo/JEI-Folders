package com.jeifolders.gui.bookmarks;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.data.FolderDataService;
import com.jeifolders.gui.LayoutConstants;
import com.jeifolders.gui.folderButtons.UnifiedFolderManager;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.integration.TypedIngredientHelper;
import com.jeifolders.integration.impl.JeiBookmarkAdapter;
import com.jeifolders.integration.impl.JeiContentsImpl;
import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Display for folder bookmarks that handles both display management and rendering.
 */
public class UnifiedFolderContentsDisplay {
    // Constants
    private static final int MIN_CONTENT_HEIGHT = 40;
    private static final int MIN_CONTENT_WIDTH = 80;
    private static final int DEFAULT_DISPLAY_WIDTH = 200;
    private static final int DEFAULT_DISPLAY_HEIGHT = 100;

    // Core components
    private final FolderDataService folderService;
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
    private int width = DEFAULT_DISPLAY_WIDTH;
    private int height = DEFAULT_DISPLAY_HEIGHT;
    private final int[] bounds = new int[4]; // x, y, width, height
    private Rectangle2i lastCalculatedBounds = null;
    private int calculatedNameY = -1;
    private int calculatedDisplayY = -1;
    
    private Rectangle2i lastCalculatedArea = Rectangle2i.EMPTY;
    
    // Event system
    private final UnifiedFolderManager eventManager = UnifiedFolderManager.getInstance();
    private final Consumer<UnifiedFolderManager.FolderEvent> folderChangedListener;

    /**
     * Creates a new unified folder contents display
     */
    private UnifiedFolderContentsDisplay(
        FolderDataService folderService, 
        FolderBookmarkList bookmarkList,
        JeiBookmarkAdapter bookmarkAdapter, 
        JeiContentsImpl contentsImpl
    ) {
        this.folderService = folderService;
        this.ingredientService = JEIIntegrationFactory.getIngredientService();
        this.bookmarkList = bookmarkList;
        this.bookmarkAdapter = bookmarkAdapter;
        this.contentsImpl = contentsImpl;
        
        // Folder changed listener
        this.folderChangedListener = event -> {
            if (activeFolder != null && event.getFolderId() == activeFolder.getId()) {
                ModLogger.info("FolderEventManager: Active folder {} was modified, refreshing display", event.getFolderId());
                needsRefresh = true;
            }
        };
        
        // Register with event system
        eventManager.addEventListener(UnifiedFolderManager.EventType.FOLDER_CONTENTS_CHANGED, folderChangedListener);
    }

    /**
     * Creates a new instance of UnifiedFolderContentsDisplay
     * @param folderService The folder data service
     * @return An optional containing the new display if creation was successful
     */
    public static Optional<UnifiedFolderContentsDisplay> create(FolderDataService folderService) {
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
    public static Optional<UnifiedFolderContentsDisplay> createForFolder(
        FolderDataService folderService,
        FolderDataRepresentation folder
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

        try {
            // Store the current page number before refreshing
            int currentPageNumber = getCurrentPageNumber();
            
            List<BookmarkIngredient> bookmarkIngredients = TypedIngredientHelper.convertToBookmarkIngredients(
                TypedIngredientHelper.loadBookmarksFromFolder(folderService, activeFolder.getId(), true)
            );
            
            // Set the ingredients
            setIngredients(bookmarkIngredients);
            
            // Force layout update
            if (contentsImpl != null) {
                contentsImpl.updateLayout(true);
                
                // Restore the page number
                if (currentPageNumber > 1 && getPageCount() >= currentPageNumber) {
                    // Navigate to the previously active page
                    for (int i = 1; i < currentPageNumber; i++) {
                        contentsImpl.nextPage();
                    }
                    ModLogger.debug("Restored pagination to page {} after refresh", currentPageNumber);
                }
            }
            
            // Fire a display refreshed event
            eventManager.fireEvent(UnifiedFolderManager.EventBuilder.create(UnifiedFolderManager.EventType.DISPLAY_REFRESHED)
                .withFolder(activeFolder)
                .build());
            
        } catch (Exception e) {
            ModLogger.error("Error refreshing bookmarks: {}", e.getMessage(), e);
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
            
            lastCalculatedArea = newBounds;
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
     * For drag operations, we use a more generous hit area.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Basic check if mouse is over the current display bounds
        boolean overCurrentBounds = mouseX >= x && mouseX < x + width && 
                                   mouseY >= y && mouseY < y + height;
        
        // If this is already true, no need for additional checks
        if (overCurrentBounds) {
            return true;
        }
        
        // For drag operations, check against the background area if available
        if (backgroundArea != null && !backgroundArea.isEmpty()) {
            // Add extended margins to the background area for easier drag and drop
            boolean overBackground = mouseX >= (backgroundArea.getX() - LayoutConstants.DRAG_DROP_HORIZONTAL_MARGIN) && 
                   mouseX <= (backgroundArea.getX() + backgroundArea.getWidth() + LayoutConstants.DRAG_DROP_HORIZONTAL_MARGIN) &&
                   mouseY >= (backgroundArea.getY() - LayoutConstants.DRAG_DROP_VERTICAL_MARGIN) && 
                   mouseY <= (backgroundArea.getY() + backgroundArea.getHeight() + LayoutConstants.DRAG_DROP_VERTICAL_MARGIN);
                   
            if (overBackground) {
                ModLogger.debug("Mouse in extended background hit area for drag and drop");
                return true;
            }
        }
        
        // For drag and drop, be even more lenient with the main display bounds
        if (mouseX >= (x - LayoutConstants.DRAG_DROP_HORIZONTAL_MARGIN) && 
            mouseX < (x + width + LayoutConstants.DRAG_DROP_HORIZONTAL_MARGIN)) {
            boolean inVerticalRegion = mouseY >= (y - LayoutConstants.DRAG_DROP_VERTICAL_MARGIN) && 
                                      mouseY <= (y + height + LayoutConstants.DRAG_DROP_VERTICAL_MARGIN);
            if (inVerticalRegion) {
                ModLogger.debug("Mouse in extended drag-and-drop hit area");
                return true;
            }
        }
        
        return false;
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
    
    /**
     * Handles a click on the bookmark display
     * @return true if the click was handled
     */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        // Early return if not over the display
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        
        // Handle page navigation
        if (button == 0) {
            // Check if next page button is clicked
            if (isNextButtonClicked(mouseX, mouseY)) {
                nextPage();
                return true;
            }

            // Check if back button is clicked
            if (isBackButtonClicked(mouseX, mouseY)) {
                previousPage();
                return true;
            }
        }

        // Check if a bookmark was clicked
        Optional<String> clickedBookmarkKey = getBookmarkKeyAt(mouseX, mouseY);
        return clickedBookmarkKey.isPresent();
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

        // Only process drops that are over this display
        if (!isMouseOver(mouseX, mouseY)) {
            ModLogger.info("[DROP-DEBUG] Ingredient drop not over display area: ({}, {}) not in ({}, {}, {}, {})", 
                          mouseX, mouseY, x, y, width, height);
            return false;
        }
        
        ModLogger.info("[DROP-DEBUG] Processing ingredient drop - ingredient: {} ({})", 
                      ingredient, ingredient.getClass().getName());
        
        try {
            // Try to get the ingredient key
            String key = TypedIngredientHelper.getKeyForIngredient(ingredient);
            ModLogger.info("[DROP-DEBUG] Generated ingredient key: {}", key);
            
            if (key == null || key.isEmpty()) {
                ModLogger.warn("[DROP-DEBUG] Failed to generate key for dropped ingredient of type: {}", 
                              ingredient.getClass().getName());
                return false;
            }
            
            // Check if the folder already has this bookmark
            if (activeFolder.containsBookmark(key)) {
                ModLogger.info("[DROP-DEBUG] Folder already contains bookmark with key: {}", key);
                return true;
            }
            
            // Add the ingredient to the folder
            int folderId = activeFolder.getId();
            ModLogger.info("[DROP-DEBUG] Adding bookmark to folder {} with key: {}", folderId, key);
            folderService.addBookmark(folderId, key);
            
            // Log the success
            Optional<FolderDataRepresentation> folderOpt = folderService.getFolder(folderId);
            if (folderOpt.isPresent()) {
                FolderDataRepresentation folder = folderOpt.get();
                List<String> bookmarkKeys = folder.getBookmarkKeys();
                ModLogger.info("[DROP-DEBUG] Folder '{}' now has {} bookmarks, added key: {}", 
                              activeFolder.getName(), bookmarkKeys.size(), key);
            }
            
            // Fire bookmark added event with the EventBuilder pattern
            if (ingredient instanceof BookmarkIngredient) {
                ModLogger.info("[DROP-DEBUG] Ingredient is a BookmarkIngredient, firing BOOKMARK_ADDED event");
                eventManager.fireEvent(UnifiedFolderManager.EventBuilder.create(UnifiedFolderManager.EventType.BOOKMARK_ADDED)
                    .withFolder(activeFolder)
                    .withIngredient(ingredient)
                    .withBookmarkKey(key)
                    .build());
            } else {
                // For non-BookmarkIngredient, just fire a folder contents changed event
                ModLogger.info("[DROP-DEBUG] Ingredient is not a BookmarkIngredient, firing FOLDER_CONTENTS_CHANGED event");
                eventManager.fireEvent(UnifiedFolderManager.EventBuilder.create(UnifiedFolderManager.EventType.FOLDER_CONTENTS_CHANGED)
                    .withFolderId(folderId)
                    .build());
            }
            
            // Refresh the display
            ModLogger.info("[DROP-DEBUG] Refreshing bookmark display after adding ingredient");
            refreshBookmarks();
            
            // Save the changes
            folderService.saveData();
            
            ModLogger.info("[DROP-DEBUG] Successfully handled ingredient drop");
            return true;
        } catch (Exception e) {
            ModLogger.error("[DROP-DEBUG] Error processing ingredient drop: {}", e.getMessage(), e);
            e.printStackTrace();
            return false;
        }
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
     * Force a refresh of the contents and layout
     */
    public void forceRefresh() {
        refreshBookmarks();
        if (contentsImpl != null) {
            contentsImpl.updateLayout(true);
        }
    }
}