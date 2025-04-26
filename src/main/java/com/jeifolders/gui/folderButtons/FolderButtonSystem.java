package com.jeifolders.gui.folderButtons;

import com.jeifolders.data.FolderDataRepresentation;
import com.jeifolders.data.FolderDataService;
import com.jeifolders.gui.FolderNameInputScreen;
import com.jeifolders.gui.bookmarks.BookmarkManager;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Main entry point for the folder button system.
 * Responsible for rendering, event handling, mouse interactions,
 * and overall UI state management.
 */
public class FolderButtonSystem extends AbstractWidget implements FolderButtonInterface {
    // Singleton instance for static access
    private static FolderButtonSystem instance;
    
    // UI State
    private int currentDeleteButtonX = -1;
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
    
    // Static initialization tracking
    private static boolean isInitialized = false;
    
    // First time loading flag
    private boolean firstTimeLoaded = true;
    private int ticksAfterInit = 0;
    private static final int FORCE_REFRESH_DELAY = 10; // Ticks to wait before forcing refresh
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderButtonSystem getInstance() {
        if (instance == null) {
            instance = new FolderButtonSystem();
        }
        return instance;
    }
    
    /**
     * Initialize the folder button system
     */
    public static void init() {
        if (!isInitialized) {
            // Register event handlers
            NeoForge.EVENT_BUS.register(FolderButtonSystem.class);
            
            // Initialize the singleton instance
            getInstance();
            
            isInitialized = true;
            ModLogger.debug("FolderButtonSystem initialized and registered with EVENT_BUS");
        }
    }
    
    /**
     * Check if the system has been initialized
     */
    public static boolean isInitialized() {
        return isInitialized && instance != null;
    }
    
    /**
     * Private constructor for singleton pattern
     */
    private FolderButtonSystem() {
        super(10, 10, FolderButtonTextures.ICON_WIDTH, FolderButtonTextures.ICON_HEIGHT, 
              Component.translatable("gui.jeifolders.folder"));
        
        ModLogger.debug("Initializing FolderButton with consolidated components");
        
        // Initialize the component classes
        this.folderManager = UnifiedFolderManager.getInstance();
        this.renderingManager = FolderRenderingManager.getInstance();
        this.bookmarkManager = new BookmarkManager(folderManager);
        
        // Register the dialog handler with the folder manager
        folderManager.setAddFolderDialogHandler(this::showFolderNameInputScreen);
        
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
        // Check for null folder manager buttons list
        if (folderManager.getFolderButtons() == null) {
            ModLogger.error("Folder manager's button list is null");
            return;
        }
        
        // Use the UnifiedFolderManager to create and initialize folder buttons
        FolderButton buttonToActivate = folderManager.initializeFolderButtons(renderingManager, this::onFolderClicked);
        
        // Set active folder if needed
        if (buttonToActivate != null) {
            folderManager.setActiveFolder(buttonToActivate);
        }
    }
    
