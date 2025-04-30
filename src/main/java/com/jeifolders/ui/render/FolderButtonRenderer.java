package com.jeifolders.ui.render;

import com.jeifolders.data.Folder;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.components.buttons.FolderButtonTextures;
import com.jeifolders.ui.util.MouseHitUtil;
import com.jeifolders.ui.util.TooltipRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Specialized renderer for folder buttons.
 */
public class FolderButtonRenderer {
    
    private FolderButtonRenderer() {}
    
    /**
     * Renders a folder button with appropriate styling and tooltip.
     * 
     * @param button The button to render
     * @param graphics The GUI graphics context
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param partialTick Partial tick for animations
     */
    public static void renderFolderButton(FolderButton button, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = button.getX();
        int y = button.getY();
        boolean isActive = button.isActive();
        
        // Check hover state using the HitTestable interface implementation
        boolean isHovered = MouseHitUtil.isMouseOver(mouseX, mouseY, button);
        button.setHovered(isHovered);
        
        // Get hover animation progress
        float hoverProgress = button.getHoverProgress();
        boolean isAnimatedHover = hoverProgress > 0.5f;
        
        // Render based on button type
        FolderButton.ButtonType buttonType = button.getButtonType();
        switch (buttonType) {
            case NORMAL:
                // Render folder icon
                FolderButtonTextures.renderFolderRowIcon(graphics, x, y, isActive, isAnimatedHover);
                
                // Render shortened folder name if available
                renderFolderButtonName(button, graphics);
                break;
            case ADD:
                // Render add button
                FolderButtonTextures.renderAddFolderIcon(graphics, x, y, isAnimatedHover);
                break;
            case DELETE:
                // Render delete button
                FolderButtonTextures.renderDeleteFolderIcon(graphics, x, y);
                break;
        }
        
        // Show tooltip when hovering
        if (isHovered) {
            String tooltipText;
            if (buttonType == FolderButton.ButtonType.ADD) {
                tooltipText = "tooltip.jeifolders.add_folder";
            } else if (button.getFolder() != null) {
                tooltipText = button.getFolder().getName();
            } else {
                tooltipText = buttonType.name().toLowerCase();
            }
            
            TooltipRenderer.renderTooltip(graphics, tooltipText, mouseX, mouseY);
        }
    }
    
    /**
     * Renders a shortened folder name under the folder button
     */
    public static void renderFolderButtonName(FolderButton button, GuiGraphics graphics) {
        Folder folder = button.getFolder();
        if (folder == null) return;
        
        // Get the folder name
        String folderName = folder.getName();
        
        // Get the first 3 characters, or the entire name if it's shorter
        String shortName = folderName.length() > 3 ? folderName.substring(0, 3) : folderName;
        
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        
        // Calculate the position to center the text under the folder icon
        int textWidth = Minecraft.getInstance().font.width(shortName);
        int textX = x + (width - textWidth) / 2;
        int textY = y + height + 2;
        
        graphics.drawString(
            Minecraft.getInstance().font,
            shortName,
            textX,
            textY,
            0xFFFFFF,
            true
        );
    }
    
    /**
     * Renders a delete button at specified coordinates
     */
    public static void renderDeleteButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Render the delete button
        FolderButtonTextures.renderDeleteFolderIcon(graphics, x, y);
        
        // Show tooltip when hovering over delete button using direct rectangle hit test
        boolean isHovered = isMouseOverDeleteButton(mouseX, mouseY, x, y);
        if (isHovered) {
            TooltipRenderer.renderTooltip(graphics, "tooltip.jeifolders.delete_folder", mouseX, mouseY);
        }
    }
    
    /**
     * Checks if mouse is over a delete button
     */
    public static boolean isMouseOverDeleteButton(int mouseX, int mouseY, int buttonX, int buttonY) {
        return MouseHitUtil.isMouseOverRect(mouseX, mouseY, buttonX, buttonY, 
                                         FolderButtonTextures.ICON_WIDTH, 
                                         FolderButtonTextures.ICON_HEIGHT);
    }
}