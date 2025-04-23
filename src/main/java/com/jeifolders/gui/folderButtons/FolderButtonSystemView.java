package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.gui.FolderNameInputScreen;
import com.jeifolders.gui.bookmarks.BookmarkManager;
import com.jeifolders.util.ModLogger;
import com.jeifolders.integration.JEIIntegrationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles UI rendering and interactions for the folder button system.
 * Responsible for rendering, mouse handling, and UI state management.
 */
public class FolderButtonSystemView extends AbstractWidget implements FolderButtonInterface {
    // Constants
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 10;
    private static final int ICON_WIDTH = FolderButtonTextures.ICON_WIDTH;
    private static final int ICON_HEIGHT = FolderButtonTextures.ICON_HEIGHT;
    private static final int FOLDER_SPACING_Y = 30;  
    private static final int FOLDER_SPACING_X = 2;
    private static final int EXCLUSION_PADDING = 10;

    // UI State
    private boolean isHovered = false;
    private boolean isFoldersVisible = true;
    private int foldersPerRow = 1;
    private int currentDeleteButtonX = -1;
    private int currentDeleteButtonY = -1;
    private boolean deleteHovered = false;
    
    // Layout calculations
    private int calculatedNameY = -1;
    private int nameYOffset = 0;
    private int calculatedBookmarkDisplayY = -1;
    private int cachedGuiScaledWidth = -1;
    private long lastCalculationTime = 0;
    private static final int LAYOUT_RECALC_INTERVAL_MS = 1000;
    
    // Window size tracking
    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;
    
    // Exclusion zone
    public static Rect2i lastDrawnArea = new Rect2i(0, 0, 0, 0);
    
    // Services
    private final FolderButtonStateManager stateManager;
    private final BookmarkManager bookmarkManager;
    
    // Event listeners
    private static final List<Consumer<FolderButtonSystemView>> clickListeners = new ArrayList<>();

    public FolderButtonSystemView(FolderButtonStateManager stateManager, BookmarkManager bookmarkManager) {
        super(PADDING_X, PADDING_Y, ICON_WIDTH, ICON_HEIGHT, Component.translatable("gui.jeifolders.folder"));
        this.stateManager = stateManager;
        this.bookmarkManager = bookmarkManager;
        this.width = ICON_WIDTH;
        this.height = ICON_HEIGHT;
        
        // Initialize window size tracking
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() != null) {
            lastWindowWidth = minecraft.getWindow().getGuiScaledWidth();
            lastWindowHeight = minecraft.getWindow().getGuiScaledHeight();
        }
        
        // Calculate initial layout
        calculateFoldersPerRow();
        stateManager.loadFolders(PADDING_X, PADDING_Y, foldersPerRow, 
                               ICON_WIDTH + (2 * FOLDER_SPACING_X), FOLDER_SPACING_Y);
        
        // Update layout positions and bookmark display bounds
        updateLayoutPositions();
        
        ModLogger.debug("FolderButtonView initialized with {} folders, visibility: {}, foldersPerRow: {}",
            stateManager.getFolderButtons().size(), isFoldersVisible, foldersPerRow);
            
        // Set up JEI runtime initialization
        initializeJeiRuntime();
        
