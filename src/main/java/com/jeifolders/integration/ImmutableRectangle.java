package com.jeifolders.integration;

/**
 * Immutable rectangle implementation to avoid direct dependency on JEI's ImmutableRect2i class.
 */
public class ImmutableRectangle {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    
    /**
     * Creates a new immutable rectangle with the specified dimensions
     */
    public ImmutableRectangle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    /**
     * Get the x coordinate of this rectangle
     */
    public int getX() {
        return x;
    }
    
    /**
     * Get the y coordinate of this rectangle
     */
    public int getY() {
        return y;
    }
    
    /**
     * Get the width of this rectangle
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * Get the height of this rectangle
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * Check if this rectangle contains the given point
     */
    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < x + width && 
               pointY >= y && pointY < y + height;
    }
    
    /**
     * Check if this rectangle is empty (has no area)
     */
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutableRectangle)) return false;
        
        ImmutableRectangle other = (ImmutableRectangle) o;
        return x == other.x && 
               y == other.y && 
               width == other.width && 
               height == other.height;
    }
    
    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }
    
    @Override
    public String toString() {
        return "Rectangle[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]";
    }
}