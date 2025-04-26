package com.jeifolders.data;

import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.util.ModLogger;
import com.jeifolders.util.SnbtFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Central service for managing folder data and persistence.
 * Handles loading, saving, and CRUD operations for folders.
 */
public class FolderStorageService {
    // Singleton instance
    private static FolderStorageService instance;

    // Data collections
    private final Map<Integer, Folder> folders = new HashMap<>();
    private final Map<String, Object> ingredientCache = new HashMap<>();

    // Runtime state
    private Integer lastActiveFolderId = null;
    private boolean isLoaded = false;
    private boolean isDirty = false;
    private boolean usingFallbackDir = false;

    // File paths
    private Path configDir;
    private File dataFile;

    // Constants
    private static final String ALL_WORLDS_FOLDER = "all";
    private static final String DATA_FILENAME = "jeifolders.snbt";
    private static final String CONFIG_ROOT = "./config/jeifolders/";
    private static final int MIN_SAVE_INTERVAL_MS = 5000; // Minimum time between saves
    private long lastSaveTime = 0;
    private FolderStateManager callbackManager;

    // NBT Keys
    private static final String FOLDERS_KEY = "folders";
    private static final String ACTIVE_FOLDER_KEY = "activeFolder";

    /**
     * Private constructor for singleton pattern
     */
    private FolderStorageService() {
        setupConfigDir();
    }

    /**
     * Get the singleton instance
     */
    public static synchronized FolderStorageService getInstance() {
        if (instance == null) {
            instance = new FolderStorageService();
        }
        return instance;
    }

