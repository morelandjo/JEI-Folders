package com.jeifolders.data;

import com.jeifolders.integration.JEIIntegration;
import com.jeifolders.util.SnbtFormat;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Manages backend folder data, including CRUD operations, bookmark management,
 * and data persistence (saving/loading to SNBT files).
 */
public class FolderManager {
    private static final String FILENAME = "jeifolders.snbt";
    private static final LevelResource SAVE_FOLDER = new LevelResource(".");
    private static final FolderManager INSTANCE = new FolderManager();
    
    private final Map<Integer, Folder> folders = new HashMap<>();
    private int nextFolderId = 0;

    // Add a cache to store processed ingredients for each folder
    private final Map<Integer, List<ITypedIngredient<?>>> folderIngredientsCache = new HashMap<>();
    
    private FolderManager() {
        // Private constructor for singleton
    }
    
    public static FolderManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Sets the JEI runtime to enable bookmark functionality.
     */
    public void setJeiRuntime(Object jeiRuntime) {
        if (jeiRuntime instanceof IJeiRuntime runtime) {
            JEIIntegration.setJeiRuntime(runtime);
            loadData();
        } else {
            ModLogger.error("Invalid JEI runtime provided to FolderManager");
        }
    }
    
    /**
     * Creates a new folder with the given name.
     */
    public Folder createFolder(String name) {
        Folder folder = new Folder(nextFolderId++, name);
        folders.put(folder.getId(), folder);
        saveData();
        return folder;
    }
    
    /**
     * Removes a folder with the given ID.
     */
    public void removeFolder(int id) {
        folders.remove(id);
        // Invalidate the cache for this folder
        invalidateIngredientsCache(id);
        saveData();
    }
    
    /**
     * Gets a folder with the given ID.
     */
    public Optional<Folder> getFolder(int folderId) {
        return Optional.ofNullable(folders.get(folderId));
    }
    
    /**
     * Gets all folders.
     */
    public List<Folder> getAllFolders() {
        return new ArrayList<>(folders.values());
    }
    
    /**
     * Adds a bookmark to a folder and saves data.
     */
    public void addBookmarkToFolder(int folderId, String bookmarkKey) {
        Folder folder = folders.get(folderId);
        if (folder != null) {
            folder.addBookmarkKey(bookmarkKey);
            // Invalidate the cache for this folder
            invalidateIngredientsCache(folderId);
            saveData();
        } else {
            ModLogger.warn("Could not add bookmark - folder with id {} not found", folderId);
        }
    }
    
    /**
     * Removes a bookmark from a folder.
     */
    public void removeBookmarkFromFolder(int folderId, String bookmarkKey) {
        Folder folder = folders.get(folderId);
        if (folder != null && folder.removeBookmark(bookmarkKey)) {
            // Invalidate the cache for this folder
            invalidateIngredientsCache(folderId);
            saveData();
        }
    }
    
    /**
     * Gets bookmarks for a specific folder.
     */
    public List<String> getFolderBookmarkKeys(int folderId) {
        Folder folder = folders.get(folderId);
        return folder != null ? folder.getBookmarkKeys() : List.of();
    }

    /**
     * Gets the cached ingredients for a folder, processing them if not already cached.
     */
    public List<ITypedIngredient<?>> getCachedIngredientsForFolder(int folderId) {
        // Return the cached ingredients if available
        if (folderIngredientsCache.containsKey(folderId)) {
            ModLogger.debug("Using cached ingredients for folder ID: {}, {} items", 
                folderId, folderIngredientsCache.get(folderId).size());
            return new ArrayList<>(folderIngredientsCache.get(folderId));
        }
        
        // Otherwise, process and cache them
        Optional<Folder> folder = getFolder(folderId);
        if (!folder.isPresent()) {
            return Collections.emptyList();
        }
        
        List<String> bookmarkKeys = folder.get().getBookmarkKeys();
        // Fix: Use JEIIngredientManager instead of JEIIntegration
        List<ITypedIngredient<?>> ingredients = com.jeifolders.integration.JEIIngredientManager.getIngredientsForKeys(bookmarkKeys);
        
        // Cache the processed ingredients
        folderIngredientsCache.put(folderId, new ArrayList<>(ingredients));
        ModLogger.info("Cached {} ingredients for folder ID: {}", ingredients.size(), folderId);
        
        return ingredients;
    }

    /**
     * Invalidates the ingredient cache for a specific folder.
     */
    public void invalidateIngredientsCache(int folderId) {
        folderIngredientsCache.remove(folderId);
        ModLogger.debug("Invalidated ingredients cache for folder ID: {}", folderId);
    }

    /**
     * Invalidates the entire ingredient cache.
     */
    public void invalidateAllIngredientCaches() {
        folderIngredientsCache.clear();
        ModLogger.debug("Invalidated all ingredient caches");
    }
    
