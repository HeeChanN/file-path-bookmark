# UI 개선 제안 (반복 #1)

## 분석 일시
2025-10-17 (첫 번째 분석)

## 전체 평가
**현재 UI의 Notion 유사도**: 5/10

**주요 강점**:
- 아코디언 방식의 그룹 토글 구조가 Notion과 유사
- DnD 기능이 잘 구현되어 있음
- 기본적인 계층 구조가 명확함
- 컨텍스트 메뉴(우클릭) 기능 구현됨

**주요 약점**:
- Notion의 특징적인 색상 스키마가 적용되지 않음 (기본 Swing 색상 사용)
- 호버 시 부드러운 배경색 변화 효과 없음
- 패딩과 간격이 Notion보다 좁고 답답함
- 폰트가 Notion의 깔끔한 느낌과 거리가 있음

---

## 개선사항 #1: Notion 색상 스키마 적용
**우선순위**: High
**예상 작업 시간**: 20분
**카테고리**: 색상

### 문제점
현재 UI는 기본 Swing 색상(`UIManager.getColor("Panel.background")`)을 사용하고 있습니다. Notion의 따뜻하고 미니멀한 색상 스키마(`#F7F6F3` 배경, `#37352F` 텍스트)가 적용되지 않아 차갑고 기계적인 느낌을 줍니다.

**MainFrameV2.java**에서:
- 배경: 기본 Swing 배경색 (흰색 또는 시스템 기본)
- 구분선: `getSeparatorColor()` - 연한 회색 (230, 230, 235)
- 텍스트: 기본 검정

### 개선 방향
Notion 라이트 테마의 핵심 색상을 정확히 적용:
- 배경: `#F7F6F3` (247, 246, 243) - 따뜻한 베이지
- 텍스트: `#37352F` (55, 53, 47) - 부드러운 다크 그레이
- 구분선: `#E8E7E3` (232, 231, 227) - 미묘한 보더
- 호버: `#E8E7E3` (232, 231, 227) - Notion 특유의 호버색

### 구현 세부사항

- **파일**: `app/src/main/java/ui/MainFrameV2.java`

  - **변경 1**: 클래스 상단에 Notion 색상 상수 추가
    ```java
    // Before
    public class MainFrameV2 extends JFrame {
        private final BookmarkService bookmarkService;
        // ...

    // After
    public class MainFrameV2 extends JFrame {
        // Notion 라이트 테마 색상
        private static final Color NOTION_BG = new Color(247, 246, 243);
        private static final Color NOTION_TEXT = new Color(55, 53, 47);
        private static final Color NOTION_HOVER = new Color(232, 231, 227);
        private static final Color NOTION_BORDER = new Color(232, 231, 227);
        private static final Color NOTION_ACCENT = new Color(35, 131, 226);

        private final BookmarkService bookmarkService;
        // ...
    ```

  - **변경 2**: 메인 윈도우 배경색 설정 (생성자)
    ```java
    // Before (line 70)
    setLayout(new BorderLayout());
    loadWindowPrefs();

    // After
    setLayout(new BorderLayout());
    getContentPane().setBackground(NOTION_BG);
    loadWindowPrefs();
    ```

  - **변경 3**: 아코디언 컨테이너 배경색 설정 (line 78 근처)
    ```java
    // Before
    accordion.setLayout(new BoxLayout(accordion, BoxLayout.Y_AXIS));
    scroll = new JScrollPane(accordion);

    // After
    accordion.setLayout(new BoxLayout(accordion, BoxLayout.Y_AXIS));
    accordion.setBackground(NOTION_BG);
    scroll = new JScrollPane(accordion);
    scroll.getViewport().setBackground(NOTION_BG);
    ```

  - **변경 4**: 툴바 배경색 (buildToolbar 메서드, line 111)
    ```java
    // Before
    private JComponent buildToolbar() {
        var bar = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();

    // After
    private JComponent buildToolbar() {
        var bar = new JPanel(new GridBagLayout());
        bar.setBackground(NOTION_BG);
        var gbc = new GridBagConstraints();
    ```

  - **변경 5**: GroupSection 헤더 배경색 (line 202)
    ```java
    // Before
    header.setBackground(UIManager.getColor("Panel.background"));

    // After
    header.setBackground(NOTION_BG);
    ```

  - **변경 6**: BookmarkRow 배경색 (line 357)
    ```java
    // Before
    setBackground(UIManager.getColor("Panel.background"));

    // After
    setBackground(NOTION_BG);
    ```

  - **변경 7**: getSeparatorColor() 메서드 수정 (line 990)
    ```java
    // Before
    private Color getSeparatorColor() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = new Color(230, 230, 235);
        return c;
    }

    // After
    private Color getSeparatorColor() {
        return NOTION_BORDER;
    }
    ```

  - **변경 8**: 상태바 배경색 (line 91)
    ```java
    // Before
    statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, getSeparatorColor()));

    // After
    statusBar.setBackground(NOTION_BG);
    statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, getSeparatorColor()));
    ```

