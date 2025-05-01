package com.jeifolders.ui.controllers;

import com.jeifolders.data.Folder;
import com.jeifolders.core.FolderManager;
import com.jeifolders.data.FolderStorageService;
import com.jeifolders.integration.JEIIntegrationFactory;
import com.jeifolders.integration.JEIRuntime;
import com.jeifolders.integration.TypedIngredient;
import com.jeifolders.ui.components.buttons.FolderButton;
import com.jeifolders.ui.components.buttons.FolderButtonTextures;
import com.jeifolders.ui.components.contents.FolderContentsView;
import com.jeifolders.ui.display.BookmarkDisplayManager;
import com.jeifolders.ui.interaction.FolderInteractionHandler;
import com.jeifolders.ui.interaction.IngredientDropTarget;
import com.jeifolders.ui.layout.FolderLayoutService;
import com.jeifolders.ui.render.UIRenderManager;
import com.jeifolders.ui.screen.FolderScreenManager;
import com.jeifolders.ui.state.FolderUIStateManager;
import com.jeifolders.ui.util.LayoutConstants;
import com.jeifolders.util.ModLogger;
import mezz.jei.api.runtime.IJeiRuntime;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the folder button system.
 * Coordinates between state management, input handling, and screen management components.
 */
public class FolderUIController extends AbstractWidget implements IngredientDropTarget {
    // Singleton instance for static access
    private static FolderUIController instance;
    
    // Window size tracking
    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;
    
    // Exclusion zone
    public static Rect2i lastDrawnArea = new Rect2i(0, 0, 0, 0);
    
    // Component-based architecture components
    private FolderManager coreFolderManager;
    private FolderUIStateManager uiStateManager;
    private BookmarkDisplayManager displayManager;
    private FolderInteractionHandler interactionHandler;
    private FolderStorageService storageService;
    
    // Layout and rendering components
    private final FolderLayoutService layoutService;
    private final UIRenderManager renderManager;
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
     * Initialize the folder button system with a specific FolderManager instance
     * 
     * @param folderManager The FolderManager instance to use
     */
    public static void init(FolderManager folderManager) {
        if (!isInitialized) {
            // Register event handlers
            NeoForge.EVENT_BUS.register(FolderUIController.class);
            
            // Initialize the singleton instance
            FolderUIController controller = getInstance();
            
            // Explicitly connect with the given FolderManager
            controller.connectComponents(folderManager);
            
            isInitialized = true;
            ModLogger.error("[INIT-DEBUG] FolderButtonSystem initialized with explicit FolderManager");
        }
    }
    
    /**
     * Connect components explicitly to ensure proper initialization
     */
    private void connectComponents(FolderManager folderManager) {
        // Make sure FolderLayoutService has the proper references
        FolderLayoutService.init(folderManager);
        
        // Update our local references
        this.coreFolderManager = folderManager;
        this.uiStateManager = folderManager.getUIStateManager();
        this.displayManager = folderManager.getDisplayManager();
        this.interactionHandler = folderManager.getInteractionHandler();
        this.storageService = folderManager.getStorageService();
        
        // Update layout positions and bookmark display bounds
        updateLayoutPositions();
        
        ModLogger.error("[INIT-DEBUG] FolderUIController explicitly connected to FolderManager");
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
        
        ModLogger.debug("Initializing FolderButton with component-based architecture");
        
        // Initialize the component classes
        this.coreFolderManager = FolderManager.getInstance();
        
        // Access the new component services directly
        this.uiStateManager = coreFolderManager.getUIStateManager();
        this.displayManager = coreFolderManager.getDisplayManager();
        this.interactionHandler = coreFolderManager.getInteractionHandler();
        this.storageService = coreFolderManager.getStorageService();
        this.layoutService = FolderLayoutService.getInstance();
        
        // Create the specialized components
        this.renderManager = new UIRenderManager(this.uiStateManager, this.displayManager, layoutService);
        this.screenManager = new FolderScreenManager(coreFolderManager, this::createFolder);
        
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
            uiStateManager.getFolderButtons().size(), uiStateManager.areFoldersVisible(), layoutService.getFoldersPerRow());
            
        // Set up JEI runtime initialization
        setupJeiIntegration();
        
        // Restore the state if needed
        if (uiStateManager.shouldRestoreFromStaticState()) {
            displayManager.createBookmarkDisplay(true);
        }
        
