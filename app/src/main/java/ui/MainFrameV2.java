package ui;

import model.Bookmark;
import model.BookmarkGroup;
import model.BookmarkType;
import service.bookmark.BookmarkService;
import service.bookmark_group.BookmarkGroupService;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

public class MainFrameV2 extends JFrame {

    // Notion 라이트 테마 색상
    private static final Color NOTION_BG = new Color(247, 246, 243);
    private static final Color NOTION_TEXT = new Color(55, 53, 47);
    private static final Color NOTION_HOVER = new Color(232, 231, 227);
    private static final Color NOTION_BORDER = new Color(232, 231, 227);
    private static final Color NOTION_ACCENT = new Color(35, 131, 226);
    private static final Color NOTION_HINT = new Color(155, 154, 151);

    private final BookmarkService bookmarkService;
    private final BookmarkGroupService bookmarkGroupService;

    // 상단 툴바 (간소화)
    private final JButton addGroupBtn = new JButton("그룹 추가");
    private final JButton expandAllBtn = new JButton("모두 펼치기");
    private final JButton collapseAllBtn = new JButton("모두 접기");

    // 중앙 아코디언 컨테이너
    private final JPanel accordion = new JPanel();
    private JScrollPane scroll; // 스크롤 위치 보존용

    // 섹션 펼침/접힘 상태 보존
    private final Map<Long, Boolean> expandState = new HashMap<>();

    // 드래그 위치 하이라이트(GlassPane)
    private final HighlightGlass highlight = new HighlightGlass();

    // 상태바(스낵바 스타일)
    private final JPanel statusBar = new JPanel(new BorderLayout());
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton statusActionBtn = new JButton();
    private Timer statusTimer;
    private Runnable statusActionHandler;

    // 최근 삭제 항목(Undo용)
    private static final class DeletedBookmark {
        long groupId; String name; String path; int index;
        DeletedBookmark(long groupId, String name, String path, int index){
            this.groupId=groupId; this.name=name; this.path=path; this.index=index;
        }
    }
    private DeletedBookmark lastDeleted;

