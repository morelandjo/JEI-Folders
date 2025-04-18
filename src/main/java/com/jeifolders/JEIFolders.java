package com.jeifolders;

import com.jeifolders.gui.FolderButton;
import com.jeifolders.integration.JEIIntegration;
import com.jeifolders.data.FolderManager;
import com.jeifolders.gui.FolderManagerGUI;
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
        ModLogger.info("JEI Folders mod initializing");
        
        // Register setup methods for modloading - use MOD event bus for these
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
        // Register event handlers for world loading/unloading
        NeoForge.EVENT_BUS.addListener(this::onWorldLoad);
        NeoForge.EVENT_BUS.addListener(this::onWorldUnload);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModLogger.debug("JEI Folders common setup");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ModLogger.info("JEI Folders client setup - initializing folder manager GUI");
        FolderManagerGUI.init();
        
        // Register with JEI runtime when available
        JEIIntegration.registerRuntimeAvailableCallback(jeiRuntime -> {
            ModLogger.info("JEI Runtime available, initializing JEI-Folders integration");
            // Any initialization that depends on JEI runtime
        });
    }

    private void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide() && !dataLoaded) {
            ModLogger.debug("Loading folder data on world load");
            FolderManager.getInstance().loadData();
            dataLoaded = true;
        }
    }

    private void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ModLogger.debug("Saving folder data on world unload");
            FolderManager.getInstance().saveData();
            dataLoaded = false;
        }
    }
    
    private void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!dataLoaded) {
            ModLogger.debug("Loading folder data on player login");
            FolderManager.getInstance().loadData();
            dataLoaded = true;
        }
    }
}
