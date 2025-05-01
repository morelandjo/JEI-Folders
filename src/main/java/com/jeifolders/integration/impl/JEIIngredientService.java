package com.jeifolders.integration.impl;

import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.ingredient.Ingredient;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.integration.JEIRuntime;
import com.jeifolders.util.ModLogger;

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
import java.util.stream.Collectors;

/**
 * Implementation of the IngredientService interface that handles all JEI ingredient-related operations.
 * This class consolidates ingredient management functionality that was previously spread across multiple classes.
 */
public class JEIIngredientService implements IngredientService {

    // Cache for ingredients by folder ID
    private final Map<Integer, List<Ingredient>> ingredientCache = new ConcurrentHashMap<>();
    
    // Cache for ingredients by key
    private final Map<String, Ingredient> keyToIngredientCache = new ConcurrentHashMap<>();
    
    // Reference to the folder service to access bookmark keys - initialized lazily to avoid circular dependency
    private FolderStorageService folderService;
    
    // Reference to the JEIRuntime for centralized JEI access
    private final JEIRuntime jeiRuntime = JEIRuntime.getInstance();
    
    // JEI's codec helper for serialization
    private ICodecHelper codecHelper;
    
    // Reference to the ingredient manager
    private final IngredientManager unifiedIngredientManager = IngredientManager.getInstance();
    
    /**
     * Creates a new JEIIngredientService instance.
     */
    public JEIIngredientService() {
        // Don't initialize folderService here to avoid circular dependency
    }
    
    // Get folder service lazily to avoid circular dependency
    private FolderStorageService getFolderService() {
        if (folderService == null) {
            folderService = FolderStorageService.getInstance();
        }
        return folderService;
    }
    
    /**
     * Sets the JEI codec helper, needed for ingredient serialization.
     */
    public void setJeiHelpers(IJeiHelpers jeiHelpers) {
        this.codecHelper = jeiHelpers.getCodecHelper();
    }
    
    /**
     * Gets the current ingredient manager from JEIRuntime
     */
    private Optional<IIngredientManager> getIngredientManager() {
        return jeiRuntime.getIngredientManager();
    }
    
