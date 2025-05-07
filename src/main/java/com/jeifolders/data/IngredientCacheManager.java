package com.jeifolders.data;

import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.JEIIntegrationService;
import com.jeifolders.integration.api.IngredientService;
import com.jeifolders.util.ModLogger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages caching of ingredient objects for the folder storage system.
 */
public class IngredientCacheManager {
    // Singleton instance
    private static IngredientCacheManager instance;
    
    // Cache storage
    private final Map<String, Object> cache = new HashMap<>();
    
    /**
     * Private constructor for singleton pattern
     */
    private IngredientCacheManager() {
        // No initialization needed
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized IngredientCacheManager getInstance() {
        if (instance == null) {
            instance = new IngredientCacheManager();
        }
        return instance;
    }
    
    /**
     * Gets a cached ingredient by key
     * 
     * @param key The ingredient key
     * @return The cached ingredient, or null if not found
     */
    public Object get(String key) {
        return cache.get(key);
    }
    
    /**
     * Caches an ingredient
     * 
     * @param key The ingredient key
     * @param ingredient The ingredient to cache
     */
    public void put(String key, Object ingredient) {
        if (key != null && ingredient != null) {
            cache.put(key, ingredient);
        }
    }
    
    /**
     * Clears all cached ingredients
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * Gets the number of cached ingredients
     * 
     * @return The number of cached ingredients
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Removes all unused keys from the cache
     * 
     * @param activeKeys The keys that should be kept in the cache
     * @return The number of removed entries
     */
    public int pruneUnused(Collection<String> activeKeys) {
        int initialSize = cache.size();
        if (activeKeys.isEmpty()) {
            clear();
            return initialSize;
        }
        
        Set<String> keysToRemove = new HashSet<>(cache.keySet());
        keysToRemove.removeAll(activeKeys);
        
        for (String key : keysToRemove) {
            cache.remove(key);
        }
        
        return keysToRemove.size();
    }
    
    /**
     * Loads ingredients for all bookmarks in all folders
     * 
     * @param folders Collection of folders containing bookmarks
     */
    public void loadIngredientsForFolders(Collection<Folder> folders) {
        for (Folder folder : folders) {
            loadIngredientsForFolder(folder);
        }
    }
    
    /**
     * Loads ingredient data for a specific folder
     * 
     * @param folder The folder to load ingredients for
     */
    public void loadIngredientsForFolder(Folder folder) {
        if (folder == null) {
            return;
        }

        // Get the JEI integration service
        JEIIntegrationService integrationService = JEIIntegrationAPI.getIntegrationService();
        
        // JEI might not be initialized yet
        if (!integrationService.isJeiRuntimeAvailable()) {
            ModLogger.debug("Cannot load ingredients - JEI runtime not available");
            return;
        }

        // Get the ingredient service that has the getIngredientForKey method
        IngredientService ingredientService = JEIIntegrationAPI.getIngredientService();
        
        // Load all bookmark ingredients
        for (String bookmarkKey : folder.getBookmarkKeys()) {
            try {
                // Try to parse the ingredient from the bookmark key
                var ingredientOpt = ingredientService.getIngredientForKey(bookmarkKey);
                
                if (ingredientOpt.isPresent()) {
                    // Cache the ingredient if successfully parsed
                    put(bookmarkKey, ingredientOpt.get());
                } else {
                    ModLogger.debug("Failed to load ingredient for key: {}", bookmarkKey);
                }
            } catch (Exception e) {
                ModLogger.debug("Error loading ingredient for key {}: {}", bookmarkKey, e.getMessage());
            }
        }
    }
    
    /**
     * Loads a single ingredient by key
     * 
     * @param bookmarkKey The bookmark key to load
     * @return true if loaded successfully, false otherwise
     */
    public boolean loadIngredient(String bookmarkKey) {
        if (bookmarkKey == null || bookmarkKey.isEmpty()) {
            return false;
        }
        
        // Get the JEI integration service
        JEIIntegrationService integrationService = JEIIntegrationAPI.getIntegrationService();
        
        // JEI might not be initialized yet
        if (!integrationService.isJeiRuntimeAvailable()) {
            return false;
        }

        // Get the ingredient service that has the getIngredientForKey method
        IngredientService ingredientService = JEIIntegrationAPI.getIngredientService();
        
        try {
            // Try to parse the ingredient from the bookmark key
            var ingredientOpt = ingredientService.getIngredientForKey(bookmarkKey);
            
            if (ingredientOpt.isPresent()) {
                // Cache the ingredient if successfully parsed
                put(bookmarkKey, ingredientOpt.get());
                return true;
            }
        } catch (Exception e) {
            ModLogger.debug("Error loading ingredient for key {}: {}", bookmarkKey, e.getMessage());
        }
        
        return false;
    }
}