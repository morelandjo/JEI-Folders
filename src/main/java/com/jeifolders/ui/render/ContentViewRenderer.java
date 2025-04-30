package com.jeifolders.ui.render;

import com.jeifolders.ui.components.contents.FolderContentsView;
import com.jeifolders.ui.util.TooltipRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.jeifolders.util.ModLogger;

/**
 * Specialized renderer for folder content views (bookmarks).
 */
public class ContentViewRenderer {
    /**
     * Renders the folder contents display with its current state
     * 
     * @param contentsView The contents view to render
     * @param graphics The GUI graphics context
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param partialTick Partial tick for smooth animations
     */
    public void renderContentsView(FolderContentsView contentsView, 
                                 GuiGraphics graphics, 
                                 int mouseX, int mouseY, 
                                 float partialTick) {
        // If the view needs refresh, do that first
        if (contentsView.needsRefresh()) {
            contentsView.refreshBookmarks();
        }
        
        // Only render if there's an active folder
        if (contentsView.getActiveFolder() != null) {
            try {
                // Draw the main content
                contentsView.getContentsImpl().draw(Minecraft.getInstance(), graphics, mouseX, mouseY, partialTick);
                
                // Only draw tooltips if mouse is over the display
                if (contentsView.isMouseOver(mouseX, mouseY)) {
                    // Use the centralized TooltipRenderer instead of calling drawTooltips directly
                    TooltipRenderer.renderIngredientTooltip(
                        Minecraft.getInstance(),
                        graphics,
                        mouseX,
                        mouseY,
                        () -> contentsView.getContentsImpl().drawTooltips(Minecraft.getInstance(), graphics, mouseX, mouseY)
                    );
                }
            } catch (Exception e) {
                ModLogger.error("Error rendering bookmark display: {}", e.getMessage(), e);
            }
        }
    }
}