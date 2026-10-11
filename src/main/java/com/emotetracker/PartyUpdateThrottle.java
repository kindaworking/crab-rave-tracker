package com.emotetracker;

final class PartyUpdateThrottle
{
    static final long INTERVAL_NANOS = 1_000_000_000L;
    private DanceStats lastStats;
    private long lastSentNanos;
    private boolean syncPending;

    void requestSync()
    {
        syncPending = true;
    }

    boolean shouldSend(DanceStats stats, long now)
    {
        return (lastStats == null || now - lastSentNanos >= INTERVAL_NANOS)
            && (syncPending || stats.active || !stats.sameAs(lastStats));
    }

    void sent(DanceStats stats, long now)
    {
        lastStats = stats;
        lastSentNanos = now;
        syncPending = false;
    }

    void reset()
    {
        lastStats = null;
        syncPending = false;
    }
}
