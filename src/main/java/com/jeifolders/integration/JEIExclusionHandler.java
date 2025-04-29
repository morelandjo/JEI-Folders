package com.jeifolders.integration;

import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import com.jeifolders.ui.util.ExclusionHandler;
import com.jeifolders.util.ModLogger;

/**
 * JEI-specific implementation of the ExclusionHandlerInterface.
 * This class handles the direct interactions with JEI API.
 */
public class JEIExclusionHandler implements IGlobalGuiHandler {
    private final ExclusionHandler exclusionHandler;
    
    public JEIExclusionHandler(ExclusionHandler exclusionHandler) {
        this.exclusionHandler = exclusionHandler;
    }
    
    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        Collection<Rect2i> areas = exclusionHandler.getExclusionAreas();
        
        // Debug log to verify exclusion areas
        if (!areas.isEmpty()) {
            for (Rect2i area : areas) {
                ModLogger.debug("JEI exclusion area provided: x={}, y={}, width={}, height={}", 
                    area.getX(), area.getY(), area.getWidth(), area.getHeight());
            }
        }
        
        return areas;
    }
    
    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        return Optional.empty();
    }
}