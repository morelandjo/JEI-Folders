package com.jeifolders.data;

import com.jeifolders.util.ModLogger;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/**
 * Repository for managing folder data and state.
 * Centralizes folder data operations that were previously scattered in FolderStorageService.
 */
public class FolderRepository {
    // Singleton instance
    private static FolderRepository instance;
    
    // Data collections
    private final Map<Integer, Folder> folders = new HashMap<>();
    
    // Runtime state
    private Integer lastActiveFolderId = null;
    private boolean isLoaded = false;
    private boolean isDirty = false;
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderRepository() {
        // No initialization needed
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderRepository getInstance() {
        if (instance == null) {
            instance = new FolderRepository();
        }
        return instance;
    }
    
    /**
     * Clear all data in the repository
     */
    public void clear() {
        folders.clear();
        lastActiveFolderId = null;
        isLoaded = false;
    }
    
    /**
     * Get all folders
     * 
     * @return List of all folders
     */
    public List<Folder> getAllFolders() {
        return new ArrayList<>(folders.values());
    }
    
    /**
     * Get the internal folder map
     * 
     * @return Map of folder IDs to folders
     */
    public Map<Integer, Folder> getFoldersMap() {
        return folders;
    }
    
    /**
     * Get a folder by ID
     * 
     * @param id The folder ID
     * @return Optional containing the folder, or empty if not found
     */
    public Optional<Folder> getFolder(int id) {
        return Optional.ofNullable(folders.get(id));
    }
    
    /**
     * Get the last active folder ID
     * 
     * @return The last active folder ID, or null if none was active
     */
    public Integer getLastActiveFolderId() {
        return lastActiveFolderId;
    }
    
    /**
     * Set the last active folder ID
     * 
     * @param id The folder ID to set as active, or null to clear
     */
    public void setLastActiveFolderId(Integer id) {
        lastActiveFolderId = id;
        markDirty();
    }
    
    /**
     * Creates a new folder
     * 
     * @param name The name of the folder
     * @return The newly created folder
     */
    public Folder createFolder(String name) {
        // Find the next available ID
        int nextId = 1;
        while (folders.containsKey(nextId)) {
            nextId++;
        }
        
        // Create the new folder
        Folder folder = new Folder(nextId, name);
        folders.put(nextId, folder);
        
        markDirty();
        
        ModLogger.debug("Created new folder: {} (ID: {})", name, nextId);
        return folder;
    }
    
    /**
     * Deletes a folder
     * 
     * @param id The ID of the folder to delete
     * @return true if the folder was deleted, false if it didn't exist
     */
    public boolean deleteFolder(int id) {
        // Remove the folder
        Folder removed = folders.remove(id);
        if (removed != null) {
            // If this was the active folder, clear it
            if (lastActiveFolderId != null && lastActiveFolderId == id) {
                lastActiveFolderId = null;
            }
            
            markDirty();
            
            ModLogger.debug("Deleted folder: {} (ID: {})", removed.getName(), id);
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
        // Get the folder
        Folder folder = folders.get(id);
        if (folder != null) {
            // Update the name
            folder.setName(newName);
            
            markDirty();
            
            ModLogger.debug("Updated folder name: {} (ID: {})", newName, id);
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
        // Get the folder
        Folder folder = folders.get(folderId);
        if (folder != null) {
            // Add the bookmark
            if (folder.addBookmarkKey(bookmarkKey)) {
                markDirty();
                
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
        // Get the folder
        Folder folder = folders.get(folderId);
        if (folder != null) {
            // Remove the bookmark
            if (folder.removeBookmarkKey(bookmarkKey)) {
                markDirty();
                
                ModLogger.debug("Removed bookmark from folder {} (ID: {}): {}", folder.getName(), folderId, bookmarkKey);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets all bookmark keys across all folders
     * 
     * @return Set of all bookmark keys
     */
    public Set<String> getAllBookmarkKeys() {
        Set<String> allKeys = new HashSet<>();
        for (Folder folder : folders.values()) {
            allKeys.addAll(folder.getBookmarkKeys());
        }
        return allKeys;
    }
    
    /**
     * Load folder data from parsed NBT data
     * 
     * @param folderData The folder data extracted from NBT
     */
    public void loadFromFolderData(DataSerializer.FolderData folderData) {
        // Clear existing data
        clear();
        
        // Load folders
        folders.putAll(folderData.getFolderMap());
        
        // Load active folder ID
        lastActiveFolderId = folderData.getLastActiveFolderId();
        
        // Mark as loaded and clean
        isLoaded = true;
        isDirty = false;
    }
    
    /**
     * Mark repository as dirty (needing save)
     */
    public void markDirty() {
        isDirty = true;
    }
    
    /**
     * Mark repository as clean (saved)
     */
    public void markClean() {
        isDirty = false;
    }
    
    /**
     * Mark repository as loaded
     */
    public void markLoaded() {
        isLoaded = true;
    }
    
    /**
     * Check if repository is dirty (has unsaved changes)
     * 
     * @return true if there are unsaved changes
     */
    public boolean isDirty() {
        return isDirty;
    }
    
    /**
     * Check if repository is loaded
     * 
     * @return true if data is loaded
     */
    public boolean isLoaded() {
        return isLoaded;
    }
}