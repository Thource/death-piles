package dev.thource.runelite.dudewheresmystuff.minigames;

import dev.thource.runelite.dudewheresmystuff.DudeWheresMyStuffPlugin;
import dev.thource.runelite.dudewheresmystuff.ItemStack;
import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.api.Varbits;

/** Slayer is responsible for tracking the player's Slayer points. */
@Getter
public class Slayer extends MinigamesStorage {

  private final ItemStack points = new ItemStack(ItemID.SKULL, "Points", 0, 0, 0, true);

  Slayer(DudeWheresMyStuffPlugin plugin) {
    super(MinigamesStorageType.SLAYER, plugin);

    items.add(points);
  }

  @Override
  public boolean onVarbitChanged() {
    int newPoints = plugin.getClient().getVarbitValue(Varbits.SLAYER_POINTS);
    if (newPoints == points.getQuantity()) {
      return false;
    }

    points.setQuantity(newPoints);
    return true;
  }
}
