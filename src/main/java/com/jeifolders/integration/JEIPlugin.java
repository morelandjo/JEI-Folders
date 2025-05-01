package com.jeifolders.integration;

import com.jeifolders.JEIFolders;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.ui.util.ExclusionHandler;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Main JEI plugin class for JEI-Folders integration.
 * Acts as the entry point for JEI integration.
 */
@JeiPlugin
public class JEIPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(JEIFolders.MOD_ID, "jei_plugin");
    
    // Access the JEI runtime directly using the new unified class
    private final JEIRuntime jeiRuntime = JEIIntegrationFactory.getJEIRuntime();
    
    // Static instance of the exclusion handler
    private static final ExclusionHandler exclusionHandler = new ExclusionHandler();

    @Override
    @Nonnull
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
        // Now using the dedicated class for this functionality
        registration.addGhostIngredientHandler(screenClass, new FolderGhostIngredientHandler<>());
    }

    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime runtime) {
        // Initialize the runtime directly
        jeiRuntime.setJeiRuntime(runtime);
        
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
    public static ExclusionHandler getExclusionHandler() {
        return exclusionHandler;
    }
}