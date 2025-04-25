package com.jeifolders.gui.folderButtons;

import com.jeifolders.JEIFolders;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Manages all GUI textures
 */
public class FolderButtonTextures {
    public static final ResourceLocation SPRITE_SHEET = ResourceLocation.fromNamespaceAndPath(JEIFolders.MOD_ID, "textures/gui/folders.png");

    private static final int SHEET_WIDTH = 64;
    private static final int SHEET_HEIGHT = 64;

    public static final int ICON_WIDTH = 16;
    public static final int ICON_HEIGHT = 16;

    // Sprite positions in the sprite sheet (U, V coordinates)
    private static final int FOLDER_ADD_U = 0;
    private static final int FOLDER_ADD_V = 0;
    
    private static final int FOLDER_DELETE_U = 16; 
    private static final int FOLDER_DELETE_V = 0;
    
    private static final int FOLDER_ADD_HOVER_U = 0;
    private static final int FOLDER_ADD_HOVER_V = 16;
    
    private static final int FOLDER_ROW_U = 0;
    private static final int FOLDER_ROW_V = 32;
    
    private static final int FOLDER_ROW_HOVER_U = 16;
    private static final int FOLDER_ROW_HOVER_V = 32;
    
    private static final int FOLDER_ROW_OPEN_U = 0;
    private static final int FOLDER_ROW_OPEN_V = 48;
    
    private static final int FOLDER_ROW_OPEN_HOVER_U = 16;
    private static final int FOLDER_ROW_OPEN_HOVER_V = 48;

    /**
     * Renders the Add Folder icon at the specified position
     */
    public static void renderAddFolderIcon(GuiGraphics graphics, int x, int y, boolean isHovered) {
        if (isHovered) {
            graphics.blit(SPRITE_SHEET, x, y, FOLDER_ADD_HOVER_U, FOLDER_ADD_HOVER_V, ICON_WIDTH, ICON_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT);
        } else {
            graphics.blit(SPRITE_SHEET, x, y, FOLDER_ADD_U, FOLDER_ADD_V, ICON_WIDTH, ICON_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT);
        }
    }

    /**
     * Renders the Delete Folder icon at the specified position
     */
    public static void renderDeleteFolderIcon(GuiGraphics graphics, int x, int y) {
        graphics.blit(SPRITE_SHEET, x, y, FOLDER_DELETE_U, FOLDER_DELETE_V, ICON_WIDTH, ICON_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT);
    }

    /**
     * Renders a Folder Row icon at the specified position
     */
    public static void renderFolderRowIcon(GuiGraphics graphics, int x, int y, boolean isActive, boolean isHovered) {
        int u, v;
        
        if (isActive) {
            u = isHovered ? FOLDER_ROW_OPEN_HOVER_U : FOLDER_ROW_OPEN_U;
            v = isHovered ? FOLDER_ROW_OPEN_HOVER_V : FOLDER_ROW_OPEN_V;
        } else {
            u = isHovered ? FOLDER_ROW_HOVER_U : FOLDER_ROW_U;
            v = isHovered ? FOLDER_ROW_HOVER_V : FOLDER_ROW_V;
        }
        
        graphics.blit(SPRITE_SHEET, x, y, u, v, ICON_WIDTH, ICON_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT);
    }
}