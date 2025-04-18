package com.jeifolders.data;

import com.jeifolders.JEIFolders;
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
public class Folder {
    private final int id;
    private String name;
    private final List<String> bookmarkKeys = new ArrayList<>();
    
    public Folder(int id, String name) {
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
        // Check for duplicates before adding
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
    public static Folder fromNbt(CompoundTag tag) {
        int id = tag.getInt("id");
        String name = tag.getString("name");
        Folder folder = new Folder(id, name);

        if (tag.contains("bookmarks")) {
            ListTag bookmarksTag = tag.getList("bookmarks", Tag.TAG_STRING);
            for (int i = 0; i < bookmarksTag.size(); i++) {
                folder.bookmarkKeys.add(bookmarksTag.getString(i)); // Direct add to avoid redundant checks for initial loading
            }
        }

        ModLogger.debug("Loaded folder: ID={}, Name={}, Bookmarks={}", id, name, folder.bookmarkKeys.size());
        return folder;
    }
}
