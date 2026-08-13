package com.barracudaghost;

public class FrameDif
{
    public final int X;
    public final int Y;
    public final int O;
    public final int Gap;

    public FrameDif(int worldX, int worldY, int orientation, int tickGap)
    {
        this.X = worldX;
        this.Y = worldY;
        this.O = orientation;
        this.Gap = tickGap;
    }
}