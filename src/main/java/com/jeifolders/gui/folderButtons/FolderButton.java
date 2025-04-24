package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataRepresentation;

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

    private final FolderDataRepresentation folder;
    private boolean isHovered = false;
    private boolean isActive = false;
    private boolean showSuccessAnimation = false;
    private int successAnimationTicksRemaining = 0;
    private final Consumer<FolderDataRepresentation> clickHandler;
    
    // Pre-computed values for rendering optimization
    private final String displayName;
    private final int textWidth;
    private int textX;

    public FolderButton(int x, int y, FolderDataRepresentation folder, Consumer<FolderDataRepresentation> clickHandler) {
        super(x, y, ICON_WIDTH, ICON_HEIGHT, Component.literal(folder.getName()));
        this.folder = folder;
        this.clickHandler = clickHandler;
        
        if (folder.getName().length() > TEXT_MAX_LENGTH) {
            this.displayName = folder.getName().substring(0, TEXT_MAX_LENGTH);
        } else {
            this.displayName = folder.getName();
        }
        
        this.textWidth = Minecraft.getInstance().font.width(displayName);
        this.textX = getX() + (width - textWidth) / 2;
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update hover state
        isHovered = mouseX >= getX() && mouseY >= getY() && 
                   mouseX < getX() + width && mouseY < getY() + height;
        
        // Use the GuiTextures helper to render the folder icon from the sprite sheet
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
        
        // Display tooltip when hovering
        if (isHovered) {
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                Component.literal(folder.getName()),
                mouseX, mouseY
            );
        }

        // Draw success animation if active using tick-based animation, constrain to folder area
        if (showSuccessAnimation && successAnimationTicksRemaining > 0) {
            // Calculate the bounds of the safe highlight zone
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int guiWidth = 176; // Standard GUI width
            int guiLeft = (screenWidth - guiWidth) / 2;
            
            // Constrain highlight to not go past the left GUI edge
            int maxHighlightX = Math.max(0, Math.min(guiLeft - 5, getX() + getWidth() + 2));
            
            // Apply highlight with constrained boundaries
            float progress = successAnimationTicksRemaining / (float)SUCCESS_ANIMATION_DURATION;
            int alpha = (int)(progress * 255);
            int color = (alpha << 24) | 0x00FF00; // Green color with fading alpha
            
            // Constrained fill - only highlight up to the safe limit
            graphics.fill(
                Math.max(0, getX() - 2), 
                getY() - 2, 
                Math.min(maxHighlightX, getX() + getWidth() + 2), 
                getY() + getHeight() + 2, 
                color
            );
        }
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
    
}
