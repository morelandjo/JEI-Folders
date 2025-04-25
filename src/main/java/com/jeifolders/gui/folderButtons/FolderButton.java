package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.LayoutConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class FolderButton extends AbstractWidget {
    private static final int ICON_WIDTH = FolderButtonTextures.ICON_WIDTH;
    private static final int ICON_HEIGHT = FolderButtonTextures.ICON_HEIGHT;
    private static final int TEXT_MAX_LENGTH = 3;
    private static final int SUCCESS_ANIMATION_DURATION = 10;
    
    /**
     * Enum to define the different types of folder buttons
     */
    public enum ButtonType {
        NORMAL, // Standard folder button
        ADD     // Add folder button
    }

    private final FolderDataRepresentation folder;
    private boolean isHovered = false;
    private boolean isActive = false;
    private boolean showSuccessAnimation = false;
    private int successAnimationTicksRemaining = 0;
    private Consumer<FolderDataRepresentation> clickHandler;
    private ButtonType buttonType = ButtonType.NORMAL;
    
    private final String displayName;
    private final String fullName;
    private final int textWidth;
    private int textX;
    private boolean needsTooltip;
    
    // Cache tooltip
    private Component tooltipComponent;
    
    // Cache for position updates
    private int lastX = -1;
    private int lastY = -1;
    private int lastWidth = -1;
    
    /**
     * Constructor for "Add Folder" button
     */
    public FolderButton(int x, int y, ButtonType buttonType) {
        super(x, y, ICON_WIDTH, ICON_HEIGHT, Component.literal("Add"));
        this.buttonType = buttonType;
        this.folder = null; 
        this.clickHandler = null;
        this.fullName = "Add Folder";
        this.displayName = ""; // Empty string instead of "+" to not show text
        this.needsTooltip = true;
        this.tooltipComponent = Component.literal(fullName);
        this.textWidth = 0; // Set to 0 since we don't want text
        updateTextPosition();
    }

    /**
     * Constructor for regular folder button (clickHandler set separately)
     */
    public FolderButton(int x, int y, FolderDataRepresentation folder) {
        this(x, y, folder, null);
    }

    public FolderButton(int x, int y, FolderDataRepresentation folder, Consumer<FolderDataRepresentation> clickHandler) {
        super(x, y, ICON_WIDTH, ICON_HEIGHT, Component.literal(folder.getName()));
        this.folder = folder;
        this.clickHandler = clickHandler;
        this.fullName = folder.getName();
        
        // Pre-compute display name
        if (fullName.length() > TEXT_MAX_LENGTH) {
            this.displayName = fullName.substring(0, TEXT_MAX_LENGTH);
            this.needsTooltip = true;
        } else {
            this.displayName = fullName;
            this.needsTooltip = false;
        }
        
        // Pre-compute tooltip component
        this.tooltipComponent = Component.literal(fullName);
        
        // Pre-compute text measurements
        this.textWidth = Minecraft.getInstance().font.width(displayName);
        updateTextPosition();
    }
    
    /**
     * Sets the click handler for this button
     */
    public void setClickHandler(Consumer<FolderDataRepresentation> handler) {
        this.clickHandler = handler;
    }
    
    /**
     * Returns the button type
     */
    public ButtonType getButtonType() {
        return buttonType;
    }
    
    /**
     * Updates the cached text position when button position changes
     */
    private void updateTextPosition() {
        this.textX = getX() + (width - textWidth) / 2;
        
        // Update cached positions
        this.lastX = getX();
        this.lastY = getY();
        this.lastWidth = width;
    }
    
    @Override
    public void setX(int x) {
        super.setX(x);
        if (lastX != x) {
            updateTextPosition();
        }
    }
    
    @Override
    public void setY(int y) {
        super.setY(y);
        if (lastY != y) {
            lastY = y;
        }
    }
    
    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        if (lastWidth != width) {
            updateTextPosition();
        }
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Check if position has changed since last render (could happen during layout changes)
        if (getX() != lastX || getY() != lastY || width != lastWidth) {
            updateTextPosition();
        }
        
        // Update hover state
        isHovered = mouseX >= getX() && mouseY >= getY() && 
                   mouseX < getX() + width && mouseY < getY() + height;
        
        // Different rendering based on button type
        if (buttonType == ButtonType.ADD) {
            // Use the special Add button icon for the Add button
            FolderButtonTextures.renderAddFolderIcon(graphics, getX(), getY(), isHovered);
        } else {
            // Regular folder icon for normal folder buttons
            FolderButtonTextures.renderFolderRowIcon(graphics, getX(), getY(), isActive, isHovered);
            
            // Draw folder name below the icon
            graphics.drawString(
                Minecraft.getInstance().font, 
                displayName, 
                textX, 
                getY() + ICON_HEIGHT + 2, 
                0xFFFFFF,
                true
            );
        }
        
        // Display tooltip when hovering
        if (isHovered && (needsTooltip || showSuccessAnimation)) {
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                tooltipComponent,
                mouseX, mouseY
            );
        }

        // Draw success animation if active using tick-based animation, constrain to folder area
        if (showSuccessAnimation && successAnimationTicksRemaining > 0) {
            renderSuccessAnimation(graphics);
        }
    }
    
    /**
     * Renders the success animation with proper constraints
     */
    private void renderSuccessAnimation(GuiGraphics graphics) {
        // Calculate the bounds of the safe highlight zone using LayoutConstants
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int guiLeft = LayoutConstants.calculateGuiLeft(screenWidth);
        
        // Constrain highlight to not go past the left GUI edge
        int maxHighlightX = Math.max(0, Math.min(guiLeft - 5, getX() + getWidth() + 2));
        
        // Apply highlight with constrained boundaries
        float progress = successAnimationTicksRemaining / (float)SUCCESS_ANIMATION_DURATION;
        int alpha = (int)(progress * 255);
        int color = (alpha << 24) | 0x00FF00; // Green color with fading alpha
        
        graphics.fill(
            Math.max(0, getX() - 2), 
            getY() - 2, 
            Math.min(maxHighlightX, getX() + getWidth() + 2), 
            getY() + getHeight() + 2, 
            color
        );
    }
    
    /**
     * Update animation state - should be called each game tick
     */
    public void tick() {
        if (showSuccessAnimation && successAnimationTicksRemaining > 0) {
            successAnimationTicksRemaining--;
            if (successAnimationTicksRemaining <= 0) {
                showSuccessAnimation = false;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered && button == 0) {
            this.onClick();
            return true;
        }
        return false;
    }
    
    private void onClick() {
        if (clickHandler != null) {
            clickHandler.accept(folder);
        }
    }

    /**
     * Plays a success animation on this folder button.
     */
    public void playSuccessAnimation() {
        showSuccessAnimation = true;
        successAnimationTicksRemaining = SUCCESS_ANIMATION_DURATION;
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narrationOutput) {
        this.defaultButtonNarrationText(narrationOutput);
    }
    
    public FolderDataRepresentation getFolder() {
        return folder;
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Update the button's position without triggering a full recalculation
     */
    public void updatePosition(int x, int y) {
        boolean changed = x != getX() || y != getY();
        setPosition(x, y);
        if (changed) {
            updateTextPosition();
        }
    }
}
