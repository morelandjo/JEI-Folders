package com.jeifolders.data;

import com.jeifolders.util.ModLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Handles low-level file operations for the folder storage system.
 */
public class FileStorage {
    // Singleton instance
    private static FileStorage instance;
    
    /**
     * Private constructor for singleton pattern
     */
    private FileStorage() {
        // No initialization needed
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized FileStorage getInstance() {
        if (instance == null) {
            instance = new FileStorage();
        }
        return instance;
    }
    
    /**
     * Read data from a file
     * 
     * @param path Path to the file to read
     * @return Optional containing the file contents as a string, or empty if file doesn't exist/is empty
     */
    public Optional<String> readDataFile(Path path) {
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            ModLogger.debug("File does not exist or is not a regular file: {}", path);
            return Optional.empty();
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            // Read the full file content
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            
            String content = sb.toString().trim();
            if (content.isEmpty()) {
                ModLogger.debug("File is empty: {}", path);
                return Optional.empty();
            }
            
            ModLogger.debug("Successfully read {} bytes from file: {}", content.length(), path);
            return Optional.of(content);
        } catch (IOException e) {
            ModLogger.error("Error reading file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Write data to a file
     * 
     * @param path Path to the file to write
     * @param data Data to write to the file
     * @return true if successful, false otherwise
     */
    public boolean writeDataFile(Path path, String data) {
        try {
            // Ensure parent directories exist
            ensureParentDirectoriesExist(path);
            
            // Write the data to the file
            try (FileWriter writer = new FileWriter(path.toFile())) {
                writer.write(data);
            }
            
            ModLogger.debug("Successfully wrote {} bytes to file: {}", data.length(), path);
            return true;
        } catch (IOException e) {
            ModLogger.error("Error writing to file {}: {}", path, e.getMessage());
            return false;
        }
    }
    
    /**
     * Ensure all parent directories for a file path exist
     * 
     * @param path The path to check/create parent directories for
     * @throws IOException If directory creation fails
     */
    public void ensureParentDirectoriesExist(Path path) throws IOException {
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            ModLogger.debug("Created parent directories: {}", parentDir);
        }
    }
    
    /**
     * Ensure a directory exists
     * 
     * @param path The directory path to ensure exists
     * @return true if successful, false otherwise
     */
    public boolean ensureDirectoryExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                ModLogger.debug("Created directory: {}", path);
            }
            return true;
        } catch (IOException e) {
            ModLogger.error("Failed to create directory {}: {}", path, e.getMessage());
            return false;
        }
    }
}