### 기대 효과
- Notion 특유의 따뜻하고 미니멀한 느낌 구현
- 시각적 피로도 감소
- 전문적이고 현대적인 디자인
- Notion 유사도: 5/10 → 7/10

---

## 개선사항 #2: 호버 효과 추가
**우선순위**: High
**예상 작업 시간**: 30분
**카테고리**: 인터랙션

### 문제점
현재 BookmarkRow와 GroupSection 헤더에 마우스를 올렸을 때 시각적 피드백이 없습니다. Notion의 가장 큰 특징 중 하나는 모든 항목에 부드러운 호버 효과(배경색 변화)가 있다는 점입니다.

### 개선 방향
- 북마크 행에 마우스를 올리면 배경색이 `NOTION_HOVER`로 부드럽게 변화
- 그룹 헤더에 마우스를 올리면 동일한 효과
- 자연스러운 전환을 위해 Timer 기반 애니메이션 추가 (선택적)

### 구현 세부사항

- **파일**: `app/src/main/java/ui/MainFrameV2.java`

  - **변경 1**: BookmarkRow에 호버 리스너 추가 (생성자 내부, line 400 근처)
    ```java
    // Before (마지막 부분)
    add(moreBtn, gbc);
    }

    // After
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
    ```

  - **변경 2**: GroupSection 헤더에 호버 효과 추가 (line 260 근처)
    ```java
    // Before (header.add(moreBtn, gbc); 이후)
    add(header, BorderLayout.NORTH);

    // After
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
    ```

### 기대 효과
- 사용자에게 명확한 인터랙션 피드백 제공
- Notion과 동일한 직관적인 UX
- 클릭 가능한 영역 시각적으로 명확히 표현
- Notion 유사도: 7/10 → 8.5/10

---

## 개선사항 #3: 패딩 및 간격 조정
**우선순위**: Medium
**예상 작업 시간**: 15분
**카테고리**: 레이아웃

### 문제점
현재 UI는 Notion에 비해 패딩이 좁아 답답한 느낌을 줍니다:
- BookmarkRow 패딩: `(4, 4, 4, 4)` - 너무 좁음
- GroupSection 헤더 패딩: `(6, 8, 6, 8)` - 수직 패딩 부족
- 콘텐츠 들여쓰기: `(2, 24, 4, 12)` - 너무 작음

Notion은 여유있는 공간을 통해 가독성과 편안함을 제공합니다.

### 개선 방향
Notion의 패딩 값에 맞춰 조정:
- 북마크 행: 상하 6-8px, 좌우 10-12px
- 그룹 헤더: 상하 10px, 좌우 12px
- 콘텐츠 들여쓰기: 좌측 32px (더 명확한 계층)

### 구현 세부사항

