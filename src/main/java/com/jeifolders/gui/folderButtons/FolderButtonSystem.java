package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.FolderNameInputScreen;
import com.jeifolders.gui.bookmarks.BookmarkManager;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
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
 * Responsible for rendering, mouse handling, and UI state management.
 */
public class FolderButtonSystem extends AbstractWidget implements FolderButtonInterface {
    // UI State
    private boolean isHovered = false;
    private int currentDeleteButtonX = -1;
    private int currentDeleteButtonY = -1;
    private boolean deleteHovered = false;
    
    // Window size tracking
    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;
    
    // Exclusion zone
    public static Rect2i lastDrawnArea = new Rect2i(0, 0, 0, 0);
    
    // Consolidated managers
    private final UnifiedFolderManager folderManager;
    private final FolderRenderingManager renderingManager;
    private final BookmarkManager bookmarkManager;
    
    public FolderButtonSystem() {
        super(10, 10, FolderButtonTextures.ICON_WIDTH, FolderButtonTextures.ICON_HEIGHT, 
              Component.translatable("gui.jeifolders.folder"));
        
        ModLogger.debug("Initializing FolderButton with consolidated components");
        
        // Initialize the component classes
        this.folderManager = UnifiedFolderManager.getInstance();
        this.renderingManager = FolderRenderingManager.getInstance();
        this.bookmarkManager = new BookmarkManager(folderManager);
        
        // Initialize window size tracking
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() != null) {
            lastWindowWidth = minecraft.getWindow().getGuiScaledWidth();
            lastWindowHeight = minecraft.getWindow().getGuiScaledHeight();
        }
        
        // Calculate initial layout and load folders
        renderingManager.calculateFoldersPerRow();
        loadFolders();
        
        // Update layout positions and bookmark display bounds
        updateLayoutPositions();
        
        ModLogger.debug("FolderButtonSystem initialized with {} folders, visibility: {}, foldersPerRow: {}",
            folderManager.getFolderButtons().size(), folderManager.areFoldersVisible(), renderingManager.getFoldersPerRow());
            
        // Set up JEI runtime initialization
        initializeJeiRuntime();
        
        // Restore the state if needed
        if (folderManager.shouldRestoreFromStaticState()) {
            bookmarkManager.restoreFromStaticState();
        }
        
