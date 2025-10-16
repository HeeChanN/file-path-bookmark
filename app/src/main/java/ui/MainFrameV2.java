package ui;

import model.Bookmark;
import model.BookmarkGroup;
import model.BookmarkType; // ★ 파일/폴더 아이콘에 사용
import service.bookmark.BookmarkService;
import service.bookmark_group.BookmarkGroupService;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 한 패널에 '그룹 헤더(토글) + 해당 그룹 북마크 목록'을 아코디언 방식으로 표시하는 메인 프레임.
 * - 그룹/북마크 순서 변경: 드롭다운 + 드래그앤드롭
 * - 드래그 시 드롭 위치 하이라이트 라인 표시(GlassPane)
 * - 그룹 삭제/이름변경, 북마크 수정까지 연결
 */
public class MainFrameV2 extends JFrame {

    private final BookmarkService bookmarkService;
    private final BookmarkGroupService bookmarkGroupService;

    // 상단 툴바 구성 요소
    private final JButton addGroupBtn = new JButton("그룹 추가");
    private final JButton expandAllBtn = new JButton("모두 펼치기");
    private final JButton collapseAllBtn = new JButton("모두 접기");

    // 중앙: 아코디언 컨테이너(세로 흐름)
    private final JPanel accordion = new JPanel();

    // 섹션 펼침/접힘 상태 보존
    private final Map<Long, Boolean> expandState = new HashMap<>();

    // 드래그 위치 하이라이트(GlassPane)
    private final HighlightGlass highlight = new HighlightGlass();

    public MainFrameV2(BookmarkService bookmarkService, BookmarkGroupService bookmarkGroupService) {
        super("Shortcut Manager");
        this.bookmarkService = Objects.requireNonNull(bookmarkService);
        this.bookmarkGroupService = Objects.requireNonNull(bookmarkGroupService);

        // ====== UI 기본 ======
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 상단 툴바
        var toolbar = buildToolbar();
        add(toolbar, BorderLayout.NORTH);

        // 중앙 아코디언 컨테이너
        accordion.setLayout(new BoxLayout(accordion, BoxLayout.Y_AXIS));
        var scroll = new JScrollPane(accordion);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // DnD(그룹 재정렬) 드롭 핸들러 부착
        accordion.setTransferHandler(new GroupReorderHandler());

        // GlassPane 하이라이트 설정
        getRootPane().setGlassPane(highlight);
        highlight.setVisible(false);

        // 데이터 로드 → 섹션 구성
        rebuildAccordion();

        // 액션 바인딩
        wireActions();
    }

    // 상단 툴바 구성
    private JComponent buildToolbar() {
        var bar = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;

        stylizeButton(addGroupBtn);
        stylizeButton(expandAllBtn);
        stylizeButton(collapseAllBtn);

        bar.add(addGroupBtn, gbc); gbc.gridx++;
        bar.add(expandAllBtn, gbc); gbc.gridx++;
        bar.add(collapseAllBtn, gbc); gbc.gridx++;

        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        var spacer = new JPanel();
        spacer.setOpaque(false);
        bar.add(spacer, gbc); // 우측 여백

        return bar;
    }

