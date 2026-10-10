package com.emotetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("emote-tracker")
public interface EmoteTrackerConfig extends Config
{
    @ConfigItem(keyName = "debugDetection", name = "Log detection changes",
        description = "Log local animation and graphic IDs for in-game verification")
    default boolean debugDetection()
    {
        return false;
    }
}