    /**
     * Sets up the configuration directory and data file
     */
    private void setupConfigDir() {
        try {
            // Base config directory
            configDir = Paths.get(CONFIG_ROOT);

            // Create the config directory if it doesn't exist
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                ModLogger.debug("Created config directory: {}", configDir);
            }

            // Always try to get a world-specific folder first
            String worldFolderName = determineWorldFolder();
            Path worldDir = configDir.resolve(worldFolderName);

            try {
                // Create the world-specific directory if it doesn't exist
                if (!Files.exists(worldDir)) {
                    Files.createDirectories(worldDir);
                    ModLogger.debug("Created world-specific directory: {}", worldDir);
                }

                // Set the data file path
                dataFile = worldDir.resolve(DATA_FILENAME).toFile();
                ModLogger.debug("Data file path set to: {}", dataFile.getAbsolutePath());
                usingFallbackDir = worldFolderName.equals(ALL_WORLDS_FOLDER);
            } catch (Exception e) {
                setupFallbackDir("Error creating world directory: " + e.getMessage());
            }
        } catch (Exception e) {
            setupFallbackDir("Error setting up config directory: " + e.getMessage());
        }
    }

    /**
     * Sets up a fallback directory when the primary setup fails
     * 
     * @param errorMessage The error message to log
     */
    private void setupFallbackDir(String errorMessage) {
        ModLogger.error(errorMessage);
        
        try {
            // Fallback to the default "all" location
            Path fallbackDir = configDir.resolve(ALL_WORLDS_FOLDER);
            if (!Files.exists(fallbackDir)) {
                Files.createDirectories(fallbackDir);
            }
            dataFile = fallbackDir.resolve(DATA_FILENAME).toFile();
            usingFallbackDir = true;
            ModLogger.warn("Using fallback data file location: {}", dataFile.getAbsolutePath());
        } catch (IOException ioEx) {
            ModLogger.error("Failed to create fallback directory: {}", ioEx.getMessage());
            
            // Ultimate fallback - use a file in the current directory
            dataFile = new File(DATA_FILENAME);
            ModLogger.warn("Using emergency fallback data file: {}", dataFile.getAbsolutePath());
        }
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
     * Determines the world-specific folder name based on server information
     */
    private String determineWorldFolder() {
        try {
            // Try multiple strategies to find the world name
            String worldName = null;
            
            // Get the Minecraft instance safely
            Minecraft minecraft = getMinecraftSafe();
            
            // Strategy 1: Get directly from ServerLifecycleHooks (most reliable for servers)
            try {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    // This is safe because WorldData is the interface that server.getWorldData() returns
                    worldName = server.getWorldData().getLevelName();
                    ModLogger.debug("Got world name from ServerLifecycleHooks: {}", worldName);
                }
            } catch (Exception e) {
                ModLogger.debug("Could not get world name from ServerLifecycleHooks: {}", e.getMessage());
            }
            
           
            
            // Strategy 3: Try to get from integrated server
            if (worldName == null && minecraft != null) {
                try {
                    IntegratedServer integratedServer = minecraft.getSingleplayerServer();
                    if (integratedServer != null) {
                        // This is safe because WorldData is the interface that integrated server uses
                        worldName = integratedServer.getWorldData().getLevelName();
                        ModLogger.debug("Got world name from integrated server: {}", worldName);
                    }
                } catch (Exception e) {
                    ModLogger.debug("Could not get world name from integrated server: {}", e.getMessage());
                }
            }
            
            // Strategy 4: For multiplayer, try to get server address
            if (worldName == null && minecraft != null && minecraft.getCurrentServer() != null) {
                try {
                    String serverName = minecraft.getCurrentServer().name;
                    String serverIP = minecraft.getCurrentServer().ip;
                    
                    // Use server name if available, otherwise use IP
                    if (serverName != null && !serverName.isEmpty()) {
                        worldName = "mp_" + serverName.replace(' ', '_');
                        ModLogger.debug("Using multiplayer server name as world name: {}", worldName);
                    } else if (serverIP != null && !serverIP.isEmpty()) {
                        worldName = "mp_" + serverIP.replace(':', '_');
                        ModLogger.debug("Using multiplayer server address as world name: {}", worldName);
                    }
                } catch (Exception e) {
                    ModLogger.debug("Could not get server info for multiplayer: {}", e.getMessage());
                }
            }
            
            
            
            // If we have a world name, sanitize and return it
            if (worldName != null && !worldName.trim().isEmpty()) {
                // Sanitize the world name for use as a directory name
                worldName = worldName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
                ModLogger.info("Using world-specific data folder: {}", worldName);
                return worldName;
            } else {
                ModLogger.warn("Failed to determine world name after trying all strategies");
            }
        } catch (Exception e) {
            ModLogger.error("Error determining world folder: {}", e.getMessage());
            e.printStackTrace();
        }
        
        // Default to the "all" folder if we can't determine the world
        ModLogger.info("Using default 'all' data folder (no specific world detected)");
        return ALL_WORLDS_FOLDER;
    }
    
    /**
     * Resets the data service state by updating file paths and reloading data
     */
    public void resetDataPaths() {
        ModLogger.debug("Resetting data paths and reloading data");
        setupConfigDir();
        loadData();
    }

    /**
     * Loads all folder data from the data file
     * 
     * @return true if data was loaded successfully, false otherwise
     */
    public boolean loadData() {
        // Reset loaded state to force a reload
        isLoaded = false;
        
        // Clear any existing data
        folders.clear();
        ingredientCache.clear();
        lastActiveFolderId = null;

        // Make sure we have a valid data file path
        setupConfigDir();

        ModLogger.info("Loading data from {}", dataFile.getAbsolutePath());

        // If the file doesn't exist, there's no data to load
        if (!dataFile.exists()) {
            ModLogger.info("No folder data file found at {}, will create new data", dataFile.getAbsolutePath());
            isLoaded = true;
            return true;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            // Read the full file content
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            // Check if the file is empty
            String snbtData = sb.toString().trim();
            if (snbtData.isEmpty()) {
                ModLogger.info("Data file is empty, treating as no data");
                isLoaded = true;
                return true;
            }

            // Parse the SNBT data to NBT
            CompoundTag rootTag = TagParser.parseTag(snbtData);

            // Load folders
            if (rootTag.contains(FOLDERS_KEY, Tag.TAG_LIST)) {
                ListTag foldersList = rootTag.getList(FOLDERS_KEY, Tag.TAG_COMPOUND);
                ModLogger.debug("Found {} folders in data file", foldersList.size());

                for (int i = 0; i < foldersList.size(); i++) {
                    CompoundTag folderTag = foldersList.getCompound(i);
                    
                    // Parse folder from NBT
                    Folder folder = Folder.fromNbt(folderTag);
                    
                    if (folder != null) {
                        folders.put(folder.getId(), folder);
                        ModLogger.debug("Loaded folder: {} (ID: {})", folder.getName(), folder.getId());
                    } else {
                        ModLogger.warn("Failed to load folder at index {}", i);
                    }
                }

                ModLogger.info("Successfully loaded {} folders", folders.size());
            }

            // Load active folder ID
            if (rootTag.contains(ACTIVE_FOLDER_KEY, Tag.TAG_INT)) {
                lastActiveFolderId = rootTag.getInt(ACTIVE_FOLDER_KEY);
                ModLogger.debug("Loaded active folder ID: {}", lastActiveFolderId);
            }

            // Load cached ingredients (for each folder's bookmarks)
            for (Folder folder : folders.values()) {
                loadIngredientsForFolder(folder);
            }

            isLoaded = true;
            isDirty = false;
            
            ModLogger.info("Data loaded successfully from {}", dataFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            ModLogger.error("Error loading data from {}: {}", dataFile.getAbsolutePath(), e.getMessage());
            e.printStackTrace();
        }

        // Default loading state to true even if there was an error, to avoid repeated load attempts
        isLoaded = true;
        return false;
    }
    
    /**
     * Saves all folder data to the data file
     * 
     * @return true if data was saved successfully, false otherwise
     */
    public boolean saveData() {
        // Throttle saves to avoid excessive writes
        long currentTime = System.currentTimeMillis();
        if (!isDirty || currentTime - lastSaveTime < MIN_SAVE_INTERVAL_MS) {
            return true;
        }
        
        // Make sure the config directory is set up
        setupConfigDir();
        
        ModLogger.debug("Saving data to {}", dataFile.getAbsolutePath());
        
        try {
            // Create parent directories if they don't exist
            File parentDir = dataFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Create tag structure
            CompoundTag rootTag = new CompoundTag();
            
            // Save folders
            ListTag foldersList = new ListTag();
            for (Folder folder : folders.values()) {
                CompoundTag folderTag = folder.toNbt();
                foldersList.add(folderTag);
            }
            rootTag.put(FOLDERS_KEY, foldersList);
            
            // Save active folder ID
            if (lastActiveFolderId != null) {
                rootTag.putInt(ACTIVE_FOLDER_KEY, lastActiveFolderId);
            }
            
            // Convert to SNBT and write to file
            String snbt = SnbtFormat.format(rootTag);
            
            try (FileWriter writer = new FileWriter(dataFile)) {
                writer.write(snbt);
            }
            
            isDirty = false;
            lastSaveTime = currentTime;
            ModLogger.info("Data saved successfully to {}", dataFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            ModLogger.error("Error saving data to {}: {}", dataFile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    /**
     * Registers a callback manager to notify of data changes
     * 
     * @param manager The folder manager to notify of changes
     */
    public void registerCallback(FolderStateManager manager) {
        this.callbackManager = manager;
        ModLogger.debug("Callback manager registered with FolderDataService");
    }

    /**
     * Loads ingredient data for a specific folder
     * 
     * @param folder The folder to load ingredients for
     */
    private void loadIngredientsForFolder(Folder folder) {
        if (folder == null) {
            return;
        }

        // Get the service and check if it's available
        var jeiService = JEIIntegrationFactory.getJEIService();
        
        // JEI might not be initialized yet
        if (!jeiService.getJeiRuntime().isPresent()) {
            ModLogger.debug("Cannot load ingredients - JEI runtime not available");
            return;
        }

        // Get the ingredient service that has the getIngredientForKey method
        var ingredientService = JEIIntegrationFactory.getIngredientService();
        
        // Load all bookmark ingredients
        for (String bookmarkKey : folder.getBookmarkKeys()) {
            try {
                // Try to parse the ingredient from the bookmark key
                var ingredientOpt = ingredientService.getIngredientForKey(bookmarkKey);
                
                if (ingredientOpt.isPresent()) {
                    // Cache the ingredient if successfully parsed
                    ingredientCache.put(bookmarkKey, ingredientOpt.get());
                } else {
                    ModLogger.debug("Failed to load ingredient for key: {}", bookmarkKey);
                }
            } catch (Exception e) {
                ModLogger.debug("Error loading ingredient for key {}: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Get all folders
     * 
     * @return List of all folders
     */
    public List<Folder> getAllFolders() {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        return new ArrayList<>(folders.values());
    }
    
    /**
     * Get a folder by ID
     * 
     * @param id The folder ID
     * @return Optional containing the folder, or empty if not found
     */
    public Optional<Folder> getFolder(int id) {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        return Optional.ofNullable(folders.get(id));
    }
    
    /**
     * Get the last active folder ID
     * 
     * @return The last active folder ID, or null if none was active
     */
    public Integer getLastActiveFolderId() {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        return lastActiveFolderId;
    }
    
    /**
     * Set the last active folder ID
     * 
     * @param id The folder ID to set as active, or null to clear
     */
    public void setLastActiveFolderId(Integer id) {
        lastActiveFolderId = id;
        isDirty = true;
        saveData();
    }
    
    /**
     * Creates a new folder
     * 
     * @param name The name of the folder
     * @return The newly created folder
     */
    public Folder createFolder(String name) {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        
        // Find the next available ID
        int nextId = 1;
        while (folders.containsKey(nextId)) {
            nextId++;
        }
        
        // Create the new folder
        Folder folder = new Folder(nextId, name);
        folders.put(nextId, folder);
        
        // Mark as dirty and save
        isDirty = true;
        saveData();
        
        ModLogger.info("Created new folder: {} (ID: {})", name, nextId);
        return folder;
    }
    
    /**
     * Deletes a folder
     * 
     * @param id The ID of the folder to delete
     * @return true if the folder was deleted, false if it didn't exist
     */
    public boolean deleteFolder(int id) {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        
        // Remove the folder
        Folder removed = folders.remove(id);
        if (removed != null) {
            // If this was the active folder, clear it
            if (lastActiveFolderId != null && lastActiveFolderId == id) {
                lastActiveFolderId = null;
            }
            
            // Mark as dirty and save
            isDirty = true;
            saveData();
            
            ModLogger.info("Deleted folder: {} (ID: {})", removed.getName(), id);
            return true;
        }
        
        return false;
    }
    
    /**
     * Updates a folder's name
     * 
     * @param id The ID of the folder to update
     * @param newName The new name for the folder
     * @return true if the folder was updated, false if it didn't exist
     */
    public boolean updateFolderName(int id, String newName) {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        
        // Get the folder
        Folder folder = folders.get(id);
        if (folder != null) {
            // Update the name
            folder.setName(newName);
            
            // Mark as dirty and save
            isDirty = true;
            saveData();
            
            ModLogger.info("Updated folder name: {} (ID: {})", newName, id);
            return true;
        }
        
        return false;
    }
    
    /**
     * Adds a bookmark to a folder
     * 
     * @param folderId The ID of the folder to add the bookmark to
     * @param bookmarkKey The bookmark key to add
     * @return true if the bookmark was added, false if the folder doesn't exist
     */
    public boolean addBookmark(int folderId, String bookmarkKey) {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        
        // Get the folder
        Folder folder = folders.get(folderId);
        if (folder != null) {
            // Add the bookmark
            if (folder.addBookmarkKey(bookmarkKey)) {
                // Mark as dirty and save
                isDirty = true;
                saveData();
                
                ModLogger.debug("Added bookmark to folder {} (ID: {}): {}", folder.getName(), folderId, bookmarkKey);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Removes a bookmark from a folder
     * 
     * @param folderId The ID of the folder to remove the bookmark from
     * @param bookmarkKey The bookmark key to remove
     * @return true if the bookmark was removed, false if the folder doesn't exist or didn't contain the bookmark
     */
    public boolean removeBookmark(int folderId, String bookmarkKey) {
        // Make sure data is loaded
        if (!isLoaded) {
            loadData();
        }
        
        // Get the folder
        Folder folder = folders.get(folderId);
        if (folder != null) {
            // Remove the bookmark
            if (folder.removeBookmarkKey(bookmarkKey)) {
                // Mark as dirty and save
                isDirty = true;
                saveData();
                
                ModLogger.debug("Removed bookmark from folder {} (ID: {}): {}", folder.getName(), folderId, bookmarkKey);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets a cached ingredient by key
     * 
     * @param key The ingredient key
     * @return The cached ingredient, or null if not found
     */
    public Object getCachedIngredient(String key) {
        return ingredientCache.get(key);
    }
    
    /**
     * Caches an ingredient
     * 
     * @param key The ingredient key
     * @param ingredient The ingredient to cache
     */
    public void cacheIngredient(String key, Object ingredient) {
        ingredientCache.put(key, ingredient);
    }
    
    /**
     * Clears all cached ingredients
     */
    public void clearCachedIngredients() {
        ingredientCache.clear();
    }
    
    /**
     * Checks if the data is dirty (has unsaved changes)
     * 
     * @return true if there are unsaved changes
     */
    public boolean isDirty() {
        return isDirty;
    }
    
    /**
     * Checks if we're using the fallback directory
     * 
     * @return true if using the fallback directory
     */
    public boolean isUsingFallbackDir() {
        return usingFallbackDir;
    }
}