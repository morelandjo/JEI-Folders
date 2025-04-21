package com.jeifolders.integration;

import com.jeifolders.data.FolderDataManager;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * Helper class for managing JEI runtime-related operations.
 * Centralizes all JEI runtime interactions to keep them in the integration layer.
 */
public class JeiRuntimeHelper {
    private static final JEIService jeiService = JEIIntegrationFactory.getJEIService();
    
    private JeiRuntimeHelper() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Initializes the JEI runtime and sets up all necessary components.
     * This should be called when the JEI runtime becomes available.
     * 
     * @param runtime The JEI runtime instance
     */
    public static void initializeJeiRuntime(IJeiRuntime runtime) {
        ModLogger.info("Initializing JEI runtime in JeiRuntimeHelper");
        
        // Set the runtime in the JEIService
        jeiService.setJeiRuntime(runtime);
        
        // Any other runtime initialization can be centralized here
        
        // Request data to be loaded now that JEI is available
        FolderDataManager.getInstance().loadData();
    }
    
    /**
     * Checks if the JEI runtime is currently available
     * 
     * @return true if JEI runtime is available, false otherwise
     */
    public static boolean isJeiRuntimeAvailable() {
        return jeiService.getJeiRuntime().isPresent();
    }
}