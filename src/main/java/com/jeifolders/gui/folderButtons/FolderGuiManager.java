package com.jeifolders.gui.folderButtons;

import com.jeifolders.util.ModLogger;

import net.minecraft.client.Minecraft;
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
public class FolderGuiManager {
    private static FolderButtonSystem folderButton;
    
    public static void init() {
        NeoForge.EVENT_BUS.register(FolderGuiManager.class);
    }
    
    @SubscribeEvent
    public static void onGuiInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractContainerScreen) {
            ModLogger.debug("Adding folder button to GUI: {}", screen.getClass().getSimpleName());
            
            folderButton = new FolderButtonSystem();
            event.addListener(folderButton);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (folderButton != null && Minecraft.getInstance().screen instanceof AbstractContainerScreen) {
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