- **파일**: `app/src/main/java/ui/MainFrameV2.java`

  - **변경 1**: BookmarkRow 패딩 증가 (line 359-362)
    ```java
    // Before
    setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
    ));

    // After
    setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
    ));
    ```

  - **변경 2**: GroupSection 헤더 패딩 증가 (line 256)
    ```java
    // Before
    gbc.insets = new Insets(6, 8, 6, 8);

    // After
    gbc.insets = new Insets(10, 12, 10, 12);
    ```

  - **변경 3**: 콘텐츠 들여쓰기 조정 (line 268)
    ```java
    // Before
    content.setBorder(BorderFactory.createEmptyBorder(2, 24, 4, 12));

    // After
    content.setBorder(BorderFactory.createEmptyBorder(4, 32, 8, 12));
    ```

  - **변경 4**: 툴바 패딩 조정 (line 113)
    ```java
    // Before
    gbc.insets = new Insets(8, 8, 8, 8);

    // After
    gbc.insets = new Insets(12, 12, 12, 12);
    ```

### 기대 효과
- 더 여유있고 읽기 편한 레이아웃
- Notion의 미니멀하고 공간을 활용한 디자인 구현
- 계층 구조가 더 명확해짐
- 전반적인 UI 품질 향상
- Notion 유사도: 8.5/10 → 9/10

---

## 개선사항 상태
✅ **개선사항 #1**: Notion 색상 스키마 적용 - **완료**
✅ **개선사항 #2**: 호버 효과 추가 - **완료**
✅ **개선사항 #3**: 패딩 및 간격 조정 - **완료**

**현재 Notion 유사도**: 9/10

---

# 추가 UI 개선 제안 (반복 #4-6)

## 분석 일시
2025-10-17 (추가 분석)

## 전체 평가
기본적인 Notion 스타일은 매우 잘 구현되어 있습니다. 이제 더 세밀한 디테일과 사용성 개선에 집중합니다.

**추가로 개선 가능한 영역**:
- 아이콘 크기 및 스타일 개선
- 선택 상태(Selected) 표시 추가
- 북마크 행의 더 나은 시각적 계층

---

## 개선사항 #4: 토글 아이콘 및 More 버튼 스타일 개선
**우선순위**: High
**예상 작업 시간**: 20분
**카테고리**: 인터랙션/시각 디자인

### 문제점
현재 구현:
- 토글 버튼: "▸"와 "▾" 문자 사용 (line 234, 338)
- More 버튼: "⋯" 문자 사용 (line 204, 373)
- 버튼이 기본 Swing 스타일이라 Notion의 미니멀한 느낌과 다름
- 호버 시 배경색 변화가 없어 클릭 가능 여부가 불명확

Notion의 특징:
- 아이콘 버튼은 기본적으로 투명
- 호버 시에만 연한 회색 원형 배경 표시
- 크기가 일정 (보통 24×24px)

### 개선 방향
1. 토글 버튼 스타일 개선:
```java
toggle.setBorderPainted(false);
toggle.setContentAreaFilled(false);
toggle.setFocusPainted(false);
toggle.setPreferredSize(new Dimension(24, 24));
```

2. More 버튼에 호버 효과 추가:
```java
stylizeIconButton(moreBtn);
moreBtn.setPreferredSize(new Dimension(24, 24));
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
```

### 기대 효과
- 인터랙티브 요소의 시각적 피드백 강화
- Notion의 미니멀하고 깔끔한 아이콘 버튼 스타일 재현
- 사용성 향상

---

## 개선사항 #5: 아이템 선택 상태 시각화
**우선순위**: Medium
**예상 작업 시간**: 25분
**카테고리**: 인터랙션

### 문제점
현재 구현에는 선택(Selected) 상태가 없습니다:
- 북마크를 클릭해도 선택 표시가 없음
- 어떤 항목에 포커스가 있는지 알 수 없음
- Notion은 선택된 항목에 더 진한 배경색 표시

### 개선 방향
1. BookmarkRow에 선택 상태 추가:
```java
private boolean selected = false;
private static final Color NOTION_SELECTED = new Color(224, 224, 224);

// 클릭 시 선택 상태 토글
addMouseListener(new MouseAdapter() {
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
            selected = !selected;
            updateBackground();
        } else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
            openBookmarkPath(bm.getPath());
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (!selected) setBackground(NOTION_HOVER);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        updateBackground();
    }
});

private void updateBackground() {
    setBackground(selected ? NOTION_SELECTED : NOTION_BG);
}
```

