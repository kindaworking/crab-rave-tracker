package com.emotetracker;

import net.runelite.client.party.messages.PartyMemberMessage;

public final class CrabRaveUpdate extends PartyMemberMessage
{
    public int version = 1;
    public long sequence;
    public long totalNanos;
    public long dancingNanos;
    public long ravingNanos;
    public long longestNanos;
    public int activations;
    public boolean active;
    public boolean raving;

    public CrabRaveUpdate()
    {
    }

    CrabRaveUpdate(DanceStats stats, long sequence)
    {
        this.sequence = sequence;
        totalNanos = stats.totalNanos;
        dancingNanos = stats.dancingNanos;
        ravingNanos = stats.ravingNanos;
        longestNanos = stats.longestNanos;
        activations = stats.activations;
        active = stats.active;
        raving = stats.raving;
    }

    boolean valid()
    {
        return version == 1 && sequence > 0 && totalNanos >= 0 && dancingNanos >= 0
            && ravingNanos >= 0 && longestNanos >= 0 && activations >= 0;
    }

    DanceStats stats()
    {
        return new DanceStats(totalNanos, dancingNanos, ravingNanos, longestNanos,
            activations, active, raving);
    }
}
