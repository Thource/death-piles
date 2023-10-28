package dev.thource.runelite.dudewheresmystuff.death;

import dev.thource.runelite.dudewheresmystuff.DudeWheresMyStuffPlugin;
import dev.thource.runelite.dudewheresmystuff.DurationFormatter;
import dev.thource.runelite.dudewheresmystuff.ItemStack;
import dev.thource.runelite.dudewheresmystuff.Region;
import dev.thource.runelite.dudewheresmystuff.SaveFieldFormatter;
import dev.thource.runelite.dudewheresmystuff.SaveFieldLoader;
import dev.thource.runelite.dudewheresmystuff.StorageManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.ImageUtil;

/** Deathpile is responsible for tracking the player's deathpiled items. */
@Getter
@Slf4j
public class Deathpile extends DeathStorage {

  private static final ImageIcon WARNING_ICON =
      new ImageIcon(ImageUtil.loadImageResource(DudeWheresMyStuffPlugin.class, "warning.png"));
  private final DeathStorageManager deathStorageManager;
  @Setter protected DeathWorldMapPoint worldMapPoint;
  private WorldPoint worldPoint;
  private boolean useAccountPlayTime;
  // when useAccountPlayTime is true, expiryTime is the account played minutes that the deathpile
  // will expire at.
  // when useAccountPlayTime is false, expiryTime is the amount of ticks left until
  // the deathpile expires, ticking down only while the player is logged in.
  private int expiryTime;
  private long expiredAt = -1L;
  @Getter private Color color = Color.WHITE;

  Deathpile(
      DudeWheresMyStuffPlugin plugin,
      boolean useAccountPlayTime,
      int expiryTime,
      WorldPoint worldPoint,
      DeathStorageManager deathStorageManager,
      List<ItemStack> deathItems) {
    super(DeathStorageType.DEATHPILE, plugin);
    this.useAccountPlayTime = useAccountPlayTime;
    this.expiryTime = expiryTime;
    this.worldPoint = worldPoint;
    this.deathStorageManager = deathStorageManager;
    this.items.addAll(deathItems);
    this.color = generateColor();
  }

  static Deathpile load(DudeWheresMyStuffPlugin plugin, DeathStorageManager deathStorageManager,
      String profileKey, String uuid) {
    Deathpile deathpile = new Deathpile(
        plugin,
        true,
        0,
        null,
        deathStorageManager,
        new ArrayList<>()
    );

    deathpile.uuid = UUID.fromString(uuid);
    deathpile.load(deathStorageManager.getConfigManager(), deathStorageManager.getConfigKey(),
        profileKey);

    return deathpile;
  }

  private Color generateColor() {
    if (worldPoint == null) {
      return Color.WHITE;
    }

    Random rand = new Random(
        worldPoint.getX() * 200L + worldPoint.getY() * 354L + worldPoint.getPlane() * 42L);

    float saturation = 0.7f + rand.nextFloat() * 0.3f;
    float hue = rand.nextFloat();
    return Color.getHSBColor(hue, saturation, 0.8f);
  }

  @Override
  protected ArrayList<String> getSaveValues() {
    ArrayList<String> saveValues = super.getSaveValues();

    saveValues.add(SaveFieldFormatter.format(worldPoint));
    saveValues.add(SaveFieldFormatter.format(useAccountPlayTime));
    saveValues.add(SaveFieldFormatter.format(expiryTime));
    saveValues.add(SaveFieldFormatter.format(expiredAt));

    return saveValues;
  }

  @Override
  protected void loadValues(ArrayList<String> values) {
    super.loadValues(values);

    worldPoint = SaveFieldLoader.loadWorldPoint(values, worldPoint);
    useAccountPlayTime = SaveFieldLoader.loadBoolean(values, useAccountPlayTime);
    expiryTime = SaveFieldLoader.loadInt(values, expiryTime);
    expiredAt = SaveFieldLoader.loadLong(values, expiredAt);
    color = generateColor();
  }

  @Override
  protected void createStoragePanel(StorageManager<?, ?> storageManager) {
    super.createStoragePanel(storageManager);
    assert storagePanel != null;

    Region region = Region.get(worldPoint.getRegionID());
    if (region == null) {
      storagePanel.setSubTitle("Unknown");
    } else {
      storagePanel.setSubTitle(region.getName());
    }

    JLabel footerLabel = storagePanel.getFooterLabel();
    if (hasExpired()) {
      if (!useAccountPlayTime) {
        footerLabel.setIconTextGap(66);
        footerLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        footerLabel.setIcon(WARNING_ICON);
        footerLabel.setToolTipText(
            "This deathpile is using tick-based tracking, which means that the timer could be out "
                + "of sync. To use the more accurate play time based timers, enable cross-client "
                + "timers in the plugin settings.");
      } else if (deathStorageManager.getStartPlayedMinutes() <= 0) {
        footerLabel.setToolTipText(
            "This deathpile is using play time based tracking, but the plugin doesn't know what "
                + "your current play time is. To update your play time, swap the quest interface "
                + "to the \"Character summary\" tab (brown star).");
      }
    }

    createComponentPopupMenu(storageManager);
  }

