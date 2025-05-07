package com.jeifolders.integration.core;

import com.jeifolders.JEIFolders;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.JEIIntegrationService;
import com.jeifolders.integration.handlers.FolderAreaContainerHandler;
import com.jeifolders.integration.handlers.FolderGhostIngredientHandler;
import com.jeifolders.integration.handlers.GhostIngredientHandler;
import com.jeifolders.integration.handlers.JEIExclusionHandler;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.ui.util.UiExclusionHandler;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Main JEI plugin class for JEI-Folders integration.
 * Acts as the entry point for JEI integration.
 */
@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(JEIFolders.MOD_ID, "jei_plugin");
    
    // Access the JEI runtime directly using the unified class
    private final JEIIntegrationService integrationService = JEIIntegrationAPI.getIntegrationService();
    
    // Static instance of the exclusion handler
    private static final UiExclusionHandler exclusionHandler = new UiExclusionHandler();

    @Override
    @Nonnull
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerGuiHandlers(@Nonnull IGuiHandlerRegistration registration) {
        // Register our exclusion handler using the JEI-specific wrapper from handlers package
        registration.addGlobalGuiHandler(new JEIExclusionHandler(exclusionHandler));

        // Register container handler to add folder areas to exclusion zones
        // Use raw types with unchecked conversion to match JEI's API expectations
        @SuppressWarnings("unchecked")
        Class<AbstractContainerScreen<?>> screenClass = (Class<AbstractContainerScreen<?>>) (Class<?>) AbstractContainerScreen.class;
        registration.addGuiContainerHandler(screenClass, new FolderAreaContainerHandler<>());
        
        // Register ghost ingredient handlers for folder drag and drop
        // Using both handlers to ensure compatibility during transition
        registration.addGhostIngredientHandler(screenClass, new GhostIngredientHandler<>());
        registration.addGhostIngredientHandler(screenClass, new FolderGhostIngredientHandler<>(screenClass));
    }

    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime runtime) {
        // Initialize the runtime through the service
        ((JEIRuntime) integrationService).setJeiRuntime(runtime);
        
        // Load data once JEI is available
        FolderStorageService.getInstance().loadData();
        
        // Force a UI refresh to make folders visible immediately
        Minecraft.getInstance().execute(() -> {
            if (FolderUIController.isInitialized()) {
                FolderUIController.getInstance().rebuildFolders();
            }
        });
        
        // Initialize the folder UI with the JEI runtime
        initializeFolderUI(runtime);
    }
    
    /**
     * Initialize the folder UI with the JEI runtime
     */
    private void initializeFolderUI(IJeiRuntime runtime) {
        if (FolderUIController.isInitialized()) {
            FolderUIController controller = FolderUIController.getInstance();
            // The controller interface needs to be updated - we'll fix that separately
            controller.rebuildFolders();
            ModLogger.debug("JEI runtime provided to folder UI controller");
        } else {
            ModLogger.warn("FolderUIController not initialized when JEI runtime became available");
        }
    }
    
    /**
     * Gets the shared exclusion handler instance
     */
    public static UiExclusionHandler getExclusionHandler() {
        return exclusionHandler;
    }
    
    /**
     * Registers a callback to be executed when the JEI runtime becomes available.
     */
    public static void registerRuntimeAvailableCallback(Consumer<IJeiRuntime> callback) {
        JEIIntegrationAPI.getIntegrationService().registerRuntimeCallback(callback);
    }

    /**
     * Checks if the JEI runtime is currently available
     * 
     * @return true if JEI runtime is available, false otherwise
     */
    public static boolean isJeiRuntimeAvailable() {
        return JEIIntegrationAPI.getIntegrationService().isJeiRuntimeAvailable();
    }
}