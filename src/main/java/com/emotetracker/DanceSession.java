package com.emotetracker;

final class DanceSession
{
    private long dancingNanos;
    private long ravingNanos;
    private long longestNanos;
    private long segmentStartNanos;
    private long danceStartNanos;
    private int activations;
    private boolean active;
    private boolean raving;

    void observe(boolean dancing, boolean raving, long nowNanos)
    {
        if (!active)
        {
            if (dancing)
            {
                beginDance(nowNanos, raving);
            }
            return;
        }

        if (!dancing)
        {
            bankSegment(nowNanos);
            endDance(nowNanos);
            return;
        }

        if (raving != this.raving)
        {
            bankSegment(nowNanos);
            segmentStartNanos = nowNanos;
            this.raving = raving;
        }
    }

    private void beginDance(long nowNanos, boolean raving)
    {
        active = true;
        this.raving = raving;
        activations++;
        danceStartNanos = nowNanos;
        segmentStartNanos = nowNanos;
    }

    private void endDance(long nowNanos)
    {
        longestNanos = Math.max(longestNanos, nowNanos - danceStartNanos);
        active = false;
        raving = false;
    }

    private void bankSegment(long nowNanos)
    {
        long elapsedNanos = nowNanos - segmentStartNanos;

        if (raving)
        {
            ravingNanos += elapsedNanos;
        }
        else
        {
            dancingNanos += elapsedNanos;
        }
    }

    void reset(long nowNanos)
    {
        dancingNanos = 0;
        ravingNanos = 0;
        longestNanos = 0;
        activations = active ? 1 : 0;
        segmentStartNanos = nowNanos;
        danceStartNanos = nowNanos;
    }

    Snapshot snapshot(long nowNanos)
    {
        long elapsedNanos = active ? nowNanos - segmentStartNanos : 0;
        long currentDancingNanos = dancingNanos;
        long currentRavingNanos = ravingNanos;

        if (active && raving)
        {
            currentRavingNanos += elapsedNanos;
        }
        else if (active)
        {
            currentDancingNanos += elapsedNanos;
        }

        long currentLongestNanos = active ? Math.max(longestNanos, nowNanos - danceStartNanos) : longestNanos;
        return new Snapshot(currentDancingNanos, currentRavingNanos, currentLongestNanos, activations, active, raving);
    }

    static final class Snapshot
    {
        final long dancingNanos;
        final long ravingNanos;
        final long longestNanos;
        final int activations;
        final boolean active;
        final boolean raving;

        Snapshot(long dancingNanos, long ravingNanos, long longestNanos, int activations,
            boolean active, boolean raving)
        {
            this.dancingNanos = dancingNanos;
            this.ravingNanos = ravingNanos;
            this.longestNanos = longestNanos;
            this.activations = activations;
            this.active = active;
            this.raving = raving;
        }
    }
}
