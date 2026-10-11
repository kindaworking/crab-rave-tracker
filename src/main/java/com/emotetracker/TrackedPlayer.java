package com.emotetracker;

import java.awt.image.BufferedImage;

final class TrackedPlayer
{
    final long id;
    final String name;
    final BufferedImage avatar;
    final DanceStats stats;

    TrackedPlayer(long id, String name, BufferedImage avatar, DanceStats stats)
    {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.stats = stats;
    }
}
