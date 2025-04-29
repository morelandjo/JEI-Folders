package com.jeifolders.data;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Handles resolving file paths and determining world folder names for the folder storage system.
 * Centralizes path resolution logic that was previously scattered in FolderStorageService.
 */
public class ConfigPathResolver {
    // Constants
    private static final String ALL_WORLDS_FOLDER = "all";
    private static final String DATA_FILENAME = "jeifolders.snbt";
    private static final String CONFIG_ROOT = "./config/jeifolders/";
    
    // Singleton instance
    private static ConfigPathResolver instance;
    
    // Class state
    private Path configDir;
    
    // Cache the last determined world name to avoid excessive determination
    private String cachedWorldName = null;
    private long worldNameDeterminedAt = 0;
    private static final long WORLD_NAME_CACHE_DURATION_MS = 30000; // 30 seconds
    
    // Track the last logged world folder to prevent duplicate log messages
    private String lastLoggedWorldFolder = null;
    
    /**
     * Private constructor for singleton pattern
     */
    private ConfigPathResolver() {
        // Initialize the config directory path
        configDir = Paths.get(CONFIG_ROOT);
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized ConfigPathResolver getInstance() {
        if (instance == null) {
            instance = new ConfigPathResolver();
        }
        return instance;
    }
    
    /**
     * Resolves the config path for a specified world name
     * 
     * @param worldName The world folder name
     * @return The resolved path for this world's config directory
     */
    public Path resolveWorldPath(String worldName) {
        return configDir.resolve(worldName);
    }
    
    /**
     * Resolves the path to the base configuration directory
     * 
     * @return The path to the base configuration directory
     */
    public Path resolveConfigRootPath() {
        return configDir;
    }
    
    /**
     * Sets up the base configuration directory
     * 
     * @return true if successful, false otherwise
     */
    public boolean setupBaseConfigDirectory() {
        try {
            // Create the config directory if it doesn't exist
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                ModLogger.debug("Created config directory: {}", configDir);
            }
            return true;
        } catch (IOException e) {
            ModLogger.error("Failed to set up base config directory: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the path to the data file for a specific world
     * 
     * @param worldName The world folder name
     * @return The file object for the data file
     */
    public File getDataFile(String worldName) {
        Path worldDir = resolveWorldPath(worldName);
        return worldDir.resolve(DATA_FILENAME).toFile();
    }
    
    /**
     * Ensures a world directory exists
     * 
     * @param worldName The world folder name
     * @return true if successful, false if an error occurs
     */
    public boolean ensureWorldDirectory(String worldName) {
        try {
            Path worldDir = resolveWorldPath(worldName);
            if (!Files.exists(worldDir)) {
                Files.createDirectories(worldDir);
                ModLogger.debug("Created world-specific directory: {}", worldDir);
            }
            return true;
        } catch (IOException e) {
            ModLogger.error("Failed to create world directory: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Determines the world-specific folder name based on server information
     * with centralized logging to prevent duplicate messages
     */
    public String determineWorldFolder() {
        ModLogger.debug("[WORLD-DEBUG] Beginning world folder determination");
        
        // Check if we have a recent cached world name
        long currentTime = System.currentTimeMillis();
        if (cachedWorldName != null && 
            currentTime - worldNameDeterminedAt < WORLD_NAME_CACHE_DURATION_MS) {
            ModLogger.debug("[WORLD-DEBUG] Using cached world name: {} (age: {} ms)",
                cachedWorldName, currentTime - worldNameDeterminedAt);
            return cachedWorldName;
        }
        
        try {
            // Get the Minecraft instance safely
            Minecraft minecraft = getMinecraftSafe();
            if (minecraft == null) {
                ModLogger.debug("[WORLD-DEBUG] Could not get Minecraft instance");
                return logAndReturnWorldFolder(getDefaultWorldFolderName());
            }
            
            // Try each world name strategy in order of reliability
            String worldName = tryWorldNameStrategies(minecraft);
            
            // Process the world name if we found one
            if (worldName != null && !worldName.trim().isEmpty()) {
                String sanitized = sanitizeWorldName(worldName);
                // Update the cache
                cachedWorldName = sanitized;
                worldNameDeterminedAt = currentTime;
                return logAndReturnWorldFolder(sanitized);
            } else {
                ModLogger.warn("[WORLD-DEBUG] Failed to determine world name after trying all strategies");
                return logAndReturnWorldFolder(getDefaultWorldFolderName());
            }
        } catch (Exception e) {
            ModLogger.error("[WORLD-DEBUG] Error determining world folder: {}", e.getMessage());
            e.printStackTrace();
            return logAndReturnWorldFolder(getDefaultWorldFolderName());
        }
    }
    
    /**
     * Centralized method for logging world folder changes and returning the folder name
     * This prevents duplicate logging and ensures consistent messaging
     */
    private String logAndReturnWorldFolder(String worldFolder) {
        // If this is the same folder we last logged about, don't log again
        if (Objects.equals(worldFolder, lastLoggedWorldFolder)) {
            return worldFolder;
        }
        
        // Update the last logged folder
        lastLoggedWorldFolder = worldFolder;
        
        // Log the appropriate message based on whether it's the default folder
        if (worldFolder.equals(ALL_WORLDS_FOLDER)) {
            ModLogger.warn("Using default 'all' data folder (no specific world detected)");
        } else {
            ModLogger.debug("Using world-specific data folder: {}", worldFolder);
        }
        
        return worldFolder;
    }
    
    /**
     * Helper method to use the default world folder with appropriate logging
     */
    private String useDefaultWorldFolder() {
        String defaultFolder = getDefaultWorldFolderName();
        // Update the cache
        cachedWorldName = defaultFolder;
        worldNameDeterminedAt = System.currentTimeMillis();
        return logAndReturnWorldFolder(defaultFolder);
    }
    
    /**
     * Tries all world name determination strategies in order of reliability
     * 
     * @param minecraft The Minecraft instance
     * @return The world name, or null if none could be determined
     */
    private String tryWorldNameStrategies(Minecraft minecraft) {
        // Strategy 1: Try to get world name from current server
        String fromServer = tryGetCurrentServerWorldName();
        ModLogger.debug("[WORLD-DEBUG] World name from server: {}", fromServer != null ? fromServer : "null");
        if (fromServer != null) {
            return fromServer;
        }
        
        // Strategy 2: Try to get world name from level
        String fromLevel = tryGetCurrentLevelWorldName();
        ModLogger.debug("[WORLD-DEBUG] World name from level: {}", fromLevel != null ? fromLevel : "null");
        if (fromLevel != null) {
            return fromLevel;
        }
        
        // Strategy 3: Try to get world name from screen
        String fromScreen = tryGetWorldNameFromScreen();
        ModLogger.debug("[WORLD-DEBUG] World name from screen: {}", fromScreen != null ? fromScreen : "null");
        if (fromScreen != null) {
            return fromScreen;
        }
        
        ModLogger.debug("[WORLD-DEBUG] All strategies failed to determine world name");
        return null;
    }
    
    /**
     * Strategy 1: Try to get world name from server hooks or integrated server
     */
    private String tryGetCurrentServerWorldName() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.getWorldData() != null) {
                String worldName = server.getWorldData().getLevelName();
                if (worldName != null && !worldName.isEmpty()) {
                    ModLogger.debug("[WORLD-DEBUG] Got world name from server hooks: {}", worldName);
                    return worldName;
                }
            }
        } catch (Exception e) {
            ModLogger.debug("[WORLD-DEBUG] Error getting world name from server hooks: {}", e.getMessage());
        }
        
        try {
            Minecraft minecraft = getMinecraftSafe();
            if (minecraft != null) {
                IntegratedServer integratedServer = minecraft.getSingleplayerServer();
                if (integratedServer != null && integratedServer.getWorldData() != null) {
                    String worldName = integratedServer.getWorldData().getLevelName();
                    if (worldName != null && !worldName.isEmpty()) {
                        ModLogger.debug("[WORLD-DEBUG] Got world name from integrated server: {}", worldName);
                        return worldName;
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.debug("[WORLD-DEBUG] Error getting world name from integrated server: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Strategy 2: Try to get world name from current level
     */
    private String tryGetCurrentLevelWorldName() {
        try {
            Minecraft minecraft = getMinecraftSafe();
            if (minecraft != null && minecraft.level != null) {
                // Try to access from level properties directly when available
                try {
                    // Try to use the server name if we're in an integrated server
                    if (minecraft.getSingleplayerServer() != null &&
                        minecraft.getSingleplayerServer().getWorldData() != null) {
                        String worldName = minecraft.getSingleplayerServer().getWorldData().getLevelName();
                        if (worldName != null && !worldName.isEmpty()) {
                            ModLogger.debug("[WORLD-DEBUG] Got world name from integrated server via level: {}", worldName);
                            return worldName;
                        }
                    }
                } catch (Exception e) {
                    ModLogger.debug("[WORLD-DEBUG] Could not access integrated server through level: {}", e.getMessage());
                }
                
                // Try the dimension path as an identifier
                try {
                    String dimensionKey = minecraft.level.dimension().location().toString();
                    if (dimensionKey != null && !dimensionKey.isEmpty()) {
                        ModLogger.debug("[WORLD-DEBUG] Using dimension key as fallback: {}", dimensionKey);
                        return "world_" + dimensionKey.replace(':', '_');
                    }
                } catch (Exception e) {
                    ModLogger.debug("[WORLD-DEBUG] Could not access dimension key: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            ModLogger.debug("[WORLD-DEBUG] Error getting name from client level: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Strategy 3: Try to get world name from current screen (SelectWorldScreen)
     */
    private String tryGetWorldNameFromScreen() {
        try {
            Minecraft minecraft = getMinecraftSafe();
            if (minecraft != null && minecraft.screen instanceof SelectWorldScreen) {
                Screen screen = minecraft.screen;
                // This is just a hint that we're on the world selection screen
                // We can't directly get the selected world name from here in most cases
                ModLogger.debug("[WORLD-DEBUG] Detected world selection screen");
                
                // Try to get server info for multiplayer
                if (minecraft.getCurrentServer() != null) {
                    String serverName = minecraft.getCurrentServer().name;
                    String serverIP = minecraft.getCurrentServer().ip;
                    
                    if (serverName != null && !serverName.isEmpty()) {
                        ModLogger.debug("[WORLD-DEBUG] Using multiplayer server name: {}", serverName);
                        return "mp_" + serverName;
                    } else if (serverIP != null && !serverIP.isEmpty()) {
                        ModLogger.debug("[WORLD-DEBUG] Using multiplayer server address: {}", serverIP);
                        return "mp_" + serverIP;
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.debug("[WORLD-DEBUG] Error getting world name from screen: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Invalidates the world name cache, forcing a fresh determination next time
     */
    public void invalidateWorldNameCache() {
        ModLogger.debug("[WORLD-DEBUG] World name cache invalidated");
        cachedWorldName = null;
    }
    
    /**
     * Safe wrapper for Minecraft instance access
     */
    private Minecraft getMinecraftSafe() {
        try {
            return Minecraft.getInstance();
        } catch (Exception e) {
            ModLogger.error("[WORLD-DEBUG] Error accessing Minecraft instance: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Sanitizes a world name for use as a directory name
     * 
     * @param worldName The world name to sanitize
     * @return The sanitized world name
     */
    private String sanitizeWorldName(String worldName) {
        return worldName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }
    
    /**
     * Gets the default world folder name when no specific world can be determined
     * 
     * @return The default world folder name
     */
    public String getDefaultWorldFolderName() {
        return ALL_WORLDS_FOLDER;
    }
}