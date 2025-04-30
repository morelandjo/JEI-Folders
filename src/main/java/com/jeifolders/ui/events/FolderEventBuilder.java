package com.jeifolders.ui.events;

import com.jeifolders.data.Folder;

/**
 * Builder pattern for creating folder events.
 * Makes event creation more concise and less error-prone.
 */
public class FolderEventBuilder {
    private final Object source;
    private final FolderEventType type;
    private final FolderEvent event;
    
    /**
     * Start building an event of the specified type.
     * 
     * @param source The event source
     * @param type The event type
     */
    public FolderEventBuilder(Object source, FolderEventType type) {
        this.source = source;
        this.type = type;
        this.event = new FolderEvent(source, type);
    }
    
    /**
     * Add folder information to the event.
     * 
     * @param folder The folder
     * @return This builder for chaining
     */
    public FolderEventBuilder withFolder(Folder folder) {
        if (folder != null) {
            event.with("folder", folder)
                 .with("folderId", folder.getId());
        }
        return this;
    }
    
    /**
     * Add folder ID to the event.
     * 
     * @param folderId The folder ID
     * @return This builder for chaining
     */
    public FolderEventBuilder withFolderId(Integer folderId) {
        event.with("folderId", folderId);
        return this;
    }
    
    /**
     * Add ingredient information to the event.
     * 
     * @param ingredient The ingredient object
     * @return This builder for chaining
     */
    public FolderEventBuilder withIngredient(Object ingredient) {
        event.with("ingredient", ingredient);
        return this;
    }
    
    /**
     * Add bookmark key to the event.
     * 
     * @param key The bookmark key
     * @return This builder for chaining
     */
    public FolderEventBuilder withBookmarkKey(String key) {
        event.with("bookmarkKey", key);
        return this;
    }
    
    /**
     * Add folder name to the event.
     * 
     * @param name The folder name
     * @return This builder for chaining
     */
    public FolderEventBuilder withFolderName(String name) {
        event.with("folderName", name);
        return this;
    }
    
    /**
     * Add button information to the event.
     * 
     * @param button The folder button
     * @return This builder for chaining
     */
    public FolderEventBuilder withButton(Object button) {
        event.with("folderButton", button);
        return this;
    }
    
    /**
     * Add any custom data to the event.
     * 
     * @param key The data key
     * @param value The data value
     * @return This builder for chaining
     */
    public FolderEventBuilder withData(String key, Object value) {
        event.with(key, value);
        return this;
    }
    
    /**
     * Build the event.
     * 
     * @return The constructed FolderEvent
     */
    public FolderEvent build() {
        return event;
    }
}