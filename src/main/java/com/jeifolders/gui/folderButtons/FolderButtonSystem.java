package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.events.BookmarkEvents;
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
    
    // Services
    private final FolderStateManager stateManager;
    private final FolderButtonStateManager legacyStateManager; // For backward compatibility
    private final BookmarkManager bookmarkManager;
    private final BookmarkEvents eventSystem;
    private final FolderLayoutManager layoutManager;
    private final FolderEventManager eventManager;
    
    public FolderButtonSystem() {
        super(10, 10, FolderButtonTextures.ICON_WIDTH, FolderButtonTextures.ICON_HEIGHT, 
              Component.translatable("gui.jeifolders.folder"));
        
        ModLogger.debug("Initializing FolderButton with specialized components");
        
        // Initialize the component classes
        this.layoutManager = new FolderLayoutManager();
        this.eventManager = FolderEventManager.getInstance();
        this.stateManager = FolderStateManager.getInstance();
        this.legacyStateManager = new FolderButtonStateManager(); // For backward compatibility
        this.bookmarkManager = new BookmarkManager(legacyStateManager); // Will be updated later
        this.eventSystem = BookmarkEvents.getInstance();
        
        // Initialize window size tracking
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() != null) {
            lastWindowWidth = minecraft.getWindow().getGuiScaledWidth();
            lastWindowHeight = minecraft.getWindow().getGuiScaledHeight();
        }
        
        // Calculate initial layout and load folders
        layoutManager.calculateFoldersPerRow();
        loadFolders();
        
        // Update layout positions and bookmark display bounds
        updateLayoutPositions();
        
        ModLogger.debug("FolderButtonSystem initialized with {} folders, visibility: {}, foldersPerRow: {}",
            stateManager.getFolderButtons().size(), stateManager.areFoldersVisible(), layoutManager.getFoldersPerRow());
            
        // Set up JEI runtime initialization
        initializeJeiRuntime();
        
        // Restore the state if needed
        if (stateManager.shouldRestoreFromStaticState()) {
            bookmarkManager.restoreFromStaticState();
        }
        
        ModLogger.debug("FolderButtonSystem initialization complete");
    }
    
    /**
     * Loads all folders and initializes folder buttons
     */
    private void loadFolders() {
        List<FolderDataRepresentation> folders = stateManager.loadAllFolders();
        Integer lastActiveFolderId = stateManager.getLastActiveFolderId();
        FolderButton buttonToActivate = null;
        
        // Create folder buttons from the loaded data
        for (int i = 0; i < folders.size(); i++) {
            FolderDataRepresentation folder = folders.get(i);
            // Calculate positions using index + 1 (leaving 0 for Add button)
            int[] position = layoutManager.calculateFolderPosition(i + 1);
            int x = position[0];
            int y = position[1];

            FolderButton button = new FolderButton(x, y, folder, this::onFolderClicked);
            stateManager.getFolderButtons().add(button);

            if (lastActiveFolderId != null && folder.getId() == lastActiveFolderId) {
                buttonToActivate = button;
            }
        }

        // Sync with legacy state manager for now (will be removed later)
        legacyStateManager.loadFolders(
            layoutManager.getPaddingX(), 
            layoutManager.getPaddingY(), 
            layoutManager.getFoldersPerRow(),
            layoutManager.getIconWidth() + (2 * layoutManager.getFolderSpacingX()),
            layoutManager.getFolderSpacingY()
        );

        // Set active folder if needed
        if (buttonToActivate != null) {
            stateManager.setActiveFolder(buttonToActivate);
        }
    }
    
    /**
     * Updates the vertical positions for folder names and bookmark display
     */
    private void updateLayoutPositions() {
        layoutManager.updateLayoutPositions(stateManager.getFolderButtons().size());
        
        // Update the bookmark manager with the calculated positions
        bookmarkManager.setCalculatedPositions(
            layoutManager.getCalculatedNameY(), 
            layoutManager.getCalculatedBookmarkDisplayY()
        );
    }
    
    /**
     * Called when a folder is clicked
     */
    private void onFolderClicked(FolderDataRepresentation folder) {
        // Find which button was clicked
        FolderButton clickedButton = null;
        for (FolderButton button : stateManager.getFolderButtons()) {
            if (button.getFolder() == folder) {
                clickedButton = button;
                break;
            }
        }

        if (clickedButton == null) {
            return;
        }

        // Fire the folder clicked event
        eventManager.fireFolderClickedEvent(folder);

        // Handle toggle behavior (deactivate if already active)
        if (stateManager.getActiveFolder() == clickedButton) {
            stateManager.deactivateActiveFolder();
            return;
        }

        // Set as active folder
        stateManager.setActiveFolder(clickedButton);
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Calculate the position for the Add button as if it's the first icon in the grid
        int[] addButtonPos = layoutManager.calculateAddButtonPosition();
        int addButtonX = addButtonPos[0];
        int addButtonY = addButtonPos[1];

        // Determine if the mouse is over the Add button at its new position
        isHovered = mouseX >= addButtonX && mouseY >= addButtonY &&
                   mouseX < addButtonX + width && mouseY < addButtonY + height;

        // Render the add folder button using the sprite sheet
        FolderButtonTextures.renderAddFolderIcon(graphics, addButtonX, addButtonY, isHovered);

        if (stateManager.areFoldersVisible()) {
            for (FolderButton button : stateManager.getFolderButtons()) {
                button.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        if (stateManager.hasActiveFolder()) {
            renderActiveFolderDetails(graphics, mouseX, mouseY);
        } else {
            currentDeleteButtonX = -1;
            currentDeleteButtonY = -1;
            deleteHovered = false;
        }

        if (stateManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            bookmarkManager.getBookmarkDisplay().render(graphics, mouseX, mouseY, partialTick);
        }

        updateExclusionZone();
    }
    
    /**
     * Renders details for the active folder, including name and delete button
     */
    private void renderActiveFolderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        FolderButton activeFolder = stateManager.getActiveFolder();
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
            layoutManager.getCalculatedNameY(),
            0xFFFFFF,
            true
        );

        // Show tooltip with the full name when hovering over a truncated name
        if (!displayName.equals(fullName) && mouseX >= 10 && mouseX < 10 + Minecraft.getInstance().font.width(displayName) &&
            mouseY >= layoutManager.getCalculatedNameY() - 4 && mouseY < layoutManager.getCalculatedNameY() + 10) {
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                Component.literal(fullName),
                mouseX, mouseY
            );
        }

        // Calculate and position the delete button using the layout manager
        int[] deleteButtonPos = layoutManager.calculateDeleteButtonPosition();
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
        if (stateManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            bookmarkDisplayHeight = bookmarkManager.getBookmarkDisplay().getHeight();
        }
        
        lastDrawnArea = layoutManager.updateExclusionZone(
            stateManager.getFolderButtons().size(), 
            stateManager.areFoldersVisible(), 
            stateManager.hasActiveFolder(),
            bookmarkDisplayHeight
        );
        
        // Update bookmark display bounds if active
        if (stateManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            Rect2i zone = layoutManager.getExclusionZone();
            int bookmarkDisplayWidth = zone.getWidth() + 10;
            bookmarkManager.getBookmarkDisplay().updateBounds(
                0, 
                layoutManager.getCalculatedBookmarkDisplayY(), 
                bookmarkDisplayWidth,
                bookmarkManager.getBookmarkDisplay().getHeight()
            );
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentDeleteButtonX >= 0 && button == 0 && deleteHovered) {
            // Fire delete button clicked event before deleting
            if (stateManager.hasActiveFolder()) {
                eventManager.fireDeleteButtonClickedEvent(stateManager.getActiveFolder().getFolder().getId());
            }
            
            stateManager.deleteActiveFolder();
            loadFolders();
            return true;
        }

        if (stateManager.areFoldersVisible()) {
            for (FolderButton folderButton : stateManager.getFolderButtons()) {
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
        int[] addButtonPos = layoutManager.calculateAddButtonPosition();
        int addButtonX = addButtonPos[0];
        int addButtonY = addButtonPos[1];

        // Check if the Add button was clicked at its new position
        if (mouseX >= addButtonX && mouseX < addButtonX + width &&
            mouseY >= addButtonY && mouseY < addButtonY + height && button == 0) {
            // Fire add button clicked event
            eventManager.fireAddButtonClickedEvent();
            
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
            layoutManager.calculateFoldersPerRow();
            loadFolders();
            updateLayoutPositions();
            
            // Force update bookmark display after window resize
            if (stateManager.hasActiveFolder()) {
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
        FolderDataRepresentation folder = stateManager.createFolder(name);
        // Update UI state after folder creation
        stateManager.setFoldersVisible(true);
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
        FolderButton targetFolder = stateManager.getFolderButtonAt(mouseX, mouseY);
        
        if (targetFolder != null) {
            // If dropped on a folder, activate it and add the ingredient
            stateManager.setActiveFolder(targetFolder);
            
            // Sync with legacy state for backward compatibility
            legacyStateManager.loadFolders(
                layoutManager.getPaddingX(), 
                layoutManager.getPaddingY(), 
                layoutManager.getFoldersPerRow(),
                layoutManager.getIconWidth() + (2 * layoutManager.getFolderSpacingX()),
                layoutManager.getFolderSpacingY()
            );
            
            // Fire ingredient dropped event
            eventManager.fireIngredientDroppedEvent(ingredient, targetFolder.getFolder().getId());
            
            // Use the bookmark manager to handle the actual ingredient storing
            return bookmarkManager.handleIngredientDrop(mouseX, mouseY, ingredient, stateManager.areFoldersVisible());
        }
        
        // Handle case where ingredient is dropped on bookmark area
        if (stateManager.hasActiveFolder()) {
            return bookmarkManager.handleIngredientDrop(mouseX, mouseY, ingredient, stateManager.areFoldersVisible());
        }
        
        return false;
    }
    
    public void tick() {
        checkForWindowResize();
        legacyStateManager.tickFolderButtons();
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
     * Gets the state manager instance
     * @return The state manager
     */
    public FolderStateManager getStateManager() {
        return stateManager;
    }
    
    /**
     * Gets the event system instance
     * @return The event system
     */
    public BookmarkEvents getEventSystem() {
        return eventSystem;
    }
    
    /**
     * Adds a listener for folder button clicks
     * @param listener The listener to add
     * @deprecated Use the FolderEventManager directly instead
     */
    @Deprecated
    public static void addClickListener(Consumer<FolderButtonSystem> listener) {
        if (listener != null) {
            // Bridge to the new event system
            FolderEventManager manager = FolderEventManager.getInstance();
            
            // Map all click events to this listener
            manager.addEventListener(FolderEventManager.EventType.ADD_BUTTON_CLICKED, event -> {
                listener.accept(null);
            });
            
            manager.addEventListener(FolderEventManager.EventType.FOLDER_CLICKED, event -> {
                listener.accept(null);
            });
            
            manager.addEventListener(FolderEventManager.EventType.DELETE_BUTTON_CLICKED, event -> {
                listener.accept(null);
            });
            
            manager.addEventListener(FolderEventManager.EventType.BOOKMARK_CLICKED, event -> {
                listener.accept(null);
            });
        }
    }
    
    /**
     * Removes a click listener
     * @param listener The listener to remove
     * @deprecated Use the FolderEventManager directly instead
     */
    @Deprecated
    public static void removeClickListener(Consumer<FolderButtonSystem> listener) {
        // Not implemented in the bridge - use EventManager directly instead
        ModLogger.warn("removeClickListener is deprecated - use FolderEventManager directly");
    }
    
    // Implementation of FolderButtonInterface
    
    @Override
    public List<FolderButton> getFolderButtons() {
        return stateManager.getFolderButtons();
    }

    @Override
    public boolean isBookmarkAreaAvailable() {
        return stateManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null;
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

    /**
     * Gets the layout manager instance
     * @return The layout manager
     */
    public FolderLayoutManager getLayoutManager() {
        return layoutManager;
    }
}
