package com.barracudaghost;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import com.google.inject.Provides;

@Slf4j
@PluginDescriptor(
		name = "Barracuda Ghost Ship"
)
public class BarracudaGhostPlugin extends Plugin
{
	private enum RecordState
	{
		IDLE,
		ARMED,
		RECORDING
	}

	private static final String CONFIG_GROUP = "barracudaghost";

	private static final int STANDARD_LEAD_TICKS = 0;
	private static final int FOLLOW_MODE_RAMP_RATE = 2;
	private static final int GAME_TICK_LENGTH_MS = 600;
	private static final int MISSING_BOAT_TICK_THRESHOLD = 1;
	private static final long EVENT_FLASH_DURATION_MS = 1500;
	private static final int MASTER_STATE_IN_TRIAL_VALUE = 2;

	private static final String SAIL_TRIM_TRIGGER = "You trim the sails";
	private static final String MOTE_SPENT_TRIGGER = "You release the wind mote for a burst of speed!";
	private static final String PREPARE_PREFIX = "You prepare to begin the";
	private static final String COMPLETE_PREFIX = "You have completed the";
	private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+):(\\d+)");

	private static final int MOTE_HARVEST_VARBIT_ID = 19178;
	private static final int HARVEST_CLICK_OFFSET_TICKS = 4;

	private static final int HARVEST_WINDOW_MIN_TICKS = 2;
	private static final int HARVEST_WINDOW_MAX_TICKS = 6;

	private static final String PB_KEY_PREFIX = "pb_";

	@Inject
	private Client client;

	@Inject
	private Config config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Overlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	private PluginPanel panel;
	private NavigationButton navButton;

	private RecordState state = RecordState.IDLE;
	private int missingBoatTicks;

	private final List<FrameDif> keyframes = new ArrayList<>();
	private final List<Events> recordedEvents = new ArrayList<>();
	private int tickCounter;

	private GhostShip ghostShip;
	private List<Events> events = new ArrayList<>();
	private int nextGhostEventIndex;
	private final long[] lastEventTriggerMillis = new long[Events.Type.values().length];

	private int playbackTick;
	private FrameDif currentFrameDif;
	private FrameDif previousFrameDif;
	private long lastTickTimeMillis;

	private int lastHarvestVarbitValue = -1;
	private int lastExtractorClickTick = -1;
	private boolean extractorHarvestPending;

	private TrialData.TrialType currentTrialType = TrialData.TrialType.UNKNOWN;
	private boolean awaitingFinalTime;

	private String loadedGhostUsername;
	private TrialData.Rank loadedGhostRank;
	private boolean pendingRankCheck;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);

		panel = new PluginPanel(configManager, this);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/BarracudaGhostShipIcon.png");

		navButton = NavigationButton.builder()
				.tooltip("Barracuda Ghost Ship")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
	}

	public boolean isInTrial()
	{
		return state != RecordState.IDLE;
	}

	public String getLoadedGhostUsername()
	{
		return loadedGhostUsername;
	}

	public FrameDif getInterpolatedGhostFrame()
	{
		if (currentFrameDif == null)
		{
			return null;
		}
		if (previousFrameDif == null)
		{
			return currentFrameDif;
		}

		long elapsed = System.currentTimeMillis() - lastTickTimeMillis;
		double t = Math.min(1.0, Math.max(0.0, elapsed / (double) GAME_TICK_LENGTH_MS));

		int x = (int) Math.round(previousFrameDif.X
				+ t * (currentFrameDif.X - previousFrameDif.X));
		int y = (int) Math.round(previousFrameDif.Y
				+ t * (currentFrameDif.Y - previousFrameDif.Y));

		double diff = angleDiff(previousFrameDif.O, currentFrameDif.O);
		int orientation = (int) Math.round(previousFrameDif.O + t * diff);
		orientation = ((orientation % 2048) + 2048) % 2048;

		return new FrameDif(x, y, orientation, 0);
	}

	public float getEventFlashIntensity(Events.Type type)
	{
		long trigger = lastEventTriggerMillis[type.ordinal()];
		if (trigger <= 0)
		{
			return 0f;
		}
		long elapsed = System.currentTimeMillis() - trigger;
		if (elapsed >= EVENT_FLASH_DURATION_MS)
		{
			return 0f;
		}
		return 1f - (float) elapsed / EVENT_FLASH_DURATION_MS;
	}

	private int getGhostLeadTicks()
	{
		boolean followModeEnabled = Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, "followModeEnabled"));
		return followModeEnabled ? config.followModeLeadTicks() : STANDARD_LEAD_TICKS;
	}

	private int getRankVarbitForTrial(TrialData.TrialType trialType)
	{
		switch (trialType)
		{
			case TEMPOR_TANTRUM:
				return VarbitID.SAILING_BT_TEMPOR_TANTRUM_PREVIOUS_ATTEMPT;
			case JUBBLY_JIVE:
				return VarbitID.SAILING_BT_JUBBLY_JIVE_PREVIOUS_ATTEMPT;
			case GWENITH_GLIDE:
				return VarbitID.SAILING_BT_GWENITH_GLIDE_PREVIOUS_ATTEMPT;
			default:
				return -1;
		}
	}

	private int getMasterStateVarbitForTrial(TrialData.TrialType trialType)
	{
		switch (trialType)
		{
			case TEMPOR_TANTRUM:
				return VarbitID.SAILING_BT_TEMPOR_TANTRUM_MASTER_STATE;
			case JUBBLY_JIVE:
				return VarbitID.SAILING_BT_JUBBLY_JIVE_MASTER_STATE;
			case GWENITH_GLIDE:
				return VarbitID.SAILING_BT_GWENITH_GLIDE_MASTER_STATE;
			default:
				return -1;
		}
	}

	private TrialData.Rank readCurrentRank(TrialData.TrialType trialType)
	{
		int varbitId = getRankVarbitForTrial(trialType);
		if (varbitId == -1)
		{
			return TrialData.Rank.UNRANKED;
		}

		int value = client.getVarbitValue(varbitId);
		TrialData.Rank[] ranks = TrialData.Rank.values();
		int index = value - 1;
		if (index < 0 || index >= ranks.length)
		{
			return TrialData.Rank.UNRANKED;
		}
		return ranks[index];
	}

	private static String formatEnumName(Enum<?> value)
	{
		String[] words = value.name().split("_");
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < words.length; i++)
		{
			String word = words[i];
			if (word.isEmpty())
			{
				continue;
			}
			if (result.length() > 0)
			{
				result.append(" ");
			}
			result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
		}
		return result.toString();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (state != RecordState.RECORDING)
		{
			return;
		}

		String target = event.getMenuTarget();
		boolean isExtractorClick = target != null && target.toLowerCase().contains("crystal extractor");

		if (isExtractorClick)
		{
			lastExtractorClickTick = tickCounter;
			extractorHarvestPending = true;
		}
		else if (extractorHarvestPending)
		{
			extractorHarvestPending = false;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()) || !"erasePbData".equals(event.getKey()))
		{
			return;
		}

		if ("true".equals(event.getNewValue()) && panel != null)
		{
			panel.startErasePbConfirmationFlow();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (state == RecordState.ARMED && event.getVarpId() == VarPlayerID.SAILING_BT_TIME_START
				&& event.getValue() != 0)
		{
			beginRecordingNow();
			return;
		}

		int varbitId = event.getVarbitId();

		int masterStateVarbitId = getMasterStateVarbitForTrial(currentTrialType);
		if (masterStateVarbitId != -1 && varbitId == masterStateVarbitId)
		{
			if (event.getValue() == MASTER_STATE_IN_TRIAL_VALUE)
			{
				if (currentTrialType != TrialData.TrialType.UNKNOWN)
				{
					armForNewAttempt();
				}
			}
			else if (state != RecordState.IDLE)
			{
				abortSession();
			}
			return;
		}

		if (state != RecordState.RECORDING || varbitId != MOTE_HARVEST_VARBIT_ID)
		{
			return;
		}

		int newValue = event.getValue();
		if (lastHarvestVarbitValue >= 0 && newValue > lastHarvestVarbitValue)
		{
			int ticksSinceClick = tickCounter - lastExtractorClickTick;
			boolean withinWindow = extractorHarvestPending
					&& ticksSinceClick >= HARVEST_WINDOW_MIN_TICKS
					&& ticksSinceClick <= HARVEST_WINDOW_MAX_TICKS;

			if (withinWindow)
			{
				int eventTick = Math.max(0, tickCounter - HARVEST_CLICK_OFFSET_TICKS);
				recordedEvents.add(new Events(eventTick, Events.Type.ExtractorHarvested));

				extractorHarvestPending = false;
			}
		}
		lastHarvestVarbitValue = newValue;
	}

	private void checkRankMismatch()
	{
		if (ghostShip == null || loadedGhostRank == null)
		{
			return;
		}

		TrialData.Rank currentRank = readCurrentRank(currentTrialType);
		if (loadedGhostRank != currentRank)
		{
			if (panel != null)
			{
				panel.ExternalStatus("Ghost is " + formatEnumName(loadedGhostRank)
						+ " rank, you're on " + formatEnumName(currentRank) + " - not loaded.");
			}
			ghostShip = null;
			events = new ArrayList<>();
			loadedGhostUsername = null;
			loadedGhostRank = null;
		}
	}

	private void beginRecordingNow()
	{
		state = RecordState.RECORDING;

		checkRankMismatch();

		WorldEntity boat = findPlayerBoat();
		WorldView topLevel = client.getTopLevelWorldView();

		if (boat != null && topLevel != null)
		{
			LocalPoint localPos = boat.getLocalLocation();
			int orientation = boat.getOrientation();
			int worldX = localPos.getX() + topLevel.getBaseX() * 128;
			int worldY = localPos.getY() + topLevel.getBaseY() * 128;

			keyframes.add(new FrameDif(worldX, worldY, orientation, 0));
		}

		playbackTick = 0;
		if (ghostShip != null)
		{
			previousFrameDif = ghostShip.getFrameAtTick(playbackTick);
			currentFrameDif = ghostShip.getFrameAtTick(playbackTick + 1);
			lastTickTimeMillis = System.currentTimeMillis();
			advanceGhostEvents();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String msg = event.getMessage();

		if (msg.contains(PREPARE_PREFIX))
		{
			currentTrialType = TrialData.TrialType.fromChatMessage(msg);

			int masterStateVarbitId = getMasterStateVarbitForTrial(currentTrialType);
			if (masterStateVarbitId != -1 && client.getVarbitValue(masterStateVarbitId) == MASTER_STATE_IN_TRIAL_VALUE
					&& state == RecordState.IDLE)
			{
				armForNewAttempt();
			}
			return;
		}

		if (msg.contains(COMPLETE_PREFIX))
		{
			onTrialCompleted();
			return;
		}

		if (awaitingFinalTime)
		{
			Matcher matcher = TIME_PATTERN.matcher(msg);
			if (matcher.find())
			{
				int minutes = Integer.parseInt(matcher.group(1));
				int seconds = Integer.parseInt(matcher.group(2));
				int totalSeconds = minutes * 60 + seconds;
				finalizeRecording(totalSeconds);
			}
			return;
		}

		if (state != RecordState.RECORDING)
		{
			return;
		}

		if (msg.contains(SAIL_TRIM_TRIGGER))
		{
			recordEvent(Events.Type.SailTrim);
		}
		else if (msg.contains(MOTE_SPENT_TRIGGER))
		{
			recordEvent(Events.Type.MoteUsed);
		}
	}

	private void recordEvent(Events.Type type)
	{
		recordedEvents.add(new Events(tickCounter, type));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState newState = event.getGameState();
		if ((newState == GameState.LOGIN_SCREEN || newState == GameState.CONNECTION_LOST)
				&& state != RecordState.IDLE)
		{
			abortSession();
		}
	}

	private void armForNewAttempt()
	{
		state = RecordState.ARMED;
		keyframes.clear();
		recordedEvents.clear();
		tickCounter = 0;
		playbackTick = 0;
		currentFrameDif = null;
		previousFrameDif = null;
		missingBoatTicks = 0;
		nextGhostEventIndex = 0;
		lastHarvestVarbitValue = -1;
		lastExtractorClickTick = -1;
		extractorHarvestPending = false;
		awaitingFinalTime = false;
		java.util.Arrays.fill(lastEventTriggerMillis, -1L);

		loadGhost();

		pendingRankCheck = ghostShip != null && loadedGhostRank != null;

		if (panel != null)
		{
			panel.updateTrialLockState();
		}
	}

	private void abortSession()
	{
		state = RecordState.IDLE;
		currentFrameDif = null;
		previousFrameDif = null;
		missingBoatTicks = 0;
		awaitingFinalTime = false;
		pendingRankCheck = false;
		java.util.Arrays.fill(lastEventTriggerMillis, -1L);

		if (panel != null)
		{
			panel.updateTrialLockState();
		}
	}

	private void loadGhost()
	{
		String ghostData = configManager.getConfiguration(CONFIG_GROUP, "ghostData");
		if (ghostData == null || ghostData.isEmpty())
		{
			ghostShip = null;
			events = new ArrayList<>();
			loadedGhostUsername = null;
			loadedGhostRank = null;
			return;
		}

		try
		{
			RecordingEncoder.DecodedGhost decoded = RecordingEncoder.decode(ghostData);

			if (currentTrialType != TrialData.TrialType.UNKNOWN
					&& decoded.trialdata.trialType != TrialData.TrialType.UNKNOWN
					&& decoded.trialdata.trialType != currentTrialType)
			{
				if (panel != null)
				{
					panel.ExternalStatus("Ghost is for " + formatEnumName(decoded.trialdata.trialType)
							+ ", not " + formatEnumName(currentTrialType) + " - not loaded.");
				}
				ghostShip = null;
				events = new ArrayList<>();
				loadedGhostUsername = null;
				loadedGhostRank = null;
				return;
			}

			ghostShip = new GhostShip(decoded.frames);
			events = decoded.events;
			loadedGhostUsername = decoded.trialdata.username;
			loadedGhostRank = decoded.trialdata.rank;
		}
		catch (Exception e)
		{
			ghostShip = null;
			events = new ArrayList<>();
			loadedGhostUsername = null;
			loadedGhostRank = null;
		}
	}

	private void onTrialCompleted()
	{
		state = RecordState.IDLE;
		currentFrameDif = null;
		previousFrameDif = null;

		recordedEvents.sort((a, b) -> Integer.compare(a.tick, b.tick));
		awaitingFinalTime = true;
	}

	private void finalizeRecording(int finalTimeSeconds)
	{
		awaitingFinalTime = false;

		TrialData.Rank rank = readCurrentRank(currentTrialType);
		String username = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";

		TrialData metadata = new TrialData(currentTrialType, rank, finalTimeSeconds, username);

		try
		{
			String encoded = RecordingEncoder.encode(keyframes, recordedEvents, metadata);

			configManager.setConfiguration(CONFIG_GROUP, "lastRecordedGhost", encoded);
			updatePersonalBestIfNeeded(currentTrialType, rank, encoded);
			if (panel != null)
			{
				panel.refresh();
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to encode ghost data", e);
		}
	}

	private void updatePersonalBestIfNeeded(TrialData.TrialType trialType, TrialData.Rank rank, String encoded)
	{
		if (trialType == TrialData.TrialType.UNKNOWN)
		{
			return;
		}

		String pbKey = PB_KEY_PREFIX + trialType.name() + "_" + rank.name();
		String existing = configManager.getConfiguration(CONFIG_GROUP, pbKey);

		boolean hadExistingPb = existing != null && !existing.isEmpty();
		boolean isNewBest = !hadExistingPb;
		double improvementSeconds = 0;

		if (hadExistingPb)
		{
			try
			{
				RecordingEncoder.DecodedGhost newDecoded = RecordingEncoder.decode(encoded);
				RecordingEncoder.DecodedGhost existingDecoded = RecordingEncoder.decode(existing);

				double newTicks = newDecoded.frames.size() - 1;
				double existingTicks = existingDecoded.frames.size() - 1;

				if (newTicks < existingTicks)
				{
					isNewBest = true;
					improvementSeconds = (existingTicks - newTicks) * 0.6;
				}
			}
			catch (Exception e)
			{
				isNewBest = true;
			}
		}

		if (isNewBest)
		{
			configManager.setConfiguration(CONFIG_GROUP, pbKey, encoded);
			if (panel != null)
			{
				String message = "New personal best for " + formatEnumName(trialType)
						+ " " + formatEnumName(rank);
				if (hadExistingPb && improvementSeconds > 0)
				{
					message += " by " + String.format("%.1f", improvementSeconds) + " seconds!";
				}
				else
				{
					message += "!";
				}
				panel.ExternalStatus(message);
			}
		}
	}

	private WorldEntity findPlayerBoat()
	{
		WorldView topLevel = client.getTopLevelWorldView();
		if (topLevel == null)
		{
			return null;
		}

		IndexedObjectSet<? extends WorldEntity> entities = topLevel.worldEntities();
		for (WorldEntity we : entities)
		{
			if (we.getOwnerType() == WorldEntity.OWNER_TYPE_SELF_PLAYER)
			{
				return we;
			}
		}
		return null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (state == RecordState.IDLE)
		{
			return;
		}

		WorldEntity boat = findPlayerBoat();
		WorldView topLevel = client.getTopLevelWorldView();

		if (boat == null || topLevel == null)
		{
			missingBoatTicks++;

			if (missingBoatTicks >= MISSING_BOAT_TICK_THRESHOLD)
			{
				abortSession();
			}
			return;
		}

		missingBoatTicks = 0;

		if (state == RecordState.ARMED && pendingRankCheck)
		{
			pendingRankCheck = false;
			checkRankMismatch();
		}

		if (state != RecordState.RECORDING)
		{
			return;
		}

		LocalPoint localPos = boat.getLocalLocation();
		int orientation = boat.getOrientation();

		int worldX = localPos.getX() + topLevel.getBaseX() * 128;
		int worldY = localPos.getY() + topLevel.getBaseY() * 128;

		int targetLead = getGhostLeadTicks();
		int currentGap = playbackTick - tickCounter;

		tickCounter++;

		if (currentGap < targetLead)
		{
			playbackTick += FOLLOW_MODE_RAMP_RATE;
		}
		else
		{
			playbackTick += 1;
		}

		if (ghostShip != null)
		{
			previousFrameDif = currentFrameDif;
			currentFrameDif = ghostShip.getFrameAtTick(playbackTick + 1);
			lastTickTimeMillis = System.currentTimeMillis();
			advanceGhostEvents();
		}

		keyframes.add(new FrameDif(worldX, worldY, orientation, 1));
	}

	private void advanceGhostEvents()
	{
		while (nextGhostEventIndex < events.size()
				&& events.get(nextGhostEventIndex).tick <= playbackTick)
		{
			Events firedEvent = events.get(nextGhostEventIndex);
			lastEventTriggerMillis[firedEvent.type.ordinal()] = System.currentTimeMillis();
			nextGhostEventIndex++;
		}
	}

	private static double angleDiff(int a, int b)
	{
		int diff = (b - a) % 2048;
		if (diff > 1024)
		{
			diff -= 2048;
		}
		else if (diff < -1024)
		{
			diff += 2048;
		}
		return diff;
	}

	@Provides
	Config provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(Config.class);
	}

	public boolean isFollowModeEnabled()
	{
		return Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, "followModeEnabled"));
	}

	public List<TrailPoint> getGhostTrailPoints()
	{
		if (ghostShip == null)
		{
			return new ArrayList<>();
		}
		return ghostShip.getTrailPointsUpToTick(playbackTick);
	}

	public int getPlaybackTick()
	{
		return playbackTick;
	}

	public List<Events> getGhostTrailEvents()
	{
		if (events.isEmpty() || nextGhostEventIndex <= 0)
		{
			return new ArrayList<>();
		}
		return events.subList(0, Math.min(nextGhostEventIndex, events.size()));
	}

	public FrameDif getGhostFrameAtTick(int tick)
	{
		return ghostShip != null ? ghostShip.getFrameAtTick(tick) : null;
	}
}