        ModLogger.debug("FolderButtonSystem initialization complete");
    }
    
    /**
     * Loads all folders and initializes folder buttons
     */
    private void loadFolders() {
        // Check for null folder manager buttons list
        if (uiStateManager.getFolderButtons() == null) {
            ModLogger.error("Folder manager's button list is null");
            return;
        }
        
        // Use the FolderLayoutService to create and initialize folder buttons
        FolderButton buttonToActivate = initializeFolderButtons();
        
        // Set active folder if needed
        if (buttonToActivate != null) {
            uiStateManager.setActiveFolder(buttonToActivate);
        }
    }
    
    /**
     * Initializes folder buttons directly
     * 
     * @return The button to activate (if any)
     */
    private FolderButton initializeFolderButtons() {
        FolderButton buttonToActivate = null;
        
        // Get all folders from the storage service
        List<Folder> folders = storageService.getAllFolders();
        if (folders == null) {
            storageService.loadData();
            folders = storageService.getAllFolders();
            // If still null, create an empty list as fallback
            if (folders == null) {
                folders = new ArrayList<>();
            }
        }
        
        // Create button list
        List<FolderButton> buttons = new ArrayList<>();
        
        // Create an "Add Folder" button at index 0
        int[] addPos = layoutService.calculateAddButtonPosition();
        FolderButton addButton = new FolderButton(addPos[0], addPos[1], FolderButton.ButtonType.ADD);
        addButton.setClickHandler(interactionHandler::handleAddFolderButtonClick);
        buttons.add(addButton);
        
        // Create and position normal folder buttons
        int buttonIndex = 1;
        for (Folder folder : folders) {
            int[] pos = layoutService.calculateFolderPosition(buttonIndex);
            FolderButton button = new FolderButton(pos[0], pos[1], folder);
            button.setClickHandler(this::onFolderClicked);
            buttons.add(button);
            buttonIndex++;
        }
        
        // Find button to activate based on the last active folder ID
        Integer lastActiveFolderId = uiStateManager.getLastActiveFolderId();
        if (lastActiveFolderId != null) {
            for (FolderButton button : buttons) {
                if (button.getButtonType() == FolderButton.ButtonType.NORMAL && 
                    button.getFolder() != null && 
                    button.getFolder().getId() == lastActiveFolderId) {
                    buttonToActivate = button;
                    break;
                }
            }
        }
        
        // Set the buttons in the state manager
        uiStateManager.setFolderButtons(buttons);
        
        return buttonToActivate;
    }
    
    /**
     * Completely rebuilds the folder UI state from data
     */
    public void rebuildFolders() {
        ModLogger.debug("Performing complete folder UI rebuild");
        
        try {
            // Always reset data paths first to ensure we're reading from the correct location
            storageService.resetDataPaths();
            
            // Clear existing folder buttons
            uiStateManager.getFolderButtons().clear();
            
            // Reload folder data and create new buttons
            loadFolders();
            
            // Update layout positions
            updateLayoutPositions();
            
            // Update bookmark display
            if (uiStateManager.hasActiveFolder()) {
                refreshBookmarkDisplay();
            }
            
        } catch (Exception e) {
            ModLogger.error("Error during folder UI rebuild: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Updates the vertical positions for folder names and bookmark display
     */
    private void updateLayoutPositions() {
        layoutService.updateLayoutPositions(uiStateManager.getFolderButtons().size());
    }
    
    /**
     * Called when a folder is clicked
     */
    private void onFolderClicked(Folder folder) {
        // Use the interaction handler directly
        interactionHandler.handleFolderClick(folder);
    }
    
    @Override
    public void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Reset first time loading flag since we're rendering now
        firstTimeLoaded = false;
        
        // Directly use the UIRenderManager
        renderManager.renderUI(graphics, mouseX, mouseY, partialTick);
        
        // Update the exclusion zone via the layout service
        lastDrawnArea = layoutService.updateExclusionZoneAndUI();
        
        // If we have an active folder, make sure the exclusion zone is big enough
        // to cover both the folders and the ingredient GUI
        if (uiStateManager.hasActiveFolder() && displayManager.getBookmarkDisplay() != null) {
            FolderContentsView display = displayManager.getBookmarkDisplay();
            
            // Get the current exclusion zone
            int currentWidth = lastDrawnArea.getWidth();
            
            // Use the current width as is
            int expandedWidth = currentWidth;
            
            // Ensure height extends well beyond the bookmark display using the constants
            // Use the LayoutConstants to calculate proper height
            int expandedHeight = display.getY() + 
                                LayoutConstants.calculateIngredientAreaHeight(display.getHeight());
            
            // Create a new exclusion zone with the expanded dimensions
            lastDrawnArea = new Rect2i(0, 0, expandedWidth, expandedHeight);
            
            
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
                rebuildFolders();
                firstTimeLoaded = false;
                ticksAfterInit = 0;
            }
        }
        
        // Update animations for folder buttons
        if (uiStateManager.areFoldersVisible()) {
            uiStateManager.tickFolderButtons();
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
            ModLogger.debug("Window resized: {}x{} -> {}x{}, preserving folder state",
                    lastWindowWidth, lastWindowHeight, currentWidth, currentHeight);
                    
            // Update cached window dimensions
            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
            
            // Recalculate layout with preserved folder state
            layoutService.calculateFoldersPerRow();
            loadFolders();
            updateLayoutPositions();
            
            // Force update bookmark display after window resize
            if (uiStateManager.hasActiveFolder()) {
                refreshBookmarkDisplay();
            }
        }
    }
    
    /**
     * Refreshes the bookmark display with options to control behavior
     * 
     * @return true if the refresh was successful
     */
    public boolean refreshBookmarkDisplay() {
        if (uiStateManager.hasActiveFolder()) {
            return displayManager.refreshActiveFolder(true);
        }
        return false;
    }
    
    /**
     * Gets the layout service instance
     * @return The layout service
     */
    public FolderLayoutService getLayoutService() {
        return layoutService;
    }
    
    /**
     * Gets the core folder manager instance
     * @return The core folder manager
     */
    public FolderManager getCoreFolderManager() {
        return coreFolderManager;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return interactionHandler.handleMouseClick(mouseX, mouseY, button, 
                                             renderManager.isDeleteButtonHovered(),
                                             renderManager.getCurrentDeleteButtonX());
    }
    
    @Override
    public boolean handleIngredientDrop(double mouseX, double mouseY, Object ingredient) {
        if (ingredient == null) {
            ModLogger.debug("Cannot handle ingredient drop: ingredient is null");
            return false;
        }
        
        if (!uiStateManager.areFoldersVisible()) {
            ModLogger.debug("Cannot handle ingredient drop: folders not visible");
            return false;
        }
        
        return interactionHandler.handleIngredientDrop(mouseX, mouseY, ingredient, uiStateManager.areFoldersVisible());
    }
    
    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narrationOutput) {
        this.defaultButtonNarrationText(narrationOutput);
    }
    
    @Override
    public List<FolderButton> getFolderButtons() {
        return uiStateManager.getFolderButtons();
    }

    @Override
    public boolean isBookmarkAreaAvailable() {
        return uiStateManager.hasActiveFolder() && displayManager.getBookmarkDisplay() != null;
    }

    @Override
    public Rect2i getBookmarkDisplayArea() {
        FolderContentsView display = displayManager.getBookmarkDisplay();
        if (display != null) {
            return new Rect2i(
                display.getX(),
                display.getY(),
                display.getWidth(),
                display.getHeight()
            );
        }
        return new Rect2i(0, 0, 0, 0);
    }
    
    /**
     * Creates a new folder with the given name
     * @param name The name of the folder
     * @return The newly created folder
     */
    public Folder createFolder(String name) {
        Folder folder = interactionHandler.createFolder(name);
        
        // Handle UI updates after folder creation
        uiStateManager.setFoldersVisible(true);
        
        // Reload folder buttons from data
        loadFolders();
        updateLayoutPositions();
        
        return folder;
    }
    
    /**
     * Initializes the folder button functionality when the Minecraft client is ready
     */
    private void setupJeiIntegration() {
        // Register a callback to be notified when the JEI runtime becomes available
        JEIRuntime jeiRuntime = JEIIntegrationFactory.getJEIRuntime();
        jeiRuntime.registerRuntimeCallback(runtime -> {
            ModLogger.debug("JEI runtime available in FolderUIController");
            if (this != FolderUIController.getInstance()) {
                ModLogger.warn("Runtime callback received by non-singleton FolderUIController instance");
                return;
            }
            
            rebuildFolders();
        });
    }
    
    /**
     * Sets the JEI runtime for this instance
     */
    public void setJeiRuntime(IJeiRuntime runtime) {
        ModLogger.debug("JEI runtime provided to folder UI controller");
        // Update to use the new JEIRuntime class
        JEIIntegrationFactory.getJEIRuntime().setJeiRuntime(runtime);
        rebuildFolders();
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
            if (folderButton.firstTimeLoaded && folderButton.getFolderButtons().size() <= 1) {
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
     * Gets the current exclusion zone
     * @return The current exclusion zone
     */
    public Rect2i getExclusionZone() {
        return renderManager.getExclusionZone();
    }
    
    /**
     * Checks if the delete button is currently hovered
     * @return Whether the delete button is hovered
     */
    public boolean isDeleteButtonHovered() {
        return renderManager.isDeleteButtonHovered();
    }
    
    /**
     * Gets the current X position of the delete button
     * @return The X position of the delete button
     */
    public int getCurrentDeleteButtonX() {
        return renderManager.getCurrentDeleteButtonX();
    }
    
    /**
     * Gets the screen manager instance
     * @return The screen manager
     */
    public FolderScreenManager getScreenManager() {
        return screenManager;
    }
    
    /**
     * Gets the UI state manager
     * @return The UI state manager
     */
    public FolderUIStateManager getUIStateManager() {
        return uiStateManager;
    }
    
    /**
     * Gets the display manager
     * @return The display manager
     */
    public BookmarkDisplayManager getDisplayManager() {
        return displayManager;
    }
    
    /**
     * Gets the interaction handler
     * @return The interaction handler
     */
    public FolderInteractionHandler getInteractionHandler() {
        return interactionHandler;
    }
}
