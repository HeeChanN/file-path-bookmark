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

## 다음 단계
**우선 구현할 개선사항**: #1 (Notion 색상 스키마 적용)

**이유**:
- 색상은 UI의 첫인상을 결정하는 가장 중요한 요소
- 작업 시간이 짧고(20분) 효과가 즉각적으로 나타남
- 후속 개선사항(#2, #3)의 기반이 됨
- Notion 유사도를 빠르게 5/10 → 7/10으로 끌어올릴 수 있음
- 기존 기능에 영향을 주지 않는 안전한 변경
