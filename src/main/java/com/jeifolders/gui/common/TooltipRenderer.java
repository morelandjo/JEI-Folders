package com.jeifolders.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

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
    
    /**
     * Renders a list of tooltip components.
     * 
     * @param graphics The GUI graphics context
     * @param tooltips List of components to display in the tooltip
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     */
    public static void renderTooltip(GuiGraphics graphics, List<Component> tooltips, int mouseX, int mouseY) {
        if (tooltips != null && !tooltips.isEmpty()) {
            // Handle list of components properly
            // In newer versions of Minecraft/NeoForge, we need to use a different approach
            // for rendering lists of tooltip components
            Component firstComponent = tooltips.get(0);
            if (tooltips.size() == 1) {
                graphics.renderTooltip(
                    Minecraft.getInstance().font,
                    firstComponent,
                    mouseX, mouseY
                );
            } else {
                // Create tooltip data with the list of components
                graphics.renderComponentTooltip(
                    Minecraft.getInstance().font,
                    tooltips,
                    mouseX, mouseY
                );
            }
        }
    }
    
    /**
     * Delegate method for JEI-specific ingredient tooltip rendering.
     * This method should be used when JEI needs to render ingredient tooltips.
     * 
     * @param minecraft The Minecraft instance
     * @param graphics The GUI graphics context
     * @param mouseX X coordinate of the mouse
     * @param mouseY Y coordinate of the mouse
     * @param renderCallback A runnable containing the JEI-specific tooltip rendering code
     */
    public static void renderIngredientTooltip(
        Minecraft minecraft, 
        GuiGraphics graphics, 
        int mouseX, int mouseY,
        Runnable renderCallback
    ) {
        if (renderCallback != null) {
            renderCallback.run();
        }
    }
}