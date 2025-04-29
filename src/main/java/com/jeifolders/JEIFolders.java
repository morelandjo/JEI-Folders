package com.jeifolders;

import com.jeifolders.integration.JEIIntegration;
import com.jeifolders.ui.controllers.FolderUIController;
import com.jeifolders.ui.layout.FolderLayoutService;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.util.ModLogger;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.IEventBus;

@net.neoforged.fml.common.Mod(JEIFolders.MOD_ID)
public class JEIFolders {
    public static final String MOD_ID = "jeifolders";
    
    // Track whether data has been loaded this session to prevent double-loading
    private boolean dataLoaded = false;

    public JEIFolders(IEventBus modEventBus) {
        ModLogger.info("JEI Folders initializing");
        
        // Register setup methods
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
        // Register NeoForge events
        NeoForge.EVENT_BUS.addListener(this::onWorldLoad);
        NeoForge.EVENT_BUS.addListener(this::onWorldUnload);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        
        ModLogger.info("JEI Folders events registered");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModLogger.info("JEI Folders common setup");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ModLogger.info("Setting up JEI Folders client");
        
        // Initialize the layout service first
        FolderLayoutService.init();
        
        // Initialize the folder button system 
        FolderUIController.init();
        
        // Initialize JEI integration
        JEIIntegration.registerRuntimeAvailableCallback(jeiRuntime -> {
            ModLogger.info("JEI Runtime available, initializing JEI-Folders integration");
            // Pass the JEI runtime to our main controller
            FolderUIController.getInstance().setJeiRuntime(jeiRuntime);
        });
        
        ModLogger.info("Client setup complete");
    }

    /**
     * Helper method to load data when needed and avoid code duplication
     * 
     * @param source The source of the data load request for logging
     * @param shouldCheck Whether to check the dataLoaded flag before loading
     * @return True if data was loaded, false otherwise
     */
    private boolean loadDataIfNeeded(String source, boolean shouldCheck) {
        if (!shouldCheck || !dataLoaded) {
            ModLogger.debug("Loading folder data on {}", source);
            FolderStorageService.getInstance().loadData();
            dataLoaded = true;
            
            // Refresh UI after data load if system is initialized
            if (FolderUIController.isInitialized()) {
                FolderUIController.getInstance().refreshBookmarkDisplay();
            }
            return true;
        }
        return false;
    }

    private void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            loadDataIfNeeded("world load", true);
        }
    }

    private void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ModLogger.debug("Saving folder data on world unload");
            FolderStorageService.getInstance().saveData();
            dataLoaded = false;
        }
    }
    
    private void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        loadDataIfNeeded("player login", true);
    }
}
