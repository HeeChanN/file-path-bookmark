package ui;

import model.Bookmark;
import model.BookmarkGroup;
import service.bookmark.BookmarkService;
import service.bookmark_group.BookmarkGroupService;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 한 패널에 '그룹 헤더(토글) + 해당 그룹 북마크 목록'을 아코디언 방식으로 표시하는 메인 프레임.
 */
public class MainFrame extends JFrame {

    private final BookmarkService bookmarkService;
    private final BookmarkGroupService bookmarkGroupService;

    // 상단 툴바 구성 요소
    private final JButton addGroupBtn = new JButton("그룹 추가");
    private final JButton expandAllBtn = new JButton("모두 펼치기");
    private final JButton collapseAllBtn = new JButton("모두 접기");

    // 중앙: 아코디언 컨테이너(세로 흐름)
    private final JPanel accordion = new JPanel();

    public MainFrame(BookmarkService bookmarkService, BookmarkGroupService bookmarkGroupService) {
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
        accordion.removeAll();

        List<BookmarkGroup> groups = bookmarkGroupService.getBookmarkGroups();
        if (groups.isEmpty()) {
            accordion.add(emptyHint("그룹이 없습니다. [그룹 추가] 버튼으로 시작해 보세요."));
        } else {
            for (BookmarkGroup g : groups) {
                accordion.add(new GroupSection(g));
                accordion.add(Box.createVerticalStrut(8));
            }
        }

        accordion.add(Box.createVerticalGlue());
        accordion.revalidate();
        accordion.repaint();
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
        private final JPanel content = new JPanel();

        GroupSection(BookmarkGroup group) {
            super(new BorderLayout());
            this.group = group;

            // 헤더 영역
            var header = new JPanel(new GridBagLayout());
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1,1,1,1, new Color(230,230,235)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            header.setBackground(new Color(248, 248, 252));

            toggle.setText("▸"); // 접힘 표시 (펼침 시 ▾ 로 전환)
            toggle.setSelected(true);
            updateToggleGlyph();
            stylizeButton(addBookmarkBtn);

            title.setText(group.getName());
            title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D()+1));

            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 4, 0, 4);
            gbc.gridx=0; gbc.gridy=0; gbc.anchor = GridBagConstraints.WEST;
            header.add(toggle, gbc);

            gbc.gridx=1; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
            header.add(title, gbc);

            gbc.gridx=2; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE;
            header.add(addBookmarkBtn, gbc);

            add(header, BorderLayout.NORTH);

            // 콘텐츠(북마크 리스트 패널)
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 12));
            add(content, BorderLayout.CENTER);

            // 최초 로드
            reloadBookmarks();

            // 동작
            toggle.addActionListener(e -> {
                updateToggleGlyph();
                content.setVisible(toggle.isSelected());
                revalidate();
            });

            addBookmarkBtn.addActionListener(e -> promptAddBookmark());
        }

        void setExpanded(boolean expanded) {
            toggle.setSelected(expanded);
            updateToggleGlyph();
            content.setVisible(expanded);
            revalidate();
        }

        private void updateToggleGlyph() {
            toggle.setText(toggle.isSelected() ? "▾" : "▸");
        }

        private void promptAddBookmark() {
            String path = JOptionPane.showInputDialog(MainFrame.this, "파일 또는 폴더 경로를 입력하세요");
            if (path == null || path.isBlank()) return;

            Path p = Path.of(path);
            String defaultName = p.getFileName() != null ? p.getFileName().toString() : path;
            String displayName = JOptionPane.showInputDialog(MainFrame.this, "표시 이름(생략 가능)", defaultName);
            if (displayName == null) return;

            try {
                Bookmark saved = bookmarkService.createBookmark(group.getId(), displayName, path);
                // 항목을 새로 그리되, 이 섹션만 갱신
                addBookmarkItem(saved);
                content.revalidate();
                content.repaint();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(MainFrame.this, "북마크 생성 실패: " + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
            }
        }

        /** 이 섹션의 북마크 목록을 다시 그림 */
        private void reloadBookmarks() {
            content.removeAll();

            List<Bookmark> bookmarks = group.getBookmarks(); // 서비스 경유로 바꾸고 싶다면 사용처 맞게 수정
            if (bookmarks == null || bookmarks.isEmpty()) {
                content.add(emptyHint("이 그룹에 북마크가 없습니다."));
            } else {
                for (Bookmark b : bookmarks) {
                    addBookmarkItem(b);
                    content.add(Box.createVerticalStrut(6));
                }
            }
        }

        private void addBookmarkItem(Bookmark b) {
            var row = new BookmarkRow(b, group);
            content.add(row);
        }
    }

    /** 북마크 한 줄 표시(이름 굵게 + 경로 회색), [복사], [삭제] 버튼 */
    private final class BookmarkRow extends JPanel {
        private final Bookmark bm;
        private final BookmarkGroup group;

        BookmarkRow(Bookmark bm, BookmarkGroup group) {
            super(new GridBagLayout());
            this.bm = bm; this.group = group;

            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1,1,1,1, new Color(235,235,240)),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            JLabel label = new JLabel(
                    "<html><b>" + esc(bm.getDisplayName()) + "</b><br>" +
                            "<span style='font-size:10px;color:gray'>" + esc(bm.getLinkPath()) + "</span></html>"
            );

            JButton copyBtn = new JButton("복사");
            JButton delBtn  = new JButton("삭제");
            stylizeButton(copyBtn);
            stylizeButton(delBtn);

            // 레이아웃
            var gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 4, 2, 4);
            gbc.gridx=0; gbc.gridy=0; gbc.weightx=1.0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor=GridBagConstraints.WEST;
            add(label, gbc);

            gbc.gridx=1; gbc.weightx=0; gbc.fill = GridBagConstraints.NONE; gbc.anchor=GridBagConstraints.EAST;
            add(copyBtn, gbc);
            gbc.gridx=2;
            add(delBtn, gbc);

            // 동작
            copyBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(bm.getLinkPath()), null);
                JOptionPane.showMessageDialog(MainFrame.this, "경로를 클립보드에 복사했습니다.");
            });

            delBtn.addActionListener(e -> {
                int r = JOptionPane.showConfirmDialog(MainFrame.this,
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
                    JOptionPane.showMessageDialog(MainFrame.this, "이미 삭제되었거나 존재하지 않습니다.");
                    // 상위 섹션만 리빌드하고 싶으면, 가장 가까운 GroupSection 찾아 reload 호출해도 됨.
                }
            });
        }
    }

    // ----- 유틸 -----
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
        // FlatLaf 있을 때는 둥근 모양 / 없으면 기본
        try {
            b.putClientProperty("JButton.buttonType", "roundRect"); // FlatClientProperties.BUTTON_TYPE_ROUND_RECT
        } catch (Exception ignored) {}
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
