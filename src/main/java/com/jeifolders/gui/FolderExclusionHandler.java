package com.jeifolders.gui;

import com.jeifolders.JEIFolders;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Handles exclusion areas for folder bookmarks in JEI.
 */
public class FolderExclusionHandler implements IGlobalGuiHandler {
    private final Set<ImmutableRect2i> exclusionAreas = new HashSet<>();
    
    public void addExclusionArea(ImmutableRect2i area) {
        if (!area.isEmpty()) {
            ModLogger.debug("Adding exclusion area: {}", area);
            exclusionAreas.add(area);
        }
    }
    
    public void removeExclusionArea(ImmutableRect2i area) {
        ModLogger.debug("Removing exclusion area: {}", area);
        exclusionAreas.remove(area);
    }
    
    public void clearExclusionAreas() {
        ModLogger.debug("Clearing all exclusion areas");
        exclusionAreas.clear();
    }

    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        if (exclusionAreas.isEmpty()) {
            return Collections.emptyList();
        }
        
        Collection<Rect2i> result = new ArrayList<>(exclusionAreas.size());
        for (ImmutableRect2i area : exclusionAreas) {
            result.add(new Rect2i(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        }
        return result;
    }
    
    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        return Optional.empty();
    }
}
