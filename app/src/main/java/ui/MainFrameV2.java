package ui;

import model.Bookmark;
import model.BookmarkGroup;
import model.BookmarkType; // 파일/폴더 아이콘
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

    // 상단 툴바
    private final JButton addGroupBtn = new JButton("그룹 추가");
    private final JButton expandAllBtn = new JButton("모두 펼치기");
    private final JButton collapseAllBtn = new JButton("모두 접기");

    // 중앙 아코디언 컨테이너
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

        // 그룹 DnD: 빈 공간(섹션 사이)에서도 작동하도록 아코디언에 Import 핸들러
        accordion.setTransferHandler(new GroupReorderImportHandler());

        // GlassPane 하이라이트
        getRootPane().setGlassPane(highlight);
        highlight.setVisible(false);

        // 데이터 로드 → 섹션 구성
        rebuildAccordion();

        // 액션 바인딩
        wireActions();
    }

    // 상단 툴바
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
        bar.add(spacer, gbc);
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
        // 펼침 상태 백업
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
                accordion.add(new GroupSection(g, i, groups.size()));
                accordion.add(Box.createVerticalStrut(8));
            }
        }

        accordion.add(Box.createVerticalGlue());
        accordion.revalidate();
        accordion.repaint();

        hideDropHighlight();
    }

    /** 모든 섹션 펼치기/접기 */
    private void setAllSectionsExpanded(boolean expanded) {
        for (Component c : accordion.getComponents()) {
            if (c instanceof GroupSection) ((GroupSection) c).setExpanded(expanded);
        }
    }

    /** 그룹 + 북마크 목록 한 묶음 */
    private final class GroupSection extends JPanel {
        private final BookmarkGroup group;
        private final JToggleButton toggle = new JToggleButton();
        private final JLabel title = new JLabel();
        private final JButton addBookmarkBtn = new JButton("북마크 추가");
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

            // 헤더
            var header = new JPanel(new GridBagLayout());
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1,1,1,1, new Color(230,230,235)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            header.setBackground(new Color(248, 248, 252));

            // 그룹 헤더 DnD: Export + Import 모두 처리(헤더 위로 드롭해도 동작)
            var headerDnD = new GroupHeaderTransferHandler(group.getId());
            header.setTransferHandler(headerDnD);
            // 드래그 시작 안정화: press/drag 모두에서 시작
            var dragStarter = new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    header.getTransferHandler().exportAsDrag(header, e, TransferHandler.MOVE);
                }
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    header.getTransferHandler().exportAsDrag(header, e, TransferHandler.MOVE);
                }
            };
            header.addMouseListener(dragStarter);
            header.addMouseMotionListener(dragStarter);

            toggle.setText("▸");
            toggle.setSelected(expandState.getOrDefault(group.getId(), Boolean.TRUE));
            updateToggleGlyph();
            stylizeButton(addBookmarkBtn);
            stylizeButton(renameGroupBtn);
            stylizeButton(deleteGroupBtn);

            title.setText(group.getName());
            title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D()+1));

            // 순서 드롭다운(1..N)
            String[] positions = IntStream.rangeClosed(1, totalGroups).mapToObj(Integer::toString).toArray(String[]::new);
            orderCombo = new JComboBox<>(positions);
            orderCombo.setSelectedIndex(currentIndex);
            orderCombo.setMaximumRowCount(Math.min(totalGroups, 20));
            orderCombo.setToolTipText("이 그룹의 표시 순서를 선택하세요");

            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 4, 0, 4);
            gbc.gridx=0; gbc.gridy=0; gbc.anchor = GridBagConstraints.WEST;
            header.add(toggle, gbc);

            gbc.gridx=1; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
            header.add(title, gbc);

            gbc.gridx=2; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE;
            header.add(new JLabel("순서"), gbc);
            gbc.gridx=3;
            header.add(orderCombo, gbc);
            gbc.gridx=4;
            header.add(renameGroupBtn, gbc);
            gbc.gridx=5;
            header.add(deleteGroupBtn, gbc);
            gbc.gridx=6;
            header.add(addBookmarkBtn, gbc);

            add(header, BorderLayout.NORTH);

            // 콘텐츠(북마크 리스트)
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 12));
            add(content, BorderLayout.CENTER);

            // 북마크 리스트 패널에도 Import 핸들러(빈 공간 드롭 지원)
            content.setTransferHandler(new BookmarkListImportHandler(group.getId(), content));

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

            // 그룹 순서 변경(드롭다운)
            orderCombo.addActionListener(e -> {
                int toIndex = orderCombo.getSelectedIndex();
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
                bookmarkService.createBookmark(group.getId(), displayName, path);
                reloadBookmarks(); // 이 섹션만 갱신
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(MainFrameV2.this, "북마크 생성 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
            }
        }

        /** 이 섹션의 북마크 목록을 다시 그림 */
        private void reloadBookmarks() {
            content.removeAll();

            List<Bookmark> bookmarks = group.getBookmarks(); // 필요 시 서비스 경유로 교체
            if (bookmarks == null || bookmarks.isEmpty()) {
                content.add(emptyHint("이 그룹에 북마크가 없습니다."));
            } else {
                for (Bookmark b : bookmarks) {
                    content.add(new BookmarkRow(group.getId(), b, content));
                    content.add(Box.createVerticalStrut(6));
                }
            }

            content.revalidate();
            content.repaint();
        }
    }

    /** 북마크 한 줄 (아이콘 + 이름/경로, [복사][수정][삭제]) + DnD Export/Import */
    private final class BookmarkRow extends JPanel {
        private final long groupId;
        private final Bookmark bm;
        private final JPanel listPanel;

        BookmarkRow(long groupId, Bookmark bm, JPanel listPanel) {
            super(new GridBagLayout());
            this.groupId = groupId;
            this.bm = bm;
            this.listPanel = listPanel;

            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1,1,1,1, new Color(235,235,240)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            // 파일/폴더 아이콘
            Icon icon = iconForBookmark(bm);
            JLabel label = new JLabel(
                    "<html><b>" + esc(bm.getDisplayName()) + "</b><br>" +
                            "<span style='font-size:10px;color:gray'>" + esc(bm.getPath()) + "</span></html>",
                    icon, SwingConstants.LEFT
            );

            JButton copyBtn  = new JButton("복사");
            JButton editBtn  = new JButton("수정");
            JButton delBtn   = new JButton("삭제");
            stylizeButton(copyBtn);
            stylizeButton(editBtn);
            stylizeButton(delBtn);

            // DnD: 행 자체에 Export + Import 모두 지원(행 위/사이 어디든 드롭 동작)
            var rowDnD = new BookmarkRowTransferHandler(groupId, bm.getId(), listPanel);
            setTransferHandler(rowDnD);
            // 드래그 시작 안정화
            var dragStarter = new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    getTransferHandler().exportAsDrag(BookmarkRow.this, e, TransferHandler.MOVE);
                }
                @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                    getTransferHandler().exportAsDrag(BookmarkRow.this, e, TransferHandler.MOVE);
                }
            };
            addMouseListener(dragStarter);
            addMouseMotionListener(dragStarter);

            // 레이아웃
            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 4, 2, 4);
            gbc.gridx=0; gbc.gridy=0; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor=GridBagConstraints.WEST;
            add(label, gbc);

            gbc.gridx=1; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE; gbc.anchor=GridBagConstraints.EAST;
            add(copyBtn, gbc);
            gbc.gridx=2;
            add(editBtn, gbc);
            gbc.gridx=3;
            add(delBtn, gbc);

            // 동작
            copyBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(bm.getPath()), null);
                JOptionPane.showMessageDialog(MainFrameV2.this, "경로를 클립보드에 복사했습니다.");
            });

            editBtn.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(MainFrameV2.this, "표시 이름", bm.getDisplayName());
                if (newName == null) return;
                String newPath = JOptionPane.showInputDialog(MainFrameV2.this, "파일/폴더 경로", bm.getPath());
                if (newPath == null) return;

                try {
                    bookmarkService.updateBookmark(bm.getId(), newName, newPath);
                    rebuildAccordion(); // 간단·안전
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

    // =================== Group DnD ===================

    /** 그룹 헤더: Export + Import(헤더 위 드롭도 허용) */
    private final class GroupHeaderTransferHandler extends TransferHandler {
        private final long groupId;
        GroupHeaderTransferHandler(long groupId) { this.groupId = groupId; }

        // Export
        @Override protected Transferable createTransferable(JComponent c) {
            return new StringSelection(String.valueOf(groupId));
        }
        @Override public int getSourceActions(JComponent c) { return MOVE; }
        @Override protected void exportDone(JComponent source, Transferable data, int action) {
            hideDropHighlight();
        }

        // Import (그룹 DnD만 허용)
        @Override public boolean canImport(TransferSupport s) {
            if (!(s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor))) {
                hideDropHighlight(); return false;
            }
            String payload = getStringData(s);
            if (payload == null || payload.startsWith("B:")) { // 북마크 payload는 거절
                hideDropHighlight(); return false;
            }
            // 헤더 위로 드롭할 때 하이라이트(accordion 기준으로 계산)
            Point pInHeader = s.getDropLocation().getDropPoint();
            Point pInAccordion = SwingUtilities.convertPoint((Component)s.getComponent(), pInHeader, accordion);
            int boundaryY = computeGroupBoundaryY(pInAccordion);
            showDropHighlightAt(accordion, boundaryY);
            return true;
        }

        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                Long movedId = Long.parseLong(getStringData(s));

                Point pInHeader = s.getDropLocation().getDropPoint();
                Point pInAccordion = SwingUtilities.convertPoint((Component)s.getComponent(), pInHeader, accordion);

                int fromIndex = findGroupIndexById(movedId);
                if (fromIndex < 0) { hideDropHighlight(); return false; }

                int toIndex = computeGroupTargetIndex(pInAccordion);
                if (toIndex == fromIndex) { hideDropHighlight(); return false; }
                if (toIndex > fromIndex) toIndex--;

                setUiBusy(true);
                int finalIndex = toIndex;
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        bookmarkGroupService.reorderBookmarkGroups(movedId, finalIndex);
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
                JOptionPane.showMessageDialog(MainFrameV2.this, "그룹 순서 변경 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }

    /** 아코디언(빈 공간) 위 드롭 시 그룹 재정렬 허용 */
    private final class GroupReorderImportHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport s) {
            if (!(s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor))) {
                hideDropHighlight(); return false;
            }
            String payload = getStringData(s);
            if (payload == null || payload.startsWith("B:")) { hideDropHighlight(); return false; }

            Point p = s.getDropLocation().getDropPoint(); // accordion 좌표
            int boundaryY = computeGroupBoundaryY(p);
            showDropHighlightAt(accordion, boundaryY);
            return true;
        }

        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                Long movedId = Long.parseLong(getStringData(s));
                Point p = s.getDropLocation().getDropPoint();

                int fromIndex = findGroupIndexById(movedId);
                if (fromIndex < 0) { hideDropHighlight(); return false; }

                int toIndex = computeGroupTargetIndex(p);
                if (toIndex == fromIndex) { hideDropHighlight(); return false; }
                if (toIndex > fromIndex) toIndex--;

                setUiBusy(true);
                int finalIndex = toIndex;
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        bookmarkGroupService.reorderBookmarkGroups(movedId, finalIndex);
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
                JOptionPane.showMessageDialog(MainFrameV2.this, "그룹 순서 변경 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }

    // 그룹 경계선 Y(accordion 좌표)
    private int computeGroupBoundaryY(Point dropPointInAccordion) {
        Rectangle lastRect = null;
        for (Component c : accordion.getComponents()) {
            if (c instanceof GroupSection gs) {
                Rectangle r = gs.getBounds();
                int midY = r.y + r.height / 2;
                if (dropPointInAccordion.y < midY) return r.y;
                lastRect = r;
            }
        }
        return lastRect != null ? lastRect.y + lastRect.height : 0;
    }

    // 그룹 타겟 인덱스
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

    // =================== Bookmark DnD ===================

    /** 북마크 행: Export + Import(행/사이/행 위 드롭 모두 허용) */
    private final class BookmarkRowTransferHandler extends TransferHandler {
        private final long groupId;
        private final long bookmarkId;
        private final JPanel listPanel;

        BookmarkRowTransferHandler(long groupId, long bookmarkId, JPanel listPanel) {
            this.groupId = groupId; this.bookmarkId = bookmarkId; this.listPanel = listPanel;
        }

        // Export
        @Override protected Transferable createTransferable(JComponent c) {
            return new StringSelection("B:" + groupId + ":" + bookmarkId);
        }
        @Override public int getSourceActions(JComponent c) { return MOVE; }
        @Override protected void exportDone(JComponent source, Transferable data, int action) {
            hideDropHighlight();
        }

        // Import(같은 그룹의 북마크만 허용)
        @Override public boolean canImport(TransferSupport s) {
            if (!(s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor))) {
                hideDropHighlight(); return false;
            }
            String payload = getStringData(s);
            if (payload == null || !payload.startsWith("B:")) { hideDropHighlight(); return false; }
            String[] parts = payload.split(":");
            if (parts.length != 3) { hideDropHighlight(); return false; }
            long fromGroup = parseLongSafe(parts[1], -1);
            if (fromGroup != groupId) { hideDropHighlight(); return false; }

            // 하이라이트(좌표 변환: 현재 컴포넌트 → listPanel)
            Point pInComp = s.getDropLocation().getDropPoint();
            Point pInList = SwingUtilities.convertPoint((Component)s.getComponent(), pInComp, listPanel);
            int boundaryY = computeBookmarkBoundaryY(listPanel, pInList);
            showDropHighlightAt(listPanel, boundaryY);
            return true;
        }

        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                String payload = getStringData(s);
                String[] parts = payload.split(":");
                long movedBookmarkId = parseLongSafe(parts[2], -1);

                Point pInComp = s.getDropLocation().getDropPoint();
                Point pInList = SwingUtilities.convertPoint((Component)s.getComponent(), pInComp, listPanel);

                int fromIndex = findBookmarkIndexById(listPanel, movedBookmarkId);
                if (fromIndex < 0) { hideDropHighlight(); return false; }

                int toIndex = computeBookmarkTargetIndex(listPanel, pInList);
                if (toIndex == fromIndex) { hideDropHighlight(); return false; }
                if (toIndex > fromIndex) toIndex--;

                setUiBusy(true);
                int finalIndex = toIndex;
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        // NOTE: API 시그니처 (groupId, prevId, toIndex)
                        // prevId = 이동할 북마크 ID 로 사용
                        bookmarkService.reorderBookmark(groupId, movedBookmarkId, finalIndex);
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
                JOptionPane.showMessageDialog(MainFrameV2.this, "북마크 순서 변경 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }

    /** 리스트 패널(빈 공간) Import 핸들러 */
    private final class BookmarkListImportHandler extends TransferHandler {
        private final long groupId;
        private final JPanel listPanel;

        BookmarkListImportHandler(long groupId, JPanel listPanel) {
            this.groupId = groupId; this.listPanel = listPanel;
        }

        @Override public boolean canImport(TransferSupport s) {
            if (!(s.isDrop() && s.isDataFlavorSupported(DataFlavor.stringFlavor))) {
                hideDropHighlight(); return false;
            }
            String payload = getStringData(s);
            if (payload == null || !payload.startsWith("B:")) { hideDropHighlight(); return false; }
            String[] parts = payload.split(":");
            if (parts.length != 3) { hideDropHighlight(); return false; }
            long fromGroup = parseLongSafe(parts[1], -1);
            if (fromGroup != groupId) { hideDropHighlight(); return false; }

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
                long movedBookmarkId = parseLongSafe(parts[2], -1);

                Point p = s.getDropLocation().getDropPoint(); // listPanel 기준

                int fromIndex = findBookmarkIndexById(listPanel, movedBookmarkId);
                if (fromIndex < 0) { hideDropHighlight(); return false; }

                int toIndex = computeBookmarkTargetIndex(listPanel, p);
                if (toIndex == fromIndex) { hideDropHighlight(); return false; }
                if (toIndex > fromIndex) toIndex--;

                setUiBusy(true);
                int finalIndex = toIndex;
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() {
                        bookmarkService.reorderBookmark(groupId, movedBookmarkId, finalIndex);
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
                JOptionPane.showMessageDialog(MainFrameV2.this, "북마크 순서 변경 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
                return false;
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
                gs.addBookmarkBtn.setEnabled(!busy);
                gs.renameGroupBtn.setEnabled(!busy);
                gs.deleteGroupBtn.setEnabled(!busy);
                gs.orderCombo.setEnabled(!busy);
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
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(new Color(130, 130, 140));
        l.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var p = new JPanel(new BorderLayout());
        p.add(l, BorderLayout.CENTER);
        p.setOpaque(false);
        return p;
    }

    private static void stylizeButton(AbstractButton b) {
        try { b.putClientProperty("JButton.buttonType", "roundRect"); } catch (Exception ignored) {}
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
}
