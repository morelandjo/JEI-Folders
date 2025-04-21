package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;

import java.util.*;

/**
 * Implementation of the IngredientService that handles all ingredient operations
 */
public class IngredientServiceImpl implements IngredientService {
    private static final IngredientServiceImpl INSTANCE = new IngredientServiceImpl();
    
    // Add a cache for processed key-to-ingredient mappings
    private final Map<String, ITypedIngredient<?>> keyToIngredientCache = new HashMap<>();
    private final Map<Integer, List<ITypedIngredient<?>>> folderIngredientsCache = new HashMap<>();
    private final JEIService jeiService;
    
    private IngredientServiceImpl() {
        this.jeiService = JEIServiceImpl.getInstance();
    }
    
    /**
     * Gets the singleton instance
     */
    public static IngredientService getInstance() {
        return INSTANCE;
    }
    
    @Override
    public String getKeyForIngredient(Object ingredient) {
        try {
            // If ingredient is null, return empty
            if (ingredient == null) {
                ModLogger.warn("Attempted to get key for null ingredient");
                return "";
            }

            ModLogger.debug("Getting key for ingredient of class: {}", ingredient.getClass().getName());

            Optional<Object> managerOpt = jeiService.getIngredientManager();
            if (managerOpt.isEmpty()) {
                ModLogger.error("JEI ingredient manager not available for key generation");
                return "";
            }
            
            Object managerObj = managerOpt.get();
            if (!(managerObj instanceof IIngredientManager)) {
                ModLogger.error("JEI ingredient manager is not of expected type");
                return "";
            }
            
            IIngredientManager manager = (IIngredientManager) managerObj;

            // Special handling for directly passed JEI ingredient
            if (ingredient instanceof ITypedIngredient<?> typedIngredient) {
                ModLogger.debug("Processing typed ingredient: {}", typedIngredient);
                return getUidForTypedIngredient(typedIngredient, manager);
            }

            // Try to create a typed ingredient from raw object using a type-safe method
            String key = createKeyFromRawIngredient(ingredient, manager);

            ModLogger.debug("Generated key for ingredient: {}", key);
            return key;
        } catch (Exception e) {
            ModLogger.error("Error generating key for ingredient: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Creates a key from a raw ingredient object using proper type handling
     */
    @SuppressWarnings("unchecked")
    private <T> String createKeyFromRawIngredient(Object ingredient, IIngredientManager manager) {
        // Try with runtime type checking to find a suitable ingredient type
        for (IIngredientType<?> type : manager.getRegisteredIngredientTypes()) {
            Class<?> ingredientClass = type.getIngredientClass();

            if (ingredientClass.isInstance(ingredient)) {
                // We found a matching type, now use it with the right generic parameter
                IIngredientType<T> castType = (IIngredientType<T>) type;
                T castIngredient = (T) ingredient;

                // Create and return the key
                Optional<ITypedIngredient<T>> typedIngredient = manager.createTypedIngredient(castType, castIngredient);
                if (typedIngredient.isPresent()) {
                    return getUidForTypedIngredient(typedIngredient.get(), manager);
                }
            }
        }

        // If we couldn't find a matching type, use the toString fallback
        return ingredient.toString();
    }

    private <T> String getUidForTypedIngredient(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
        try {
            IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(typedIngredient.getType());

            Object uid = helper.getUid(typedIngredient.getIngredient(), UidContext.Recipe);

            return typedIngredient.getType().getUid() + ":" + uid.toString();
        } catch (Exception e) {
            ModLogger.error("Error generating key for typed ingredient", e);
            return typedIngredient.toString();
        }
    }

    @Override
    public Optional<ITypedIngredient<?>> getTypedIngredientFromObject(Object ingredient) {
        Optional<Object> managerOpt = jeiService.getIngredientManager();
        if (managerOpt.isEmpty()) {
            ModLogger.error("JEI ingredient manager not available");
            return Optional.empty();
        }
        
        Object managerObj = managerOpt.get();
        if (!(managerObj instanceof IIngredientManager)) {
            ModLogger.error("JEI ingredient manager is not of expected type");
            return Optional.empty();
        }
        
        IIngredientManager manager = (IIngredientManager) managerObj;

        for (IIngredientType<?> type : manager.getRegisteredIngredientTypes()) {
            Optional<?> typedIngredient = tryConvertIngredient(manager, type, ingredient);
            if (typedIngredient.isPresent()) {
                ITypedIngredient<?> result = createTypedIngredient(manager, type, typedIngredient.get());
                if (result != null) {
                    return Optional.of(result);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Attempts to convert an object to a specific ingredient type
     */
    @SuppressWarnings("unchecked")
    private <T> Optional<T> tryConvertIngredient(IIngredientManager manager, IIngredientType<T> type, Object ingredient) {
        try {
            // Check if the object is already of the right type
            if (type.getIngredientClass().isInstance(ingredient)) {
                return Optional.of((T) ingredient);
            }

            // Try to handle common conversions like ItemStack
            if (type.getIngredientClass() == net.minecraft.world.item.ItemStack.class &&
                ingredient instanceof net.minecraft.world.item.ItemStack) {
                return Optional.of((T) ingredient);
            }

            // Add more conversions as needed
        } catch (Exception e) {
            ModLogger.debug("Failed to convert ingredient {} to type {}: {}", 
                    ingredient, type.getIngredientClass().getSimpleName(), e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Creates a typed ingredient from an object of the appropriate type
     */
    @SuppressWarnings("unchecked")
    private <T> ITypedIngredient<?> createTypedIngredient(IIngredientManager manager, IIngredientType<T> type, Object ingredient) {
        try {
            T typedIngredient = (T) ingredient;
            return manager.createTypedIngredient(type, typedIngredient).orElse(null);
        } catch (Exception e) {
            ModLogger.error("Failed to create typed ingredient: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Optional<ITypedIngredient<?>> getIngredientForKey(String bookmarkKey) {
        // First check the cache
        if (keyToIngredientCache.containsKey(bookmarkKey)) {
            return Optional.ofNullable(keyToIngredientCache.get(bookmarkKey));
        }

        // If not in cache, perform the lookup
        Optional<Object> managerOpt = jeiService.getIngredientManager();
        if (managerOpt.isEmpty()) {
            return Optional.empty();
        }

        Object managerObj = managerOpt.get();
        if (!(managerObj instanceof IIngredientManager)) {
            ModLogger.error("JEI ingredient manager is not of expected type");
            return Optional.empty();
        }
        
        IIngredientManager ingredientManager = (IIngredientManager) managerObj;

        // Find the ingredient using JEI's methods
        ITypedIngredient<?> ingredient = findIngredientByKey(bookmarkKey, ingredientManager);

        // Cache the result, even if it's null
        keyToIngredientCache.put(bookmarkKey, ingredient);

        return Optional.ofNullable(ingredient);
    }

    @Override
    public List<ITypedIngredient<?>> getIngredientsForKeys(List<String> bookmarkKeys) {
        // Special optimize case for empty list
        if (bookmarkKeys.isEmpty()) {
            return Collections.emptyList();
        }

        // Start timing for performance logging
        long startTime = System.currentTimeMillis();

        List<ITypedIngredient<?>> result = new ArrayList<>();
        List<String> keysToProcess = new ArrayList<>();

        // First check which keys we already have in cache
        for (String key : bookmarkKeys) {
            if (keyToIngredientCache.containsKey(key)) {
                ITypedIngredient<?> cachedIngredient = keyToIngredientCache.get(key);
                if (cachedIngredient != null) {
                    result.add(cachedIngredient);
                }
            } else {
                keysToProcess.add(key);
            }
        }

        // If all ingredients were in cache, return immediately
        if (keysToProcess.isEmpty()) {
            ModLogger.info("All {} ingredients were retrieved from cache in {}ms", 
                result.size(), (System.currentTimeMillis() - startTime));
            return result;
        }

        // Process any keys not found in cache
        Optional<Object> managerOpt = jeiService.getIngredientManager();
        if (managerOpt.isEmpty()) {
            ModLogger.warn("JEI ingredient manager not available for ingredient lookup");
            return result;
        }
        
        Object managerObj = managerOpt.get();
        if (!(managerObj instanceof IIngredientManager)) {
            ModLogger.error("JEI ingredient manager is not of expected type");
            return result;
        }
        
        IIngredientManager ingredientManager = (IIngredientManager) managerObj;

        // Record how many ingredients were found in cache vs needed processing
        int cacheHits = result.size();

        // Only use debug logging for small numbers of keys
        boolean detailedDebug = keysToProcess.size() < 50;

        // Process remaining keys
        for (String key : keysToProcess) {
            if (detailedDebug) {
                ModLogger.debug("Finding ingredient for uncached key: {}", key);
            }

            ITypedIngredient<?> ingredient = findIngredientByKey(key, ingredientManager);
            if (ingredient != null) {
                result.add(ingredient);
                // Add to cache
                keyToIngredientCache.put(key, ingredient);
            } else {
                // Cache null result to avoid repeated lookups
                keyToIngredientCache.put(key, null);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        ModLogger.info("Retrieved {} ingredients ({} from cache, {} newly processed) in {}ms", 
            result.size(), cacheHits, (result.size() - cacheHits), totalTime);

        return result;
    }

    @Override
    public List<ITypedIngredient<?>> getCachedIngredientsForFolder(int folderId) {
        // Return the cached ingredients if available
        if (folderIngredientsCache.containsKey(folderId)) {
            ModLogger.debug("Using cached ingredients for folder ID: {}, {} items", 
                folderId, folderIngredientsCache.get(folderId).size());
            return new ArrayList<>(folderIngredientsCache.get(folderId));
        }
        
        // Otherwise, process and cache them
        com.jeifolders.data.FolderDataManager folderManager = com.jeifolders.data.FolderDataManager.getInstance();
        Optional<com.jeifolders.data.FolderDataRepresentation> folder = folderManager.getFolder(folderId);
        if (folder.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> bookmarkKeys = folder.get().getBookmarkKeys();
        List<ITypedIngredient<?>> ingredients = getIngredientsForKeys(bookmarkKeys);
        
        // Cache the processed ingredients
        folderIngredientsCache.put(folderId, new ArrayList<>(ingredients));
        ModLogger.info("Cached {} ingredients for folder ID: {}", ingredients.size(), folderId);
        
        return ingredients;
    }

    @Override
    public void invalidateIngredientsCache(int folderId) {
        folderIngredientsCache.remove(folderId);
        ModLogger.debug("Invalidated ingredients cache for folder ID: {}", folderId);
    }

    @Override
    public void clearCache() {
        keyToIngredientCache.clear();
        folderIngredientsCache.clear();
        ModLogger.info("Cleared all ingredient caches");
    }
    
    private ITypedIngredient<?> findIngredientByKey(String key, IIngredientManager ingredientManager) {
        try {
            // Split the key into type and id parts
            int colonIndex = key.indexOf(':');
            if (colonIndex <= 0 || colonIndex == key.length() - 1) {
                ModLogger.error("Invalid key format: {}", key);
                return null;
            }

            // Extract the ingredient type from the key
            String typeId = key.substring(0, colonIndex);
            // We don't need itemId for now as we're checking the full key later
            
            // Special handling for item_stack type which might not be found directly
            IIngredientType<?> ingredientType;
            if ("item_stack".equals(typeId)) {
                try {
                    // Try to get the vanilla item stack type from the registry first
                    Optional<IIngredientType<?>> optionalType = ingredientManager.getIngredientTypeForUid(typeId);
                    if (optionalType.isPresent()) {
                        ingredientType = optionalType.get();
                    } else {
                        // Fall back to get ItemStack ingredient type using a different approach
                        ModLogger.info("Using fallback method to get item_stack ingredient type");
                        ingredientType = getItemStackIngredientType(ingredientManager);
                    }
                } catch (Exception e) {
                    ModLogger.warn("Error getting item_stack ingredient type, using fallback: {}", e.getMessage());
                    ingredientType = getItemStackIngredientType(ingredientManager);
                }
            } else {
                // For other ingredient types, use the normal lookup
                Optional<IIngredientType<?>> optionalType = ingredientManager.getIngredientTypeForUid(typeId);
                if (optionalType.isEmpty()) {
                    ModLogger.error("Unknown ingredient type: {}", typeId);
                    return null;
                }
                ingredientType = optionalType.get();
            }

            // Iterate through all ingredients of that type
            for (Object ingredient : ingredientManager.getAllIngredients(ingredientType)) {
                Optional<?> typedIngredient = ingredientManager.createTypedIngredient(ingredient);
                if (typedIngredient.isPresent()) {
                    // No need for unchecked warning here
                    ITypedIngredient<?> castedIngredient = (ITypedIngredient<?>) typedIngredient.get();
                    String ingredientKey = getKeyForIngredient(castedIngredient);
                    if (key.equals(ingredientKey)) {
                        return castedIngredient;
                    }
                }
            }

            ModLogger.warn("No ingredient found for key: {}", key);
            return null;
        } catch (Exception e) {
            ModLogger.error("Error getting ingredient for key: {}", key, e);
            return null;
        }
    }
    
    /**
     * Helper method to get the ItemStack ingredient type directly from the available types
     */
    private IIngredientType<?> getItemStackIngredientType(IIngredientManager ingredientManager) {
        // Look through all registered ingredient types to find the ItemStack type
        for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
            if (type.getIngredientClass().getSimpleName().equals("ItemStack")) {
                ModLogger.info("Found ItemStack ingredient type: {}", type);
                return type;
            }
        }
        
        // If that fails, throw an exception - this should never happen in a normal Minecraft environment
        throw new IllegalStateException("Could not find ItemStack ingredient type in JEI");
    }
}