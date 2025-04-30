package com.jeifolders.integration;

import com.jeifolders.JEIFolders;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.ui.util.ExclusionHandler;
import com.jeifolders.ui.util.IngredientDragManager;
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
        
        // Set the runtime in the JEIService
        jeiService.setJeiRuntime(runtime);
        
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
     */
    private static class FolderGhostIngredientHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
        // Track if we're in an actual drag (not just hover) using mouse state
        private boolean mouseButtonDown = false;
        
        @Override
        public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            
            // If doStart is true, JEI is informing us that a drag operation has started
            // This parameter is essential for JEI's ghost ingredient system to work
            boolean dragStarting = doStart;
            
            // For additional precision, we also check mouse state directly
            Minecraft minecraft = Minecraft.getInstance();
            boolean leftMouseDown = minecraft.mouseHandler.isLeftPressed();
            
            // Detect start of drag through either doStart or mouse state
            if (dragStarting || leftMouseDown) {
                // This is either the start of a drag or an ongoing drag
                if (!mouseButtonDown) {
                    // First detection of this drag operation
                    mouseButtonDown = true;
                    ModLogger.debug("[DRAG-DEBUG] Ingredient drag detected, targets will be created");
                    
                    // Set the dragged ingredient for the actual drag
                    JEIIntegrationFactory.getJEIService().setDraggedIngredient(ingredient);
                    JEIIntegrationFactory.getJEIService().setActuallyDragging(true);
                }
                
                // Now add the targets for the drag operation
                if (FolderUIController.isInitialized()) {
                    FolderUIController controller = FolderUIController.getInstance();
                    
                    // Add targets for folder buttons
                    controller.getFolderButtons().forEach(buttonInstance -> {
                        targets.add(createFolderTarget(buttonInstance, controller, ingredient));
                    });

                    // Add target for bookmark display area if available
                    if (controller.isBookmarkAreaAvailable()) {
                        Rect2i bookmarkArea = controller.getBookmarkDisplayArea();
                        targets.add(createBookmarkAreaTarget(bookmarkArea, controller, ingredient));
                    }
                }
            } else if (mouseButtonDown) {
                // Mouse button was down but now it's up - reset the state
                mouseButtonDown = false;
                JEIIntegrationFactory.getJEIService().setActuallyDragging(false);
                JEIIntegrationFactory.getJEIService().clearDraggedIngredient();
                ModLogger.debug("[DRAG-DEBUG] Drag operation ended");
            }
            
            return targets;
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
                    
                    controller.handleIngredientDrop(centerX, centerY, ingredient);
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
                    // Return the bookmark area directly
                    return bookmarkArea;
                }

                @Override
                public void accept(I ingredientObj) {
                    ModLogger.debug("[HOVER-FIX] Ingredient dropped on bookmark display");
                    
                    // Calculate center of bookmark area for better drop accuracy
                    int centerX = bookmarkArea.getX() + bookmarkArea.getWidth() / 2;
                    int centerY = bookmarkArea.getY() + bookmarkArea.getHeight() / 2;
                    
                    // Pass the actual Minecraft item (ingredientObj) instead of the wrapper
                    controller.handleIngredientDrop(centerX, centerY, ingredientObj);
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