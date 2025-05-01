package com.jeifolders.integration;

import com.jeifolders.JEIFolders;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.data.IngredientCacheManager;
import com.jeifolders.integration.impl.JEIIngredientService;
import com.jeifolders.integration.ingredient.Ingredient;
import com.jeifolders.integration.ingredient.IngredientManager;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.ui.util.IngredientDragManager;
import com.jeifolders.ui.util.ExclusionHandler;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

/**
 * Main JEI plugin class for JEI-Folders integration.
 * Acts as the entry point for JEI integration.
 */
@JeiPlugin
public class JEIIntegration implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(JEIFolders.MOD_ID, "jei_plugin");
    
    // Access the services through the appropriate instance getters
    private final IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
    private final JEIRuntime jeiRuntime = JEIIntegrationFactory.getJEIRuntime();
    
    // Static instance of the exclusion handler
    private static final ExclusionHandler exclusionHandler = new ExclusionHandler();

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGuiHandlers(@Nonnull IGuiHandlerRegistration registration) {
        // Register our exclusion handler using the JEI-specific wrapper
        registration.addGlobalGuiHandler(new JEIExclusionHandler(exclusionHandler));

        // Register container handler to add folder areas to exclusion zones
        // Use raw types with unchecked conversion to match JEI's API expectations
        @SuppressWarnings("unchecked")
        Class<AbstractContainerScreen<?>> screenClass = (Class<AbstractContainerScreen<?>>) (Class<?>) AbstractContainerScreen.class;
        registration.addGuiContainerHandler(screenClass, new FolderAreaContainerHandler<>());
        
        // Register ghost ingredient handler for folder drag and drop
        registration.addGhostIngredientHandler(screenClass, new FolderGhostIngredientHandler<>(screenClass));
    }

    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime runtime) {
        // Initialize the runtime directly instead of using JeiRuntimeHelper
        initializeJeiRuntime(runtime);
        
        // Initialize components that specifically need runtime access
        IngredientDragManager.getInstance().setJeiRuntime(runtime);
        initializeFolderButton(runtime);
    }
    
    /**
     * Initializes the JEI runtime and sets up all necessary components.
     * This should be called when the JEI runtime becomes available.
     * 
     * @param runtime The JEI runtime instance
     */
    private void initializeJeiRuntime(IJeiRuntime runtime) {
        // Set the runtime in the JEIRuntime
        jeiRuntime.setJeiRuntime(runtime);
        
        // Request data to be loaded now that JEI is available
        FolderStorageService.getInstance().loadData();
        
        // Force a UI refresh to make folders visible immediately
        Minecraft.getInstance().execute(() -> {
            if (FolderUIController.isInitialized()) {
                FolderUIController.getInstance().rebuildFolders();
            }
        });
    }

    /**
     * Checks if the JEI runtime is currently available
     * 
     * @return true if JEI runtime is available, false otherwise
     */
    public static boolean isJeiRuntimeAvailable() {
        return JEIIntegrationFactory.getJEIRuntime().getJeiRuntime().isPresent();
    }

    private void initializeFolderButton(IJeiRuntime runtime) {
        // Get the folder button interface from FolderButtonSystem
        if (FolderUIController.isInitialized()) {
            FolderUIController folderButton = FolderUIController.getInstance();
            folderButton.setJeiRuntime(runtime);
            ModLogger.debug("JEI runtime provided to folder button");
        } else {
            ModLogger.warn("FolderButtonSystem not initialized when JEI runtime became available");
        }
    }

    /**
     * Registers a callback to be executed when the JEI runtime becomes available.
     */
    public static void registerRuntimeAvailableCallback(Consumer<IJeiRuntime> callback) {
        JEIIntegrationFactory.getJEIRuntime().registerRuntimeCallback(callback);
    }

    public static void setJeiRuntime(IJeiRuntime runtime) {
        JEIIntegrationFactory.getJEIRuntime().setJeiRuntime(runtime);
    }

    public static Optional<IJeiRuntime> getJeiRuntime() {
        return JEIIntegrationFactory.getJEIRuntime().getJeiRuntime();
    }

    public static void setDraggedIngredient(ITypedIngredient<?> ingredient) {
        JEIIntegrationFactory.getJEIRuntime().setDraggedIngredient(ingredient);
    }

    public static void clearDraggedIngredient() {
        JEIIntegrationFactory.getJEIRuntime().clearDraggedIngredient();
    }

    public static Optional<ITypedIngredient<?>> getDraggedIngredient() {
        Optional<Ingredient> unifiedIngredientOpt = JEIIntegrationFactory.getJEIRuntime().getDraggedIngredient();
        if (unifiedIngredientOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Ingredient unifiedIngredient = unifiedIngredientOpt.get();
        ITypedIngredient<?> typedIngredient = unifiedIngredient.getTypedIngredient();
        
        if (typedIngredient != null) {
            return Optional.of(typedIngredient);
        } else {
            ModLogger.error("Unified ingredient does not have a valid typed ingredient");
            return Optional.empty();
        }
    }
    
    public static void setActuallyDragging(boolean isDragging) {
        JEIIntegrationFactory.getJEIRuntime().setActuallyDragging(isDragging);
    }

    /**
     * Gets the shared exclusion handler instance
     */
    public static ExclusionHandler getExclusionHandler() {
        return exclusionHandler;
    }

    /**
     * Container handler specifically for folder areas
     */
    private static class FolderAreaContainerHandler<T extends AbstractContainerScreen<?>> implements IGuiContainerHandler<T> {
        @Override
        @Nonnull
        public List<Rect2i> getGuiExtraAreas(@Nonnull T containerScreen) {
            List<Rect2i> areas = new ArrayList<>();

            // Add the folder button exclusion zone if available
            if (FolderUIController.lastDrawnArea.getWidth() > 0 && FolderUIController.lastDrawnArea.getHeight() > 0) {
                areas.add(FolderUIController.lastDrawnArea);
            }

            return areas;
        }
    }

    /**
     * Ghost ingredient handler specifically for folder interactions
     */
    private static class FolderGhostIngredientHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
        private final Class<T> screenClass;
        
        public FolderGhostIngredientHandler(Class<T> screenClass) {
            this.screenClass = screenClass;
        }
        
        @Override
        @Nonnull
        public <I> List<Target<I>> getTargetsTyped(@Nonnull T screen, @Nonnull ITypedIngredient<I> ingredient, boolean doStart) {
            // Get the controller for the current screen
            FolderUIController controller = FolderUIController.getInstance();
            if (controller == null || !controller.isBookmarkAreaAvailable()) {
                return Collections.emptyList();
            }
            
            // Create targets for all folder buttons and the active bookmark display
            List<Target<I>> targets = new ArrayList<>();
            
            // Add targets for folder buttons
            controller.getFolderButtons().forEach(button -> {
                targets.add(createFolderTarget(button, controller, ingredient));
            });
            
            // Add target for the bookmark area if present
            Rect2i bookmarkArea = controller.getBookmarkDisplayArea();
            if (bookmarkArea != null) {
                targets.add(createBookmarkAreaTarget(bookmarkArea, controller, ingredient));
            }
            
            return targets;
        }
        
        @Override
        public void onComplete() {
            // Nothing to do here
        }
        
        private <I> Target<I> createFolderTarget(
                com.jeifolders.ui.components.buttons.FolderButton button,
                FolderUIController controller,
                ITypedIngredient<I> ingredient) {

            return new Target<I>() {
                @Override
                public Rect2i getArea() {
                    // Use button's area directly instead of creating a new Rect2i
                    return new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight());
                }

                @Override
                public void accept(I ingredientObj) {
                    ModLogger.debug("[HOVER-FIX] Ingredient dropped on folder: {}", button.getFolder().getName());
                    
                    // Get the center of the button for the drop position
                    int centerX = button.getX() + button.getWidth() / 2;
                    int centerY = button.getY() + button.getHeight() / 2;
                    
                    // Create and pass the unified ingredient to the controller
                    Ingredient unifiedIngredient = IngredientManager.getInstance().createIngredient(ingredient);
                    controller.handleIngredientDrop(centerX, centerY, unifiedIngredient);
                }
            };
        }
        
        private <I> Target<I> createBookmarkAreaTarget(
                Rect2i bookmarkArea,
                FolderUIController controller,
                ITypedIngredient<I> ingredient) {
                
            return new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return bookmarkArea;
                }
                
                @Override
                public void accept(I ingredientObj) {
                    ModLogger.debug("[HOVER-FIX] Ingredient dropped on bookmark display");
                    
                    // Calculate center of bookmark area for better drop accuracy
                    int centerX = bookmarkArea.getX() + bookmarkArea.getWidth() / 2;
                    int centerY = bookmarkArea.getY() + bookmarkArea.getHeight() / 2;
                    
                    // Create and pass the unified ingredient to the controller
                    Ingredient unifiedIngredient = IngredientManager.getInstance().createIngredient(ingredient);
                    controller.handleIngredientDrop(centerX, centerY, unifiedIngredient);
                }
            };
        }
    }
}