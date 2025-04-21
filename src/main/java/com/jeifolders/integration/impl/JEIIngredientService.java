package com.jeifolders.integration.impl;

import com.jeifolders.data.FolderDataManager;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.util.ModLogger;

import com.mojang.serialization.Codec;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.constants.VanillaTypes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the IngredientService interface that handles all JEI ingredient-related operations.
 * This class consolidates ingredient management functionality that was previously spread across multiple classes.
 */
public class JEIIngredientService implements IngredientService {

    // Cache for ingredients by folder ID
    private final Map<Integer, List<ITypedIngredient<?>>> ingredientCache = new ConcurrentHashMap<>();
    
    // Cache for ingredients by key
    private final Map<String, ITypedIngredient<?>> keyToIngredientCache = new ConcurrentHashMap<>();
    
    // Reference to the folder manager to access bookmark keys - initialized lazily to avoid circular dependency
    private FolderDataManager folderManager;
    
    // JEI's ingredient manager (set when JEI runtime is available)
    private IIngredientManager ingredientManager;
    
    // JEI's codec helper for serialization
    private ICodecHelper codecHelper;
    
    /**
     * Creates a new JEIIngredientService instance.
     */
    public JEIIngredientService() {
        // Don't initialize folderManager here to avoid circular dependency
    }
    
    // Get folder manager lazily to avoid circular dependency
    private FolderDataManager getFolderManager() {
        if (folderManager == null) {
            folderManager = FolderDataManager.getInstance();
        }
        return folderManager;
    }
    
    /**
     * Sets the JEI ingredient manager, needed for ingredient operations.
     */
    public void setIngredientManager(IIngredientManager ingredientManager) {
        this.ingredientManager = ingredientManager;
        ModLogger.info("Ingredient manager set in JEIIngredientService");
        
        // Clear the cache when the ingredient manager changes
        clearCache();
    }
    
    /**
     * Sets the JEI codec helper, needed for ingredient serialization.
     */
    public void setJeiHelpers(IJeiHelpers jeiHelpers) {
        this.codecHelper = jeiHelpers.getCodecHelper();
        ModLogger.info("Codec helper set in JEIIngredientService");
    }
    
