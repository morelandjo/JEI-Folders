package com.jeifolders;

import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.api.JEIIntegrationAPI;
import com.jeifolders.integration.api.JEIIntegrationService;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.ui.keybinds.KeyBindingManager;
import com.jeifolders.util.ModLogger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;

/**
 * Main mod class for JEI-Folders.
 */
@Mod(JEIFolders.MOD_ID)
public class JEIFolders {
    public static final String MOD_ID = "jeifolders";
    private static final Logger LOGGER = LogUtils.getLogger();

    public JEIFolders(IEventBus modEventBus) {
        // Register for mod setup events
        modEventBus.addListener(this::clientSetup);
        
        // Initialize key bindings
        KeyBindingManager.initialize(modEventBus);
        
        // Initialize UI controller early to ensure it's ready before JEI initializes
        FolderUIController.init();
        
        LOGGER.info("JEI-Folders initialized");
    }

    /**
     * Client setup handler.
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        // Initialize data services first
        FolderStorageService.getInstance().initialize();
        
        // Re-initialize UI controller to ensure it's properly set up
        FolderUIController controller = FolderUIController.getInstance();
        controller.initialize();
        
        // Make sure the folders are visible by default
        controller.getUIStateManager().setFoldersVisible(true);
        
        // Check for JEI runtime
        JEIIntegrationService integrationService = JEIIntegrationAPI.getIntegrationService();
        
        if (integrationService.isJeiRuntimeAvailable()) {
            ModLogger.info("JEI runtime already available during setup, initializing folders");
            FolderStorageService.getInstance().loadData();
            initializeWithJEI();
        } else {
            ModLogger.info("JEI runtime not yet available, folders will be initialized when JEI loads");
            integrationService.registerRuntimeCallback(runtime -> {
                ModLogger.info("JEI runtime now available, initializing folders");
                // Perform a full initialization sequence now that JEI is ready
                initializeWithJEI();
            });
        }
    }
    
    /**
     * Performs a complete initialization sequence with JEI runtime
     */
    private void initializeWithJEI() {
        try {
            // Ensure data is loaded
            FolderStorageService.getInstance().loadData();
            
            // Force a complete UI controller re-initialization
            FolderUIController controller = FolderUIController.getInstance();
            
            // Ensure folder visibility is set
            controller.getUIStateManager().setFoldersVisible(true);
            
            // First clean up any existing resources
            controller.cleanup();
            
            // Then create new display components
            controller.createDisplayComponents();
            
            // Finally rebuild all folders with their contents
            controller.rebuildFolders();
            
            ModLogger.info("JEI-Folders successfully initialized with JEI runtime");
        } catch (Exception e) {
            ModLogger.error("Failed to initialize JEI-Folders with JEI runtime", e);
        }
    }
}
