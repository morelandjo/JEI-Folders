// filepath: /Users/josh/IdeaProjects/JEI-Folders/JEI-Folders/src/main/java/com/jeifolders/integration/handlers/FolderAreaContainerHandler.java
package com.jeifolders.integration.handlers;

import com.jeifolders.ui.controllers.FolderUIController;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Container handler specifically for folder areas.
 * This handler informs JEI about areas that should be excluded from its UI coverage.
 */
public class FolderAreaContainerHandler<T extends AbstractContainerScreen<?>> implements IGuiContainerHandler<T> {
    @Override
    @Nonnull
    public List<Rect2i> getGuiExtraAreas(@Nonnull T containerScreen) {
        List<Rect2i> areas = new ArrayList<>();

        // Add the folder button exclusion zone if available
        if (FolderUIController.lastDrawnArea.getWidth() > 0 && FolderUIController.lastDrawnArea.getHeight() > 0) {
            areas.add(FolderUIController.lastDrawnArea);
        }

        return areas;
    }
}