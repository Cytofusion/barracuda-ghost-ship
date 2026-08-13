package com.barracudaghost;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BarracudaGhostTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BarracudaGhostPlugin.class);
		RuneLite.main(args);
	}
}