package ui;

import model.Bookmark;
import model.BookmarkGroup;
import one.microstream.storage.types.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import persistence.RootData;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

// DevFrame.java
public class DevFrame extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(DevFrame.class);

    private final RootData root;
    private final StorageManager storage;

    private final JTextArea out = new JTextArea(24, 90);

    public DevFrame(RootData root, StorageManager storage) {
        super("Dev Inspector");
        this.root = root; this.storage = storage;

        out.setEditable(false);
        JButton dump = new JButton("로그 출력");
        dump.addActionListener(e -> {
            // 데이터가 많으면 EDT가 잠길 수 있으니 가볍게 백그라운드로
            new Thread(() -> dump(root), "dev-dump").start();
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(dump);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(out), BorderLayout.CENTER);

        setSize(900, 600);
        setLocationByPlatform(true);
    }

//    private String dump(RootData root) {
//        var sb = new StringBuilder();
//        var groups = root.groups();
//        sb.append("Storage Dir: ").append(storageDir).append('\n');
//        sb.append("Groups: ").append(groups.size()).append('\n');
//        for (var g : groups) {
//            sb.append("- [").append(g.getId()).append("] ").append(g.getName())
//                    .append(" (bookmarks=").append(g.getBookmarks().size()).append(")\n");
//            for (var b : g.getBookmarks()) {
//                sb.append("   * [").append(b.getId()).append("] ")
//                        .append(b.getDisplayName()).append(" -> ").append(b.getLinkPath()).append('\n');
//            }
//        }
//        return sb.toString();
//    }

    private void dump(RootData root) {
        var groups = root.groups();
        log.info("=== STORAGE SNAPSHOT === (groups={})", groups.size());
        for (BookmarkGroup g : groups) {
            var bms = g.getBookmarks(); // 여기서 lazy restore가 일어날 수 있음
            log.info("- Group [{}] {} (bookmarks={})", g.getId(), g.getName(), bms.size());
            for (Bookmark b : bms) {
                log.info("   * [{}] {} -> {}", b.getId(), b.getDisplayName(), b.getPath());
            }
        }
    }

//    private void exportJson() {
//        try {
//            var chooser = new JFileChooser();
//            chooser.setSelectedFile(new File("snapshot.json"));
//            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
//
//            var groups = new ArrayList<Map<String,Object>>();
//            for (var g : root.groups()) {
//                var bookmarks = new ArrayList<Map<String,Object>>();
//                for (var b : g.getBookmarks()) {
//                    bookmarks.add(Map.of(
//                            "id", b.getId(),
//                            "displayName", b.getDisplayName(),
//                            "linkPath", b.getLinkPath()
//                    ));
//                }
//                groups.add(Map.of(
//                        "id", g.getId(),
//                        "name", g.getName(),
//                        "bookmarks", bookmarks
//                ));
//            }
//            var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
//                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
//            mapper.writeValue(chooser.getSelectedFile(), groups);
//            JOptionPane.showMessageDialog(this, "Exported: " + chooser.getSelectedFile());
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
//                    "Error", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    private void resetStorage() {
//        int r = JOptionPane.showConfirmDialog(this,
//                "Delete ALL data under:\n" + storageDir + "\n\nThis will exit the app.",
//                "Confirm RESET", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
//        if (r != JOptionPane.OK_OPTION) return;
//
//        try {
//            storage.shutdown(); // 스토리지 닫고
//            // 디렉터리 삭제
//            try (var s = Files.walk(storageDir)) {
//                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
//                    try { Files.delete(p); } catch (Exception ignored) {}
//                });
//            }
//            JOptionPane.showMessageDialog(this, "Deleted. Restarting app...");
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        } finally {
//            System.exit(0);
//        }
}

