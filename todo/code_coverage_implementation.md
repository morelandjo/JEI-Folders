# Code Coverage Implementation Plan

## 1. Set Up JaCoCo for Code Coverage

Add JaCoCo to `build.gradle`:

```gradle
plugins {
    id 'java'
    id 'jacoco'  // Add the JaCoCo plugin
}

jacoco {
    toolVersion = "0.8.8"  // Use the latest stable version
}

jacocoTestReport {
    reports {
        xml.required = true  // For integration with other tools
        html.required = true // For human-readable reports
    }
}

test {
    finalizedBy jacocoTestReport  // Generate report after tests run
    jacoco {
        includes = ['com.jeifolders.*']  // Focus on your package
        excludes = ['com.jeifolders.integration.*']  // Exclude integration code as needed
    }
}
```

## 2. Write Comprehensive Unit Tests

Create tests for core functionality:

```java
@Test
public void testFolderCreation() {
    FolderManager manager = FolderManager.getInstance();
    Folder folder = manager.createFolder("Test Folder");
    assertNotNull(folder);
    assertEquals("Test Folder", folder.getName());
}
```

Test event system components:

```java
@Test
public void testEventDispatch() {
    FolderEventDispatcher dispatcher = new FolderEventDispatcher();
    final boolean[] eventReceived = {false};
    
    dispatcher.addEventListener(FolderEventType.FOLDER_CREATED, event -> {
        eventReceived[0] = true;
        assertEquals("Test Folder", event.getFolder().getName());
    });
    
    dispatcher.fire(FolderEventType.FOLDER_CREATED)
        .withFolder(new Folder(1, "Test Folder"))
        .build();
        
    assertTrue(eventReceived[0]);
}
```

## 3. Analyze the JaCoCo Reports

After running tests with `./gradlew test`, JaCoCo will generate reports in `build/reports/jacoco/test/html/index.html`. Look for:

- **Methods with 0% coverage**: Likely unused methods that could be candidates for removal
- **Classes with low coverage**: Potential candidates for refactoring or removal
- **Complex methods with partial coverage**: Might indicate overly complex code that's difficult to test

## 4. Instrument Runtime Usage

For UI-heavy code that's hard to test traditionally:

```java
public class UsageTracker {
    private static final Map<String, Integer> methodUsage = new ConcurrentHashMap<>();
    
    public static void trackCall(String methodName) {
        methodUsage.merge(methodName, 1, Integer::sum);
    }
    
    public static void dumpStats() {
        methodUsage.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> ModLogger.debug("Method usage: {} - {} calls", e.getKey(), e.getValue()));
    }
}
```

Add tracking to key methods:

```java
public void handleBookmarkDisplayClick(double mouseX, double mouseY, int button) {
    UsageTracker.trackCall("handleBookmarkDisplayClick");
    // Normal method body
}
```

## 5. Static Analysis Tools

Complement coverage with static analysis:

1. **JDepend** for package dependencies
2. **SonarQube** for code quality analysis including:
   - Detecting unused private methods
   - Highlighting unreachable code
   - Identifying duplicated code

## 6. Combining the Results for Decision Making

Create a spreadsheet with columns:
- Method/Class name
- Code coverage percentage
- Runtime instrumentation call count
- Static analyzer warnings
- Notes on backward compatibility concerns

## 7. Practical Implementation Steps

1. Add the JaCoCo configuration to build.gradle
2. Create a baseline test suite targeting 50% coverage
3. Run the analysis and identify clear candidates for removal (0% coverage)
4. Add runtime instrumentation for borderline cases
5. Test the application thoroughly with instrumentation
6. Document findings and create a refactoring plan