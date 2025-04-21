package com.jeifolders.integration;

import com.jeifolders.gui.FolderExclusionHandler;
import com.jeifolders.util.ModLogger;

import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;

import java.util.Collection;
import java.util.Optional;

/**
 * JEI-specific implementation of the ExclusionHandlerInterface.
 * This class handles the direct interactions with JEI API.
 */
public class JEIExclusionHandler implements IGlobalGuiHandler {
    private final FolderExclusionHandler exclusionHandler;
    
    public JEIExclusionHandler(FolderExclusionHandler exclusionHandler) {
        this.exclusionHandler = exclusionHandler;
    }
    
    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        return exclusionHandler.getExclusionAreas();
    }
    
    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        return Optional.empty();
    }
}