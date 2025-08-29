package com.AccidentalTeleportBlocker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AccidentalTeleportBlockerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AccidentalTeleportBlockerPlugin.class);
		RuneLite.main(args);
	}
}