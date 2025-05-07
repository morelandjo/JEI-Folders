package com.jeifolders.ui.keybinds;

import com.jeifolders.JEIFolders;
import com.jeifolders.util.ModLogger;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

/**
 * Manages keyboard input and key bindings for the JEI Folders mod.
 */
public class KeyBindingManager {
    private static final String KEY_CATEGORY = "key.categories." + JEIFolders.MOD_ID;
    private static boolean initialized = false;
    
    // Key bindings
    public static KeyMapping toggleFoldersKey;
    public static KeyMapping openCurrentFolderKey;
    
    // Binding listeners
    private static final List<Consumer<KeyMapping>> keyPressListeners = new ArrayList<>();
    
    /**
     * Initializes the key binding manager with the mod event bus
     * 
     * @param modEventBus The mod event bus
     */
    public static void initialize(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        
        // Register for key registration events
        modEventBus.addListener(EventPriority.NORMAL, false, 
            RegisterKeyMappingsEvent.class, KeyBindingManager::onRegisterKeyMappings);
        
        // Register for input events
        NeoForge.EVENT_BUS.register(KeyBindingManager.class);
        
        initialized = true;
        
        ModLogger.debug("KeyBindingManager initialized with mod event bus");
    }
    
    /**
     * Handles key mapping registration
     */
    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ModLogger.debug("Registering key bindings");
        
        // Create key bindings
        toggleFoldersKey = new KeyMapping(
            "key." + JEIFolders.MOD_ID + ".toggle_folders",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            KEY_CATEGORY
        );
        
        openCurrentFolderKey = new KeyMapping(
            "key." + JEIFolders.MOD_ID + ".open_current_folder",
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
        );
        
        // Register key bindings
        event.register(toggleFoldersKey);
        event.register(openCurrentFolderKey);
        
        ModLogger.debug("Key bindings registered");
    }
    
    /**
     * Registers a key press listener
     * 
     * @param listener The listener to register
     */
    public static void registerKeyPressListener(Consumer<KeyMapping> listener) {
        if (listener != null) {
            keyPressListeners.add(listener);
        }
    }
    
    /**
     * Checks if a key binding is currently pressed
     * 
     * @param keyMapping The key binding to check
     * @return true if the key binding is pressed
     */
    public static boolean isPressed(KeyMapping keyMapping) {
        return keyMapping != null && keyMapping.isDown();
    }
    
    /**
     * Handles key input events
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // Check for key presses and notify listeners
        if (toggleFoldersKey.consumeClick() || openCurrentFolderKey.consumeClick()) {
            // Determine which key was pressed
            KeyMapping pressedKey = null;
            if (toggleFoldersKey.consumeClick()) {
                pressedKey = toggleFoldersKey;
            } else if (openCurrentFolderKey.consumeClick()) {
                pressedKey = openCurrentFolderKey;
            }
            
            // Notify listeners
            if (pressedKey != null) {
                for (Consumer<KeyMapping> listener : keyPressListeners) {
                    try {
                        listener.accept(pressedKey);
                    } catch (Exception e) {
                        ModLogger.error("Error in key press listener: {}", e.getMessage(), e);
                    }
                }
            }
        }
    }
}