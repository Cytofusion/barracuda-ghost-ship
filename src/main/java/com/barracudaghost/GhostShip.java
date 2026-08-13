package com.barracudaghost;

import java.util.ArrayList;
import java.util.List;

public class GhostShip
{
    private final List<FrameDif> keyframes;
    private final int[] cumulativeTicks;
    private final int totalTicks;

    public GhostShip(List<FrameDif> keyframes)
    {
        this.keyframes = keyframes;
        this.cumulativeTicks = new int[keyframes.size()];

        int running = 0;
        for (int i = 0; i < keyframes.size(); i++)
        {
            running += keyframes.get(i).Gap;
            cumulativeTicks[i] = running;
        }

        this.totalTicks = running;
    }

    public int TotalTicks()
    {
        return totalTicks;
    }

    public boolean isFinished(int elapsedTicks)
    {
        return elapsedTicks >= totalTicks;
    }

    public FrameDif getFrameAtTick(int elapsedTicks)
    {
        if (elapsedTicks <= 0 || keyframes.size() == 1)
        {
            FrameDif first = keyframes.get(0);
            return new FrameDif(first.X, first.Y, first.O, 0);
        }

        if (elapsedTicks >= totalTicks)
        {
            FrameDif last = keyframes.get(keyframes.size() - 1);
            return new FrameDif(last.X, last.Y, last.O, 0);
        }

        int index = 0;
        while (index < cumulativeTicks.length - 1 && cumulativeTicks[index + 1] < elapsedTicks)
        {
            index++;
        }

        FrameDif from = keyframes.get(index);
        FrameDif to = keyframes.get(index + 1);
        int fromTick = cumulativeTicks[index];
        int toTick = cumulativeTicks[index + 1];

        if (toTick == fromTick)
        {
            return new FrameDif(to.X, to.Y, to.O, 0);
        }

        double t = (double) (elapsedTicks - fromTick) / (toTick - fromTick);

        int x = (int) Math.round(from.X + t * (to.X - from.X));
        int y = (int) Math.round(from.Y + t * (to.Y - from.Y));
        int orientation = (int) Math.round(from.O + t * angleDiff(from.O, to.O));
        orientation = ((orientation % 2048) + 2048) % 2048;

        return new FrameDif(x, y, orientation, 0);
    }

    public List<TrailPoint> getTrailPointsUpToTick(int elapsedTicks)
    {
        List<TrailPoint> result = new ArrayList<>();
        for (int i = 0; i < keyframes.size(); i++)
        {
            if (cumulativeTicks[i] <= elapsedTicks)
            {
                int age = elapsedTicks - cumulativeTicks[i];
                result.add(new TrailPoint(keyframes.get(i), age));
            }
            else
            {
                break;
            }
        }
        return result;
    }

    private static double angleDiff(int a, int b)
    {
        int diff = (b - a) % 2048;
        if (diff > 1024)
        {
            diff -= 2048;
        }
        else if (diff < -1024)
        {
            diff += 2048;
        }
        return diff;
    }

}