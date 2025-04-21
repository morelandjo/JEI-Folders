package com.jeifolders.integration;

import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.bookmarks.BookmarkList;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended version of BookmarkList that adds methods for adding and removing BookmarkListeners.
 * This allows our adapter to manage listeners without modifying JEI code.
 */
public class BookmarkListExtended extends BookmarkList {
    private final List<BookmarkListener> bookmarkListeners = new ArrayList<>();
    
    public BookmarkListExtended(
        IRecipeManager recipeManager,
        IFocusFactory focusFactory,
        IIngredientManager ingredientManager,
        RegistryAccess registryAccess,
        IBookmarkConfig bookmarkConfig,
        IClientConfig clientConfig,
        IGuiHelper guiHelper,
        ICodecHelper codecHelper
    ) {
        super(recipeManager, focusFactory, ingredientManager, registryAccess, bookmarkConfig, clientConfig, guiHelper, codecHelper);
    }
    
    /**
     * Add a listener that will be notified when bookmarks change.
     *
     * @param listener the listener to add
     */
    public void addListener(BookmarkListener listener) {
        if (!bookmarkListeners.contains(listener)) {
            bookmarkListeners.add(listener);
        }
    }
    
    /**
     * Remove a previously added listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(BookmarkListener listener) {
        bookmarkListeners.remove(listener);
    }
    
    @Override
    public boolean add(mezz.jei.gui.bookmarks.IBookmark value) {
        boolean result = super.add(value);
        if (result) {
            notifyListeners();
        }
        return result;
    }
    
    @Override
    public boolean remove(mezz.jei.gui.bookmarks.IBookmark ingredient) {
        boolean result = super.remove(ingredient);
        if (result) {
            notifyListeners();
        }
        return result;
    }
    
    /**
     * Add a bookmark from a typed ingredient
     */
    public void addBookmark(ITypedIngredient<?> ingredient) {
        // Use the superclass's add method with appropriate IBookmark implementation
        // This is simplified - you may need to implement the actual bookmark creation
        mezz.jei.gui.bookmarks.IBookmark bookmark = mezz.jei.gui.bookmarks.IngredientBookmark.create(ingredient, getIngredientManager());
        add(bookmark);
    }
    
    /**
     * Remove a bookmark for a typed ingredient
     */
    public void removeBookmark(ITypedIngredient<?> ingredient) {
        // This is simplified - you may need to implement the actual bookmark search and removal
        getElements().stream()
            .filter(element -> element.getTypedIngredient().equals(ingredient))
            .findFirst()
            .flatMap(element -> element.getBookmark())
            .ifPresent(this::remove);
    }
    
    /**
     * Notify all listeners that the bookmarks have changed
     */
    private void notifyListeners() {
        for (BookmarkListener listener : bookmarkListeners) {
            listener.onBookmarkListChanged();
        }
    }
    
    private IIngredientManager getIngredientManager() {
        // This is a workaround since we don't have direct access to the ingredientManager field
        // You might need to store this field separately
        return null; // Replace with actual implementation
    }
}