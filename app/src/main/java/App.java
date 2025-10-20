import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.FlatLightLaf;
import config.AppConfig;

import config.MicroStreamConfig;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ui.MainFrameV3;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Timer;

public class App {

    private static final int LOCK_PORT = 9876; // 임의의 포트
    private static ServerSocket lockSocket;

    private static MainFrameV3 frame;
    private static TrayIcon trayIcon;
    private static final int TOP_RIGHT_MARGIN = 16; // 모서리에서 띄울 여백(px)
    private static final int SHOW_DELAY_MS = 280;   // 파일 다이얼로그와 z-order 경합 완화 지연

    private static Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception{

        if (!acquireLock()) {
            // 이미 실행 중이면 기존 인스턴스에 메시지만 보내고 종료
            sendToRunningInstance();
            System.exit(0);
            return;
        }

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
        boolean dev = false;

        UIManager.put("Component.arc", 14);
        UIManager.put("Button.arc", 16);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);

        FlatLightLaf.setup(); // 다크모드는 FlatDarkLaf.setup()

        Thread.sleep(100);
        SwingUtilities.invokeLater(() -> {
            frame = new MainFrameV3(bookmarkService,bookmarkGroupService);
            frame.setAlwaysOnTop(true);
            frame.setVisible(false);
            setupSystemTray();
            if(dev){
                new DevFrame((RootData) storage.root(),storage).setVisible(true);
            }
        });

        // === 네이티브 메시징 stdin 루프(안정화) ===
        startIPCServer();
        //sendReadySignalToExtension();
        // Native Messaging stdin 루프
        listenToNativeMessages();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            storage.shutdown();
        }));
    }
    private static boolean acquireLock() {
        try {
            lockSocket = new ServerSocket(LOCK_PORT);
            return true;
        } catch (IOException e) {
            return false; // 포트가 이미 사용 중 = 앱이 실행 중
        }
    }

    private static void sendToRunningInstance() {
        try (Socket socket = new Socket("localhost", LOCK_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("SHOW_WINDOW");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startIPCServer() {
        new Thread(() -> {
            try {
                while (true) {
                    Socket client = lockSocket.accept();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(client.getInputStream())
                    );
                    String command = in.readLine();

                    if ("SHOW_WINDOW".equals(command)) {
                        SwingUtilities.invokeLater(App::showWindowTopRight);
                    }

                    client.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "IPC-Server").start();
    }

    private static void listenToNativeMessages() {
        var mapper = new ObjectMapper();
        var in = new DataInputStream(System.in);

        try {
            while (true) {
                byte[] len4 = new byte[4];
                in.readFully(len4);
                int len = ByteBuffer.wrap(len4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (len <= 0) continue;

                byte[] payload = new byte[len];
                in.readFully(payload);

                Map<?, ?> msg = mapper.readValue(payload, Map.class);
                String type = String.valueOf(msg.get("type"));

                if ("FILE_DIALOG_OPENING".equals(type)) {
                    SwingUtilities.invokeLater(App::showWindowTopRight);
                }
            }
        } catch (EOFException eof) {
            // 정상 종료
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setupSystemTray() {
        if (!SystemTray.isSupported()) {
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

            trayIcon = new TrayIcon(image, "파일 경로 북마크", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> showWindowTopRight());

            tray.add(trayIcon);
        } catch (Exception ex) {
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
            g.setColor(new Color(8, 76, 182)); // 보라
            g.fillOval(0, 0, size - 1, size - 1);

            // 글자 F
            g.setColor(Color.WHITE);
            g.setFont(new Font("Dialog", Font.BOLD, 12));
            g.drawString("B", 5, 12);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void showWindowTopRight() {

        new javax.swing.Timer(SHOW_DELAY_MS, ev -> {
            GraphicsDevice target = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            Rectangle wa = workAreaOf(target);

            int x = wa.x + wa.width - 320 - TOP_RIGHT_MARGIN;
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
        }) {{
            setRepeats(false);
            start();
        }};
    }

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

    private static void hideWindow() {
        if (frame == null) return;
        frame.setVisible(false);
    }

    private static void sendReadySignalToExtension() {
        try {
            var mapper = new ObjectMapper();
            Map<String, Object> readyMsg = Map.of(
                    "type", "APP_READY",
                    "timestamp", System.currentTimeMillis()
            );

            byte[] json = mapper.writeValueAsBytes(readyMsg);
            ByteBuffer buf = ByteBuffer.allocate(4 + json.length)
                    .order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(json.length);
            buf.put(json);

            System.out.write(buf.array());
            System.out.flush();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
