package com.jeifolders.gui.view.render;

import com.jeifolders.gui.common.MouseHitUtil;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.layout.FolderLayoutService;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.buttons.FolderButtonTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/**
 * Responsible for rendering folder UI components.
 * Handles rendering of folder buttons, active folder details, and exclusion zones.
 */
public class FolderRenderer {
    private final FolderStateManager folderManager;
    private final FolderLayoutService layoutService;
    
    // UI State
    private int currentDeleteButtonX = -1;
    private boolean deleteHovered = false;
    
    public FolderRenderer(FolderStateManager folderManager, 
                         FolderLayoutService layoutService) {
        this.folderManager = folderManager;
        this.layoutService = layoutService;
    }
    
    /**
     * Main rendering method for the folder UI
     */
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (folderManager.areFoldersVisible()) {
            for (FolderButton button : folderManager.getFolderButtons()) {
                button.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        if (folderManager.hasActiveFolder()) {
            renderActiveFolderDetails(graphics, mouseX, mouseY);
        } else {
            currentDeleteButtonX = -1;
            deleteHovered = false;
        }

        if (folderManager.hasActiveFolder() && folderManager.getBookmarkDisplay() != null) {
            folderManager.getBookmarkDisplay().render(graphics, mouseX, mouseY, partialTick);
        }

        // Update the exclusion zone through the layout service
        layoutService.updateExclusionZoneAndUI();
    }
    
    /**
     * Renders details for the active folder, including name and delete button
     */
    public void renderActiveFolderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        FolderButton activeFolder = folderManager.getActiveFolder();
        if (activeFolder == null) {
            return;
        }

        String fullName = activeFolder.getFolder().getName();
        String displayName = fullName;
        // Limit the display name to 12 characters, adding "..." if it's longer
        if (fullName.length() > 12) {
            displayName = fullName.substring(0, 12) + "...";
        }

        // Use the correct Y position from layoutService for the folder name
        int nameY = layoutService.getFolderNameY();
        
        // Changed back to white color (0xFFFFFF) for the folder name
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
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                Component.literal(fullName),
                mouseX, mouseY
            );
        }

        // Calculate and position the delete button using the layout service
        int[] deleteButtonPos = layoutService.calculateDeleteButtonPosition();
        int deleteX = deleteButtonPos[0];
        int deleteY = deleteButtonPos[1];
        
        // Render the delete button using the sprite sheet
        FolderButtonTextures.renderDeleteFolderIcon(graphics, deleteX, deleteY);

        // Check if mouse is over delete button
        deleteHovered = MouseHitUtil.isMouseOverRect(mouseX, mouseY, deleteX, deleteY, 16, 16);

        if (deleteHovered) {
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                Component.translatable("tooltip.jeifolders.delete_folder"),
                mouseX, mouseY
            );
        }

        currentDeleteButtonX = deleteX;
    }
    
    /**
     * Gets the current exclusion zone from the layout service
     */
    public Rect2i getExclusionZone() {
        return layoutService.getExclusionZone();
    }
    
    /**
     * Checks if the delete button is currently hovered
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
}