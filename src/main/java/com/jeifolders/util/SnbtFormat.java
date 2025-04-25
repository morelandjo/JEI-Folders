package com.jeifolders.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import net.minecraft.nbt.CompoundTag;

/**
 * Utility class for reading and writing SNBT (Stringified NBT) files.
 */
public class SnbtFormat {
    
    /**
     * Writes a CompoundTag to an SNBT file.
     * 
     * @param file The file to write to
     * @param tag The tag to write
     * @throws IOException If an I/O error occurs
     */
    public static void writeToFile(File file, CompoundTag tag) throws IOException {
        // Make sure parent directory exists
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // Convert the CompoundTag to an SNBT string
        String snbtString = convertToString(tag);
        
        // Write the string to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(snbtString);
        }
        
        ModLogger.debug("Saved SNBT data to {}", file.getAbsolutePath());
    }
    
    /**
     * Converts a CompoundTag to an SNBT string.
     */
    private static String convertToString(CompoundTag tag) {
        // Use toString() for a basic SNBT representation
        // This gives us something like {folders:[{id:0,name:"test",bookmarks:[]}],nextId:1}
        return tag.toString();
    }
    
    /**
     * Format a CompoundTag to a nicely formatted SNBT string with indentation
     * 
     * @param tag The tag to format
     * @return Formatted SNBT string
     */
    public static String format(CompoundTag tag) {
        // Simple implementation that adds indentation to make the SNBT more readable
        return tag.toString().replace(",", ",\n  ")
            .replace("{", "{\n  ")
            .replace("}", "\n}");
    }
}
