package com.jeifolders.data;

import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Represents a JEI folder with a unique ID and name.
 */
public class FolderDataRepresentation {
    private final int id;
    private String name;
    private final List<String> bookmarkKeys = new ArrayList<>();
    
    public FolderDataRepresentation(int id, String name) {
        this.id = id;
        this.name = name;
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Add a bookmark key to this folder
     */
    public void addBookmarkKey(String key) {
        if (!bookmarkKeys.contains(key)) {
            bookmarkKeys.add(key);
            ModLogger.debug("Added bookmark key '{}' to folder '{}', now has {} bookmarks", 
                key, name, bookmarkKeys.size());
        }
    }
    
    /**
     * Removes a bookmark from this folder.
     */
    public boolean removeBookmark(String bookmarkKey) {
        boolean removed = bookmarkKeys.remove(bookmarkKey);
        if (removed) {
            ModLogger.debug("Removed bookmark '{}' from folder '{}'", bookmarkKey, name);
        }
        return removed;
    }
    
    /**
     * Get a copy of the bookmark keys to avoid modification issues
     */
    public List<String> getBookmarkKeys() {
        return new ArrayList<>(bookmarkKeys);
    }
    
    /**
     * Checks if the folder contains the given bookmark.
     */
    public boolean containsBookmark(String bookmarkKey) {
        return bookmarkKeys.contains(bookmarkKey);
    }
    
    /**
     * Clears all bookmarks from this folder.
     */
    public void clearBookmarks() {
        if (!bookmarkKeys.isEmpty()) {
            ModLogger.debug("Cleared {} bookmarks from folder '{}'", bookmarkKeys.size(), name);
            bookmarkKeys.clear();
        }
    }
    
    /**
     * Serializes the folder to an NBT tag.
     */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        
        // Serialize bookmarks
        ListTag bookmarksTag = new ListTag();
        for (String bookmarkKey : bookmarkKeys) {
            bookmarksTag.add(StringTag.valueOf(bookmarkKey));
        }
        tag.put("bookmarks", bookmarksTag);
        
        return tag;
    }
    
    /**
     * Creates a Folder object from an NBT tag.
     */
    public static FolderDataRepresentation fromNbt(CompoundTag tag) {
        int id = tag.getInt("id");
        String name = tag.getString("name");
        FolderDataRepresentation folder = new FolderDataRepresentation(id, name);

        if (tag.contains("bookmarks")) {
            ListTag bookmarksTag = tag.getList("bookmarks", Tag.TAG_STRING);
            for (int i = 0; i < bookmarksTag.size(); i++) {
                folder.bookmarkKeys.add(bookmarksTag.getString(i));
            }
        }

        ModLogger.debug("Loaded folder: ID={}, Name={}, Bookmarks={}", id, name, folder.bookmarkKeys.size());
        return folder;
    }
}