### 기대 효과
- 현재 포커스된 항목 명확히 표시
- Notion과 동일한 선택 인터랙션
- 키보드 네비게이션 준비

---

## 개선사항 #6: 힌트 텍스트 색상 및 스타일 통일
**우선순위**: Low
**예상 작업 시간**: 10분
**카테고리**: 색상/타이포그래피

### 문제점
현재 힌트 텍스트:
- emptyHint() 메서드에서 `new Color(120, 120, 130)` 사용 (line 914)
- Notion의 회색 텍스트 색상과 다름
- 일관성 없는 색상 사용

Notion의 힌트 텍스트:
- 색상: `#9B9A97` (155, 154, 151) - 따뜻한 회색
- 크기: 본문보다 약간 작음

### 개선 방향
1. Notion 힌트 색상 상수 추가:
```java
private static final Color NOTION_HINT = new Color(155, 154, 151);
```

2. emptyHint() 메서드 수정:
```java
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
```

### 기대 효과
- 색상 팔레트 일관성 향상
- Notion의 따뜻한 색감 완벽 재현
- 가독성 및 계층 구조 명확화

---

## 개선사항 상태 (추가)
✅ **개선사항 #4**: More 버튼 호버 효과 - **완료** (Iteration #4)
✅ **개선사항 #5**: 토글 버튼 스타일 개선 - **완료** (Iteration #6)
✅ **개선사항 #6**: 힌트 텍스트 색상 통일 - **완료** (Iteration #5)

**현재 Notion 유사도**: 9.5/10

---

# 추가 UI 개선 제안 (반복 #7-9)

## 분석 일시
2025-10-17 (제2차 추가 분석)

## 전체 평가
기본적인 Notion 스타일은 매우 완성도 높게 구현되었습니다. 이제 더 세밀한 폴리싱과 사용성 개선에 집중합니다.

**추가로 개선 가능한 영역**:
- 툴바 버튼 스타일 일관성
- 상태바 스타일 개선
- 그룹 제목 폰트 스타일

---

## 개선사항 #7: 툴바 버튼 Notion 스타일로 개선
**우선순위**: Medium
**예상 작업 시간**: 15분
**카테고리**: 인터랙션/버튼 스타일

### 문제점
현재 툴바 버튼:
- `stylizeButton()` 메서드가 "roundRect" 스타일만 적용 (line 964-966)
- 호버 효과가 없어 인터랙티브하지 않음
- Notion의 플랫하고 미니멀한 버튼 스타일과 다름

Notion 툴바 버튼:
- 기본: 투명 배경 또는 연한 회색
- 호버: NOTION_HOVER 배경색
- 패딩: 6-8px

### 개선 방향
stylizeButton() 메서드에 호버 효과 추가:
```java
private static void stylizeButton(AbstractButton b) {
    b.setBackground(NOTION_BG);
    b.setForeground(NOTION_TEXT);
    b.setBorderPainted(false);
    b.setFocusPainted(false);
    b.setContentAreaFilled(false);
    b.setOpaque(true);

    b.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
            b.setBackground(NOTION_HOVER);
        }
        @Override
        public void mouseExited(MouseEvent e) {
            b.setBackground(NOTION_BG);
        }
    });
}
```

### 기대 효과
- 툴바 버튼의 시각적 피드백 강화
- 전체 UI의 일관성 향상
- Notion과 동일한 인터랙티브 경험

---

## 개선사항 #8: 상태바 텍스트 및 버튼 스타일 개선
**우선순위**: Low
**예상 작업 시간**: 10분
**카테고리**: 색상/타이포그래피

### 문제점
현재 상태바 (line 50-55, 101-109):
- 텍스트 색상이 기본 검정색
- 상태 액션 버튼에 스타일 미적용
- Notion의 부드러운 색감과 다름

### 개선 방향
1. 상태바 라벨에 Notion 텍스트 색상 적용:
```java
statusLabel.setForeground(NOTION_TEXT);
statusLabel.setFont(statusLabel.getFont().deriveFont(13f));
```