    private void wireActions() {
        addGroupBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "그룹명");
            if (name == null || name.isBlank()) return;
            try {
                bookmarkGroupService.createBookmarkGroup(name);
                rebuildAccordion();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, "그룹 생성 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
            }
        });

        expandAllBtn.addActionListener(e -> setAllSectionsExpanded(true));
        collapseAllBtn.addActionListener(e -> setAllSectionsExpanded(false));
    }

    /** 전체 아코디언 섹션 재구성 */
    private void rebuildAccordion() {
        // 현재 펼침 상태 백업
        for (Component c : accordion.getComponents()) {
            if (c instanceof GroupSection gs) {
                expandState.put(gs.group.getId(), gs.toggle.isSelected());
            }
        }

        accordion.removeAll();

        List<BookmarkGroup> groups = bookmarkGroupService.getBookmarkGroups();
        if (groups.isEmpty()) {
            accordion.add(emptyHint("그룹이 없습니다. [그룹 추가] 버튼으로 시작해 보세요."));
        } else {
            for (int i = 0; i < groups.size(); i++) {
                BookmarkGroup g = groups.get(i);
                accordion.add(new GroupSection(g, i, groups.size())); // index, total 전달
                accordion.add(Box.createVerticalStrut(8));
            }
        }

        accordion.add(Box.createVerticalGlue());
        accordion.revalidate();
        accordion.repaint();

        // 재구성 시 하이라이트 숨기기(안전)
        hideDropHighlight();
    }

    /** 모든 섹션 펼치기/접기 */
    private void setAllSectionsExpanded(boolean expanded) {
        for (Component c : accordion.getComponents()) {
            if (c instanceof GroupSection) {
                ((GroupSection) c).setExpanded(expanded);
            }
        }
    }

    /** 그룹 + 북마크 목록 한 묶음 */
    private final class GroupSection extends JPanel {
        private final BookmarkGroup group;
        private final JToggleButton toggle = new JToggleButton();
        private final JLabel title = new JLabel();
        private final JButton addBookmarkBtn = new JButton("북마크 추가");

        // ★ 그룹 이름 변경/삭제 버튼
        private final JButton renameGroupBtn = new JButton("이름 변경");
        private final JButton deleteGroupBtn = new JButton("삭제");

        private final JPanel content = new JPanel();

        // 순서 변경용(드롭다운)
        private final JComboBox<String> orderCombo;
        private int currentIndex;      // 0-based
        private final int totalGroups;

        GroupSection(BookmarkGroup group, int index, int totalGroups) {
            super(new BorderLayout());
            this.group = group;
            this.currentIndex = index;
            this.totalGroups = totalGroups;

            // 헤더 영역
            var header = new JPanel(new GridBagLayout());
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1,1,1,1, new Color(230,230,235)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            header.setBackground(new Color(248, 248, 252));

            // DnD: 헤더 드래그(Export) 활성화 → 그룹 재정렬
            header.setTransferHandler(new GroupHeaderExportHandler(group.getId()));
            header.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    JComponent c = (JComponent) e.getSource();
                    c.getTransferHandler().exportAsDrag(c, e, TransferHandler.MOVE);
                }
            });

            toggle.setText("▸"); // 접힘 표시 (펼침 시 ▾ 로 전환)
            toggle.setSelected(expandState.getOrDefault(group.getId(), Boolean.TRUE)); // 보존된 상태 반영
            updateToggleGlyph();
            stylizeButton(addBookmarkBtn);
            stylizeButton(renameGroupBtn); // ★
            stylizeButton(deleteGroupBtn); // ★

            title.setText(group.getName());
            title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D()+1));

            // 순서 드롭다운(1..N)
            String[] positions = IntStream.rangeClosed(1, totalGroups)
                    .mapToObj(Integer::toString).toArray(String[]::new);
            orderCombo = new JComboBox<>(positions);
            orderCombo.setSelectedIndex(currentIndex); // 0-based index
            orderCombo.setMaximumRowCount(Math.min(totalGroups, 20));
            orderCombo.setToolTipText("이 그룹의 표시 순서를 선택하세요");

            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 4, 0, 4);
            gbc.gridx=0; gbc.gridy=0; gbc.anchor = GridBagConstraints.WEST;
            header.add(toggle, gbc);

            gbc.gridx=1; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
            header.add(title, gbc);

            // 우측에 [순서 ▾][이름변경][삭제][북마크 추가]
            gbc.gridx=2; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE;
            header.add(new JLabel("순서"), gbc);
            gbc.gridx=3;
            header.add(orderCombo, gbc);
            gbc.gridx=4;
            header.add(renameGroupBtn, gbc); // ★
            gbc.gridx=5;
            header.add(deleteGroupBtn, gbc); // ★
            gbc.gridx=6;
            header.add(addBookmarkBtn, gbc);

            add(header, BorderLayout.NORTH);

            // 콘텐츠(북마크 리스트 패널)
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 12));

            // ★ 북마크 재정렬(행 DnD) 드롭 핸들러 부착
            content.setTransferHandler(new BookmarkReorderHandler(group.getId(), content));

            add(content, BorderLayout.CENTER);

            // 최초 로드
            reloadBookmarks();

            // 동작
            toggle.addActionListener(e -> {
                updateToggleGlyph();
                content.setVisible(toggle.isSelected());
                expandState.put(group.getId(), toggle.isSelected());
                revalidate();
            });

            addBookmarkBtn.addActionListener(e -> promptAddBookmark());

            // ★ 그룹 이름 변경
            renameGroupBtn.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(MainFrameV2.this, "새 그룹명", group.getName());
                if (newName == null || newName.isBlank() || newName.equals(group.getName())) return;
                try {
                    bookmarkGroupService.renameBookmarkGroup(group.getId(), newName);
                    rebuildAccordion();
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(MainFrameV2.this, "이름 변경 실패: " + ex.getMessage(),
                            "오류", JOptionPane.ERROR_MESSAGE);
                }
            });

            // ★ 그룹 삭제
            deleteGroupBtn.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(MainFrameV2.this,
                        "그룹을 삭제하시겠습니까?\n" + group.getName(),
                        "확인", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.OK_OPTION) return;
                try {
                    bookmarkGroupService.deleteBookmarkGroup(group.getId());
                    rebuildAccordion();
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(MainFrameV2.this, "삭제 실패: " + ex.getMessage(),
                            "오류", JOptionPane.ERROR_MESSAGE);
                }
            });

            // 그룹 순서 드롭다운
            orderCombo.addActionListener(e -> {
                int toIndex = orderCombo.getSelectedIndex(); // 0-based
                if (toIndex == currentIndex) return;

                setUiBusy(true);
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        bookmarkGroupService.reorderBookmarkGroups(group.getId(), toIndex);
                        return null;
                    }
                    @Override protected void done() {
                        setUiBusy(false);
                        rebuildAccordion();
                    }
                }.execute();
            });

            // 접힘/펼침 반영
            content.setVisible(toggle.isSelected());
        }

        void setExpanded(boolean expanded) {
            toggle.setSelected(expanded);
            updateToggleGlyph();
            content.setVisible(expanded);
            expandState.put(group.getId(), expanded);
            revalidate();
        }

        private void updateToggleGlyph() {
            toggle.setText(toggle.isSelected() ? "▾" : "▸");
        }

        private void promptAddBookmark() {
            String path = JOptionPane.showInputDialog(MainFrameV2.this, "파일 또는 폴더 경로를 입력하세요");
            if (path == null || path.isBlank()) return;

            Path p = Path.of(path);
            String defaultName = p.getFileName() != null ? p.getFileName().toString() : path;
            String displayName = JOptionPane.showInputDialog(MainFrameV2.this, "표시 이름(생략 가능)", defaultName);
            if (displayName == null) return;

            try {
                Bookmark saved = bookmarkService.createBookmark(group.getId(), displayName, path);
                // 이 섹션만 새로 그림
                reloadBookmarks();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(MainFrameV2.this, "북마크 생성 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
            }
        }

        /** 이 섹션의 북마크 목록을 다시 그림 */
        private void reloadBookmarks() {
            content.removeAll();

            List<Bookmark> bookmarks = group.getBookmarks(); // 서비스 경유로 바꾸려면 여기 교체
            if (bookmarks == null || bookmarks.isEmpty()) {
                content.add(emptyHint("이 그룹에 북마크가 없습니다."));
            } else {
                for (Bookmark b : bookmarks) {
                    addBookmarkItem(b);
                    content.add(Box.createVerticalStrut(6));
                }
            }

            content.revalidate();
            content.repaint();
        }

        private void addBookmarkItem(Bookmark b) {
            var row = new BookmarkRow(group.getId(), b, content); // ★ 콘텐츠 패널 전달(DnD용)
            content.add(row);
        }
    }

    /** 북마크 한 줄 표시(아이콘 + 이름 굵게 + 경로 회색), [복사][수정][삭제] 버튼 + DnD Export */
    private final class BookmarkRow extends JPanel {
        private final long groupId;   // ★ 재정렬 대상 그룹 id
        private final Bookmark bm;
        private final JPanel listPanel; // ★ 부모 목록 패널(드롭 위치 계산에 사용)

        BookmarkRow(long groupId, Bookmark bm, JPanel listPanel) {
            super(new GridBagLayout());
            this.groupId = groupId;
            this.bm = bm;
            this.listPanel = listPanel;

            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1,1,1,1, new Color(235,235,240)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            // ★ 파일/폴더 아이콘 적용
            Icon icon = iconForBookmark(bm);
            JLabel label = new JLabel(
                    "<html><b>" + esc(bm.getDisplayName()) + "</b><br>" +
                            "<span style='font-size:10px;color:gray'>" + esc(bm.getPath()) + "</span></html>",
                    icon, SwingConstants.LEFT
            );

            JButton copyBtn  = new JButton("복사");
            JButton editBtn  = new JButton("수정");   // ★
            JButton delBtn   = new JButton("삭제");
            stylizeButton(copyBtn);
            stylizeButton(editBtn);
            stylizeButton(delBtn);

            // ★ DnD: 행 드래그(Export) 활성화 (payload: "B:<groupId>:<bookmarkId>")
            setTransferHandler(new BookmarkRowExportHandler(groupId, bm.getId()));
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    getTransferHandler().exportAsDrag(BookmarkRow.this, e, TransferHandler.MOVE);
                }
            });

            // 레이아웃
            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 4, 2, 4);
            gbc.gridx=0; gbc.gridy=0; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor=GridBagConstraints.WEST;
            add(label, gbc);

            gbc.gridx=1; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE; gbc.anchor=GridBagConstraints.EAST;
            add(copyBtn, gbc);
            gbc.gridx=2;
            add(editBtn, gbc); // ★
            gbc.gridx=3;
            add(delBtn, gbc);

            // 동작
            copyBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(bm.getPath()), null);
                JOptionPane.showMessageDialog(MainFrameV2.this, "경로를 클립보드에 복사했습니다.");
            });

            // ★ 북마크 수정: 이름/경로 입력 → 서비스 호출
            editBtn.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(MainFrameV2.this, "표시 이름", bm.getDisplayName());
                if (newName == null) return;
                String newPath = JOptionPane.showInputDialog(MainFrameV2.this, "파일/폴더 경로", bm.getPath());
                if (newPath == null) return;

                try {
                    bookmarkService.updateBookmark(bm.getId(), newName, newPath);
                    // 목록만 새로 그리기
                    Container p = getParent();
                    if (p instanceof JPanel panel) {
                        // 상위 GroupSection의 reload를 유도하기 위해 프레임 전체 리빌드 or 섹션만 갱신
                        rebuildAccordion(); // 안전하게 전체 갱신
                    }
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(MainFrameV2.this, "수정 실패: " + ex.getMessage(),
                            "오류", JOptionPane.ERROR_MESSAGE);
                }
            });

            delBtn.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(MainFrameV2.this,
                        "삭제하시겠습니까?\n" + bm.getDisplayName(), "확인",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.OK_OPTION) return;

                try {
                    bookmarkService.remove(bm.getId());
                    // UI에서 이 행 제거
                    Container parent = getParent();
                    parent.remove(this);
                    parent.revalidate();
                    parent.repaint();
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(MainFrameV2.this, "이미 삭제되었거나 존재하지 않습니다.");
                }
            });
        }
    }

    // ================= DnD & Highlight 유틸 =================

    /** 헤더에서 groupId(Long)을 문자열로 내보내는 Export 핸들러 */
    private final class GroupHeaderExportHandler extends TransferHandler {
        private final Long groupId;
        GroupHeaderExportHandler(Long groupId) { this.groupId = groupId; }
        @Override protected Transferable createTransferable(JComponent c) {
            return new StringSelection(String.valueOf(groupId)); // DataFlavor.stringFlavor
        }
        @Override public int getSourceActions(JComponent c) { return MOVE; }
        @Override protected void exportDone(JComponent source, Transferable data, int action) {
            hideDropHighlight(); // 드래그 종료 시 하이라이트 숨김
        }
    }

    /** 아코디언 위로 드롭됐을 때, 목표 인덱스를 계산해 서비스로 이동(그룹 재정렬) + 드래그 중 하이라이트 표시 */
    private final class GroupReorderHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport s) {
            boolean ok = s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor);
            if (!ok) { hideDropHighlight(); return false; }
            // 그룹 드래그일 때만 하이라이트
            String payload = getStringData(s);
            if (payload != null && !payload.startsWith("B:")) {
                Point p = s.getDropLocation().getDropPoint(); // accordion 좌표
                int boundaryYInAccordion = computeGroupBoundaryY(p);
                showDropHighlightAt(accordion, boundaryYInAccordion);
            } else {
                hideDropHighlight();
            }
            return true;
        }

        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            String movedIdStr = getStringData(s);
            if (movedIdStr == null || movedIdStr.startsWith("B:")) return false; // 북마크 페이로드는 여기서 처리 안 함

            try {
                Long movedId = Long.parseLong(movedIdStr);
                Point p = s.getDropLocation().getDropPoint(); // accordion 기준

                int fromIndex = findGroupIndexById(movedId);
                if (fromIndex < 0) return false;

                int toIndex = computeGroupTargetIndex(p);
                if (toIndex == fromIndex) { hideDropHighlight(); return false; }
                if (toIndex > fromIndex) toIndex--; // 아래로 이동 보정

                setUiBusy(true);
                int finalIndex = toIndex;
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        bookmarkGroupService.reorderBookmarkGroups(movedId, finalIndex); // 0-based
                        return null;
                    }
                    @Override protected void done() {
                        setUiBusy(false);
                        hideDropHighlight();
                        rebuildAccordion();
                    }
                }.execute();

                return true;
            } catch (Exception ex) {
                setUiBusy(false);
                hideDropHighlight();
                JOptionPane.showMessageDialog(MainFrameV2.this, "순서 변경 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        private String getStringData(TransferSupport s) {
            try {
                if (s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    return (String) s.getTransferable().getTransferData(DataFlavor.stringFlavor);
                }
            } catch (Exception ignore) {}
            return null;
        }

        // 그룹 경계선 Y(accordion 좌표)
        private int computeGroupBoundaryY(Point dropPointInAccordion) {
            Rectangle lastRect = null;
            for (Component c : accordion.getComponents()) {
                if (c instanceof GroupSection gs) {
                    Rectangle r = gs.getBounds(); // accordion 기준
                    int midY = r.y + r.height / 2;
                    if (dropPointInAccordion.y < midY) {
                        return r.y; // 섹션 윗선에 그리기
                    }
                    lastRect = r;
                }
            }
            return lastRect != null ? lastRect.y + lastRect.height : 0;
        }

        // 목표 인덱스(그룹)
        private int computeGroupTargetIndex(Point dropPointInAccordion) {
            int idx = 0;
            for (Component c : accordion.getComponents()) {
                if (c instanceof GroupSection gs) {
                    Rectangle r = gs.getBounds();
                    int midY = r.y + r.height / 2;
                    if (dropPointInAccordion.y < midY) return idx;
                    idx++;
                }
            }
            return idx;
        }

        private int findGroupIndexById(Long id) {
            int idx = 0;
            for (Component c : accordion.getComponents()) {
                if (c instanceof GroupSection gs) {
                    if (Objects.equals(gs.group.getId(), id)) return idx;
                    idx++;
                }
            }
            return -1;
        }
    }

    /** 북마크 행에서 "B:<groupId>:<bookmarkId>" 문자열로 내보내는 Export 핸들러 */
    private final class BookmarkRowExportHandler extends TransferHandler {
        private final long groupId;
        private final long bookmarkId;
        BookmarkRowExportHandler(long groupId, long bookmarkId) {
            this.groupId = groupId; this.bookmarkId = bookmarkId;
        }
        @Override protected Transferable createTransferable(JComponent c) {
            return new StringSelection("B:" + groupId + ":" + bookmarkId);
        }
        @Override public int getSourceActions(JComponent c) { return MOVE; }
        @Override protected void exportDone(JComponent source, Transferable data, int action) {
            hideDropHighlight();
        }
    }

    /** 그룹 섹션 안의 content(JPanel) 위로 드롭 시 북마크 재정렬 */
    private final class BookmarkReorderHandler extends TransferHandler {
        private final long groupId;
        private final JPanel listPanel; // 해당 그룹의 북마크 목록 패널

        BookmarkReorderHandler(long groupId, JPanel listPanel) {
            this.groupId = groupId;
            this.listPanel = listPanel;
        }

        @Override public boolean canImport(TransferSupport s) {
            boolean ok = s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor);
            if (!ok) { hideDropHighlight(); return false; }

            String payload = getStringData(s);
            if (payload == null || !payload.startsWith("B:")) { hideDropHighlight(); return false; }

            // 같은 그룹의 북마크만 허용
            String[] parts = payload.split(":");
            if (parts.length != 3) { hideDropHighlight(); return false; }
            long fromGroup = Long.parseLong(parts[1]);
            if (fromGroup != groupId) { hideDropHighlight(); return false; }

            // 드래그 중 라인 하이라이트
            Point p = s.getDropLocation().getDropPoint(); // listPanel 좌표
            int boundaryY = computeBookmarkBoundaryY(listPanel, p);
            showDropHighlightAt(listPanel, boundaryY);
            return true;
        }

        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                String payload = getStringData(s);
                String[] parts = payload.split(":");
                long fromGroup = Long.parseLong(parts[1]);
                long movedBookmarkId = Long.parseLong(parts[2]);

                Point p = s.getDropLocation().getDropPoint(); // listPanel 기준
                int fromIndex = findBookmarkIndexById(listPanel, movedBookmarkId);
                if (fromIndex < 0) { hideDropHighlight(); return false; }

                int toIndex = computeBookmarkTargetIndex(listPanel, p);
                if (toIndex == fromIndex) { hideDropHighlight(); return false; }
                if (toIndex > fromIndex) toIndex--; // 아래로 이동 보정

                setUiBusy(true);
                int finalIndex = toIndex;
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        // ★ 주의: 시그니처가 (groupId, prevId, toIndex) → prevId는 "이동할 북마크 id"로 사용
                        bookmarkService.reorderBookmark(fromGroup, movedBookmarkId, finalIndex);
                        return null;
                    }
                    @Override protected void done() {
                        setUiBusy(false);
                        hideDropHighlight();
                        rebuildAccordion(); // 해당 섹션만 부분 갱신하려면 구조 분리 필요. 안전하게 전체 갱신.
                    }
                }.execute();

                return true;
            } catch (Exception ex) {
                setUiBusy(false);
                hideDropHighlight();
                JOptionPane.showMessageDialog(MainFrameV2.this, "북마크 순서 변경 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        private String getStringData(TransferSupport s) {
            try {
                if (s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    return (String) s.getTransferable().getTransferData(DataFlavor.stringFlavor);
                }
            } catch (Exception ignore) {}
            return null;
        }

        /** 드롭 라인 Y(listPanel 좌표) 계산: 각 BookmarkRow의 윗선 또는 마지막 아래선 */
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
            return (lastRect != null) ? lastRect.y + lastRect.height : 0;
        }

        /** 타겟 인덱스 계산(BookmarkRow만 카운트) */
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
    }

    /** GlassPane 위에 드롭 하이라이트 선을 그리는 레이어 */
    private final class HighlightGlass extends JComponent {
        private int y = -1; // glassPane 좌표계

        HighlightGlass() {
            setOpaque(false);
            enableEvents(0); // 이벤트 투과
        }

        void showAt(int yGlass) {
            this.y = yGlass;
            if (!isVisible()) setVisible(true);
            repaint();
        }

        void clear() {
            this.y = -1;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            if (y < 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int margin = 12;

                // 메인 라인
                g2.setColor(new Color(64, 128, 255, 220));
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(margin, y, w - margin, y);

                // 얇은 그림자라인
                g2.setColor(new Color(64, 128, 255, 90));
                g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(margin, y, w - margin, y);

                // 사이드 마커(삼각형)
                Polygon left = new Polygon(
                        new int[]{margin - 8, margin - 1, margin - 8},
                        new int[]{y - 6, y, y + 6}, 3);
                Polygon right = new Polygon(
                        new int[]{w - margin + 8, w - margin + 1, w - margin + 8},
                        new int[]{y - 6, y, y + 6}, 3);
                g2.setColor(new Color(64, 128, 255, 200));
                g2.fill(left); g2.fill(right);
            } finally {
                g2.dispose();
            }
        }
    }

    /** 임의 컴포넌트 기준 Y를 glassPane 좌표로 변환하여 하이라이트 표시 */
    private void showDropHighlightAt(JComponent base, int boundaryYInBase) {
        Point basePoint = new Point(0, boundaryYInBase);
        Point glassPoint = SwingUtilities.convertPoint(base, basePoint, highlight);
        highlight.showAt(glassPoint.y);
    }

    /** 하이라이트 숨김 */
    private void hideDropHighlight() {
        highlight.clear();
        highlight.setVisible(false);
    }

    /** UI 전체 활성/비활성 + 커서 처리 */
    private void setUiBusy(boolean busy) {
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        addGroupBtn.setEnabled(!busy);
        expandAllBtn.setEnabled(!busy);
        collapseAllBtn.setEnabled(!busy);
        for (Component c : accordion.getComponents()) {
            if (c instanceof GroupSection gs) {
                gs.toggle.setEnabled(!busy);
                gs.addBookmarkBtn.setEnabled(!busy);
                gs.renameGroupBtn.setEnabled(!busy);
                gs.deleteGroupBtn.setEnabled(!busy);
                gs.orderCombo.setEnabled(!busy);
            }
        }
    }

    // ----- 아이콘/유틸 -----

    // ★ 파일/폴더 아이콘 선택
    private Icon iconForBookmark(Bookmark b) {
        BookmarkType t = b.getTargetType();
        Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
        Icon dirIcon  = UIManager.getIcon("FileView.directoryIcon");
        if (t == BookmarkType.DIRECTORY && dirIcon != null) return dirIcon;
        if (t == BookmarkType.FILE && fileIcon != null) return fileIcon;
        // fallback: 기본 문서 모양
        return fileIcon != null ? fileIcon : UIManager.getIcon("Tree.leafIcon");
    }

    private static JComponent emptyHint(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(new Color(130, 130, 140));
        l.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var p = new JPanel(new BorderLayout());
        p.add(l, BorderLayout.CENTER);
        p.setOpaque(false);
        return p;
    }

    private static void stylizeButton(AbstractButton b) {
        try {
            b.putClientProperty("JButton.buttonType", "roundRect");
        } catch (Exception ignored) {}
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
