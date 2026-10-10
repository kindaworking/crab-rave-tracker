package com.emotetracker;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

final class EmoteTrackerPanel extends PluginPanel
{
    private final JLabel status = new JLabel("Not dancing");
    private final JLabel totalTime = new JLabel();
    private final JLabel dancingTime = new JLabel();
    private final JLabel ravingTime = new JLabel();
    private final JLabel sessionCount = new JLabel();
    private final JLabel longestDance = new JLabel();
    private String results = "";

    EmoteTrackerPanel(Runnable reset)
    {
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel stats = new JPanel(new GridLayout(0, 1, 0, 8));
        stats.add(new JLabel("Crab Rave Tracker"));
        stats.add(status);
        stats.add(totalTime);
        stats.add(dancingTime);
        stats.add(ravingTime);
        stats.add(sessionCount);
        stats.add(longestDance);
        add(stats, BorderLayout.NORTH);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
        JButton resetButton = new JButton("Reset Session");
        JButton copyButton = new JButton("Copy Results");

        resetButton.addActionListener(event -> reset.run());
        copyButton.addActionListener(event ->
        {
            if (!results.isEmpty())
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(results), null);
            }
        });

        actions.add(resetButton);
        actions.add(copyButton);
        add(actions, BorderLayout.SOUTH);
    }

    void render(DanceSession.Snapshot snapshot, String playerName)
    {
        String total = duration(snapshot.dancingNanos + snapshot.ravingNanos);
        String dancing = duration(snapshot.dancingNanos);
        String raving = duration(snapshot.ravingNanos);

        status.setText(statusText(snapshot));
        totalTime.setText(row("Total", total));
        dancingTime.setText(row("Dancing", dancing));
        ravingTime.setText(row("Raving", raving));
        sessionCount.setText(row("Dance sessions", Integer.toString(snapshot.activations)));
        longestDance.setText(row("Longest", duration(snapshot.longestNanos)));

        results = "Crab Rave Tracker " + playerName + "\n"
            + "**Total**: " + total + "\n"
            + "**Dancing**: " + dancing + "\n"
            + "**Raving**: " + raving + "\n"
            + "(V).{^m^}.(V)";
    }

    private static String statusText(DanceSession.Snapshot snapshot)
    {
        if (!snapshot.active)
        {
            return "Not dancing";
        }

        return snapshot.raving ? "Raving" : "Dancing";
    }

    private static String row(String label, String value)
    {
        return "<html><b>" + label + "</b>: " + value + "</html>";
    }

    static String duration(long nanos)
    {
        long hundredths = nanos / 10_000_000L;
        long minutes = hundredths / 6000;
        long seconds = (hundredths / 100) % 60;
        long centiseconds = hundredths % 100;

        return String.format("%02d:%02d.%02d", minutes, seconds, centiseconds);
    }
}
