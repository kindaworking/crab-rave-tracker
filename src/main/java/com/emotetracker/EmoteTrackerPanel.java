package com.emotetracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class EmoteTrackerPanel extends PluginPanel
{
    private final JPanel players = new JPanel();
    private final Map<Long, PlayerBlock> blocks = new HashMap<>();
    private final JButton copyParty = new JButton("Copy Party Results");
    private List<Long> order = new ArrayList<>();
    private String myResults = "";
    private String partyResults = "";

    EmoteTrackerPanel(Runnable reset)
    {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        players.setLayout(new BoxLayout(players, BoxLayout.Y_AXIS));
        players.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        JLabel title = new JLabel("Crab Rave Tracker");
        title.setForeground(ColorScheme.TEXT_COLOR);
        content.add(title, BorderLayout.NORTH);
        content.add(players, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 4));
        actions.setOpaque(false);
        JButton resetButton = new JButton("Reset My Session");
        JButton copyButton = new JButton("Copy My Results");
        resetButton.addActionListener(event -> reset.run());
        copyButton.addActionListener(event -> copy(myResults));
        copyParty.addActionListener(event -> copy(partyResults));
        actions.add(resetButton);
        actions.add(copyButton);
        actions.add(copyParty);
        content.add(actions, BorderLayout.SOUTH);
        add(content, BorderLayout.NORTH);
    }

    void render(List<TrackedPlayer> entries, boolean inParty)
    {
        List<Long> nextOrder = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++)
        {
            TrackedPlayer player = entries.get(i);
            nextOrder.add(player.id);
            blocks.computeIfAbsent(player.id, ignored -> new PlayerBlock()).render(player);
            if (i == 0)
            {
                myResults = results(player);
            }
        }
        blocks.keySet().retainAll(nextOrder);
        if (!nextOrder.equals(order))
        {
            players.removeAll();
            nextOrder.forEach(id -> players.add(blocks.get(id)));
            order = nextOrder;
        }
        partyResults = partyResults(entries);
        copyParty.setVisible(inParty);
        revalidate();
        repaint();
    }

    private static void copy(String text)
    {
        if (!text.isEmpty())
        {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        }
    }

    static String partyResults(List<TrackedPlayer> entries)
    {
        return entries.stream()
            .filter(player -> player.name != null)
            .sorted(Comparator.comparingLong((TrackedPlayer player) ->
                player.stats == null ? -1 : player.stats.totalNanos).reversed()
                .thenComparing(player -> player.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(player -> player.id))
            .map(EmoteTrackerPanel::results)
            .collect(Collectors.joining("\n\n"));
    }

    static String results(TrackedPlayer player)
    {
        DanceStats stats = player.stats;
        if (player.name == null)
        {
            return "";
        }
        return "Crab Dancer " + player.name + "\n"
            + "Total: " + (stats == null ? "--:--.--" : duration(stats.totalNanos)) + "\n"
            + "Dancing: " + (stats == null ? "--:--.--" : duration(stats.dancingNanos)) + "\n"
            + "Raving: " + (stats == null ? "--:--.--" : duration(stats.ravingNanos)) + "\n"
            + "(V).{^m^}.(V)";
    }

    static String duration(long nanos)
    {
        long hundredths = nanos / 10_000_000L;
        return String.format("%02d:%02d.%02d", hundredths / 6000,
            (hundredths / 100) % 60, hundredths % 100);
    }

    private static final class PlayerBlock extends JPanel
    {
        private final JLabel avatar = new JLabel("?", SwingConstants.CENTER);
        private final JLabel name = new JLabel();
        private final JLabel status = new JLabel();
        private final JLabel[] values = new JLabel[5];
        private BufferedImage avatarImage;

        PlayerBlock()
        {
            setLayout(new BorderLayout(0, 6));
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(8, 0, 8, 0)));
            JPanel identity = new JPanel(new BorderLayout(8, 0));
            identity.setOpaque(false);
            avatar.setPreferredSize(new Dimension(32, 32));
            avatar.setMinimumSize(new Dimension(32, 32));
            avatar.setOpaque(true);
            avatar.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
            avatar.setForeground(ColorScheme.TEXT_COLOR);
            identity.add(avatar, BorderLayout.WEST);
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            name.putClientProperty("html.disable", true);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            name.setForeground(ColorScheme.TEXT_COLOR);
            status.setFont(status.getFont().deriveFont(11f));
            text.add(name);
            text.add(status);
            identity.add(text, BorderLayout.CENTER);
            add(identity, BorderLayout.NORTH);

            JPanel stats = new JPanel(new GridLayout(5, 1, 0, 2));
            stats.setOpaque(false);
            String[] labels = {"Total", "Dancing", "Raving", "Dance sessions", "Longest"};
            for (int i = 0; i < labels.length; i++)
            {
                JPanel row = new JPanel(new BorderLayout(4, 0));
                row.setOpaque(false);
                JLabel label = new JLabel(labels[i]);
                label.setForeground(ColorScheme.TEXT_COLOR);
                row.add(label, BorderLayout.WEST);
                values[i] = new JLabel("-", SwingConstants.RIGHT);
                values[i].setForeground(ColorScheme.TEXT_COLOR);
                row.add(values[i], BorderLayout.CENTER);
                stats.add(row);
            }
            add(stats, BorderLayout.CENTER);
        }

        void render(TrackedPlayer player)
        {
            name.setText(player.name == null ? "Choose a character" : player.name);
            name.setToolTipText(player.name);
            if (avatarImage != player.avatar)
            {
                avatarImage = player.avatar;
                avatar.setIcon(avatarImage == null ? null : new ImageIcon(
                    avatarImage.getScaledInstance(32, 32, Image.SCALE_SMOOTH)));
                avatar.setText(avatarImage == null ? "?" : "");
            }
            DanceStats stats = player.stats;
            if (player.name == null)
            {
                status.setText("Not logged in");
            }
            else if (stats == null)
            {
                status.setText("No data received");
            }
            else if (!stats.active)
            {
                status.setText("Not dancing");
            }
            else
            {
                status.setText(stats.raving ? "Raving" : "Dancing");
            }
            status.setForeground(stats != null && stats.active ? ColorScheme.BRAND_ORANGE : Color.LIGHT_GRAY);
            String[] formatted = stats == null ? new String[]{"-", "-", "-", "-", "-"}
                : new String[]{duration(stats.totalNanos), duration(stats.dancingNanos),
                    duration(stats.ravingNanos), Integer.toString(stats.activations), duration(stats.longestNanos)};
            for (int i = 0; i < values.length; i++)
            {
                values[i].setText(formatted[i]);
            }
        }
    }
}
