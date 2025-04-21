package com.jeifolders.util;

import com.jeifolders.integration.JEIRuntimeWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages access to the JEI runtime without direct JEI dependencies.
 */
public class JeiRuntimeManager {
    private static final JEIRuntimeWrapper runtimeWrapper = new JEIRuntimeWrapper();
    private static final List<Consumer<Object>> pendingObservers = new ArrayList<>();
    
    /**
     * Sets the JEI runtime and notifies all registered observers
     */
    public static void setRuntime(Object runtime) {
        runtimeWrapper.setRuntime(runtime);
        
        // Call any pending observers that were registered before the runtime was available
        synchronized (pendingObservers) {
            for (Consumer<Object> observer : pendingObservers) {
                observer.accept(runtime);
            }
            pendingObservers.clear();
        }
    }
    
    /**
     * Register an observer to be notified when the JEI runtime is available
     */
    public static void registerObserver(Consumer<Object> observer) {
        if (runtimeWrapper.isRuntimeAvailable()) {
            // JEI runtime is already available, notify immediately
            runtimeWrapper.getJeiRuntime().ifPresent(observer::accept);
        } else {
            // JEI runtime not available yet, store observer for later notification
            synchronized (pendingObservers) {
                pendingObservers.add(observer);
            }
        }
    }
}