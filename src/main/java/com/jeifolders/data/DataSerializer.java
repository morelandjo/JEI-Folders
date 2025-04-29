package com.jeifolders.data;

import com.jeifolders.util.ModLogger;
import com.jeifolders.util.SnbtFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.util.Optional;

/**
 * Handles serialization and deserialization of folder data to/from NBT format.
 */
public class DataSerializer {
    // Singleton instance
    private static DataSerializer instance;
    
    // NBT keys
    private static final String FOLDERS_KEY = "folders";
    private static final String ACTIVE_FOLDER_KEY = "activeFolder";
    
    /**
     * Private constructor for singleton pattern
     */
    private DataSerializer() {
        // No initialization needed
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized DataSerializer getInstance() {
        if (instance == null) {
            instance = new DataSerializer();
        }
        return instance;
    }
    
    /**
     * Deserializes SNBT string data into an NBT CompoundTag
     * 
     * @param snbtData The SNBT string data to parse
     * @return Optional containing the parsed CompoundTag, or empty on error
     */
    public Optional<CompoundTag> deserializeData(String snbtData) {
        if (snbtData == null || snbtData.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            CompoundTag rootTag = TagParser.parseTag(snbtData);
            return Optional.of(rootTag);
        } catch (Exception e) {
            ModLogger.error("Error parsing SNBT data: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Serializes folder data to SNBT string format
     * 
     * @param folderRepository The repository containing the folders to serialize
     * @return The SNBT string representation
     */
    public String serializeFolderRepository(FolderRepository folderRepository) {
        CompoundTag rootTag = createRootTag(folderRepository);
        return formatToSnbt(rootTag);
    }
    
    /**
     * Creates the root CompoundTag containing all data to be saved
     * 
     * @param folderRepository The repository containing the folders and state
     * @return The root tag with all data
     */
    public CompoundTag createRootTag(FolderRepository folderRepository) {
        CompoundTag rootTag = new CompoundTag();
        
        // Save folders
        ListTag foldersList = createFoldersListTag(folderRepository);
        rootTag.put(FOLDERS_KEY, foldersList);
        
        // Save active folder ID
        Integer activeId = folderRepository.getLastActiveFolderId();
        if (activeId != null) {
            rootTag.putInt(ACTIVE_FOLDER_KEY, activeId);
        }
        
        return rootTag;
    }
    
    /**
     * Creates a ListTag containing all folder data
     * 
     * @param folderRepository The repository containing the folders
     * @return The folders list tag
     */
    private ListTag createFoldersListTag(FolderRepository folderRepository) {
        ListTag foldersList = new ListTag();
        
        for (Folder folder : folderRepository.getAllFolders()) {
            CompoundTag folderTag = folder.toNbt();
            foldersList.add(folderTag);
        }
        
        return foldersList;
    }
    
    /**
     * Formats a CompoundTag to a pretty-printed SNBT string
     * 
     * @param tag The tag to format
     * @return The formatted SNBT string
     */
    public String formatToSnbt(CompoundTag tag) {
        return SnbtFormat.format(tag);
    }
    
    /**
     * Extracts a list of Folder objects from the NBT data
     * 
     * @param rootTag The root NBT tag
     * @return A FolderData object containing the parsed information
     */
    public FolderData extractDataFromNbt(CompoundTag rootTag) {
        FolderData data = new FolderData();
        
        // Extract folders if present
        if (rootTag.contains(FOLDERS_KEY, Tag.TAG_LIST)) {
            ListTag foldersList = rootTag.getList(FOLDERS_KEY, Tag.TAG_COMPOUND);
            
            for (int i = 0; i < foldersList.size(); i++) {
                CompoundTag folderTag = foldersList.getCompound(i);
                Folder folder = Folder.fromNbt(folderTag);
                
                if (folder != null) {
                    data.addFolder(folder);
                } else {
                    ModLogger.warn("Failed to parse folder at index {}", i);
                }
            }
            
            ModLogger.debug("Extracted {} folders from NBT data", data.getFolders().size());
        }
        
        // Extract active folder ID if present
        if (rootTag.contains(ACTIVE_FOLDER_KEY, Tag.TAG_INT)) {
            data.setLastActiveFolderId(rootTag.getInt(ACTIVE_FOLDER_KEY));
            ModLogger.debug("Extracted active folder ID: {}", data.getLastActiveFolderId());
        }
        
        return data;
    }
    
    /**
     * A data class to hold the parsed folder information
     */
    public static class FolderData {
        private final java.util.Map<Integer, Folder> folders = new java.util.HashMap<>();
        private Integer lastActiveFolderId = null;
        
        public void addFolder(Folder folder) {
            folders.put(folder.getId(), folder);
        }
        
        public java.util.Collection<Folder> getFolders() {
            return folders.values();
        }
        
        public java.util.Map<Integer, Folder> getFolderMap() {
            return folders;
        }
        
        public Integer getLastActiveFolderId() {
            return lastActiveFolderId;
        }
        
        public void setLastActiveFolderId(Integer id) {
            this.lastActiveFolderId = id;
        }
    }
}