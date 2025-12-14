import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ChatClientGUI {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private final BlockingQueue<Frame> inbound = new LinkedBlockingQueue<>();
    private final BlockingQueue<Frame> sendQueue = new LinkedBlockingQueue<>();

    private final AtomicInteger seq = new AtomicInteger(1);
    private volatile boolean running = false;
    private volatile String nick = null;
    private volatile String room = "lobby";
    private volatile boolean isKorean = true;
    private volatile boolean isDarkTheme = false;
    private volatile int fontSize = 13;
    private volatile boolean autoScroll = true;
    private volatile boolean soundEnabled = true;
    private final AtomicInteger pendingUIUpdates = new AtomicInteger(0);

    private Thread readerThread;
    private Thread writerThread;
    private ScheduledExecutorService heartbeatScheduler;
    private ExecutorService uiUpdateExecutor;
    private ExecutorService networkExecutor;

    private int nextSeq() { return seq.getAndIncrement(); }

    private JFrame frame;
    private JPanel mainPanel;
    private JSplitPane splitPane;

    private JTextField hostField;
    private JTextField portField;
    private JTextField nickField;
    private JButton connectBtn;
    private JButton disconnectBtn;

    private JTextField roomField;
    private JButton joinBtn;
    private JButton leaveBtn;
    private JButton refreshRoomsBtn;
    private JButton refreshUsersBtn;
    private JButton historyBtn;

    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendBtn;

    private JList<String> roomList;
    private JList<String> userList;
    private DefaultListModel<String> roomListModel;
    private DefaultListModel<String> userListModel;
    
    private final java.util.List<String> chatHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.Set<String> bookmarkedMessages = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet();
    private final java.util.Set<String> blockedUsers = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet();
    private final java.util.Set<String> friends = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet();

    private JLabel statusLabel;
    private JLabel hostLabel;
    private JLabel portLabel;
    private JLabel nickLabel;
    private JLabel roomLabel;
    private JLabel roomsLabel;
    private JLabel usersLabel;

    private String getText(String key) {
        if (isKorean) {
            return switch (key) {
                case "title" -> "채팅 클라이언트";
                case "host" -> "호스트";
                case "port" -> "포트";
                case "nick" -> "닉네임";
                case "room" -> "방";
                case "connect" -> "연결";
                case "disconnect" -> "연결 해제";
                case "join" -> "입장";
                case "leave" -> "퇴장";
                case "refreshRooms" -> "방 새로고침";
                case "refreshUsers" -> "사용자 새로고침";
                case "history" -> "히스토리";
                case "send" -> "전송";
                case "rooms" -> "방 목록";
                case "users" -> "사용자 목록";
                case "status" -> "상태";
                case "disconnected" -> "연결 안 됨";
                case "connecting" -> "연결 중...";
                case "connected" -> "연결됨";
                case "language" -> "언어";
                case "korean" -> "한국어";
                case "english" -> "English";
                case "theme" -> "테마";
                case "lightTheme" -> "밝은 테마";
                case "darkTheme" -> "어두운 테마";
                case "fontSize" -> "폰트 크기";
                case "view" -> "보기";
                case "autoScroll" -> "자동 스크롤";
                case "clearChat" -> "채팅 지우기";
                case "settings" -> "설정";
                case "soundNotification" -> "소리 알림";
                default -> key;
            };
        } else {
            return switch (key) {
                case "title" -> "Chat Client";
                case "host" -> "Host";
                case "port" -> "Port";
                case "nick" -> "Nickname";
                case "room" -> "Room";
                case "connect" -> "Connect";
                case "disconnect" -> "Disconnect";
                case "join" -> "Join";
                case "leave" -> "Leave";
                case "refreshRooms" -> "Refresh Rooms";
                case "refreshUsers" -> "Refresh Users";
                case "history" -> "History";
                case "send" -> "Send";
                case "rooms" -> "Rooms";
                case "users" -> "Users";
                case "status" -> "Status";
                case "disconnected" -> "Disconnected";
                case "connecting" -> "Connecting...";
                case "connected" -> "Connected";
                case "language" -> "Language";
                case "korean" -> "한국어";
                case "english" -> "English";
                case "theme" -> "Theme";
                case "lightTheme" -> "Light Theme";
                case "darkTheme" -> "Dark Theme";
                case "fontSize" -> "Font Size";
                case "view" -> "View";
                case "autoScroll" -> "Auto Scroll";
                case "clearChat" -> "Clear Chat";
                case "settings" -> "Settings";
                case "soundNotification" -> "Sound Notification";
                default -> key;
            };
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new ChatClientGUI().showUI());
    }

    private void showUI() {
        frame = new JFrame(getText("title"));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        uiUpdateExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "UI-Update");
            t.setDaemon(true);
            return t;
        });
        
        networkExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Network");
            t.setDaemon(true);
            return t;
        });

        JMenuBar menuBar = new JMenuBar();
        
        JMenu langMenu = new JMenu(getText("language"));
        JMenuItem koreanItem = new JMenuItem(getText("korean"));
        JMenuItem englishItem = new JMenuItem(getText("english"));
        koreanItem.addActionListener(e -> {
            isKorean = true;
            updateLanguage();
        });
        englishItem.addActionListener(e -> {
            isKorean = false;
            updateLanguage();
        });
        langMenu.add(koreanItem);
        langMenu.add(englishItem);
        
        JMenu themeMenu = new JMenu(isKorean ? "테마" : "Theme");
        JMenuItem lightThemeItem = new JMenuItem(isKorean ? "밝은 테마" : "Light Theme");
        JMenuItem darkThemeItem = new JMenuItem(isKorean ? "어두운 테마" : "Dark Theme");
        lightThemeItem.addActionListener(e -> {
            isDarkTheme = false;
            applyTheme();
        });
        darkThemeItem.addActionListener(e -> {
            isDarkTheme = true;
            applyTheme();
        });
        themeMenu.add(lightThemeItem);
        themeMenu.add(darkThemeItem);
        
        JMenu fontMenu = new JMenu(isKorean ? "폰트 크기" : "Font Size");
        JMenuItem smallFontItem = new JMenuItem("10");
        JMenuItem mediumFontItem = new JMenuItem("13");
        JMenuItem largeFontItem = new JMenuItem("16");
        JMenuItem xlargeFontItem = new JMenuItem("20");
        smallFontItem.addActionListener(e -> setFontSize(10));
        mediumFontItem.addActionListener(e -> setFontSize(13));
        largeFontItem.addActionListener(e -> setFontSize(16));
        xlargeFontItem.addActionListener(e -> setFontSize(20));
        fontMenu.add(smallFontItem);
        fontMenu.add(mediumFontItem);
        fontMenu.add(largeFontItem);
        fontMenu.add(xlargeFontItem);
        
        JMenu viewMenu = new JMenu(isKorean ? "보기" : "View");
        JCheckBoxMenuItem autoScrollItem = new JCheckBoxMenuItem(isKorean ? "자동 스크롤" : "Auto Scroll", autoScroll);
        JMenuItem clearChatItem = new JMenuItem(isKorean ? "채팅 지우기" : "Clear Chat");
        autoScrollItem.addActionListener(e -> autoScroll = autoScrollItem.isSelected());
        clearChatItem.addActionListener(e -> clearChat());
        viewMenu.add(autoScrollItem);
        viewMenu.addSeparator();
        viewMenu.add(clearChatItem);
        
        JMenu settingsMenu = new JMenu(isKorean ? "설정" : "Settings");
        JCheckBoxMenuItem soundItem = new JCheckBoxMenuItem(isKorean ? "소리 알림" : "Sound Notification", soundEnabled);
        soundItem.addActionListener(e -> soundEnabled = soundItem.isSelected());
        settingsMenu.add(soundItem);
        
        JMenu messageMenu = new JMenu(isKorean ? "메시지" : "Message");
        JMenuItem searchItem = new JMenuItem(isKorean ? "검색" : "Search");
        JMenuItem bookmarkItem = new JMenuItem(isKorean ? "북마크 보기" : "View Bookmarks");
        JMenuItem exportItem = new JMenuItem(isKorean ? "기록 내보내기" : "Export Chat");
        JMenuItem importItem = new JMenuItem(isKorean ? "기록 가져오기" : "Import Chat");
        searchItem.addActionListener(e -> showSearchDialog());
        bookmarkItem.addActionListener(e -> showBookmarks());
        exportItem.addActionListener(e -> exportChatHistory());
        importItem.addActionListener(e -> importChatHistory());
        messageMenu.add(searchItem);
        messageMenu.add(bookmarkItem);
        messageMenu.addSeparator();
        messageMenu.add(exportItem);
        messageMenu.add(importItem);
        
        JMenu userMenu = new JMenu(isKorean ? "사용자" : "User");
        JMenuItem blockItem = new JMenuItem(isKorean ? "사용자 차단" : "Block User");
        JMenuItem unblockItem = new JMenuItem(isKorean ? "차단 해제" : "Unblock User");
        JMenuItem addFriendItem = new JMenuItem(isKorean ? "친구 추가" : "Add Friend");
        JMenuItem removeFriendItem = new JMenuItem(isKorean ? "친구 제거" : "Remove Friend");
        JMenuItem friendsItem = new JMenuItem(isKorean ? "친구 목록" : "Friends");
        JMenuItem profileItem = new JMenuItem(isKorean ? "프로필 설정" : "Set Profile");
        blockItem.addActionListener(e -> blockUser());
        unblockItem.addActionListener(e -> unblockUser());
        addFriendItem.addActionListener(e -> addFriend());
        removeFriendItem.addActionListener(e -> removeFriend());
        friendsItem.addActionListener(e -> showFriendsList());
        profileItem.addActionListener(e -> setUserProfile());
        userMenu.add(blockItem);
        userMenu.add(unblockItem);
        userMenu.addSeparator();
        userMenu.add(addFriendItem);
        userMenu.add(removeFriendItem);
        userMenu.addSeparator();
        userMenu.add(friendsItem);
        userMenu.add(profileItem);
        
        JMenu roomMenu = new JMenu(isKorean ? "방" : "Room");
        JMenuItem createRoomItem = new JMenuItem(isKorean ? "방 생성" : "Create Room");
        JMenuItem deleteRoomItem = new JMenuItem(isKorean ? "방 삭제" : "Delete Room");
        JMenuItem roomSettingsItem = new JMenuItem(isKorean ? "방 설정" : "Room Settings");
        createRoomItem.addActionListener(e -> createRoom());
        deleteRoomItem.addActionListener(e -> deleteRoom());
        roomSettingsItem.addActionListener(e -> showRoomSettings());
        roomMenu.add(createRoomItem);
        roomMenu.add(deleteRoomItem);
        roomMenu.addSeparator();
        roomMenu.add(roomSettingsItem);
        
        JMenu statsMenu = new JMenu(isKorean ? "통계" : "Statistics");
        JMenuItem messageStatsItem = new JMenuItem(isKorean ? "메시지 통계" : "Message Stats");
        JMenuItem activityStatsItem = new JMenuItem(isKorean ? "활동 통계" : "Activity Stats");
        messageStatsItem.addActionListener(e -> showMessageStats());
        activityStatsItem.addActionListener(e -> showActivityStats());
        statsMenu.add(messageStatsItem);
        statsMenu.add(activityStatsItem);
        
        menuBar.add(langMenu);
        menuBar.add(themeMenu);
        menuBar.add(fontMenu);
        menuBar.add(viewMenu);
        menuBar.add(messageMenu);
        menuBar.add(userMenu);
        menuBar.add(roomMenu);
        menuBar.add(statsMenu);
        menuBar.add(settingsMenu);
        frame.setJMenuBar(menuBar);

        mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(245, 245, 250));

        mainPanel.add(buildTopPanel(), BorderLayout.NORTH);
        mainPanel.add(buildCenterPanel(), BorderLayout.CENTER);
        mainPanel.add(buildBottomPanel(), BorderLayout.SOUTH);

        setConnectedUI(false, getText("disconnected"));

        frame.setContentPane(mainPanel);
        frame.setSize(1100, 750);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void updateLanguage() {
        frame.setTitle(getText("title"));
        hostLabel.setText(getText("host"));
        portLabel.setText(getText("port"));
        nickLabel.setText(getText("nick"));
        roomLabel.setText(getText("room"));
        roomsLabel.setText(getText("rooms"));
        usersLabel.setText(getText("users"));
        connectBtn.setText(getText("connect"));
        disconnectBtn.setText(getText("disconnect"));
        joinBtn.setText(getText("join"));
        leaveBtn.setText(getText("leave"));
        refreshRoomsBtn.setText(getText("refreshRooms"));
        refreshUsersBtn.setText(getText("refreshUsers"));
        historyBtn.setText(getText("history"));
        sendBtn.setText(getText("send"));
        
        if (!running) {
            statusLabel.setText(getText("disconnected"));
        }
    }

    private JPanel buildTopPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(255, 255, 255));
        p.setBorder(new LineBorder(new Color(220, 220, 230), 1, true));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;

        hostField = new JTextField("127.0.0.1", 12);
        portField = new JTextField("5555", 6);
        nickField = new JTextField("", 10);
        styleTextField(hostField);
        styleTextField(portField);
        styleTextField(nickField);

        connectBtn = new JButton(getText("connect"));
        disconnectBtn = new JButton(getText("disconnect"));
        styleButton(connectBtn, new Color(76, 175, 80));
        styleButton(disconnectBtn, new Color(244, 67, 54));

        roomField = new JTextField("lobby", 10);
        joinBtn = new JButton(getText("join"));
        leaveBtn = new JButton(getText("leave"));
        refreshRoomsBtn = new JButton(getText("refreshRooms"));
        refreshUsersBtn = new JButton(getText("refreshUsers"));
        historyBtn = new JButton(getText("history"));
        styleTextField(roomField);
        styleButton(joinBtn, new Color(33, 150, 243));
        styleButton(leaveBtn, new Color(255, 152, 0));
        styleButton(refreshRoomsBtn, new Color(156, 39, 176));
        styleButton(refreshUsersBtn, new Color(156, 39, 176));
        styleButton(historyBtn, new Color(103, 58, 183));

        connectBtn.addActionListener(e -> onConnect());
        disconnectBtn.addActionListener(e -> onDisconnect());
        joinBtn.addActionListener(e -> onJoinRoom());
        leaveBtn.addActionListener(e -> onLeaveRoom());
        refreshRoomsBtn.addActionListener(e -> onRefreshRooms());
        refreshUsersBtn.addActionListener(e -> onRefreshUsers());
        historyBtn.addActionListener(e -> onHistory());

        hostLabel = new JLabel(getText("host"));
        portLabel = new JLabel(getText("port"));
        nickLabel = new JLabel(getText("nick"));
        roomLabel = new JLabel(getText("room"));
        styleLabel(hostLabel);
        styleLabel(portLabel);
        styleLabel(nickLabel);
        styleLabel(roomLabel);
    
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        p.add(hostLabel, c);
        c.gridx = 1; c.weightx = 0.3;
        p.add(hostField, c);

        c.gridx = 2; c.weightx = 0;
        p.add(portLabel, c);
        c.gridx = 3; c.weightx = 0.15;
        p.add(portField, c);

        c.gridx = 4; c.weightx = 0;
        p.add(nickLabel, c);
        c.gridx = 5; c.weightx = 0.2;
        p.add(nickField, c);

        c.gridx = 6; c.weightx = 0;
        p.add(connectBtn, c);
        c.gridx = 7;
        p.add(disconnectBtn, c);
      
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        p.add(roomLabel, c);
        c.gridx = 1; c.weightx = 0.2;
        p.add(roomField, c);
        c.gridx = 2; c.weightx = 0;
        p.add(joinBtn, c);
        c.gridx = 3; c.weightx = 0;
        p.add(leaveBtn, c);
        c.gridx = 4; c.weightx = 0;
        p.add(refreshRoomsBtn, c);
        c.gridx = 5; c.weightx = 0;
        p.add(refreshUsersBtn, c);
        c.gridx = 6; c.weightx = 0;
        p.add(historyBtn, c);

        statusLabel = new JLabel(getText("status"));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        statusLabel.setForeground(new Color(100, 100, 100));
        c.gridx = 0; c.gridy = 2; c.gridwidth = 8; c.weightx = 1.0;
        p.add(statusLabel, c);

        return p;
    }

    private JPanel buildCenterPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(Color.WHITE);
        chatPanel.setBorder(new LineBorder(new Color(220, 220, 230), 1, true));
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        chatArea.setBackground(new Color(250, 250, 255));
        chatArea.setForeground(new Color(30, 30, 30));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(new Color(250, 250, 255));
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        sidePanel.setPreferredSize(new Dimension(220, 0));
        sidePanel.setBackground(new Color(255, 255, 255));

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        roomList.setBackground(new Color(250, 250, 255));
        roomList.setSelectionBackground(new Color(100, 181, 246));
        roomList.setSelectionForeground(Color.WHITE);
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && roomList.getSelectedValue() != null) {
                String selectedRoom = roomList.getSelectedValue();
                roomField.setText(selectedRoom);
            }
        });

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        userList.setBackground(new Color(250, 250, 255));
        userList.setSelectionBackground(new Color(100, 181, 246));
        userList.setSelectionForeground(Color.WHITE);
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && userList.getSelectedValue() != null) {
                    String selectedUser = userList.getSelectedValue();
                    String prompt = isKorean ? selectedUser + "에게 메시지:" : "Message to " + selectedUser + ":";
                    String title = isKorean ? "귓속말" : "Whisper";
                    String msg = JOptionPane.showInputDialog(frame, prompt, title, JOptionPane.QUESTION_MESSAGE);
                    if (msg != null && !msg.trim().isEmpty()) {
                        sendWhisper(selectedUser, msg.trim());
                    }
                }
            }
        });

        JSplitPane userRoomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        userRoomSplit.setTopComponent(new JScrollPane(roomList));
        userRoomSplit.setBottomComponent(new JScrollPane(userList));
        userRoomSplit.setResizeWeight(0.5);
        userRoomSplit.setDividerLocation(0.5);
        userRoomSplit.setBorder(null);

        roomsLabel = new JLabel(getText("rooms"));
        roomsLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        roomsLabel.setForeground(new Color(50, 50, 50));
        roomsLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        usersLabel = new JLabel(getText("users"));
        usersLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        usersLabel.setForeground(new Color(50, 50, 50));
        usersLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel roomPanel = new JPanel(new BorderLayout());
        roomPanel.setBackground(Color.WHITE);
        roomPanel.add(roomsLabel, BorderLayout.NORTH);
        roomPanel.add(userRoomSplit, BorderLayout.CENTER);

        sidePanel.add(roomPanel, BorderLayout.CENTER);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatPanel, sidePanel);
        splitPane.setResizeWeight(0.75);
        splitPane.setDividerLocation(850);
        splitPane.setBorder(null);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(245, 245, 250));
        centerPanel.add(splitPane, BorderLayout.CENTER);
        return centerPanel;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBackground(new Color(255, 255, 255));
        p.setBorder(new LineBorder(new Color(220, 220, 230), 1, true));
        
        inputField = new JTextField();
        styleTextField(inputField);
        inputField.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        
        JButton emojiBtn = new JButton("😀");
        emojiBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        emojiBtn.setToolTipText(isKorean ? "이모지" : "Emoji");
        emojiBtn.addActionListener(e -> showEmojiPicker());
        
        sendBtn = new JButton(getText("send"));
        styleButton(sendBtn, new Color(33, 150, 243));

        sendBtn.addActionListener(e -> onSend());
        inputField.addActionListener(e -> onSend());
        
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '/') {
                    SwingUtilities.invokeLater(() -> showCommandHelp());
                }
            }
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F) {
                    e.consume();
                    showSearchDialog();
                } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_B) {
                    e.consume();
                    String selected = chatArea.getSelectedText();
                    if (selected != null && !selected.trim().isEmpty()) {
                        bookmarkedMessages.add(selected.trim());
                        appendLine(isKorean ? "[시스템] 북마크에 추가되었습니다." : "[SYSTEM] Added to bookmarks.");
                    }
                } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C && e.isShiftDown()) {
                    e.consume();
                    String selected = chatArea.getSelectedText();
                    if (selected != null) {
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(selected), null);
                        appendLine(isKorean ? "[시스템] 복사되었습니다." : "[SYSTEM] Copied to clipboard.");
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_TAB && inputField.getText().startsWith("/")) {
                    e.consume();
                    completeCommand();
                }
            }
        });
        
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem copyItem = new JMenuItem(isKorean ? "복사" : "Copy");
                    JMenuItem bookmarkItem = new JMenuItem(isKorean ? "북마크 추가" : "Add Bookmark");
                    JMenuItem quoteItem = new JMenuItem(isKorean ? "인용" : "Quote");
                    JMenuItem editItem = new JMenuItem(isKorean ? "메시지 수정" : "Edit Message");
                    JMenuItem deleteItem = new JMenuItem(isKorean ? "메시지 삭제" : "Delete Message");
                    
                    copyItem.addActionListener(ev -> {
                        String selected = chatArea.getSelectedText();
                        if (selected != null) {
                            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new java.awt.datatransfer.StringSelection(selected), null);
                        }
                    });
                    
                    bookmarkItem.addActionListener(ev -> {
                        String selected = chatArea.getSelectedText();
                        if (selected != null && !selected.trim().isEmpty()) {
                            bookmarkedMessages.add(selected.trim());
                            appendLine(isKorean ? "[시스템] 북마크에 추가되었습니다." : "[SYSTEM] Added to bookmarks.");
                        }
                    });
                    
                    quoteItem.addActionListener(ev -> {
                        String selected = chatArea.getSelectedText();
                        if (selected != null && !selected.trim().isEmpty() && running) {
                            inputField.setText("> " + selected.trim() + "\n");
                            inputField.requestFocus();
                        }
                    });
                    
                    editItem.addActionListener(ev -> {
                        String selected = chatArea.getSelectedText();
                        if (selected != null && !selected.trim().isEmpty() && running) {
                            editMessage(selected.trim());
                        }
                    });
                    
                    deleteItem.addActionListener(ev -> {
                        String selected = chatArea.getSelectedText();
                        if (selected != null && !selected.trim().isEmpty() && running) {
                            deleteMessage(selected.trim());
                        }
                    });
                    
                    popup.add(copyItem);
                    popup.add(bookmarkItem);
                    popup.add(quoteItem);
                    popup.addSeparator();
                    popup.add(editItem);
                    popup.add(deleteItem);
                    popup.show(chatArea, e.getX(), e.getY());
                }
            }
        });

        JPanel inputPanel = new JPanel(new BorderLayout(3, 0));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(emojiBtn, BorderLayout.EAST);
        
        p.add(inputPanel, BorderLayout.CENTER);
        p.add(sendBtn, BorderLayout.EAST);
        return p;
    }
    
    private void styleTextField(JTextField field) {
        field.setBorder(new LineBorder(new Color(200, 200, 210), 1));
        field.setBackground(Color.WHITE);
        field.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
    }
    
    private void styleButton(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("맑은 고딕", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(color.darker());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(color);
            }
        });
    }
    
    private void styleLabel(JLabel label) {
        label.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        label.setForeground(new Color(60, 60, 60));
    }
    
    private void showCommandHelp() {
        StringBuilder helpText = new StringBuilder();
        if (isKorean) {
            helpText.append("=== 명령어 목록 ===\n\n");
            helpText.append("/rooms\n");
            helpText.append("  방 목록 조회\n\n");
            helpText.append("/roominfo <room>\n");
            helpText.append("  방 정보 조회 (방의 멤버 목록 등)\n\n");
            helpText.append("/users\n");
            helpText.append("  사용자 목록 조회\n\n");
            helpText.append("/userinfo <nick>\n");
            helpText.append("  사용자 정보 조회 (현재 방, 상태 등)\n\n");
            helpText.append("/history [room] [count]\n");
            helpText.append("  채팅 히스토리 조회\n");
            helpText.append("  room: 방 이름 (생략 시 현재 방)\n");
            helpText.append("  count: 조회할 메시지 개수 (기본값: 20)\n\n");
            helpText.append("/w <nick> <msg>\n");
            helpText.append("  귓속말 전송\n\n");
            helpText.append("/join <room>\n");
            helpText.append("  방 입장\n\n");
            helpText.append("/leave\n");
            helpText.append("  방 퇴장\n\n");
            helpText.append("/ping\n");
            helpText.append("  서버에 핑 전송\n\n");
            helpText.append("/quit\n");
            helpText.append("  연결 종료\n");
        } else {
            helpText.append("=== Command List ===\n\n");
            helpText.append("/rooms\n");
            helpText.append("  List all rooms\n\n");
            helpText.append("/roominfo <room>\n");
            helpText.append("  Get room information (members, etc.)\n\n");
            helpText.append("/users\n");
            helpText.append("  List all users\n\n");
            helpText.append("/userinfo <nick>\n");
            helpText.append("  Get user information (current room, status, etc.)\n\n");
            helpText.append("/history [room] [count]\n");
            helpText.append("  Get chat history\n");
            helpText.append("  room: Room name (default: current room)\n");
            helpText.append("  count: Number of messages (default: 20)\n\n");
            helpText.append("/w <nick> <msg>\n");
            helpText.append("  Send whisper\n\n");
            helpText.append("/join <room>\n");
            helpText.append("  Join a room\n\n");
            helpText.append("/leave\n");
            helpText.append("  Leave current room\n\n");
            helpText.append("/ping\n");
            helpText.append("  Send ping to server\n\n");
            helpText.append("/quit\n");
            helpText.append("  Disconnect\n");
        }
        
        JTextArea textArea = new JTextArea(helpText.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        textArea.setBackground(new Color(250, 250, 255));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        scrollPane.setBorder(null);
        
        String title = isKorean ? "명령어 도움말" : "Command Help";
        JOptionPane.showMessageDialog(frame, scrollPane, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void setConnectedUI(boolean connected, String status) {
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        nickField.setEnabled(!connected);

        connectBtn.setEnabled(!connected);
        disconnectBtn.setEnabled(connected);

        roomField.setEnabled(connected);
        joinBtn.setEnabled(connected);
        leaveBtn.setEnabled(connected);
        refreshRoomsBtn.setEnabled(connected);
        refreshUsersBtn.setEnabled(connected);
        historyBtn.setEnabled(connected);

        inputField.setEnabled(connected);
        sendBtn.setEnabled(connected);

        statusLabel.setText(status);
    }

    private void appendLine(String s) {
        if (pendingUIUpdates.incrementAndGet() > 10) {
            pendingUIUpdates.set(0);
        SwingUtilities.invokeLater(() -> {
            chatArea.append(s + "\n");
                if (autoScroll) {
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }
            });
        } else {
            uiUpdateExecutor.execute(() -> {
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(s + "\n");
                    if (autoScroll) {
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                    }
                });
            });
        }
    }
    
    private void clearChat() {
        SwingUtilities.invokeLater(() -> chatArea.setText(""));
    }
    
    private void setFontSize(int size) {
        fontSize = size;
        SwingUtilities.invokeLater(() -> {
            Font currentFont = chatArea.getFont();
            chatArea.setFont(new Font(currentFont.getName(), currentFont.getStyle(), fontSize));
            inputField.setFont(new Font("맑은 고딕", Font.PLAIN, fontSize));
        });
    }
    
    private void applyTheme() {
        SwingUtilities.invokeLater(() -> {
            if (isDarkTheme) {
                mainPanel.setBackground(new Color(30, 30, 30));
                chatArea.setBackground(new Color(40, 40, 45));
                chatArea.setForeground(new Color(220, 220, 220));
                inputField.setBackground(new Color(40, 40, 45));
                inputField.setForeground(new Color(220, 220, 220));
            } else {
                mainPanel.setBackground(new Color(245, 245, 250));
                chatArea.setBackground(new Color(250, 250, 255));
                chatArea.setForeground(new Color(30, 30, 30));
                inputField.setBackground(Color.WHITE);
                inputField.setForeground(new Color(30, 30, 30));
            }
            frame.repaint();
        });
    }
  
    private void onConnect() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String n = nickField.getText().trim();

        if (host.isEmpty() || portText.isEmpty() || n.isEmpty()) {
            String msg = isKorean ? "[UI] 호스트/포트/닉네임이 필요합니다" : "[UI] host/port/nick required";
            appendLine(msg);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (Exception e) {
            String msg = isKorean ? "[UI] 잘못된 포트입니다" : "[UI] invalid port";
            appendLine(msg);
            return;
        }

        setConnectedUI(false, getText("connecting"));
        connectBtn.setEnabled(false);

        networkExecutor.execute(() -> connectAndHandshake(host, port, n));
    }

    private void connectAndHandshake(String host, int port, String n) {
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30000);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            running = true;

            writerThread = new Thread(this::writerLoop, "GUI-Writer");
            readerThread = new Thread(this::readerLoop, "GUI-Reader");
            writerThread.start();
            readerThread.start();

            heartbeatScheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
          
            send(Frame.ofText(MsgType.HELLO, nextSeq(), Kvp.encode(Kvp.kv("client", "swing"))));
            Frame welcome = takeAnyOf(3, TimeUnit.SECONDS, MsgType.WELCOME);
            if (welcome == null) throw new IOException("No WELCOME");

            Map<String, String> welcomeKv = Kvp.decode(welcome.payloadText());
            appendLine(String.format("[SERVER] %s v%s", 
                welcomeKv.getOrDefault("server", "ChatServer"),
                welcomeKv.getOrDefault("version", "1.0")));
           
            send(Frame.ofText(MsgType.LOGIN, nextSeq(), Kvp.encode(Kvp.kv("nick", n))));
            Frame resp = takeAnyOf(5, TimeUnit.SECONDS, MsgType.LOGIN_OK, MsgType.LOGIN_FAIL);
            if (resp == null) throw new IOException("No LOGIN response");

            if (resp.type == MsgType.LOGIN_FAIL) {
                Map<String, String> failKv = Kvp.decode(resp.payloadText());
                appendLine("[LOGIN_FAIL] " + failKv.getOrDefault("reason", "Unknown error"));
                hardDisconnect("login fail");
                SwingUtilities.invokeLater(() -> setConnectedUI(false, getText("disconnected")));
                return;
            }

            Map<String, String> ok = Kvp.decode(resp.payloadText());
            nick = ok.getOrDefault("nick", n);
         
            room = roomField.getText().trim();
            if (room.isEmpty()) room = "lobby";
            send(Frame.ofText(MsgType.JOIN, nextSeq(), Kvp.encode(Kvp.kv("room", room))));

            String statusMsg = isKorean ? 
                "연결됨: " + nick + " / 방=" + room : 
                "connected as " + nick + " / room=" + room;
            SwingUtilities.invokeLater(() -> setConnectedUI(true, statusMsg));
            appendLine("[LOGIN_OK] nick=" + nick);

            onRefreshRooms();
            onRefreshUsers();
        } catch (Exception e) {
            appendLine("[CONNECT_ERROR] " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            hardDisconnect("connect error");
            SwingUtilities.invokeLater(() -> setConnectedUI(false, getText("disconnected")));
        }
    }

    private void sendHeartbeat() {
        if (running) {
            try {
                send(Frame.ofText(MsgType.PING, nextSeq(), Kvp.encode(Kvp.kv("t", Instant.now().toString()))));
            } catch (Exception e) {
            }
        }
    }

    private void onDisconnect() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        send(Frame.ofText(MsgType.LOGOUT, nextSeq(), Kvp.encode(Kvp.kv())));
        hardDisconnect("user disconnect");
        setConnectedUI(false, getText("disconnected"));
        
        if (networkExecutor != null) {
            networkExecutor.shutdown();
            networkExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "Network");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private void onJoinRoom() {
        if (!running) return;
        String r = roomField.getText().trim();
        if (r.isEmpty()) r = "lobby";
        send(Frame.ofText(MsgType.JOIN, nextSeq(), Kvp.encode(Kvp.kv("room", r))));
        Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.JOIN_OK, MsgType.ERROR);
        if (resp != null && resp.type == MsgType.JOIN_OK) {
            Map<String, String> kv = Kvp.decode(resp.payloadText());
            room = kv.getOrDefault("room", r);
            String statusMsg = isKorean ? 
                "연결됨: " + nick + " / 방=" + room : 
                "connected as " + nick + " / room=" + room;
            SwingUtilities.invokeLater(() -> {
                roomField.setText(room);
                setConnectedUI(true, statusMsg);
            });
            String msg = isKorean ? "[JOIN_OK] 방 입장: " : "[JOIN_OK] Joined room: ";
            appendLine(msg + room);
            onRefreshRooms();
        }
    }

    private void onLeaveRoom() {
        if (!running) return;
        send(Frame.ofText(MsgType.LEAVE, nextSeq(), Kvp.encode(Kvp.kv("room", room))));
        Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.LEAVE_OK, MsgType.ERROR);
        if (resp != null && resp.type == MsgType.LEAVE_OK) {
            String msg = isKorean ? "[LEAVE_OK] 방 퇴장: " : "[LEAVE_OK] Left room: ";
            appendLine(msg + room);
            room = "lobby";
            String statusMsg = isKorean ? 
                "연결됨: " + nick + " / 방=" + room : 
                "connected as " + nick + " / room=" + room;
            SwingUtilities.invokeLater(() -> {
                roomField.setText(room);
                setConnectedUI(true, statusMsg);
            });
            onRefreshRooms();
        }
    }

    private void onRefreshRooms() {
        if (!running) return;
        send(Frame.ofText(MsgType.ROOM_LIST, nextSeq(), Kvp.encode(Kvp.kv())));
        networkExecutor.execute(() -> {
            Frame resp = takeType(MsgType.ROOM_LIST_RESP, 3, TimeUnit.SECONDS);
            if (resp != null) {
                Map<String, String> kv = Kvp.decode(resp.payloadText());
                String roomsStr = kv.getOrDefault("rooms", "");
                SwingUtilities.invokeLater(() -> {
                    roomListModel.clear();
                    if (!roomsStr.isEmpty()) {
                        String[] rooms = roomsStr.split(",");
                        for (String r : rooms) {
                            if (!r.trim().isEmpty()) {
                                roomListModel.addElement(r.trim());
                            }
                        }
                    }
                });
            }
        });
    }

    private void onRefreshUsers() {
        if (!running) return;
        send(Frame.ofText(MsgType.USER_LIST, nextSeq(), Kvp.encode(Kvp.kv())));
        networkExecutor.execute(() -> {
            Frame resp = takeType(MsgType.USER_LIST_RESP, 3, TimeUnit.SECONDS);
            if (resp != null) {
                Map<String, String> kv = Kvp.decode(resp.payloadText());
                String usersStr = kv.getOrDefault("users", "");
                SwingUtilities.invokeLater(() -> {
                    userListModel.clear();
                    if (!usersStr.isEmpty()) {
                        String[] users = usersStr.split(",");
                        for (String u : users) {
                            if (!u.trim().isEmpty()) {
                                userListModel.addElement(u.trim());
                            }
                        }
                    }
                });
            }
        });
    }

    private void onHistory() {
        if (!running) return;
        String prompt1 = isKorean ? "방 이름 (비워두면 현재 방):" : "Room name (leave empty for current room):";
        String title = isKorean ? "채팅 히스토리" : "Chat History";
        String roomName = JOptionPane.showInputDialog(frame, prompt1, title, JOptionPane.QUESTION_MESSAGE);
        if (roomName == null) return;
        if (roomName.trim().isEmpty()) roomName = room;
        
        String prompt2 = isKorean ? "메시지 개수:" : "Number of messages:";
        String countStr = JOptionPane.showInputDialog(frame, prompt2, title, JOptionPane.QUESTION_MESSAGE);
        int count = 20;
        if (countStr != null && !countStr.trim().isEmpty()) {
            try {
                count = Integer.parseInt(countStr.trim());
            } catch (NumberFormatException e) {
                String msg = isKorean ? "[ERROR] 잘못된 개수입니다. 기본값 20 사용" : "[ERROR] Invalid count, using default 20";
                appendLine(msg);
            }
        }
        
        send(Frame.ofText(MsgType.CHAT_HISTORY, nextSeq(), 
            Kvp.encode(Kvp.kv("room", roomName, "count", String.valueOf(count)))));
        networkExecutor.execute(() -> {
            Frame resp = takeType(MsgType.CHAT_HISTORY_RESP, 3, TimeUnit.SECONDS);
            if (resp != null) {
                Map<String, String> kv = Kvp.decode(resp.payloadText());
                String messagesStr = kv.getOrDefault("messages", "");
                appendLine(String.format("[HISTORY] Room: %s, Messages: %s", 
                    kv.getOrDefault("room", ""), kv.getOrDefault("count", "0")));
                if (!messagesStr.isEmpty()) {
                    String[] messages = messagesStr.split("\n");
                    for (String msg : messages) {
                        String[] parts = msg.split("\\|", 3);
                        if (parts.length == 3) {
                            appendLine(String.format("  [%s] %s: %s", 
                                parts[1], parts[0], parts[2]));
                        }
                    }
                }
            }
        });
    }

    private void sendWhisper(String to, String msg) {
        if (!running) return;
        send(Frame.ofText(MsgType.WHISPER, nextSeq(), Kvp.encode(Kvp.kv("to", to, "msg", msg))));
    }

    private void onSend() {
        if (!running) return;
        String line = inputField.getText().trim();
        if (line.isEmpty()) return;
        inputField.setText("");

        if (line.startsWith("/quit")) {
            onDisconnect();
            return;
        }

        if (line.startsWith("/join ")) {
            String r = line.substring(6).trim();
            if (r.isEmpty()) r = "lobby";
            roomField.setText(r);
            onJoinRoom();
            return;
        }

        if (line.startsWith("/leave")) {
            onLeaveRoom();
            return;
        }

        if (line.startsWith("/rooms")) {
            onRefreshRooms();
            return;
        }

        if (line.startsWith("/roominfo ")) {
            String roomName = line.substring(10).trim();
            send(Frame.ofText(MsgType.ROOM_INFO, nextSeq(), Kvp.encode(Kvp.kv("room", roomName))));
            networkExecutor.execute(() -> {
                Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.ROOM_INFO_RESP, MsgType.ERROR);
                if (resp != null && resp.type == MsgType.ROOM_INFO_RESP) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    appendLine(String.format("[ROOM_INFO] %s - Members: %s (count: %s)", 
                        kv.getOrDefault("room", ""),
                        kv.getOrDefault("members", ""),
                        kv.getOrDefault("count", "0")));
                }
            });
            return;
        }

        if (line.startsWith("/users")) {
            onRefreshUsers();
            return;
        }

        if (line.startsWith("/userinfo ")) {
            String targetNick = line.substring(10).trim();
            send(Frame.ofText(MsgType.USER_INFO, nextSeq(), Kvp.encode(Kvp.kv("nick", targetNick))));
            networkExecutor.execute(() -> {
                Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.USER_INFO_RESP, MsgType.ERROR);
                if (resp != null && resp.type == MsgType.USER_INFO_RESP) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    appendLine(String.format("[USER_INFO] %s - Room: %s, Status: %s", 
                        kv.getOrDefault("nick", ""),
                        kv.getOrDefault("room", ""),
                        kv.getOrDefault("status", "")));
                }
            });
            return;
        }

        if (line.startsWith("/history")) {
            String[] parts = line.split(" ", 3);
            String roomName = parts.length > 1 ? parts[1].trim() : room;
            String countStr = parts.length > 2 ? parts[2].trim() : "20";
            int count = 20;
            try {
                count = Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
            }
            send(Frame.ofText(MsgType.CHAT_HISTORY, nextSeq(), 
                Kvp.encode(Kvp.kv("room", roomName, "count", String.valueOf(count)))));
            networkExecutor.execute(() -> {
                Frame resp = takeType(MsgType.CHAT_HISTORY_RESP, 3, TimeUnit.SECONDS);
                if (resp != null) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    String messagesStr = kv.getOrDefault("messages", "");
                    appendLine(String.format("[HISTORY] Room: %s, Messages: %s", 
                        kv.getOrDefault("room", ""), kv.getOrDefault("count", "0")));
                    if (!messagesStr.isEmpty()) {
                        String[] messages = messagesStr.split("\n");
                        for (String msg : messages) {
                            String[] parts2 = msg.split("\\|", 3);
                            if (parts2.length == 3) {
                                appendLine(String.format("  [%s] %s: %s", 
                                    parts2[1], parts2[0], parts2[2]));
                            }
                        }
                    }
                }
            });
            return;
        }

        if (line.startsWith("/w ")) {
            String rest = line.substring(3).trim();
            int sp = rest.indexOf(' ');
            if (sp <= 0) {
                String msg = isKorean ? "[UI] 사용법: /w nick msg" : "[UI] usage: /w nick msg";
                appendLine(msg);
                return;
            }
            String to = rest.substring(0, sp);
            String msg = rest.substring(sp + 1);
            sendWhisper(to, msg);
            return;
        }

        if (line.startsWith("/ping")) {
            send(Frame.ofText(MsgType.PING, nextSeq(), Kvp.encode(Kvp.kv("t", Instant.now().toString()))));
            return;
        }
       
        send(Frame.ofText(MsgType.CHAT, nextSeq(), Kvp.encode(Kvp.kv("room", room, "msg", line))));
    }

    private void send(Frame f) {
        if (!running) return;
        sendQueue.offer(f);
    }

    private final Object outLock = new Object();

    private void writerLoop() {
        try {
            while (running) {
                Frame f = sendQueue.take();
                synchronized (outLock) {
                    if (out != null) {
                    f.writeTo(out);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            running = false;
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored2) {}
        }
    }

    private void readerLoop() {
        try {
            while (running) {
                Frame f = Frame.readFrom(in);
                inbound.offer(f);
                renderFrame(f);
            }
        } catch (Exception e) {
            if (running) {
                String msg = isKorean ? "[연결 끊김]" : "[DISCONNECTED]";
                appendLine(msg);
            }
        } finally {
            running = false;
            SwingUtilities.invokeLater(() -> setConnectedUI(false, getText("disconnected")));
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void renderFrame(Frame f) {
        Map<String, String> kv = Kvp.decode(f.payloadText());

        switch (f.type) {
            case MsgType.CHAT -> {
                String roomName = kv.getOrDefault("room", "?");
                String from = kv.getOrDefault("from", "?");
                String msg = kv.getOrDefault("msg", "");
                
                if (blockedUsers.contains(from)) {
                    return;
                }
                
                String formattedMsg = String.format("[%s] %s: %s", roomName, from, msg);
                appendLine(formattedMsg);
                
                if (soundEnabled && !from.equals(nick)) {
                    java.awt.Toolkit.getDefaultToolkit().beep();
                }
            }
            case MsgType.WHISPER -> {
                String from = kv.getOrDefault("from", "?");
                String to = kv.getOrDefault("to", "?");
                String msg = kv.getOrDefault("msg", "");
                appendLine(String.format("[WHISPER] %s -> %s: %s", from, to, msg));
            }
            case MsgType.ERROR -> {
                Map<String, String> errorKv = Kvp.decode(f.payloadText());
                String code = errorKv.getOrDefault("code", "UNKNOWN");
                String msg = errorKv.getOrDefault("msg", "");
                appendLine(String.format("[ERROR] %s: %s", code, msg));
            }
            case MsgType.PONG -> {
                String t = kv.getOrDefault("t", "");
                appendLine(String.format("[PONG] t=%s", t));
            }
            case MsgType.JOIN_OK -> {
                Map<String, String> joinKv = Kvp.decode(f.payloadText());
                room = joinKv.getOrDefault("room", room);
                String statusMsg = isKorean ? 
                    "연결됨: " + nick + " / 방=" + room : 
                    "connected as " + nick + " / room=" + room;
                SwingUtilities.invokeLater(() -> {
                    roomField.setText(room);
                    setConnectedUI(true, statusMsg);
                });
                onRefreshRooms();
            }
            case MsgType.LEAVE_OK -> {
                onRefreshRooms();
            }
            case MsgType.STATS_RESP -> {
                Map<String, String> statsKv = Kvp.decode(f.payloadText());
                StringBuilder stats = new StringBuilder();
                stats.append(isKorean ? "서버 통계\n\n" : "Server Statistics\n\n");
                stats.append(statsKv.getOrDefault("data", "No data"));
                JOptionPane.showMessageDialog(frame, stats.toString(),
                    isKorean ? "통계" : "Statistics",
                    JOptionPane.INFORMATION_MESSAGE);
            }
            case MsgType.ROOM_CREATE -> {
                Map<String, String> createKv = Kvp.decode(f.payloadText());
                String createdRoom = createKv.getOrDefault("room", "");
                if (!createdRoom.isEmpty()) {
                    appendLine(isKorean ? "[시스템] 방이 생성되었습니다: " + createdRoom :
                        "[SYSTEM] Room created: " + createdRoom);
                    onRefreshRooms();
                }
            }
            case MsgType.FRIEND_ADD -> {
                Map<String, String> friendKv = Kvp.decode(f.payloadText());
                String friendName = friendKv.getOrDefault("friend", "");
                if (friendName.isEmpty()) {
                    friendName = friendKv.getOrDefault("user", "");
                }
                String status = friendKv.getOrDefault("status", "");
                if ("added".equals(status) && !friendName.isEmpty()) {
                    friends.add(friendName);
                    appendLine(isKorean ? "[시스템] 친구가 추가되었습니다: " + friendName :
                        "[SYSTEM] Friend added: " + friendName);
                } else if (friendKv.containsKey("code")) {
                    String code = friendKv.getOrDefault("code", "");
                    String msg = friendKv.getOrDefault("msg", "");
                    appendLine(isKorean ? "[오류] 친구 추가 실패: " + msg :
                        "[ERROR] Friend add failed: " + msg);
                }
            }
            case MsgType.FRIEND_REMOVE -> {
                Map<String, String> friendKv = Kvp.decode(f.payloadText());
                String friendName = friendKv.getOrDefault("friend", "");
                if (friendName.isEmpty()) {
                    friendName = friendKv.getOrDefault("user", "");
                }
                String status = friendKv.getOrDefault("status", "");
                if ("removed".equals(status) && !friendName.isEmpty()) {
                    friends.remove(friendName);
                    appendLine(isKorean ? "[시스템] 친구가 제거되었습니다: " + friendName :
                        "[SYSTEM] Friend removed: " + friendName);
                }
            }
            case MsgType.MSG_EDIT -> {
                Map<String, String> editKv = Kvp.decode(f.payloadText());
                String roomName = editKv.getOrDefault("room", "?");
                String from = editKv.getOrDefault("from", "?");
                String original = editKv.getOrDefault("original", "");
                String newMsg = editKv.getOrDefault("new", "");
                if (!original.isEmpty() && !newMsg.isEmpty()) {
                    appendLine(isKorean ? 
                        String.format("[수정됨] %s: %s -> %s", from, original, newMsg) :
                        String.format("[EDITED] %s: %s -> %s", from, original, newMsg));
                }
            }
            case MsgType.MSG_DELETE -> {
                Map<String, String> deleteKv = Kvp.decode(f.payloadText());
                String roomName = deleteKv.getOrDefault("room", "?");
                String from = deleteKv.getOrDefault("from", "?");
                String message = deleteKv.getOrDefault("message", "");
                if (!message.isEmpty()) {
                    appendLine(isKorean ? 
                        String.format("[삭제됨] %s가 메시지를 삭제했습니다: %s", from, message) :
                        String.format("[DELETED] %s deleted message: %s", from, message));
                }
            }
            default -> {
            }
        }
    }

    private Frame takeType(byte type, long timeout, TimeUnit unit) {
        try {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (System.nanoTime() < deadline) {
                Frame f = inbound.poll(50, TimeUnit.MILLISECONDS);
                if (f == null) continue;
                if (f.type == type) return f;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private Frame takeAnyOf(long timeout, TimeUnit unit, byte... types) {
        try {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            Frame f = inbound.poll(50, TimeUnit.MILLISECONDS);
            if (f == null) continue;
            for (byte t : types) {
                if (f.type == t) return f;
            }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private void hardDisconnect(String reason) {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        String msg = isKorean ? "[종료] " : "[QUIT] ";
        appendLine(msg + reason);
    }
    
    private void showSearchDialog() {
        String query = JOptionPane.showInputDialog(frame, 
            isKorean ? "검색할 키워드를 입력하세요:" : "Enter search keyword:",
            isKorean ? "메시지 검색" : "Search Messages", 
            JOptionPane.QUESTION_MESSAGE);
        if (query != null && !query.trim().isEmpty()) {
            searchMessages(query.trim());
        }
    }
    
    private void searchMessages(String keyword) {
        java.util.List<String> results = new java.util.ArrayList<>();
        for (String line : chatHistory) {
            if (line.toLowerCase().contains(keyword.toLowerCase())) {
                results.add(line);
            }
        }
        
        if (results.isEmpty()) {
            JOptionPane.showMessageDialog(frame, 
                isKorean ? "검색 결과가 없습니다." : "No results found.",
                isKorean ? "검색 결과" : "Search Results",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        StringBuilder resultText = new StringBuilder();
        resultText.append(isKorean ? "검색 결과 (" : "Search Results (");
        resultText.append(results.size());
        resultText.append(isKorean ? "개):\n\n" : "):\n\n");
        for (String result : results) {
            resultText.append(result).append("\n");
        }
        
        JTextArea textArea = new JTextArea(resultText.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        JOptionPane.showMessageDialog(frame, scrollPane, 
            isKorean ? "검색 결과" : "Search Results",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showBookmarks() {
        if (bookmarkedMessages.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                isKorean ? "저장된 북마크가 없습니다." : "No bookmarks saved.",
                isKorean ? "북마크" : "Bookmarks",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        StringBuilder bookmarks = new StringBuilder();
        for (String msg : bookmarkedMessages) {
            bookmarks.append(msg).append("\n");
        }
        
        JTextArea textArea = new JTextArea(bookmarks.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        JOptionPane.showMessageDialog(frame, scrollPane,
            isKorean ? "북마크" : "Bookmarks",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void exportChatHistory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(isKorean ? "채팅 기록 저장" : "Save Chat History");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            isKorean ? "텍스트 파일 (*.txt)" : "Text Files (*.txt)", "txt"));
        
        if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().endsWith(".txt")) {
                    file = new File(file.getAbsolutePath() + ".txt");
                }
                
                try (PrintWriter writer = new PrintWriter(new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {
                    for (String line : chatHistory) {
                        writer.println(line);
                    }
                }
                
                JOptionPane.showMessageDialog(frame,
                    isKorean ? "기록이 저장되었습니다." : "History saved successfully.",
                    isKorean ? "저장 완료" : "Save Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame,
                    isKorean ? "저장 중 오류가 발생했습니다." : "Error saving file.",
                    isKorean ? "오류" : "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void importChatHistory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(isKorean ? "채팅 기록 불러오기" : "Load Chat History");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            isKorean ? "텍스트 파일 (*.txt)" : "Text Files (*.txt)", "txt"));
        
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                java.util.List<String> imported = new java.util.ArrayList<>();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        imported.add(line);
                        appendLine(line);
                    }
                }
                
                JOptionPane.showMessageDialog(frame,
                    isKorean ? "기록을 불러왔습니다 (" + imported.size() + "줄)." : 
                    "History loaded (" + imported.size() + " lines).",
                    isKorean ? "불러오기 완료" : "Load Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame,
                    isKorean ? "파일을 읽는 중 오류가 발생했습니다." : "Error reading file.",
                    isKorean ? "오류" : "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void blockUser() {
        String username = JOptionPane.showInputDialog(frame,
            isKorean ? "차단할 사용자 이름:" : "Username to block:",
            isKorean ? "사용자 차단" : "Block User",
            JOptionPane.QUESTION_MESSAGE);
        if (username != null && !username.trim().isEmpty()) {
            blockedUsers.add(username.trim());
            appendLine(isKorean ? "[시스템] " + username.trim() + " 사용자를 차단했습니다." : 
                "[SYSTEM] Blocked user: " + username.trim());
        }
    }
    
    private void unblockUser() {
        if (blockedUsers.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                isKorean ? "차단된 사용자가 없습니다." : "No blocked users.",
                isKorean ? "차단 해제" : "Unblock User",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String[] blocked = blockedUsers.toArray(new String[0]);
        String username = (String) JOptionPane.showInputDialog(frame,
            isKorean ? "차단 해제할 사용자:" : "User to unblock:",
            isKorean ? "차단 해제" : "Unblock User",
            JOptionPane.QUESTION_MESSAGE, null, blocked, blocked[0]);
        if (username != null) {
            blockedUsers.remove(username);
            appendLine(isKorean ? "[시스템] " + username + " 사용자의 차단을 해제했습니다." :
                "[SYSTEM] Unblocked user: " + username);
        }
    }
    
    private void addFriend() {
        if (!running) {
            JOptionPane.showMessageDialog(frame,
                isKorean ? "서버에 연결되어 있지 않습니다." : "Not connected to server.",
                isKorean ? "오류" : "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String username = JOptionPane.showInputDialog(frame,
            isKorean ? "추가할 친구의 닉네임을 입력하세요:" : "Enter friend's nickname:",
            isKorean ? "친구 추가" : "Add Friend",
            JOptionPane.QUESTION_MESSAGE);
        if (username != null && !username.trim().isEmpty()) {
            username = username.trim();
            if (friends.contains(username)) {
                JOptionPane.showMessageDialog(frame,
                    isKorean ? "이미 친구 목록에 있습니다." : "Already in friends list.",
                    isKorean ? "알림" : "Info",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            send(Frame.ofText(MsgType.FRIEND_ADD, nextSeq(), 
                Kvp.encode(Kvp.kv("friend", username))));
            appendLine(isKorean ? "[시스템] 친구 추가 요청: " + username : 
                "[SYSTEM] Friend add request: " + username);
        }
    }
    
    private void removeFriend() {
        if (friends.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                isKorean ? "친구 목록이 비어있습니다." : "Friends list is empty.",
                isKorean ? "알림" : "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String[] friendArray = friends.toArray(new String[0]);
        String username = (String) JOptionPane.showInputDialog(frame,
            isKorean ? "제거할 친구를 선택하세요:" : "Select friend to remove:",
            isKorean ? "친구 제거" : "Remove Friend",
            JOptionPane.QUESTION_MESSAGE, null, friendArray, friendArray[0]);
        if (username != null && running) {
            send(Frame.ofText(MsgType.FRIEND_REMOVE, nextSeq(), 
                Kvp.encode(Kvp.kv("friend", username))));
            friends.remove(username);
            appendLine(isKorean ? "[시스템] 친구를 제거했습니다: " + username : 
                "[SYSTEM] Removed friend: " + username);
        }
    }
    
    private void showFriendsList() {
        if (friends.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                isKorean ? "친구 목록이 비어있습니다." : "Friends list is empty.",
                isKorean ? "친구 목록" : "Friends",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        StringBuilder friendsList = new StringBuilder();
        friendsList.append(isKorean ? "친구 목록:\n\n" : "Friends:\n\n");
        for (String friend : friends) {
            friendsList.append("• ").append(friend).append("\n");
        }
        
        JOptionPane.showMessageDialog(frame, friendsList.toString(),
            isKorean ? "친구 목록" : "Friends",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void setUserProfile() {
        String statusMsg = JOptionPane.showInputDialog(frame,
            isKorean ? "상태 메시지를 입력하세요:" : "Enter status message:",
            isKorean ? "프로필 설정" : "Set Profile",
            JOptionPane.QUESTION_MESSAGE);
        if (statusMsg != null) {
            appendLine(isKorean ? "[시스템] 상태 메시지가 설정되었습니다: " + statusMsg :
                "[SYSTEM] Status message set: " + statusMsg);
        }
    }
    
    private void createRoom() {
        String roomName = JOptionPane.showInputDialog(frame,
            isKorean ? "방 이름:" : "Room name:",
            isKorean ? "방 생성" : "Create Room",
            JOptionPane.QUESTION_MESSAGE);
        if (roomName != null && !roomName.trim().isEmpty()) {
            String password = JOptionPane.showInputDialog(frame,
                isKorean ? "비밀번호 (선택사항):" : "Password (optional):",
                isKorean ? "방 비밀번호" : "Room Password",
                JOptionPane.QUESTION_MESSAGE);
            
            if (running) {
                Map<String, String> kv = new java.util.HashMap<>();
                kv.put("room", roomName.trim());
                if (password != null && !password.trim().isEmpty()) {
                    kv.put("password", password.trim());
                }
                send(Frame.ofText(MsgType.ROOM_CREATE, nextSeq(), Kvp.encode(kv)));
                appendLine(isKorean ? "[시스템] 방 생성 요청: " + roomName.trim() :
                    "[SYSTEM] Room creation requested: " + roomName.trim());
            }
        }
    }
    
    private void deleteRoom() {
        String roomName = JOptionPane.showInputDialog(frame,
            isKorean ? "삭제할 방 이름:" : "Room name to delete:",
            isKorean ? "방 삭제" : "Delete Room",
            JOptionPane.QUESTION_MESSAGE);
        if (roomName != null && !roomName.trim().isEmpty() && running) {
            send(Frame.ofText(MsgType.ROOM_DELETE, nextSeq(), 
                Kvp.encode(Kvp.kv("room", roomName.trim()))));
            appendLine(isKorean ? "[시스템] 방 삭제 요청: " + roomName.trim() :
                "[SYSTEM] Room deletion requested: " + roomName.trim());
        }
    }
    
    private void showRoomSettings() {
        String roomName = JOptionPane.showInputDialog(frame,
            isKorean ? "설정할 방 이름:" : "Room name:",
            isKorean ? "방 설정" : "Room Settings",
            JOptionPane.QUESTION_MESSAGE);
        if (roomName == null || roomName.trim().isEmpty()) return;
        
        String[] options = isKorean ? 
            new String[]{"비밀번호 설정", "설명 설정", "관리자 지정", "참가자 제한"} :
            new String[]{"Set Password", "Set Description", "Set Admin", "Set Limit"};
        
        String choice = (String) JOptionPane.showInputDialog(frame,
            isKorean ? "설정을 선택하세요:" : "Select setting:",
            isKorean ? "방 설정" : "Room Settings",
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        
        if (choice != null && running) {
            if (choice.equals(options[0]) || choice.equals("Set Password")) {
                String password = JOptionPane.showInputDialog(frame,
                    isKorean ? "비밀번호:" : "Password:",
                    isKorean ? "비밀번호 설정" : "Set Password",
                    JOptionPane.QUESTION_MESSAGE);
                if (password != null) {
                    send(Frame.ofText(MsgType.ROOM_SET_PASSWORD, nextSeq(),
                        Kvp.encode(Kvp.kv("room", roomName, "password", password))));
                }
            } else if (choice.equals(options[1]) || choice.equals("Set Description")) {
                String desc = JOptionPane.showInputDialog(frame,
                    isKorean ? "설명:" : "Description:",
                    isKorean ? "설명 설정" : "Set Description",
                    JOptionPane.QUESTION_MESSAGE);
                if (desc != null) {
                    send(Frame.ofText(MsgType.ROOM_SET_DESCRIPTION, nextSeq(),
                        Kvp.encode(Kvp.kv("room", roomName, "description", desc))));
                }
            } else if (choice.equals(options[2]) || choice.equals("Set Admin")) {
                String admin = JOptionPane.showInputDialog(frame,
                    isKorean ? "관리자 닉네임:" : "Admin nickname:",
                    isKorean ? "관리자 지정" : "Set Admin",
                    JOptionPane.QUESTION_MESSAGE);
                if (admin != null) {
                    send(Frame.ofText(MsgType.ROOM_SET_ADMIN, nextSeq(),
                        Kvp.encode(Kvp.kv("room", roomName, "admin", admin))));
                }
            } else if (choice.equals(options[3]) || choice.equals("Set Limit")) {
                String limit = JOptionPane.showInputDialog(frame,
                    isKorean ? "최대 참가자 수:" : "Max participants:",
                    isKorean ? "참가자 제한" : "Set Limit",
                    JOptionPane.QUESTION_MESSAGE);
                if (limit != null) {
                    try {
                        int limitNum = Integer.parseInt(limit);
                        send(Frame.ofText(MsgType.ROOM_SET_LIMIT, nextSeq(),
                            Kvp.encode(Kvp.kv("room", roomName, "limit", limit))));
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(frame,
                            isKorean ? "올바른 숫자를 입력하세요." : "Please enter a valid number.",
                            isKorean ? "오류" : "Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }
    
    private void showMessageStats() {
        int totalMessages = chatHistory.size();
        int chatMessages = 0;
        int whisperMessages = 0;
        int systemMessages = 0;
        
        for (String line : chatHistory) {
            if (line.contains("[WHISPER]")) whisperMessages++;
            else if (line.contains("[SYSTEM]") || line.contains("[시스템]")) systemMessages++;
            else if (line.contains(":")) chatMessages++;
        }
        
        StringBuilder stats = new StringBuilder();
        stats.append(isKorean ? "메시지 통계\n\n" : "Message Statistics\n\n");
        stats.append(isKorean ? "전체 메시지: " : "Total Messages: ").append(totalMessages).append("\n");
        stats.append(isKorean ? "채팅 메시지: " : "Chat Messages: ").append(chatMessages).append("\n");
        stats.append(isKorean ? "귓속말: " : "Whispers: ").append(whisperMessages).append("\n");
        stats.append(isKorean ? "시스템 메시지: " : "System Messages: ").append(systemMessages).append("\n");
        stats.append(isKorean ? "북마크: " : "Bookmarks: ").append(bookmarkedMessages.size()).append("\n");
        
        JOptionPane.showMessageDialog(frame, stats.toString(),
            isKorean ? "메시지 통계" : "Message Statistics",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showActivityStats() {
        if (running) {
            send(Frame.ofText(MsgType.STATS_REQUEST, nextSeq(), Kvp.encode(Kvp.kv())));
            appendLine(isKorean ? "[시스템] 통계 요청 중..." : "[SYSTEM] Requesting statistics...");
        } else {
            JOptionPane.showMessageDialog(frame,
                isKorean ? "서버에 연결되어 있지 않습니다." : "Not connected to server.",
                isKorean ? "오류" : "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void editMessage(String originalText) {
        if (!running) return;
        
        String[] parts = originalText.split(":", 2);
        if (parts.length < 2) {
            JOptionPane.showMessageDialog(frame,
                isKorean ? "메시지를 인식할 수 없습니다." : "Cannot identify message.",
                isKorean ? "오류" : "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String currentMsg = parts[1].trim();
        JTextField textField = new JTextField(currentMsg);
        Object[] message = {
            isKorean ? "수정할 메시지를 입력하세요:" : "Enter new message:",
            textField
        };
        int option = JOptionPane.showConfirmDialog(frame,
            message,
            isKorean ? "메시지 수정" : "Edit Message",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        String newMsg = (option == JOptionPane.OK_OPTION) ? textField.getText() : null;
        
        if (newMsg != null && !newMsg.trim().isEmpty() && !newMsg.equals(currentMsg)) {
            send(Frame.ofText(MsgType.MSG_EDIT, nextSeq(), 
                Kvp.encode(Kvp.kv("original", originalText, "new", newMsg.trim()))));
            appendLine(isKorean ? "[시스템] 메시지 수정 요청 전송" : "[SYSTEM] Message edit request sent");
        }
    }
    
    private void deleteMessage(String messageText) {
        if (!running) return;
        
        int result = JOptionPane.showConfirmDialog(frame,
            isKorean ? "이 메시지를 삭제하시겠습니까?" : "Delete this message?",
            isKorean ? "메시지 삭제" : "Delete Message",
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            send(Frame.ofText(MsgType.MSG_DELETE, nextSeq(), 
                Kvp.encode(Kvp.kv("message", messageText))));
            appendLine(isKorean ? "[시스템] 메시지 삭제 요청 전송" : "[SYSTEM] Message delete request sent");
        }
    }
    
    private void showEmojiPicker() {
        String[] emojis = {"😀", "😂", "😍", "😎", "😊", "👍", "❤️", "🎉", "🔥", "⭐",
                          "😢", "😡", "🤔", "👏", "🙌", "💯", "🎯", "🚀", "💪", "✨"};
        
        JPanel emojiPanel = new JPanel(new GridLayout(4, 5, 5, 5));
        for (String emoji : emojis) {
            JButton emojiButton = new JButton(emoji);
            emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
            emojiButton.setPreferredSize(new Dimension(40, 40));
            emojiButton.addActionListener(e -> {
                String current = inputField.getText();
                inputField.setText(current + emoji);
                inputField.requestFocus();
                JDialog dialog = (JDialog) SwingUtilities.getWindowAncestor(emojiButton);
                if (dialog != null) dialog.dispose();
            });
            emojiPanel.add(emojiButton);
        }
        
        JDialog emojiDialog = new JDialog(frame, isKorean ? "이모지 선택" : "Select Emoji", true);
        emojiDialog.add(emojiPanel);
        emojiDialog.pack();
        emojiDialog.setLocationRelativeTo(frame);
        emojiDialog.setVisible(true);
    }
    
    private void completeCommand() {
        String text = inputField.getText();
        if (!text.startsWith("/")) return;
        
        String[] commands = {"/join", "/leave", "/rooms", "/roominfo", "/users", "/userinfo",
                            "/history", "/w", "/ping", "/quit", "/search", "/bookmark"};
        
        String partial = text.substring(1);
        java.util.List<String> matches = new java.util.ArrayList<>();
        for (String cmd : commands) {
            if (cmd.substring(1).startsWith(partial.toLowerCase())) {
                matches.add(cmd);
            }
        }
        
        if (matches.size() == 1) {
            inputField.setText(matches.get(0) + " ");
            inputField.setCaretPosition(inputField.getText().length());
        } else if (matches.size() > 1) {
            String common = findCommonPrefix(matches);
            if (common.length() > partial.length()) {
                inputField.setText("/" + common);
                inputField.setCaretPosition(inputField.getText().length());
            }
        }
    }
    
    private String findCommonPrefix(java.util.List<String> strings) {
        if (strings.isEmpty()) return "";
        String first = strings.get(0).substring(1);
        for (int i = 0; i < first.length(); i++) {
            char c = first.charAt(i);
            for (int j = 1; j < strings.size(); j++) {
                String s = strings.get(j).substring(1);
                if (i >= s.length() || s.charAt(i) != c) {
                    return first.substring(0, i);
                }
            }
        }
        return first;
    }
}
