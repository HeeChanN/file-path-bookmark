import com.formdev.flatlaf.FlatLightLaf;
import config.AppConfig;

import config.MicroStreamConfig;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import persistence.BookmarkGroupMicroStreamRepository;
import persistence.BookmarkMicroStreamRepository;
import persistence.RootData;
import service.IdGenerator;
import service.bookmark.BookmarkRepository;
import service.bookmark.BookmarkService;
import service.bookmark_group.BookmarkGroupRepository;
import service.bookmark_group.BookmarkGroupService;
import ui.DevFrame;
import ui.MainFrame;
import ui.MainFrameV2;

import javax.swing.*;

public class App {
    public static void main(String[] args) {

        // 초기화 작업
        EmbeddedStorageManager storage = new AppConfig().getStorage();
        RootData root = (RootData) storage.root();
        BookmarkGroupRepository bookmarkGroupRepository = new BookmarkGroupMicroStreamRepository(root, storage);
        BookmarkRepository bookmarkRepository = new BookmarkMicroStreamRepository(root, storage);
        IdGenerator idGenerator = IdGenerator.fromExisting(
                root.groups(),
                root.groups().stream().flatMap(bookmarkGroup-> bookmarkGroup.getBookmarks().stream())
                        .toList()
        );
        BookmarkGroupService bookmarkGroupService = new BookmarkGroupService(bookmarkGroupRepository, idGenerator);
        BookmarkService bookmarkService = new BookmarkService(bookmarkRepository, bookmarkGroupService,idGenerator);



        MicroStreamConfig.createDefaultGroup(root,bookmarkGroupService);
        boolean dev = true;

        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 16);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);

        FlatLightLaf.setup(); // 다크모드는 FlatDarkLaf.setup()

        SwingUtilities.invokeLater(() -> {
            new MainFrameV2(bookmarkService,bookmarkGroupService).setVisible(true);
            if(dev){
                new DevFrame((RootData) storage.root(),storage).setVisible(true);
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            storage.shutdown();
        }));

//        System.out.println("=== STORAGE SNAPSHOT ===");
//        for (var g : root.groups()) {
//            System.out.println("- Group #" + g.getId() + " : " + g.getName());
//            for (var b : g.getBookmarks()) {
//                System.out.println("    * " + b.getId() + "  " + b.getDisplayName()
//                        + "  [" + b.getLinkPath() + "]");
//            }
//        }
    }
}
