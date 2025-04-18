package com.jeifolders.gui;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Manages frontend GUI elements for folders, including adding the FolderButton
 * to the GUI and providing access to it for other components.
 */
public class FolderManagerGUI {
    private static FolderButton folderButton;
    
    public static void init() {
        NeoForge.EVENT_BUS.register(FolderManagerGUI.class);
    }
    
    @SubscribeEvent
    public static void onGuiInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractContainerScreen) {
            ModLogger.debug("Adding folder button to GUI: {}", screen.getClass().getSimpleName());
            
            folderButton = new FolderButton();
            event.addListener(folderButton);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        // Update animations for all folder-related UI if the folder button exists
        if (folderButton != null) {
            folderButton.tick();
        }
    }
    
    /**
     * Gets the folder button from the current GUI
     */
    public static FolderButtonInterface getFolderButton() {
        return folderButton;
    }
}
