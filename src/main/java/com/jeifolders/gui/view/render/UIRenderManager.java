package com.jeifolders.gui.view.render;

import com.jeifolders.data.Folder;
import com.jeifolders.gui.common.TooltipRenderer;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.layout.FolderLayoutService;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.contents.FolderContentsView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

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
            FolderButtonRenderer.renderFolderButton(button, graphics, mouseX, mouseY, partialTick);
        }
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
        TooltipRenderer.renderTruncatedTextTooltip(
            graphics, fullName, displayName, 10, nameY, mouseX, mouseY
        );
        
        // Calculate and position the delete button using the layout service
        int[] deleteButtonPos = layoutService.calculateDeleteButtonPosition();
        int deleteX = deleteButtonPos[0];
        int deleteY = deleteButtonPos[1];
        
        // Render the delete button and check if it's hovered
        deleteHovered = FolderButtonRenderer.isMouseOverDeleteButton(mouseX, mouseY, deleteX, deleteY);
        FolderButtonRenderer.renderDeleteButton(graphics, deleteX, deleteY, mouseX, mouseY);
        
        currentDeleteButtonX = deleteX;
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