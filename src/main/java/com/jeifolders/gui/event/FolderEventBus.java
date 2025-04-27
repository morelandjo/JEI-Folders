package com.jeifolders.gui.event;

import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A simple type-safe event bus for folder events.
 * Handles event registration and dispatching with improved type safety.
 */
public class FolderEventBus {
    // Map of event type to list of listeners for that type
    private final Map<FolderEventType, List<Consumer<FolderEvent>>> listeners = new EnumMap<>(FolderEventType.class);
    
    /**
     * Create a new event bus with initial listeners for all event types
     */
    public FolderEventBus() {
        // Initialize empty listener lists for each event type
        for (FolderEventType type : FolderEventType.values()) {
            listeners.put(type, new ArrayList<>());
        }
        ModLogger.debug("FolderEventBus initialized");
    }
    
    /**
     * Register a listener for a specific event type
     * 
     * @param type The event type to listen for
     * @param listener The callback to invoke when the event occurs
     */
    public void register(FolderEventType type, Consumer<FolderEvent> listener) {
        if (listener != null) {
            listeners.get(type).add(listener);
            ModLogger.debug("Added event listener for {}, total: {}", type, listeners.get(type).size());
        }
    }
    
    /**
     * Register a listener for all event types
     * 
     * @param listener The callback to invoke for any event
     */
    public void registerGlobal(Consumer<FolderEvent> listener) {
        if (listener != null) {
            for (FolderEventType type : FolderEventType.values()) {
                listeners.get(type).add(listener);
            }
            ModLogger.debug("Added global event listener");
        }
    }
    
    /**
     * Unregister a listener from a specific event type
     * 
     * @param type The event type to unregister from
     * @param listener The listener to remove
     */
    public void unregister(FolderEventType type, Consumer<FolderEvent> listener) {
        listeners.get(type).remove(listener);
        ModLogger.debug("Removed event listener for {}, remaining: {}", 
                       type, listeners.get(type).size());
    }
    
    /**
     * Unregister a listener from all event types
     * 
     * @param listener The listener to remove
     */
    public void unregisterGlobal(Consumer<FolderEvent> listener) {
        for (FolderEventType type : FolderEventType.values()) {
            listeners.get(type).remove(listener);
        }
        ModLogger.debug("Removed global event listener");
    }
    
    /**
     * Post an event to all registered listeners
     * 
     * @param event The event to dispatch
     */
    public void post(FolderEvent event) {
        FolderEventType type = event.getType();
        List<Consumer<FolderEvent>> typeListeners = listeners.get(type);
        
        if (typeListeners != null && !typeListeners.isEmpty()) {
            // Create a copy of the list to avoid concurrent modification issues
            List<Consumer<FolderEvent>> listenersCopy = new ArrayList<>(typeListeners);
            
            for (Consumer<FolderEvent> listener : listenersCopy) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    ModLogger.error("Error in folder event listener: {}", e.getMessage(), e);
                }
            }
            
            ModLogger.debug("Posted {} event to {} listeners", type, typeListeners.size());
        }
    }
    
    /**
     * Create and post an event in one step
     * 
     * @param source The event source object
     * @param type The event type
     * @return The created event object (can be used to add data)
     */
    public FolderEvent postEvent(Object source, FolderEventType type) {
        FolderEvent event = new FolderEvent(source, type);
        post(event);
        return event;
    }
}