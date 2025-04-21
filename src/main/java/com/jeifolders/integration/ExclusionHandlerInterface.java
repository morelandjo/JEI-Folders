package com.jeifolders.integration;

import java.util.Collection;
import java.util.Optional;

/**
 * Interface that abstracts JEI's exclusion handling functionality.
 * This decouples our code from direct JEI dependencies.
 */
public interface ExclusionHandlerInterface {
    /**
     * Gets the extra areas that should be excluded from JEI's ingredient rendering
     */
    Collection<Object> getExtraAreas();
    
    /**
     * Gets an optional clickable ingredient under the mouse coordinates
     */
    Optional<Object> getClickableIngredientUnderMouse(double mouseX, double mouseY);
}