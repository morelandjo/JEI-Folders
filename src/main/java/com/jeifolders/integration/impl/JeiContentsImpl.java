package com.jeifolders.integration.impl;

import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.Rectangle2i;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JEI-specific implementation for rendering and interacting with bookmark ingredients.
 * This class contains all direct references to JEI classes and keeps them isolated
 * from the rest of the mod.
 */
public class JeiContentsImpl {
    private final JeiBookmarkAdapter bookmarkAdapter;
    private final IngredientGridWithNavigation contents;
    private final IIngredientManager ingredientManager;
    private Set<ImmutableRect2i> guiExclusionAreas = Set.of();
    private boolean updatingLayout = false;

    /**
     * Creates a new JeiContentsImpl with the specified bookmark adapter and JEI runtime.
     */
    public JeiContentsImpl(Object bookmarkAdapterObj, Object jeiRuntimeObj) {
        if (!(bookmarkAdapterObj instanceof JeiBookmarkAdapter)) {
            throw new IllegalArgumentException("Expected JeiBookmarkAdapter, got " + 
                (bookmarkAdapterObj != null ? bookmarkAdapterObj.getClass().getName() : "null"));
        }
        
        if (!(jeiRuntimeObj instanceof IJeiRuntime)) {
            throw new IllegalArgumentException("Expected IJeiRuntime, got " + 
                (jeiRuntimeObj != null ? jeiRuntimeObj.getClass().getName() : "null"));
        }
        
        this.bookmarkAdapter = (JeiBookmarkAdapter)bookmarkAdapterObj;
        IJeiRuntime jeiRuntime = (IJeiRuntime)jeiRuntimeObj;
        this.ingredientManager = jeiRuntime.getIngredientManager();

        // Get necessary JEI components through Internal utilities
        IClientConfig clientConfig = Internal.getJeiClientConfigs().getClientConfig();
        IClientToggleState toggleState = Internal.getClientToggleState();
        IIngredientGridConfig bookmarkListConfig = Internal.getJeiClientConfigs().getBookmarkListConfig();
        IIngredientFilterConfig ingredientFilterConfig = Internal.getJeiClientConfigs().getIngredientFilterConfig();
        IConnectionToServer serverConnection = Internal.getServerConnection();
        IInternalKeyMappings keyMappings = Internal.getKeyMappings();
        Textures textures = Internal.getTextures();
        IColorHelper colorHelper = jeiRuntime.getJeiHelpers().getColorHelper();
        IScreenHelper screenHelper = jeiRuntime.getScreenHelper();
        
        try {
            // Create the ingredient grid for displaying bookmarks
            IngredientGrid ingredientGrid = new IngredientGrid(
                ingredientManager,
                bookmarkListConfig,
                ingredientFilterConfig,
                clientConfig,
                toggleState,
                serverConnection,
                keyMappings,
                colorHelper,
                false  // searchable parameter: false for bookmark display
            );

            // JeiBookmarkAdapter implements IIngredientGridSource, so we can use it directly
            // Create the grid with navigation
            this.contents = new IngredientGridWithNavigation(
                "FolderBookmarkOverlay",  // debug name
                this.bookmarkAdapter,     // ingredientSource (implement IIngredientGridSource)
                ingredientGrid,
                toggleState,
                clientConfig,
                serverConnection,
                bookmarkListConfig,
                textures.getBookmarkListBackground(),
                textures.getBookmarkListSlotBackground(),
                screenHelper,
                ingredientManager
            );

            ModLogger.info("Successfully created JEI ingredient grid for folder display");
        } catch (Exception e) {
            ModLogger.error("Failed to create JEI ingredient grid: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize JEI contents implementation", e);
        }
    }

    /**
     * Sets the ingredients to be displayed.
     */
    public void setIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        ModLogger.info("Setting {} ingredients in JEI contents implementation", bookmarkIngredients.size());
        
