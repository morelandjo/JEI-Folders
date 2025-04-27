package com.jeifolders.gui.view.render;

import com.jeifolders.data.Folder;
import com.jeifolders.gui.common.MouseHitUtil;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.layout.FolderLayoutService;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.buttons.FolderButtonTextures;
import com.jeifolders.gui.view.contents.FolderContentsView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Centralized manager for all UI rendering in the JEI Folders mod.
 * This class separates rendering logic from data management and UI components.
 */
public class UIRenderManager {
    private final FolderStateManager stateManager;
    private final FolderLayoutService layoutService;
    private final ContentViewRenderer contentsRenderer;
    
    // UI State tracked by the renderer
    private int currentDeleteButtonX = -1;
    private boolean deleteHovered = false;
    
    /**
     * Creates a new UI render manager
     * 
     * @param stateManager The folder state manager
     * @param layoutService The layout service
     */
    public UIRenderManager(FolderStateManager stateManager, FolderLayoutService layoutService) {
        this.stateManager = stateManager;
        this.layoutService = layoutService;
        this.contentsRenderer = new ContentViewRenderer();
    }
    
    /**
     * Main entry point for rendering the entire UI
     * 
     * @param graphics The GUI graphics context
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param partialTick Partial tick for smooth animations
     * @return The calculated exclusion zone
     */
    public Rect2i renderUI(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset state
        deleteHovered = false;
        
        // Render folder buttons if visible
        if (stateManager.areFoldersVisible()) {
            renderFolderButtons(graphics, mouseX, mouseY, partialTick);
        }
        
        // Render active folder details if there is an active folder
        if (stateManager.hasActiveFolder()) {
            renderActiveFolderDetails(graphics, mouseX, mouseY);
        } else {
            currentDeleteButtonX = -1;
        }
        
        // Render bookmark display if available using the content renderer
        FolderContentsView bookmarkDisplay = stateManager.getBookmarkDisplay();
        if (stateManager.hasActiveFolder() && bookmarkDisplay != null) {
            contentsRenderer.renderContentsView(bookmarkDisplay, graphics, mouseX, mouseY, partialTick);
        }
        
        // Update exclusion zone and return it
        return layoutService.updateExclusionZoneAndUI();
    }
    
    /**
     * Renders all folder buttons
     */
    private void renderFolderButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<FolderButton> buttons = stateManager.getFolderButtons();
        if (buttons == null || buttons.isEmpty()) {
            return;
        }
        
        for (FolderButton button : buttons) {
            renderFolderButton(button, graphics, mouseX, mouseY, partialTick);
        }
    }
    
    /**
     * Renders a single folder button
     */
    private void renderFolderButton(FolderButton button, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        boolean isActive = button.isActive();
        
        // Check hover state and update the button's hover state
        boolean isHovered = MouseHitUtil.isMouseOverRect(mouseX, mouseY, x, y, width, height);
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
            
            renderTooltip(graphics, tooltipText, mouseX, mouseY);
        }
    }
    
    /**
     * Renders a shortened folder name under the folder button
     */
    private void renderFolderButtonName(FolderButton button, GuiGraphics graphics) {
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
        int textY = y + height + 2; // Position right below the folder icon
        
        // Draw the name with a shadow to make it more readable
        graphics.drawString(
            Minecraft.getInstance().font,
            shortName,
            textX,
            textY,
            0xFFFFFF, // White color
            true // Draw with shadow
        );
    }
    
    /**
     * Renders active folder details including name and delete button
     */
    public void renderActiveFolderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        FolderButton activeButton = stateManager.getActiveFolder();
        if (activeButton == null || activeButton.getFolder() == null) {
            return;
        }
        
        Folder activeFolder = activeButton.getFolder();
        String fullName = activeFolder.getName();
        String displayName = fullName;
        
        // Limit the display name to 12 characters, adding "..." if it's longer
        if (fullName.length() > 12) {
            displayName = fullName.substring(0, 12) + "...";
        }
        
        // Use the correct Y position from layoutService for the folder name
        int nameY = layoutService.getFolderNameY();
        
        // Draw folder name with white color
        graphics.drawString(
            Minecraft.getInstance().font,
            displayName,
            10,
            nameY,
            0xFFFFFF, // White color
            true
        );
        
        // Show tooltip with the full name when hovering over a truncated name
        if (!displayName.equals(fullName) && 
            MouseHitUtil.isMouseOverRect(mouseX, mouseY, 10, nameY - 4, 
                                       Minecraft.getInstance().font.width(displayName), 14)) {
            renderTooltip(graphics, fullName, mouseX, mouseY);
        }
        
        // Calculate and position the delete button using the layout service
        int[] deleteButtonPos = layoutService.calculateDeleteButtonPosition();
        int deleteX = deleteButtonPos[0];
        int deleteY = deleteButtonPos[1];
        
        // Render the delete button
        FolderButtonTextures.renderDeleteFolderIcon(graphics, deleteX, deleteY);
        
        // Check if mouse is over delete button
        deleteHovered = MouseHitUtil.isMouseOverRect(mouseX, mouseY, deleteX, deleteY, 16, 16);
        
        // Show tooltip when hovering over delete button
        if (deleteHovered) {
            renderTooltip(graphics, "tooltip.jeifolders.delete_folder", mouseX, mouseY);
        }
        
        currentDeleteButtonX = deleteX;
    }
    
    /**
     * Helper method to render tooltips with consistent formatting
     */
    private void renderTooltip(GuiGraphics graphics, String text, int mouseX, int mouseY) {
        // Check if text is a translation key
        boolean isTranslationKey = text.contains(".");
        Component component = isTranslationKey ? 
                            Component.translatable(text) : 
                            Component.literal(text);
        
        graphics.renderTooltip(
            Minecraft.getInstance().font,
            component,
            mouseX, mouseY
        );
    }
    
    /**
     * Gets whether the delete button is currently hovered
     */
    public boolean isDeleteButtonHovered() {
        return deleteHovered;
    }
    
    /**
     * Gets the current X position of the delete button
     */
    public int getCurrentDeleteButtonX() {
        return currentDeleteButtonX;
    }
    
    /**
     * Gets the current exclusion zone from the layout service
     */
    public Rect2i getExclusionZone() {
        return layoutService.getExclusionZone();
    }
}