    @Override
    public String getKeyForIngredient(Object ingredientObj) {
        if (ingredientObj == null || ingredientManager == null) {
            return "";
        }
        
        try {
            // Get a typed ingredient if needed
            ITypedIngredient<?> ingredient;
            if (ingredientObj instanceof ITypedIngredient<?>) {
                ingredient = (ITypedIngredient<?>) ingredientObj;
            } else {
                Optional<ITypedIngredient<?>> optionalIngredient = getTypedIngredientFromObject(ingredientObj);
                if (optionalIngredient.isEmpty()) {
                    return "";
                }
                ingredient = optionalIngredient.get();
            }
            
            // Get the type ID and ingredient type
            IIngredientType<?> type = ingredient.getType();
            String typeId = type.getUid();
            
            // Generate the key using the proper approach
            return generateKeyForTypedIngredient(ingredient, typeId);
        } catch (Exception e) {
            ModLogger.error("Error getting key for ingredient: {}", e.getMessage(), e);
            return "";
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> String generateKeyForTypedIngredient(ITypedIngredient<T> ingredient, String typeId) {
        // Get the helper for this ingredient type
        IIngredientHelper<T> helper = (IIngredientHelper<T>) ingredientManager.getIngredientHelper(ingredient.getType());
        
        // Get the UID using the helper
        Object uid = helper.getUid(ingredient.getIngredient(), UidContext.Ingredient);
        
        // Combine type ID and UID into a key
        return typeId + ":" + uid.toString();
    }
    
    @Override
    public Optional<ITypedIngredient<?>> getIngredientForKey(String bookmarkKey) {
        if (bookmarkKey == null || bookmarkKey.isEmpty() || ingredientManager == null) {
            return Optional.empty();
        }
        
        // Check cache first
        if (keyToIngredientCache.containsKey(bookmarkKey)) {
            return Optional.ofNullable(keyToIngredientCache.get(bookmarkKey));
        }
        
        try {
            // Parse the key to get type ID and UID
            String[] parts = bookmarkKey.split(":", 2);
            if (parts.length < 2) {
                ModLogger.warn("Invalid ingredient key format: {}", bookmarkKey);
                return Optional.empty();
            }
            
            String typeId = parts[0];
            
            // Find the ingredient type
            Optional<IIngredientType<?>> optionalType = ingredientManager.getIngredientTypeForUid(typeId);
            if (optionalType.isEmpty()) {
                // Handle different type ID formats
                // 1. Try with VanillaTypes constants
                if ("item_stack".equals(typeId)) {
                    optionalType = Optional.of(VanillaTypes.ITEM_STACK);
                }
                // 2. Handle fully qualified class name (as returned by IIngredientType.getUid())
                else if (typeId.equals("net.minecraft.world.item.ItemStack")) {
                    optionalType = Optional.of(VanillaTypes.ITEM_STACK);
                }
                // 3. For any other fully qualified class name, try to find the type by matching class name
                else if (typeId.contains(".")) {
                    optionalType = findIngredientTypeByClassName(typeId);
                }
                
                if (optionalType.isEmpty()) {
                    ModLogger.warn("Unknown ingredient type ID: {}", typeId);
                    return Optional.empty();
                }
            }
            
            IIngredientType<?> ingredientType = optionalType.get();
            
            // Find the ingredient by iterating through all ingredients of this type
            ITypedIngredient<?> result = findIngredientOfType(ingredientType, bookmarkKey);
            
            // Cache the result
            keyToIngredientCache.put(bookmarkKey, result);
            
            return Optional.ofNullable(result);
        } catch (Exception e) {
            ModLogger.warn("Error getting ingredient for key '{}': {}", bookmarkKey, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Helper method to find an ingredient type by its class name
     */
    private Optional<IIngredientType<?>> findIngredientTypeByClassName(String className) {
        // Try to get all ingredient types and find one that matches the class name
        try {
            for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
                if (type.getIngredientClass().getName().equals(className) ||
                    type.getUid().equals(className)) {
                    return Optional.of(type);
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error finding ingredient type by class name: {}", e.getMessage(), e);
        }
        
        return Optional.empty();
    }
    
    @SuppressWarnings("unchecked")
    private <T> ITypedIngredient<T> findIngredientOfType(IIngredientType<T> type, String targetKey) {
        Collection<T> allIngredients = ingredientManager.getAllIngredients(type);
        IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(type);
        
        ModLogger.debug("Finding ingredient for key '{}' in {} ingredients of type {}", 
            targetKey, allIngredients.size(), type.getUid());
            
        // Parse the target key to separate type ID and UID
        String[] parts = targetKey.split(":", 2);
        if (parts.length < 2) {
            ModLogger.warn("Invalid ingredient key format: {}", targetKey);
            return null;
        }
        
        String typeId = parts[0];
        String targetUid = parts[1];
        
        // For item types, the targetUid is often in the format "minecraft:oak_log"
        // We need to directly match against this format
        for (T ingredient : allIngredients) {
            try {
                // Get the UID for this ingredient
                String uid = helper.getUid(ingredient, UidContext.Ingredient).toString();
                
                // First try direct matching with the targetUid
                if (targetUid.equals(uid)) {
                    Optional<ITypedIngredient<T>> typedIngredient = ingredientManager.createTypedIngredient(type, ingredient);
                    if (typedIngredient.isPresent()) {
                        ModLogger.info("Found exact match for key '{}': {}", targetKey, uid);
                        return typedIngredient.get();
                    }
                }
                
                // Also try creating the key and comparing the full key
                Optional<ITypedIngredient<T>> typedIngredient = ingredientManager.createTypedIngredient(type, ingredient);
                if (typedIngredient.isPresent()) {
                    String key = getKeyForIngredient(typedIngredient.get());
                    if (targetKey.equals(key)) {
                        ModLogger.info("Found key match for '{}': {}", targetKey, key);
                        return typedIngredient.get();
                    }
                    
                    // If the type IDs match but the UIDs don't, log extra info for debugging
                    if (key.startsWith(typeId + ":")) {
                        ModLogger.debug("Key mismatch: target='{}', actual='{}'", targetKey, key);
                    }
                }
            } catch (Exception e) {
                ModLogger.warn("Error comparing ingredient: {}", e.getMessage());
            }
        }
        
        ModLogger.warn("Could not find ingredient for key: {}", targetKey);
        return null;
    }
    
    @Override
    public List<ITypedIngredient<?>> getIngredientsForKeys(List<String> bookmarkKeys) {
        if (bookmarkKeys == null || bookmarkKeys.isEmpty() || ingredientManager == null) {
            return List.of();
        }
        
        List<ITypedIngredient<?>> result = new ArrayList<>();
        
        for (String key : bookmarkKeys) {
            Optional<ITypedIngredient<?>> ingredient = getIngredientForKey(key);
            ingredient.ifPresent(result::add);
        }
        
        ModLogger.debug("Retrieved {} ingredients for {} bookmark keys", result.size(), bookmarkKeys.size());
        return result;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Optional<ITypedIngredient<?>> getTypedIngredientFromObject(Object ingredient) {
        if (ingredient == null || ingredientManager == null) {
            return Optional.empty();
        }
        
        // If it's already a typed ingredient, return it directly
        if (ingredient instanceof ITypedIngredient<?>) {
            return Optional.of((ITypedIngredient<?>) ingredient);
        }
        
        // Use the ingredient manager's type detection
        try {
            IIngredientType<?> type = ingredientManager.getIngredientType(ingredient);
            if (type != null) {
                // Use a safer approach that doesn't require casting the Optional itself
                Optional<?> result = createTypedIngredientForType(type, ingredient);
                if (result.isPresent()) {
                    return Optional.of((ITypedIngredient<?>) result.get());
                }
            }
            
            // Try direct ingredient creation as fallback
            Optional<?> typedIngredient = ingredientManager.createTypedIngredient(ingredient);
            if (typedIngredient.isPresent()) {
                return Optional.of((ITypedIngredient<?>) typedIngredient.get());
            }
            return Optional.empty();
        } catch (Exception e) {
            ModLogger.warn("Error creating typed ingredient: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> Optional<ITypedIngredient<T>> createTypedIngredientForType(IIngredientType<T> type, Object ingredient) {
        try {
            // Safe cast since we've already checked the type
            T typedValue = (T) ingredient;
            return ingredientManager.createTypedIngredient(type, typedValue);
        } catch (ClassCastException e) {
            ModLogger.warn("Type mismatch creating typed ingredient: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public List<ITypedIngredient<?>> getCachedIngredientsForFolder(int folderId) {
        // Check if we have a cache hit first
        List<ITypedIngredient<?>> cachedIngredients = ingredientCache.get(folderId);
        if (cachedIngredients != null) {
            ModLogger.debug("Ingredient cache hit for folder {}: {} ingredients", 
                folderId, cachedIngredients.size());
            return new ArrayList<>(cachedIngredients);
        }
        
        // Cache miss, need to process the ingredients
        List<String> bookmarkKeys = getFolderBookmarkKeys(folderId);
        if (bookmarkKeys.isEmpty()) {
            ModLogger.debug("No bookmarks found for folder {}", folderId);
            ingredientCache.put(folderId, List.of());
            return List.of();
        }
        
        // Get the ingredients for these keys
        List<ITypedIngredient<?>> ingredients = getIngredientsForKeys(bookmarkKeys);
        
        // Store in cache
        if (!ingredients.isEmpty()) {
            List<ITypedIngredient<?>> defensiveCopy = new ArrayList<>(ingredients);
            ingredientCache.put(folderId, defensiveCopy);
            ModLogger.debug("Cached {} ingredients for folder {}", defensiveCopy.size(), folderId);
        } else {
            ingredientCache.put(folderId, List.of());
            ModLogger.debug("Cached empty ingredient list for folder {}", folderId);
        }
        
        return ingredients;
    }
    
    private List<String> getFolderBookmarkKeys(int folderId) {
        return getFolderManager().getFolderBookmarkKeys(folderId);
    }
    
    @Override
    public void invalidateIngredientsCache(int folderId) {
        boolean removed = ingredientCache.remove(folderId) != null;
        ModLogger.debug("Invalidated ingredient cache for folder {}: {}", folderId, removed);
    }
    
    @Override
    public void clearCache() {
        ingredientCache.clear();
        keyToIngredientCache.clear();
        ModLogger.debug("Cleared entire ingredient cache");
    }
    
    /**
     * @return Whether the ingredient service is ready to be used
     */
    public boolean isReady() {
        return ingredientManager != null;
    }
}