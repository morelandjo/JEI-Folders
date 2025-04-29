package com.jeifolders.data;

import com.jeifolders.util.ModLogger;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Central facade for managing folder data and persistence.
 * Coordinates between specialized components for folder data management.
 */
public class FolderStorageService {
    // Singleton instance
    private static FolderStorageService instance;
    
    // Component references
    private final ConfigPathResolver pathResolver;
    private final FileStorage fileStorage;
    private final DataSerializer dataSerializer;
    private final FolderRepository folderRepository;
    private final IngredientCacheManager ingredientCache;
    
    // State tracking
    private boolean usingFallbackDir = false;
    private static final int MIN_SAVE_INTERVAL_MS = 5000; // Minimum time between saves
    private long lastSaveTime = 0;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderStorageService() {
        // Initialize component instances
        this.pathResolver = ConfigPathResolver.getInstance();
        this.fileStorage = FileStorage.getInstance();
        this.dataSerializer = DataSerializer.getInstance();
        this.folderRepository = FolderRepository.getInstance();
        this.ingredientCache = IngredientCacheManager.getInstance();
        
        // Set up the configuration directory
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
            // Set up the base configuration directory
            pathResolver.setupBaseConfigDirectory();
            
            // Set up the world-specific directory
            String worldName = pathResolver.determineWorldFolder();
            if (pathResolver.ensureWorldDirectory(worldName)) {
                usingFallbackDir = worldName.equals(pathResolver.getDefaultWorldFolderName());
            } else {
                setupFallbackDir("Failed to create world directory");
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
            // Use the "all" folder as fallback
            String fallbackWorld = pathResolver.getDefaultWorldFolderName();
            if (pathResolver.ensureWorldDirectory(fallbackWorld)) {
                usingFallbackDir = true;
                ModLogger.warn("Using fallback world directory: {}", fallbackWorld);
            } else {
                ModLogger.error("Failed to create fallback directory");
            }
        } catch (Exception e) {
            ModLogger.error("Failed to create fallback directory: {}", e.getMessage());
        }
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
        try {
            // Clear existing data
            folderRepository.clear();
            ingredientCache.clear();
            
            // Determine the world and get the data file path
            String worldName = pathResolver.determineWorldFolder();
            File dataFile = pathResolver.getDataFile(worldName);
            
            ModLogger.info("Loading data from {}", dataFile.getAbsolutePath());
            
            // Check if the file exists
            if (!dataFile.exists()) {
                ModLogger.info("No folder data file found at {}, will create new data", dataFile.getAbsolutePath());
                folderRepository.markLoaded();
                return true;
            }
            
            // Read the data file
            Optional<String> fileContents = fileStorage.readDataFile(dataFile.toPath());
            if (fileContents.isEmpty() || fileContents.get().isEmpty()) {
                ModLogger.info("Data file is empty, treating as no data");
                folderRepository.markLoaded();
                return true;
            }
            
            // Parse the SNBT data to NBT
            Optional<net.minecraft.nbt.CompoundTag> rootTagOpt = dataSerializer.deserializeData(fileContents.get());
            if (rootTagOpt.isEmpty()) {
                ModLogger.error("Failed to parse data file");
                folderRepository.markLoaded();
                return false;
            }
            
            // Extract folder data from NBT
            net.minecraft.nbt.CompoundTag rootTag = rootTagOpt.get();
            DataSerializer.FolderData folderData = dataSerializer.extractDataFromNbt(rootTag);
            
            // Load the data into the repository
            folderRepository.loadFromFolderData(folderData);
            
            // Load ingredients for all folders
            ingredientCache.loadIngredientsForFolders(folderRepository.getAllFolders());
            
            // Prune unused ingredients
            pruneUnusedIngredients();
            
            ModLogger.info("Data loaded successfully from {}", dataFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            ModLogger.error("Error loading data: {}", e.getMessage());
            e.printStackTrace();
            
            // Mark as loaded to avoid repeated load attempts
            folderRepository.markLoaded();
            return false;
        }
    }
    
    /**
     * Saves all folder data to the data file
     * 
     * @return true if data was saved successfully, false otherwise
     */
    public boolean saveData() {
        // Check if we need to save (throttling to avoid excessive writes)
        long currentTime = System.currentTimeMillis();
        if (!folderRepository.isDirty() || currentTime - lastSaveTime < MIN_SAVE_INTERVAL_MS) {
            return true;
        }
        
        // Make sure the config directory is set up
        setupConfigDir();
        
        try {
            // Determine the world and get the data file path
            String worldName = pathResolver.determineWorldFolder();
            File dataFile = pathResolver.getDataFile(worldName);
            
            ModLogger.debug("Saving data to {}", dataFile.getAbsolutePath());
            
            // Serialize the data to SNBT
            String snbt = dataSerializer.serializeFolderRepository(folderRepository);
            
            // Write the data to the file
            boolean success = fileStorage.writeDataFile(dataFile.toPath(), snbt);
            
            if (success) {
                // Update state
                folderRepository.markClean();
                lastSaveTime = currentTime;
                ModLogger.info("Data saved successfully to {}", dataFile.getAbsolutePath());
            } else {
                ModLogger.error("Failed to write data to {}", dataFile.getAbsolutePath());
            }
            
            return success;
        } catch (Exception e) {
            ModLogger.error("Error saving data to {}: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Ensures data is loaded before performing an operation
     */
    private void ensureDataLoaded() {
        if (!folderRepository.isLoaded()) {
            loadData();
        }
    }
    
    /**
     * Marks data as dirty and triggers a save
     */
    private void markDirtyAndSave() {
        folderRepository.markDirty();
        saveData();
    }
    
    /**
     * Get all folders
     * 
     * @return List of all folders
     */
    public List<Folder> getAllFolders() {
        ensureDataLoaded();
        return folderRepository.getAllFolders();
    }
    
    /**
     * Get a folder by ID
     * 
     * @param id The folder ID
     * @return Optional containing the folder, or empty if not found
     */
    public Optional<Folder> getFolder(int id) {
        ensureDataLoaded();
        return folderRepository.getFolder(id);
    }
    
    /**
     * Get the last active folder ID
     * 
     * @return The last active folder ID, or null if none was active
     */
    public Integer getLastActiveFolderId() {
        ensureDataLoaded();
        return folderRepository.getLastActiveFolderId();
    }
    
    /**
     * Set the last active folder ID
     * 
     * @param id The folder ID to set as active, or null to clear
     */
    public void setLastActiveFolderId(Integer id) {
        ensureDataLoaded();
        folderRepository.setLastActiveFolderId(id);
        markDirtyAndSave();
    }
    
    /**
     * Creates a new folder
     * 
     * @param name The name of the folder
     * @return The newly created folder
     */
    public Folder createFolder(String name) {
        ensureDataLoaded();
        Folder folder = folderRepository.createFolder(name);
        markDirtyAndSave();
        return folder;
    }
    
    /**
     * Deletes a folder
     * 
     * @param id The ID of the folder to delete
     * @return true if the folder was deleted, false if it didn't exist
     */
    public boolean deleteFolder(int id) {
        ensureDataLoaded();
        boolean result = folderRepository.deleteFolder(id);
        if (result) {
            markDirtyAndSave();
        }
        return result;
    }
    
    /**
     * Updates a folder's name
     * 
     * @param id The ID of the folder to update
     * @param newName The new name for the folder
     * @return true if the folder was updated, false if it didn't exist
     */
    public boolean updateFolderName(int id, String newName) {
        ensureDataLoaded();
        boolean result = folderRepository.updateFolderName(id, newName);
        if (result) {
            markDirtyAndSave();
        }
        return result;
    }
    
    /**
     * Adds a bookmark to a folder
     * 
     * @param folderId The ID of the folder to add the bookmark to
     * @param bookmarkKey The bookmark key to add
     * @return true if the bookmark was added, false if the folder doesn't exist
     */
    public boolean addBookmark(int folderId, String bookmarkKey) {
        ensureDataLoaded();
        boolean result = folderRepository.addBookmark(folderId, bookmarkKey);
        if (result) {
            markDirtyAndSave();
        }
        return result;
    }
    
    /**
     * Removes a bookmark from a folder
     * 
     * @param folderId The ID of the folder to remove the bookmark from
     * @param bookmarkKey The bookmark key to remove
     * @return true if the bookmark was removed, false if the folder doesn't exist or didn't contain the bookmark
     */
    public boolean removeBookmark(int folderId, String bookmarkKey) {
        ensureDataLoaded();
        boolean result = folderRepository.removeBookmark(folderId, bookmarkKey);
        if (result) {
            markDirtyAndSave();
            pruneUnusedIngredients();
        }
        return result;
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
        return folderRepository.isDirty();
    }
    
    /**
     * Checks if we're using the fallback directory
     * 
     * @return true if using the fallback directory
     */
    public boolean isUsingFallbackDir() {
        return usingFallbackDir;
    }

    /**
     * Prunes unused ingredients from the cache
     * 
     * @return The number of pruned ingredients
     */
    public int pruneUnusedIngredients() {
        // Collect all active bookmark keys from the repository
        Set<String> activeKeys = folderRepository.getAllBookmarkKeys();
        
        // Prune the cache
        int prunedCount = ingredientCache.pruneUnused(activeKeys);
        if (prunedCount > 0) {
            ModLogger.debug("Pruned {} unused ingredients from cache", prunedCount);
        }
        return prunedCount;
    }
}