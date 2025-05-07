package com.jeifolders.integration.ingredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

import com.jeifolders.integration.api.IIngredient;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.IngredientService;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

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
    
    // Factory methods for common ingredient types
    
    /**
     * Create an ingredient from an ItemStack
     * 
     * @param itemStack The ItemStack to convert
     * @return The unified ingredient
     */
    public IIngredient createFromItemStack(ItemStack itemStack) {
        if (!isInitialized || itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        try {
            IIngredientType<ItemStack> itemType = mezz.jei.api.constants.VanillaTypes.ITEM_STACK;
            Optional<ITypedIngredient<ItemStack>> typedIngredient = 
                jeiIngredientManager.createTypedIngredient(itemType, itemStack);
            return typedIngredient.map(this::createIngredient)
                    .orElseGet(() -> new Ingredient(itemStack));
        } catch (Exception e) {
            ModLogger.error("Failed to create ingredient from ItemStack: {}", e.getMessage());
            return new Ingredient(itemStack);
        }
    }
    
    /**
     * Create an ingredient from a FluidStack
     * 
     * @param fluidStack The FluidStack to convert
     * @return The unified ingredient
     */
    public IIngredient createFromFluidStack(FluidStack fluidStack) {
        if (!isInitialized || fluidStack == null || fluidStack.isEmpty()) {
            return null;
        }
        
        try {
            IIngredientType<FluidStack> fluidType = mezz.jei.api.constants.VanillaTypes.FLUID_STACK;
            Optional<ITypedIngredient<FluidStack>> typedIngredient = 
                jeiIngredientManager.createTypedIngredient(fluidType, fluidStack);
            return typedIngredient.map(this::createIngredient)
                    .orElseGet(() -> new Ingredient(fluidStack));
        } catch (Exception e) {
            ModLogger.error("Failed to create ingredient from FluidStack: {}", e.getMessage());
            return new Ingredient(fluidStack);
        }
    }
    
    /**
     * Create an ingredient from a serialized CompoundTag
     * 
     * @param tag The CompoundTag containing serialized ingredient data
     * @return The unified ingredient, or null if deserialization failed
     */
    public IIngredient deserializeFromTag(CompoundTag tag) {
        if (!isInitialized || tag == null) {
            return null;
        }
        
        try {
            // Check what type of ingredient it is
            String typeStr = tag.getString("IngredientType");
            IngredientType type = IngredientType.valueOf(typeStr);
            
            switch (type) {
                case ITEM:
                    if (tag.contains("ItemStack")) {
                        ItemStack itemStack = ItemStack.of(tag.getCompound("ItemStack"));
                        return createFromItemStack(itemStack);
                    }
                    break;
                case FLUID:
                    if (tag.contains("FluidStack")) {
                        FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(tag.getCompound("FluidStack"));
                        return createFromFluidStack(fluidStack);
                    }
                    break;
                default:
                    ModLogger.error("Unknown ingredient type in tag: {}", typeStr);
                    break;
            }
            
            // If we reach here, fall back to trying to load by ID if possible
            if (tag.contains("RegistryId")) {
                ResourceLocation id = new ResourceLocation(tag.getString("RegistryId"));
                // Try to resolve from registry... implementation depends on specific registries
            }
            
            return null;
        } catch (Exception e) {
            ModLogger.error("Failed to deserialize ingredient from tag: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Serialize an ingredient to a CompoundTag
     * 
     * @param ingredient The ingredient to serialize
     * @return The CompoundTag containing serialized data, or null if serialization failed
     */
    public CompoundTag serializeToTag(IIngredient ingredient) {
        if (!isInitialized || ingredient == null) {
            return null;
        }
        
        try {
            CompoundTag tag = new CompoundTag();
            
            // Store the ingredient type
            tag.putString("IngredientType", ingredient.getType().name());
            
            // Handle different types of ingredients
            Object rawIngredient = ingredient.getRawIngredient();
            if (rawIngredient instanceof ItemStack) {
                ItemStack itemStack = (ItemStack) rawIngredient;
                CompoundTag itemTag = new CompoundTag();
                itemStack.save(itemTag);
                tag.put("ItemStack", itemTag);
                
                // Also store ID for easier lookup
                ResourceLocation id = itemStack.getItem().getRegistryName();
                if (id != null) {
                    tag.putString("RegistryId", id.toString());
                }
            } else if (rawIngredient instanceof FluidStack) {
                FluidStack fluidStack = (FluidStack) rawIngredient;
                CompoundTag fluidTag = new CompoundTag();
                fluidStack.writeToNBT(fluidTag);
                tag.put("FluidStack", fluidTag);
                
                // Also store ID for easier lookup
                ResourceLocation id = fluidStack.getFluid().getRegistryName();
                if (id != null) {
                    tag.putString("RegistryId", id.toString());
                }
            }
            
            return tag;
        } catch (Exception e) {
            ModLogger.error("Failed to serialize ingredient to tag: {}", e.getMessage());
            return null;
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

    /**
     * Gets the ingredient key for various ingredient types
     * 
     * @param ingredient The ingredient to get the key for
     * @return The key string or empty if unable to determine
     */
    public String getKeyForIngredient(Object ingredient) {
        if (ingredient == null || !isInitialized) {
            return "";
        }
        
        try {
            // Handle different types of ingredients
            if (ingredient instanceof IIngredient) {
                IIngredient unifiedIngredient = (IIngredient) ingredient;
                if (unifiedIngredient.getTypedIngredient() != null) {
                    return getKeyForTypedIngredient(unifiedIngredient.getTypedIngredient());
                } else if (unifiedIngredient.getRawIngredient() != null) {
                    return getKeyForRawIngredient(unifiedIngredient.getRawIngredient());
                }
            } else if (ingredient instanceof BookmarkIngredient) {
                return getKeyForTypedIngredient(((BookmarkIngredient)ingredient).getTypedIngredient());
            } else if (ingredient instanceof ITypedIngredient) {
                return getKeyForTypedIngredient((ITypedIngredient<?>)ingredient);
            } else {
                return getKeyForRawIngredient(ingredient);
            }
        } catch (Exception e) {
            ModLogger.error("Error getting key for ingredient: {}", e.getMessage(), e);
        }
        
        return "";
    }
    
    /**
     * Gets the key for a JEI typed ingredient
     * 
     * @param ingredient The typed ingredient
     * @return The key string
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String getKeyForTypedIngredient(ITypedIngredient<?> ingredient) {
        if (ingredient == null || !isInitialized) {
            return "";
        }
        
        try {
            // Get the helper for this ingredient type
            IIngredientHelper helper = jeiIngredientManager.getIngredientHelper(ingredient.getType());
            if (helper != null) {
                // Use the UidForIngredient as the key
                return helper.getUniqueId(ingredient.getIngredient()).toString();
            }
        } catch (Exception e) {
            ModLogger.error("Failed to get key for typed ingredient: {}", e.getMessage());
        }
        
        return "";
    }
    
    /**
     * Gets the key for a raw ingredient object
     * 
     * @param ingredient The raw ingredient object
     * @return The key string
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String getKeyForRawIngredient(Object ingredient) {
        if (ingredient == null || !isInitialized) {
            return "";
        }
        
        try {
            // Try to find the appropriate ingredient helper for this object
            for (IIngredientType<?> type : jeiIngredientManager.getRegisteredIngredientTypes()) {
                try {
                    Object ingredientClass = type.getClass().getMethod("getIngredientClass").invoke(type);
                    if (ingredientClass != null && ((Class<?>)ingredientClass).isInstance(ingredient)) {
                        // Found the right type, use its helper
                        IIngredientHelper helper = jeiIngredientManager.getIngredientHelper(type);
                        return helper.getUniqueId(ingredient).toString();
                    }
                } catch (Exception e) {
                    // Try next type
                }
            }
            
            // Create a typed ingredient from the raw ingredient as a fallback
            Optional<ITypedIngredient<Object>> typedIngredient = jeiIngredientManager.createTypedIngredient(ingredient);
            if (typedIngredient.isPresent()) {
                return getKeyForTypedIngredient(typedIngredient.get());
            }
        } catch (Exception e) {
            ModLogger.error("Failed to get key for raw ingredient: {}", e.getMessage());
        }
        
        return "";
    }
    
    /**
     * Compare two ingredients for equality based on their keys
     * 
     * @param a First ingredient
     * @param b Second ingredient
     * @return true if both ingredients have the same key
     */
    public boolean areIngredientsEqual(Object a, Object b) {
        if (a == null || b == null) {
            return false;
        }
        
        if (a == b) {
            return true;
        }
        
        String keyA = getKeyForIngredient(a);
        String keyB = getKeyForIngredient(b);
        
        return !keyA.isEmpty() && !keyB.isEmpty() && keyA.equals(keyB);
    }
    
    /**
     * Find an ingredient in a list based on key matching
     * 
     * @param ingredient The ingredient to find
     * @param list The list to search in
     * @return The found ingredient or empty if not found
     */
    public <T> Optional<T> findIngredient(Object ingredient, List<T> list) {
        if (ingredient == null || list == null || list.isEmpty()) {
            return Optional.empty();
        }
        
        String key = getKeyForIngredient(ingredient);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        
        return list.stream()
            .filter(Objects::nonNull)
            .filter(item -> {
                String itemKey = getKeyForIngredient(item);
                return !itemKey.isEmpty() && itemKey.equals(key);
            })
            .findFirst();
    }
    
    /**
     * Creates a combined, deduplicated list of ingredients
     * 
     * @param listA First list
     * @param listB Second list
     * @return Combined list with no duplicates
     */
    public <T> List<T> combineIngredientLists(List<T> listA, List<T> listB) {
        if (listA == null || listA.isEmpty()) {
            return listB != null ? new ArrayList<>(listB) : new ArrayList<>();
        }
        
        if (listB == null || listB.isEmpty()) {
            return new ArrayList<>(listA);
        }
        
        List<T> result = new ArrayList<>(listA);
        
        for (T item : listB) {
            if (item == null) {
                continue;
            }
            
            // Check if item already exists in result by ingredient key
            boolean exists = result.stream()
                .anyMatch(existingItem -> areIngredientsEqual(item, existingItem));
                
            if (!exists) {
                result.add(item);
            }
        }
        
        return result;
    }
    
    /**
     * Gets an ingredient for a specific bookmark key.
     * 
     * @param key The bookmark key to look up
     * @return The ingredient for the key, or empty if not found
     */
    public Optional<IIngredient> getIngredientForKey(String key) {
        if (key == null || key.isEmpty() || !isInitialized) {
            return Optional.empty();
        }
        
        try {
            // Loop through ingredient types and try to find a match
            for (IIngredientType<?> type : jeiIngredientManager.getRegisteredIngredientTypes()) {
                try {
                    @SuppressWarnings("unchecked")
                    IIngredientHelper<Object> helper = (IIngredientHelper<Object>) jeiIngredientManager.getIngredientHelper(type);
                    
                    // Try to parse the key and get the ingredient
                    Object rawIngredient = helper.getIngredientFromUniqueId(key);
                    if (rawIngredient != null) {
                        // Create a typed ingredient from the raw ingredient
                        @SuppressWarnings("unchecked")
                        Optional<ITypedIngredient<Object>> typedIngredient = jeiIngredientManager.createTypedIngredient(rawIngredient);
                        if (typedIngredient.isPresent()) {
                            // Create our unified ingredient
                            return Optional.of(createIngredient(typedIngredient.get()));
                        }
                    }
                } catch (Exception e) {
                    // Silently continue to next type
                }
            }
        } catch (Exception e) {
            ModLogger.error("Error getting ingredient for key {}: {}", key, e.getMessage());
        }
        
        return Optional.empty();
    }
}