    // 환경설정 저장
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrameV2.class);

    public MainFrameV2(BookmarkService bookmarkService, BookmarkGroupService bookmarkGroupService) {
        super("Shortcut Manager");
        this.bookmarkService = Objects.requireNonNull(bookmarkService);
        this.bookmarkGroupService = Objects.requireNonNull(bookmarkGroupService);

        // ====== UI 기본 ======
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(NOTION_BG);
        loadWindowPrefs();

        // 상단 툴바
        var toolbar = buildToolbar();
        add(toolbar, BorderLayout.NORTH);

        // 중앙 아코디언 컨테이너
        accordion.setLayout(new BoxLayout(accordion, BoxLayout.Y_AXIS));
        accordion.setBackground(NOTION_BG);
        scroll = new JScrollPane(accordion);
        scroll.getViewport().setBackground(NOTION_BG);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // 그룹 DnD: 빈 공간(섹션 사이)에서도 작동하도록 아코디언에 Import 핸들러
        accordion.setTransferHandler(new GroupReorderImportHandler());

        // GlassPane 하이라이트
        getRootPane().setGlassPane(highlight);
        highlight.setVisible(false);

        // 하단 상태바
        statusBar.setBackground(NOTION_BG);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, getSeparatorColor()));
        statusActionBtn.setVisible(false);
        statusActionBtn.setFocusable(false);
        statusActionBtn.addActionListener(e -> { if(statusActionHandler!=null) statusActionHandler.run(); });
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(statusActionBtn, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        // 데이터 로드 → 섹션 구성
        rebuildAccordion();

        // 액션 바인딩(단축키는 제거)
        wireActions();

        // 종료시 환경 저장
        addWindowListener(new WindowAdapter(){ @Override public void windowClosing(WindowEvent e){ saveWindowPrefs(); } });
    }

    // 상단 툴바 (심플)
    private JComponent buildToolbar() {
        var bar = new JPanel(new GridBagLayout());
        bar.setBackground(NOTION_BG);
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;

        stylizeButton(addGroupBtn);
        stylizeButton(expandAllBtn);
        stylizeButton(collapseAllBtn);

        bar.add(addGroupBtn, gbc); gbc.gridx++;
        bar.add(expandAllBtn, gbc); gbc.gridx++;
        bar.add(collapseAllBtn, gbc);

        // 얇은 하단 보더를 위한 래퍼
        var wrapper = new JPanel(new BorderLayout());
        wrapper.add(bar, BorderLayout.CENTER);
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()));
        return wrapper;
    }

    private void wireActions() {
        addGroupBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "그룹명");
            if (name == null || name.isBlank()) return;
            try {
                bookmarkGroupService.createBookmarkGroup(name);
                setStatus("그룹이 추가되었습니다.");
                rebuildAccordion();
            } catch (RuntimeException ex) {
                showError("그룹 생성 실패: " + ex.getMessage());
            }
        });

        expandAllBtn.addActionListener(e -> setAllSectionsExpanded(true));
        collapseAllBtn.addActionListener(e -> setAllSectionsExpanded(false));
    }

    /** 전체 아코디언 섹션 재구성 */
    private void rebuildAccordion() {
        // 펼침 상태/스크롤 위치 백업
        for (Component c : accordion.getComponents()) {
            if (c instanceof GroupSection gs) expandState.put(gs.group.getId(), gs.toggle.isSelected());
        }
        Point viewPos = scroll.getViewport().getViewPosition();

        accordion.removeAll();

        List<BookmarkGroup> groups = bookmarkGroupService.getBookmarkGroups();
        // prefs에서 펼침 상태 로드
        loadExpandStateFromPrefs(groups);

        if (groups.isEmpty()) {
            accordion.add(emptyHint("그룹이 없습니다. [그룹 추가] 버튼을 눌러 시작하세요."));
        } else {
            for (int i = 0; i < groups.size(); i++) {
                BookmarkGroup g = groups.get(i);
                var section = new GroupSection(g);
                accordion.add(section);
            }
        }

        accordion.add(Box.createVerticalGlue());
        accordion.revalidate();
        accordion.repaint();

        // 스크롤 복원
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(viewPos));

        hideDropHighlight();
    }

    /** 모든 섹션 펼치기/접기 */
    private void setAllSectionsExpanded(boolean expanded) {
        for (Component c : accordion.getComponents()) if (c instanceof GroupSection) ((GroupSection) c).setExpanded(expanded);
    }

    /** 그룹 + 북마크 목록 한 묶음 (Notion 토글 스타일) */
    private final class GroupSection extends JPanel {
        private final BookmarkGroup group;
        private final JToggleButton toggle = new JToggleButton();
        private final JLabel title = new JLabel();
        private final JButton moreBtn = new JButton("⋯");
        private final JPanel header = new JPanel(new GridBagLayout());
        private final JPanel content = new JPanel();

        GroupSection(BookmarkGroup group) {
            super(new BorderLayout());
            this.group = group;

            // 헤더: 하단 보더만, 얇고 플랫
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()));
            header.setBackground(NOTION_BG);
            header.setOpaque(true);

            // DnD: 헤더 자체가 드래그 시작점 + Import 허용
            var headerDnD = new GroupHeaderTransferHandler(group.getId());
            header.setTransferHandler(headerDnD);
            var dragStarter = new MouseAdapter() {
                Point pressAt;
                @Override public void mousePressed(MouseEvent e) { pressAt = e.getPoint(); }
                @Override public void mouseDragged(MouseEvent e) {
                    if (pressAt != null && e.getPoint().distance(pressAt) > 6) {
                        header.getTransferHandler().exportAsDrag(header, e, TransferHandler.MOVE);
                        pressAt = null;
                    }
                }
            };
            header.addMouseListener(dragStarter);
            header.addMouseMotionListener(dragStarter);

            // 토글 아이콘
            toggle.setText("▸");
            toggle.setBorderPainted(false);
            toggle.setContentAreaFilled(false);
            toggle.setFocusPainted(false);
            toggle.setSelected(expandState.getOrDefault(group.getId(), Boolean.TRUE));
            updateToggleGlyph();

            // 그룹명 (더블클릭으로 이름 변경)
            title.setText(group.getName());
            title.setFont(title.getFont().deriveFont(Font.PLAIN, title.getFont().getSize2D()+1));
            title.setToolTipText("더블클릭하여 그룹명 변경");
            title.addMouseListener(new MouseAdapter(){
                @Override public void mouseClicked(MouseEvent e){
                    if (e.getClickCount()==2) {
                        String newName = JOptionPane.showInputDialog(MainFrameV2.this, "새 그룹명", group.getName());
                        if (newName == null || newName.isBlank() || newName.equals(group.getName())) return;
                        try { bookmarkGroupService.renameBookmarkGroup(group.getId(), newName); setStatus("그룹 이름이 변경되었습니다."); rebuildAccordion(); }
                        catch (RuntimeException ex) { showError("이름 변경 실패: " + ex.getMessage()); }
                    }
                }
            });

            // more 버튼(컨텍스트 메뉴, 작은 버튼)
            stylizeIconButton(moreBtn);
            moreBtn.setPreferredSize(new Dimension(24, 24));
            // More 버튼 호버 효과
            moreBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    moreBtn.setBackground(NOTION_HOVER);
                    moreBtn.setOpaque(true);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    moreBtn.setOpaque(false);
                }
            });
            var groupMenu = buildGroupPopupMenu();
            moreBtn.addActionListener(e -> groupMenu.show(moreBtn, 0, moreBtn.getHeight()));
            // 헤더 우클릭도 동일 메뉴
            header.addMouseListener(new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){ if (e.isPopupTrigger()) groupMenu.show(header, e.getX(), e.getY()); }
                @Override public void mouseReleased(MouseEvent e){ if (e.isPopupTrigger()) groupMenu.show(header, e.getX(), e.getY()); }
            });

            // 헤더 레이아웃: [▸][제목]...................................[⋯]
            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 12, 10, 12);
            gbc.gridx=0; gbc.gridy=0; gbc.anchor = GridBagConstraints.WEST;
            header.add(toggle, gbc);
            gbc.gridx=1; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
            header.add(title, gbc);
            gbc.gridx=2; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE;
            header.add(moreBtn, gbc);

            add(header, BorderLayout.NORTH);

            // 헤더 호버 효과
            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    header.setBackground(NOTION_HOVER);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    header.setBackground(NOTION_BG);
                }
            });

            // 콘텐츠(북마크 리스트) — Notion 스타일
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(4, 32, 8, 12)); // 명확한 계층 들여쓰기
            content.setOpaque(false);
            add(content, BorderLayout.CENTER);

            // 북마크 리스트 패널에도 Import 핸들러(빈 공간 드롭 + 파일/텍스트 드롭 + 크로스 그룹 이동)
            content.setTransferHandler(new BookmarkListImportHandler(group.getId(), content));

            // 최초 로드
            reloadBookmarks();

            // 동작
            toggle.addActionListener(e -> { updateToggleGlyph(); content.setVisible(toggle.isSelected()); expandState.put(group.getId(), toggle.isSelected()); revalidate(); });

            // 접힘/펼침 반영
            content.setVisible(toggle.isSelected());
        }

        private JPopupMenu buildGroupPopupMenu() {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem add = new JMenuItem("북마크 추가…");
            add.addActionListener(e -> promptAddBookmark());
            menu.add(add);

            JMenuItem rename = new JMenuItem("이름 변경…");
            rename.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(MainFrameV2.this, "새 그룹명", group.getName());
                if (newName == null || newName.isBlank() || newName.equals(group.getName())) return;
                try { bookmarkGroupService.renameBookmarkGroup(group.getId(), newName); setStatus("그룹 이름이 변경되었습니다."); rebuildAccordion(); }
                catch (RuntimeException ex) { showError("이름 변경 실패: " + ex.getMessage()); }
            });
            menu.add(rename);

            JMenuItem del = new JMenuItem("삭제…");
            del.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(MainFrameV2.this,
                        "그룹을 삭제하시겠습니까?\n" + group.getName(),
                        "확인", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.OK_OPTION) return;
                try { bookmarkGroupService.deleteBookmarkGroup(group.getId()); setStatus("그룹이 삭제되었습니다."); rebuildAccordion(); }
                catch (RuntimeException ex) { showError("삭제 실패: " + ex.getMessage()); }
            });
            menu.add(del);
            return menu;
        }

        void setExpanded(boolean expanded) { toggle.setSelected(expanded); updateToggleGlyph(); content.setVisible(expanded); expandState.put(group.getId(), expanded); revalidate(); }
        private void updateToggleGlyph() { toggle.setText(toggle.isSelected() ? "▾" : "▸"); }

        void promptAddBookmark() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("파일 또는 폴더 선택");
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            int res = fc.showOpenDialog(MainFrameV2.this);
            if (res != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile(); String defaultName = f.getName();
            String displayName = JOptionPane.showInputDialog(MainFrameV2.this, "표시 이름(생략 가능)", defaultName); if (displayName == null) return;
            try { bookmarkService.createBookmark(group.getId(), displayName, f.getAbsolutePath()); setStatus("북마크가 추가되었습니다."); reloadBookmarks(); }
            catch (RuntimeException ex) { showError("북마크 생성 실패: " + ex.getMessage()); }
        }

        /** 이 섹션의 북마크 목록을 다시 그림 */
        private void reloadBookmarks() {
            content.removeAll();
            List<Bookmark> bookmarks = group.getBookmarks();
            if (bookmarks == null || bookmarks.isEmpty()) {
                content.add(emptyHint("이 그룹이 비어 있습니다. 파일을 드래그하여 추가하세요."));
            } else {
                int count = 0;
                for (Bookmark b : bookmarks) {
                    content.add(new BookmarkRow(group.getId(), b, content));
                    count++;
                }
                if (count == 0) content.add(emptyHint("항목이 없습니다."));
            }
            content.revalidate(); content.repaint();
        }
    }

    /** 북마크 한 줄 (Compact Row: 아이콘 + 이름) + ⋯ 메뉴 + DnD */
    private final class BookmarkRow extends JPanel {
        private final long groupId; private final Bookmark bm; private final JPanel listPanel;
        private final JButton moreBtn = new JButton("⋯");
        private final JLabel nameLabel;

        BookmarkRow(long groupId, Bookmark bm, JPanel listPanel) {
            super(new GridBagLayout());
            this.groupId = groupId; this.bm = bm; this.listPanel = listPanel;

            setOpaque(true);
            setBackground(NOTION_BG);
            // Notion 스타일: 여유있는 패딩
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));

            // 아이콘 + 한 줄 라벨(툴팁은 전체 경로)
            Icon icon = iconForBookmark(bm);
            nameLabel = new JLabel(esc(bm.getDisplayName()), icon, SwingConstants.LEFT);
            nameLabel.setToolTipText(bm.getPath());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN));

            // 더블클릭 = 열기
            addMouseListener(new MouseAdapter(){ @Override public void mouseClicked(MouseEvent e){ if (e.getClickCount()==2 && SwingUtilities.isLeftMouseButton(e)) { openBookmarkPath(bm.getPath()); } }});

            // DnD: 행 전체에서 드래그 시작 가능
            var rowDnD = new BookmarkRowTransferHandler(groupId, bm.getId(), listPanel);
            setTransferHandler(rowDnD);
            addMouseListener(new MouseAdapter(){
                Point pressAt;
                @Override public void mousePressed(MouseEvent e){ pressAt = e.getPoint(); }
                @Override public void mouseDragged(MouseEvent e){
                    if (pressAt != null && e.getPoint().distance(pressAt) > 6) {
                        getTransferHandler().exportAsDrag(BookmarkRow.this, e, TransferHandler.MOVE);
                        pressAt = null;
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter(){
                Point pressAt;
                @Override public void mouseDragged(MouseEvent e){
                    if (pressAt == null) pressAt = new Point(0,0);
                    if (e.getPoint().distance(pressAt) > 6) {
                        getTransferHandler().exportAsDrag(BookmarkRow.this, e, TransferHandler.MOVE);
                    }
                }
            });

            // ⋯ 메뉴 (작은 버튼)
            stylizeIconButton(moreBtn);
            moreBtn.setPreferredSize(new Dimension(24, 24));
            // More 버튼 호버 효과
            moreBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    moreBtn.setBackground(NOTION_HOVER);
                    moreBtn.setOpaque(true);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    moreBtn.setOpaque(false);
                }
            });
            var rowMenu = buildRowPopupMenu();
            moreBtn.addActionListener(e -> rowMenu.show(moreBtn, 0, moreBtn.getHeight()));
            addMouseListener(new MouseAdapter(){ @Override public void mousePressed(MouseEvent e){ if (e.isPopupTrigger()) rowMenu.show(BookmarkRow.this, e.getX(), e.getY()); } @Override public void mouseReleased(MouseEvent e){ if (e.isPopupTrigger()) rowMenu.show(BookmarkRow.this, e.getX(), e.getY()); }});

            // 레이아웃
            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 6, 0, 6);
            gbc.gridx=0; gbc.gridy=0; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor=GridBagConstraints.WEST;
            add(nameLabel, gbc);
            gbc.gridx=1; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE; gbc.anchor=GridBagConstraints.EAST;
            add(moreBtn, gbc);

            // Notion 스타일 호버 효과
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(NOTION_HOVER);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(NOTION_BG);
                }
            });
        }

        private JPopupMenu buildRowPopupMenu(){
            JPopupMenu menu = new JPopupMenu();
            JMenuItem open = new JMenuItem("열기");
            open.addActionListener(e -> openBookmarkPath(bm.getPath()));
            menu.add(open);

            JMenuItem openFolder = new JMenuItem("포함 폴더 열기");
            openFolder.addActionListener(e -> {
                try {
                    File f = new File(bm.getPath());
                    File dir = f.isDirectory() ? f : f.getParentFile();
                    if (dir != null) Desktop.getDesktop().open(dir);
                } catch (Exception ex) { showError("폴더를 열 수 없습니다: "+ex.getMessage()); }
            });
            menu.add(openFolder);

            JMenuItem copy = new JMenuItem("경로 복사");
            copy.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(bm.getPath()), null);
                setStatus("경로를 복사했습니다.");
            });
            menu.add(copy);

            JMenuItem rename = new JMenuItem("이름 변경…");
            rename.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(MainFrameV2.this, "표시 이름", bm.getDisplayName());
                if (newName == null) return;
                try { bookmarkService.updateBookmark(bm.getId(), newName, bm.getPath()); setStatus("이름이 변경되었습니다."); rebuildAccordion(); }
                catch (RuntimeException ex) { showError("수정 실패: " + ex.getMessage()); }
            });
            menu.add(rename);

            JMenuItem changePath = new JMenuItem("경로 변경…");
            changePath.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("새 경로 선택");
                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                int res = fc.showOpenDialog(MainFrameV2.this);
                if (res != JFileChooser.APPROVE_OPTION) return;
                File f = fc.getSelectedFile();
                try { bookmarkService.updateBookmark(bm.getId(), bm.getDisplayName(), f.getAbsolutePath()); setStatus("경로가 변경되었습니다."); rebuildAccordion(); }
                catch (RuntimeException ex) { showError("수정 실패: " + ex.getMessage()); }
            });
            menu.add(changePath);

            JMenuItem del = new JMenuItem("삭제…");
            del.addActionListener(e -> deleteBookmark());
            menu.add(del);

            return menu;
        }

        private void deleteBookmark(){
            int r = JOptionPane.showConfirmDialog(MainFrameV2.this,
                    "삭제하시겠습니까?\n" + bm.getDisplayName(),
                    "확인", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            try {
                int idx = findBookmarkIndexById(listPanel, bm.getId());
                lastDeleted = new DeletedBookmark(groupId, bm.getDisplayName(), bm.getPath(), Math.max(idx, 0));
                bookmarkService.remove(bm.getId());
                setStatusWithAction("북마크가 삭제되었습니다.", "되돌리기", () -> {
                    try {
                        if (lastDeleted == null) return;
                        bookmarkService.createBookmark(lastDeleted.groupId, lastDeleted.name, lastDeleted.path);
                        // 위치 복원: 새로 생성된 북마크를 target index로 이동
                        List<BookmarkGroup> groups = bookmarkGroupService.getBookmarkGroups();
                        for (BookmarkGroup g : groups) if (g.getId()==lastDeleted.groupId) {
                            List<Bookmark> lb = g.getBookmarks(); if (lb!=null && !lb.isEmpty()) {
                                Bookmark newest = lb.get(lb.size()-1);
                                int to = Math.min(lastDeleted.index, lb.size()-1);
                                bookmarkService.reorderBookmark(lastDeleted.groupId, newest.getId(), to);
                            }
                        }
                        lastDeleted = null; rebuildAccordion(); setStatus("복구했습니다.");
                    } catch(Exception ex){ showError("복구 실패: "+ex.getMessage()); }
                });
                Container parent = getParent();
                parent.remove(BookmarkRow.this);
                parent.revalidate();
                parent.repaint();
            } catch (RuntimeException ex) { showError("이미 삭제되었거나 존재하지 않습니다."); }
        }
    }

    // =================== Group DnD ===================

    /** 그룹 헤더: Export + Import(헤더 위 드롭 허용) */
    private final class GroupHeaderTransferHandler extends TransferHandler {
        private final long groupId;
        GroupHeaderTransferHandler(long groupId) { this.groupId = groupId; }

        // Export
        @Override protected Transferable createTransferable(JComponent c) { return new StringSelection(String.valueOf(groupId)); }
        @Override public int getSourceActions(JComponent c) { return MOVE; }
        @Override protected void exportDone(JComponent source, Transferable data, int action) { hideDropHighlight(); }

        // Import (그룹 DnD만 허용 + 파일/텍스트 드롭으로 빠른 추가)
        @Override public boolean canImport(TransferSupport s) {
            if (!s.isDrop()) { hideDropHighlight(); return false; }
            if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { hideDropHighlight(); return true; }
            if (s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                // 문자열: 내부 그룹ID or 북마크ID or 외부 텍스트
                hideDropHighlight(); return true;
            }
            hideDropHighlight(); return false;
        }

        @Override public boolean importData(TransferSupport s) {
            if (!s.isDrop()) return false;
            try {
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null) {
                        for (File f : files) bookmarkService.createBookmark(groupId, f.getName(), f.getAbsolutePath());
                        setStatus(files.size()+"개 항목을 추가했습니다."); rebuildAccordion(); return true;
                    }
                    return false;
                }
                String payload = getStringData(s);
                if (payload == null) return false;

                if (payload.startsWith("B:")) { // 다른 그룹에서 북마크 이동
                    String[] parts = payload.split(":");
                    long movedBookmarkId = parseLongSafe(parts[2], -1);
                    setUiBusy(true);
                    new SwingWorker<Void, Void>() {
                        @Override protected Void doInBackground() {
                            // 서비스에 moveBookmark(id, toGroup, toIndex=0) 있다고 가정
                            try { bookmarkService.moveBookmark(movedBookmarkId, groupId, 0); } catch (Throwable t) { /* 없으면 무시 */ }
                            return null;
                        }
                        @Override protected void done() {
                            setUiBusy(false); setStatus("북마크를 다른 그룹으로 이동했습니다."); rebuildAccordion();
                        }
                    }.execute();
                    return true;
                } else {
                    // 외부 텍스트 줄단위로 경로 처리
                    for (String line : payload.split("\\R")) {
                        if (line.isBlank()) continue;
                        File f = new File(line.trim());
                        if (f.exists()) bookmarkService.createBookmark(groupId, f.getName(), f.getAbsolutePath());
                    }
                    setStatus("텍스트 드롭으로 항목을 추가했습니다."); rebuildAccordion(); return true;
                }
            } catch (Exception ex) {
                setUiBusy(false); hideDropHighlight(); showError("작업 실패: " + ex.getMessage()); return false;
            }
        }
    }

    /** 아코디언(빈 공간) 위 드롭 시 그룹 재정렬 허용 */
    private final class GroupReorderImportHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport s) {
            if (!(s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor))) { hideDropHighlight(); return false; }
            String payload = getStringData(s); if (payload == null || payload.startsWith("B:")) { hideDropHighlight(); return false; }
            Point p = s.getDropLocation().getDropPoint(); int boundaryY = computeGroupBoundaryY(p); showDropHighlightAt(accordion, boundaryY); return true;
        }
        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                Long movedId = Long.parseLong(getStringData(s)); Point p = s.getDropLocation().getDropPoint();
                int fromIndex = findGroupIndexById(movedId); if (fromIndex < 0) { hideDropHighlight(); return false; }
                int toIndex = computeGroupTargetIndex(p); if (toIndex == fromIndex) { hideDropHighlight(); return false; } if (toIndex > fromIndex) toIndex--;
                setUiBusy(true);
                int finalIndex = toIndex;
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() { bookmarkGroupService.reorderBookmarkGroups(movedId, finalIndex); return null; }
                    @Override protected void done() { setUiBusy(false); hideDropHighlight(); setStatus("그룹 순서가 변경되었습니다."); rebuildAccordion(); }
                }.execute();
                return true;
            } catch (Exception ex) {
                setUiBusy(false); hideDropHighlight(); showError("그룹 순서 변경 실패: " + ex.getMessage()); return false;
            }
        }
    }

    private int computeGroupBoundaryY(Point dropPointInAccordion) {
        Rectangle lastRect = null;
        for (Component c : accordion.getComponents()) if (c instanceof GroupSection gs) { Rectangle r = gs.getBounds(); int midY = r.y + r.height / 2; if (dropPointInAccordion.y < midY) return r.y; lastRect = r; }
        return lastRect != null ? lastRect.y + lastRect.height : 0;
    }

    private int computeGroupTargetIndex(Point dropPointInAccordion) {
        int idx = 0; for (Component c : accordion.getComponents()) if (c instanceof GroupSection gs) { Rectangle r = gs.getBounds(); int midY = r.y + r.height / 2; if (dropPointInAccordion.y < midY) return idx; idx++; } return idx;
    }

    private int findGroupIndexById(Long id) { int idx = 0; for (Component c : accordion.getComponents()) if (c instanceof GroupSection gs) { if (Objects.equals(gs.group.getId(), id)) return idx; idx++; } return -1; }

    // =================== Bookmark DnD ===================

    /** 북마크 행: Export + Import(행/사이/행 위 드롭) + 파일/텍스트 드롭 + **크로스 그룹 이동** */
    private final class BookmarkRowTransferHandler extends TransferHandler {
        private final long groupId; private final long bookmarkId; private final JPanel listPanel;
        BookmarkRowTransferHandler(long groupId, long bookmarkId, JPanel listPanel) { this.groupId = groupId; this.bookmarkId = bookmarkId; this.listPanel = listPanel; }
        @Override protected Transferable createTransferable(JComponent c) { return new StringSelection("B:" + groupId + ":" + bookmarkId); }
        @Override public int getSourceActions(JComponent c) { return MOVE; }
        @Override protected void exportDone(JComponent source, Transferable data, int action) { hideDropHighlight(); }
        @Override public boolean canImport(TransferSupport s) {
            if (!s.isDrop()) { hideDropHighlight(); return false; }
            if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { hideDropHighlight(); return true; }
            if (s.isDataFlavorSupported(DataFlavor.stringFlavor)) return true;
            hideDropHighlight(); return false;
        }
        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null) {
                        for (File f : files) bookmarkService.createBookmark(groupId, f.getName(), f.getAbsolutePath());
                        setStatus(files.size()+"개 항목을 추가했습니다."); rebuildAccordion(); return true;
                    }
                    return false;
                }
                String payload = getStringData(s); if (payload == null) return false;

                if (payload.startsWith("B:")) {
                    String[] parts = payload.split(":");
                    long fromGroup = parseLongSafe(parts[1], -1);
                    long movedBookmarkId = parseLongSafe(parts[2], -1);

                    Point pInComp = s.getDropLocation().getDropPoint();
                    Point pInList = SwingUtilities.convertPoint((Component)s.getComponent(), pInComp, listPanel);

                    int fromIndex = findBookmarkIndexById(listPanel, movedBookmarkId);
                    int toIndex = computeBookmarkTargetIndex(listPanel, pInList);
                    if (toIndex == fromIndex && fromGroup == groupId) { hideDropHighlight(); return false; }
                    if (toIndex > fromIndex && fromGroup == groupId) toIndex--;

                    setUiBusy(true);
                    int finalIndex = toIndex;
                    new SwingWorker<Void, Void>() {
                        @Override protected Void doInBackground() {
                            if (fromGroup == groupId) {
                                bookmarkService.reorderBookmark(groupId, movedBookmarkId, finalIndex);
                            } else {
                                try { bookmarkService.moveBookmark(movedBookmarkId, groupId, finalIndex); } catch (Throwable t) {
                                    // moveBookmark 미구현 환경을 위해 폴백: 대상 그룹 끝으로 넣고 reorder
                                    Bookmark moved = null;
                                    List<BookmarkGroup> groups = bookmarkGroupService.getBookmarkGroups();
                                    outer: for (BookmarkGroup g : groups) {
                                        List<Bookmark> lb = g.getBookmarks();
                                        if (lb == null) continue;
                                        for (Bookmark b : lb) if (b.getId() == movedBookmarkId) { moved = b; break outer; }
                                    }
                                    if (moved != null) {
                                        bookmarkService.createBookmark(groupId, moved.getDisplayName(), moved.getPath());
                                        // 원본 삭제는 서비스 정책에 따름
                                    }
                                }
                            }
                            return null;
                        }
                        @Override protected void done() {
                            setUiBusy(false); hideDropHighlight(); setStatus("북마크 위치가 변경되었습니다."); rebuildAccordion();
                        }
                    }.execute();
                    return true;
                } else {
                    // 외부 텍스트 줄단위로 경로 처리
                    for (String line : payload.split("\\R")) {
                        if (line.isBlank()) continue;
                        File f = new File(line.trim());
                        if (f.exists()) bookmarkService.createBookmark(groupId, f.getName(), f.getAbsolutePath());
                    }
                    setStatus("텍스트 드롭으로 항목을 추가했습니다."); rebuildAccordion(); return true;
                }
            } catch (Exception ex) {
                setUiBusy(false); hideDropHighlight(); showError("작업 실패: " + ex.getMessage()); return false;
            }
        }
    }

    /** 리스트 패널(빈 공간) Import 핸들러 */
    private final class BookmarkListImportHandler extends TransferHandler {
        private final long groupId; private final JPanel listPanel;
        BookmarkListImportHandler(long groupId, JPanel listPanel) { this.groupId = groupId; this.listPanel = listPanel; }
        @Override public boolean canImport(TransferSupport s) {
            if (!s.isDrop()) { hideDropHighlight(); return false; }
            if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { hideDropHighlight(); return true; }
            if (s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String payload = getStringData(s); if (payload == null) { hideDropHighlight(); return false; }
                // 북마크 이동 or 외부 텍스트
                // 하이라이트(좌표 변환: 현재 컴포넌트 → listPanel)
                Point p = s.getDropLocation().getDropPoint();
                int boundaryY = computeBookmarkBoundaryY(listPanel, p);
                showDropHighlightAt(listPanel, boundaryY);
                return true;
            }
            hideDropHighlight(); return false;
        }
        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null) {
                        for (File f : files) bookmarkService.createBookmark(groupId, f.getName(), f.getAbsolutePath());
                        setStatus(files.size()+"개 항목을 추가했습니다."); rebuildAccordion(); return true;
                    }
                    return false;
                }
                String payload = getStringData(s);
                if (payload == null) return false;

                Point p = s.getDropLocation().getDropPoint(); // listPanel 기준
                int toIndex = computeBookmarkTargetIndex(listPanel, p);

                if (payload.startsWith("B:")) {
                    String[] parts = payload.split(":");
                    long fromGroup = parseLongSafe(parts[1], -1);
                    long movedBookmarkId = parseLongSafe(parts[2], -1);

                    int fromIndex = findBookmarkIndexById(listPanel, movedBookmarkId);
                    if (toIndex == fromIndex && fromGroup == groupId) { hideDropHighlight(); return false; }
                    if (toIndex > fromIndex && fromGroup == groupId) toIndex--;

                    int finalIndex = toIndex;
                    setUiBusy(true);
                    new SwingWorker<Void, Void>() {
                        @Override protected Void doInBackground() {
                            if (fromGroup == groupId) {
                                bookmarkService.reorderBookmark(groupId, movedBookmarkId, finalIndex);
                            } else {
                                try { bookmarkService.moveBookmark(movedBookmarkId, groupId, finalIndex); } catch (Throwable t) {
                                    // 폴백
                                    Bookmark moved = null;
                                    List<BookmarkGroup> groups = bookmarkGroupService.getBookmarkGroups();
                                    outer: for (BookmarkGroup g : groups) {
                                        List<Bookmark> lb = g.getBookmarks();
                                        if (lb == null) continue;
                                        for (Bookmark b : lb) if (b.getId() == movedBookmarkId) { moved = b; break outer; }
                                    }
                                    if (moved != null) {
                                        bookmarkService.createBookmark(groupId, moved.getDisplayName(), moved.getPath());
                                    }
                                }
                            }
                            return null;
                        }
                        @Override protected void done() {
                            setUiBusy(false); hideDropHighlight(); setStatus("북마크 위치가 변경되었습니다."); rebuildAccordion();
                        }
                    }.execute();
                    return true;
                } else {
                    // 외부 텍스트 줄단위로 경로 처리
                    for (String line : payload.split("\\R")) {
                        if (line.isBlank()) continue;
                        File f = new File(line.trim());
                        if (f.exists()) bookmarkService.createBookmark(groupId, f.getName(), f.getAbsolutePath());
                    }
                    setStatus("텍스트 드롭으로 항목을 추가했습니다."); rebuildAccordion(); return true;
                }
            } catch (Exception ex) {
                setUiBusy(false); hideDropHighlight(); showError("작업 실패: " + ex.getMessage()); return false;
            }
        }
    }

    private int computeBookmarkBoundaryY(JPanel panel, Point dropPointInPanel) {
        Rectangle lastRect = null;
        for (Component c : panel.getComponents()) {
            if (c instanceof BookmarkRow br) {
                Rectangle r = br.getBounds();
                int midY = r.y + r.height / 2;
                if (dropPointInPanel.y < midY) return r.y;
                lastRect = r;
            }
        }
        return lastRect != null ? lastRect.y + lastRect.height : 0;
    }

    private int computeBookmarkTargetIndex(JPanel panel, Point dropPointInPanel) {
        int idx = 0;
        for (Component c : panel.getComponents()) {
            if (c instanceof BookmarkRow br) {
                Rectangle r = br.getBounds();
                int midY = r.y + r.height / 2;
                if (dropPointInPanel.y < midY) return idx;
                idx++;
            }
        }
        return idx;
    }

    private int findBookmarkIndexById(JPanel panel, long bookmarkId) {
        int idx = 0;
        for (Component c : panel.getComponents()) {
            if (c instanceof BookmarkRow br) {
                if (br.bm.getId() == bookmarkId) return idx;
                idx++;
            }
        }
        return -1;
    }

    // =================== Highlight ===================

    /** GlassPane 위에 드롭 하이라이트 선을 그리는 레이어 */
    private final class HighlightGlass extends JComponent {
        private int y = -1; // glassPane 좌표계
        HighlightGlass() { setOpaque(false); enableEvents(0); }
        void showAt(int yGlass) { this.y = yGlass; if (!isVisible()) setVisible(true); repaint(); }
        void clear() { this.y = -1; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            if (y < 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), margin = 12;
                g2.setColor(new Color(64, 128, 255, 220));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(margin, y, w - margin, y);
                g2.setColor(new Color(64, 128, 255, 90));
                g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(margin, y, w - margin, y);
                Polygon left = new Polygon(new int[]{margin - 8, margin - 1, margin - 8},
                        new int[]{y - 6, y, y + 6}, 3);
                Polygon right = new Polygon(new int[]{w - margin + 8, w - margin + 1, w - margin + 8},
                        new int[]{y - 6, y, y + 6}, 3);
                g2.setColor(new Color(64, 128, 255, 200));
                g2.fill(left); g2.fill(right);
            } finally { g2.dispose(); }
        }
    }

    /** base 컴포넌트의 Y를 glassPane 좌표로 변환해 하이라이트 */
    private void showDropHighlightAt(JComponent base, int boundaryYInBase) {
        Point basePoint = new Point(0, boundaryYInBase);
        Point glassPoint = SwingUtilities.convertPoint(base, basePoint, highlight);
        highlight.showAt(glassPoint.y);
    }

    private void hideDropHighlight() {
        highlight.clear();
        highlight.setVisible(false);
    }

    // =================== 공통 유틸 ===================

    private void setUiBusy(boolean busy) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        addGroupBtn.setEnabled(!busy);
        expandAllBtn.setEnabled(!busy);
        collapseAllBtn.setEnabled(!busy);
        for (Component c : accordion.getComponents()) {
            if (c instanceof GroupSection gs) {
                gs.toggle.setEnabled(!busy);
                gs.moreBtn.setEnabled(!busy);
            }
        }
    }

    private Icon iconForBookmark(Bookmark b) {
        BookmarkType t = b.getTargetType();
        Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
        Icon dirIcon  = UIManager.getIcon("FileView.directoryIcon");
        if (t == BookmarkType.DIRECTORY && dirIcon != null) return dirIcon;
        if (t == BookmarkType.FILE && fileIcon != null) return fileIcon;
        return fileIcon != null ? fileIcon : UIManager.getIcon("Tree.leafIcon");
    }

    private static JComponent emptyHint(String text) {
        JLabel l = new JLabel(text, SwingConstants.LEFT);
        l.setForeground(NOTION_HINT);
        l.setFont(l.getFont().deriveFont(13f));
        l.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        var p = new JPanel(new BorderLayout());
        p.add(l, BorderLayout.CENTER);
        p.setOpaque(false);
        return p;
    }

    private static void stylizeButton(AbstractButton b) {
        try { b.putClientProperty("JButton.buttonType", "roundRect"); } catch (Exception ignored) {}
    }

    private static void stylizeIconButton(AbstractButton b) {
        b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static String getStringData(TransferHandler.TransferSupport s) {
        try {
            if (s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) s.getTransferable().getTransferData(DataFlavor.stringFlavor);
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static long parseLongSafe(String s, long dflt) {
        try { return Long.parseLong(s); } catch (Exception e) { return dflt; }
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
        statusActionBtn.setVisible(false);
        if (statusTimer != null) statusTimer.stop();
        statusTimer = new Timer(2500, e -> statusLabel.setText(" "));
        statusTimer.setRepeats(false);
        statusTimer.start();
    }

    private void setStatusWithAction(String msg, String action, Runnable handler) {
        statusLabel.setText(msg);
        statusActionBtn.setText(action);
        statusActionBtn.setVisible(true);
        this.statusActionHandler = handler;
        if (statusTimer != null) statusTimer.stop();
        statusTimer = new Timer(6000, e -> { statusLabel.setText(" "); statusActionBtn.setVisible(false); });
        statusTimer.setRepeats(false);
        statusTimer.start();
    }

    private void showError(String msg){
        JOptionPane.showMessageDialog(this, msg, "오류", JOptionPane.ERROR_MESSAGE);
    }

    private void openBookmarkPath(String pathStr){
        try {
            if (pathStr == null || pathStr.isBlank()) throw new IllegalArgumentException("경로가 비어있습니다.");
            Path p = java.nio.file.Path.of(pathStr);
            Desktop.getDesktop().open(p.toFile());
        } catch (Exception ex) {
            showError("열 수 없습니다: " + ex.getMessage());
        }
    }

    private void loadWindowPrefs(){
        try {
            int w = prefs.getInt("win.w", getWidth());
            int h = prefs.getInt("win.h", getHeight());
            setSize(w, h);
        } catch (Exception ignore){}
    }

    private void saveWindowPrefs(){
        try {
            prefs.putInt("win.w", getWidth());
            prefs.putInt("win.h", getHeight());
            // 펼침 상태 저장
            List<BookmarkGroup> groups = bookmarkGroupService.getBookmarkGroups();
            StringBuilder sb = new StringBuilder();
            for (BookmarkGroup g : groups) {
                boolean ex = expandState.getOrDefault(g.getId(), Boolean.TRUE);
                sb.append(g.getId()).append(":").append(ex ? "1" : "0").append(",");
            }
            prefs.put("expandState", sb.toString());
        } catch (Exception ignore){}
    }

    private void loadExpandStateFromPrefs(List<BookmarkGroup> groups){
        try {
            String s = prefs.get("expandState", "");
            Map<Long, Boolean> map = new HashMap<>();
            for (String part : s.split(",")) {
                if (part.isBlank()) continue;
                String[] kv = part.split(":");
                long id = parseLongSafe(kv[0], -1);
                boolean ex = "1".equals(kv.length > 1 ? kv[1] : "1");
                map.put(id, ex);
            }
            for (BookmarkGroup g : groups) {
                if (!expandState.containsKey(g.getId()) && map.containsKey(g.getId())) {
                    expandState.put(g.getId(), map.get(g.getId()));
                }
            }
        } catch (Exception ignore){}
    }

    private Color getSeparatorColor() {
        return NOTION_BORDER;
    }
}