  @Override
  protected void createComponentPopupMenu(StorageManager<?, ?> storageManager) {
    if (storagePanel == null) {
      return;
    }

    final JPopupMenu popupMenu = new JPopupMenu();
    popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
    storagePanel.setComponentPopupMenu(popupMenu);

    final JMenuItem deleteDeathpile = new JMenuItem("Delete Deathpile");
    deleteDeathpile.addActionListener(
        e -> {
          boolean confirmed = hasExpired() || DudeWheresMyStuffPlugin.getConfirmation(storagePanel,
              "Are you sure you want to delete this deathpile?\nThis cannot be undone.",
              "Confirm deletion");

          if (confirmed) {
            deathStorageManager.getStorages().remove(this);
            deathStorageManager.refreshMapPoints();
            deathStorageManager.getStorageTabPanel().reorderStoragePanels();
            deleteData(deathStorageManager);
          }
        });
    popupMenu.add(deleteDeathpile);

    createDebugMenuOptions(storageManager, popupMenu);
  }

  private void createDebugMenuOptions(StorageManager<?, ?> storageManager, JPopupMenu popupMenu) {
    if (plugin.isDeveloperMode()) {
      JMenu debugMenu = new JMenu("Debug");
      popupMenu.add(debugMenu);

      JMenuItem setExpiresIn = new JMenuItem("Set expires in");
      debugMenu.add(setExpiresIn);
      setExpiresIn.addActionListener(
          e -> {
            int minutes = 0;
            try {
              minutes = Integer.parseInt(
                  JOptionPane.showInputDialog("Enter expiry in minutes from now"));
            } catch (NumberFormatException nfe) {
              // Do nothing
            }

            if (minutes <= 0) {
              return;
            }

            if (useAccountPlayTime) {
              expiryTime = deathStorageManager.getPlayedMinutes() + minutes;
            } else {
              expiryTime = minutes * 100;
            }
            expiredAt = -1L;
            softUpdate();
            storageManager.getStorageTabPanel().reorderStoragePanels();
          }
      );

      JMenuItem expire = new JMenuItem("Expire");
      debugMenu.add(expire);
      expire.addActionListener(
          e -> {
            expiredAt = -1L;
            expiryTime = 0;
            softUpdate();
            storageManager.getStorageTabPanel().reorderStoragePanels();
          }
      );
    }
  }

  @Override
  public boolean onGameTick() {
    if (expiredAt != -1L) {
      return false;
    }

    if (!useAccountPlayTime) {
      expiryTime--;
      if (expiryTime <= 0) {
        expiredAt = System.currentTimeMillis();

        SwingUtilities.invokeLater(() -> {
          if (storagePanel == null) {
            return;
          }

          JLabel footerLabel = storagePanel.getFooterLabel();
          footerLabel.setIcon(null);
          footerLabel.setToolTipText(null);
        });
      }

      return true;
    }

    if (deathStorageManager.getStartPlayedMinutes() > 0
        && deathStorageManager.getPlayedMinutes() >= expiryTime) {
      expiredAt = System.currentTimeMillis();

      return true;
    }

    return false;
  }

  @Override
  public void reset() {
    // deathpiles get removed instead of reset
  }

  String getExpireText() {
    if (expiredAt != -1L) {
      return "Expired " + DurationFormatter.format(System.currentTimeMillis() - expiredAt) + " ago";
    }

    if (useAccountPlayTime && deathStorageManager.getStartPlayedMinutes() <= 0) {
      return "Waiting for play time";
    }

    return "Expires in " + DurationFormatter.format(getExpiryMs() - System.currentTimeMillis());
  }

  /**
   * Returns a unix timestamp of the expiry.
   *
   * <p>If previewMode is true, this will change so that it is static when displayed.
   *
   * @return Unix timestamp of the expiry
   */
  public long getExpiryMs() {
    if (expiredAt != -1L) {
      return expiredAt;
    }

    if (!useAccountPlayTime) {
      return System.currentTimeMillis() + (expiryTime * 600L);
    }

    // We don't know the player's play time yet, so assume they're fresh for sorting purposes
    if (deathStorageManager.getStartPlayedMinutes() <= 0) {
      return System.currentTimeMillis() + 3_540_000;
    }

    int minutesLeft = expiryTime - deathStorageManager.getPlayedMinutes();
    if (deathStorageManager.isPreviewManager()) {
      return System.currentTimeMillis() + (minutesLeft * 60000L);
    }

    return System.currentTimeMillis()
        + (minutesLeft * 60000L)
        - ((System.currentTimeMillis() - deathStorageManager.startMs) % 60000);
  }

  public boolean hasExpired() {
    return getExpiryMs() < System.currentTimeMillis();
  }

  @Override
  public void softUpdate() {
    if (storagePanel == null) {
      return;
    }

    storagePanel.setFooterText(getExpireText());
  }

  @Override
  public boolean isWithdrawable() {
    return super.isWithdrawable() && !hasExpired();
  }
}
