package com.jeifolders.data;

import com.jeifolders.gui.folderButtons.UnifiedFolderManager;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.util.ModLogger;
import com.jeifolders.util.SnbtFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Consolidated service class that handles all folder data operations including:
 * - CRUD operations for folders and bookmarks
 * - Data persistence through file storage
 * - Ingredient caching and management
 */
public class FolderDataService {
    private static final String FILENAME = "jeifolders.snbt";
    private static final String CONFIG_BASE_PATH = "config/jeifolders";
    private static final FolderDataService INSTANCE = new FolderDataService();
    
    private final Map<Integer, FolderDataRepresentation> folders = new HashMap<>();
    private int nextFolderId = 0;

    private IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
    private UnifiedFolderManager folderManager;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderDataService() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance
     */
    public static FolderDataService getInstance() {
        return INSTANCE;
    }
    
    /**
     * Sets the UnifiedFolderManager instance
     */
    public void setFolderManager(UnifiedFolderManager folderManager) {
        this.folderManager = folderManager;
    }
    
    // ===== FOLDER DATA OPERATIONS =====
    
    /**
     * Creates a new folder with the given name.
     */
    public FolderDataRepresentation createFolder(String name) {
        FolderDataRepresentation folder = new FolderDataRepresentation(nextFolderId++, name);
        folders.put(folder.getId(), folder);
        saveData();
        return folder;
    }
    
    /**
     * Removes a folder with the given ID.
     */
    public void removeFolder(int id) {
        folders.remove(id);
        invalidateIngredientsCache(id);
        saveData();
    }
    
    /**
     * Gets a folder with the given ID.
     */
    public Optional<FolderDataRepresentation> getFolder(int folderId) {
        return Optional.ofNullable(folders.get(folderId));
    }
    
    /**
     * Gets all folders.
     */
    public List<FolderDataRepresentation> getAllFolders() {
        return new ArrayList<>(folders.values());
    }
    
    // ===== BOOKMARK OPERATIONS =====
    
    /**
     * Adds a bookmark to a folder and saves data.
     */
    public void addBookmarkToFolder(int folderId, String bookmarkKey) {
        getFolder(folderId).ifPresentOrElse(
            folder -> {
                // Folder exists, add the bookmark
                folder.addBookmarkKey(bookmarkKey);
                invalidateIngredientsCache(folderId);
                saveData();
                
                // Notify the unified folder manager
                if (folderManager != null) {
                    folderManager.notifyFolderContentsChanged(folderId);
                }
            },
            // Folder doesn't exist
            () -> ModLogger.warn("Could not add bookmark - folder with id {} not found", folderId)
        );
    }
    
    /**
     * Removes a bookmark from a folder.
     */
    public void removeBookmarkFromFolder(int folderId, String bookmarkKey) {
        getFolder(folderId).ifPresent(folder -> {
            if (folder.removeBookmark(bookmarkKey)) {
                invalidateIngredientsCache(folderId);
                saveData();
                
                // Notify the unified folder manager
                if (folderManager != null) {
                    folderManager.notifyFolderContentsChanged(folderId);
                }
            }
        });
    }
    
    /**
     * Gets bookmarks for a specific folder.
     */
    public List<String> getFolderBookmarkKeys(int folderId) {
        FolderDataRepresentation folder = folders.get(folderId);
        return folder != null ? folder.getBookmarkKeys() : List.of();
    }
    
    // ===== INGREDIENT MANAGEMENT =====

    /**
     * Gets the IngredientService instance, initializing it if needed.
     */
    private IngredientService getIngredientService() {
        if (ingredientService == null) {
            ingredientService = JEIIntegrationFactory.getIngredientService();
        }
        return ingredientService;
    }

    /**
     * Gets the cached ingredients for a folder, processing them if not already cached.
     */
    public List<Object> getCachedIngredientsForFolder(int folderId) {
        Optional<FolderDataRepresentation> folder = getFolder(folderId);
        if (folder.isEmpty()) {
            return Collections.emptyList();
        }
        
        return new ArrayList<>(getIngredientService().getCachedIngredientsForFolder(folderId));
    }

    /**
     * Invalidates the ingredient cache for a specific folder.
     */
    public void invalidateIngredientsCache(int folderId) {
        getIngredientService().invalidateIngredientsCache(folderId);
    }

    /**
     * Invalidates the entire ingredient cache.
     */
    public void invalidateAllIngredientCaches() {
        ModLogger.debug("Invalidating all ingredient caches");
        
        IngredientService service = getIngredientService();
        
        if (service != null) {
            try {
                service.clearCache();
                ModLogger.debug("Ingredient cache cleared successfully");
            } catch (Exception e) {
                ModLogger.error("Error while clearing ingredient cache: {}", e.getMessage());
            }
        } else {
            ModLogger.debug("Cannot invalidate ingredient cache - ingredientService is unavailable");
        }
    }
    
