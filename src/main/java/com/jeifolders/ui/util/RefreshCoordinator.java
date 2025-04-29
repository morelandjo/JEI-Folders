package com.jeifolders.ui.util;

import com.jeifolders.util.ModLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Coordinates refresh operations to prevent cascading refreshes
 * and reduce unnecessary UI updates.
 */
public class RefreshCoordinator {
    private static RefreshCoordinator instance;
    
    // Refresh state tracking
    private boolean isRefreshing = false;
    private long refreshStartTime = 0;
    private final Map<String, Long> lastComponentRefreshTimes = new HashMap<>();
    private final Map<Integer, RefreshStats> folderRefreshStats = new HashMap<>();
    
    // Constants for refresh control
    private static final long GLOBAL_MIN_REFRESH_INTERVAL_MS = 250;
    private static final long COMPONENT_MIN_REFRESH_INTERVAL_MS = 500;
    private static final long ADAPTIVE_REFRESH_TIMEOUT_MS = 2000;
    
    // Private constructor for singleton
    private RefreshCoordinator() {}
    
    // Get singleton instance
    public static synchronized RefreshCoordinator getInstance() {
        if (instance == null) {
            instance = new RefreshCoordinator();
        }
        return instance;
    }
    
    /**
     * Track statistics for each folder's refresh operations
     */
    private static class RefreshStats {
        long lastRefreshTime = 0;
        int consecutiveRefreshCount = 0;
        long adaptiveInterval = GLOBAL_MIN_REFRESH_INTERVAL_MS;
    }
    
    /**
     * Request permission to refresh a component
     * 
     * @param componentId Identifier for the requesting component
     * @param forceRefresh Whether to force refresh regardless of timing
     * @return true if refresh is allowed, false otherwise
     */
    public synchronized boolean canRefresh(String componentId, boolean forceRefresh) {
        long currentTime = System.currentTimeMillis();
        
        // If we're in the middle of a refresh operation and it's not forced
        if (isRefreshing && !forceRefresh) {
            // Only allow if the current refresh has been going on for too long
            if (currentTime - refreshStartTime < COMPONENT_MIN_REFRESH_INTERVAL_MS) {
                ModLogger.debug("[REFRESH-COORD] Blocking refresh request from {} - already refreshing (in progress for {} ms)", 
                    componentId, currentTime - refreshStartTime);
                return false;
            }
        }
        
        // Check component-specific throttling
        Long lastRefreshTime = lastComponentRefreshTimes.get(componentId);
        if (lastRefreshTime != null && !forceRefresh) {
            long sinceLastRefresh = currentTime - lastRefreshTime;
            if (sinceLastRefresh < COMPONENT_MIN_REFRESH_INTERVAL_MS) {
                ModLogger.debug("[REFRESH-COORD] Throttling refresh request from {} - too soon ({} ms)", 
                    componentId, sinceLastRefresh);
                return false;
            }
        }
        
        // Allow the refresh
        lastComponentRefreshTimes.put(componentId, currentTime);
        return true;
    }
    
    /**
     * Request permission to refresh a specific folder
     * Uses adaptive throttling that increases with consecutive refreshes
     * 
     * @param folderId The folder ID
     * @param forceRefresh Whether to force refresh regardless of timing
     * @return true if refresh is allowed, false otherwise
     */
    public synchronized boolean canRefreshFolder(int folderId, boolean forceRefresh) {
        long currentTime = System.currentTimeMillis();
        
        // Get or create stats for this folder
        RefreshStats stats = folderRefreshStats.computeIfAbsent(folderId, id -> new RefreshStats());
        
        // If force refresh, reset the stats but still record this refresh
        if (forceRefresh) {
            stats.consecutiveRefreshCount = 0;
            stats.adaptiveInterval = GLOBAL_MIN_REFRESH_INTERVAL_MS;
            stats.lastRefreshTime = currentTime;
            return true;
        }
        
        // Check if enough time has passed since the last refresh
        long timeSinceLastRefresh = currentTime - stats.lastRefreshTime;
        
        // Reset consecutive count if there's been a significant pause
        if (timeSinceLastRefresh > ADAPTIVE_REFRESH_TIMEOUT_MS) {
            stats.consecutiveRefreshCount = 0;
            stats.adaptiveInterval = GLOBAL_MIN_REFRESH_INTERVAL_MS;
            ModLogger.debug("[REFRESH-COORD] Resetting consecutive refresh counter for folder {} after {} ms of inactivity", 
                folderId, timeSinceLastRefresh);
        }
        
        // Check if we should allow this refresh based on the adaptive interval
        if (timeSinceLastRefresh < stats.adaptiveInterval) {
            ModLogger.debug("[REFRESH-COORD] Throttling refresh for folder {} - too soon ({} ms, required {} ms)", 
                folderId, timeSinceLastRefresh, stats.adaptiveInterval);
            return false;
        }
        
        // Update stats for this folder
        stats.lastRefreshTime = currentTime;
        stats.consecutiveRefreshCount++;
        
        // Increase the adaptive interval for consecutive refreshes
        // This will make the delay between refreshes longer if they're happening rapidly
        if (stats.consecutiveRefreshCount > 3) {
            // Cap at 2 seconds maximum interval
            stats.adaptiveInterval = Math.min(
                GLOBAL_MIN_REFRESH_INTERVAL_MS * (1 + stats.consecutiveRefreshCount / 2),
                2000
            );
            ModLogger.debug("[REFRESH-COORD] Increasing adaptive refresh interval for folder {} to {} ms (after {} consecutive refreshes)",
                folderId, stats.adaptiveInterval, stats.consecutiveRefreshCount);
        }
        
        return true;
    }
    
    /**
     * Mark the beginning of a refresh operation
     */
    public synchronized void beginRefresh() {
        isRefreshing = true;
        refreshStartTime = System.currentTimeMillis();
        ModLogger.debug("[REFRESH-COORD] Beginning refresh operation");
    }
    
    /**
     * Mark the end of a refresh operation
     */
    public synchronized void endRefresh() {
        isRefreshing = false;
        ModLogger.debug("[REFRESH-COORD] Completed refresh operation after {} ms", 
            System.currentTimeMillis() - refreshStartTime);
    }
    
    /**
     * Clear all refresh stats
     */
    public synchronized void reset() {
        isRefreshing = false;
        lastComponentRefreshTimes.clear();
        folderRefreshStats.clear();
        ModLogger.debug("[REFRESH-COORD] Refresh coordinator reset");
    }
}