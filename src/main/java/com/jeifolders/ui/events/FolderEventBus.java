package com.jeifolders.ui.events;

import com.jeifolders.util.ModLogger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Type-safe event bus for folder events.
 * Provides a centralized mechanism for components to communicate via events.
 */
public class FolderEventBus {
    // Map of event type to list of listeners for that type
    private final Map<FolderEventType, List<Consumer<FolderEvent>>> listeners = new EnumMap<>(FolderEventType.class);
    
    // Flag to enable detailed event logging
    private boolean detailedLogging = false;
    
    /**
     * Create a new event bus with initial listeners for all event types
     */
    public FolderEventBus() {
        // Initialize empty thread-safe listener lists for each event type
        for (FolderEventType type : FolderEventType.values()) {
            listeners.put(type, new CopyOnWriteArrayList<>());
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
            if (detailedLogging) {
                ModLogger.debug("Added event listener for {}, total: {}", type, listeners.get(type).size());
            }
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
            if (detailedLogging) {
                ModLogger.debug("Added global event listener");
            }
        }
    }
    
    /**
     * Unregister a listener from a specific event type
     * 
     * @param type The event type to unregister from
     * @param listener The listener to remove
     */
    public void unregister(FolderEventType type, Consumer<FolderEvent> listener) {
        if (type != null && listener != null) {
            listeners.get(type).remove(listener);
            if (detailedLogging) {
                ModLogger.debug("Removed event listener for {}, remaining: {}", 
                              type, listeners.get(type).size());
            }
        }
    }
    
    /**
     * Unregister a listener from all event types
     * 
     * @param listener The listener to remove
     */
    public void unregisterGlobal(Consumer<FolderEvent> listener) {
        if (listener != null) {
            for (FolderEventType type : FolderEventType.values()) {
                listeners.get(type).remove(listener);
            }
            if (detailedLogging) {
                ModLogger.debug("Removed global event listener");
            }
        }
    }
    
    /**
     * Post an event to all registered listeners.
     * This method is thread-safe and will catch exceptions from individual listeners
     * without disrupting other listeners.
     * 
     * @param event The event to dispatch
     */
    public void post(FolderEvent event) {
        if (event == null) {
            ModLogger.warn("Attempted to post null event");
            return;
        }
        
        FolderEventType type = event.getType();
        List<Consumer<FolderEvent>> typeListeners = listeners.get(type);
        
        if (typeListeners != null && !typeListeners.isEmpty()) {
            if (detailedLogging) {
                ModLogger.debug("Posting {} event to {} listeners", type, typeListeners.size());
            }
            
            // We're using CopyOnWriteArrayList, so no need to create a defensive copy
            for (Consumer<FolderEvent> listener : typeListeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    ModLogger.error("Error in folder event listener for type {}: {}", 
                                  type, e.getMessage(), e);
                }
            }
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
    
    /**
     * Enable or disable detailed event logging
     * 
     * @param enable True to enable detailed logging, false to disable
     */
    public void setDetailedLogging(boolean enable) {
        this.detailedLogging = enable;
    }
    
    /**
     * Clear all event listeners
     */
    public void clearAllListeners() {
        for (FolderEventType type : FolderEventType.values()) {
            listeners.get(type).clear();
        }
        ModLogger.debug("Cleared all event listeners");
    }
}