2. 상태 액션 버튼 스타일 개선:
```java
stylizeButton(statusActionBtn);
statusActionBtn.setForeground(NOTION_ACCENT);
```

### 기대 효과
- 상태바가 전체 UI와 조화
- Notion의 따뜻한 색감 유지
- 시각적 일관성 향상

---

## 개선사항 #9: 그룹 제목 폰트 스타일 개선
**우선순위**: Low
**예상 작업 시간**: 5분
**카테고리**: 타이포그래피

### 문제점
현재 그룹 제목 (line 257-258):
- `title.getFont().deriveFont(Font.PLAIN, title.getFont().getSize2D()+1)`
- 크기만 1pt 증가, 굵기는 PLAIN
- Notion은 그룹 제목이 약간 더 굵음 (Medium weight)

### 개선 방향
그룹 제목 폰트를 Medium weight으로 변경:
```java
title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
title.setForeground(NOTION_TEXT);
```

### 기대 효과
- 그룹 제목이 더 명확하게 구분됨
- 계층 구조의 시각적 명확성 향상
- Notion의 타이포그래피 완벽 재현

---

## 개선사항 상태 (제2차)
✅ **개선사항 #7**: 툴바 버튼 Notion 스타일 개선 - **완료** (Iteration #7)
✅ **개선사항 #8**: 상태바 스타일 개선 - **완료** (Iteration #8)
✅ **개선사항 #9**: 그룹 제목 폰트 스타일 개선 - **완료** (Iteration #9)

**현재 Notion 유사도**: 9.7/10

---

# 추가 UI 개선 제안 (반복 #10-12)

## 분석 일시
2025-10-17 (제3차 추가 분석)

## 전체 평가
Notion 스타일이 거의 완벽하게 구현되었습니다. 이제 최종 폴리싱과 세밀한 UX 개선에 집중합니다.

**추가로 개선 가능한 영역**:
- 북마크 라벨 폰트 크기 조정
- 팝업 메뉴 스타일 개선
- 스크롤바 스타일 커스터마이징

---

## 개선사항 #10: 북마크 라벨 폰트 크기 조정
**우선순위**: Low
**예상 작업 시간**: 5분
**카테고리**: 타이포그래피

### 문제점
현재 북마크 라벨 (line 425):
- `nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN))`
- 폰트 크기가 기본값으로 설정되어 있음
- Notion은 북마크 항목이 14px 크기로 표시됨
- 현재는 크기 지정 없이 시스템 기본값 사용

### 개선 방향
북마크 라벨의 폰트 크기를 명시적으로 설정:
```java
nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 14f));
nameLabel.setForeground(NOTION_TEXT);
```

### 구현 세부사항
- **파일**: `app/src/main/java/ui/MainFrameV2.java`
- **위치**: BookmarkRow 생성자, line 425
- **변경**:
  ```java
  // Before
  nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN));

  // After
  nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 14f));
  nameLabel.setForeground(NOTION_TEXT);
  ```

### 기대 효과
- 북마크 텍스트의 가독성 향상
- Notion의 타이포그래피 완벽 재현
- 일관된 폰트 크기로 전문적인 느낌

---

## 개선사항 #11: 팝업 메뉴(JPopupMenu) 스타일 개선
**우선순위**: Medium
**예상 작업 시간**: 20분
**카테고리**: 인터랙션/메뉴

### 문제점
현재 팝업 메뉴:
- 기본 Swing 스타일 사용
- Notion의 부드러운 색감과 다름
- 메뉴 아이템에 호버 효과가 기본 스타일
- 패딩과 간격이 좁음

Notion의 컨텍스트 메뉴:
- 부드러운 배경색 (NOTION_BG)
- 호버 시 NOTION_HOVER
- 여유있는 패딩 (6-8px)
- 아이콘과 텍스트 간격 명확

