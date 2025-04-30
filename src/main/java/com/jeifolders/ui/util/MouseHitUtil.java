package com.jeifolders.ui.util;

import com.jeifolders.integration.Rectangle2i;
import com.jeifolders.ui.components.buttons.FolderButton;

import net.minecraft.client.renderer.Rect2i;

/**
 * Centralized utility for mouse hit detection across the UI.
 */
public final class MouseHitUtil {
    
    // Prevent instantiation
    private MouseHitUtil() {}
    
    /**
     * Checks if mouse coordinates are over a rectangular area.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param x X coordinate of the rectangle
     * @param y Y coordinate of the rectangle
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     * @return true if the mouse is over the rectangle
     */
    public static boolean isMouseOverRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && 
               mouseY >= y && mouseY < y + height;
    }
    
    /**
     * Checks if mouse coordinates are over a HitTestable component.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param component Any component implementing the HitTestable interface
     * @return true if the mouse is over the component
     */
    public static boolean isMouseOver(double mouseX, double mouseY, HitTestable component) {
        if (component == null) return false;
        return component.isMouseOver(mouseX, mouseY);
    }
    
    /**
     * Checks if mouse coordinates are over a HitTestable component with extended hit area.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param component Any component implementing the HitTestable interface
     * @return true if the mouse is over the component's extended hit area
     */
    public static boolean isMouseOverExtended(double mouseX, double mouseY, HitTestable component) {
        if (component == null) return false;
        return component.isMouseOverExtended(mouseX, mouseY);
    }
    
    /**
     * Checks if mouse coordinates are over a folder button.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param button The folder button to check
     * @return true if the mouse is over the button
     */
    public static boolean isMouseOverButton(double mouseX, double mouseY, FolderButton button) {
        if (button == null) return false;
        return isMouseOverRect(mouseX, mouseY, button.getX(), button.getY(), 
                               button.getWidth(), button.getHeight());
    }
    
    /**
     * Checks if mouse coordinates are over a Minecraft Rect2i.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param rect The rectangle to check
     * @return true if the mouse is over the rectangle
     */
    public static boolean isMouseOverRect(double mouseX, double mouseY, Rect2i rect) {
        if (rect == null) return false;
        return isMouseOverRect(mouseX, mouseY, rect.getX(), rect.getY(), 
                              rect.getWidth(), rect.getHeight());
    }
    
    /**
     * Checks if mouse coordinates are over a Rectangle2i from our integration package.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param rect The rectangle to check
     * @return true if the mouse is over the rectangle
     */
    public static boolean isMouseOverRect(double mouseX, double mouseY, Rectangle2i rect) {
        if (rect == null || rect.isEmpty()) return false;
        return isMouseOverRect(mouseX, mouseY, rect.getX(), rect.getY(), 
                              rect.getWidth(), rect.getHeight());
    }
    
    /**
     * Checks if mouse coordinates are over a rectangular area with extended margins.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param x X coordinate of the rectangle
     * @param y Y coordinate of the rectangle
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     * @param horizontalMargin Amount to extend the hit area horizontally
     * @param verticalMargin Amount to extend the hit area vertically
     * @return true if the mouse is over the extended rectangle area
     */
    public static boolean isMouseOverExtendedRect(double mouseX, double mouseY, 
                                                int x, int y, int width, int height,
                                                int horizontalMargin, int verticalMargin) {
        return mouseX >= (x - horizontalMargin) && 
               mouseX <= (x + width + horizontalMargin) &&
               mouseY >= (y - verticalMargin) && 
               mouseY <= (y + height + verticalMargin);
    }
    
    /**
     * Checks if mouse coordinates are over a rectangle with the standard drag-drop margins.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param x X coordinate of the rectangle
     * @param y Y coordinate of the rectangle
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     * @return true if the mouse is over the extended rectangle area
     */
    public static boolean isMouseOverDragDropArea(double mouseX, double mouseY, 
                                                int x, int y, int width, int height) {
        return isMouseOverExtendedRect(mouseX, mouseY, x, y, width, height,
                                    LayoutConstants.DRAG_DROP_HORIZONTAL_MARGIN,
                                    LayoutConstants.DRAG_DROP_VERTICAL_MARGIN);
    }
    
    /**
     * Checks if mouse coordinates are over a Minecraft Rect2i with the standard drag-drop margins.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param rect The rectangle to check
     * @return true if the mouse is over the extended rectangle area
     */
    public static boolean isMouseOverDragDropArea(double mouseX, double mouseY, Rect2i rect) {
        if (rect == null) return false;
        return isMouseOverDragDropArea(mouseX, mouseY, rect.getX(), rect.getY(), 
                                     rect.getWidth(), rect.getHeight());
    }
    
    /**
     * Checks if mouse coordinates are over a Rectangle2i with the standard drag-drop margins.
     * 
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param rect The rectangle to check
     * @return true if the mouse is over the extended rectangle area
     */
    public static boolean isMouseOverDragDropArea(double mouseX, double mouseY, Rectangle2i rect) {
        if (rect == null || rect.isEmpty()) return false;
        return isMouseOverDragDropArea(mouseX, mouseY, rect.getX(), rect.getY(), 
                                     rect.getWidth(), rect.getHeight());
    }
    
    /**
     * Checks if mouse coordinates are over a content view, considering both basic bounds and
     * extended areas for drag-drop operations.
     *
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param x X coordinate of the content view
     * @param y Y coordinate of the content view
     * @param width Width of the content view
     * @param height Height of the content view
     * @param backgroundArea Optional background area (can be null)
     * @return true if the mouse is over the content view or its extended hit area
     */
    public static boolean isMouseOverContentView(double mouseX, double mouseY,
                                               int x, int y, int width, int height,
                                               Rectangle2i backgroundArea) {
        // Basic check if mouse is over the current display bounds
        if (isMouseOverRect(mouseX, mouseY, x, y, width, height)) {
            return true;
        }
        
        // For drag operations, check against the background area if available
        if (backgroundArea != null && !backgroundArea.isEmpty() &&
            isMouseOverDragDropArea(mouseX, mouseY, backgroundArea)) {
            return true;
        }
        
        // For drag and drop, be even more lenient with the main display bounds
        return isMouseOverDragDropArea(mouseX, mouseY, x, y, width, height);
    }
}