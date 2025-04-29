package com.jeifolders.integration;

import com.jeifolders.util.ModLogger;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Wraps the JEI runtime functionality to avoid direct JEI imports outside the integration package.
 * This acts as a facade for JEI runtime operations.
 */
public class JEIRuntimeWrapper {
    // The actual runtime will be managed by the JEIService
    private final JEIService jeiService;
    
    public JEIRuntimeWrapper() {
        this.jeiService = JEIIntegrationFactory.getJEIService();
    }
    
    /**
     * Gets the JEI runtime if it's available
     * @return An Optional containing the JEI runtime object if available
     */
    public Optional<Object> getJeiRuntime() {
        return jeiService.getJeiRuntime();
    }
    
    /**
     * Sets the JEI runtime object (used internally by integration code)
     * @param runtime The JEI runtime object (IJeiRuntime)
     */
    public void setRuntime(Object runtime) {
        if (runtime == null) {
            ModLogger.warn("Attempted to set null JEI runtime");
            return;
        }
        
        try {
            // Cast safely within the integration layer
            jeiService.setJeiRuntime(runtime);
        } catch (ClassCastException e) {
            ModLogger.error("Invalid runtime object provided: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Register a callback to be executed when the JEI runtime becomes available.
     * The callback will receive an Object that represents the JEI runtime.
     */
    public void registerObserver(Consumer<Object> callback) {
        if (callback == null) {
            return;
        }
        
        jeiService.registerRuntimeCallback(callback);
    }
    
    /**
     * Checks if the JEI runtime is available
     * @return true if the runtime is available
     */
    public boolean isRuntimeAvailable() {
        return jeiService.getJeiRuntime().isPresent();
    }
}