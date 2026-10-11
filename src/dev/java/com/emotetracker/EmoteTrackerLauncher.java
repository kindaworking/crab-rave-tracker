package com.emotetracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class EmoteTrackerLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(EmoteTrackerPlugin.class);
        RuneLite.main(args);
    }
}
