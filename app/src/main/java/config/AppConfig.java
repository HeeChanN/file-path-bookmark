package config;

import one.microstream.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Path;

public final class AppConfig {
    public static final String APP_NAME = "FilePathBookmark";
    public static final Path STORE_DIR_PATH =  AppPaths.dataDir(APP_NAME);
    private final EmbeddedStorageManager storage;

    public AppConfig() {
        this.storage= MicroStreamConfig.start(STORE_DIR_PATH,false);
    }

    public EmbeddedStorageManager getStorage() {
        return storage;
    }
}
