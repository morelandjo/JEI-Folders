package com.jeifolders.gui;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderManager;
import com.jeifolders.integration.JEIIntegration;
import com.jeifolders.integration.JEIIngredientManager;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class FolderButton extends AbstractWidget implements FolderButtonInterface {
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 10;
    private static final int ICON_WIDTH = GuiTextures.ICON_WIDTH;
    private static final int ICON_HEIGHT = GuiTextures.ICON_HEIGHT;
    private static final int FOLDER_SPACING_Y = 30;  // Vertical spacing between folder rows - adjust this value
    private static final int FOLDER_SPACING_X = 2;   // Horizontal spacing between folders - adjust this value

    private int foldersPerRow = 1;

    public static Rect2i lastDrawnArea = new Rect2i(0, 0, 0, 0);
    private static final int EXCLUSION_PADDING = 10;

    private boolean isHovered = false;
    private static final List<Consumer<FolderButton>> clickListeners = new ArrayList<>();
    private final List<FolderRowButton> folderButtons = new ArrayList<>();
    private boolean isFoldersVisible = true;

    private FolderRowButton activeFolder = null;

    private int currentDeleteButtonX = -1;
    private int currentDeleteButtonY = -1;
    private boolean deleteHovered = false;

    private FolderBookmarkContentsDisplay bookmarkDisplay;
    private final FolderManager folderManager = FolderManager.getInstance();

    private ImmutableRect2i lastBookmarkBounds = null;
    private int cachedGuiScaledWidth = -1;
    private long lastCalculationTime = 0;
    private static final int LAYOUT_RECALC_INTERVAL_MS = 1000;

    private int calculatedNameY = -1;
    private int nameYOffset = 0;
    private int calculatedBookmarkDisplayY = -1;

    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;

    private Folder lastActiveFolder = null;

    private static Integer lastActiveFolderId = null;
    private static List<ITypedIngredient<?>> lastBookmarkContents = new ArrayList<>();
    private static long lastGuiRebuildTime = 0;
    private static final long GUI_REBUILD_DEBOUNCE_TIME = 500;

    public FolderButton() {
        // Initialize at the same position as before, but we'll adjust the position for rendering
        super(PADDING_X, PADDING_Y, ICON_WIDTH, ICON_HEIGHT, Component.translatable("gui.jeifolders.folder"));
        this.width = ICON_WIDTH;
        this.height = ICON_HEIGHT;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() != null) {
            lastWindowWidth = minecraft.getWindow().getGuiScaledWidth();
            lastWindowHeight = minecraft.getWindow().getGuiScaledHeight();
        }

        calculateFoldersPerRow();
        loadFolders();
        ModLogger.debug("FolderButton initialized with {} folders, visibility: {}, foldersPerRow: {}",
            folderButtons.size(), isFoldersVisible, foldersPerRow);

        // Create the bookmark display with proper initialization
        createBookmarkDisplay();

        if (lastActiveFolderId != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastGuiRebuildTime < GUI_REBUILD_DEBOUNCE_TIME) {
                ModLogger.info("Restoring active folder from static cache. ID: {}, Time since rebuild: {}ms",
                    lastActiveFolderId, (currentTime - lastGuiRebuildTime));
                restoreFolderFromStaticState();
            }
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

    private void loadFolders() {
        Folder folderToRestore = lastActiveFolder;
        Integer folderIdToRestore = folderToRestore != null ? folderToRestore.getId() : null;

        if (activeFolder != null) {
            folderToRestore = activeFolder.getFolder();
            folderIdToRestore = folderToRestore.getId();
            lastActiveFolder = folderToRestore;
        }

        if (folderIdToRestore == null && lastActiveFolderId != null) {
            folderIdToRestore = lastActiveFolderId;
        }

        activeFolder = null;
        folderButtons.clear();
        folderManager.loadData();

        List<Folder> folders = folderManager.getAllFolders();
        int startX = getX();
        int startY = getY();

        FolderRowButton buttonToActivate = null;

        // Start positioning actual folders from index 1 in the grid to leave space for the Add button
        for (int i = 0; i < folders.size(); i++) {
            Folder folder = folders.get(i);
            // Position in grid - accounting for add button at position 0
            int gridPosition = i + 1; // +1 to leave index 0 for Add button
            int row = gridPosition / foldersPerRow;
            int col = gridPosition % foldersPerRow;
            int x = startX + col * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
            int y = startY + row * FOLDER_SPACING_Y;

            FolderRowButton button = new FolderRowButton(x, y, folder, this::onFolderClicked);
            folderButtons.add(button);

            if (folderIdToRestore != null && folder.getId() == folderIdToRestore) {
                buttonToActivate = button;
            }
        }

        updateLayoutPositions();

        if (buttonToActivate != null) {
            buttonToActivate.setActive(true);
            activeFolder = buttonToActivate;
            lastActiveFolderId = buttonToActivate.getFolder().getId();

            if (bookmarkDisplay != null) {
                bookmarkDisplay.setActiveFolder(buttonToActivate.getFolder());
                updateBookmarkDisplayBounds();
            }
        } else if (folderIdToRestore != null) {
            lastActiveFolder = null;
            if (lastActiveFolderId != null && lastActiveFolderId.equals(folderIdToRestore)) {
                lastActiveFolderId = null;
                lastBookmarkContents = new ArrayList<>();
            }
        }
    }

    private void updateLayoutPositions() {
        // Calculate how many rows we need, now accounting for the Add button
        int effectiveButtonCount = folderButtons.size() + 1; // +1 for the Add button
        int rows = (int)Math.ceil((double)effectiveButtonCount / foldersPerRow);
        nameYOffset = rows * FOLDER_SPACING_Y;
        calculatedNameY = getY() + nameYOffset + 5;
        calculatedBookmarkDisplayY = calculatedNameY + 10;
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
        GuiTextures.renderAddFolderIcon(graphics, addButtonX, addButtonY, isHovered);

        if (isFoldersVisible) {
            for (FolderRowButton button : folderButtons) {
                button.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        if (activeFolder != null) {
            renderActiveFolderDetails(graphics, mouseX, mouseY);
        } else {
            currentDeleteButtonX = -1;
            currentDeleteButtonY = -1;
            deleteHovered = false;
        }

        if (activeFolder != null && bookmarkDisplay != null) {
            bookmarkDisplay.render(graphics, mouseX, mouseY, partialTick);
        }

        updateExclusionZone();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentDeleteButtonX >= 0 && button == 0 && deleteHovered) {
            deleteActiveFolder();
            return true;
        }

        if (isFoldersVisible) {
            for (FolderRowButton folderButton : folderButtons) {
                if (folderButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        if (activeFolder != null && bookmarkDisplay != null) {
            // Add page navigation handling
            if (button == 0) {
                // Check if next page button is clicked
                if (bookmarkDisplay.isNextButtonClicked(mouseX, mouseY)) {
                    bookmarkDisplay.nextPage();
                    return true;
                }

                // Check if back button is clicked
                if (bookmarkDisplay.isBackButtonClicked(mouseX, mouseY)) {
                    bookmarkDisplay.previousPage();
                    return true;
                }
            }

            Optional<String> clickedBookmarkKey = bookmarkDisplay.getBookmarkKeyAt(mouseX, mouseY);
            if (clickedBookmarkKey.isPresent()) {
                return true;
            }
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

    private void renderActiveFolderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
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
        if (isFoldersVisible && !folderButtons.isEmpty() && foldersPerRow > 1) {
            int gridWidth = foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
            exclusionWidth = Math.min(maxExclusionWidth, Math.max(exclusionWidth, gridWidth + (EXCLUSION_PADDING * 2)));
        }
        
        // Position the delete button at the right edge of the exclusion zone with some padding
        int deleteX = exclusionWidth - 16 - 5; // Icon width (16) + padding (5)
        int deleteY = calculatedNameY - 4;
        
        // Render the delete button using the sprite sheet
        GuiTextures.renderDeleteFolderIcon(graphics, deleteX, deleteY);

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

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narrationOutput) {
        this.defaultButtonNarrationText(narrationOutput);
    }

    @Override
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        if (ingredient == null) {
            ModLogger.warn("Received null ingredient for drop");
            return false;
        }

        String key = JEIIngredientManager.getKeyForIngredient(ingredient);
        if (key == null || key.isEmpty()) {
            ModLogger.warn("Failed to generate key for ingredient: {}", ingredient);
            return false;
        }

        if (isFoldersVisible) {
            FolderRowButton targetButton = getFolderButtonAt(mouseX, mouseY);
            if (targetButton != null) {
                Folder folder = targetButton.getFolder();
                ModLogger.debug("Adding bookmark {} to folder {}", key, folder.getName());
                folderManager.addBookmarkToFolder(folder.getId(), key);
                targetButton.playSuccessAnimation();

                if (activeFolder != null && activeFolder.getFolder().getId() == folder.getId()) {
                    bookmarkDisplay.refreshBookmarks();
                }

                return true;
            }
        }

        if (activeFolder != null && bookmarkDisplay != null && bookmarkDisplay.isMouseOver(mouseX, mouseY)) {
            folderManager.addBookmarkToFolder(activeFolder.getFolder().getId(), key);
            bookmarkDisplay.refreshBookmarks();
            return true;
        }

        return false;
    }

    @Override
    public List<FolderRowButton> getFolderButtons() {
        return folderButtons;
    }

    @Override
    public boolean isBookmarkAreaAvailable() {
        return activeFolder != null && bookmarkDisplay != null;
    }

    @Override
    public Rect2i getBookmarkDisplayArea() {
        if (bookmarkDisplay != null) {
            return new Rect2i(
                bookmarkDisplay.getX(),
                bookmarkDisplay.getY(),
                bookmarkDisplay.getWidth(),
                bookmarkDisplay.getHeight()
            );
        }
        return new Rect2i(0, 0, 0, 0);
    }

    public FolderRowButton getFolderButtonAt(double mouseX, double mouseY) {
        if (isFoldersVisible) {
            for (FolderRowButton button : folderButtons) {
                if (mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
                    mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
                    return button;
                }
            }
        }
        return null;
    }

    private boolean isIngredientBeingDragged() {
        Optional<ITypedIngredient<?>> ingredient = JEIIntegration.getDraggedIngredient();
        boolean isDragging = ingredient.isPresent();
        if (isDragging) {
            ModLogger.debug("[HOVER-DEBUG] isIngredientBeingDragged returning true, ingredient={}", ingredient.get());
        }
        return isDragging;
    }

    private void createBookmarkDisplay() {
        bookmarkDisplay = new FolderBookmarkContentsDisplay(folderManager);
        
        // Make sure the display knows about any active folder and has bounds set
        if (activeFolder != null) {
            bookmarkDisplay.setActiveFolder(activeFolder.getFolder());
            updateLayoutPositions();
            updateBookmarkDisplayBounds();
            
            // Apply cached bookmark contents if available
            if (!lastBookmarkContents.isEmpty()) {
                ModLogger.debug("Applying {} cached bookmark items during display creation", 
                    lastBookmarkContents.size());
                bookmarkDisplay.setIngredients(lastBookmarkContents);
            }
        }
    }

    private void updateBookmarkDisplayBounds() {
        if (bookmarkDisplay == null) return;

        int bookmarkDisplayWidth = 200;
        int bookmarkDisplayHeight = 100;

        if (lastBookmarkBounds == null ||
            lastBookmarkBounds.getY() != calculatedBookmarkDisplayY ||
            lastBookmarkBounds.getWidth() != bookmarkDisplayWidth ||
            lastBookmarkBounds.getHeight() != bookmarkDisplayHeight) {

            lastBookmarkBounds = new ImmutableRect2i(0, calculatedBookmarkDisplayY,
                                                bookmarkDisplayWidth, bookmarkDisplayHeight);
            bookmarkDisplay.updateBounds(0, calculatedBookmarkDisplayY,
                                       bookmarkDisplayWidth, bookmarkDisplayHeight);
        }
    }

    private void restoreFolderFromStaticState() {
        if (lastActiveFolderId == null) return;

        for (FolderRowButton button : folderButtons) {
            if (button.getFolder().getId() == lastActiveFolderId) {
                ModLogger.info("Found matching folder to restore: {}", button.getFolder().getName());
                button.setActive(true);
                activeFolder = button;

                if (bookmarkDisplay != null) {
                    bookmarkDisplay.setActiveFolder(button.getFolder());
                    if (!lastBookmarkContents.isEmpty()) {
                        bookmarkDisplay.setIngredients(lastBookmarkContents);
                    }
                    updateBookmarkDisplayBounds();
                }
                return;
            }
        }
        ModLogger.warn("Could not find folder with ID {} to restore", lastActiveFolderId);
        lastActiveFolderId = null;
        lastBookmarkContents = new ArrayList<>();
    }

    private void onFolderClicked(Folder folder) {
        // Start timing for performance tracking
        long startTime = System.currentTimeMillis();
        ModLogger.debug("Folder clicked: {}", folder.getName());

        FolderRowButton clickedButton = null;
        for (FolderRowButton button : folderButtons) {
            if (button.getFolder() == folder) {
                clickedButton = button;
                break;
            }
        }

        if (clickedButton == null) {
            return;
        }

        if (activeFolder == clickedButton) {
            activeFolder.setActive(false);
            activeFolder = null;
            updateBookmarkDisplayBounds();
            lastActiveFolderId = null;
            lastBookmarkContents = new ArrayList<>();

            if (bookmarkDisplay != null) {
                bookmarkDisplay.setActiveFolder(null);
            }

            ModLogger.debug("Folder deactivated, static state cleared");
            return;
        }

        // Deactivate any previous folder first
        if (activeFolder != null) {
            activeFolder.setActive(false);
        }

        // Set the new folder as active in the UI
        clickedButton.setActive(true);
        activeFolder = clickedButton;

        // Update static tracking for GUI rebuilds
        lastActiveFolderId = folder.getId();
        lastGuiRebuildTime = System.currentTimeMillis();
        
        // Only update display once to avoid multiple redraws
        bookmarkDisplay.setActiveFolder(folder);
        updateBookmarkDisplayBounds();
        
        // Update cache after display is ready
        safeUpdateBookmarkContents();
        
        long endTime = System.currentTimeMillis();
        ModLogger.info("Folder activation completed in {}ms. ID: {}, Bookmarks: {}",
            (endTime - startTime),
            lastActiveFolderId, 
            lastBookmarkContents.size());
    }

    private void safeUpdateBookmarkContents() {
        if (bookmarkDisplay != null) {
            lastBookmarkContents = new ArrayList<>(bookmarkDisplay.getIngredients());
        } else {
            lastBookmarkContents = new ArrayList<>();
        }
    }

    /**
     * Explicitly reloads the bookmark display for the active folder
     * This should be called when the display seems to be missing or needs refreshed
     */
    public void reloadBookmarkDisplay() {
        if (activeFolder == null) return;
        
        ModLogger.info("Explicitly reloading bookmark display");
        
        // Save the current ingredients if any
        List<ITypedIngredient<?>> savedIngredients = new ArrayList<>();
        if (bookmarkDisplay != null) {
            savedIngredients = bookmarkDisplay.getIngredients();
        }
        
        // Create a new display
        createBookmarkDisplay();
        
        // If we had ingredients before, restore them
        if (!savedIngredients.isEmpty()) {
            bookmarkDisplay.setIngredients(savedIngredients);
        }
        // Otherwise use any cached ones
        else if (!lastBookmarkContents.isEmpty()) {
            bookmarkDisplay.setIngredients(lastBookmarkContents);
        }
        
        updateBookmarkDisplayBounds();
    }

    private void deleteActiveFolder() {
        if (activeFolder == null) {
            return;
        }

        int folderId = activeFolder.getFolder().getId();
        ModLogger.debug("Deleting folder: {} (ID: {})", activeFolder.getFolder().getName(), folderId);

        FolderManager.getInstance().removeFolder(folderId);
        activeFolder = null;
        loadFolders();
    }

    private void onClick() {
        ModLogger.debug("Folder button clicked");
        Minecraft.getInstance().setScreen(new FolderNameInputScreen(
            Minecraft.getInstance().screen,
            folderName -> {
                FolderManager folderManager = FolderManager.getInstance();
                Folder folder = folderManager.createFolder(folderName);
                ModLogger.debug("Created folder: {} (ID: {})", folder.getName(), folder.getId());
                isFoldersVisible = true;
                loadFolders();

                clickListeners.forEach(listener -> listener.accept(this));
            }
        ));
    }

    public void setJeiRuntime(IJeiRuntime jeiRuntime) {
        JEIIntegration.setJeiRuntime(jeiRuntime);
    }

    public void tick() {
        checkForWindowResize();
        for (FolderRowButton button : folderButtons) {
            button.tick();
        }
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
                    
            // Store the active folder before rebuilding UI
            if (activeFolder != null) {
                lastActiveFolder = activeFolder.getFolder();
                ModLogger.debug("Saving active folder during resize: {} (ID: {})",
                    lastActiveFolder.getName(), lastActiveFolder.getId());
                
                // DETAILED DEBUG: Log what's actually in the active folder and static state
                ModLogger.info("DEBUG-RESIZE: Active folder before resize - Name: {}, ID: {}, Bookmarks: {}",
                    lastActiveFolder.getName(), lastActiveFolder.getId(), lastActiveFolder.getBookmarkKeys().size());
                ModLogger.info("DEBUG-RESIZE: Static state before resize - LastActiveFolderId: {}, LastBookmarkContents: {} items", 
                    lastActiveFolderId, lastBookmarkContents.size());
                
                // IMPORTANT: Update static preservation variables here too!
                lastActiveFolderId = lastActiveFolder.getId();
                lastGuiRebuildTime = System.currentTimeMillis();
                
                // Use our safe method to update bookmark contents
                safeUpdateBookmarkContents();
            } else {
                ModLogger.info("DEBUG-RESIZE: No active folder to preserve during resize");
            }
            
            // Update cached window dimensions
            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
            
            // Recalculate layout with preserved folder state
            calculateFoldersPerRow();
            
            // NEW DEBUG: Log state before loading folders
            ModLogger.info("DEBUG-RESIZE: Before loadFolders() - lastActiveFolder: {}, lastActiveFolderId: {}", 
                (lastActiveFolder != null ? lastActiveFolder.getId() : "null"), 
                lastActiveFolderId);
                
            loadFolders();
            
            // NEW DEBUG: Log state after loading folders
            ModLogger.info("DEBUG-RESIZE: After loadFolders() - activeFolder: {}, lastActiveFolder: {}, lastActiveFolderId: {}", 
                (activeFolder != null ? activeFolder.getFolder().getId() : "null"),
                (lastActiveFolder != null ? lastActiveFolder.getId() : "null"), 
                lastActiveFolderId);
                
            // Force update bookmark display after window resize
            if (activeFolder != null && bookmarkDisplay != null) {
                // Use our explicit reload method which handles everything properly
                reloadBookmarkDisplay();
                
                ModLogger.info("DEBUG-RESIZE: Explicitly updated bookmark display after resize for folder: {}", 
                    activeFolder.getFolder().getName());
            }
        }
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

        if (isFoldersVisible && !folderButtons.isEmpty() && foldersPerRow > 1) {
            int gridWidth = foldersPerRow * (ICON_WIDTH + (2 * FOLDER_SPACING_X));
            exclusionWidth = Math.min(maxExclusionWidth, Math.max(exclusionWidth, gridWidth + (EXCLUSION_PADDING * 2)));
        }

        // Calculate the initial exclusion height - just tall enough for folders and their names
        int exclusionHeight;
        
        if (isFoldersVisible && !folderButtons.isEmpty()) {
            // Calculate how many rows we have
            int totalButtons = folderButtons.size() + 1; // +1 for the Add button
            int rows = (int)Math.ceil((double)totalButtons / foldersPerRow);
            
            // Height should include icon height + text height (approx 10px) + padding
            int folderWithNameHeight = ICON_HEIGHT + 10 + 5;
            exclusionHeight = rows * FOLDER_SPACING_Y;
            
            // If we have only one row, just use the folder height + name height + padding
            if (rows == 1) {
                exclusionHeight = folderWithNameHeight;
            }
            
            // Add an extra 10 pixels to the bottom padding
            exclusionHeight += 10;
        } else {
            // If no folders visible, just include the add button height
            exclusionHeight = height + (EXCLUSION_PADDING * 2);
        }

        // Only extend the exclusion zone when a folder is active
        if (activeFolder != null && bookmarkDisplay != null) {
            int bookmarkDisplayWidth = exclusionWidth + 10;
            bookmarkDisplay.updateBounds(0, calculatedBookmarkDisplayY, bookmarkDisplayWidth, bookmarkDisplay.getHeight());
            exclusionHeight = calculatedBookmarkDisplayY - getY() + bookmarkDisplay.getHeight() + 5;
        }

        int exclusionX = 5;
        int exclusionY = Math.max(0, getY() - EXCLUSION_PADDING);

        lastDrawnArea = new Rect2i(exclusionX, exclusionY, exclusionWidth, exclusionHeight);
    }

    public static void addClickListener(Consumer<FolderButton> listener) {
        clickListeners.add(listener);
    }

    public void markContentChanged() {
        // Method preserved for API compatibility
    }

    private void highlightDropTargets(GuiGraphics graphics, int mouseX, int mouseY) {
        // This method is now deliberately empty.
        // We've completely disabled highlighting of folders when an ingredient might be dragged.
        ModLogger.debug("[HOVER-DEBUG] highlightDropTargets called but doing nothing (green highlighting disabled)");
    }
}
