package com.emotetracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
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

    @Inject
    private PartyService party;

    @Inject
    private WSClient wsClient;

    private final Map<Long, CrabRaveUpdate> remoteStats = new HashMap<>();
    private final PartyUpdateThrottle updateThrottle = new PartyUpdateThrottle();
    private long currentPartyId;
    private long localMemberId = -1;
    private long sequence;
    private String lastKnownPlayerName;
    private String lastSyncedPlayerName;
    private long lastSyncRequestNanos;
    private int syncRetriesRemaining;
    private boolean syncRequestPending;

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
        remoteStats.clear();
        updateThrottle.reset();
        currentPartyId = 0;
        localMemberId = -1;
        lastKnownPlayerName = null;
        lastSyncedPlayerName = null;
        syncRequestPending = false;
        syncRetriesRemaining = 0;
        wsClient.registerMessage(CrabRaveUpdate.class);
        wsClient.registerMessage(CrabRaveSyncRequest.class);

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
        wsClient.unregisterMessage(CrabRaveUpdate.class);
        wsClient.unregisterMessage(CrabRaveSyncRequest.class);
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
        syncParty(nowNanos);

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
            syncParty(System.nanoTime());
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
            updateThrottle.requestSync();
            syncParty(System.nanoTime());
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

    @Subscribe
    public void onCrabRaveUpdate(CrabRaveUpdate update)
    {
        onClientThread(() ->
        {
            PartyMember local = party.getLocalMember();
            if (!party.isInParty() || (local != null && update.getMemberId() == local.getMemberId())
                || party.getMemberById(update.getMemberId()) == null || !update.valid())
            {
                return;
            }
            CrabRaveUpdate previous = remoteStats.get(update.getMemberId());
            if (previous == null || update.sequence > previous.sequence)
            {
                remoteStats.put(update.getMemberId(), update);
                render();
            }
        });
    }

    @Subscribe
    public void onCrabRaveSyncRequest(CrabRaveSyncRequest request)
    {
        requestPartySync(request.getMemberId());
    }

    @Subscribe
    public void onUserSync(UserSync request)
    {
        requestPartySync(request.getMemberId());
    }

    private void requestPartySync(long memberId)
    {
        onClientThread(() ->
        {
            PartyMember local = party.getLocalMember();
            if (local != null && local.getMemberId() != memberId
                && party.getMemberById(memberId) != null)
            {
                updateThrottle.requestSync();
            }
        });
    }

    @Subscribe
    public void onPartyChanged(PartyChanged event)
    {
        onClientThread(() ->
        {
            remoteStats.clear();
            updateThrottle.reset();
            localMemberId = -1;
            currentPartyId = party.getPartyId();
            syncRequestPending = false;
            syncRetriesRemaining = 0;
            render();
        });
    }

    @Subscribe
    public void onUserJoin(UserJoin event)
    {
        onClientThread(() ->
        {
            PartyMember local = party.getLocalMember();
            if (local != null && event.getMemberId() == local.getMemberId())
            {
                localMemberId = -1;
                remoteStats.clear();
            }
            render();
        });
    }

    @Subscribe
    public void onUserPart(UserPart event)
    {
        onClientThread(() ->
        {
            remoteStats.remove(event.getMemberId());
            render();
        });
    }

    private void onClientThread(Runnable action)
    {
        EmoteTrackerPanel currentPanel = panel;
        clientThread.invokeLater(() ->
        {
            if (running && panel == currentPanel)
            {
                action.run();
            }
        });
    }

    private void syncParty(long now)
    {
        if (currentPartyId != party.getPartyId())
        {
            currentPartyId = party.getPartyId();
            remoteStats.clear();
            updateThrottle.reset();
            localMemberId = -1;
            syncRequestPending = false;
            syncRetriesRemaining = 0;
        }
        if (!party.isInParty())
        {
            return;
        }
        PartyMember local = party.getLocalMember();
        if (local == null)
        {
            localMemberId = -1;
            return;
        }
        if (localMemberId != local.getMemberId())
        {
            localMemberId = local.getMemberId();
            updateThrottle.requestSync();
            syncRequestPending = true;
            syncRetriesRemaining = 2;
        }
        String playerName = localPlayerName(local);
        if (playerName != null && !playerName.equals(lastSyncedPlayerName))
        {
            lastSyncedPlayerName = playerName;
            updateThrottle.requestSync();
            syncRequestPending = true;
            syncRetriesRemaining = 2;
        }
        boolean missingData = party.getMembers().stream().anyMatch(member ->
            member.getMemberId() != local.getMemberId() && !remoteStats.containsKey(member.getMemberId()));
        if (syncRequestPending || (syncRetriesRemaining > 0 && missingData
            && now - lastSyncRequestNanos >= 5_000_000_000L))
        {
            if (!syncRequestPending)
            {
                syncRetriesRemaining--;
            }
            party.send(new CrabRaveSyncRequest());
            lastSyncRequestNanos = now;
            syncRequestPending = false;
        }
        DanceStats stats = DanceStats.local(session.snapshot(now));
        if (updateThrottle.shouldSend(stats, now))
        {
            sequence = Math.max(sequence + 1, System.currentTimeMillis());
            party.send(new CrabRaveUpdate(stats, sequence));
            updateThrottle.sent(stats, now);
        }
    }

    private void render()
    {
        DanceStats snapshot = DanceStats.local(session.snapshot(System.nanoTime()));
        boolean inParty = party.isInParty();
        PartyMember local = inParty ? party.getLocalMember() : null;
        String playerName = localPlayerName(local);
        List<TrackedPlayer> entries = new ArrayList<>();
        entries.add(new TrackedPlayer(-1, playerName,
            local == null ? null : local.getAvatar(), playerName == null ? null : snapshot));
        if (inParty)
        {
            List<PartyMember> members = new ArrayList<>(party.getMembers());
            remoteStats.keySet().removeIf(id -> members.stream().noneMatch(member -> member.getMemberId() == id));
            members.sort(Comparator.comparing(EmoteTrackerPlugin::memberName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(PartyMember::getMemberId));
            for (PartyMember member : members)
            {
                if (local != null && member.getMemberId() == local.getMemberId())
                {
                    continue;
                }
                if (!hasCharacterName(member.getDisplayName()))
                {
                    continue;
                }
                CrabRaveUpdate update = remoteStats.get(member.getMemberId());
                entries.add(new TrackedPlayer(member.getMemberId(), memberName(member), member.getAvatar(),
                    update == null ? null : update.stats()));
            }
        }

        EmoteTrackerPanel currentPanel = panel;
        SwingUtilities.invokeLater(() ->
        {
            if (running && panel == currentPanel)
            {
                currentPanel.render(entries, inParty);
            }
        });
    }

    private static String memberName(PartyMember member)
    {
        String name = member.getDisplayName();
        return hasCharacterName(name) ? name : "";
    }

    private static boolean hasCharacterName(String name)
    {
        return name != null && !name.trim().isEmpty() && !"<unknown>".equals(name);
    }

    private String localPlayerName(PartyMember local)
    {
        Player player = client.getLocalPlayer();
        String name = player == null ? null : player.getName();
        if (!hasCharacterName(name))
        {
            name = local == null ? null : local.getDisplayName();
        }
        if (hasCharacterName(name))
        {
            lastKnownPlayerName = name;
        }
        return lastKnownPlayerName;
    }
}
