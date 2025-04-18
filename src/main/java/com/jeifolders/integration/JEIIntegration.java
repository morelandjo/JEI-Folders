package com.jeifolders.integration;

import com.jeifolders.JEIFolders;
import com.jeifolders.gui.FolderButton;
import com.jeifolders.gui.FolderExclusionHandler;
import com.jeifolders.gui.FolderManagerGUI;
import com.jeifolders.gui.GlobalIngredientDragManager;
import com.jeifolders.gui.FolderButtonInterface;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

@JeiPlugin
public class JEIIntegration implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(JEIFolders.MOD_ID, "jei_plugin");
    private static IJeiRuntime jeiRuntime;
    private static final List<Consumer<IJeiRuntime>> runtimeCallbacks = new CopyOnWriteArrayList<>();
    private static IIngredientManager ingredientManager;
    private static ITypedIngredient<?> currentDraggedIngredient;
    private static final FolderExclusionHandler exclusionHandler = new FolderExclusionHandler();
    
    // This flag will track if an ingredient is actually being dragged
    private static boolean isActuallyDragging = false;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Register our exclusion handler
        registration.addGlobalGuiHandler(exclusionHandler);

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
        setJeiRuntime(runtime);

        // Initialize all components that need the JEI runtime
        GlobalIngredientDragManager.getInstance().setJeiRuntime(runtime);
        initializeFolderButton(runtime);

        // Notify any registered callbacks
        notifyRuntimeCallbacks(runtime);
    }

    private void initializeFolderButton(IJeiRuntime runtime) {
        FolderButtonInterface buttonInterface = FolderManagerGUI.getFolderButton();
        if (buttonInterface instanceof FolderButton folderButton) {
            folderButton.setJeiRuntime(runtime);
            ModLogger.debug("JEI runtime provided to folder button");
        }
    }

    private void notifyRuntimeCallbacks(IJeiRuntime runtime) {
        for (Consumer<IJeiRuntime> callback : runtimeCallbacks) {
            callback.accept(runtime);
        }
        runtimeCallbacks.clear();
    }

    /**
     * Registers a callback to be executed when the JEI runtime becomes available.
     */
    public static void registerRuntimeAvailableCallback(Consumer<IJeiRuntime> callback) {
        if (jeiRuntime != null) {
            // If runtime is already available, call the callback immediately
            callback.accept(jeiRuntime);
        } else {
            // Otherwise, save the callback for later execution
            runtimeCallbacks.add(callback);
        }
    }

    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        ingredientManager = runtime.getIngredientManager();
        ModLogger.debug("JEI runtime set in JEIIntegration");
        JEIIngredientManager.setIngredientManager(ingredientManager);
    }

    public static Optional<IJeiRuntime> getJeiRuntime() {
        return Optional.ofNullable(jeiRuntime);
    }

    public static void setDraggedIngredient(ITypedIngredient<?> ingredient) {
        currentDraggedIngredient = ingredient;
        ModLogger.debug("Set dragged ingredient: {}", ingredient);
    }

    public static void clearDraggedIngredient() {
        currentDraggedIngredient = null;
        isActuallyDragging = false;
        ModLogger.debug("Cleared dragged ingredient");
    }

    public static Optional<ITypedIngredient<?>> getDraggedIngredient() {
        // Add detailed logging to understand when this is called and what it returns
        if (currentDraggedIngredient != null) {
            ModLogger.debug("[HOVER-DEBUG] getDraggedIngredient called, ingredient present, isActuallyDragging={}", isActuallyDragging);
        }
        // Only return the dragged ingredient if we're actually dragging
        if (isActuallyDragging && currentDraggedIngredient != null) {
            return Optional.of(currentDraggedIngredient);
        }
        return Optional.empty();
    }
    
    public static void setActuallyDragging(boolean isDragging) {
        isActuallyDragging = isDragging;
        ModLogger.debug("Set actually dragging: {}", isDragging);
    }

    public static IIngredientManager getIngredientManager() {
        if (ingredientManager == null && jeiRuntime != null) {
            ingredientManager = jeiRuntime.getIngredientManager();
        }
        return ingredientManager;
    }

    /**
     * Container handler specifically for folder areas
     */
    private static class FolderAreaContainerHandler<T extends AbstractContainerScreen<?>> implements IGuiContainerHandler<T> {
        @Override
        public List<Rect2i> getGuiExtraAreas(T containerScreen) {
            List<Rect2i> areas = new ArrayList<>();

            // Add the folder button exclusion zone if available
            if (FolderButton.lastDrawnArea.getWidth() > 0 && FolderButton.lastDrawnArea.getHeight() > 0) {
                areas.add(FolderButton.lastDrawnArea);
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
                setDraggedIngredient(ingredient);
                setActuallyDragging(true);
                
                // Now add the targets since this is an actual drag
                FolderButtonInterface buttonInterface = FolderManagerGUI.getFolderButton();
                if (buttonInterface instanceof FolderButton folderButton) {
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
                setActuallyDragging(false);
                clearDraggedIngredient();
                ModLogger.debug("[HOVER-FIX] Mouse button released, drag operation ended");
            } else {
                // This is just a hover, not a drag - don't return any targets
                ModLogger.debug("[HOVER-FIX] This is just a hover (not a drag), not returning any targets");
            }
            
            return targets;
        }

        private <I> Target<I> createFolderTarget(
                com.jeifolders.gui.FolderRowButton folderRowButton,
                FolderButton folderButton,
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
                FolderButton folderButton,
                ITypedIngredient<I> ingredient) {

            return new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return bookmarkArea;
                }

                @Override
                public void accept(I ingredientObj) {
                    ModLogger.debug("[HOVER-FIX] Ingredient dropped on bookmark display");
                    folderButton.handleIngredientDrop(
                            bookmarkArea.getX() + bookmarkArea.getWidth() / 2,
                            bookmarkArea.getY() + bookmarkArea.getHeight() / 2,
                            ingredient
                    );
                }
            };
        }

        @Override
        public void onComplete() {
            ModLogger.debug("[HOVER-FIX] Ghost ingredient drag completed");
            mouseButtonDown = false;
            setActuallyDragging(false);
            clearDraggedIngredient();
        }
    }
}