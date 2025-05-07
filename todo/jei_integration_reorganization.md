# JEI Integration Reorganization Plan


## Suggested File Structure

```
src/main/java/com/jeifolders/integration/
├── api/                                # Public API interfaces
│   ├── IngredientService.java          # Core ingredient service interface
│   └── JEIFoldersAPI.java              # Public API facade
├── core/                               # Core implementation classes
│   ├── JEIPlugin.java                  # Main JEI plugin (entry point)
│   ├── JEIRuntime.java                 # Unified runtime access
│   ├── IngredientManager.java          # Centralized ingredient management
│   └── BookmarkManager.java            # Bookmark management
├── handlers/                           # Event and interaction handlers
│   ├── DragDropHandler.java            # Drag and drop functionality
│   ├── GhostIngredientHandler.java     # JEI ghost ingredient handling
│   └── ExclusionHandler.java           # JEI exclusion handling
├── model/                              # Data model classes
│   ├── Ingredient.java                 # Unified ingredient model
│   ├── BookmarkIngredient.java         # Bookmark-specific ingredient
│   └── IngredientType.java             # Ingredient type enum
└── util/                               # Utility classes
    ├── IngredientHelper.java           # Helper for ingredient conversion
    └── RenderHelper.java               # Rendering utilities
```



## Specific Recommendations

### 1. Consolidate into Core Classes

1. **Replace `JEIIntegration` with `JEIPlugin`**
   - Keep your existing `src/main/java/com/jeifolders/integration/JEIPlugin.java` and retire `src/main/java/com/jeifolders/integration/JEIIntegration.java`
   - Move handler implementations to separate files in `handlers/`

2. **Consolidate Ingredient Services**
   - Merge `IngredientServiceImpl`, `JEIIngredientService`, and `TypedIngredientHelper` into unified `IngredientManager`
   - Keep your existing `IngredientManager` as the main implementation

3. **Simplify Runtime Management**
   - Use your existing `JEIRuntime` class as the central access point for JEI runtime

### 2. Standardize Naming Convention

1. **Use consistent capitalization**
   - Use `JEI` prefix consistently (not `Jei`)
   - Example: Change `JeiBookmarkAdapter` to `JEIBookmarkAdapter`

2. **Use consistent naming patterns**
   - Interface: `IIngredientService` 
   - Implementation: `IngredientManager` or `DefaultIngredientService`

### 3. Improve Ingredient System

1. **Use your unified Ingredient class**
   - Continue using the unified `Ingredient` class from `src/main/java/com/jeifolders/integration/ingredient/Ingredient.java` 
   - Retire `TypedIngredient` and `BookmarkIngredient` classes after migration

2. **Simplify ingredient creation**
   - Create factory methods in `IngredientManager` for common ingredient types
   - Implement serialization/deserialization in one place

### 4. Reduce Redundancies

1. **Remove duplicate implementations**
   - `IngredientServiceImpl` and `JEIIngredientService` have significant overlap
   - Keep the most comprehensive implementation and extend as needed

2. **Consolidate helper methods**
   - Move conversion methods from `TypedIngredientHelper` to `IngredientManager`

### 5. Create a Clear API

1. **Define a public API facade**
   - Create `JEIFoldersAPI` as the main entry point for other modules
   - Hide implementation details behind interfaces

2. **Separate interfaces from implementations**
   - Define clean interfaces in `api/` package
   - Keep implementations in separate packages

## Implementation Strategy

I suggest implementing these changes in phases:

1. **Preparation Phase**: Create the new folder structure without changing code
2. **Core Components**: Implement the core classes first (JEIPlugin, JEIRuntime)
3. **Ingredient System**: Consolidate ingredient-related functionality
4. **Handlers**: Move handler implementations to dedicated classes
5. **API Refinement**: Clean up interfaces and create the public API
6. **Final Cleanup**: Remove redundant files and update references