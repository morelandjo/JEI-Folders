package com.jeifolders.gui.controller;

import com.jeifolders.data.Folder;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.gui.common.MouseHitUtil;
import com.jeifolders.gui.interaction.IngredientDropTarget;
import com.jeifolders.gui.layout.FolderLayoutService;
import com.jeifolders.gui.screen.FolderScreenManager;
import com.jeifolders.gui.view.buttons.FolderButton;
import com.jeifolders.gui.view.buttons.FolderButtonTextures;
import com.jeifolders.gui.view.render.FolderRenderer;
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
 * Coordinates between state management, input handling, and screen management components.
 * Rendering is delegated to the centralized rendering system.
 */
public class FolderUIController extends AbstractWidget implements IngredientDropTarget {
    // Singleton instance for static access
    private static FolderUIController instance;
    
    // Window size tracking
    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;
    
    // Exclusion zone
    public static Rect2i lastDrawnArea = new Rect2i(0, 0, 0, 0);
    
    // Component managers
    private final FolderStateManager folderManager;
    private final FolderLayoutService layoutService;
    
    // Newly refactored components
    private final FolderRenderer renderer;
    private final FolderScreenManager screenManager;
    
    // Static initialization tracking
    private static boolean isInitialized = false;
    
    // First time loading flag
    private boolean firstTimeLoaded = true;
    private int ticksAfterInit = 0;
    private static final int FORCE_REFRESH_DELAY = 10; // Ticks to wait before forcing refresh
    
    /**
     * Get the singleton instance
     */
    public static synchronized FolderUIController getInstance() {
        if (instance == null) {
            instance = new FolderUIController();
        }
        return instance;
    }
    
