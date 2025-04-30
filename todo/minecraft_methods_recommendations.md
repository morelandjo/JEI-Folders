# Minecraft Methods to Consider Using

This document outlines areas where we could leverage existing Minecraft methods instead of our custom implementations.

## Rectangle/Bounds Handling

The codebase contains a custom `Rectangle2i` class in the integration package that appears to duplicate functionality already present in Minecraft's `Rect2i` class:

```java
// Our custom class
public class Rectangle2i {
    // Custom implementation
}
```

Instead of maintaining this abstraction layer, we could directly use Minecraft's `net.minecraft.client.renderer.Rect2i` in more places, which already provides methods for position, dimensions, and intersection tests.

## Mouse Hit Detection

In `MouseHitUtil` class, we're implementing custom hit detection logic, but could use Minecraft's built-in methods:

```java
// Replace custom hit detection with
boolean isHovered = guiComponent.isMouseOver(mouseX, mouseY);
```

This is especially applicable in our FolderGhostIngredientHandler where we're creating manual Rect2i objects.

## Tooltip Rendering

In `TooltipRenderer`, we have custom methods that wrap Minecraft's tooltip rendering:

```java
public static void renderTooltip(GuiGraphics graphics, List<Component> tooltips, int mouseX, int mouseY) {
    // Custom implementation
}
```

We could directly use Minecraft's tooltip rendering methods in more places:

```java
graphics.renderTooltip(font, tooltipText, mouseX, mouseY);
graphics.renderComponentTooltip(font, tooltipComponents, mouseX, mouseY);
```

## World and Server Name Resolution

In `ConfigPathResolver`, there's complex logic for determining world names:

```java
private String tryWorldNameStrategies(Minecraft minecraft) {
    // Multiple complex strategies
}
```

Consider using Minecraft's built-in methods to get world information more directly:

```java
// For local worlds
minecraft.getLevelSource().getLevelInfos().getName();

// For server worlds
minecraft.getCurrentServer().name;
```

## Ingredient Dragging Detection

Our `IngredientDragHandler` and `IngredientDragManager` contain custom logic for tracking mouse states and drag operations. Minecraft provides event systems and input detection that could be leveraged more directly:

```java
// Use Minecraft's input handlers directly
InputConstants.isKeyDown(window, GLFW_MOUSE_BUTTON_LEFT);
```

## Exclusion Zone Management

The `ExclusionManager` and `ExclusionHandler` classes have custom logic for managing screen areas, but could potentially leverage more of Minecraft's existing layout management:

```java
// Consider if any of these can use Minecraft's existing screen layout helpers
public Rect2i updateExclusionZone(int folderCount, boolean foldersVisible, 
                                 boolean hasActiveFolder, int bookmarkDisplayHeight) {
    // Custom calculation
}
```

By using more of Minecraft's built-in methods, we could reduce code maintenance, improve compatibility with future Minecraft versions, and potentially fix some edge cases that are already handled in the Minecraft codebase.