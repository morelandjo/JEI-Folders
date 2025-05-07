// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/core/IngredientServiceImpl.java
package com.jeifolders.integration.core;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.api.IngredientService;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Concrete implementation of IngredientService.
 * Provides the unified ingredient operations needed by other components.
 */
public class IngredientServiceImpl implements IngredientService {

    // Cache of ingredients by folder ID
    private final Map<Integer, List<IIngredient>> ingredientCache = new HashMap<>();
    
    // Singleton instance
    private static final IngredientServiceImpl INSTANCE = new IngredientServiceImpl();
    
    /**
     * Private constructor for singleton
     */
    private IngredientServiceImpl() {
        // Private constructor to enforce singleton pattern
    }
    
    /**
     * Get the singleton instance
     * 
     * @return The singleton instance
     */
    public static IngredientServiceImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public Optional<IIngredient> getIngredientForKey(String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        
        // Get the ingredient manager
        IngredientManager ingredientManager = IngredientManager.getInstance();
        if (!ingredientManager.isInitialized()) {
            ModLogger.warn("Cannot get ingredient for key - IngredientManager not initialized");
            return Optional.empty();
        }
        
        // Get the JEI ingredient manager
        Optional<IIngredientManager> jeiManagerOpt = ingredientManager.getIngredientManager();
        if (!jeiManagerOpt.isPresent()) {
            ModLogger.warn("Cannot get ingredient for key - JEI ingredient manager not available");
            return Optional.empty();
        }
        
        IIngredientManager jeiManager = jeiManagerOpt.get();
        
        // Try to parse the key into ingredients
        try {
            // First try to get an exact string key match (e.g. from bookmarks)
            for (var type : jeiManager.getRegisteredIngredientTypes()) {
                for (Object rawIngredient : jeiManager.getAllIngredients(type)) {
                    try {
                        @SuppressWarnings("unchecked")
                        IIngredientHelper<Object> helper = 
                            (IIngredientHelper<Object>) jeiManager.getIngredientHelper(rawIngredient);
                        
                        String ingredientKey = helper.getUniqueId(rawIngredient, mezz.jei.api.ingredients.subtypes.UidContext.Recipe);
                        if (ingredientKey.equals(key)) {
                            // We found a direct match for the key
                            Optional<ITypedIngredient<Object>> typedIngredient = 
                                jeiManager.createTypedIngredient(rawIngredient);
                            
                            if (typedIngredient.isPresent()) {
                                return Optional.of(ingredientManager.createIngredient(typedIngredient.get()));
                            }
                        }
                    } catch (Exception e) {
                        // Skip this ingredient if there's an error
                        continue;
                    }
                }
            }
            
            ModLogger.debug("No ingredient found with exact key: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            ModLogger.error("Error getting ingredient for key {}: {}", key, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public String getKeyForIngredient(Object ingredient) {
        if (ingredient == null) {
            return "";
        }
        
        // Handle different types of ingredients
        try {
            // Handle our unified IIngredient
            if (ingredient instanceof IIngredient) {
                IIngredient unifiedIngredient = (IIngredient) ingredient;
                if (unifiedIngredient.getTypedIngredient() != null) {
                    return getKeyForJeiIngredient(unifiedIngredient.getTypedIngredient());
                } else if (unifiedIngredient.getRawIngredient() != null) {
                    return getKeyForRawIngredient(unifiedIngredient.getRawIngredient());
                }
            }
            // Handle JEI's ITypedIngredient directly
            else if (ingredient instanceof ITypedIngredient<?>) {
                return getKeyForJeiIngredient((ITypedIngredient<?>) ingredient);
            }
            // Handle any other raw ingredient
            else {
                return getKeyForRawIngredient(ingredient);
            }
        } catch (Exception e) {
            ModLogger.error("Error getting key for ingredient: {}", e.getMessage());
        }
        
        return "";
    }

    /**
     * Helper method to get a key for a JEI ITypedIngredient
     */
    private String getKeyForJeiIngredient(ITypedIngredient<?> ingredient) {
        if (ingredient == null) {
            return "";
        }
        
        try {
            IngredientManager ingredientManager = IngredientManager.getInstance();
            Optional<IIngredientManager> jeiManagerOpt = ingredientManager.getIngredientManager();
            
            if (!jeiManagerOpt.isPresent()) {
                return "";
            }
            
            IIngredientManager jeiManager = jeiManagerOpt.get();
            
            // This gets the raw ingredient from the ITypedIngredient
            Object rawIngredient = ingredient.getIngredient();
            
            // Use the appropriate helper to get the unique ID
            @SuppressWarnings("unchecked")
            IIngredientHelper<Object> helper = 
                (IIngredientHelper<Object>) jeiManager.getIngredientHelper(ingredient.getType());
            
            return helper.getUniqueId(rawIngredient, mezz.jei.api.ingredients.subtypes.UidContext.Recipe);
        } catch (Exception e) {
            ModLogger.error("Error getting key for JEI ingredient: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Helper method to get a key for a raw ingredient object
     */
    private String getKeyForRawIngredient(Object rawIngredient) {
        if (rawIngredient == null) {
            return "";
        }
        
        try {
            IngredientManager ingredientManager = IngredientManager.getInstance();
            Optional<IIngredientManager> jeiManagerOpt = ingredientManager.getIngredientManager();
            
            if (!jeiManagerOpt.isPresent()) {
                return rawIngredient.toString();
            }
            
            // Get the JEI ingredient manager
            IIngredientManager jeiManager = jeiManagerOpt.get();
            
            // Try to create a typed ingredient first
            @SuppressWarnings("unchecked")
            Optional<ITypedIngredient<Object>> typedIngredient = jeiManager.createTypedIngredient(rawIngredient);
            
            if (typedIngredient.isPresent()) {
                return getKeyForJeiIngredient(typedIngredient.get());
            } else {
                // Fallback to string representation
                return rawIngredient.toString();
            }
        } catch (Exception e) {
            ModLogger.error("Error getting key for raw ingredient: {}", e.getMessage(), e);
            return rawIngredient.toString();
        }
    }

    @Override
    public List<IIngredient> getCachedIngredientsForFolder(int folderId) {
        // Check the cache first
        if (ingredientCache.containsKey(folderId)) {
            return ingredientCache.get(folderId);
        }
        
        // Load ingredients from folder
        List<IIngredient> ingredients = loadIngredientsFromFolder(folderId);
        
        // Cache the results
        ingredientCache.put(folderId, ingredients);
        
        return ingredients;
    }
    
    /**
     * Helper method to load ingredients from a folder
     */
    private List<IIngredient> loadIngredientsFromFolder(int folderId) {
        // Get the folder service
        FolderStorageService folderService = FolderStorageService.getInstance();
        
        // Get the folder
        Optional<Folder> folderOpt = folderService.getFolder(folderId);
        if (!folderOpt.isPresent()) {
            ModLogger.warn("Folder with ID {} not found", folderId);
            return Collections.emptyList();
        }
        
        Folder folder = folderOpt.get();
        List<String> bookmarkKeys = folder.getBookmarkKeys();
        
        // Load ingredients for all keys
        List<IIngredient> ingredients = new ArrayList<>();
        for (String key : bookmarkKeys) {
            getIngredientForKey(key).ifPresent(ingredients::add);
        }
        
        return ingredients;
    }

    @Override
    public void invalidateIngredientsCache(int folderId) {
        ingredientCache.remove(folderId);
    }

    @Override
    public boolean addIngredientToFolder(int folderId, IIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        
        // Get the folder service
        FolderStorageService folderService = FolderStorageService.getInstance();
        
        // Get the folder
        Optional<Folder> folderOpt = folderService.getFolder(folderId);
        if (!folderOpt.isPresent()) {
            ModLogger.warn("Folder with ID {} not found", folderId);
            return false;
        }
        
        Folder folder = folderOpt.get();
        
        // Get the key for this ingredient
        String key = ingredient.getKey();
        if (key.isEmpty()) {
            ModLogger.warn("Cannot add ingredient with empty key to folder");
            return false;
        }
        
        // Check if it's already in the folder
        if (folder.getBookmarkKeys().contains(key)) {
            return false;
        }
        
        // Add the key to the folder
        folder.addBookmarkKey(key);
        
        // Save the folder
        folderService.saveFolder(folder);
        
        // Invalidate the cache
        invalidateIngredientsCache(folderId);
        
        return true;
    }

    @Override
    public boolean removeIngredientFromFolder(int folderId, IIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        
        // Get the folder service
        FolderStorageService folderService = FolderStorageService.getInstance();
        
        // Get the folder
        Optional<Folder> folderOpt = folderService.getFolder(folderId);
        if (!folderOpt.isPresent()) {
            ModLogger.warn("Folder with ID {} not found", folderId);
            return false;
        }
        
        Folder folder = folderOpt.get();
        
        // Get the key for this ingredient
        String key = ingredient.getKey();
        if (key.isEmpty()) {
            ModLogger.warn("Cannot remove ingredient with empty key from folder");
            return false;
        }
        
        // Check if it's in the folder
        if (!folder.getBookmarkKeys().contains(key)) {
            return false;
        }
        
        // Remove the key from the folder
        folder.removeBookmarkKey(key);
        
        // Save the folder
        folderService.saveFolder(folder);
        
        // Invalidate the cache
        invalidateIngredientsCache(folderId);
        
        return true;
    }

    @Override
    public boolean isIngredientInFolder(int folderId, IIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        
        // Get the folder service
        FolderStorageService folderService = FolderStorageService.getInstance();
        
        // Get the folder
        Optional<Folder> folderOpt = folderService.getFolder(folderId);
        if (!folderOpt.isPresent()) {
            ModLogger.warn("Folder with ID {} not found", folderId);
            return false;
        }
        
        Folder folder = folderOpt.get();
        
        // Get the key for this ingredient
        String key = ingredient.getKey();
        if (key.isEmpty()) {
            return false;
        }
        
        // Check if it's in the folder
        return folder.getBookmarkKeys().contains(key);
    }

    @Override
    public IIngredient createIngredient(ITypedIngredient<?> typedIngredient) {
        return IngredientManager.getInstance().createIngredient(typedIngredient);
    }

    @Override
    public IIngredient createIngredient(Object ingredient) {
        return IngredientManager.getInstance().createIngredient(ingredient);
    }

    @Override
    public String getDisplayName(IIngredient ingredient) {
        return IngredientManager.getInstance().getDisplayName(ingredient);
    }
}