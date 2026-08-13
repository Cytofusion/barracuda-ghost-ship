package com.barracudaghost;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;

public class Overlay extends net.runelite.client.ui.overlay.Overlay
{

    private static final double[][] BoatBody = {
            {0, -402},
            {44, -354},
            {90, -259},
            {119, -165},
            {137, -70},
            {142, 22},
            {137, 106},
            {123, 192},
            {95, 320},
            {8, 320},
            {8, 395},
            {-8, 395},
            {-8, 320},
            {-95, 320},
            {-123, 192},
            {-137, 106},
            {-142, 22},
            {-137, -70},
            {-119, -165},
            {-90, -259},
            {-44, -354},
    };

    private static final int UsernameHeight = 220;
    private static final int EventTextHeight = 110;

    private static final int GhostFill = 130;
    private static final int GhostOutline = 200;
    private static final double OutlineShader = 0.5;

    private static final int MarkerRadius = 8;

    private final Client client;
    private final BarracudaGhostPlugin plugin;
    private final Config config;

    @Inject
    private Overlay(Client client, BarracudaGhostPlugin plugin, Config config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        FrameDif frameDif = plugin.getInterpolatedGhostFrame();
        if (frameDif == null)
        {
            return null;
        }

        WorldView topLevel = client.getTopLevelWorldView();
        if (topLevel == null)
        {
            return null;
        }

        Trail(graphics, topLevel, frameDif);
        TrailEvents(graphics, topLevel);

        double angle = frameDif.O * (2.0 * Math.PI / 2048.0);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        int localBaseX = frameDif.X - topLevel.getBaseX() * 128;
        int localBaseY = frameDif.Y - topLevel.getBaseY() * 128;

        int[] xPoints = new int[BoatBody.length];
        int[] yPoints = new int[BoatBody.length];
        int pointCount = 0;

        for (double[] corner : BoatBody)
        {
            double rotatedX = corner[0] * cos + corner[1] * sin;
            double rotatedY = -corner[0] * sin + corner[1] * cos;

            LocalPoint cornerLocal = new LocalPoint(
                    localBaseX + (int) Math.round(rotatedX),
                    localBaseY + (int) Math.round(rotatedY)
            );

            Point canvasPoint = Perspective.localToCanvas(client, cornerLocal, client.getPlane(), 0);
            if (canvasPoint == null)
            {
                return null;
            }

            xPoints[pointCount] = canvasPoint.getX();
            yPoints[pointCount] = canvasPoint.getY();
            pointCount++;
        }

        Polygon boatPolygon = new Polygon(xPoints, yPoints, pointCount);

        Color baseColor = config.ghostColor();
        Color fillColor = withAlpha(baseColor, GhostFill);
        Color outlineColor = withAlpha(darken(baseColor, OutlineShader), GhostOutline);

        float bestFlashIntensity = 0f;
        Color bestEventColor = null;

        for (Events.Type type : Events.Type.values())
        {
            if (!isEventFlashEnabled(type))
            {
                continue;
            }

            float intensity = plugin.getEventFlashIntensity(type);
            if (intensity > bestFlashIntensity)
            {
                bestFlashIntensity = intensity;
                bestEventColor = colorForType(type);
            }
        }

        if (bestEventColor != null && bestFlashIntensity > 0f)
        {
            fillColor = blend(fillColor, bestEventColor, bestFlashIntensity);
            outlineColor = blend(outlineColor, bestEventColor, bestFlashIntensity);
        }

        graphics.setColor(fillColor);
        graphics.fillPolygon(boatPolygon);

        graphics.setColor(outlineColor);
        graphics.drawPolygon(boatPolygon);

        Username(graphics, localBaseX, localBaseY);
        EventText(graphics, localBaseX, localBaseY);

        return null;
    }