    /**
     * Initialize the folder button system
     */
    public static void init() {
        if (!isInitialized) {
            // Register event handlers
            NeoForge.EVENT_BUS.register(FolderUIController.class);
            
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
    private FolderUIController() {
        super(10, 10, FolderButtonTextures.ICON_WIDTH, FolderButtonTextures.ICON_HEIGHT, 
              Component.translatable("gui.jeifolders.folder"));
        
        ModLogger.debug("Initializing FolderButton with refactored components");
        
        // Initialize the component classes
        this.folderManager = FolderStateManager.getInstance();
        this.layoutService = FolderLayoutService.getInstance();
        
        // Create the specialized components
        this.renderer = new FolderRenderer(folderManager, layoutService);
        this.screenManager = new FolderScreenManager(folderManager, this::createFolder);
        
        // Initialize window size tracking
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() != null) {
            lastWindowWidth = minecraft.getWindow().getGuiScaledWidth();
            lastWindowHeight = minecraft.getWindow().getGuiScaledHeight();
        }
        
        // Calculate initial layout and load folders
        layoutService.calculateFoldersPerRow();
        loadFolders();
        
        // Update layout positions and bookmark display bounds
        updateLayoutPositions();
        
        ModLogger.debug("FolderButtonSystem initialized with {} folders, visibility: {}, foldersPerRow: {}",
            folderManager.getFolderButtons().size(), folderManager.areFoldersVisible(), layoutService.getFoldersPerRow());
            
        // Set up JEI runtime initialization
        initializeJeiRuntime();
        
        // Restore the state if needed
        if (folderManager.shouldRestoreFromStaticState()) {
            folderManager.createBookmarkDisplay(true);
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
        
        // Use the FolderLayoutService to create and initialize folder buttons
        FolderButton buttonToActivate = folderManager.initializeFolderButtons(layoutService, this::onFolderClicked);
        
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
            FolderStorageService.getInstance().resetDataPaths();
            
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
        layoutService.updateLayoutPositions(folderManager.getFolderButtons().size());
    }
    
    /**
     * Called when a folder is clicked
     */
    private void onFolderClicked(Folder folder) {
        // Delegate folder click handling to FolderStateManager
        folderManager.handleFolderClick(folder);
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset first time loading flag since we're rendering now
        firstTimeLoaded = false;
        
        // Delegate rendering to the FolderRenderer facade which uses the centralized UIRenderManager
        renderer.renderWidget(graphics, mouseX, mouseY, partialTick);
        
        // Update the exclusion zone via the layout service
        lastDrawnArea = layoutService.updateExclusionZoneAndUI();
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
            folderManager.tickFolderButtons();
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
            layoutService.calculateFoldersPerRow();
            loadFolders();
            updateLayoutPositions();
            
            // Force update bookmark display after window resize
            if (folderManager.hasActiveFolder()) {
                folderManager.refreshBookmarkDisplay();
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
        return folderManager.refreshBookmarkDisplay();
    }
    
    /**
     * Gets the folder state manager instance
     * @return The folder state manager
     */
    public FolderStateManager getFolderManager() {
        return folderManager;
    }
    
    /**
     * Gets the layout service instance
     * @return The layout service
     */
    public FolderLayoutService getLayoutService() {
        return layoutService;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renderer.getCurrentDeleteButtonX() >= 0 && button == 0 && renderer.isDeleteButtonHovered()) {
            // Fire delete button clicked event before deleting
            if (folderManager.hasActiveFolder()) {
                folderManager.fireDeleteButtonClickedEvent(folderManager.getActiveFolder().getFolder().getId());
            }
            
            folderManager.deleteActiveFolder();
            return true;
        }

        if (folderManager.areFoldersVisible()) {
            for (FolderButton folderButton : folderManager.getFolderButtons()) {
                if (MouseHitUtil.isMouseOverButton(mouseX, mouseY, folderButton)) {
                    // Handle different button types
                    switch (folderButton.getButtonType()) {
                        case ADD:
                            // Add button handled by the folder manager
                            folderManager.handleAddFolderButtonClick(null);
                            break;
                        case NORMAL:
                            // Normal folder buttons handled by the folder manager
                            if (folderButton.getFolder() != null) {
                                folderManager.handleFolderClick(folderButton.getFolder());
                            }
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            }
        }

        // Check if the click should be handled by the bookmark display
        return folderManager.hasActiveFolder() && 
               folderManager.getBookmarkDisplay() != null &&
               folderManager.handleBookmarkDisplayClick(mouseX, mouseY, button);
    }
    
    @Override
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        if (ingredient == null) {
            ModLogger.debug("Cannot handle ingredient drop: ingredient is null");
            return false;
        }
        
        if (!folderManager.areFoldersVisible()) {
            ModLogger.debug("Cannot handle ingredient drop: folders not visible");
            return false;
        }
        
        return folderManager.handleIngredientDrop(mouseX, mouseY, ingredient, folderManager.areFoldersVisible());
    }
    
    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narrationOutput) {
        this.defaultButtonNarrationText(narrationOutput);
    }
    
    @Override
    public List<FolderButton> getFolderButtons() {
        return folderManager.getFolderButtons();
    }

    @Override
    public boolean isBookmarkAreaAvailable() {
        return folderManager.hasActiveFolder() && folderManager.getBookmarkDisplay() != null;
    }

    @Override
    public Rect2i getBookmarkDisplayArea() {
        if (folderManager.getBookmarkDisplay() != null) {
            return new Rect2i(
                folderManager.getBookmarkDisplay().getX(),
                folderManager.getBookmarkDisplay().getY(),
                folderManager.getBookmarkDisplay().getWidth(),
                folderManager.getBookmarkDisplay().getHeight()
            );
        }
        return new Rect2i(0, 0, 0, 0);
    }
    
    /**
     * Helper method to check if the mouse is over a button
     * @deprecated Use MouseHitUtil.isMouseOverButton instead
     */
    @Deprecated
    private boolean isMouseOver(double mouseX, double mouseY, FolderButton button) {
        return MouseHitUtil.isMouseOverButton(mouseX, mouseY, button);
    }
    
    /**
     * Creates a new folder with the given name
     * @param name The name of the folder
     * @return The newly created folder
     */
    public Folder createFolder(String name) {
        // Delegate folder creation to the FolderStateManager
        Folder folder = folderManager.createFolder(name);
        
        // Handle UI updates after folder creation
        folderManager.setFoldersVisible(true);
        
        // Reload folder buttons from data
        loadFolders();
        updateLayoutPositions();
        
        return folder;
    }
    
    public void initializeJeiRuntime() {
        JEIIntegrationFactory.getJEIService().registerRuntimeCallback(runtime -> {
            // Perform any initialization that depends on the JEI runtime
            ModLogger.debug("JEI runtime initialized in FolderButtonSystem");
        });
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
    
    // Event handlers 
    
    @SubscribeEvent
    public static void onGuiInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractContainerScreen) {
            ModLogger.debug("Adding folder button to GUI: {}", screen.getClass().getSimpleName());
            
            // Get or create the singleton instance
            FolderUIController folderButton = getInstance();
            
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
    
    /**
     * Gets the folder renderer instance
     * @return The folder renderer
     */
    public FolderRenderer getRenderer() {
        return renderer;
    }
    
    /**
     * Gets the screen manager instance
     * @return The screen manager
     */
    public FolderScreenManager getScreenManager() {
        return screenManager;
    }
}
