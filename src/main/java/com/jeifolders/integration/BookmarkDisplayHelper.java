package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper class that encapsulates JEI-specific bookmark display operations.
 * This class serves as a bridge between the GUI layer and JEI integration.
 */
public class BookmarkDisplayHelper {
    private final JEIService jeiService;
    private final IngredientService ingredientService;
    
    /**
     * Create a new BookmarkDisplayHelper instance.
     */
    public BookmarkDisplayHelper() {
        this.jeiService = JEIIntegrationFactory.getJEIService();
        this.ingredientService = JEIIntegrationFactory.getIngredientService();
    }
    
    /**
     * Register a callback to be executed when JEI runtime becomes available.
     * 
     * @param callback The callback to execute with the runtime
     */
    public void registerRuntimeCallback(RuntimeCallback callback) {
        jeiService.registerRuntimeCallback(runtime -> 
            callback.onRuntimeAvailable(runtime));
    }
    
    /**
     * Get the current JEI runtime if available.
     * 
     * @return An optional containing the runtime if available
     */
    public Optional<Object> getJeiRuntime() {
        return jeiService.getJeiRuntime().map(runtime -> (Object)runtime);
    }
    
    /**
     * Get cached ingredients for a folder.
     * 
     * @param folderId The ID of the folder to get ingredients for
     * @return A list of BookmarkIngredient wrappers
     */
    public List<BookmarkIngredient> getCachedIngredientsForFolder(int folderId) {
        List<ITypedIngredient<?>> ingredients = ingredientService.getCachedIngredientsForFolder(folderId);
        return wrapIngredients(ingredients);
    }
    
    /**
     * Invalidate the ingredient cache for a folder.
     * 
     * @param folderId The ID of the folder to invalidate cache for
     */
    public void invalidateIngredientsCache(int folderId) {
        ingredientService.invalidateIngredientsCache(folderId);
    }
    
    /**
     * Wraps JEI ITypedIngredient objects in BookmarkIngredient wrappers.
     * 
     * @param ingredients The JEI ingredients to wrap
     * @return A list of BookmarkIngredient wrappers
     */
    public List<BookmarkIngredient> wrapIngredients(List<ITypedIngredient<?>> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Collections.emptyList();
        }
        
        return ingredients.stream()
            .filter(i -> i != null)
            .map(BookmarkIngredient::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Unwraps BookmarkIngredient objects to JEI ITypedIngredient objects.
     * 
     * @param bookmarkIngredients The wrappers to unwrap
     * @return A list of JEI ITypedIngredient objects
     */
    public List<ITypedIngredient<?>> unwrapIngredients(List<BookmarkIngredient> bookmarkIngredients) {
        if (bookmarkIngredients == null || bookmarkIngredients.isEmpty()) {
            return Collections.emptyList();
        }
        
        return bookmarkIngredients.stream()
            .filter(i -> i != null && i.getTypedIngredient() != null)
            .map(BookmarkIngredient::getTypedIngredient)
            .collect(Collectors.toList());
    }
    
    /**
     * Convert generic objects to BookmarkIngredient wrappers.
     * 
     * @param objects The objects to convert
     * @return A list of BookmarkIngredient wrappers
     */
    @SuppressWarnings("unchecked")
    public List<BookmarkIngredient> convertToBookmarkIngredients(List<Object> objects) {
        List<BookmarkIngredient> result = new ArrayList<>();
        
        if (objects == null || objects.isEmpty()) {
            return result;
        }
        
        for (Object obj : objects) {
            if (obj instanceof ITypedIngredient<?>) {
                result.add(new BookmarkIngredient((ITypedIngredient<?>)obj));
            } else if (obj instanceof BookmarkIngredient) {
                result.add((BookmarkIngredient)obj);
            } else {
                ModLogger.warn("Skipping non-supported object: {}", 
                    obj != null ? obj.getClass().getName() : "null");
            }
        }
        
        return result;
    }
    
    /**
     * Functional interface for runtime callbacks.
     */
    public interface RuntimeCallback {
        void onRuntimeAvailable(Object runtime);
    }
}