### 개선 방향
팝업 메뉴 스타일을 Notion 스타일로 변경하는 메서드 추가:
```java
private static void stylizePopupMenu(JPopupMenu menu) {
    menu.setBackground(NOTION_BG);
    menu.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(NOTION_BORDER, 1),
        BorderFactory.createEmptyBorder(4, 0, 4, 0)
    ));

    for (Component c : menu.getComponents()) {
        if (c instanceof JMenuItem item) {
            item.setBackground(NOTION_BG);
            item.setForeground(NOTION_TEXT);
            item.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            item.setOpaque(true);

            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    item.setBackground(NOTION_HOVER);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    item.setBackground(NOTION_BG);
                }
            });
        }
    }
}
```

그리고 모든 `buildGroupPopupMenu()` 및 `buildRowPopupMenu()` 메서드에서 메뉴 반환 전에 적용:
```java
private JPopupMenu buildGroupPopupMenu() {
    JPopupMenu menu = new JPopupMenu();
    // ... 메뉴 아이템 추가 ...
    stylizePopupMenu(menu);
    return menu;
}
```

### 기대 효과
- 팝업 메뉴가 전체 UI와 조화
- Notion의 일관된 인터랙션 경험
- 시각적 완성도 향상

---

## 개선사항 #12: 스크롤바 스타일 커스터마이징
**우선순위**: Low
**예상 작업 시간**: 15분
**카테고리**: UI 폴리싱

### 문제점
현재 스크롤바:
- 기본 Swing 스크롤바 사용
- Notion의 얇고 미니멀한 스크롤바와 다름
- 호버 전에는 거의 보이지 않는 Notion 스타일 미구현

Notion의 스크롤바:
- 평상시: 매우 얇고 반투명 (약 6-8px)
- 호버 시: 약간 더 진하게 표시
- 배경과 잘 어울리는 색상

### 개선 방향
JScrollPane의 스크롤바에 커스텀 UI 적용:
```java
// 생성자에서 scroll 생성 후
scroll.getVerticalScrollBar().setUI(new NotionScrollBarUI());
scroll.getVerticalScrollBar().setUnitIncrement(16);

// 커스텀 스크롤바 UI 클래스
private static class NotionScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
        this.thumbColor = new Color(155, 154, 151, 100); // NOTION_HINT with alpha
        this.trackColor = NOTION_BG;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private JButton createZeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        return button;
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(thumbColor);
        g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                         thumbBounds.width - 4, thumbBounds.height - 4, 4, 4);
        g2.dispose();
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        // 트랙을 그리지 않음 (Notion 스타일)
    }
}
```

### 기대 효과
- Notion과 동일한 미니멀한 스크롤바
- UI의 최종 폴리싱
- 전문적이고 현대적인 느낌 완성

---

## 개선사항 상태 (제3차)
✅ **개선사항 #10**: 북마크 라벨 폰트 크기 조정 - **완료** (Iteration #10)
✅ **개선사항 #11**: 팝업 메뉴 스타일 개선 - **완료** (Iteration #11)
✅ **개선사항 #12**: 스크롤바 스타일 커스터마이징 - **완료** (Iteration #12)

**현재 Notion 유사도**: 9.8/10

---

# 최종 UI 개선 제안 (반복 #13-15)

## 분석 일시
2025-10-17 (제4차 최종 분석)

## 전체 평가
Notion 스타일이 매우 잘 구현되었습니다. 사용자 피드백을 반영하여 최종 폴리싱을 진행합니다.

**사용자 요청사항**:
- 프레임 크기를 Notion 북마크처럼 가로가 짧고 세로가 긴 형태로 변경
- 각 row의 높이를 Notion Sidebar처럼 간격을 촘촘하게 조정

**추가로 개선 가능한 영역**:
- 프레임 기본 크기 조정
- 북마크 행 패딩 및 높이 최적화
- 그룹 헤더 패딩 조정

---

## 개선사항 #13: 프레임 크기를 Notion Sidebar 비율로 조정
**우선순위**: High
**예상 작업 시간**: 5분
**카테고리**: 레이아웃/윈도우 크기

### 문제점
현재 프레임 크기 (line 76):
- `setSize(900, 600)` - 가로 900px, 세로 600px
- 일반적인 가로가 넓은 형태
- Notion Sidebar는 가로가 좁고 세로가 긴 형태 (약 280-320px × 700-800px)

