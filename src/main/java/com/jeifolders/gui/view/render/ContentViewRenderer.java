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
     * Checks if the mouse is over the contents view, using the same logic
     * as the original isMouseOver method but in our centralized renderer
     * 
     * @param contentsView The contents view to check
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @return true if the mouse is over the contents view
     */
    public boolean isMouseOverContentsView(FolderContentsView contentsView, double mouseX, double mouseY) {
        int x = contentsView.getX();
        int y = contentsView.getY();
        int width = contentsView.getWidth();
        int height = contentsView.getHeight();
        
        // Basic check if mouse is over the current display bounds
        boolean overCurrentBounds = MouseHitUtil.isMouseOverRect(mouseX, mouseY, x, y, width, height);
        
        // If this is already true, no need for additional checks
        if (overCurrentBounds) {
            return true;
        }
        
        // For drag operations, check against the background area if available
        var backgroundArea = contentsView.getBackgroundArea();
        if (backgroundArea != null && !backgroundArea.isEmpty()) {
            // Add extended margins to the background area for easier drag and drop
            boolean overBackground = MouseHitUtil.isMouseOverDragDropArea(mouseX, mouseY, backgroundArea);
            
            if (overBackground) {
                return true;
            }
        }
        
        // For drag and drop, be even more lenient with the main display bounds
        boolean inExtendedArea = MouseHitUtil.isMouseOverDragDropArea(mouseX, mouseY, x, y, width, height);
        return inExtendedArea;
    }
}