package com.jeifolders.data;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
     */
    public String determineWorldFolder() {
        try {
            // Get the Minecraft instance safely
            Minecraft minecraft = getMinecraftSafe();
            
            // Try each world name strategy in order of reliability
            String worldName = tryWorldNameStrategies(minecraft);
            
            // Process the world name if we found one
            if (worldName != null && !worldName.trim().isEmpty()) {
                return sanitizeWorldName(worldName);
            } else {
                ModLogger.warn("Failed to determine world name after trying all strategies");
            }
        } catch (Exception e) {
            ModLogger.error("Error determining world folder: {}", e.getMessage());
            e.printStackTrace();
        }
        
        // Default to the "all" folder if we can't determine the world
        return getDefaultWorldFolderName();
    }
    
    /**
     * Tries all world name determination strategies in order of reliability
     * 
     * @param minecraft The Minecraft instance
     * @return The world name, or null if none could be determined
     */
    private String tryWorldNameStrategies(Minecraft minecraft) {
        // Strategy 1: Try to get world name from ServerLifecycleHooks
        String worldName = tryGetWorldNameFromServerHooks();
        if (worldName != null) {
            return worldName;
        }
        
        // Strategy 2: Try to get world name from integrated server
        worldName = tryGetWorldNameFromIntegratedServer(minecraft);
        if (worldName != null) {
            return worldName;
        }
        
        // Strategy 3: Try to get server name or address for multiplayer
        return tryGetMultiplayerServerName(minecraft);
    }
    
    /**
     * Safe wrapper for Minecraft instance access
     */
    private Minecraft getMinecraftSafe() {
        try {
            return Minecraft.getInstance();
        } catch (Exception e) {
            ModLogger.error("Error accessing Minecraft instance: {}", e.getMessage());
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
        String sanitized = worldName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        ModLogger.info("Using world-specific data folder: {}", sanitized);
        return sanitized;
    }
    
    /**
     * Gets the default world folder name when no specific world can be determined
     * 
     * @return The default world folder name
     */
    public String getDefaultWorldFolderName() {
        ModLogger.info("Using default 'all' data folder (no specific world detected)");
        return ALL_WORLDS_FOLDER;
    }
    
    /**
     * Strategy 1: Try to get world name from ServerLifecycleHooks
     * 
     * @return The world name, or null if not available
     */
    private String tryGetWorldNameFromServerHooks() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                String worldName = server.getWorldData().getLevelName();
                if (worldName != null) {
                    ModLogger.debug("Got world name from ServerLifecycleHooks: {}", worldName);
                    return worldName;
                }
            }
        } catch (Exception e) {
            ModLogger.debug("Could not get world name from ServerLifecycleHooks: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Strategy 2: Try to get world name from integrated server
     * 
     * @param minecraft The Minecraft client instance
     * @return The world name, or null if not available
     */
    private String tryGetWorldNameFromIntegratedServer(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        
        try {
            IntegratedServer integratedServer = minecraft.getSingleplayerServer();
            if (integratedServer != null) {
                String worldName = integratedServer.getWorldData().getLevelName();
                if (worldName != null) {
                    ModLogger.debug("Got world name from integrated server: {}", worldName);
                    return worldName;
                }
            }
        } catch (Exception e) {
            ModLogger.debug("Could not get world name from integrated server: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Strategy 3: Try to get server name or address for multiplayer
     * 
     * @param minecraft The Minecraft client instance
     * @return The server name or address formatted as a directory name, or null if not available
     */
    private String tryGetMultiplayerServerName(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        
        try {
            var currentServer = minecraft.getCurrentServer();
            if (currentServer == null) {
                return null;
            }
            
            String serverName = currentServer.name;
            String serverIP = currentServer.ip;
            
            // Use server name if available, otherwise use IP
            if (serverName != null && !serverName.isEmpty()) {
                String worldName = "mp_" + serverName.replace(' ', '_');
                ModLogger.debug("Using multiplayer server name as world name: {}", worldName);
                return worldName;
            } else if (serverIP != null && !serverIP.isEmpty()) {
                String worldName = "mp_" + serverIP.replace(':', '_');
                ModLogger.debug("Using multiplayer server address as world name: {}", worldName);
                return worldName;
            }
        } catch (Exception e) {
            ModLogger.debug("Could not get server info for multiplayer: {}", e.getMessage());
        }
        return null;
    }
}