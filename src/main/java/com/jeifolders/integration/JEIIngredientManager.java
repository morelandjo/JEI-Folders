package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.*;

/**
 * Manages ingredient-related operations for JEI integration.
 */
public class JEIIngredientManager {
    private static IIngredientManager ingredientManager;

    // Add a cache for processed key-to-ingredient mappings
    private static final Map<String, ITypedIngredient<?>> keyToIngredientCache = new HashMap<>();

    public static void setIngredientManager(IIngredientManager manager) {
        ingredientManager = manager;
    }

    public static IIngredientManager getIngredientManager() {
        if (ingredientManager == null) {
            Optional<IJeiRuntime> runtimeOptional = JEIIntegration.getJeiRuntime();
            if (runtimeOptional.isPresent()) {
                ingredientManager = runtimeOptional.get().getIngredientManager();
            }
        }
        return ingredientManager;
    }

    public static String getKeyForIngredient(Object ingredient) {
        try {
            // If ingredient is null, return empty
            if (ingredient == null) {
                ModLogger.warn("Attempted to get key for null ingredient");
                return "";
            }

            ModLogger.debug("Getting key for ingredient of class: {}", ingredient.getClass().getName());

            IIngredientManager manager = getIngredientManager();
            if (manager == null) {
                ModLogger.error("JEI ingredient manager not available for key generation");
                return "";
            }

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
    private static <T> String createKeyFromRawIngredient(Object ingredient, IIngredientManager manager) {
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

    private static <T> String getUidForTypedIngredient(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
        try {
            IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(typedIngredient.getType());

            Object uid = helper.getUid(typedIngredient.getIngredient(), UidContext.Recipe);

            return typedIngredient.getType().getUid() + ":" + uid.toString();
        } catch (Exception e) {
            ModLogger.error("Error generating key for typed ingredient", e);
            return typedIngredient.toString();
        }
    }

    /**
     * Converts a generic Object to an ITypedIngredient if possible.
     * This is used for drag-and-drop operations where the ingredient
     * might come from different sources.
     */
    public static ITypedIngredient<?> getTypedIngredientFromObject(Object ingredient) {
        IIngredientManager manager = getIngredientManager();
        if (manager == null) return null;

        for (IIngredientType<?> type : manager.getRegisteredIngredientTypes()) {
            Optional<?> typedIngredient = tryConvertIngredient(manager, type, ingredient);
            if (typedIngredient.isPresent()) {
                return createTypedIngredient(manager, type, typedIngredient.get());
            }
        }

        return null;
    }

    /**
     * Attempts to convert an object to a specific ingredient type.
     */
    @SuppressWarnings("unchecked")
    private static <T> Optional<T> tryConvertIngredient(IIngredientManager manager, IIngredientType<T> type, Object ingredient) {
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
     * Creates a typed ingredient from an object of the appropriate type.
     */
    @SuppressWarnings("unchecked")
    private static <T> ITypedIngredient<?> createTypedIngredient(IIngredientManager manager, IIngredientType<T> type, Object ingredient) {
        try {
            T typedIngredient = (T) ingredient;
            return manager.createTypedIngredient(type, typedIngredient).orElse(null);
        } catch (Exception e) {
            ModLogger.error("Failed to create typed ingredient: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the ingredient for a bookmark key.
     * This is a cached operation to avoid expensive lookups.
     * 
     * @param bookmarkKey The bookmark key to look up
     * @return The found ingredient, or null if not found
     */
    public static ITypedIngredient<?> getIngredientForKey(String bookmarkKey) {
        // First check the cache
        if (keyToIngredientCache.containsKey(bookmarkKey)) {
            return keyToIngredientCache.get(bookmarkKey);
        }

        // If not in cache, perform the lookup
        Optional<IJeiRuntime> runtimeOpt = JEIIntegration.getJeiRuntime();
        if (!runtimeOpt.isPresent()) {
            return null;
        }

        IJeiRuntime runtime = runtimeOpt.get();
        IIngredientManager ingredientManager = runtime.getIngredientManager();

        // Find the ingredient using JEI's methods
        ITypedIngredient<?> ingredient = findIngredientByKey(bookmarkKey, ingredientManager);

        // Cache the result, even if it's null
        keyToIngredientCache.put(bookmarkKey, ingredient);

        return ingredient;
    }

    /**
     * Gets ingredients for a list of bookmark keys.
     * Uses batch processing to improve performance.
     * 
     * @param bookmarkKeys The list of bookmark keys to look up
     * @return A list of found ingredients (excluding any that couldn't be found)
     */
    public static List<ITypedIngredient<?>> getIngredientsForKeys(List<String> bookmarkKeys) {
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
        Optional<IJeiRuntime> runtimeOpt = JEIIntegration.getJeiRuntime();
        if (!runtimeOpt.isPresent()) {
            ModLogger.warn("JEI runtime not available for ingredient lookup");
            return result;
        }

        IJeiRuntime runtime = runtimeOpt.get();
        IIngredientManager ingredientManager = runtime.getIngredientManager();

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

    /**
     * Clears the ingredient cache.
     * Call this when JEI reloads to ensure fresh data.
     */
    public static void clearCache() {
        keyToIngredientCache.clear();
        ModLogger.info("Cleared ingredient key cache");
    }

    /**
     * Gets a list of ingredients from a list of bookmark keys.
     * This is a wrapper around getIngredientsForKeys that returns generic Objects
     * instead of JEI-specific types.
     */
    public static List<Object> getIngredientsForBookmarkKeys(List<String> keys) {
        List<ITypedIngredient<?>> typedIngredients = getIngredientsForKeys(keys);
        List<Object> result = new ArrayList<>(typedIngredients.size());

        // Convert to generic objects to avoid direct JEI dependencies
        for (ITypedIngredient<?> ingredient : typedIngredients) {
            result.add(ingredient);
        }

        return result;
    }

    private static ITypedIngredient<?> findIngredientByKey(String key, IIngredientManager ingredientManager) {
        try {
            // Split the key into type and id parts
            int colonIndex = key.indexOf(':');
            if (colonIndex <= 0 || colonIndex == key.length() - 1) {
                ModLogger.error("Invalid key format: {}", key);
                return null;
            }

            // Extract the ingredient type from the key
            String typeId = key.substring(0, colonIndex);
            Optional<IIngredientType<?>> optionalType = ingredientManager.getIngredientTypeForUid(typeId);

            if (optionalType.isEmpty()) {
                ModLogger.error("Unknown ingredient type: {}", typeId);
                return null;
            }

            IIngredientType<?> ingredientType = optionalType.get();

            // Iterate through all ingredients of that type
            for (Object ingredient : ingredientManager.getAllIngredients(ingredientType)) {
                Optional<?> typedIngredient = ingredientManager.createTypedIngredient(ingredient);
                if (typedIngredient.isPresent()) {
                    @SuppressWarnings("unchecked")
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
}
