package com.jeifolders.gui.view.render;

import com.jeifolders.gui.common.MouseHitUtil;
import com.jeifolders.gui.view.contents.FolderContentsView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.jeifolders.util.ModLogger;

/**
 * Specialized renderer for folder content views (bookmarks).
 * This class separates rendering logic from the content view's data management.
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
                if (isMouseOverContentsView(contentsView, mouseX, mouseY)) {
                    contentsView.getContentsImpl().drawTooltips(Minecraft.getInstance(), graphics, mouseX, mouseY);
                }
            } catch (Exception e) {
                ModLogger.error("Error rendering bookmark display: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Checks if the mouse is over the contents view, using the centralized MouseHitUtil
     * 
     * @param contentsView The contents view to check
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @return true if the mouse is over the contents view
     */
    public boolean isMouseOverContentsView(FolderContentsView contentsView, double mouseX, double mouseY) {
        return MouseHitUtil.isMouseOverContentView(
            mouseX, mouseY,
            contentsView.getX(),
            contentsView.getY(),
            contentsView.getWidth(),
            contentsView.getHeight(),
            contentsView.getBackgroundArea()
        );
    }
}