        ModLogger.debug("FolderButtonSystem initialization complete");
    }
    
    /**
     * Loads all folders and initializes folder buttons
     */
    private void loadFolders() {
        List<FolderDataRepresentation> folders = folderManager.loadAllFolders();
        Integer lastActiveFolderId = folderManager.getLastActiveFolderId();
        FolderButton buttonToActivate = null;
        
        // Create folder buttons from the loaded data
        for (int i = 0; i < folders.size(); i++) {
            FolderDataRepresentation folder = folders.get(i);
            // Calculate positions using index + 1 (leaving 0 for Add button)
            int[] position = renderingManager.calculateFolderPosition(i + 1);
            int x = position[0];
            int y = position[1];

            FolderButton button = new FolderButton(x, y, folder, this::onFolderClicked);
            folderManager.getFolderButtons().add(button);

            if (lastActiveFolderId != null && folder.getId() == lastActiveFolderId) {
                buttonToActivate = button;
            }
        }

        // Set active folder if needed
        if (buttonToActivate != null) {
            folderManager.setActiveFolder(buttonToActivate);
        }
    }
    
    /**
     * Updates the vertical positions for folder names and bookmark display
     */
    private void updateLayoutPositions() {
        renderingManager.updateLayoutPositions(folderManager.getFolderButtons().size());
        
        // Update the bookmark manager with the calculated positions
        bookmarkManager.setCalculatedPositions(
            renderingManager.getFolderNameY(), 
            renderingManager.getBookmarkDisplayY()
        );
    }
    
    /**
     * Called when a folder is clicked
     */
    private void onFolderClicked(FolderDataRepresentation folder) {
        // Find which button was clicked
        FolderButton clickedButton = null;
        for (FolderButton button : folderManager.getFolderButtons()) {
            if (button.getFolder() == folder) {
                clickedButton = button;
                break;
            }
        }

        if (clickedButton == null) {
            return;
        }

        // Fire the folder clicked event
        folderManager.fireFolderClickedEvent(folder);

        // Handle toggle behavior (deactivate if already active)
        if (folderManager.getActiveFolder() == clickedButton) {
            folderManager.deactivateActiveFolder();
            return;
        }

        // Set as active folder
        folderManager.setActiveFolder(clickedButton);
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Calculate the position for the Add button as if it's the first icon in the grid
        int[] addButtonPos = renderingManager.calculateAddButtonPosition();
        int addButtonX = addButtonPos[0];
        int addButtonY = addButtonPos[1];

        // Determine if the mouse is over the Add button at its new position
        isHovered = mouseX >= addButtonX && mouseY >= addButtonY &&
                   mouseX < addButtonX + width && mouseY < addButtonY + height;

        // Render the add folder button using the sprite sheet
        FolderButtonTextures.renderAddFolderIcon(graphics, addButtonX, addButtonY, isHovered);

        if (folderManager.areFoldersVisible()) {
            for (FolderButton button : folderManager.getFolderButtons()) {
                button.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        if (folderManager.hasActiveFolder()) {
            renderActiveFolderDetails(graphics, mouseX, mouseY);
        } else {
            currentDeleteButtonX = -1;
            currentDeleteButtonY = -1;
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
    private void renderActiveFolderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        FolderButton activeFolder = folderManager.getActiveFolder();
        if (activeFolder == null) {
            return;
        }

        String fullName = activeFolder.getFolder().getName();
        String displayName = fullName;
        // Limit the display name to 12 characters, adding "..." if it's longer
        if (fullName.length() > 12) {
            displayName = fullName.substring(0, 12) + "...";
        }

        graphics.drawString(
            Minecraft.getInstance().font,
            displayName,
            10,
            renderingManager.getFolderNameY(),
            0xFFFFFF,
            true
        );

        // Show tooltip with the full name when hovering over a truncated name
        if (!displayName.equals(fullName) && mouseX >= 10 && mouseX < 10 + Minecraft.getInstance().font.width(displayName) &&
            mouseY >= renderingManager.getFolderNameY() - 4 && mouseY < renderingManager.getFolderNameY() + 10) {
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
        currentDeleteButtonY = deleteY;
    }
    
    /**
     * Updates the exclusion zone for other UI elements
     */
    private void updateExclusionZone() {
        int bookmarkDisplayHeight = 0;
        if (folderManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            bookmarkDisplayHeight = bookmarkManager.getBookmarkDisplay().getHeight();
        }
        
        lastDrawnArea = renderingManager.updateExclusionZone(
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
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentDeleteButtonX >= 0 && button == 0 && deleteHovered) {
            // Fire delete button clicked event before deleting
            if (folderManager.hasActiveFolder()) {
                folderManager.fireDeleteButtonClickedEvent(folderManager.getActiveFolder().getFolder().getId());
            }
            
            folderManager.deleteActiveFolder();
            loadFolders();
            return true;
        }

        if (folderManager.areFoldersVisible()) {
            for (FolderButton folderButton : folderManager.getFolderButtons()) {
                if (folderButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        // Handle bookmark display click
        if (bookmarkManager.handleBookmarkDisplayClick(mouseX, mouseY, button)) {
            return true;
        }

        // Calculate the position for the Add button
        int[] addButtonPos = renderingManager.calculateAddButtonPosition();
        int addButtonX = addButtonPos[0];
        int addButtonY = addButtonPos[1];

        // Check if the Add button was clicked at its new position
        if (mouseX >= addButtonX && mouseX < addButtonX + width &&
            mouseY >= addButtonY && mouseY < addButtonY + height && button == 0) {
            // Fire add button clicked event
            folderManager.fireAddButtonClickedEvent();
            
            this.showFolderNameInputScreen();
            return true;
        }

        return false;
    }
    
    /**
     * Explicitly check if window has been resized and handle UI adjustments if needed
     */
    private void checkForWindowResize() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) return;
        
        int currentWidth = minecraft.getWindow().getGuiScaledWidth();
        int currentHeight = minecraft.getWindow().getGuiScaledHeight();
        
        // Check if window dimensions changed
        if (currentWidth != lastWindowWidth || currentHeight != lastWindowHeight) {
            ModLogger.info("Window resized: {}x{} -> {}x{}, preserving folder state",
                    lastWindowWidth, lastWindowHeight, currentWidth, currentHeight);
                    
            // Update cached window dimensions
            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
            
            // Recalculate layout with preserved folder state
            renderingManager.calculateFoldersPerRow();
            loadFolders();
            updateLayoutPositions();
            
            // Force update bookmark display after window resize
            if (folderManager.hasActiveFolder()) {
                // Use our explicit refresh method which handles everything properly
                bookmarkManager.refreshBookmarkDisplay();
                ModLogger.info("Updated bookmark display after resize");
            }
        }
    }
    
    /**
     * Creates a new folder with the given name
     * @param name The name of the folder
     * @return The newly created folder
     */
    public FolderDataRepresentation createFolder(String name) {
        FolderDataRepresentation folder = folderManager.createFolder(name);
        // Update UI state after folder creation
        folderManager.setFoldersVisible(true);
        loadFolders();
        updateLayoutPositions();
        return folder;
    }
    
    /**
     * Shows the folder name input screen for creating a new folder
     */
    private void showFolderNameInputScreen() {
        ModLogger.debug("Add folder button clicked");
        Minecraft.getInstance().setScreen(new FolderNameInputScreen(
            Minecraft.getInstance().screen,
            folderName -> {
                createFolder(folderName);
            }
        ));
    }
    
    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narrationOutput) {
        this.defaultButtonNarrationText(narrationOutput);
    }
    
    @Override
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        // First check if the ingredient is dropped on a folder button
        FolderButton targetFolder = folderManager.getFolderButtonAt(mouseX, mouseY);
        
        if (targetFolder != null) {
            // If dropped on a folder, activate it and add the ingredient
            folderManager.setActiveFolder(targetFolder);
            
            // Fire ingredient dropped event
            folderManager.fireIngredientDroppedEvent(ingredient, targetFolder.getFolder().getId());
            
            // Use the bookmark manager to handle the actual ingredient storing
            return bookmarkManager.handleIngredientDrop(mouseX, mouseY, ingredient, folderManager.areFoldersVisible());
        }
        
        // Handle case where ingredient is dropped on bookmark area
        if (folderManager.hasActiveFolder()) {
            return bookmarkManager.handleIngredientDrop(mouseX, mouseY, ingredient, folderManager.areFoldersVisible());
        }
        
        return false;
    }
    
    public void tick() {
        checkForWindowResize();
    }
    
    /**
     * Sets the JEI runtime object
     * @param runtime The JEI runtime object from JEI API
     */
    public void setJeiRuntime(Object runtime) {
        JEIIntegrationFactory.getJEIService().setJeiRuntime(runtime);
    }

    public void initializeJeiRuntime() {
        JEIIntegrationFactory.getJEIService().registerRuntimeCallback(runtime -> {
            // Perform any initialization that depends on the JEI runtime
            ModLogger.debug("JEI runtime initialized in FolderButtonSystem");
        });
    }
    
    /**
     * Refreshes the bookmark display with options to control behavior
     * 
     * @return true if the refresh was successful
     */
    public boolean refreshBookmarkDisplay() {
        return bookmarkManager.refreshBookmarkDisplay();
    }
    
    /**
     * Gets the bookmark manager instance
     * @return The bookmark manager
     */
    public BookmarkManager getBookmarkManager() {
        return bookmarkManager;
    }
    
    /**
     * Gets the unified folder manager instance
     * @return The unified folder manager
     */
    public UnifiedFolderManager getFolderManager() {
        return folderManager;
    }
    
    /**
     * Gets the rendering manager instance
     * @return The rendering manager
     */
    public FolderRenderingManager getRenderingManager() {
        return renderingManager;
    }
    
    // Implementation of FolderButtonInterface
    
    @Override
    public List<FolderButton> getFolderButtons() {
        return folderManager.getFolderButtons();
    }

    @Override
    public boolean isBookmarkAreaAvailable() {
        return folderManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null;
    }

    @Override
    public Rect2i getBookmarkDisplayArea() {
        if (bookmarkManager.getBookmarkDisplay() != null) {
            return new Rect2i(
                bookmarkManager.getBookmarkDisplay().getX(),
                bookmarkManager.getBookmarkDisplay().getY(),
                bookmarkManager.getBookmarkDisplay().getWidth(),
                bookmarkManager.getBookmarkDisplay().getHeight()
            );
        }
        return new Rect2i(0, 0, 0, 0);
    }
}
