package com.jeifolders.gui;

import com.jeifolders.util.ModLogger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

/**
 * Main entry point for the folder button system.
 * 
 */
public class FolderButton extends AbstractWidget implements FolderButtonInterface {
    // Specialized component classes
    private final FolderStateManager stateManager;
    private final BookmarkManager bookmarkManager;
    private final FolderButtonView view;

    public FolderButton() {
        super(0, 0, 0, 0, Component.translatable("gui.jeifolders.folder"));
        
        ModLogger.debug("Initializing FolderButton with specialized components");
        
        // Initialize the component classes
        this.stateManager = new FolderStateManager();
        this.bookmarkManager = new BookmarkManager(stateManager);
        this.view = new FolderButtonView(stateManager, bookmarkManager);
        
        // Update dimensions to match the view
        this.width = view.getWidth();
        this.height = view.getHeight();
        
        ModLogger.debug("FolderButton initialization complete");
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        view.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return view.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narrationOutput) {
        view.updateWidgetNarration(narrationOutput);
    }
    
    @Override
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        return view.handleIngredientDrop(mouseX, mouseY, ingredient);
    }
    
    @Override
    public List<FolderRowButton> getFolderButtons() {
        return view.getFolderButtons();
    }
    
    @Override
    public boolean isBookmarkAreaAvailable() {
        return view.isBookmarkAreaAvailable();
    }
    
    @Override
    public Rect2i getBookmarkDisplayArea() {
        return view.getBookmarkDisplayArea();
    }
    
    public void tick() {
        view.tick();
    }
    
    /**
     * Sets the JEI runtime object
     * @param runtime The JEI runtime object from JEI API
     */
    public void setJeiRuntime(Object runtime) {
        view.setJeiRuntime(runtime);
    }
    
    public void initializeJeiRuntime() {
        view.initializeJeiRuntime();
    }
    
    /**
     * Force a refresh of the bookmark display
     */
    public void reloadBookmarkDisplay() {
        bookmarkManager.reloadBookmarkDisplay();
    }
    
    /**
     * Updates the bookmark display
     */
    public void forceFullRefresh() {
        bookmarkManager.forceFullRefresh();
    }
    
    
    
    /**
     * Adds a listener for folder button clicks
     */
    public static void addClickListener(Consumer<FolderButton> listener) {
        FolderButtonView.addClickListener(view -> {
            // When view fires event, adapt it to fire as if from FolderButton
            listener.accept(null);
        });
    }
}
