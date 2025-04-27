package com.jeifolders.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Centralized utility for rendering tooltips consistently across the UI.
 * This class consolidates tooltip rendering logic that was previously
 * duplicated across multiple classes.
 */
public final class TooltipRenderer {
    
    // Prevent instantiation
    private TooltipRenderer() {}
    
    /**
     * Renders a tooltip at the specified mouse position.
     * Automatically handles translation keys and literal text.
     * 
     * @param graphics The GUI graphics context
     * @param text The tooltip text or translation key
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     */
    public static void renderTooltip(GuiGraphics graphics, String text, int mouseX, int mouseY) {
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
     * Renders a tooltip for truncated text when needed.
     * Shows the full text as a tooltip when hovering over truncated text.
     * 
     * @param graphics The GUI graphics context
     * @param fullText The complete text
     * @param displayText The truncated/displayed text
     * @param x X coordinate of the text
     * @param y Y coordinate of the text
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     */
    public static void renderTruncatedTextTooltip(
        GuiGraphics graphics, 
        String fullText, 
        String displayText,
        int x, int y, 
        int mouseX, int mouseY
    ) {
        // Only show tooltip if the text is actually truncated
        if (!displayText.equals(fullText) && 
            MouseHitUtil.isMouseOverRect(
                mouseX, mouseY, 
                x, y - 4, 
                Minecraft.getInstance().font.width(displayText), 
                14
            )
        ) {
            renderTooltip(graphics, fullText, mouseX, mouseY);
        }
    }
    
    /**
     * Renders a tooltip with a component directly.
     * 
     * @param graphics The GUI graphics context
     * @param component The component to render
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     */
    public static void renderTooltip(GuiGraphics graphics, Component component, int mouseX, int mouseY) {
        graphics.renderTooltip(
            Minecraft.getInstance().font,
            component,
            mouseX, mouseY
        );
    }
}