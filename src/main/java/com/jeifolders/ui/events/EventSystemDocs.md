# JEI Folders Event System Documentation

## Overview

The event system in JEI Folders has been refactored to improve maintainability, thread safety, and performance. The system now uses a builder pattern for event creation and a separate utility for event debouncing, providing a more consistent and intuitive API.

## Core Components

### 1. FolderEventType (Enum)
Defines the types of events supported by the system:
- UI events (clicks, activations)
- Data modification events (folder contents changed, bookmark added/removed)
- Display events (refresh completed)

### 2. FolderEvent
A container for event data with type-safe accessors for common properties and a flexible key-value store for additional data.

### 3. FolderEventBuilder
Implements the builder pattern for creating events with a fluent API:

```java
eventDispatcher.fire(FolderEventType.BOOKMARK_ADDED)
    .withFolder(folder)
    .withIngredient(ingredient)
    .withBookmarkKey(key)
    .build();
```

### 4. FolderEventBus
Manages event listeners and dispatches events. Features:
- Thread-safe listener containers
- Exception handling for individual listeners
- Detailed logging options

### 5. EventDebouncer
A standalone utility for preventing event floods:
- Configurable debounce timeouts
- Key-based debouncing (e.g., by folder ID)
- Helper methods for conditional execution

### 6. FolderEventDispatcher
The main facade for the event system, providing:
- Registration and removal of event listeners
- Helper methods for common event scenarios
- Integration with the debouncing system

## Best Practices

### 1. Event Listener Management

Always store listeners as fields and unregister them when no longer needed:

```java
private final Consumer<FolderEvent> myListener = event -> { /* ... */ };

// In constructor or init method
eventDispatcher.addEventListener(FolderEventType.FOLDER_CLICKED, myListener);

// In cleanup or dispose method
eventDispatcher.removeEventListener(FolderEventType.FOLDER_CLICKED, myListener);
```

### 2. Using the Builder Pattern

Prefer the builder pattern over direct event creation:

```java
// Preferred
eventDispatcher.fire(FolderEventType.FOLDER_CONTENTS_CHANGED)
    .withFolderId(folderId)
    .withData("source", getClass().getSimpleName())
    .build();

// Avoid
FolderEvent event = new FolderEvent(this, FolderEventType.FOLDER_CONTENTS_CHANGED);
event.with("folderId", folderId);
eventDispatcher.getEventBus().post(event);
```

### 3. Event Debouncing

Use the debouncer to prevent event floods:

```java
// In field declarations
private final EventDebouncer debouncer = new EventDebouncer(250);

// When handling frequent events
if (debouncer.shouldProcess(folderId)) {
    // Process the event
}

// Or use the execute convenience method
debouncer.execute(folderId, () -> {
    // This code only runs if not debounced
});
```

### 4. Adding Custom Data

Use the `withData()` method for custom attributes:

```java
.withData("timestamp", System.currentTimeMillis())
.withData("mousePosition", new Point(mouseX, mouseY))
```

### 5. Error Handling

The event system catches exceptions in listeners to prevent cascading failures, but listeners should still handle their own errors when possible.

## Migration Guide

1. Replace direct `FolderEvent` instantiations with the builder pattern
2. Extract debouncing logic to use the `EventDebouncer` utility
3. Store event listeners as fields for proper cleanup
4. Use the new method for firing events:
   ```java
   // Old style
   eventDispatcher.fireFolderClickedEvent(folder);
   
   // New style
   eventDispatcher.fire(FolderEventType.FOLDER_CLICKED)
       .withFolder(folder)
       .build();
   ```

## Thread Safety

The event system uses thread-safe collections (`CopyOnWriteArrayList`) for listener management, making it safe to register or unregister listeners from any thread. However, events should typically be fired from the main game thread to avoid synchronization issues with game state.