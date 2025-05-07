package com.jeifolders.integration;

import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.api.IIngredient;
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
    private final JEIRuntime jeiRuntime;
    
    private IngredientServiceImpl() {
        this.jeiRuntime = JEIRuntime.getInstance();
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

            Optional<IIngredientManager> managerOpt = jeiRuntime.getIngredientManager();
            if (managerOpt.isEmpty()) {
                ModLogger.error("JEI ingredient manager not available for key generation");
                return "";
            }
            
            IIngredientManager manager = managerOpt.get();

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
    public Optional<IIngredient> getIngredientFromObject(Object ingredientObj) {
        // First, try to get a typed ingredient
        Optional<ITypedIngredient<?>> typedIngredientOpt = getTypedIngredientFromObject(ingredientObj);
        
        // If we have a typed ingredient, convert it to our unified Ingredient
        if (typedIngredientOpt.isPresent()) {
            return Optional.of(new com.jeifolders.integration.ingredient.Ingredient(typedIngredientOpt.get()));
        }
        
        // If we couldn't get a typed ingredient but the object is not null,
        // create an ingredient from the raw object
        if (ingredientObj != null) {
            return Optional.of(new com.jeifolders.integration.ingredient.Ingredient(ingredientObj));
        }
        
        // Otherwise, return empty
        return Optional.empty();
    }

    @Override
    public Optional<ITypedIngredient<?>> getTypedIngredientFromObject(Object ingredient) {
        Optional<IIngredientManager> managerOpt = jeiRuntime.getIngredientManager();
        if (managerOpt.isEmpty()) {
            ModLogger.error("JEI ingredient manager not available");
            return Optional.empty();
        }
        
        IIngredientManager manager = managerOpt.get();

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
    public Optional<IIngredient> getIngredientForKey(String bookmarkKey) {
        ModLogger.info("Looking up ingredient for key: {}", bookmarkKey);
        
        // First check the cache
        if (keyToIngredientCache.containsKey(bookmarkKey)) {
            ITypedIngredient<?> cachedIngredient = keyToIngredientCache.get(bookmarkKey);
            if (cachedIngredient != null) {
                ModLogger.info("Found cached ingredient for key: {}", bookmarkKey);
                return Optional.of(new com.jeifolders.integration.ingredient.Ingredient(cachedIngredient));
            } else {
                ModLogger.info("Found null cached ingredient for key: {}", bookmarkKey);
                return Optional.empty();
            }
        }

        // If not in cache, perform the lookup
        Optional<IIngredientManager> managerOpt = jeiRuntime.getIngredientManager();
        if (managerOpt.isEmpty()) {
            ModLogger.error("JEI ingredient manager not available for ingredient lookup");
            return Optional.empty();
        }

        IIngredientManager ingredientManager = managerOpt.get();
        
        // Find the ingredient using enhanced methods
        ITypedIngredient<?> ingredient = findAnyIngredientMatch(bookmarkKey, ingredientManager);

        // Cache the result, even if it's null
        keyToIngredientCache.put(bookmarkKey, ingredient);

        // Convert to our unified Ingredient class and return
        if (ingredient != null) {
            ModLogger.info("Successfully found ingredient for key: {}", bookmarkKey);
            return Optional.of(new com.jeifolders.integration.ingredient.Ingredient(ingredient));
        } else {
            ModLogger.warn("Failed to find ingredient for key: {}", bookmarkKey);
            return Optional.empty();
        }
    }
    
    /**
     * Enhanced method to find any matching ingredient using multiple strategies
     */
    private ITypedIngredient<?> findAnyIngredientMatch(String key, IIngredientManager ingredientManager) {
        // First try the standard lookup method
        ITypedIngredient<?> result = findIngredientByKey(key, ingredientManager);
        if (result != null) {
            return result;
        }
        
        ModLogger.info("Standard lookup failed for key: {}. Trying migration helper...", key);
        
        // Use the ItemMigrationHelper to find a matching ingredient for the key
        Optional<ITypedIngredient<?>> migrationResult = ItemMigrationHelper.getInstance()
            .findMatchingIngredient(key, ingredientManager);
        
        if (migrationResult.isPresent()) {
            ModLogger.info("Migration helper found matching ingredient for key: {}", key);
            return migrationResult.get();
        }
        
        // If migration helper fails, try brute force approach for Minecraft items
        ModLogger.info("Migration helper failed. Trying brute force approach...");
        
        if (key.startsWith("item_stack:minecraft:")) {
            String itemName = key.substring("item_stack:minecraft:".length());
            ModLogger.info("Extracted item name '{}' from key", itemName);
            
            // Try direct lookup by item name
            ITypedIngredient<?> match = findItemByName(itemName, ingredientManager);
            if (match != null) {
                ModLogger.info("Found match by item name: {}", itemName);
                return match;
            }
            
            // Try simplified name (remove any numbers/tags/etc)
            String simpleName = simplifyItemName(itemName);
            if (!simpleName.equals(itemName)) {
                ModLogger.info("Trying with simplified name: {}", simpleName);
                match = findItemByName(simpleName, ingredientManager);
                if (match != null) {
                    ModLogger.info("Found match by simplified name: {}", simpleName);
                    return match;
                }
            }
            
            // Try super aggressive matching - just first part of name
            int underscorePos = itemName.indexOf('_');
            if (underscorePos > 0) {
                String baseItemType = itemName.substring(0, underscorePos);
                ModLogger.info("Trying with base item type: {}", baseItemType);
                match = findItemByNamePart(baseItemType, ingredientManager);
                if (match != null) {
                    ModLogger.info("Found match using base item type: {}", baseItemType);
                    return match;
                }
            }
        }
        
        ModLogger.warn("All ingredient lookup strategies failed for key: {}", key);
        return null;
    }
    
    /**
     * Find an item directly by its name
     */
    private ITypedIngredient<?> findItemByName(String itemName, IIngredientManager ingredientManager) {
        // Get the ItemStack ingredient type
        IIngredientType<?> itemStackType;
        try {
            Optional<IIngredientType<?>> optionalType = ingredientManager.getIngredientTypeForUid("item_stack");
            if (optionalType.isPresent()) {
                itemStackType = optionalType.get();
            } else {
                itemStackType = getItemStackIngredientType(ingredientManager);
            }
        } catch (Exception e) {
            ModLogger.error("Failed to get ItemStack ingredient type", e);
            return null;
        }
        
        ModLogger.info("Searching all ingredients of type: {}", itemStackType);
        int checked = 0;
        
        // Loop through all ingredients of the type
        for (Object ingredient : ingredientManager.getAllIngredients(itemStackType)) {
            checked++;
            if (checked % 1000 == 0) {
                ModLogger.debug("Checked {} ingredients so far...", checked);
            }
            
            try {
                String ingredientString = ingredient.toString().toLowerCase();
                
                // Check if the ingredient string contains our item name
                if (ingredientString.contains(itemName.toLowerCase())) {
                    Optional<?> typedIngredient = ingredientManager.createTypedIngredient(ingredient);
                    if (typedIngredient.isPresent()) {
                        ModLogger.info("Found ingredient by name match: {} in {}", itemName, ingredientString);
                        return (ITypedIngredient<?>) typedIngredient.get();
                    }
                }
            } catch (Exception e) {
                // Skip problematic ingredients
                continue;
            }
        }
        
        ModLogger.info("Checked {} ingredients but found no match for: {}", checked, itemName);
        return null;
    }
    
    /**
     * Find an item by a partial name match
     */
    private ITypedIngredient<?> findItemByNamePart(String namePart, IIngredientManager ingredientManager) {
        // Get the ItemStack ingredient type
        IIngredientType<?> itemStackType;
        try {
            Optional<IIngredientType<?>> optionalType = ingredientManager.getIngredientTypeForUid("item_stack");
            if (optionalType.isPresent()) {
                itemStackType = optionalType.get();
            } else {
                itemStackType = getItemStackIngredientType(ingredientManager);
            }
        } catch (Exception e) {
            ModLogger.error("Failed to get ItemStack ingredient type", e);
            return null;
        }
        
        ModLogger.info("Searching all ingredients for partial match: {}", namePart);
        int checked = 0;
        
        // Loop through all ingredients of the type
        for (Object ingredient : ingredientManager.getAllIngredients(itemStackType)) {
            checked++;
            
            try {
                String ingredientString = ingredient.toString().toLowerCase();
                
                // Check if the ingredient string contains our item name part
                if (ingredientString.contains(namePart.toLowerCase())) {
                    Optional<?> typedIngredient = ingredientManager.createTypedIngredient(ingredient);
                    if (typedIngredient.isPresent()) {
                        ModLogger.info("Found ingredient by partial name match: {} in {}", namePart, ingredientString);
                        return (ITypedIngredient<?>) typedIngredient.get();
                    }
                }
            } catch (Exception e) {
                // Skip problematic ingredients
                continue;
            }
        }
        
        ModLogger.info("Checked {} ingredients but found no partial match for: {}", checked, namePart);
        return null;
    }
    
    /**
     * Simplify item name to improve matching chances
     */
    private String simplifyItemName(String itemName) {
        // Remove any numbers and special characters
        StringBuilder simplified = new StringBuilder();
        for (char c : itemName.toCharArray()) {
            if (Character.isLetter(c) || c == '_') {
                simplified.append(c);
            }
        }
        return simplified.toString();
    }

    @Override
    public List<IIngredient> getIngredientsForKeys(List<String> bookmarkKeys) {
        // Special optimize case for empty list
        if (bookmarkKeys.isEmpty()) {
            return Collections.emptyList();
        }

        // Start timing for performance logging
        long startTime = System.currentTimeMillis();

        List<IIngredient> result = new ArrayList<>();
        List<String> keysToProcess = new ArrayList<>();

        // First check which keys we already have in cache
        for (String key : bookmarkKeys) {
            if (keyToIngredientCache.containsKey(key)) {
                ITypedIngredient<?> cachedIngredient = keyToIngredientCache.get(key);
                if (cachedIngredient != null) {
                    // Convert ITypedIngredient to our unified Ingredient
                    result.add(new com.jeifolders.integration.ingredient.Ingredient(cachedIngredient));
                }
            } else {
                keysToProcess.add(key);
            }
        }

        // If all ingredients were in cache, return immediately
        if (keysToProcess.isEmpty()) {
            ModLogger.debug("All {} ingredients were retrieved from cache in {}ms", 
                result.size(), (System.currentTimeMillis() - startTime));
            return result;
        }

        // Process any keys not found in cache
        Optional<IIngredientManager> managerOpt = jeiRuntime.getIngredientManager();
        if (managerOpt.isEmpty()) {
            ModLogger.warn("JEI ingredient manager not available for ingredient lookup");
            return result;
        }
        
        IIngredientManager ingredientManager = managerOpt.get();

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
                // Convert to our unified Ingredient class
                result.add(new com.jeifolders.integration.ingredient.Ingredient(ingredient));
                // Add to cache
                keyToIngredientCache.put(key, ingredient);
            } else {
                // Cache null result to avoid repeated lookups
                keyToIngredientCache.put(key, null);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        ModLogger.debug("Retrieved {} ingredients ({} from cache, {} newly processed) in {}ms", 
            result.size(), cacheHits, (result.size() - cacheHits), totalTime);

        return result;
    }

    @Override
    public List<IIngredient> getCachedIngredientsForFolder(int folderId) {
        ModLogger.info("TRACKING: getCachedIngredientsForFolder called for folder ID {}", folderId);
        
        // Return the cached ingredients if available
        if (folderIngredientsCache.containsKey(folderId)) {
            ModLogger.info("TRACKING: Using cached ingredients for folder ID: {}, {} items", 
                folderId, folderIngredientsCache.get(folderId).size());
            
            // Convert cached ITypedIngredient objects to unified Ingredient objects
            List<IIngredient> result = new ArrayList<>();
            for (ITypedIngredient<?> typedIngredient : folderIngredientsCache.get(folderId)) {
                result.add(new com.jeifolders.integration.ingredient.Ingredient(typedIngredient));
            }
            return result;
        }
        
        // Otherwise, process and cache them
        FolderStorageService folderService = FolderStorageService.getInstance();
        Optional<com.jeifolders.data.Folder> folder = folderService.getFolder(folderId);
        if (folder.isEmpty()) {
            ModLogger.info("TRACKING: Folder with ID {} not found", folderId);
            return Collections.emptyList();
        }
        
        List<String> bookmarkKeys = folder.get().getBookmarkKeys();
        ModLogger.info("TRACKING: Processing {} bookmark keys for folder ID {} - Keys: {}", 
            bookmarkKeys.size(), folderId, bookmarkKeys);
        
        // Get typed ingredients from keys
        List<ITypedIngredient<?>> typedIngredients = new ArrayList<>();
        for (String key : bookmarkKeys) {
            ModLogger.info("TRACKING: Looking up ingredient for key: {}", key);
            
            // First check the cache
            if (keyToIngredientCache.containsKey(key)) {
                ITypedIngredient<?> cachedIngredient = keyToIngredientCache.get(key);
                if (cachedIngredient != null) {
                    typedIngredients.add(cachedIngredient);
                    ModLogger.info("TRACKING: Found cached ingredient for key: {}", key);
                } else {
                    ModLogger.info("TRACKING: Found null cached ingredient for key: {}", key);
                }
            } else {
                ModLogger.info("TRACKING: Key {} not found in cache, looking up ingredient", key);
                
                // Get the ingredient manager
                Optional<IIngredientManager> managerOpt = jeiRuntime.getIngredientManager();
                if (managerOpt.isEmpty()) {
                    ModLogger.error("TRACKING: JEI ingredient manager not available");
                    continue;
                }
                
                IIngredientManager ingredientManager = managerOpt.get();
                
                // Try to find the ingredient using various methods
                ITypedIngredient<?> ingredient = findIngredientByKey(key, ingredientManager);
                ModLogger.info("TRACKING: findIngredientByKey returned: {}", ingredient != null ? "found" : "null");
                
                if (ingredient != null) {
                    typedIngredients.add(ingredient);
                    keyToIngredientCache.put(key, ingredient);
                    ModLogger.info("TRACKING: Found ingredient for key: {}", key);
                } else {
                    // Try our enhanced lookup if the standard one fails
                    ingredientManager = managerOpt.get();
                    ingredient = findAnyIngredientMatch(key, ingredientManager);
                    ModLogger.info("TRACKING: findAnyIngredientMatch returned: {}", ingredient != null ? "found" : "null");
                    
                    if (ingredient != null) {
                        typedIngredients.add(ingredient);
                        keyToIngredientCache.put(key, ingredient);
                        ModLogger.info("TRACKING: Found ingredient for key using enhanced lookup: {}", key);
                    } else {
                        keyToIngredientCache.put(key, null); // Cache null result
                        ModLogger.info("TRACKING: Could not find ingredient for key: {}", key);
                    }
                }
            }
        }
        
        // Cache the processed ingredients
        folderIngredientsCache.put(folderId, new ArrayList<>(typedIngredients));
        ModLogger.info("TRACKING: Cached {} ingredients for folder ID: {}", typedIngredients.size(), folderId);
        
        // Convert to unified Ingredient objects for return
        List<IIngredient> result = new ArrayList<>();
        for (ITypedIngredient<?> typedIngredient : typedIngredients) {
            result.add(new com.jeifolders.integration.ingredient.Ingredient(typedIngredient));
        }
        ModLogger.info("TRACKING: Returning {} unified ingredients for folder ID {}", result.size(), folderId);
        return result;
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
        ModLogger.debug("Cleared all ingredient caches");
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
            String itemId = key.substring(colonIndex + 1);
            
            ModLogger.debug("Looking up ingredient - Type: {}, Item ID: {}", typeId, itemId);
            
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
                        ModLogger.debug("Using fallback method to get item_stack ingredient type");
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

            // First try the exact match approach
            ITypedIngredient<?> match = tryExactMatch(key, ingredientType, ingredientManager);
            if (match != null) {
                return match;
            }
            
            // If that fails, try a more flexible approach with just the item ID part
            match = tryFlexibleMatch(typeId, itemId, ingredientType, ingredientManager);
            if (match != null) {
                ModLogger.debug("Found ingredient using flexible matching for key: {}", key);
                return match;
            }
            
            // If flexible matching fails too, try fallback approaches for known item types
            if ("item_stack".equals(typeId)) {
                match = tryResourceLocationMatch(itemId, ingredientType, ingredientManager);
                if (match != null) {
                    ModLogger.debug("Found ingredient using ResourceLocation matching for key: {}", key);
                    return match;
                }
            }

            ModLogger.warn("No ingredient found with exact key: {}", key);
            return null;
        } catch (Exception e) {
            ModLogger.error("Error getting ingredient for key: {}", key, e);
            return null;
        }
    }
    
    /**
     * Try to find an ingredient using exact key matching
     */
    private ITypedIngredient<?> tryExactMatch(String fullKey, IIngredientType<?> ingredientType, IIngredientManager manager) {
        try {
            // Look through all ingredients of the specified type
            for (Object ingredient : manager.getAllIngredients(ingredientType)) {
                Optional<?> typedIngredient = manager.createTypedIngredient(ingredient);
                if (typedIngredient.isPresent()) {
                    ITypedIngredient<?> castedIngredient = (ITypedIngredient<?>) typedIngredient.get();
                    String ingredientKey = getKeyForIngredient(castedIngredient);
                    if (fullKey.equals(ingredientKey)) {
                        return castedIngredient;
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.debug("Exact match lookup failed: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Try a more flexible matching approach based on the item ID part of the key
     */
    private ITypedIngredient<?> tryFlexibleMatch(String typeId, String itemId, IIngredientType<?> ingredientType, IIngredientManager manager) {
        try {
            // Handle potential format changes in Minecraft versions by being flexible with matching
            for (Object ingredient : manager.getAllIngredients(ingredientType)) {
                Optional<?> typedIngredient = manager.createTypedIngredient(ingredient);
                if (typedIngredient.isPresent()) {
                    ITypedIngredient<?> castedIngredient = (ITypedIngredient<?>) typedIngredient.get();
                    
                    String ingredientKey = getKeyForIngredient(castedIngredient);
                    // Check if the key contains our item ID regardless of format
                    if (ingredientKey.contains(itemId)) {
                        return castedIngredient;
                    }
                    
                    // For item stacks, try additional matching with the toString representation
                    if ("item_stack".equals(typeId) && ingredient.toString().contains(itemId)) {
                        return castedIngredient;
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.debug("Flexible match lookup failed: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Special fallback for Minecraft items using ResourceLocation
     */
    private ITypedIngredient<?> tryResourceLocationMatch(String itemId, IIngredientType<?> ingredientType, IIngredientManager manager) {
        try {
            // For Minecraft items, use ResourceLocation-based matching
            // This is especially important as item representations can change between versions
            String minecraftId = itemId;
            if (!minecraftId.contains(":")) {
                // If the ID doesn't have a namespace, assume it's from minecraft
                minecraftId = "minecraft:" + minecraftId;
            }
            
            for (Object ingredient : manager.getAllIngredients(ingredientType)) {
                Optional<?> typedIngredient = manager.createTypedIngredient(ingredient);
                if (typedIngredient.isPresent()) {
                    ITypedIngredient<?> castedIngredient = (ITypedIngredient<?>) typedIngredient.get();
                    
                    // Try to match using toString or other methods that might contain the ResourceLocation
                    String ingString = ingredient.toString();
                    if (ingString.contains(minecraftId)) {
                        return castedIngredient;
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.debug("ResourceLocation match lookup failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Helper method to get the ItemStack ingredient type directly from the available types
     */
    private IIngredientType<?> getItemStackIngredientType(IIngredientManager ingredientManager) {
        // Look through all registered ingredient types to find the ItemStack type
        for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
            if (type.getIngredientClass().getSimpleName().equals("ItemStack")) {
                ModLogger.debug("Found ItemStack ingredient type: {}", type);
                return type;
            }
        }
        
        // If that fails, throw an exception - this should never happen in a normal Minecraft environment
        throw new IllegalStateException("Could not find ItemStack ingredient type in JEI");
    }
}