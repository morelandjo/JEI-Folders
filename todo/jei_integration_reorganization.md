# JEI Integration Reorganization Plan

## Current Issues

The JEI integration package currently has:

- **22+ Java files** with overlapping responsibilities
- **Multiple service layers** with complex interactions
- **Inconsistent naming conventions** (`JEI` vs `Jei` prefixes)
- **Redundant wrappers** for JEI types
- **Scattered logic** for core operations like drag-and-drop

## Implementation Strategy

### Phase 1: Core Runtime Consolidation

1. Create `JEIRuntime` class:
   - Merge functionality from `JEIService`, `JEIServiceImpl`, and `JEIRuntimeWrapper`
   - Implement as singleton with direct access to JEI runtime
   - Define clear methods for common runtime operations

2. Rename `JEIIntegration` to `JEIPlugin`:
   - Keep IModPlugin implementation
   - Move ghost ingredient handler to a separate class
   - Simplify runtime initialization

3. Update `JEIIntegrationFactory`:
   - Simplify to use new unified classes
   - Maintain factory pattern for backward compatibility during transition

### Phase 2: Ingredient System Unification

1. Create unified `Ingredient` class:
   - Replace `TypedIngredient` and `BookmarkIngredient`
   - Add type-safe conversion methods
   - Support serialization/deserialization

2. Create `IngredientManager` class:
   - Consolidate `IngredientService`, `IngredientServiceImpl`, and `JEIIngredientService`
   - Simplify the interface to essential methods
   - Improve caching mechanism

3. Migrate existing code to use the new classes:
   - Update references in UI components
   - Update serialization code in storage classes

### Phase 3: UI Integration Simplification

1. Create `DragAndDropHandler` class:
   - Consolidate drag handling from `IngredientDragHandler`, `FolderGhostIngredientHandler`
   - Simplify event handling
   - Use Minecraft's native input handling

2. Create `JEIDisplayAdapter` class:
   - Merge functionality from `JeiBookmarkAdapter` and any rendering classes
   - Simplify bookmark grid interaction

3. Improve `UIExclusionManager`:
   - Consolidate exclusion handling from multiple classes
   - Use Minecraft's native rectangle utilities

### Phase 4: Bookmark System Enhancement

1. Create improved `BookmarkManager`:
   - Merge functionality from `BookmarkService`, `BookmarkServiceImpl`, and `JEIBookmarkManager`
   - Simplify bookmark operations
   - Add robust event system

2. Update bookmark listeners and hooks:
   - Standardize event handling
   - Improve documentation

### Phase 5: Cleanup and Documentation

1. Remove redundant classes
2. Standardize naming conventions
3. Add comprehensive documentation
4. Create usage examples

## Dependencies Between Components

- `JEIRuntime` must be implemented first as other components depend on it
- `Ingredient` system should be next as it's used by most other components
- UI components can be implemented in parallel after core components
- Bookmark system should be last as it depends on all other components

## Testing Strategy

For each phase:
1. Implement changes
2. Verify basic functionality
3. Test edge cases
4. Ensure backward compatibility with existing mod features