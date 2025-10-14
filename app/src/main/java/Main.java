import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class Main {
    private static JFrame frame;
    private static TrayIcon trayIcon;
    private static boolean systemTrayReady = false;

    // 설정: true면 "마우스가 있는 모니터의 우상단", false면 "기본 모니터의 우상단"
    private static final boolean SHOW_ON_MOUSE_DISPLAY = false;
    private static final int TOP_RIGHT_MARGIN = 16; // 모서리에서 띄울 여백(px)
    private static final int SHOW_DELAY_MS = 280;   // 파일 다이얼로그와 z-order 경합 완화 지연

    public static void main(String[] args) throws Exception {
        // GUI 초기화 (창은 만들되, 기본은 숨김)
        hookFileLogging();
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("Helper");
            frame.setSize(340, 200);
            frame.setUndecorated(false);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            frame.add(new JLabel("파일 선택 도우미", SwingConstants.CENTER), BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(false); // 시작 시 창 숨김

            setupSystemTray();       // 트레이 아이콘 준비
            if (!systemTrayReady) {
                // 트레이를 못 쓰는 환경이면 폴백: 창을 보이게
                frame.setVisible(true);
            }
        });

        // === 네이티브 메시징 stdin 루프(안정화) ===
        var mapper = new ObjectMapper();
        var in = new DataInputStream(System.in);

        try {
            while (true) {
                // 1) 길이 4바이트(LE) 정확히 읽기
                byte[] len4 = new byte[4];
                in.readFully(len4); // 4바이트 다 읽을 때까지 블록
                int len = ByteBuffer.wrap(len4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (len <= 0) continue;

                // 2) 페이로드 정확히 읽기
                byte[] payload = new byte[len];
                in.readFully(payload);

                Map<?, ?> msg = mapper.readValue(payload, Map.class);
                String type = String.valueOf(msg.get("type"));

                // (선택) BG 디버깅용 ACK
                sendAck("ACK", type);

                if ("FILE_DIALOG_OPENING".equals(type)) {
                    SwingUtilities.invokeLater(Main::showWindowTopRight);
                } else if ("FILE_DIALOG_CLOSED".equals(type)) {
                    SwingUtilities.invokeLater(Main::hideWindow);
                }
            }
        } catch (EOFException eof) {
            // 포트가 끊어졌을 때 자연스러운 종료
        } catch (Exception e) {
            e.printStackTrace(); // 표준오류로 남겨두기
        }
    }

    /* ===== Tray & Window helpers ===== */

    private static void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            systemTrayReady = false;
            return;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();

            // 간단한 16x16 아이콘 생성(초록 동그라미에 'F')
            Image image = createTrayImage();

            PopupMenu menu = new PopupMenu();

            MenuItem openItem = new MenuItem("열기");
            openItem.addActionListener((ActionEvent e) -> showWindowTopRight());
            menu.add(openItem);

            MenuItem hideItem = new MenuItem("숨기기");
            hideItem.addActionListener((ActionEvent e) -> hideWindow());
            menu.add(hideItem);

            menu.addSeparator();

            MenuItem exitItem = new MenuItem("종료");
            exitItem.addActionListener((ActionEvent e) -> {
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
                System.exit(0);
            });
            menu.add(exitItem);

            trayIcon = new TrayIcon(image, "파일 선택 도우미", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> showWindowTopRight()); // 더블클릭/클릭 시 열기

            tray.add(trayIcon);
            systemTrayReady = true;
        } catch (Exception ex) {
            systemTrayReady = false;
        }
    }

    private static Image createTrayImage() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 배경 투명
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, size, size);

            // 원형 배지
            g.setColor(new Color(60, 180, 75)); // 초록
            g.fillOval(0, 0, size - 1, size - 1);

            // 글자 F
            g.setColor(Color.WHITE);
            g.setFont(new Font("Dialog", Font.BOLD, 12));
            g.drawString("F", 5, 12);
        } finally {
            g.dispose();
        }
        return img;
    }

    /* ===== 위치 계산 유틸 ===== */

    // (옵션) 마우스가 위치한 모니터를 찾기
    private static GraphicsDevice deviceForMouseIfNeeded() {
        if (!SHOW_ON_MOUSE_DISPLAY) {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        }
        try {
            Point p = MouseInfo.getPointerInfo() != null ? MouseInfo.getPointerInfo().getLocation() : null;
            if (p != null) {
                for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                    Rectangle b = d.getDefaultConfiguration().getBounds();
                    if (b.contains(p)) return d;
                }
            }
        } catch (Exception ignore) {}
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    // 작업영역(태스크바/메뉴바 제외) 계산
    private static Rectangle workAreaOf(GraphicsDevice gd) {
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                b.x + in.left,
                b.y + in.top,
                b.width - in.left - in.right,
                b.height - in.top - in.bottom
        );
    }

    /* ===== 창 표시/숨김 ===== */

    // 우상단 모서리에 띄우기 (지연 + z-order 보정 포함)
    private static void showWindowTopRight() {
        if (frame == null) return;

        new javax.swing.Timer(SHOW_DELAY_MS, ev -> {
            GraphicsDevice target = deviceForMouseIfNeeded();
            Rectangle wa = workAreaOf(target);

            int x = wa.x + wa.width - frame.getWidth() - TOP_RIGHT_MARGIN;
            int y = wa.y + TOP_RIGHT_MARGIN;

            frame.setLocation(x, y);

            if (!frame.isVisible()) frame.setVisible(true);
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) {
                frame.setExtendedState(Frame.NORMAL);
            }

            // z-order를 확실히 올리기 위한 시퀀스
            frame.setAlwaysOnTop(false);
            frame.setAlwaysOnTop(true);
            frame.toFront();
            frame.requestFocus();
            frame.requestFocusInWindow();

            System.err.printf("[FPB][HOST] show at top-right (%d,%d) in %s%n",
                    x, y, target.getIDstring());
        }) {{
            setRepeats(false);
            start();
        }};
    }

    private static void hideWindow() {
        if (frame == null) return;
        frame.setVisible(false);
    }

    // ===== (선택) BG 콘솔에서 수신 확인용 ACK =====
    private static void sendAck(String kind, String gotType) {
        try {
            var mapper = new ObjectMapper();
            byte[] json = mapper.writeValueAsBytes(Map.of("type", kind, "got", gotType, "ts", System.currentTimeMillis()));
            ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(json.length);
            System.out.write(buf.array());
            System.out.write(json);
            System.out.flush(); // 중요
        } catch (Exception ignore) {
        }
    }

    private static void hookFileLogging() {
        try {
            // 윈도우: %LOCALAPPDATA%\FilePickerHelper\logs\host.log
            String appLocal = System.getenv("LOCALAPPDATA");
            Path dir = (appLocal != null)
                    ? Paths.get(appLocal, "FilePickerHelper", "logs")
                    : Paths.get(System.getProperty("user.home"), "FilePickerHelper", "logs");
            Files.createDirectories(dir);

            // 간단한 롤링(5MB 넘으면 yyyyMMdd-HHmm 로그로 백업)
            Path log = dir.resolve("host.log");
            long max = 5L * 1024 * 1024;
            if (Files.exists(log) && Files.size(log) > max) {
                String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
                Files.move(log, dir.resolve("host-" + ts + ".log"), StandardCopyOption.REPLACE_EXISTING);
            }

            // System.err 을 파일로 리다이렉트 (UTF-8, auto-flush)
            PrintStream ps = new PrintStream(new FileOutputStream(log.toFile(), true), true, "UTF-8");
            System.setErr(ps);

            // 시작 로그 + 전역 예외 핸들러
            System.err.println("=== Host started " + LocalDateTime.now() + " ===");
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                System.err.println("[UNCAUGHT] thread=" + t.getName());
                e.printStackTrace();
            });
        } catch (Exception ignore) {
            // 마지막 수단: 아무 것도 하지 않음 (로그 훅 실패해도 앱은 계속)
        }
    }
}
