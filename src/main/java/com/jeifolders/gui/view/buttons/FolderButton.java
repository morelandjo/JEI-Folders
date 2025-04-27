package com.jeifolders.gui.view.buttons;

import com.jeifolders.data.Folder;
import com.jeifolders.gui.common.MouseHitUtil;

import java.util.function.Consumer;

/**
 * A button that represents a folder in the UI.
 * This class is now a pure data object without rendering logic.
 */
public class FolderButton {
    // Position and dimensions
    private int x;
    private int y;
    private final int width;
    private final int height;
    
    // State
    private final ButtonType buttonType;
    private Folder folder;
    private boolean isActive = false;
    
    // Animation state
    private boolean isHovered = false;
    private float hoverProgress = 0.0f;
    private static final float HOVER_ANIMATION_SPEED = 0.1f;
    
    // Click handler
    private Consumer<Folder> clickHandler;
    
    /**
     * Button types, either a normal folder or a special button like "Add Folder"
     */
    public enum ButtonType {
        NORMAL,
        ADD,
        DELETE
    }
    
    /**
     * Creates a new normal folder button.
     * 
     * @param x X position
     * @param y Y position
     * @param folder The folder data this button represents
     */
    public FolderButton(int x, int y, Folder folder) {
        this.x = x;
        this.y = y;
        this.width = FolderButtonTextures.ICON_WIDTH;
        this.height = FolderButtonTextures.ICON_HEIGHT;
        this.buttonType = ButtonType.NORMAL;
        this.folder = folder;
    }
    
    /**
     * Creates a normal folder button with a click handler.
     * 
     * @param x X position
     * @param y Y position
     * @param folder The folder data this button represents
     * @param clickHandler The handler for click events
     */
    public FolderButton(int x, int y, Folder folder, 
                      Consumer<Folder> clickHandler) {
        this(x, y, folder);
        this.clickHandler = clickHandler;
    }
    
    /**
     * Creates a special button like "Add Folder".
     * 
     * @param x X position
     * @param y Y position
     * @param buttonType The type of button
     */
    public FolderButton(int x, int y, ButtonType buttonType) {
        this.x = x;
        this.y = y;
        this.width = FolderButtonTextures.ICON_WIDTH;
        this.height = FolderButtonTextures.ICON_HEIGHT;
        this.buttonType = buttonType;
        this.folder = null; // Special buttons don't have a folder
    }
    
    /**
     * Update animation state.
     */
    public void tick() {
        // Update hover animation
        if (isHovered && hoverProgress < 1.0f) {
            hoverProgress = Math.min(1.0f, hoverProgress + HOVER_ANIMATION_SPEED);
        } else if (!isHovered && hoverProgress > 0.0f) {
            hoverProgress = Math.max(0.0f, hoverProgress - HOVER_ANIMATION_SPEED);
        }
    }
    
    /**
     * Handle a mouse click.
     * 
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param button Mouse button (0 = left, 1 = right)
     * @return true if the click was processed
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MouseHitUtil.isMouseOverRect(mouseX, mouseY, x, y, width, height)) {
            // Only respond to left clicks (button == 0)
            if (button == 0 && clickHandler != null) {
                // Call the click handler with the folder
                clickHandler.accept(folder);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Sets whether this folder button is active.
     * 
     * @param active Whether the button should be active
     */
    public void setActive(boolean active) {
        if (this.isActive != active) {
            this.isActive = active;
        }
    }
    
    /**
     * Sets the hover state for this button
     * 
     * @param hovered Whether the button is being hovered over
     */
    public void setHovered(boolean hovered) {
        this.isHovered = hovered;
    }
    
    /**
     * Sets the click handler for this button.
     * 
     * @param handler The consumer that will handle clicks
     */
    public void setClickHandler(Consumer<Folder> handler) {
        this.clickHandler = handler;
    }
    
    /**
     * Sets a new position for this button.
     * 
     * @param x New X position
     * @param y New Y position
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Folder getFolder() { return folder; }
    public boolean isActive() { return isActive; }
    public ButtonType getButtonType() { return buttonType; }
    public boolean isHovered() { return isHovered; }
    public float getHoverProgress() { return hoverProgress; }
}
