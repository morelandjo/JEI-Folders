package com.jeifolders.integration;

/**
 * Platform-independent rectangle class.
 * This abstraction allows us to avoid direct dependencies on Minecraft's Rect2i class.
 */
public class Rectangle2i {
    /**
     * A constant representing an empty rectangle.
     */
    public static final Rectangle2i EMPTY = new Rectangle2i(0, 0, 0, 0);
    
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    
    public Rectangle2i(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
    
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && 
               py >= y && py < y + height;
    }
    
    /**
     * Checks if this rectangle contains the specified point with double coordinates.
     * 
     * @param px the x coordinate of the point
     * @param py the y coordinate of the point
     * @return true if the rectangle contains the point, false otherwise
     */
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && 
               py >= y && py < y + height;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rectangle2i that = (Rectangle2i) o;
        return x == that.x && y == that.y && width == that.width && height == that.height;
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
        return "Rectangle2i{" +
                "x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}