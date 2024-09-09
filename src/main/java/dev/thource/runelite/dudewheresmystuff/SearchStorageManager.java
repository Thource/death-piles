package dev.thource.runelite.dudewheresmystuff;

class SearchStorageManager extends StorageManager<StorageType, Storage<StorageType>> {

  protected SearchStorageManager(DudeWheresMyStuffPlugin plugin) {
    super(plugin);
  }

  @Override
  public void load(String profileKey) {
    // No loading necessary
  }

  @Override
  public void save(String profileKey) {
    // No saving necessary
  }

  @Override
  public String getConfigKey() {
    return null;
  }
}