    private void Trail(Graphics2D graphics, WorldView topLevel, FrameDif currentFrame)
    {
        if (!plugin.isFollowModeEnabled() || !config.showGhostTrail())
        {
            return;
        }

        List<TrailPoint> trailPoints = plugin.getGhostTrailPoints();
        if (trailPoints.size() < 2)
        {
            return;
        }

        int fadeDelayTicks = config.trailFadeDelayTicks();
        double fadeIntensity = config.trailFadeIntensity() / 100.0;
        Color trailColor = config.ghostTrailColor();

        Point previousPoint = projectFrame(topLevel, trailPoints.get(0).frame);

        for (int i = 1; i < trailPoints.size(); i++)
        {
            TrailPoint segmentStart = trailPoints.get(i - 1);
            Point currentPoint = projectFrame(topLevel, trailPoints.get(i).frame);

            if (previousPoint != null && currentPoint != null)
            {
                graphics.setColor(colorForAge(trailColor, segmentStart.ageTicks, fadeDelayTicks, fadeIntensity));
                graphics.drawLine(previousPoint.getX(), previousPoint.getY(), currentPoint.getX(), currentPoint.getY());
            }

            previousPoint = currentPoint;
        }

        TrailPoint lastRecordedPoint = trailPoints.get(trailPoints.size() - 1);
        Point liveCanvasPoint = projectFrame(topLevel, currentFrame);
        if (previousPoint != null && liveCanvasPoint != null)
        {
            graphics.setColor(colorForAge(trailColor, lastRecordedPoint.ageTicks, fadeDelayTicks, fadeIntensity));
            graphics.drawLine(previousPoint.getX(), previousPoint.getY(), liveCanvasPoint.getX(), liveCanvasPoint.getY());
        }
    }

