package infra.jna;


public class LinkService {
//    private final persistence.RootData root;
//    private final StorageManager storage;
////    private final GroupRepository groups;
////    private final BookmarkRepository bookmarks;
////    private final Ids ids;
////
////    private final ExecutorService persistExec = Executors.newSingleThreadExecutor(r -> {
////        var t = new Thread(r, "store-writer"); t.setDaemon(true); return t;
////    });
//    private final WindowsShellLink shell = new WindowsShellLink();
//    private final Path linkBaseDir;
//
//    public LinkService(RootData root, StorageManager storage) {
//        this.root = root; this.storage = storage;
//        this.groups = new GroupRepository(root, storage, persistExec);
//        this.bookmarks = new BookmarkRepository(root, storage, persistExec);
//        this.ids = Ids.fromExisting(root.groups(), root.bookmarks());
//        this.linkBaseDir = resolveLinkDir("MyApp");
//        try { Files.createDirectories(linkBaseDir); } catch (Exception ignored) {}
//    }
//
//    public GroupRepository groups() { return groups; }
//    public BookmarkRepository bookmarks() { return bookmarks; }
//
//    /** 그룹 내 북마크 추가(+ .lnk 생성) */
//    public Bookmark addBookmarkWithShortcut(long groupId, Path target, String displayName) throws Exception {
//        String name = (displayName == null || displayName.isBlank())
//                ? target.getFileName().toString() : displayName;
//        Path lnk = uniquify(linkBaseDir.resolve(sanitize(name) + ".lnk"));
//        shell.createShortcut(target, lnk, name);
//        return bookmarks.add(ids.nextBookmarkId(), groupId, name, lnk.toString());
//    }
//
//    /** .lnk 해석 → 현재 경로 */
//    public Optional<Path> resolveOrRebind(Component parent, Bookmark bm) {
//        try {
//            Path lnk = Path.of(bm.linkPath());
//            String resolved = shell.resolve(lnk, true, true);
//            if (resolved != null && !resolved.isBlank() && Files.exists(Path.of(resolved))) {
//                return Optional.of(Path.of(resolved));
//            }
//            // 재지정
//            Path picked = chooseFileOrDir(parent, "대상이 이동/삭제됨. 새 위치 선택");
//            if (picked == null) return Optional.empty();
//            shell.updateTarget(lnk, picked);
//            bm.setLinkPath(lnk.toString());
//            persistExec.execute(() -> storage.store(bm));
//            return Optional.of(picked);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Optional.empty();
//        }
//    }
//
//    // --- helpers ---
//    private static Path resolveLinkDir(String appName) {
//        String local = System.getenv("LOCALAPPDATA");
//        if (local == null || local.isBlank()) local = System.getProperty("user.home");
//        return Path.of(local, appName, "links");
//    }
//    private static String sanitize(String name) { return name.replaceAll("[\\\\/:*?\"<>|]", "_"); }
//    private static Path uniquify(Path p) {
//        if (!Files.exists(p)) return p;
//        String base = p.getFileName().toString();
//        if (base.toLowerCase().endsWith(".lnk")) base = base.substring(0, base.length()-4);
//        int i = 2; Path dir = p.getParent();
//        while (true) {
//            Path cand = dir.resolve(base + " (" + i++ + ").lnk");
//            if (!Files.exists(cand)) return cand;
//        }
//    }
//    public static Path chooseFileOrDir(Component parent, String title) {
//        var fc = new JFileChooser(); fc.setDialogTitle(title);
//        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
//        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile().toPath() : null;
//    }
//
//    @Override public void close() { persistExec.shutdown(); }
}

