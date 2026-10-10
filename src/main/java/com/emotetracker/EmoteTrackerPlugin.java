package com.emotetracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name = "Crab Rave Tracker", description = "Track Dancing and Raving time per session",
    tags = {"emote", "crab", "dance", "timer", "rave"})
public class EmoteTrackerPlugin extends Plugin
{
    private static final Logger LOG = LoggerFactory.getLogger(EmoteTrackerPlugin.class);

    private static final long PANEL_REFRESH_INTERVAL_NANOS = 100_000_000L;
    // FX_EMOTE_PARTY01_ACTIVE (confetti).
    private static final int CONFETTI_GRAPHIC = 2365;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar toolbar;

    @Inject
    private EmoteTrackerConfig config;

    private DanceSession session;
    private EmoteTrackerPanel panel;
    private NavigationButton navigation;
    private long lastRenderNanos;
    private volatile boolean running;
    private String lastLoggedState = "";

    @Provides
    EmoteTrackerConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(EmoteTrackerConfig.class);
    }

    @Override
    protected void startUp()
    {
        session = new DanceSession();
        panel = new EmoteTrackerPanel(this::resetSession);
        lastRenderNanos = 0;
        lastLoggedState = "";

        BufferedImage icon = ImageUtil.loadImageResource(EmoteTrackerPlugin.class, "icon.png");
        navigation = NavigationButton.builder()
            .tooltip("Crab Rave Tracker")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        toolbar.addNavigation(navigation);
        running = true;
        EmoteTrackerPanel currentPanel = panel;
        clientThread.invokeLater(() ->
        {
            if (running && panel == currentPanel)
            {
                render();
            }
        });
    }

    @Override
    protected void shutDown()
    {
        running = false;
        toolbar.removeNavigation(navigation);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (running && event.getActor() == client.getLocalPlayer())
        {
            sample();
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!running)
        {
            return;
        }

        sample();
        long nowNanos = System.nanoTime();

        if (nowNanos - lastRenderNanos >= PANEL_REFRESH_INTERVAL_NANOS)
        {
            lastRenderNanos = nowNanos;
            render();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (running && event.getGameState() != GameState.LOGGED_IN)
        {
            session.observe(false, false, System.nanoTime());
            render();
        }
    }

    private void sample()
    {
        Player player = client.getLocalPlayer();
        boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && player != null;
        int animation = loggedIn ? player.getAnimation() : -1;
        int graphic = loggedIn ? player.getGraphic() : -1;

        boolean dancing = isCrabDance(animation);
        boolean raving = isCrabRaving(animation, graphic);

        session.observe(dancing, raving, System.nanoTime());

        if (config.debugDetection() && loggedIn)
        {
            logDetectionChange(animation, graphic);
        }
    }

    static boolean isCrabDance(int animation)
    {
        return animation == AnimationID.HUMAN_EMOTE_CRABDANCE
            || animation == AnimationID.HUMAN_EMOTE_CRABDANCE_LOOP;
    }

    static boolean isCrabRaving(int animation, int graphic)
    {
        return isCrabDance(animation) && graphic == CONFETTI_GRAPHIC;
    }

    private void resetSession()
    {
        EmoteTrackerPanel currentPanel = panel;
        clientThread.invokeLater(() ->
        {
            if (!running || panel != currentPanel)
            {
                return;
            }

            sample();
            session.reset(System.nanoTime());
            render();
        });
    }

    private void logDetectionChange(int animation, int graphic)
    {
        String currentState = "animation=" + animation + ", graphic=" + graphic;

        if (currentState.equals(lastLoggedState))
        {
            return;
        }

        LOG.info("Crab Rave Tracker: {}", currentState);
        lastLoggedState = currentState;
    }

    private void render()
    {
        DanceSession.Snapshot snapshot = session.snapshot(System.nanoTime());
        Player player = client.getLocalPlayer();
        String playerName = player == null || player.getName() == null ? "Unknown player" : player.getName();

        EmoteTrackerPanel currentPanel = panel;
        SwingUtilities.invokeLater(() ->
        {
            if (running)
            {
                currentPanel.render(snapshot, playerName);
            }
        });
    }
}
