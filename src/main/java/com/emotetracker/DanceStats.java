package com.emotetracker;

final class DanceStats
{
    final long totalNanos;
    final long dancingNanos;
    final long ravingNanos;
    final long longestNanos;
    final int activations;
    final boolean active;
    final boolean raving;

    DanceStats(long totalNanos, long dancingNanos, long ravingNanos, long longestNanos,
        int activations, boolean active, boolean raving)
    {
        this.totalNanos = totalNanos;
        this.dancingNanos = dancingNanos;
        this.ravingNanos = ravingNanos;
        this.longestNanos = longestNanos;
        this.activations = activations;
        this.active = active;
        this.raving = raving;
    }

    static DanceStats local(DanceSession.Snapshot snapshot)
    {
        return new DanceStats(snapshot.dancingNanos + snapshot.ravingNanos,
            snapshot.dancingNanos, snapshot.ravingNanos, snapshot.longestNanos,
            snapshot.activations, snapshot.active, snapshot.raving);
    }

    boolean sameAs(DanceStats other)
    {
        return other != null && totalNanos == other.totalNanos
            && dancingNanos == other.dancingNanos && ravingNanos == other.ravingNanos
            && longestNanos == other.longestNanos && activations == other.activations
            && active == other.active && raving == other.raving;
    }
}
