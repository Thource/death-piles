package dev.thource.runelite.dudewheresmystuff;

import com.google.inject.Provides;
import com.google.inject.name.Named;
import dev.thource.runelite.dudewheresmystuff.carryable.CarryableStorageManager;
import dev.thource.runelite.dudewheresmystuff.coins.CoinsStorageManager;
import dev.thource.runelite.dudewheresmystuff.death.DeathStorageManager;
import dev.thource.runelite.dudewheresmystuff.minigames.MinigamesStorageManager;
import dev.thource.runelite.dudewheresmystuff.world.WorldStorageManager;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.vars.AccountType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = "Dude, Where's My Stuff?")
public class DudeWheresMyStuffPlugin extends Plugin {

  @Inject
  @Named("developerMode")
  boolean developerMode;
  private DudeWheresMyStuffPanelContainer panelContainer;
  @Inject
  private ClientToolbar clientToolbar;
  @Inject
  private Client client;
  @Inject
  private ClientThread clientThread;
  @Inject
  private ItemManager itemManager;
  @Inject
  private DudeWheresMyStuffConfig config;
  @Inject
  private ConfigManager configManager;
  @Inject
  private DeathStorageManager deathStorageManager;
  @Inject
  private CoinsStorageManager coinsStorageManager;
  @Inject
  private CarryableStorageManager carryableStorageManager;
  @Inject
  private WorldStorageManager worldStorageManager;
  @Inject
  private MinigamesStorageManager minigamesStorageManager;
  private StorageManagerManager storageManagerManager;
  @Inject
  private DeathStorageManager previewDeathStorageManager;
  @Inject
  private CoinsStorageManager previewCoinsStorageManager;
  @Inject
  private CarryableStorageManager previewCarryableStorageManager;
  @Inject
  private WorldStorageManager previewWorldStorageManager;
  @Inject
  private MinigamesStorageManager previewMinigamesStorageManager;
  private StorageManagerManager previewStorageManagerManager;
  private NavigationButton navButton;

  private ClientState clientState = ClientState.LOGGED_OUT;
  private boolean pluginStartedAlreadyLoggedIn;
  private String previewProfileKey;

  @Override
  protected void startUp() {
    if (panelContainer == null) {
      deathStorageManager.setCarryableStorageManager(carryableStorageManager);
      deathStorageManager.setCoinsStorageManager(coinsStorageManager);
      storageManagerManager = new StorageManagerManager(this, carryableStorageManager,
          coinsStorageManager, deathStorageManager, minigamesStorageManager, worldStorageManager);

      previewDeathStorageManager.setCarryableStorageManager(previewCarryableStorageManager);
      previewDeathStorageManager.setCoinsStorageManager(previewCoinsStorageManager);
      previewStorageManagerManager = new StorageManagerManager(this, previewCarryableStorageManager,
          previewCoinsStorageManager, previewDeathStorageManager, previewMinigamesStorageManager,
          previewWorldStorageManager);

      panelContainer = new DudeWheresMyStuffPanelContainer(
          new DudeWheresMyStuffPanel(this, config, itemManager, configManager,
              storageManagerManager, false, client),
          new DudeWheresMyStuffPanel(this, config, itemManager, configManager,
              previewStorageManagerManager, true, client));

      final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

      navButton = NavigationButton.builder().tooltip("Dude, Where's My Stuff?").icon(icon)
          .panel(panelContainer).priority(4).build();
    }

    reset(true);

    clientToolbar.addNavigation(navButton);

    if (client.getGameState() == GameState.LOGGED_IN) {
      clientState = ClientState.LOGGING_IN;
      pluginStartedAlreadyLoggedIn = true;
    } else if (client.getGameState() == GameState.LOGGING_IN) {
      clientState = ClientState.LOGGING_IN;
    }
  }

  private void reset(boolean fullReset) {
    clientState = ClientState.LOGGED_OUT;

    storageManagerManager.reset();
    if (fullReset) {
      panelContainer.reset();
    } else {
      panelContainer.panel.reset();
    }
  }

  @Override
  protected void shutDown() {
    clientToolbar.removeNavigation(navButton);
  }

  @Subscribe
  public void onActorDeath(ActorDeath actorDeath) {
    storageManagerManager.onActorDeath(actorDeath);
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged gameStateChanged) {
    storageManagerManager.onGameStateChanged(gameStateChanged);

    if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
      reset(false);
    } else if (gameStateChanged.getGameState() == GameState.LOGGING_IN) {
      clientState = ClientState.LOGGING_IN;
    } else if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
      if (clientState != ClientState.LOGGING_IN) {
        return;
      }

