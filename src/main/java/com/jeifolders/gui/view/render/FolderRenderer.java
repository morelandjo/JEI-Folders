package com.jeifolders.gui.view.render;

import com.jeifolders.gui.controller.BookmarkManager;
import com.jeifolders.gui.controller.FolderStateManager;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.buttons.FolderButtonTextures;
import com.jeifolders.gui.view.layout.FolderRenderingManager;
import com.jeifolders.util.ModLogger;
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
    private final FolderRenderingManager renderingManager;
    private final BookmarkManager bookmarkManager;
    
    // UI State
    private int currentDeleteButtonX = -1;
    private boolean deleteHovered = false;
    
    public FolderRenderer(FolderStateManager folderManager, 
                         FolderRenderingManager renderingManager, 
                         BookmarkManager bookmarkManager) {
        this.folderManager = folderManager;
        this.renderingManager = renderingManager;
        this.bookmarkManager = bookmarkManager;
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

        if (folderManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            bookmarkManager.getBookmarkDisplay().render(graphics, mouseX, mouseY, partialTick);
        }

        updateExclusionZone();
    }
    
    /**
     * Renders details for the active folder, including name and delete button
     */
    public void renderActiveFolderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        FolderButton activeFolder = folderManager.getActiveFolder();
        if (activeFolder == null) {
            ModLogger.debug("[NAME-DEBUG] No active folder to render name for");
            return;
        }

        String fullName = activeFolder.getFolder().getName();
        String displayName = fullName;
        // Limit the display name to 12 characters, adding "..." if it's longer
        if (fullName.length() > 12) {
            displayName = fullName.substring(0, 12) + "...";
        }

        // Use the correct Y position from renderingManager for the folder name
        int nameY = renderingManager.getFolderNameY();
        ModLogger.debug("[NAME-DEBUG] Drawing folder name '{}' at Y={}", displayName, nameY);
        
        // Changed back to white color (0xFFFFFF) for the folder name
        graphics.drawString(
            Minecraft.getInstance().font,
            displayName,
            10,
            nameY,
            0xFFFFFF, // White color
            true
        );
        
        // Log font metrics to ensure name is within visible area
        int fontHeight = Minecraft.getInstance().font.lineHeight;
        int stringWidth = Minecraft.getInstance().font.width(displayName);
        ModLogger.debug("[NAME-DEBUG] Font metrics: width={}, height={}, screen dimensions: {}x{}", 
                      stringWidth, fontHeight,
                      Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                      Minecraft.getInstance().getWindow().getGuiScaledHeight());

        // Show tooltip with the full name when hovering over a truncated name
        if (!displayName.equals(fullName) && mouseX >= 10 && mouseX < 10 + Minecraft.getInstance().font.width(displayName) &&
            mouseY >= nameY - 4 && mouseY < nameY + 10) {
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                Component.literal(fullName),
                mouseX, mouseY
            );
        }

        // Calculate and position the delete button using the layout manager
        int[] deleteButtonPos = renderingManager.calculateDeleteButtonPosition();
        int deleteX = deleteButtonPos[0];
        int deleteY = deleteButtonPos[1];
        
        // Render the delete button using the sprite sheet
        FolderButtonTextures.renderDeleteFolderIcon(graphics, deleteX, deleteY);
        ModLogger.debug("[NAME-DEBUG] Delete button rendered at X={}, Y={}", deleteX, deleteY);

        deleteHovered = mouseX >= deleteX && mouseX < deleteX + 16 &&
                      mouseY >= deleteY && mouseY < deleteY + 16;

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
     * Updates the exclusion zone for other UI elements
     */
    public Rect2i updateExclusionZone() {
        int bookmarkDisplayHeight = 0;
        if (folderManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            bookmarkDisplayHeight = bookmarkManager.getBookmarkDisplay().getHeight();
        }
        
        Rect2i lastDrawnArea = renderingManager.updateExclusionZone(
            folderManager.getFolderButtons().size(), 
            folderManager.areFoldersVisible(), 
            folderManager.hasActiveFolder(),
            bookmarkDisplayHeight
        );
        
        // Update bookmark display bounds if active
        if (folderManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            Rect2i zone = renderingManager.getExclusionZone();
            int bookmarkDisplayWidth = zone.getWidth() + 10;
            bookmarkManager.getBookmarkDisplay().updateBounds(
                0, 
                renderingManager.getBookmarkDisplayY(), 
                bookmarkDisplayWidth,
                bookmarkManager.getBookmarkDisplay().getHeight()
            );
        }
        
        return lastDrawnArea;
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