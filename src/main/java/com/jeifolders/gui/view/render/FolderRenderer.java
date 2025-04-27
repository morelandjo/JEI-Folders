package com.jeifolders.gui.view.render;

import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.layout.FolderLayoutService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

/**
 * Facade for the UI rendering system. 
 * Delegates actual rendering to the UIRenderManager.
 */
public class FolderRenderer {
    private final UIRenderManager renderManager;
    
    /**
     * Creates a new folder renderer
     * 
     * @param folderManager The folder state manager
     * @param layoutService The layout service
     */
    public FolderRenderer(FolderStateManager folderManager, FolderLayoutService layoutService) {
        this.renderManager = new UIRenderManager(folderManager, layoutService);
    }
    
    /**
     * Main rendering method for the folder UI
     */
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderManager.renderUI(graphics, mouseX, mouseY, partialTick);
    }
    
    /**
     * Checks if the delete button is currently hovered
     */
    public boolean isDeleteButtonHovered() {
        return renderManager.isDeleteButtonHovered();
    }
    
    /**
     * Gets the current X position of the delete button
     */
    public int getCurrentDeleteButtonX() {
        return renderManager.getCurrentDeleteButtonX();
    }
    
    /**
     * Gets the current exclusion zone from the layout service
     */
    public Rect2i getExclusionZone() {
        return renderManager.getExclusionZone();
    }
}