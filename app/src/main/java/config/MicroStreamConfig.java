package config;

import model.BookmarkGroup;
import one.microstream.storage.embedded.types.EmbeddedStorage;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import persistence.RootData;
import service.IdGenerator;
import service.bookmark.BookmarkService;
import service.bookmark_group.BookmarkGroupRepository;
import service.bookmark_group.BookmarkGroupService;

import java.nio.file.Files;
import java.nio.file.Path;

public class MicroStreamConfig {

    private MicroStreamConfig() {
    }

    public static EmbeddedStorageManager start(Path dir, boolean reset) {
        try { Files.createDirectories(dir); } catch (Exception ignored) {}

        if(reset){
            deleteRecursivelyQuiet(dir);
        }

        EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        RootData root = (RootData) storage.root();
        if (root == null) {
            root = new RootData();
            storage.setRoot(root);
            storage.storeRoot();
        }
        return storage;
    }

    public static void createDefaultGroup(RootData root, BookmarkGroupService bookmarkGroupService) {
        if(root.groups().isEmpty()){
            bookmarkGroupService.createBookmarkGroup("기본");
        }
    }

    private static void deleteRecursivelyQuiet(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            try (var s = Files.walk(dir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
            }
        } catch (Exception ignored) {}
    }
}
