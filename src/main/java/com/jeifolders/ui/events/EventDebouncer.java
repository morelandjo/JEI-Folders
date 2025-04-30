package com.jeifolders.ui.events;

import com.jeifolders.util.ModLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Utility class for debouncing events across the application.
 * Handles timing of events to prevent rapid-fire duplicate events.
 */
public class EventDebouncer {
    // Store last event time by key (usually folder ID)
    private final Map<Object, Long> lastEventTimes = new ConcurrentHashMap<>();
    
    // Default debounce time in milliseconds
    private final long defaultDebounceMs;
    
    /**
     * Creates a new event debouncer with the specified default debounce time.
     * 
     * @param defaultDebounceMs The default time in milliseconds to debounce events
     */
    public EventDebouncer(long defaultDebounceMs) {
        this.defaultDebounceMs = defaultDebounceMs;
    }
    
    /**
     * Determines if an event should be processed or debounced.
     * 
     * @param key The key representing the event source (e.g., folder ID)
     * @return true if the event should be processed, false if it should be skipped
     */
    public boolean shouldProcess(Object key) {
        return shouldProcess(key, defaultDebounceMs);
    }
    
    /**
     * Determines if an event should be processed or debounced with a custom debounce time.
     * 
     * @param key The key representing the event source (e.g., folder ID)
     * @param debounceMs The custom debounce time in milliseconds
     * @return true if the event should be processed, false if it should be skipped
     */
    public boolean shouldProcess(Object key, long debounceMs) {
        if (key == null) {
            // Always process events with null key (can't debounce)
            return true;
        }
        
        long currentTime = System.currentTimeMillis();
        Long lastEventTime = lastEventTimes.get(key);
        
        if (lastEventTime != null && currentTime - lastEventTime < debounceMs) {
            ModLogger.debug("[DEBOUNCE] Skipping event for key {} - too recent ({} ms)",
                     key, currentTime - lastEventTime);
            return false;
        }
        
        // Update the last event time and allow processing
        lastEventTimes.put(key, currentTime);
        return true;
    }
    
    /**
     * Execute an action if it passes the debouncing check.
     * 
     * @param key The key representing the event source (e.g., folder ID)
     * @param action The action to execute if not debounced
     * @return true if the action was executed, false if it was debounced
     */
    public boolean execute(Object key, Runnable action) {
        if (shouldProcess(key)) {
            action.run();
            return true;
        }
        return false;
    }
    
    /**
     * Execute an action with a parameter if it passes the debouncing check.
     * 
     * @param <T> The type of the parameter
     * @param key The key representing the event source (e.g., folder ID)
     * @param param The parameter to pass to the action
     * @param action The action to execute if not debounced
     * @return true if the action was executed, false if it was debounced
     */
    public <T> boolean execute(Object key, T param, Consumer<T> action) {
        if (shouldProcess(key)) {
            action.accept(param);
            return true;
        }
        return false;
    }
    
    /**
     * Clears debouncing history for the specified key.
     * 
     * @param key The key to reset
     */
    public void reset(Object key) {
        lastEventTimes.remove(key);
    }
    
    /**
     * Clears all debouncing history.
     */
    public void resetAll() {
        lastEventTimes.clear();
    }
}