    /**
     * Loads folder data from the SNBT file.
     */
    public void loadData() {
        ModLogger.debug("Loading folder data...");
        File saveDir = getSaveDirectory();
        if (saveDir == null) {
            ModLogger.warn("Cannot load folder data: No save directory available");
            return;
        }
        
        ModLogger.debug("Using save directory: {}", saveDir.getAbsolutePath());
        
        // Ensure the directory exists
        if (!saveDir.exists()) {
            saveDir.mkdirs();
            ModLogger.debug("Created save directory: {}", saveDir.getAbsolutePath());
        }
        
        File dataFile = new File(saveDir, FILENAME);
        ModLogger.debug("Looking for data file: {}", dataFile.getAbsolutePath());
        
        if (!dataFile.exists()) {
            ModLogger.info("No folder data file found at {}, creating new data", dataFile.getAbsolutePath());
            folders.clear();
            nextFolderId = 0;
            // Invalidate all caches when reloading data
            invalidateAllIngredientCaches();
            return;
        }
        
        ModLogger.debug("Found data file, size: {} bytes", dataFile.length());
        
        try {
            // Read SNBT from file
            StringBuilder snbtContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    snbtContent.append(line);
                }
            }
            
            String content = snbtContent.toString().trim();
            if (content.isEmpty()) {
                ModLogger.warn("Data file exists but is empty, creating new data");
                folders.clear();
                nextFolderId = 0;
                // Invalidate all caches when reloading data
                invalidateAllIngredientCaches();
                return;
            }
            
            ModLogger.debug("Read SNBT content: {}", 
                content.length() > 100 ? content.substring(0, 100) + "..." : content);
            
            // Parse SNBT string to CompoundTag
            CompoundTag rootTag = TagParser.parseTag(content);
            nextFolderId = rootTag.getInt("nextId");
            
            folders.clear();
            if (rootTag.contains("folders")) {
                ListTag folderList = rootTag.getList("folders", Tag.TAG_COMPOUND);
                ModLogger.info("Loading {} folders from data file", folderList.size());
                
                for (int i = 0; i < folderList.size(); i++) {
                    CompoundTag folderTag = folderList.getCompound(i);
                    Folder folder = Folder.fromNbt(folderTag);
                    folders.put(folder.getId(), folder);
                }
            } else {
                ModLogger.info("No folders found in data file");
            }
        } catch (Exception e) {
            ModLogger.error("Failed to load folder data: {}", e.getMessage(), e);
        }
        
        // Invalidate all caches when reloading data
        invalidateAllIngredientCaches();
    }
    
    /**
     * Saves folder data to the SNBT file.
     */
    public void saveData() {
        ModLogger.debug("Saving folders data. Current folders: {}", folders.size());
        
        try {
            File saveDir = getSaveDirectory();
            if (saveDir == null) {
                ModLogger.warn("Could not save folder data - no save directory available");
                return;
            }
            
            // Ensure the directory exists
            if (!saveDir.exists()) {
                saveDir.mkdirs();
                ModLogger.debug("Created save directory: {}", saveDir.getAbsolutePath());
            }
            
            File saveFile = new File(saveDir, FILENAME);
            ModLogger.debug("Saving to file: {}", saveFile.getAbsolutePath());
            
            CompoundTag data = getCompoundTag();
            SnbtFormat.writeToFile(saveFile, data);
            ModLogger.info("Folder data saved successfully to {}", saveFile.getAbsolutePath());
        } catch (IOException e) {
            ModLogger.error("Error saving folders data: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates a CompoundTag containing all folder data for saving.
     */
    private CompoundTag getCompoundTag() {
        CompoundTag rootTag = new CompoundTag();
        rootTag.putInt("nextId", nextFolderId);
        
        ListTag folderList = new ListTag();
        for (Folder folder : folders.values()) {
            folderList.add(folder.toNbt());
        }
        rootTag.put("folders", folderList);
        
        return rootTag;
    }
    
    /**
     * Gets the current save directory.
     */
    private File getSaveDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        
        // If no level is loaded, we can't determine which world we're in
        if (minecraft.level == null) {
            ModLogger.warn("No level loaded, using generic config directory for folder data");
            return createDirectory(minecraft.gameDirectory, "config/jeifolders/default");
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
                String.format("config/jeifolders/%s/%s", subdir, sanitizeFileName(identifier)));
            
        } catch (Exception e) {
            ModLogger.error("Error determining save directory: {}", e.getMessage());
            
            // Fallback to a generic directory
            return createDirectory(minecraft.gameDirectory, "config/jeifolders/fallback");
        }
    }

    /**
     * Helper method to create a directory if it doesn't exist
     */
    private File createDirectory(File base, String path) {
        File dir = new File(base, path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Sanitizes a filename to prevent invalid characters
     */
    private String sanitizeFileName(String name) {
        // Replace invalid filename characters with underscores
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
