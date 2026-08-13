package com.barracudaghost;

import java.awt.Color;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("barracudaghost")
public interface Config extends net.runelite.client.config.Config
{
	@ConfigItem(
			keyName = "ghostColor",
			name = "Ghost color",
			description = "Color for the ghost boat overlay.",
			position = 1
	)
	default Color ghostColor()
	{
		return new Color(140, 190, 140);
	}

	@ConfigSection(
			name = "Event Flashes",
			description = "Colored glow settings shown directly on the ghost boat when it trims sails, releases a wind mote, or harvests from the crystal extractor.",
			position = 2,
			closedByDefault = false
	)
	String eventFlashesSection = "eventFlashesSection";

	@ConfigItem(
			keyName = "sailTrimFlashEnabled",
			name = "Show sail trim flash",
			description = "Show the colored flash on the ghost when it trims sails. Trims are always recorded regardless of this setting.",
			position = 3,
			section = "eventFlashesSection"
	)
	default boolean sailTrimFlashEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "sailTrimFlashColor",
			name = "Sail trim flash color",
			description = "Color for the sail trim flash.",
			position = 4,
			section = "eventFlashesSection"
	)
	default Color sailTrimFlashColor()
	{
		return new Color(255, 225, 60);
	}

	@ConfigItem(
			keyName = "moteSpentFlashEnabled",
			name = "Show mote release flash",
			description = "Show the colored flash on the ghost when it releases a wind mote. Releases are always recorded regardless of this setting.",
			position = 5,
			section = "eventFlashesSection"
	)
	default boolean moteSpentFlashEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "moteSpentFlashColor",
			name = "Mote release flash color",
			description = "Color for the mote release flash.",
			position = 6,
			section = "eventFlashesSection"
	)
	default Color moteSpentFlashColor()
	{
		return new Color(60, 220, 220);
	}

	@ConfigItem(
			keyName = "moteHarvestedFlashEnabled",
			name = "Show mote harvest flash",
			description = "Show the colored flash on the ghost when it harvests from the crystal extractor. Harvests are always recorded regardless of this setting.",
			position = 7,
			section = "eventFlashesSection"
	)
	default boolean moteHarvestedFlashEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "moteHarvestedFlashColor",
			name = "Mote harvest flash color",
			description = "Color for the crystal extractor harvest flash.",
			position = 8,
			section = "eventFlashesSection"
	)
	default Color moteHarvestedFlashColor()
	{
		return new Color(230, 50, 50);
	}

	@ConfigItem(
			keyName = "showEventTextLabels",
			name = "Show Event Text Labels",
			description = "Display events on the ghost with text labels. The Corresponding event flash must be enabled.",
			position = 9,
			section = "eventFlashesSection"
	)
	default boolean showEventTextLabels()
	{
		return false;
	}

	@ConfigSection(
			name = "Follow Mode",
			description = "Options for the Follow Mode leading-ghost feature, including its optional path trail.",
			position = 10,
			closedByDefault = false
	)
	String followModeSection = "followModeSection";

	@ConfigItem(
			keyName = "followModeLeadTicks",
			name = "Follow Mode lead ticks",
			description = "How many ticks the ghost will accelerate ahead at the start of playback.",
			position = 11,
			section = "followModeSection"
	)
	default int followModeLeadTicks()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "showGhostTrail",
			name = "Show Follow Mode trail",
			description = "Draw a line marking the path the ghost took.",
			position = 12,
			section = "followModeSection"
	)
	default boolean showGhostTrail()
	{
		return true;
	}

	@ConfigItem(
			keyName = "ghostTrailColor",
			name = "Follow Mode trail color",
			description = "Color for the Follow Mode path trail.",
			position = 13,
			section = "followModeSection"
	)
	default Color ghostTrailColor()
	{
		return new Color(210, 235, 255);
	}

	@ConfigItem(
			keyName = "showTrailEvents",
			name = "Show trail events",
			description = "Display events on the Follow Mode trail with small colored shapes.",
			position = 14,
			section = "followModeSection"
	)
	default boolean showTrailEvents()
	{
		return true;
	}

	@ConfigItem(
			keyName = "trailFadeDelayTicks",
			name = "Trail Fade Delay",
			description = "How many ticks before a trail section fades.",
			position = 15,
			section = "followModeSection"
	)
	default int trailFadeDelayTicks()
	{
		return 25;
	}

	@Range(
			min = 0,
			max = 100
	)
	@ConfigItem(
			keyName = "trailFadeIntensity",
			name = "Trail Fade Intensity",
			description = "How much the follow mode trail fades. 100 = 100% transparency.",
			position = 16,
			section = "followModeSection"
	)
	default int trailFadeIntensity()
	{
		return 50;
	}

	@ConfigItem(
			keyName = "trailSailTrimColor",
			name = "Trail sail trim color",
			description = "Color for the triangle marking a sail trim on the trail.",
			position = 17,
			section = "followModeSection"
	)
	default Color trailSailTrimColor()
	{
		return new Color(255, 225, 60);
	}

	@ConfigItem(
			keyName = "trailMoteUsedColor",
			name = "Trail mote use color",
			description = "Color for the circle marking a wind mote use on the trail.",
			position = 18,
			section = "followModeSection"
	)
	default Color trailMoteUsedColor()
	{
		return new Color(60, 220, 220);
	}

	@ConfigItem(
			keyName = "trailExtractorHarvestedColor",
			name = "Trail extractor harvest color",
			description = "Color for the square marking a crystal extractor harvest on the trail.",
			position = 19,
			section = "followModeSection"
	)
	default Color trailExtractorHarvestedColor()
	{
		return new Color(230, 50, 50);
	}

	@ConfigSection(
			name = "PB Data",
			description = "Manage personal best data.",
			position = 20,
			closedByDefault = true
	)
	String dangerZoneSection = "pbDataSection";

	@ConfigItem(
			keyName = "erasePbData",
			name = "Erase PB Data",
			description = "Check this to begin a confirmation process for permanently erasing all stored personal best data.",
			position = 20,
			section = "pbDataSection"
	)
	default boolean erasePbData()
	{
		return false;
	}
}