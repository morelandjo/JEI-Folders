package com.jeifolders.integration.ingredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;

/**
 * Unified manager for all ingredient-related operations.
 * Consolidates functionality previously spread across multiple ingredient service classes.
 */
public class IngredientManager {
    private IIngredientManager jeiIngredientManager;
    private boolean isInitialized = false;
    
    // Singleton instance
    private static IngredientManager instance;
    
    /**
     * Get the singleton instance of the IngredientManager
     * 
     * @return The singleton instance
     */
    public static IngredientManager getInstance() {
        if (instance == null) {
            instance = new IngredientManager();
        }
        return instance;
    }
    
    private IngredientManager() {
        // Private constructor to enforce singleton pattern
    }
    
    /**
     * Initialize the manager with JEI's ingredient manager
     * 
     * @param ingredientManager JEI's ingredient manager
     */
    public void initialize(IIngredientManager ingredientManager) {
        this.jeiIngredientManager = ingredientManager;
        this.isInitialized = true;
        ModLogger.debug("IngredientManager initialized with JEI's ingredient manager");
    }
    
    /**
     * Check if the manager has been initialized
     * 
     * @return True if initialized, false otherwise
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Get the JEI ingredient manager if available
     */
    public Optional<IIngredientManager> getIngredientManager() {
        return Optional.ofNullable(jeiIngredientManager);
    }
    
    /**
     * Convert a JEI ITypedIngredient to our unified Ingredient with a specific key
     * 
     * @param typedIngredient The JEI typed ingredient
     * @param key The ingredient key to use
     * @return The unified ingredient with the specified key
     */
    public IIngredient createIngredient(ITypedIngredient<?> typedIngredient, String key) {
        return new Ingredient(typedIngredient, key);
    }
    
    /**
     * Convert a JEI ITypedIngredient to our unified Ingredient
     * 
     * @param typedIngredient The JEI typed ingredient
     * @return The unified ingredient
     */
    public IIngredient createIngredient(ITypedIngredient<?> typedIngredient) {
        return new Ingredient(typedIngredient);
    }
    
    /**
     * Convert a raw object to our unified Ingredient
     * 
     * @param ingredient The raw ingredient object
     * @return The unified ingredient, or null if conversion failed
     */
    public IIngredient createIngredient(Object ingredient) {
        if (!isInitialized) {
            ModLogger.error("Cannot create ingredient before manager is initialized");
            return null;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Optional<ITypedIngredient<Object>> typedIngredient = jeiIngredientManager.createTypedIngredient(ingredient);
            return typedIngredient.map(this::createIngredient)
                    .orElseGet(() -> new Ingredient(ingredient));
        } catch (Exception e) {
            ModLogger.error("Failed to create ingredient: {}", e.getMessage());
            return new Ingredient(ingredient);
        }
    }
    
    /**
     * Get all ingredients of a specific type
     * 
     * @param ingredientType The ingredient type
     * @return A list of all ingredients of the specified type
     */
    public <T> List<IIngredient> getAllIngredientsOfType(IngredientType ingredientType) {
        if (!isInitialized) {
            ModLogger.error("Cannot get ingredients before manager is initialized");
            return Collections.emptyList();
        }
        
        List<IIngredient> result = new ArrayList<>();
        
        // Handle specific ingredient types
        switch (ingredientType) {
            case ITEM:
                // Get all items from JEI and convert them to our unified Ingredient
                try {
                    // Get the properly typed ingredient type
                    IIngredientType<net.minecraft.world.item.ItemStack> itemType = mezz.jei.api.constants.VanillaTypes.ITEM_STACK;
                    
                    // Get all ingredients of that type
                    jeiIngredientManager.getAllIngredients(itemType)
                        .forEach(itemStack -> {
                            try {
                                // This is properly typed now
                                Optional<ITypedIngredient<net.minecraft.world.item.ItemStack>> typedIngredient = 
                                    jeiIngredientManager.createTypedIngredient(itemType, itemStack);
                                typedIngredient.ifPresent(typed -> result.add(new Ingredient(typed)));
                            } catch (Exception e) {
                                ModLogger.error("Error creating typed ingredient for item: {}", e.getMessage());
                            }
                        });
                } catch (Exception e) {
                    ModLogger.error("Error getting item ingredients: {}", e.getMessage());
                }
                break;
                
            case FLUID:
                // Get all fluids from JEI and convert them to our unified Ingredient
                try {
                    // Fluid handling would go here
                } catch (Exception e) {
                    ModLogger.error("Error getting fluid ingredients: {}", e.getMessage());
                }
                break;
                
            default:
                ModLogger.debug("Requested unsupported ingredient type: {}", ingredientType);
                break;
        }
        
        return result;
    }
    
