package com.jeifolders.ui.dialogs;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class FolderNameInputScreen extends Screen {
    private static final int TEXT_FIELD_WIDTH = 200;
    private static final int TEXT_FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 98;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PADDING = 10;

    private final Screen parentScreen;
    private final Consumer<String> onConfirm;
    private EditBox nameField;
    private Component placeholderText;
    private Component dialogTitle;
    
    private int centerX;
    private int centerY;

    public FolderNameInputScreen(Screen parent, Consumer<String> onConfirm) {
        // Use empty component for the base Screen title to prevent automatic rendering
        super(Component.empty());
        this.dialogTitle = Component.translatable("gui.jeifolders.create_folder");
        this.parentScreen = parent;
        this.onConfirm = onConfirm;
        this.placeholderText = Component.translatable("gui.jeifolders.folder_name_prompt");
    }

    @Override
    protected void init() {
        centerX = this.width / 2;
        centerY = this.height / 2;
        
        // Create the edit box for folder name input
        nameField = new EditBox(
            this.font,
            centerX - TEXT_FIELD_WIDTH / 2,
            centerY - TEXT_FIELD_HEIGHT - PADDING,
            TEXT_FIELD_WIDTH,
            TEXT_FIELD_HEIGHT,
            Component.empty()
        );
        nameField.setMaxLength(32);
        nameField.setValue("");
        nameField.setHint(placeholderText);
        
        // Add confirm button
        addRenderableWidget(Button.builder(
            Component.translatable("gui.jeifolders.confirm"),
            button -> confirmName()
        ).bounds(
            centerX - BUTTON_WIDTH - PADDING/2,
            centerY + PADDING,
            BUTTON_WIDTH,
            BUTTON_HEIGHT
        ).build());
        
        // Add cancel button
        addRenderableWidget(Button.builder(
            Component.translatable("gui.jeifolders.cancel"),
            button -> cancelInput()
        ).bounds(
            centerX + PADDING/2,
            centerY + PADDING,
            BUTTON_WIDTH,
            BUTTON_HEIGHT
        ).build());
        
        // Add the name field to the screen
        addWidget(nameField);
        
        // Set focus to the text field
        setInitialFocus(nameField);
        
        
    }
    
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // First render the background
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        
        // Then render all widgets (buttons, etc)
        super.render(graphics, mouseX, mouseY, partialTick);
        
        // Draw the dialog title manually on top of everything
        graphics.drawCenteredString(
            this.font,
            dialogTitle,
            centerX,
            centerY - TEXT_FIELD_HEIGHT - PADDING * 3,
            0xFFFFFF
        );
        
        // Draw the edit box
        nameField.render(graphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle Enter key to confirm
        if (keyCode == 257 || keyCode == 335) { // Enter or numpad Enter
            confirmName();
            return true;
        }
        
        // Handle Escape key to cancel
        if (keyCode == 256) { // Escape
            cancelInput();
            return true;
        }
        
        // Pass other key presses to the text field
        return nameField.keyPressed(keyCode, scanCode, modifiers) || 
               super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private void confirmName() {
        String name = nameField.getValue().trim();
        if (!name.isEmpty()) {
            ModLogger.debug("Folder name confirmed: {}", name);
            onConfirm.accept(name);
        }
        
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }
    
    private void cancelInput() {
        ModLogger.debug("Folder creation cancelled");
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }
    
}