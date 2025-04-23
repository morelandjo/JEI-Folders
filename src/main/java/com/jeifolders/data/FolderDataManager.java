package com.jeifolders.data;

import com.jeifolders.util.SnbtFormat;
import com.jeifolders.util.ModLogger;
import com.jeifolders.gui.folderButtons.FolderChangeListener;
import com.jeifolders.integration.IngredientService;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.JEIService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages backend folder data, including CRUD operations, bookmark management,
 * and data persistence (saving/loading to SNBT files).
 */
public class FolderDataManager {
    private static final String FILENAME = "jeifolders.snbt";
    private static final FolderDataManager INSTANCE = new FolderDataManager();
    
    private final Map<Integer, FolderDataRepresentation> folders = new HashMap<>();
    private int nextFolderId = 0;
    
    // Use service interfaces instead of direct dependencies
    private JEIService jeiService = JEIIntegrationFactory.getJEIService();
    private IngredientService ingredientService = JEIIntegrationFactory.getIngredientService();
    
    // Add listeners list for folder changes
    private final List<FolderChangeListener> folderChangeListeners = new CopyOnWriteArrayList<>();
    
    private FolderDataManager() {
        // Private constructor for singleton
    }
    
    public static FolderDataManager getInstance() {
        return INSTANCE;
    }
    
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
    
    /**
     * Adds a bookmark to a folder and saves data.
     * Uses Optional for cleaner null handling.
     */
    public void addBookmarkToFolder(int folderId, String bookmarkKey) {
        getFolder(folderId).ifPresentOrElse(
            folder -> {
                // Folder exists, add the bookmark
                folder.addBookmarkKey(bookmarkKey);
                invalidateIngredientsCache(folderId);
                saveData();
                notifyFolderContentsChanged(folderId);
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
                notifyFolderContentsChanged(folderId);
            }
        });
    }
    
    /**
     * Registers a listener for folder content changes
     */
    public void addFolderChangeListener(FolderChangeListener listener) {
        if (!folderChangeListeners.contains(listener)) {
            folderChangeListeners.add(listener);
        }
    }
    
    /**
     * Removes a folder change listener
     */
    public void removeFolderChangeListener(FolderChangeListener listener) {
        folderChangeListeners.remove(listener);
    }
    
    /**
     * Notifies all listeners that a folder's contents have changed.
     */
    private void notifyFolderContentsChanged(int folderId) {
        if (folderChangeListeners.isEmpty()) {
            return;
        }
        
        ModLogger.debug("Notifying {} listeners about change to folder ID: {}", 
                folderChangeListeners.size(), folderId);
                
        folderChangeListeners.forEach(listener -> 
            notifyListenerSafely(listener, folderId));
    }

    /**
     * Safely notifies a listener about folder content changes,
     * handling any exceptions that might occur.
     * 
     * @param listener The listener to notify
     * @param folderId The ID of the folder that changed
     */
    private void notifyListenerSafely(FolderChangeListener listener, int folderId) {
        try {
            listener.onFolderContentsChanged(folderId);
        } catch (Exception e) {
            ModLogger.error("Error in folder change listener ({}): {}", 
                listener.getClass().getSimpleName(), e.getMessage());
        }
    }
    
    /**
     * Gets bookmarks for a specific folder.
     */
    public List<String> getFolderBookmarkKeys(int folderId) {
        FolderDataRepresentation folder = folders.get(folderId);
        return folder != null ? folder.getBookmarkKeys() : List.of();
    }

    /**
     * Gets the JEIService instance, initializing it if needed.
     * @return The JEIService instance
     */
    private JEIService getJeiService() {
        if (jeiService == null) {
            jeiService = JEIIntegrationFactory.getJEIService();
        }
        return jeiService;
    }

    /**
     * Gets the IngredientService instance, initializing it if needed.
     * @return The IngredientService instance
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
        // Delegate to the IngredientService
        Optional<FolderDataRepresentation> folder = getFolder(folderId);
        if (!folder.isPresent()) {
            return Collections.emptyList();
        }
        
        // Call the service implementation but return generic List<Object> instead
        // to avoid exposing JEI types in the data layer
        return new ArrayList<>(getIngredientService().getCachedIngredientsForFolder(folderId));
    }

    /**
     * Invalidates the ingredient cache for a specific folder.
     */
    public void invalidateIngredientsCache(int folderId) {
        // Delegate to the IngredientService
        getIngredientService().invalidateIngredientsCache(folderId);
    }

    /**
     * Invalidates the entire ingredient cache.
     * Uses lazy initialization to ensure the service is available.
     */
    public void invalidateAllIngredientCaches() {
        ModLogger.debug("Invalidating all ingredient caches");
        
        // Get the ingredient service using the lazy getter
        IngredientService service = getIngredientService();
        
        // Try to clear cache if service is available
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
     * 
     * @return An Optional containing the file content, or empty if loading failed
     */
    private Optional<String> loadRawDataFromFile() {
        // Use FolderStorageManager to get the data file
        File dataFile = FolderDataStorageManager.getDataFile(FILENAME);
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
            
            // Log file details in a single message
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
     * Initializes the manager with empty data.
     */
    private void initializeEmptyData() {
        ModLogger.debug("Initializing with empty data");
        folders.clear();
        nextFolderId = 0;
    }

    /**
     * Processes the raw data content into folder objects.
     * 
     * @param content The raw SNBT content to process
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
        
        // First, prepare the data for saving
        CompoundTag data = createDataTag();
        
        // Then save the data to file
        saveDataToFile(data);
    }

    /**
     * Creates a CompoundTag containing all folder data for saving.
     *
     * @return The CompoundTag representing all folder data
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
     *
     * @param dataTag The CompoundTag containing the data to save
     */
    private void saveDataToFile(CompoundTag dataTag) {
        // Use FolderStorageManager to get the data file
        File saveFile = FolderDataStorageManager.getDataFile(FILENAME);
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
