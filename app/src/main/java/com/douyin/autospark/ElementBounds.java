package com.douyin.autospark;

public class ElementBounds {
  private double x;
  private double y;
  private double width;
  private double height;

  public boolean isUsable() {
    return Double.isFinite(x) && Double.isFinite(y) && width > 0 && height > 0;
  }

  public double getX() { return x; }
  public void setX(double x) { this.x = x; }
  public double getY() { return y; }
  public void setY(double y) { this.y = y; }
  public double getWidth() { return width; }
  public void setWidth(double width) { this.width = width; }
  public double getHeight() { return height; }
  public void setHeight(double height) { this.height = height; }
}