    // ===== STORAGE OPERATIONS =====
    
    /**
     * Gets the appropriate save directory for the current game context.
     */
    private File getSaveDirectory() {
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
                ModLogger.error("Unable to find world name, using default folder");
                identifier = "all";
                subdir = "all";
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
     */
    private File createDirectory(File base, String path) {
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
     */
    private String sanitizeFileName(String name) {
        // Replace invalid filename characters with underscores
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    /**
     * Gets the file path for the folder data in the appropriate save directory
     */
    private File getDataFile(String filename) {
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
    
    /**
     * Loads folder data from the SNBT file.
     */
    public void loadData() {
        ModLogger.debug("Loading folder data...");
        
        // First, attempt to load raw data from file
        Optional<String> rawData = loadRawDataFromFile();
        
        // If loading failed or returned empty data, initialize with defaults
        if (rawData.isEmpty()) {
            initializeEmptyData();
            return;
        }
        
        // Process the raw data into our data model
        processDataContent(rawData.get());
        
        // Make sure caches are invalidated after loading
        invalidateAllIngredientCaches();
    }

    /**
     * Loads the raw string data from the SNBT file.
     */
    private Optional<String> loadRawDataFromFile() {
        // Get the data file
        File dataFile = getDataFile(FILENAME);
        if (dataFile == null) {
            ModLogger.warn("Cannot load folder data: No save directory available");
            return Optional.empty();
        }
        
        // Check if the file exists
        if (!dataFile.exists()) {
            ModLogger.info("No folder data file found at {}, will create new data", dataFile.getAbsolutePath());
            return Optional.empty();
        }
        
        // If file exists, try to read it
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
                ModLogger.warn("Data file exists but is empty");
                return Optional.empty();
            }
            
            double sizeKB = dataFile.length() / 1024.0;
            ModLogger.debug("Loaded data file ({} KB, {} chars) from {}", 
                String.format("%.2f", sizeKB), 
                content.length(), 
                dataFile.getName());
            
            return Optional.of(content);
        } catch (Exception e) {
            ModLogger.error("Failed to load folder data: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Initializes the service with empty data.
     */
    private void initializeEmptyData() {
        ModLogger.debug("Initializing with empty data");
        folders.clear();
        nextFolderId = 0;
    }

    /**
     * Processes the raw data content into folder objects.
     */
    private void processDataContent(String content) {
        try {
            // Parse SNBT string to CompoundTag
            CompoundTag rootTag = TagParser.parseTag(content);
            nextFolderId = rootTag.getInt("nextId");
            
            folders.clear();
            if (rootTag.contains("folders")) {
                ListTag folderList = rootTag.getList("folders", Tag.TAG_COMPOUND);
                ModLogger.debug("Loading {} folders from data", folderList.size());
                
                for (int i = 0; i < folderList.size(); i++) {
                    CompoundTag folderTag = folderList.getCompound(i);
                    FolderDataRepresentation folder = FolderDataRepresentation.fromNbt(folderTag);
                    folders.put(folder.getId(), folder);
                }
            } else {
                ModLogger.debug("No folders found in data");
            }
        } catch (Exception e) {
            ModLogger.error("Failed to process folder data content: {}", e.getMessage());
            // Initialize with empty data on parsing error
            initializeEmptyData();
        }
    }

    /**
     * Saves folder data to the SNBT file.
     */
    public void saveData() {
        ModLogger.debug("Saving folders data. Current folders: {}", folders.size());
        
        CompoundTag data = createDataTag();
        
        saveDataToFile(data);
    }

    /**
     * Creates a CompoundTag containing all folder data for saving.
     */
    private CompoundTag createDataTag() {
        CompoundTag rootTag = new CompoundTag();
        rootTag.putInt("nextId", nextFolderId);
        
        ListTag folderList = new ListTag();
        for (FolderDataRepresentation folder : folders.values()) {
            folderList.add(folder.toNbt());
        }
        rootTag.put("folders", folderList);
        
        return rootTag;
    }

    /**
     * Writes the provided data tag to the save file.
     */
    private void saveDataToFile(CompoundTag dataTag) {
        // Get the data file
        File saveFile = getDataFile(FILENAME);
        if (saveFile == null) {
            ModLogger.warn("Could not save folder data - no save directory available");
            return;
        }
        
        ModLogger.debug("Saving to file: {}", saveFile.getAbsolutePath());
        
        try {
            SnbtFormat.writeToFile(saveFile, dataTag);
            ModLogger.debug("Successfully saved folder data");
        } catch (IOException e) {
            ModLogger.error("Error saving folders data: {}", e.getMessage(), e);
        }
    }
}