### 개선 방향
Notion Sidebar 비율에 맞춰 프레임 크기 조정:
```java
setSize(320, 720); // 가로:세로 = 약 1:2.25 (Notion Sidebar 비율)
```

### 구현 세부사항
- **파일**: `app/src/main/java/ui/MainFrameV2.java`
- **위치**: 생성자, line 76
- **변경**:
  ```java
  // Before
  setSize(900, 600);

  // After
  setSize(320, 720); // Notion Sidebar 비율
  ```

### 기대 효과
- Notion 북마크 앱과 동일한 세로로 긴 형태
- 사이드바 형태의 전문적인 느낌
- 데스크탑 화면에서 효율적인 공간 사용

---

## 개선사항 #14: 북마크 행 높이를 Notion처럼 촘촘하게 조정
**우선순위**: High
**예상 작업 시간**: 10분
**카테고리**: 레이아웃/패딩

### 문제점
현재 북마크 행 패딩 (line 418):
- `BorderFactory.createEmptyBorder(8, 12, 8, 12)` - 상하 8px
- Notion Sidebar는 상하 패딩이 더 작음 (약 4-6px)
- 행 간격이 너무 넓어 촘촘한 느낌이 부족

### 개선 방향
북마크 행의 상하 패딩을 줄여 Notion처럼 촘촘하게:
```java
setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()),
    BorderFactory.createEmptyBorder(4, 12, 4, 12) // 상하 8px → 4px
));
```

### 구현 세부사항
- **파일**: `app/src/main/java/ui/MainFrameV2.java`
- **위치**: BookmarkRow 생성자, line 416-419
- **변경**:
  ```java
  // Before
  setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()),
      BorderFactory.createEmptyBorder(8, 12, 8, 12)
  ));

  // After
  setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()),
      BorderFactory.createEmptyBorder(4, 12, 4, 12)
  ));
  ```

### 기대 효과
- Notion Sidebar와 동일한 촘촘한 행 간격
- 더 많은 북마크를 한 화면에 표시 가능
- 깔끔하고 밀도 있는 UI

---

## 개선사항 #15: 그룹 헤더 패딩 조정
**우선순위**: Medium
**예상 작업 시간**: 5분
**카테고리**: 레이아웃/패딩

### 문제점
현재 그룹 헤더 패딩 (line 301):
- `new Insets(10, 12, 10, 12)` - 상하 10px
- 북마크 행과의 비율을 맞추기 위해 약간 줄일 필요

### 개선 방향
그룹 헤더의 상하 패딩을 약간 줄여 균형 맞추기:
```java
gbc.insets = new Insets(8, 12, 8, 12); // 상하 10px → 8px
```

### 구현 세부사항
- **파일**: `app/src/main/java/ui/MainFrameV2.java`
- **위치**: GroupSection 생성자, line 301
- **변경**:
  ```java
  // Before
  gbc.insets = new Insets(10, 12, 10, 12);

  // After
  gbc.insets = new Insets(8, 12, 8, 12);
  ```

### 기대 효과
- 그룹 헤더와 북마크 행의 시각적 균형
- 전체적으로 더 촘촘하고 일관된 느낌
- Notion Sidebar의 밀도 있는 레이아웃 완성

---

## 개선사항 상태 (제4차)
✅ **개선사항 #13**: 프레임 크기를 Notion Sidebar 비율로 조정 - **완료** (Iteration #13)
✅ **개선사항 #14**: 북마크 행 높이를 Notion처럼 촘촘하게 조정 - **완료** (Iteration #14)
✅ **개선사항 #15**: 그룹 헤더 패딩 조정 - **완료** (Iteration #15)

**현재 Notion 유사도**: 9.9/10

---

# 최종 폴리싱 UI 개선 제안 (반복 #16-18)

## 분석 일시
2025-10-17 (제5차 최종 폴리싱)

## 전체 평가
Notion 스타일이 거의 완벽하게 구현되었습니다. 이제 마지막 미세 조정과 추가 사용성 개선을 진행합니다.

**개선 가능한 영역**:
- 북마크 행 구분선 제거 또는 더 연하게 조정
- emptyHint 텍스트 크기 및 간격 조정
- 상태바 패딩 및 폰트 크기 미세 조정

