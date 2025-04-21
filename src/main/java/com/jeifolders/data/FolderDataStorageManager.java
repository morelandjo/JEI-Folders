package com.jeifolders.data;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;

import java.io.File;

/**
 * Manages storage-related operations for folder data, including directory selection,
 * path creation, and filename handling.
 */
public class FolderDataStorageManager {
    private static final String CONFIG_BASE_PATH = "config/jeifolders";
    
    /**
     * Gets the appropriate save directory for the current game context.
     *
     * @return The appropriate save directory or null if none could be determined
     */
    public static File getSaveDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        
        // If no level is loaded, we can't determine which world we're in
        if (minecraft.level == null) {
            ModLogger.warn("No level loaded, using generic config directory for folder data");
            return createDirectory(minecraft.gameDirectory, CONFIG_BASE_PATH + "/default");
        }
        
        try {
            // Determine save location based on server type
            String subdir;
            String identifier;
            
            // Check if this is singleplayer
            if (minecraft.isLocalServer() && minecraft.getSingleplayerServer() != null) {
                // Get world name from singleplayer server
                identifier = minecraft.getSingleplayerServer().getWorldData().getLevelName();
                subdir = "worlds";
                ModLogger.debug("Using singleplayer world-specific folder for '{}' save data", identifier);
            }
            // Check if we can get server info from level
            else if (minecraft.level.getServer() != null) {
                identifier = minecraft.level.getServer().getWorldData().getLevelName();
                subdir = "worlds";
                ModLogger.debug("Using world-specific folder for '{}' save data", identifier);
            }
            // Check for multiplayer connection
            else if (minecraft.getCurrentServer() != null) {
                identifier = minecraft.getCurrentServer().ip;
                subdir = "servers";
                ModLogger.debug("Using server-specific folder for '{}' save data", identifier);
            }
            // Last resort fallback
            else {
                // Use dimension as identifier
                try {
                    identifier = minecraft.level.dimension().location().toString().replace(':', '_');
                    subdir = "dimensions";
                    ModLogger.debug("Using dimension as fallback identifier: {}", identifier);
                } catch (Exception e) {
                    ModLogger.error("Could not get dimension info: {}", e.getMessage());
                    identifier = "unknown";
                    subdir = "fallback";
                }
            }
            
            return createDirectory(minecraft.gameDirectory, 
                String.format("%s/%s/%s", CONFIG_BASE_PATH, subdir, sanitizeFileName(identifier)));
            
        } catch (Exception e) {
            ModLogger.error("Error determining save directory: {}", e.getMessage());
            
            // Fallback to a generic directory
            return createDirectory(minecraft.gameDirectory, CONFIG_BASE_PATH + "/all");
        }
    }
    
    /**
     * Helper method to create a directory if it doesn't exist
     * 
     * @param base The base directory
     * @param path The relative path to create
     * @return The created or existing directory
     */
    public static File createDirectory(File base, String path) {
        File dir = new File(base, path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                ModLogger.debug("Created directory: {}", dir.getAbsolutePath());
            }
        }
        return dir;
    }
    
    /**
     * Sanitizes a filename to prevent invalid characters
     * 
     * @param name The filename to sanitize
     * @return A sanitized filename
     */
    public static String sanitizeFileName(String name) {
        // Replace invalid filename characters with underscores
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    /**
     * Gets the file path for the folder data in the appropriate save directory
     * 
     * @param filename The name of the file
     * @return The file, or null if no save directory could be determined
     */
    public static File getDataFile(String filename) {
        File saveDir = getSaveDirectory();
        if (saveDir == null) {
            return null;
        }
        
        // Ensure the directory exists
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        
        return new File(saveDir, filename);
    }
}