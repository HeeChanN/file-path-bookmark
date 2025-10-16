package config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class AppPaths {

    private static String DEFAULT_WINDOW_DATA_DIR = System.getProperty("user.home") + "\\AppData\\Local";
    private static String DEFAULT_MAC_DATA_DIR = System.getProperty("user.home") + "/Library/Application Support";

    public static Path dataDir(String appName) {
        return osDataBase(appName);
    }

    private static Path osDataBase(String appName) {
        if (isWindows()) {
            String base = envOr("LOCALAPPDATA", DEFAULT_WINDOW_DATA_DIR);
            return Paths.get(base,appName,"store");
        } else if (isMac()) {
            return Path.of(DEFAULT_MAC_DATA_DIR, appName);
        }
        else{
            throw new RuntimeException("지원하지 않는 os");
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