---

## 개선사항 #16: 북마크 행 구분선을 더 연하게 조정
**우선순위**: Low
**예상 작업 시간**: 5분
**카테고리**: 시각 디자인/구분선

### 문제점
현재 북마크 행 구분선 (line 420):
- `BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor())`
- NOTION_BORDER 색상 사용 (232, 231, 227)
- Notion은 행 구분선이 거의 보이지 않거나 더 연한 색상 사용

### 개선 방향
구분선을 제거하거나 더 연한 색상으로 변경:
```java
// 옵션 1: 구분선 제거
setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

// 옵션 2: 더 연한 구분선 (추천)
private static final Color NOTION_SEPARATOR = new Color(240, 240, 238);
setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createMatteBorder(0, 0, 1, 0, NOTION_SEPARATOR),
    BorderFactory.createEmptyBorder(4, 12, 4, 12)
));
```

### 구현 세부사항
- **파일**: `app/src/main/java/ui/MainFrameV2.java`
- **위치**: BookmarkRow 생성자, line 419-422
- **변경**: 구분선 제거 (옵션 1 선택)
  ```java
  // Before
  setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 0, 1, 0, getSeparatorColor()),
      BorderFactory.createEmptyBorder(4, 12, 4, 12)
  ));

  // After
  setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
  ```

### 기대 효과
- 더 깔끔하고 미니멀한 느낌
- Notion과 동일한 시각적 경험
- 구분선 없이도 호버 효과로 항목 구분 가능

---

## 개선사항 #17: emptyHint 텍스트 좌측 패딩 증가
**우선순위**: Low
**예상 작업 시간**: 3분
**카테고리**: 레이아웃/패딩

### 문제점
현재 emptyHint 텍스트 (line 967):
- `BorderFactory.createEmptyBorder(6, 8, 6, 8)` - 좌측 8px
- 북마크 행의 좌측 패딩(12px)과 정렬이 맞지 않음
- Notion은 힌트 텍스트가 북마크와 동일한 들여쓰기

### 개선 방향
힌트 텍스트의 좌측 패딩을 북마크와 동일하게 조정:
```java
l.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));
```

### 구현 세부사항
- **파일**: `app/src/main/java/ui/MainFrameV2.java`
- **위치**: emptyHint() 메서드, line 967
- **변경**:
  ```java
  // Before
  l.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

  // After
  l.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));
  ```

### 기대 효과
- 힌트 텍스트가 북마크와 정렬됨
- 시각적 일관성 향상
- 더 정돈된 느낌

---

## 개선사항 #18: 상태바 좌측 패딩 증가
**우선순위**: Low
**예상 작업 시간**: 3분
**카테고리**: 레이아웃/패딩

### 문제점
현재 상태바 (line 105-114):
- statusLabel과 statusActionBtn의 좌우 패딩 없음
- 텍스트가 가장자리에 너무 붙어있음
- Notion은 상태바에도 좌우 패딩 적용

### 개선 방향
상태바에 좌우 패딩 추가:
```java
statusBar.setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createMatteBorder(1, 0, 0, 0, getSeparatorColor()),
    BorderFactory.createEmptyBorder(8, 12, 8, 12)
));
```

### 구현 세부사항
- **파일**: `app/src/main/java/ui/MainFrameV2.java`
- **위치**: 생성자, line 105
- **변경**:
  ```java
  // Before
  statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, getSeparatorColor()));

  // After
  statusBar.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(1, 0, 0, 0, getSeparatorColor()),
      BorderFactory.createEmptyBorder(8, 12, 8, 12)
  ));
  ```

### 기대 효과
- 상태바 텍스트가 더 여유있게 표시됨
- 전체 UI와 일관된 패딩
- Notion의 세심한 디테일 완성

---

## 다음 단계
**우선 구현할 개선사항**: #16 (북마크 행 구분선 제거)

**이유**:
- 가장 즉각적인 시각적 개선
- Notion의 미니멀한 느낌 완성
- 간단한 변경으로 큰 효과