      storageManagerManager.load();
      panelContainer.panel.update();
    }
  }

  @Subscribe
  private void onPlayerChanged(PlayerChanged ev) {
    if (ev.getPlayer() != client.getLocalPlayer()) {
      return;
    }

    panelContainer.panel.setDisplayName(ev.getPlayer().getName());
    panelContainer.panel.update();
  }

  @Subscribe
  public void onGameTick(GameTick gameTick) {
    if (clientState == ClientState.LOGGED_OUT) {
      return;
    }

    if (clientState == ClientState.LOGGING_IN) {
      boolean isMember = client.getVar(VarClientInt.MEMBERSHIP_STATUS) == 1;
      AccountType accountType = client.getAccountType();
      String displayName = Objects.requireNonNull(client.getLocalPlayer()).getName();

      configManager.setRSProfileConfiguration(DudeWheresMyStuffConfig.CONFIG_GROUP, "isMember",
          isMember);
      configManager.setRSProfileConfiguration(DudeWheresMyStuffConfig.CONFIG_GROUP, "accountType",
          accountType.ordinal());

      panelContainer.panel.logIn(isMember, accountType, displayName);
      clientState = ClientState.LOGGED_IN;

      if (pluginStartedAlreadyLoggedIn) {
        storageManagerManager.load();

        for (ItemContainer itemContainer : client.getItemContainers()) {
          onItemContainerChanged(new ItemContainerChanged(itemContainer.getId(), itemContainer));
        }

        onVarbitChanged(new VarbitChanged());

        pluginStartedAlreadyLoggedIn = false;
      }

      panelContainer.panel.update();

      storageManagerManager.save();

      return;
    }

    if (storageManagerManager.onGameTick()) {
      panelContainer.panel.update();
      return;
    }

    panelContainer.softUpdate();
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
    if (clientState == ClientState.LOGGED_OUT) {
      return;
    }

    if (storageManagerManager.onWidgetLoaded(widgetLoaded)) {
      panelContainer.panel.update();
    }
  }

  @Subscribe
  public void onWidgetClosed(WidgetClosed widgetClosed) {
    if (clientState == ClientState.LOGGED_OUT) {
      return;
    }

    if (storageManagerManager.onWidgetClosed(widgetClosed)) {
      panelContainer.panel.update();
    }
  }

  @Subscribe
  public void onVarbitChanged(VarbitChanged varbitChanged) {
    if (clientState == ClientState.LOGGED_OUT) {
      return;
    }

    if (storageManagerManager.onVarbitChanged()) {
      panelContainer.panel.update();
    }
  }

  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged itemContainerChanged) {
    if (clientState == ClientState.LOGGED_OUT) {
      return;
    }

    if (storageManagerManager.onItemContainerChanged(itemContainerChanged)) {
      panelContainer.panel.update();
    }
  }

  @Subscribe
  public void onItemDespawned(ItemDespawned itemDespawned) {
    if (clientState == ClientState.LOGGED_OUT) {
      return;
    }

    if (storageManagerManager.onItemDespawned(itemDespawned)) {
      panelContainer.panel.update();
    }
  }

  @Provides
  DudeWheresMyStuffConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(DudeWheresMyStuffConfig.class);
  }

  void disablePreviewMode(boolean deleteData) {
    previewStorageManagerManager.reset();

    if (deleteData) {
      configManager.getRSProfileConfigurationKeys(DudeWheresMyStuffConfig.CONFIG_GROUP,
          previewProfileKey, "").forEach(
          key -> configManager.unsetConfiguration(DudeWheresMyStuffConfig.CONFIG_GROUP,
              previewProfileKey, key));
    }

    panelContainer.previewPanel.logOut();

    panelContainer.disablePreviewMode();
    this.previewProfileKey = null;
  }

  void enablePreviewMode(String profileKey, String displayName) {
    this.previewProfileKey = profileKey;

    previewDeathStorageManager.setStartPlayedMinutes(
        configManager.getConfiguration(DudeWheresMyStuffConfig.CONFIG_GROUP, profileKey,
            "minutesPlayed", int.class));
    clientThread.invoke(() -> {
      previewStorageManagerManager.load(profileKey);

      panelContainer.previewPanel.logIn(
          configManager.getConfiguration(DudeWheresMyStuffConfig.CONFIG_GROUP, profileKey,
              "isMember", boolean.class), AccountType.values()[(int) configManager.getConfiguration(
              DudeWheresMyStuffConfig.CONFIG_GROUP, profileKey, "accountType", int.class)],
          displayName);

      panelContainer.enablePreviewMode();
    });
  }

  public boolean isPreviewModeEnabled() {
    return this.previewProfileKey != null;
  }

  public ClientState getClientState() {
    return clientState;
  }

  public boolean isDeveloperModeEnabled() {
    return developerMode;
  }
}
