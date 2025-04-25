package com.jeifolders;

import com.jeifolders.integration.JEIIntegration;
import com.jeifolders.data.FolderDataService;
import com.jeifolders.gui.folderButtons.FolderButtonSystem;
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
        
        // Initialize the folder button system (which internally initializes the rendering manager)
        FolderButtonSystem.init();
        
        // Initialize JEI integration
        JEIIntegration.registerRuntimeAvailableCallback(jeiRuntime -> {
            ModLogger.info("JEI Runtime available, initializing JEI-Folders integration");
            // Pass the JEI runtime to our main controller
            FolderButtonSystem.getInstance().setJeiRuntime(jeiRuntime);
        });
        
        ModLogger.info("Client setup complete");
    }

    private void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide() && !dataLoaded) {
            ModLogger.debug("Loading folder data on world load");
            FolderDataService.getInstance().loadData();
            dataLoaded = true;
            
            // Refresh UI after data load if system is initialized
            if (FolderButtonSystem.isInitialized()) {
                FolderButtonSystem.getInstance().refreshBookmarkDisplay();
            }
        }
    }

    private void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ModLogger.debug("Saving folder data on world unload");
            FolderDataService.getInstance().saveData();
            dataLoaded = false;
        }
    }
    
    private void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!dataLoaded) {
            ModLogger.debug("Loading folder data on player login");
            FolderDataService.getInstance().loadData();
            dataLoaded = true;
            
            // Refresh UI after data load if system is initialized
            if (FolderButtonSystem.isInitialized()) {
                FolderButtonSystem.getInstance().refreshBookmarkDisplay();
            }
        }
    }
}