    /**
     * Get all available ingredients
     * 
     * @return A list of all ingredients
     */
    public List<IIngredient> getAllIngredients() {
        if (!isInitialized) {
            ModLogger.error("Cannot get all ingredients before manager is initialized");
            return Collections.emptyList();
        }
        
        List<IIngredient> allIngredients = new ArrayList<>();
        
        // Add items
        allIngredients.addAll(getAllIngredientsOfType(IngredientType.ITEM));
        
        // Add fluids
        allIngredients.addAll(getAllIngredientsOfType(IngredientType.FLUID));
        
        return allIngredients;
    }
    
    /**
     * Get JEI's ingredient manager
     * 
     * @return JEI's ingredient manager
     */
    public IIngredientManager getJeiIngredientManager() {
        if (!isInitialized) {
            ModLogger.error("Cannot get JEI ingredient manager before initialization");
            return null;
        }
        return jeiIngredientManager;
    }
    
    /**
     * Get the display name for an ingredient
     * 
     * @param ingredient The ingredient
     * @return The display name
     */
    public String getDisplayName(IIngredient ingredient) {
        if (!isInitialized) {
            return "Unknown";
        }
        
        try {
            if (ingredient.getTypedIngredient() != null) {
                // Use raw types to avoid type safety issues with generics
                @SuppressWarnings({"unchecked", "rawtypes"})
                IIngredientHelper helper = jeiIngredientManager.getIngredientHelper(ingredient.getTypedIngredient().getType());
                return helper.getDisplayName(ingredient.getTypedIngredient().getIngredient());
            } else if (ingredient.getRawIngredient() != null) {
                // Try to figure out the type from the raw ingredient
                Object rawIngredient = ingredient.getRawIngredient();
                for (IIngredientType<?> type : jeiIngredientManager.getRegisteredIngredientTypes()) {
                    // Use Class.isInstance instead of calling getType()
                    try {
                        // Access the ingredient class through reflection to handle API changes
                        // Add the proper cast to Class<?> to fix the type mismatch error
                        Class<?> ingredientClass = (Class<?>) type.getClass().getMethod("getIngredientClass").invoke(type);
                        if (ingredientClass != null && ingredientClass.isInstance(rawIngredient)) {
                            // Use raw types to avoid type safety issues
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            IIngredientHelper helper = jeiIngredientManager.getIngredientHelper(type);
                            return helper.getDisplayName(rawIngredient);
                        }
                    } catch (Exception e) {
                        // If the reflection approach fails, try a direct comparison of class names
                        try {
                            String typeName = type.toString();
                            String ingredientName = rawIngredient.getClass().getSimpleName();
                            if (typeName.toLowerCase().contains(ingredientName.toLowerCase())) {
                                @SuppressWarnings({"unchecked", "rawtypes"})
                                IIngredientHelper helper = jeiIngredientManager.getIngredientHelper(type);
                                return helper.getDisplayName(rawIngredient);
                            }
                        } catch (Exception ignored) {
                            // Continue to the next type if this approach fails
                        }
                    }
                }
            }
            return ingredient.toString();
        } catch (Exception e) {
            ModLogger.error("Failed to get display name for ingredient: {}", e.getMessage());
            return "Error";
        }
    }
    
    /**
     * Get the ingredient helper for a specific type
     * 
     * @param type The ingredient type
     * @return The ingredient helper
     */
    @SuppressWarnings("unchecked")
    private <T> IIngredientHelper<T> getHelper(IIngredientType<T> type) {
        return (IIngredientHelper<T>) jeiIngredientManager.getIngredientHelper(type);
    }
}