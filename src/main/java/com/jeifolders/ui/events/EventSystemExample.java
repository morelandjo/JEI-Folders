package com.jeifolders.ui.events;

import com.jeifolders.data.Folder;
import com.jeifolders.events.FolderEventDispatcher;
import com.jeifolders.integration.BookmarkIngredient;
import com.jeifolders.util.ModLogger;

import java.util.function.Consumer;

/**
 * Examples of how to use the improved event system.
 * This class provides reference implementations for common event scenarios.
 */
public class EventSystemExample {
    
    private final FolderEventDispatcher eventDispatcher;
    private final EventDebouncer debouncer;
    
    // Store event listeners as fields so they can be unregistered later
    private final Consumer<FolderEvent> folderClickedListener;
    private final Consumer<FolderEvent> bookmarkAddedListener;
    
    public EventSystemExample(FolderEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
        this.debouncer = new EventDebouncer(250); // 250ms debounce time
        
        // Example 1: Simple event listener with builder pattern
        this.folderClickedListener = event -> {
            Folder folder = event.getFolder();
            if (folder != null) {
                ModLogger.debug("Folder clicked: {} (ID: {})", folder.getName(), folder.getId());
            }
        };
        
        // Example 2: Event listener with debouncing
        this.bookmarkAddedListener = event -> {
            Integer folderId = event.getFolderId();
            // Fix: Cast the ingredient to BookmarkIngredient properly
            Object rawIngredient = event.get("ingredient");
            String bookmarkKey = event.getBookmarkKey();
            
            // Only process this event if it passes debouncing
            if (debouncer.shouldProcess(folderId)) {
                // Fix: Get a displayable string safely without assuming a getDisplayName method
                String ingredientName = "unknown";
                if (rawIngredient instanceof BookmarkIngredient) {
                    BookmarkIngredient ingredient = (BookmarkIngredient) rawIngredient;
                    // Get a string representation of the ingredient
                    ingredientName = ingredient.toString();
                }
                
                ModLogger.debug("Bookmark added to folder {}: {} with key {}", 
                    folderId, 
                    ingredientName, 
                    bookmarkKey);
                
                // Additional processing would go here
            }
        };
        
        // Register the listeners
        eventDispatcher.addEventListener(FolderEventType.FOLDER_CLICKED, folderClickedListener);
        eventDispatcher.addEventListener(FolderEventType.BOOKMARK_ADDED, bookmarkAddedListener);
    }
    
    /**
     * Example of using the generic event fire method with builder
     */
    public void fireCustomEvent(Folder folder, Object ingredient) {
        // Create an event with additional custom data
        eventDispatcher.fire(FolderEventType.BOOKMARK_CLICKED)
            .withFolder(folder)
            .withIngredient(ingredient)
            .withData("timestamp", System.currentTimeMillis())
            .withData("source", "EventSystemExample")
            .build();
    }
    
    /**
     * Example of using debouncer to conditionally execute code
     */
    public void handleFrequentEvent(int folderId, Runnable action) {
        // The debouncer can also execute the action directly if it passes the check
        if (debouncer.shouldProcess("folder-" + folderId)) {
            action.run();
        }
    }
    
    /**
     * Example of cleaning up resources to prevent memory leaks
     */
    public void cleanup() {
        // Always unregister listeners when they're no longer needed
        eventDispatcher.removeEventListener(FolderEventType.FOLDER_CLICKED, folderClickedListener);
        eventDispatcher.removeEventListener(FolderEventType.BOOKMARK_ADDED, bookmarkAddedListener);
    }
}