    @Override
    public String getKeyForIngredient(Object ingredientObj) {
        Optional<IIngredientManager> managerOpt = getIngredientManager();
        if (ingredientObj == null || managerOpt.isEmpty()) {
            return "";
        }
        
        IIngredientManager ingredientManager = managerOpt.get();
        
        try {
            // Get a typed ingredient if needed
            ITypedIngredient<?> typedIngredient;
            if (ingredientObj instanceof Ingredient) {
                typedIngredient = ((Ingredient) ingredientObj).getTypedIngredient();
                if (typedIngredient == null) {
                    return ((Ingredient) ingredientObj).getKey();
                }
            } else if (ingredientObj instanceof ITypedIngredient<?>) {
                typedIngredient = (ITypedIngredient<?>) ingredientObj;
            } else {
                Optional<ITypedIngredient<?>> optionalIngredient = getTypedIngredientFromObject(ingredientObj);
                if (optionalIngredient.isEmpty()) {
                    return "";
                }
                typedIngredient = optionalIngredient.get();
            }
            
            // Get the type ID and ingredient type
            IIngredientType<?> type = typedIngredient.getType();
            String typeId = type.getUid();
            
            // Generate the key using the proper approach
            return generateKeyForTypedIngredient(typedIngredient, typeId, ingredientManager);
        } catch (Exception e) {
            ModLogger.error("Error getting key for ingredient: {}", e.getMessage(), e);
            return "";
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> String generateKeyForTypedIngredient(ITypedIngredient<T> ingredient, String typeId, IIngredientManager ingredientManager) {
        // Get the helper for this ingredient type
        IIngredientHelper<T> helper = (IIngredientHelper<T>) ingredientManager.getIngredientHelper(ingredient.getType());
        
        // Get the UID using the helper
        Object uid = helper.getUid(ingredient.getIngredient(), UidContext.Ingredient);
        
        // Combine type ID and UID into a key
        return typeId + ":" + uid.toString();
    }
    
    @Override
    public Optional<Ingredient> getIngredientForKey(String bookmarkKey) {
        Optional<IIngredientManager> managerOpt = getIngredientManager();
        if (bookmarkKey == null || bookmarkKey.isEmpty() || managerOpt.isEmpty()) {
            return Optional.empty();
        }
        
        // Check cache first
        if (keyToIngredientCache.containsKey(bookmarkKey)) {
            return Optional.ofNullable(keyToIngredientCache.get(bookmarkKey));
        }
        
        try {
            // Try to get a typed ingredient from the key
            Optional<ITypedIngredient<?>> typedIngredient = getTypedIngredientFromKey(bookmarkKey, managerOpt.get());
            
            // If found, create a new Ingredient and cache it
            if (typedIngredient.isPresent()) {
                Ingredient ingredient = unifiedIngredientManager.createIngredient(typedIngredient.get(), bookmarkKey);
                keyToIngredientCache.put(bookmarkKey, ingredient);
                return Optional.of(ingredient);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            ModLogger.error("Error getting ingredient for key: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    private Optional<ITypedIngredient<?>> getTypedIngredientFromKey(String bookmarkKey, IIngredientManager ingredientManager) {
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
                if ("item_stack".equals(typeId)) {
                    optionalType = Optional.of(VanillaTypes.ITEM_STACK);
                }
                else if (typeId.equals("net.minecraft.world.item.ItemStack")) {
                    optionalType = Optional.of(VanillaTypes.ITEM_STACK);
                }
                else if (typeId.contains(".")) {
                    optionalType = findIngredientTypeByClassName(typeId, ingredientManager);
                }
                
                if (optionalType.isEmpty()) {
                    ModLogger.warn("Unknown ingredient type ID: {}", typeId);
                    return Optional.empty();
                }
            }
            
            IIngredientType<?> ingredientType = optionalType.get();
            
            // Use the existing implementation to find the ingredient by iterating over
            // all ingredients of this type and comparing keys
            for (Object ingredient : ingredientManager.getAllIngredients(ingredientType)) {
                Optional<?> optTypedIngredient = ingredientManager.createTypedIngredient(ingredient);
                if (optTypedIngredient.isPresent()) {
                    ITypedIngredient<?> typedIngredient = (ITypedIngredient<?>) optTypedIngredient.get();
                    String key = getKeyForIngredient(typedIngredient);
                    if (bookmarkKey.equals(key)) {
                        return Optional.of(typedIngredient);
                    }
                }
            }
            
            ModLogger.debug("No ingredient found for key: {}", bookmarkKey);
            return Optional.empty();
        } catch (Exception e) {
            ModLogger.error("Error parsing ingredient key: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<Ingredient> getIngredientsForKeys(List<String> bookmarkKeys) {
        Optional<IIngredientManager> managerOpt = getIngredientManager();
        if (bookmarkKeys == null || bookmarkKeys.isEmpty() || managerOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Ingredient> result = new ArrayList<>();
        
        // Process all keys
        for (String key : bookmarkKeys) {
            Optional<Ingredient> ingredient = getIngredientForKey(key);
            ingredient.ifPresent(result::add);
        }
        
        return result;
    }
    
    @Override
    public Optional<Ingredient> getIngredientFromObject(Object ingredientObj) {
        Optional<IIngredientManager> managerOpt = getIngredientManager();
        if (ingredientObj == null || managerOpt.isEmpty()) {
            return Optional.empty();
        }
        
        // If it's already a unified Ingredient, return it
        if (ingredientObj instanceof Ingredient) {
            return Optional.of((Ingredient) ingredientObj);
        }
        
        // Get a typed ingredient and create a new Ingredient
        Optional<ITypedIngredient<?>> typedIngredient = getTypedIngredientFromObject(ingredientObj);
        if (typedIngredient.isPresent()) {
            String key = getKeyForIngredient(typedIngredient.get());
            if (!key.isEmpty()) {
                return Optional.of(unifiedIngredientManager.createIngredient(typedIngredient.get(), key));
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public Optional<ITypedIngredient<?>> getTypedIngredientFromObject(Object ingredientObj) {
        Optional<IIngredientManager> managerOpt = getIngredientManager();
        if (ingredientObj == null || managerOpt.isEmpty()) {
            return Optional.empty();
        }
        
        IIngredientManager ingredientManager = managerOpt.get();
        
        // If it's already a unified Ingredient, get the wrapped ITypedIngredient
        if (ingredientObj instanceof Ingredient) {
            return Optional.ofNullable(((Ingredient) ingredientObj).getTypedIngredient());
        }
        
        // If it's already an ITypedIngredient, return it
        if (ingredientObj instanceof ITypedIngredient<?>) {
            return Optional.of((ITypedIngredient<?>) ingredientObj);
        }
        
        // Try to create an ITypedIngredient from the raw object
        // Cast to appropriate wildcard type to match the method's return type
        @SuppressWarnings("unchecked")
        Optional<ITypedIngredient<?>> result = (Optional<ITypedIngredient<?>>) (Optional<?>) ingredientManager.createTypedIngredient(ingredientObj);
        return result;
    }
    
    @Override
    public List<Ingredient> getCachedIngredientsForFolder(int folderId) {
        // Return cached ingredients if available
        if (ingredientCache.containsKey(folderId)) {
            return ingredientCache.get(folderId);
        }
        
        // Otherwise fetch from folder service and cache
        FolderStorageService folderStorage = getFolderService();
        Optional<com.jeifolders.data.Folder> folder = folderStorage.getFolder(folderId);
        
        if (folder.isEmpty()) {
            ModLogger.warn("Folder with ID {} not found", folderId);
            return Collections.emptyList();
        }
        
        List<String> bookmarkKeys = folder.get().getBookmarkKeys();
        List<Ingredient> ingredients = getIngredientsForKeys(bookmarkKeys);
        
        // Cache the result
        ingredientCache.put(folderId, ingredients);
        
        return ingredients;
    }
    
    @Override
    public void invalidateIngredientsCache(int folderId) {
        ingredientCache.remove(folderId);
    }
    
    @Override
    public void clearCache() {
        ingredientCache.clear();
        keyToIngredientCache.clear();
    }
    
    private Optional<IIngredientType<?>> findIngredientTypeByClassName(String className, IIngredientManager ingredientManager) {
        for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
            if (type.getIngredientClass().getName().equals(className)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}