        // Restore the state if needed
        if (stateManager.shouldRestoreFromStaticState()) {
            bookmarkManager.restoreFromStaticState();
        }
    }
    
    private void calculateFoldersPerRow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            foldersPerRow = 1;
            return;
        }

        int currentWidth = minecraft.getWindow().getGuiScaledWidth();
        long currentTime = System.currentTimeMillis();

        if (currentWidth == cachedGuiScaledWidth &&
            currentTime - lastCalculationTime < LAYOUT_RECALC_INTERVAL_MS) {
            return;
        }

        cachedGuiScaledWidth = currentWidth;
        lastCalculationTime = currentTime;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiWidth = 176;
        int guiLeft = (screenWidth - guiWidth) / 2;

        int availableWidth = Math.max(1, guiLeft - PADDING_X);
        int folderWidth = ICON_WIDTH + (2 * FOLDER_SPACING_X);
        foldersPerRow = Math.max(1, availableWidth / folderWidth);
        ModLogger.debug("Screen width: {}, Calculated folders per row: {}", screenWidth, foldersPerRow);
    }
    
    private void updateLayoutPositions() {
        // Calculate how many rows we need, now accounting for the Add button
        int effectiveButtonCount = stateManager.getFolderButtons().size() + 1;
        int rows = (int)Math.ceil((double)effectiveButtonCount / foldersPerRow);
        nameYOffset = rows * FOLDER_SPACING_Y;
        calculatedNameY = getY() + nameYOffset + 5;
        calculatedBookmarkDisplayY = calculatedNameY + 10;
        
        // Update the bookmark manager with the calculated positions
        bookmarkManager.setCalculatedPositions(calculatedNameY, calculatedBookmarkDisplayY);
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Calculate the position for the Add button as if it's the first icon in the grid
        int addButtonX = getX();
        int addButtonY = getY();

        // Determine if the mouse is over the Add button at its new position
        isHovered = mouseX >= addButtonX && mouseY >= addButtonY &&
                   mouseX < addButtonX + width && mouseY < addButtonY + height;

        // Render the add folder button using the sprite sheet
        FolderButtonTextures.renderAddFolderIcon(graphics, addButtonX, addButtonY, isHovered);

        if (isFoldersVisible) {
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
            calculatedNameY,
            0xFFFFFF,
            true
        );

        // Show tooltip with the full name when hovering over a truncated name
        if (!displayName.equals(fullName) && mouseX >= 10 && mouseX < 10 + Minecraft.getInstance().font.width(displayName) &&
            mouseY >= calculatedNameY - 4 && mouseY < calculatedNameY + 10) {
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                Component.literal(fullName),
                mouseX, mouseY
            );
        }

        // Calculate the exclusion zone width to position the delete button on the right edge
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiWidth = 176;
        int guiLeft = (screenWidth - guiWidth) / 2;
        int maxExclusionWidth = Math.max(50, guiLeft - 5);
        maxExclusionWidth = Math.max(40, maxExclusionWidth - 10);
        
        int exclusionWidth = Math.min(maxExclusionWidth, width + (EXCLUSION_PADDING * 2));
        if (isFoldersVisible && !stateManager.getFolderButtons().isEmpty() && foldersPerRow > 1) {
            int gridWidth = foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
            exclusionWidth = Math.min(maxExclusionWidth, Math.max(exclusionWidth, gridWidth + (EXCLUSION_PADDING * 2)));
        }
        
        // Position the delete button at the right edge of the exclusion zone with some padding
        int deleteX = exclusionWidth - 16 - 5;
        int deleteY = calculatedNameY - 4;
        
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
    
    private void updateExclusionZone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiWidth = 176;
        int guiLeft = (screenWidth - guiWidth) / 2;
        int maxExclusionWidth = Math.max(50, guiLeft - 5);

        maxExclusionWidth = Math.max(40, maxExclusionWidth - 10);

        int exclusionWidth = Math.min(maxExclusionWidth, width + (EXCLUSION_PADDING * 2));

        if (isFoldersVisible && !stateManager.getFolderButtons().isEmpty() && foldersPerRow > 1) {
            int gridWidth = foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
            exclusionWidth = Math.min(maxExclusionWidth, Math.max(exclusionWidth, gridWidth + (EXCLUSION_PADDING * 2)));
        }

        // Calculate the initial exclusion height - just tall enough for folders and their names
        int exclusionHeight;
        
        if (isFoldersVisible && !stateManager.getFolderButtons().isEmpty()) {
            // Calculate how many rows we have
            int totalButtons = stateManager.getFolderButtons().size() + 1;
            int rows = (int)Math.ceil((double)totalButtons / foldersPerRow);
            
            // Height should include icon height + text height (approx 10px) + padding
            int folderWithNameHeight = ICON_HEIGHT + 10 + 5;
            exclusionHeight = rows * FOLDER_SPACING_Y;
            
            // If we have only one row, just use the folder height + name height + padding
            if (rows == 1) {
                exclusionHeight = folderWithNameHeight;
            }
            
            exclusionHeight += 10;
        } else {
            // If no folders visible, just include the add button height
            exclusionHeight = height + (EXCLUSION_PADDING * 2);
        }

        // Only extend the exclusion zone when a folder is active
        if (stateManager.hasActiveFolder() && bookmarkManager.getBookmarkDisplay() != null) {
            int bookmarkDisplayWidth = exclusionWidth + 10;
            bookmarkManager.getBookmarkDisplay().updateBounds(0, calculatedBookmarkDisplayY, bookmarkDisplayWidth,
            bookmarkManager.getBookmarkDisplay().getHeight());
            exclusionHeight = calculatedBookmarkDisplayY - getY() + bookmarkManager.getBookmarkDisplay().getHeight() + 5;
        }

        int exclusionX = 5;
        int exclusionY = Math.max(0, getY() - EXCLUSION_PADDING);

        lastDrawnArea = new Rect2i(exclusionX, exclusionY, exclusionWidth, exclusionHeight);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentDeleteButtonX >= 0 && button == 0 && deleteHovered) {
            stateManager.deleteActiveFolder();
            stateManager.loadFolders(PADDING_X, PADDING_Y, foldersPerRow,
                                  ICON_WIDTH + (2 * FOLDER_SPACING_X), FOLDER_SPACING_Y);
            return true;
        }

        if (isFoldersVisible) {
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

        // Calculate the position for the Add button as if it's the first icon in the grid
        int addButtonX = getX();
        int addButtonY = getY();

        // Check if the Add button was clicked at its new position
        if (mouseX >= addButtonX && mouseX < addButtonX + width &&
            mouseY >= addButtonY && mouseY < addButtonY + height && button == 0) {
            this.onClick();
            return true;
        }

        return false;
    }
    
    private void onClick() {
        ModLogger.debug("Add folder button clicked");
        Minecraft.getInstance().setScreen(new FolderNameInputScreen(
            Minecraft.getInstance().screen,
            folderName -> {
                FolderDataRepresentation folder = stateManager.createFolder(folderName);
                isFoldersVisible = true;
                stateManager.loadFolders(PADDING_X, PADDING_Y, foldersPerRow,
                                      ICON_WIDTH + (2 * FOLDER_SPACING_X), FOLDER_SPACING_Y);
                updateLayoutPositions();

                clickListeners.forEach(listener -> listener.accept(this));
            }
        ));
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narrationOutput) {
        this.defaultButtonNarrationText(narrationOutput);
    }
    
    @Override
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        return bookmarkManager.handleIngredientDrop(mouseX, mouseY, ingredient, isFoldersVisible);
    }
    
    public void tick() {
        checkForWindowResize();
        stateManager.tickFolderButtons();
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
            calculateFoldersPerRow();
            stateManager.loadFolders(PADDING_X, PADDING_Y, foldersPerRow,
                                  ICON_WIDTH + (2 * FOLDER_SPACING_X), FOLDER_SPACING_Y);
            updateLayoutPositions();
            
            // Force update bookmark display after window resize
            if (stateManager.hasActiveFolder()) {
                // Use our explicit reload method which handles everything properly
                bookmarkManager.reloadBookmarkDisplay();
                ModLogger.info("Updated bookmark display after resize");
            }
        }
    }
    
    /**
     * Sets the JEI runtime object
     */
    public void setJeiRuntime(Object runtime) {
        JEIIntegrationFactory.getJEIService().setJeiRuntime(runtime);
    }

    public void initializeJeiRuntime() {
        JEIIntegrationFactory.getJEIService().registerRuntimeCallback(runtime -> {
            // Perform any initialization that depends on the JEI runtime
            ModLogger.debug("JEI runtime initialized in FolderButtonView");
        });
    }
    
    // Static methods for listeners
    
    public static void addClickListener(Consumer<FolderButtonSystemView> listener) {
        clickListeners.add(listener);
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

    
}