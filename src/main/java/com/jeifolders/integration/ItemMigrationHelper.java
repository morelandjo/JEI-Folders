package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for migrating between different item formats across versions
 */
public class ItemMigrationHelper {
    // Singleton instance
    private static final ItemMigrationHelper INSTANCE = new ItemMigrationHelper();
    
    // Common patterns for item keys
    private static final Pattern MINECRAFT_ITEM_PATTERN = Pattern.compile("item_stack:minecraft:([a-z0-9_]+)");
    
    // Cache for known migrations to avoid constant lookups
    private final Map<String, String> knownMigrations = new HashMap<>();
    
    // Cache for discovered replacement keys
    private final Map<String, String> discoveredReplacements = new HashMap<>();
    
    private ItemMigrationHelper() {
        // Initialize known migrations map with common item format changes
        initKnownMigrations();
    }
    
    /**
     * Get the singleton instance
     */
    public static ItemMigrationHelper getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the known migrations map
     */
    private void initKnownMigrations() {
        // Some common item ID format changes between Minecraft versions
        // For example, some items might have changed formats between versions
        
        // These mappings can be expanded as needed when specific format changes are identified
        knownMigrations.put("item_stack:minecraft:oak_wood", "minecraft:oak_wood");
        knownMigrations.put("item_stack:minecraft:birch_wood", "minecraft:birch_wood");
        knownMigrations.put("item_stack:minecraft:spruce_wood", "minecraft:spruce_wood");
        knownMigrations.put("item_stack:minecraft:jungle_wood", "minecraft:jungle_wood");
        knownMigrations.put("item_stack:minecraft:acacia_wood", "minecraft:acacia_wood");
        knownMigrations.put("item_stack:minecraft:dark_oak_wood", "minecraft:dark_oak_wood");
        
        ModLogger.info("Initialized {} known item migrations", knownMigrations.size());
    }
    
    /**
     * Attempts to find a matching ingredient for a potentially outdated key format
     *
     * @param oldKey The original key that didn't find a match
     * @param ingredientManager JEI's ingredient manager
     * @return An optional containing a found match, or empty if no match was found
     */
    public Optional<ITypedIngredient<?>> findMatchingIngredient(String oldKey, IIngredientManager ingredientManager) {
        // First check if we have a known migration
        if (knownMigrations.containsKey(oldKey)) {
            String newKey = knownMigrations.get(oldKey);
            ModLogger.debug("Using known migration: {} -> {}", oldKey, newKey);
            
            // Try to find the ingredient with the new key format
            ITypedIngredient<?> result = findIngredientByNamePattern(newKey, ingredientManager);
            if (result != null) {
                return Optional.of(result);
            }
        }
        
        // Check if we've discovered this replacement before
        if (discoveredReplacements.containsKey(oldKey)) {
            String discoveredKey = discoveredReplacements.get(oldKey);
            ModLogger.debug("Using previously discovered replacement: {} -> {}", oldKey, discoveredKey);
            
            ITypedIngredient<?> result = findIngredientByNamePattern(discoveredKey, ingredientManager);
            if (result != null) {
                return Optional.of(result);
            }
        }
        
        // For Minecraft items, try to extract the item name and search more broadly
        Matcher matcher = MINECRAFT_ITEM_PATTERN.matcher(oldKey);
        if (matcher.matches()) {
            String itemName = matcher.group(1);
            ModLogger.debug("Extracted item name '{}' for broader search", itemName);
            
            // Try different common formats for Minecraft items
            ITypedIngredient<?> result = tryMultipleFormats(itemName, ingredientManager);
            if (result != null) {
                // Cache the discovery for future use
                String newKey = result.getType().getUid() + ":" + result.getIngredient().toString();
                discoveredReplacements.put(oldKey, newKey);
                ModLogger.info("Discovered new key format: {} -> {}", oldKey, newKey);
                return Optional.of(result);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Try multiple common formats for an item name to find a match
     */
    private ITypedIngredient<?> tryMultipleFormats(String itemName, IIngredientManager ingredientManager) {
        // Common format patterns to try
        String[] patterns = {
            "minecraft:" + itemName,
            itemName,
            "item_stack:" + itemName,
            "minecraft:item/" + itemName
        };
        
        for (String pattern : patterns) {
            ITypedIngredient<?> result = findIngredientByNamePattern(pattern, ingredientManager);
            if (result != null) {
                ModLogger.debug("Found match using pattern: {}", pattern);
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Find an ingredient by a name pattern
     */
    private ITypedIngredient<?> findIngredientByNamePattern(String pattern, IIngredientManager ingredientManager) {
        // Get all ingredient types
        for (IIngredientType<?> type : ingredientManager.getRegisteredIngredientTypes()) {
            // Only process ItemStack type for now (most common ingredient type)
            if (type.getIngredientClass().getSimpleName().equals("ItemStack")) {
                for (Object ingredient : ingredientManager.getAllIngredients(type)) {
                    String ingredientString = ingredient.toString().toLowerCase();
                    if (ingredientString.contains(pattern.toLowerCase())) {
                        Optional<?> typedIngredient = ingredientManager.createTypedIngredient(ingredient);
                        if (typedIngredient.isPresent()) {
                            return (ITypedIngredient<?>) typedIngredient.get();
                        }
                    }
                }
            }
        }
        return null;
    }
}