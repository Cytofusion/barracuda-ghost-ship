package com.barracudaghost;

public class Events
{
    public enum Type
    {
        SailTrim,
        MoteUsed,
        ExtractorHarvested

    }

    public final int tick;
    public final Type type;

    public Events(int tick, Type type)
    {
        this.tick = tick;
        this.type = type;
    }
}