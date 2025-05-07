package com.jeifolders.integration.impl;

import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.IngredientService;
import com.jeifolders.ui.util.MouseHitUtil;
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
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JEI-specific implementation for rendering and interacting with bookmark ingredients.
 * This class contains all direct references to JEI classes and keeps them isolated
 * from the rest of the mod.
 * 
 * Uses Minecraft's Rect2i class for rectangle handling.
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

        } catch (Exception e) {
            ModLogger.error("Failed to create JEI ingredient grid: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize JEI contents implementation", e);
        }
    }

    /**
     * Sets the ingredients to be displayed.
     */
    public void setIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        ModLogger.info("DIRECT-TRACKING: JeiContentsImpl.setIngredients called with {} ingredients", 
            bookmarkIngredients != null ? bookmarkIngredients.size() : 0);
        
        try {
            // If we're being asked to clear but we have ingredients, skip it
            if ((bookmarkIngredients == null || bookmarkIngredients.isEmpty()) && 
                !bookmarkAdapter.getFolderBookmarkList().isEmpty()) {
                ModLogger.info("DIRECT-TRACKING: Skipping clear operation when we already have ingredients");
                // Just force layout update without clearing
                updateLayout(true);
                return;
            }
            
            // Convert the BookmarkIngredient wrappers to JEI ingredients
            List<ITypedIngredient<?>> jeiIngredients = unwrapIngredients(bookmarkIngredients);
            
            if (!jeiIngredients.isEmpty()) {
                ModLogger.info("DIRECT-TRACKING: Setting {} JEI ingredients", jeiIngredients.size());
                
                // Clear existing bookmarks first
                bookmarkAdapter.getFolderBookmarkList().clear();
                
                // Add the new ingredients one by one
                for (ITypedIngredient<?> ingredient : jeiIngredients) {
                    bookmarkAdapter.addBookmark(ingredient);
                }
                
                // Make sure listeners are notified of the change
                bookmarkAdapter.notifyListeners();
                
                // Force layout update
                updateLayout(true);
                ModLogger.info("DIRECT-TRACKING: Successfully set JEI ingredients and updated layout");
            } else if (bookmarkIngredients != null && !bookmarkIngredients.isEmpty()) {
                ModLogger.warn("DIRECT-TRACKING: Received non-empty bookmarkIngredients but got empty JEI ingredients after unwrapping");
            }
        } catch (Exception e) {
            ModLogger.error("Error setting ingredients in JEI contents: {}", e.getMessage(), e);
        }
    }

    /**
     * Unwraps BookmarkIngredient objects to JEI ITypedIngredient objects.
     */
    private List<ITypedIngredient<?>> unwrapIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        ModLogger.info("DIRECT-TRACKING: Unwrapping {} BookmarkIngredient objects", 
            bookmarkIngredients != null ? bookmarkIngredients.size() : 0);
            
        if (bookmarkIngredients == null || bookmarkIngredients.isEmpty()) {
            ModLogger.info("DIRECT-TRACKING: No bookmarkIngredients to unwrap");
            return Collections.emptyList();
        }
        
        List<ITypedIngredient<?>> result = new ArrayList<>(bookmarkIngredients.size());
        int nullCount = 0;
        int nullWrappedCount = 0;
        
        for (int i = 0; i < bookmarkIngredients.size(); i++) {
            BookmarkIngredient ingredient = bookmarkIngredients.get(i);
            if (ingredient == null) {
                nullCount++;
                ModLogger.warn("DIRECT-TRACKING: Skipping null BookmarkIngredient at index {}", i);
                continue;
            }
            
            // Log details about each ingredient we're trying to unwrap
            ModLogger.info("DIRECT-TRACKING: BookmarkIngredient[{}] class={}, wrappedType={}", 
                i, 
                ingredient.getClass().getSimpleName(),
                ingredient.getWrappedIngredient() != null ? ingredient.getWrappedIngredient().getClass().getSimpleName() : "null");
            
            Object typedIngredient = ingredient.getTypedIngredient();
            if (typedIngredient != null) {
                ModLogger.info("DIRECT-TRACKING: Found valid ITypedIngredient in BookmarkIngredient[{}]", i);
                
                if (typedIngredient instanceof ITypedIngredient) {
                    result.add((ITypedIngredient<?>) typedIngredient);
                } else {
                    ModLogger.warn("DIRECT-TRACKING: typedIngredient is not an instance of ITypedIngredient: {}", 
                        typedIngredient.getClass().getName());
                }
            } else {
                nullWrappedCount++;
                ModLogger.warn("DIRECT-TRACKING: BookmarkIngredient[{}] has null typedIngredient", i);
                
                // Try to examine what's in the wrapped ingredient
                Object wrapped = ingredient.getWrappedIngredient();
                if (wrapped != null) {
                    ModLogger.info("DIRECT-TRACKING: Examining wrapped ingredient: class={}", 
                        wrapped.getClass().getName());
                        
                    // If the wrapped ingredient is actually an ITypedIngredient itself, use that directly
                    if (wrapped instanceof ITypedIngredient) {
                        ModLogger.info("DIRECT-TRACKING: Wrapped ingredient is ITypedIngredient, adding it directly");
                        result.add((ITypedIngredient<?>) wrapped);
                    }
                }
            }
        }
        
        ModLogger.info("DIRECT-TRACKING: Unwrapped {} out of {} ingredients (skipped {} null, {} with null typedIngredient)",
            result.size(), bookmarkIngredients.size(), nullCount, nullWrappedCount);
            
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
     * Updates the bounds of the grid using Minecraft's Rect2i.
     * 
     * @param area The new bounds as a Rect2i
     * @return True if the update was successful
     */
    public boolean updateBounds(Rect2i area) {
        try {
            // Convert Minecraft's Rect2i to JEI's ImmutableRect2i
            ImmutableRect2i jeiRect = RectangleHelper.minecraftToJei(area);
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
     * Gets the background area of the grid as Minecraft's Rect2i.
     * 
     * @return The background area as a Rect2i
     */
    public Rect2i getBackgroundArea() {
        // Convert JEI's ImmutableRect2i to Minecraft's Rect2i
        return RectangleHelper.jeiToMinecraft(this.contents.getBackgroundArea());
    }

    /**
     * Gets the slot background area of the grid as Minecraft's Rect2i.
     * 
     * @return The slot background area as a Rect2i
     */
    public Rect2i getSlotBackgroundArea() {
        // Convert JEI's ImmutableRect2i to Minecraft's Rect2i
        return RectangleHelper.jeiToMinecraft(this.contents.getSlotBackgroundArea());
    }

    /**
     * Draws tooltips for the grid.
     * This method exists for compatibility with JEI's tooltip system.
     * It's recommended to use TooltipRenderer.renderIngredientTooltip instead 
     * of calling this directly.
     */
    public void drawTooltips(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY) {
        try {
            this.contents.drawTooltips(minecraft, graphics, mouseX, mouseY);
        } catch (Exception e) {
            ModLogger.error("Error drawing tooltips in JEI contents: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the bookmark key at the given coordinates, if any.
     *
     * @param mouseX X coordinate
     * @param mouseY Y coordinate
     * @return Optional containing the key if found
     */
    public Optional<String> getBookmarkKeyAt(double mouseX, double mouseY) {
        if (!isWithinBounds(mouseX, mouseY)) {
            return Optional.empty();
        }

        try {
            // First check if we clicked a bookmark
            Optional<BookmarkIngredient> ingredient = bookmarkAdapter.getBookmarkAt(mouseX, mouseY);
            if (ingredient.isPresent()) {
                IngredientService ingredientService = JEIIntegrationAPI.getIngredientService();
                return Optional.ofNullable(ingredientService.getKeyForIngredient(ingredient.get().getTypedIngredient()));
            }
        } catch (Exception e) {
            ModLogger.error("Error getting bookmark key at mouse position: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * Checks if the coordinates are within the bounds of the grid.
     *
     * @param mouseX X coordinate
     * @param mouseY Y coordinate
     * @return True if the coordinates are within bounds
     */
    public boolean isWithinBounds(double mouseX, double mouseY) {
        try {
            // Convert JEI's ImmutableRect2i to Minecraft's Rect2i and use MouseHitUtil
            Rect2i area = RectangleHelper.jeiToMinecraft(this.contents.getBackgroundArea());
            return MouseHitUtil.isMouseOverRect(mouseX, mouseY, area);
        } catch (Exception e) {
            ModLogger.error("Error checking if mouse is within bounds: {}", e.getMessage(), e);
            return false;
        }
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
            // Convert JEI's ImmutableRect2i to Minecraft's Rect2i and use MouseHitUtil
            if (!buttonArea.isEmpty()) {
                Rect2i mcRect = RectangleHelper.jeiToMinecraft(buttonArea);
                return MouseHitUtil.isMouseOverRect(mouseX, mouseY, mcRect);
            }
            return false;
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
            // Convert JEI's ImmutableRect2i to Minecraft's Rect2i and use MouseHitUtil
            if (!buttonArea.isEmpty()) {
                Rect2i mcRect = RectangleHelper.jeiToMinecraft(buttonArea);
                return MouseHitUtil.isMouseOverRect(mouseX, mouseY, mcRect);
            }
            return false;
        } catch (Exception e) {
            ModLogger.error("Error checking if back button is clicked: {}", e.getMessage(), e);
            return false;
        }
    }
}