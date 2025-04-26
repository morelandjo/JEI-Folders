package com.jeifolders.integration;

import com.jeifolders.JEIFolders;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.gui.common.ExclusionHandler;
import com.jeifolders.gui.common.IngredientDragManager;
import com.jeifolders.gui.controller.FolderUIController;
import com.jeifolders.gui.interaction.IngredientDropTarget;
import com.jeifolders.gui.view.layout.FolderRenderingManager;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main JEI plugin class for JEI-Folders integration.
 * Acts as the entry point for JEI integration.
 */
@JeiPlugin
public class JEIIntegration implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(JEIFolders.MOD_ID, "jei_plugin");
    
    // Access the services through the factory
    private final JEIService jeiService = JEIIntegrationFactory.getJEIService();
    
    
    // Static instance of the exclusion handler
    private static final ExclusionHandler exclusionHandler = new ExclusionHandler();

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Register our exclusion handler using the JEI-specific wrapper
        registration.addGlobalGuiHandler(new JEIExclusionHandler(exclusionHandler));

        // Register container handler to add folder areas to exclusion zones
        // Use raw types with unchecked conversion to match JEI's API expectations
        @SuppressWarnings("unchecked")
        Class<AbstractContainerScreen<?>> screenClass = (Class<AbstractContainerScreen<?>>) (Class<?>) AbstractContainerScreen.class;
        registration.addGuiContainerHandler(screenClass, new FolderAreaContainerHandler<>());
        
        // Register ghost ingredient handler for folder drag and drop
        registration.addGhostIngredientHandler(screenClass, new FolderGhostIngredientHandler<>());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        ModLogger.info("JEI runtime available, initializing");
        
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
        ModLogger.info("Initializing JEI runtime");
        
        // Set the runtime in the JEIService
        jeiService.setJeiRuntime(runtime);
        
        // Request data to be loaded now that JEI is available
        FolderStorageService.getInstance().loadData();
        
        // Force a UI refresh to make folders visible immediately
        Minecraft.getInstance().execute(() -> {
            if (FolderUIController.isInitialized()) {
                ModLogger.info("JEI runtime available - forcing folder UI refresh");
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
        return JEIIntegrationFactory.getJEIService().getJeiRuntime().isPresent();
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
        // Wrap the IJeiRuntime consumer in an Object consumer
        Consumer<Object> wrappedCallback = obj -> {
            if (obj instanceof IJeiRuntime jeiRuntime) {
                callback.accept(jeiRuntime);
            } else {
                ModLogger.error("Runtime object is not an IJeiRuntime: {}", 
                    obj != null ? obj.getClass().getName() : "null");
            }
        };
        
        JEIIntegrationFactory.getJEIService().registerRuntimeCallback(wrappedCallback);
    }

    public static void setJeiRuntime(IJeiRuntime runtime) {
        JEIIntegrationFactory.getJEIService().setJeiRuntime(runtime);
    }

    public static Optional<IJeiRuntime> getJeiRuntime() {
        Optional<Object> runtimeObj = JEIIntegrationFactory.getJEIService().getJeiRuntime();
        if (runtimeObj.isEmpty()) {
            return Optional.empty();
        }
        
        Object obj = runtimeObj.get();
        if (obj instanceof IJeiRuntime jeiRuntime) {
            return Optional.of(jeiRuntime);
        } else {
            ModLogger.error("JEI runtime is not of expected type: {}", 
                obj != null ? obj.getClass().getName() : "null");
            return Optional.empty();
        }
    }

    public static void setDraggedIngredient(ITypedIngredient<?> ingredient) {
        JEIIntegrationFactory.getJEIService().setDraggedIngredient(ingredient);
    }

    public static void clearDraggedIngredient() {
        JEIIntegrationFactory.getJEIService().clearDraggedIngredient();
    }

    public static Optional<ITypedIngredient<?>> getDraggedIngredient() {
        Optional<TypedIngredient> typedIngredientOpt = JEIIntegrationFactory.getJEIService().getDraggedIngredient();
        if (typedIngredientOpt.isEmpty()) {
            return Optional.empty();
        }
        
        TypedIngredient typedIngredient = typedIngredientOpt.get();
        Object wrappedObj = typedIngredient.getWrappedIngredient();
        
        if (wrappedObj instanceof ITypedIngredient<?> jeiTypedIngredient) {
            return Optional.of(jeiTypedIngredient);
        } else {
            ModLogger.error("Dragged ingredient is not of expected type: {}", 
                wrappedObj != null ? wrappedObj.getClass().getName() : "null");
            return Optional.empty();
        }
    }
    
    public static void setActuallyDragging(boolean isDragging) {
        JEIIntegrationFactory.getJEIService().setActuallyDragging(isDragging);
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
        public List<Rect2i> getGuiExtraAreas(T containerScreen) {
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
     * This implementation completely prevents targets from being created when just hovering
     */
    private static class FolderGhostIngredientHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
        // Track if we're in an actual drag (not just hover) using mouse state
        private boolean mouseButtonDown = false;
        
        @Override
        public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            
            // Important: We're completely ignoring the doStart parameter from JEI
            // and using our own mouse tracking to determine if this is a drag or just hover
            
            // Check the mouse state directly instead of relying on JEI's doStart
            boolean leftMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), 
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            
            if (leftMouseDown) {
                // Left mouse button is down, so this is an actual drag operation
                mouseButtonDown = true;
                
                // Log the actual drag operation
                ModLogger.debug("[HOVER-FIX] Actual drag detected, mouse button is down");
                
                // Set the dragged ingredient for the actual drag
                JEIIntegrationFactory.getJEIService().setDraggedIngredient(ingredient);
                JEIIntegrationFactory.getJEIService().setActuallyDragging(true);
                
                // Now add the targets since this is an actual drag
                if (FolderUIController.isInitialized()) {
                    FolderUIController folderButton = FolderUIController.getInstance();
                    
                    // Add targets for folder buttons
                    folderButton.getFolderButtons().forEach(folderRowButton -> {
                        targets.add(createFolderTarget(folderRowButton, folderButton, ingredient));
                    });

                    // Add target for bookmark display area if available
                    if (folderButton.isBookmarkAreaAvailable()) {
                        Rect2i bookmarkArea = folderButton.getBookmarkDisplayArea();
                        targets.add(createBookmarkAreaTarget(bookmarkArea, folderButton, ingredient));
                    }
                }
            } else if (mouseButtonDown) {
                // Mouse button was down but now it's up - reset the state
                mouseButtonDown = false;
                JEIIntegrationFactory.getJEIService().setActuallyDragging(false);
                JEIIntegrationFactory.getJEIService().clearDraggedIngredient();
                ModLogger.debug("[HOVER-FIX] Mouse button released, drag operation ended");
            } else {
                // This is just a hover, not a drag - don't return any targets
                ModLogger.debug("[HOVER-FIX] This is just a hover (not a drag), not returning any targets");
            }
            
            return targets;
        }

        private <I> Target<I> createFolderTarget(
                com.jeifolders.gui.view.buttons.FolderButton folderRowButton,
                FolderUIController folderButton,
                ITypedIngredient<I> ingredient) {

            return new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(
                            folderRowButton.getX(),
                            folderRowButton.getY(),
                            folderRowButton.getWidth(),
                            folderRowButton.getHeight()
                    );
                }

                @Override
                public void accept(I ingredientObj) {
                    ModLogger.debug("[HOVER-FIX] Ingredient dropped on folder: {}", folderRowButton.getFolder().getName());
                    folderButton.handleIngredientDrop(
                            folderRowButton.getX() + folderRowButton.getWidth() / 2,
                            folderRowButton.getY() + folderRowButton.getHeight() / 2,
                            ingredient
                    );
                }
            };
        }

        private <I> Target<I> createBookmarkAreaTarget(
                Rect2i bookmarkArea,
                FolderUIController folderButton,
                ITypedIngredient<I> ingredient) {

            return new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return bookmarkArea;
                }

                @Override
                public void accept(I ingredientObj) {
                    ModLogger.debug("[HOVER-FIX] Ingredient dropped on bookmark display");
                    
                    // We need to pass the ingredientObj (which is the actual Minecraft item)
                    // instead of the ITypedIngredient wrapper
                    folderButton.handleIngredientDrop(
                            bookmarkArea.getX() + bookmarkArea.getWidth() / 2,
                            bookmarkArea.getY() + bookmarkArea.getHeight() / 2,
                            ingredientObj
                    );
                }
            };
        }

        @Override
        public void onComplete() {
            ModLogger.debug("[HOVER-FIX] Ghost ingredient drag completed");
            mouseButtonDown = false;
            JEIIntegrationFactory.getJEIService().setActuallyDragging(false);
            JEIIntegrationFactory.getJEIService().clearDraggedIngredient();
        }
    }
}