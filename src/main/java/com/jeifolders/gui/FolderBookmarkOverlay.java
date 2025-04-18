package com.jeifolders.gui;

import com.jeifolders.JEIFolders;
import com.jeifolders.data.Folder;
import com.jeifolders.integration.JEIBookmarkManager;
import com.jeifolders.integration.JEIIngredientManager;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A simplified bookmark overlay for folders that integrates with JEI
 * This class handles displaying ingredients from a folder using JEI's rendering infrastructure
 */
public class FolderBookmarkOverlay {
    
    private static final int MIN_CONTENT_HEIGHT = 40;

    private long lastUpdateTimestamp = 0;
    private int updateCounter = 0;

    private final FolderBookmarkList bookmarkList;
    private final IngredientGridWithNavigation contents;
    private final IIngredientManager ingredientManager;

    private Folder currentFolder;
    private ImmutableRect2i backgroundArea = ImmutableRect2i.EMPTY;
    private ImmutableRect2i slotBackgroundArea = ImmutableRect2i.EMPTY;
    private Set<ImmutableRect2i> guiExclusionAreas = Set.of();

    private ImmutableRect2i lastCalculatedArea = ImmutableRect2i.EMPTY;
    private boolean updatingBounds = false;
    private List<ITypedIngredient<?>> ingredients = new ArrayList<>();

    public FolderBookmarkOverlay(
        FolderBookmarkList bookmarkList,
        IIngredientManager ingredientManager,
        IScreenHelper screenHelper,
        IInternalKeyMappings keyMappings,
        IClientConfig clientConfig,
        IClientToggleState toggleState,
        IIngredientGridConfig gridConfig,
        Textures textures,
        IIngredientFilterConfig ingredientFilterConfig,
        IConnectionToServer serverConnection,
        IColorHelper colorHelper,
        FolderExclusionHandler exclusionHandler
    ) {
        this.bookmarkList = bookmarkList;
        this.ingredientManager = ingredientManager;

        try {
            // Create the ingredient grid for displaying bookmarks
            IngredientGrid ingredientGrid = new IngredientGrid(
                ingredientManager,
                gridConfig,
                ingredientFilterConfig,
                clientConfig,
                toggleState,
                serverConnection,
                keyMappings,
                colorHelper,
                false  // searchable parameter: false for bookmark display
            );

            // Create a JEI adapter for our bookmarkList
            JEIBookmarkManager.JeiBookmarkAdapter bookmarkAdapter =
                new JEIBookmarkManager.JeiBookmarkAdapter(bookmarkList);

            // Create the grid with navigation using the adapter
            this.contents = new IngredientGridWithNavigation(
                "FolderBookmarkOverlay",
                bookmarkAdapter,
                ingredientGrid,
                toggleState,
                clientConfig,
                serverConnection,
                gridConfig,
                textures.getBookmarkListBackground(),
                textures.getBookmarkListSlotBackground(),
                screenHelper,
                ingredientManager
            );

            ModLogger.info("Successfully created ingredient grid for folder display");
        } catch (Exception e) {
            ModLogger.error("Failed to create ingredient grid: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize folder bookmark display", e);
        }
    }

    /**
     * Factory method to create a FolderBookmarkOverlay instance from a JEI runtime
     * This simplifies creation and ensures consistent configuration
     */
    public static Optional<FolderBookmarkOverlay> create(IJeiRuntime jeiRuntime, Folder folder) {
        if (jeiRuntime == null) {
            return Optional.empty();
        }
        
        try {
            IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
            IClientToggleState toggleState = Internal.getClientToggleState();
            IIngredientGridConfig bookmarkListConfig = Internal.getJeiClientConfigs().getBookmarkListConfig();
            IIngredientFilterConfig ingredientFilterConfig = Internal.getJeiClientConfigs().getIngredientFilterConfig();
            IConnectionToServer serverConnection = Internal.getServerConnection();
            IInternalKeyMappings keyMappings = Internal.getKeyMappings();
            Textures textures = Internal.getTextures();
            IColorHelper colorHelper = jeiRuntime.getJeiHelpers().getColorHelper();
            FolderExclusionHandler exclusionHandler = new FolderExclusionHandler();
            
            // Create a bookmark list for this folder
            FolderBookmarkList bookmarkList = new FolderBookmarkList();
            bookmarkList.setFolder(folder);
            
            // Create the overlay
            FolderBookmarkOverlay overlay = new FolderBookmarkOverlay(
                bookmarkList,
                jeiRuntime.getIngredientManager(),
                jeiRuntime.getScreenHelper(),
                keyMappings,
                clientConfig,
                toggleState,
                bookmarkListConfig,
                textures,
                ingredientFilterConfig,
                serverConnection,
                colorHelper,
                exclusionHandler
            );
            
            overlay.setFolder(folder);
            return Optional.of(overlay);
        } catch (Exception e) {
            ModLogger.error("Failed to create folder bookmark overlay", e);
            return Optional.empty();
        }
    }

    public void setFolder(Folder folder) {
        this.currentFolder = folder;
        this.bookmarkList.setFolder(folder);
    }