    private static Color colorForAge(Color baseColor, int ageTicks, int fadeDelayTicks, double fadeIntensity)
    {
        if (ageTicks <= fadeDelayTicks)
        {
            return baseColor;
        }

        int fadedAlpha = (int) Math.round(baseColor.getAlpha() * (1.0 - fadeIntensity));
        fadedAlpha = Math.max(0, Math.min(255, fadedAlpha));
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), fadedAlpha);
    }

    private void TrailEvents(Graphics2D graphics, WorldView topLevel)
    {
        if (!plugin.isFollowModeEnabled() || !config.showTrailEvents())
        {
            return;
        }

        int fadeDelayTicks = config.trailFadeDelayTicks();
        double fadeIntensity = config.trailFadeIntensity() / 100.0;
        int currentPlaybackTick = plugin.getPlaybackTick();

        List<Events> trailEvents = plugin.getGhostTrailEvents();
        for (Events event : trailEvents)
        {
            FrameDif frame = plugin.getGhostFrameAtTick(event.tick);
            if (frame == null)
            {
                continue;
            }

            int localX = frame.X - topLevel.getBaseX() * 128;
            int localY = frame.Y - topLevel.getBaseY() * 128;
            LocalPoint local = new LocalPoint(localX, localY);
            Point point = Perspective.localToCanvas(client, local, client.getPlane(), 0);
            if (point == null)
            {
                continue;
            }

            int age = currentPlaybackTick - event.tick;
            Color markerColor = colorForAge(colorForTrailEvent(event.type), age, fadeDelayTicks, fadeIntensity);
            drawEventMarker(graphics, point, event.type, markerColor);
        }
    }

    private void drawEventMarker(Graphics2D graphics, Point point, Events.Type type, Color color)
    {
        int x = point.getX();
        int y = point.getY();

        graphics.setColor(color);

        switch (type)
        {
            case SailTrim:
                int[] xs = { x, x - MarkerRadius, x + MarkerRadius };
                int[] ys = { y - MarkerRadius, y + MarkerRadius, y + MarkerRadius };
                graphics.fillPolygon(xs, ys, 3);
                break;
            case MoteUsed:
                graphics.fillOval(x - MarkerRadius, y - MarkerRadius, MarkerRadius * 2, MarkerRadius * 2);
                break;
            case ExtractorHarvested:
                graphics.fillRect(x - MarkerRadius, y - MarkerRadius, MarkerRadius * 2, MarkerRadius * 2);
                break;
        }
    }

    private Color colorForTrailEvent(Events.Type type)
    {
        switch (type)
        {
            case SailTrim:
                return config.trailSailTrimColor();
            case MoteUsed:
                return config.trailMoteUsedColor();
            case ExtractorHarvested:
                return config.trailExtractorHarvestedColor();
            default:
                return config.ghostTrailColor();
        }
    }

    private Point projectFrame(WorldView topLevel, FrameDif frame)
    {
        int localX = frame.X - topLevel.getBaseX() * 128;
        int localY = frame.Y - topLevel.getBaseY() * 128;
        LocalPoint local = new LocalPoint(localX, localY);
        return Perspective.localToCanvas(client, local, client.getPlane(), 0);
    }

    private void Username(Graphics2D graphics, int localBaseX, int localBaseY)
    {
        String username = plugin.getLoadedGhostUsername();
        if (username == null || username.isEmpty())
        {
            return;
        }

        LocalPoint tagLocal = new LocalPoint(localBaseX, localBaseY);
        Point tagCanvasPoint = Perspective.localToCanvas(client, tagLocal, client.getPlane(), UsernameHeight);
        if (tagCanvasPoint == null)
        {
            return;
        }

        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(username);
        int x = tagCanvasPoint.getX() - textWidth / 2;
        int y = tagCanvasPoint.getY();

        graphics.setColor(Color.BLACK);
        graphics.drawString(username, x + 1, y + 1);
        graphics.setColor(Color.WHITE);
        graphics.drawString(username, x, y);
    }

    private void EventText(Graphics2D graphics, int localBaseX, int localBaseY)
    {
        if (!config.showEventTextLabels())
        {
            return;
        }

        float bestIntensity = 0f;
        Events.Type bestType = null;

        for (Events.Type type : Events.Type.values())
        {
            if (!isEventFlashEnabled(type))
            {
                continue;
            }

            float intensity = plugin.getEventFlashIntensity(type);
            if (intensity > bestIntensity)
            {
                bestIntensity = intensity;
                bestType = type;
            }
        }

        if (bestType == null || bestIntensity <= 0f)
        {
            return;
        }

        String label = labelForEventType(bestType);
        if (label == null)
        {
            return;
        }

        LocalPoint textLocal = new LocalPoint(localBaseX, localBaseY);
        Point textCanvasPoint = Perspective.localToCanvas(client, textLocal, client.getPlane(), EventTextHeight);
        if (textCanvasPoint == null)
        {
            return;
        }

        Font originalFont = graphics.getFont();
        graphics.setFont(originalFont.deriveFont(originalFont.getSize2D() + 4f));

        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(label);
        int x = textCanvasPoint.getX() - textWidth / 2;
        int y = textCanvasPoint.getY();

        graphics.setColor(Color.BLACK);
        graphics.drawString(label, x + 1, y + 1);
        graphics.setColor(Color.WHITE);
        graphics.drawString(label, x, y);

        graphics.setFont(originalFont);
    }

    private static String labelForEventType(Events.Type type)
    {
        switch (type)
        {
            case SailTrim:
                return "Sails Trimmed";
            case MoteUsed:
                return "Mote Released";
            case ExtractorHarvested:
                return "Extractor Harvested";
            default:
                return null;
        }
    }

    private boolean isEventFlashEnabled(Events.Type type)
    {
        switch (type)
        {
            case SailTrim:
                return config.sailTrimFlashEnabled();
            case MoteUsed:
                return config.moteSpentFlashEnabled();
            case ExtractorHarvested:
                return config.moteHarvestedFlashEnabled();
            default:
                return true;
        }
    }

    private Color colorForType(Events.Type type)
    {
        switch (type)
        {
            case SailTrim:
                return config.sailTrimFlashColor();
            case MoteUsed:
                return config.moteSpentFlashColor();
            case ExtractorHarvested:
                return config.moteHarvestedFlashColor();
            default:
                return config.ghostColor();
        }
    }

    private static Color withAlpha(Color base, int alpha)
    {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    private static Color darken(Color base, double factor)
    {
        return new Color(
                (int) (base.getRed() * factor),
                (int) (base.getGreen() * factor),
                (int) (base.getBlue() * factor)
        );
    }

    private static Color blend(Color base, Color target, float t)
    {
        int r = (int) (base.getRed() + (target.getRed() - base.getRed()) * t);
        int g = (int) (base.getGreen() + (target.getGreen() - base.getGreen()) * t);
        int b = (int) (base.getBlue() + (target.getBlue() - base.getBlue()) * t);
        int a = (int) (base.getAlpha() + (255 - base.getAlpha()) * t);
        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b)),
                Math.max(0, Math.min(255, a))
        );
    }
}