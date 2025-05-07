package com.jeifolders.integration.handlers;

import com.jeifolders.ui.util.UiExclusionHandler;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;

import java.util.Collection;
import java.util.Optional;

/**
 * Adapts our ExclusionHandler to JEI's IGlobalGuiHandler interface.
 * This allows JEI to recognize areas where it shouldn't draw elements.
 */
public class JEIExclusionHandler implements IGlobalGuiHandler {
    private final UiExclusionHandler exclusionHandler;

    /**
     * Create a JEI-compatible exclusion handler
     * 
     * @param exclusionHandler The base exclusion handler to delegate to
     */
    public JEIExclusionHandler(UiExclusionHandler exclusionHandler) {
        this.exclusionHandler = exclusionHandler;
    }

    /**
     * Give JEI information about extra space that our mod takes up.
     * Used for moving JEI out of the way of our folder UI elements.
     */
    @Override
    public Collection<Rect2i> getGuiExtraAreas() {
        // Get exclusion areas from our handler
        return exclusionHandler.getExclusionAreas();
    }

    /**
     * Return a clickable ingredient under the mouse.
     * We don't have any additional clickable ingredients to report to JEI.
     */
    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        // We don't have any clickable ingredients to add to JEI
        return Optional.empty();
    }
}