    /**
     * Completely rebuilds the folder UI state from data
     */
    public void rebuildFolders() {
        ModLogger.debug("Performing complete folder UI rebuild");
        
        try {
            // Always reset data paths first to ensure we're reading from the correct location
            FolderDataService.getInstance().resetDataPaths();
            
            // Clear existing folder buttons
            folderManager.getFolderButtons().clear();
            
            // Reload folder data and create new buttons
            loadFolders();
            
            // Update layout positions
            updateLayoutPositions();
            
            // Update bookmark display
            if (folderManager.hasActiveFolder()) {
                refreshBookmarkDisplay();
            }
            
            ModLogger.info("Folder UI rebuild complete with {} folders", folderManager.getFolderButtons().size());
        } catch (Exception e) {
            ModLogger.error("Error during folder UI rebuild: {}", e.getMessage());
            e.printStackTrace();
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
        // Delegate folder click handling to UnifiedFolderManager
        folderManager.handleFolderClick(folder);
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset first time loading flag since we're rendering now
        firstTimeLoaded = false;
        
        // We no longer need to render the Add button directly here
        // as it will be rendered along with other folder buttons

        if (folderManager.areFoldersVisible()) {
            for (FolderButton button : folderManager.getFolderButtons()) {
                button.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        if (folderManager.hasActiveFolder()) {
            renderActiveFolderDetails(graphics, mouseX, mouseY);
        } else {
            currentDeleteButtonX = -1;
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
            ModLogger.debug("[NAME-DEBUG] No active folder to render name for");
            return;
        }

        String fullName = activeFolder.getFolder().getName();
        String displayName = fullName;
        // Limit the display name to 12 characters, adding "..." if it's longer
        if (fullName.length() > 12) {
            displayName = fullName.substring(0, 12) + "...";
        }

        // Use the correct Y position from renderingManager for the folder name
        int nameY = renderingManager.getFolderNameY();
        ModLogger.debug("[NAME-DEBUG] Drawing folder name '{}' at Y={}", displayName, nameY);
        
        // Changed back to white color (0xFFFFFF) for the folder name
        graphics.drawString(
            Minecraft.getInstance().font,
            displayName,
            10,
            nameY,
            0xFFFFFF, // White color
            true
        );
        
        // Log font metrics to ensure name is within visible area
        int fontHeight = Minecraft.getInstance().font.lineHeight;
        int stringWidth = Minecraft.getInstance().font.width(displayName);
        ModLogger.debug("[NAME-DEBUG] Font metrics: width={}, height={}, screen dimensions: {}x{}", 
                      stringWidth, fontHeight,
                      Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                      Minecraft.getInstance().getWindow().getGuiScaledHeight());

        // Show tooltip with the full name when hovering over a truncated name
        if (!displayName.equals(fullName) && mouseX >= 10 && mouseX < 10 + Minecraft.getInstance().font.width(displayName) &&
            mouseY >= nameY - 4 && mouseY < nameY + 10) {
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
        ModLogger.debug("[NAME-DEBUG] Delete button rendered at X={}, Y={}", deleteX, deleteY);

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
    
    /** 
     * Handles UI ticks for animation and updates
     */
    public void tick() {
        checkForWindowResize();
        
        // Handle first time initialization
        if (firstTimeLoaded) {
            ticksAfterInit++;
            
            if (ticksAfterInit >= FORCE_REFRESH_DELAY) {
                ModLogger.info("First-time initialization refresh triggered after {} ticks", ticksAfterInit);
                rebuildFolders();
                firstTimeLoaded = false;
                ticksAfterInit = 0;
            }
        }
        
        // Update animations for folder buttons
        if (folderManager.areFoldersVisible()) {
            for (FolderButton button : folderManager.getFolderButtons()) {
                button.tick();
            }
        }
    }
    
    /**
     * Check if window has been resized and handle UI adjustments if needed
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
                bookmarkManager.refreshBookmarkDisplay();
                ModLogger.info("Updated bookmark display after resize");
            }
        }
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

        return false;
    }
    
    /**
     * Creates a new folder with the given name
     * @param name The name of the folder
     * @return The newly created folder
     */
    public FolderDataRepresentation createFolder(String name) {
        // Delegate folder creation to the UnifiedFolderManager
        FolderDataRepresentation folder = folderManager.createFolder(name);
        
        // Handle UI updates after folder creation
        folderManager.setFoldersVisible(true);
        
        // Reload folder buttons from data
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
        // Add detailed logging about the incoming ingredient
        ModLogger.info("[DROP-DEBUG] handleIngredientDrop called with ingredient type: {}", 
            ingredient != null ? ingredient.getClass().getName() : "null");
        
        // First check if the ingredient is dropped on a folder button
        FolderButton targetFolder = folderManager.getFolderButtonAt(mouseX, mouseY);
        
        if (targetFolder != null) {
            // If dropped on a folder button, activate it and handle the ingredient drop
            ModLogger.info("[DROP-DEBUG] Target folder found: {}", targetFolder.getFolder().getName());
            folderManager.setActiveFolder(targetFolder);
            
            // Delegate ingredient drop handling to UnifiedFolderManager
            boolean result = folderManager.handleIngredientDropOnFolder(targetFolder.getFolder(), ingredient);
            ModLogger.info("[DROP-DEBUG] Folder drop result: {}", result);
            return result;
        }
        
        // If no specific folder was targeted, check if it's a drop on the bookmark display area
        if (folderManager.hasActiveFolder()) {
            ModLogger.info("[DROP-DEBUG] No target folder, checking bookmark display area");
            boolean result = folderManager.handleIngredientDropOnDisplay(mouseX, mouseY, ingredient);
            ModLogger.info("[DROP-DEBUG] Bookmark display drop result: {}", result);
            return result;
        }
        
        ModLogger.info("[DROP-DEBUG] No active folder or target folder, drop failed");
        return false;
    }
    
    /**
     * Sets the JEI runtime object
     * @param runtime The JEI runtime object from JEI API
     */
    public void setJeiRuntime(Object runtime) {
        JEIIntegrationFactory.getJEIService().setJeiRuntime(runtime);
        
        // Force a UI refresh now that JEI is available
        ModLogger.info("JEI runtime set, forcing folder UI refresh");
        
        // Schedule refresh on the main thread to ensure it happens after current operations
        Minecraft.getInstance().execute(() -> {
            // Force a complete refresh of the folders
            rebuildFolders();
            
            // Make sure the bookmark display is refreshed
            refreshBookmarkDisplay();
        });
    }

    public void initializeJeiRuntime() {
        JEIIntegrationFactory.getJEIService().registerRuntimeCallback(runtime -> {
            // Perform any initialization that depends on the JEI runtime
            ModLogger.debug("JEI runtime initialized in FolderButtonSystem");
        });
    }
    
    // Event handlers 
    
    @SubscribeEvent
    public static void onGuiInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractContainerScreen) {
            ModLogger.debug("Adding folder button to GUI: {}", screen.getClass().getSimpleName());
            
            // Get or create the singleton instance
            FolderButtonSystem folderButton = getInstance();
            
            // Add the button to the screen's listeners
            event.addListener(folderButton);
            
            // Force rebuild folders if this is the first screen after login
            if (folderButton.firstTimeLoaded && folderButton.getFolderManager().getFolderButtons().size() <= 1) {
                ModLogger.info("First GUI init detected, scheduling folder rebuild");
                // Reset the counter so we force a refresh soon
                folderButton.ticksAfterInit = 0;
            }
            
            // Make sure the bookmark display is refreshed after screen initialization
            folderButton.refreshBookmarkDisplay();
            ModLogger.debug("Refreshed bookmark display after GUI init");
        }
    }
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (isInitialized()) {
            getInstance().tick();
        }
    }
    
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (isInitialized()) {
            getInstance().tick();
        }
    }
    
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