    /**
     * Sets the ingredients to be displayed in the overlay.
     */
    public void setIngredients(List<ITypedIngredient<?>> ingredients) {
        // Log the ingredient count being set
        ModLogger.info("Setting ingredients in overlay: {} items for folder: {}", 
            ingredients.size(), 
            (currentFolder != null ? currentFolder.getName() + " (ID: " + currentFolder.getId() + ")" : "null"));
        
        this.ingredients = new ArrayList<>(ingredients); // Create a defensive copy

        try {
            // Clear existing bookmarks and add new ones
            bookmarkList.clearBookmarks();

            if (!ingredients.isEmpty()) {
                ingredients.forEach(bookmarkList::addBookmark);
                ModLogger.debug("Added {} ingredients to bookmark list", ingredients.size());
            }

            // Update the layout but prevent frequent calls
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTimestamp > 250) {
                // Force a layout update when ingredients change
                contents.updateLayout(true);
                lastUpdateTimestamp = currentTime;
                ModLogger.debug("Updated overlay layout after setting ingredients");
            } else {
                ModLogger.debug("Skipped updating layout - too frequent ({}ms since last update)", 
                                      currentTime - lastUpdateTimestamp);
            }
        } catch (Exception e) {
            ModLogger.error("Error setting ingredients: {}", e.getMessage(), e);
        }
    }

    /**
     * Draws the ingredients in the overlay.
     */
    public void draw(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (ingredients.isEmpty()) {
            // Log if we try to draw with no ingredients
            ModLogger.debug("Skipping overlay draw - no ingredients to display");
            return;
        }

        try {
            // Check if bookmark content appears valid before drawing
            if (backgroundArea.isEmpty() || backgroundArea.getWidth() <= 0 || backgroundArea.getHeight() <= 0) {
                ModLogger.warn("Invalid background area for overlay: {}, cannot render", backgroundArea);
                return;
            }
            
            // Draw the contents
            this.contents.draw(minecraft, graphics, mouseX, mouseY, partialTick);
        } catch (Exception e) {
            ModLogger.error("Error rendering bookmark overlay: {}", e.getMessage(), e);
        }
    }

    public void updateBounds(int x, int y, int width, int height) {
        // Keep detailed tracking of update calls to debug the issue
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String caller = stackTrace.length > 3 ? 
                      stackTrace[2].getClassName() + "." + stackTrace[2].getMethodName() + ":" + stackTrace[2].getLineNumber() : "unknown";
        
        // Log information about the update
        ModLogger.info("Updating overlay bounds: x={}, y={}, width={}, height={}, ingredients={}, caller={}", 
            x, y, width, height, ingredients.size(), caller);

        // Avoid recursive calls
        if (updatingBounds) {
            ModLogger.debug("Recursive updateBounds call prevented");
            return;
        }
        
        // Keep rate limiting but reduce log verbosity
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTimestamp < 250) { // Less than 250ms since last update
            updateCounter++;
            if (updateCounter > 4) {
                return; // Skip this update, but don't log a warning
            }
        } else {
            updateCounter = 0;
        }
        lastUpdateTimestamp = currentTime;
        
        updatingBounds = true;

        try {
            // Use consistent x position
            x = 0;

            // Use the exact provided width from the exclusion zone calculation
            // without imposing an arbitrary limit
            int availableWidth = Math.max(80, width);
            int availableHeight = Math.max(MIN_CONTENT_HEIGHT, height);

            // Create the expected area for optimization checks
            ImmutableRect2i expectedArea = new ImmutableRect2i(x, y, availableWidth, availableHeight);

            // Enhanced check with tolerance to avoid updates for minor changes
            boolean dimensionsEqual = Math.abs(expectedArea.getWidth() - lastCalculatedArea.getWidth()) <= 2 &&
                                    Math.abs(expectedArea.getHeight() - lastCalculatedArea.getHeight()) <= 2;
            boolean positionEqual = expectedArea.getX() == lastCalculatedArea.getX() && 
                                  expectedArea.getY() == lastCalculatedArea.getY();
            
            // Log area comparison for debugging
            ModLogger.debug("Area comparison - Expected: {}, Last: {}, Equal dimensions: {}, Equal position: {}", 
                expectedArea, lastCalculatedArea, dimensionsEqual, positionEqual);
                
            if (dimensionsEqual && positionEqual && !backgroundArea.isEmpty()) {
                ModLogger.debug("Skipping bounds update - no significant change");
                updatingBounds = false;
                return;
            }
            
            // Update tracking fields
            lastCalculatedArea = expectedArea;

            // Update the bounds with our calculated values
            boolean contentsBoundsUpdated = false;
            try {
                ModLogger.debug("Updating contents bounds with area: {}", expectedArea);
                this.contents.updateBounds(expectedArea, this.guiExclusionAreas, null);
                contentsBoundsUpdated = true;
            } catch (Exception e) {
                ModLogger.error("Error in contents.updateBounds: {}", e.getMessage(), e);
            }
            
            // Only update layout if dimensions actually changed AND bounds update succeeded
            if (!dimensionsEqual && contentsBoundsUpdated) {
                try {
                    ModLogger.debug("Dimensions changed, updating layout");
                    // Critical: block any recursive calls from this method
                    boolean wasUpdating = updatingBounds;
                    this.contents.updateLayout(true); // Force layout update
                    updatingBounds = wasUpdating; // Restore in case updateLayout changes it
                } catch (Exception e) {
                    ModLogger.error("Error updating layout: {}", e.getMessage(), e);
                }
            }

            // Update tracked areas
            if (!this.contents.getSlotBackgroundArea().isEmpty()) {
                this.slotBackgroundArea = this.contents.getSlotBackgroundArea();
            }

            // Handle potential empty areas
            if (!this.contents.getBackgroundArea().isEmpty()) {
                this.backgroundArea = this.contents.getBackgroundArea();
            } else {
                this.backgroundArea = expectedArea;
                ModLogger.warn("Content's background area was empty, using expected area instead");
            }

            ModLogger.info("Bookmark overlay updated to: x={}, y={}, width={}, height={}", 
                backgroundArea.getX(), backgroundArea.getY(),
                backgroundArea.getWidth(), backgroundArea.getHeight());
        } finally {
            updatingBounds = false;
        }
    }
    
    /**
     * Re-applies the current ingredients to refresh the display
     * Call this method when the overlay disappears but should be visible
     */
    public void refreshContents() {
        ModLogger.info("Refreshing overlay contents, current ingredients: {}", ingredients.size());
        if (!ingredients.isEmpty()) {
            // Make a copy of ingredients to refresh the display
            List<ITypedIngredient<?>> currentIngredients = new ArrayList<>(ingredients);
            setIngredients(currentIngredients);
            
            // Force a layout update
            try {
                contents.updateLayout(true);
                ModLogger.debug("Forced layout update during refresh");
            } catch (Exception e) {
                ModLogger.error("Error during refresh layout update: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Returns true if the overlay has content to display and is ready to be drawn
     */
    public boolean isReadyToDraw() {
        boolean hasIngredients = !ingredients.isEmpty();
        boolean hasValidArea = !backgroundArea.isEmpty() && backgroundArea.getWidth() > 0 && backgroundArea.getHeight() > 0;
        
        if (!hasIngredients || !hasValidArea) {
            ModLogger.debug("Overlay not ready to draw - hasIngredients: {}, hasValidArea: {}", 
                hasIngredients, hasValidArea);
        }
        
        return hasIngredients && hasValidArea;
    }

    public void drawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        this.contents.drawTooltips(minecraft, guiGraphics, mouseX, mouseY);
    }

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

        return this.contents.getIngredientUnderMouse(mouseX, mouseY)
            .map(clickable -> clickable.getTypedIngredient())
            .findFirst()
            .map(JEIIngredientManager::getKeyForIngredient);
    }
    
    /**
     * Gets the current ingredients displayed in the overlay.
     * @return The list of typed ingredients currently displayed
     */
    public List<ITypedIngredient<?>> getIngredients() {
        return new ArrayList<>(this.ingredients);
    }

    /**
     * Returns the next page button area for checking clicks
     */
    public ImmutableRect2i getNextPageButtonArea() {
        // If using JEI's IngredientGridWithNavigation
        if (contents instanceof mezz.jei.gui.overlay.IngredientGridWithNavigation) {
            return ((mezz.jei.gui.overlay.IngredientGridWithNavigation)contents).getNextPageButtonArea();
        }
        return ImmutableRect2i.EMPTY;
    }

    /**
     * Returns the back button area for checking clicks
     */
    public ImmutableRect2i getBackButtonArea() {
        return contents.getBackButtonArea();
    }

    /**
     * Navigate to the next page
     */
    public void nextPage() {
        // Use the page delegate to navigate pages
        contents.getPageDelegate().nextPage();
    }

    /**
     * Navigate to the previous page
     */
    public void previousPage() {
        // Use the page delegate to navigate pages
        contents.getPageDelegate().previousPage();
    }

    /**
     * Get the current page number (1-indexed for display)
     */
    public int getCurrentPageNumber() {
        // Get page number from the page delegate
        return contents.getPageDelegate().getPageNumber() + 1; // +1 because JEI uses 0-indexed pages
    }

    /**
     * Get the total number of pages
     */
    public int getPageCount() {
        // Get page count from the page delegate
        return contents.getPageDelegate().getPageCount();
    }

    /**
     * Checks if a point is over the next button
     */
    public boolean isNextButtonClicked(double mouseX, double mouseY) {
        ImmutableRect2i nextButtonArea = contents.getNextPageButtonArea();
        return !nextButtonArea.isEmpty() && nextButtonArea.contains(mouseX, mouseY);
    }

    /**
     * Checks if a point is over the back button
     */
    public boolean isBackButtonClicked(double mouseX, double mouseY) {
        ImmutableRect2i backButtonArea = contents.getBackButtonArea();
        return !backButtonArea.isEmpty() && backButtonArea.contains(mouseX, mouseY);
    }
}