        try {
            // Clear existing bookmarks first
            bookmarkAdapter.getFolderBookmarkList().clearBookmarks();
            
            // Convert the BookmarkIngredient wrappers to JEI ingredients
            List<ITypedIngredient<?>> jeiIngredients = unwrapIngredients(bookmarkIngredients);
            
            // Add the new ingredients one by one
            for (ITypedIngredient<?> ingredient : jeiIngredients) {
                bookmarkAdapter.addBookmark(ingredient);
            }
            
            // Make sure listeners are notified of the change
            bookmarkAdapter.notifyListeners();
            
            // Force layout update
            updateLayout(true);
        } catch (Exception e) {
            ModLogger.error("Error setting ingredients in JEI contents: {}", e.getMessage(), e);
        }
    }

    /**
     * Unwraps BookmarkIngredient objects to JEI ITypedIngredient objects.
     */
    private List<ITypedIngredient<?>> unwrapIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        if (bookmarkIngredients == null || bookmarkIngredients.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ITypedIngredient<?>> result = new ArrayList<>(bookmarkIngredients.size());
        for (BookmarkIngredient ingredient : bookmarkIngredients) {
            if (ingredient != null && ingredient.getTypedIngredient() != null) {
                result.add(ingredient.getTypedIngredient());
            }
        }
        
        return result;
    }

    /**
     * Draws the ingredients in the grid.
     */
    public void draw(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            this.contents.draw(minecraft, graphics, mouseX, mouseY, partialTick);
        } catch (Exception e) {
            ModLogger.error("Error drawing JEI contents: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the bounds of the grid.
     * Changed to accept our Rectangle2i instead of ImmutableRect2i
     */
    public boolean updateBounds(Rectangle2i area) {
        try {
            // Convert our Rectangle2i to JEI's ImmutableRect2i
            ImmutableRect2i jeiRect = RectangleHelper.toJei(area);
            this.contents.updateBounds(jeiRect, this.guiExclusionAreas, null);
            return true;
        } catch (Exception e) {
            ModLogger.error("Error updating bounds in JEI contents: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Updates the layout of the grid.
     */
    public void updateLayout(boolean forceUpdate) {
        if (updatingLayout) {
            return;
        }
        
        updatingLayout = true;
        try {
            this.contents.updateLayout(forceUpdate);
        } catch (Exception e) {
            ModLogger.error("Error updating layout in JEI contents: {}", e.getMessage(), e);
        } finally {
            updatingLayout = false;
        }
    }

    /**
     * Gets the background area of the grid.
     * Changed to return our Rectangle2i instead of ImmutableRect2i
     */
    public Rectangle2i getBackgroundArea() {
        // Convert JEI's ImmutableRect2i to our Rectangle2i
        return RectangleHelper.fromJei(this.contents.getBackgroundArea());
    }

    /**
     * Gets the slot background area of the grid.
     * Changed to return our Rectangle2i instead of ImmutableRect2i
     */
    public Rectangle2i getSlotBackgroundArea() {
        // Convert JEI's ImmutableRect2i to our Rectangle2i
        return RectangleHelper.fromJei(this.contents.getSlotBackgroundArea());
    }

    /**
     * Draws tooltips for the grid.
     */
    public void drawTooltips(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY) {
        try {
            this.contents.drawTooltips(minecraft, graphics, mouseX, mouseY);
        } catch (Exception e) {
            ModLogger.error("Error drawing tooltips in JEI contents: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the bookmark key at the given mouse coordinates.
     */
    public Optional<String> getBookmarkKeyAt(double mouseX, double mouseY) {
        try {
            // Get the ingredient service instance
            IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
            
            // Get the ingredient under the mouse cursor
            return this.contents.getIngredientUnderMouse(mouseX, mouseY)
                .findFirst()
                .flatMap(clickable -> Optional.ofNullable(clickable.getTypedIngredient()))
                .map(ingredient -> ingredientService.getKeyForIngredient(ingredient));
                
        } catch (Exception e) {
            ModLogger.error("Error getting bookmark key at position: {}", e.getMessage(), e);
        }
        
        return Optional.empty();
    }

    /**
     * Navigates to the next page.
     */
    public void nextPage() {
        try {
            this.contents.getPageDelegate().nextPage();
        } catch (Exception e) {
            ModLogger.error("Error navigating to next page: {}", e.getMessage(), e);
        }
    }

    /**
     * Navigates to the previous page.
     */
    public void previousPage() {
        try {
            this.contents.getPageDelegate().previousPage();
        } catch (Exception e) {
            ModLogger.error("Error navigating to previous page: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the current page number (1-indexed).
     */
    public int getCurrentPageNumber() {
        try {
            // Add 1 because JEI uses 0-indexed pages
            return this.contents.getPageDelegate().getPageNumber() + 1;
        } catch (Exception e) {
            ModLogger.error("Error getting current page number: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Gets the total number of pages.
     */
    public int getPageCount() {
        try {
            return this.contents.getPageDelegate().getPageCount();
        } catch (Exception e) {
            ModLogger.error("Error getting page count: {}", e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Checks if the next button is clicked.
     */
    public boolean isNextButtonClicked(double mouseX, double mouseY) {
        try {
            ImmutableRect2i buttonArea = this.contents.getNextPageButtonArea();
            return !buttonArea.isEmpty() && buttonArea.contains(mouseX, mouseY);
        } catch (Exception e) {
            ModLogger.error("Error checking if next button is clicked: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if the back button is clicked.
     */
    public boolean isBackButtonClicked(double mouseX, double mouseY) {
        try {
            ImmutableRect2i buttonArea = this.contents.getBackButtonArea();
            return !buttonArea.isEmpty() && buttonArea.contains(mouseX, mouseY);
        } catch (Exception e) {
            ModLogger.error("Error checking if back button is clicked: {}", e.getMessage(), e);
            return false;
        }
    }
}