package com.mycompany.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

public class ChatClientGUI {

    private final JFrame frame = new JFrame("Chat Client");
    private final JTextPane chatPane = new JTextPane();
    private final JTextField textField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JButton emojiButton = new JButton("😊");
    private final JButton attachButton = new JButton("📎");
    private final JLabel statusLabel = new JLabel("🔴 Đang chờ kết nối...");

    private BufferedReader in;
    private PrintWriter out;
    private DataOutputStream dataOut; // Luồng riêng để gửi File
    private String clientName;

    // Bộ nhớ tạm để lưu các file nhận được (ID File -> Dữ liệu Byte)
    private final HashMap<String, byte[]> fileStorage = new HashMap<>();

    public ChatClientGUI() {
        // Tối ưu hóa multicatch theo chuẩn của VS Code
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        // --- 1. HEADER ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(86, 130, 163));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel("💬 Global Chat Room");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(215, 232, 244));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusLabel, BorderLayout.EAST);

        // --- 2. CHAT AREA ---
        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(230, 235, 239));
        chatPane.setText("<html><body id='body' style='font-family: \"Segoe UI\", Arial, sans-serif; padding: 10px; margin: 0;'></body></html>");

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(null);

        // Xử lý sự kiện khi click vào link tải File
        chatPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String fileId = e.getDescription(); 
                if (fileStorage.containsKey(fileId)) {
                    downloadFile(fileId, fileStorage.get(fileId));
                }
            }
        });

        // --- 3. INPUT AREA ---
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        textField.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        // Khung chứa các nút Emoji và Attach
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actionPanel.setBackground(Color.WHITE);

        emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        emojiButton.setContentAreaFilled(false);
        emojiButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        emojiButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        attachButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        attachButton.setContentAreaFilled(false);
        attachButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10));
        attachButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        actionPanel.add(emojiButton);
        actionPanel.add(attachButton);

        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setBackground(new Color(86, 130, 163));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel inputWrapper = new JPanel(new BorderLayout(5, 0));
        inputWrapper.setBackground(Color.WHITE);
        inputWrapper.add(actionPanel, BorderLayout.WEST);
        inputWrapper.add(textField, BorderLayout.CENTER);

        bottomPanel.add(inputWrapper, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        // --- RÁP FRAME ---
        frame.getContentPane().add(headerPanel, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        // --- SỰ KIỆN GỬI TEXT ---
        ActionListener sendListener = e -> {
            String msg = textField.getText();
            if (!msg.trim().isEmpty() && out != null) {
                out.println("TEXT:" + clientName + ": " + msg); // Thêm prefix TEXT: để Server phân biệt
                textField.setText("");
            }
        };
        textField.addActionListener(sendListener);
        sendButton.addActionListener(sendListener);

        setupEmojiPicker();
        attachButton.addActionListener(e -> sendFile());
    }

    private void setupEmojiPicker() {
        JPopupMenu emojiMenu = new JPopupMenu();
        String[] emojis = {"😀", "😂", "🥰", "😎", "😭", "😡", "👍", "❤️", "🎉", "🔥", "🤔", "😅"};
        JPanel emojiPanel = new JPanel(new GridLayout(3, 4));
        for (String em : emojis) {
            JButton btn = new JButton(em);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.addActionListener(e -> {
                textField.setText(textField.getText() + em);
                emojiMenu.setVisible(false);
                textField.requestFocus();
            });
            emojiPanel.add(btn);
        }
        emojiMenu.add(emojiPanel);
        emojiButton.addActionListener(e -> emojiMenu.show(emojiButton, 0, -emojiMenu.getPreferredSize().height));
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                if (fileBytes.length > 5 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(frame, "File quá lớn! Vui lòng chọn file dưới 5MB.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Gửi thông báo FILE lên Server bằng DataOutputStream
                out.println("FILE:" + clientName + ": " + file.getName());
                dataOut.writeInt(fileBytes.length);
                dataOut.write(fileBytes);
                dataOut.flush();

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Không thể đọc file!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void downloadFile(String fileName, byte[] fileData) {
        JFileChooser fileChooser = new JFileChooser();
        String originalName = fileName.contains("_") ? fileName.substring(fileName.indexOf("_") + 1) : fileName;
        fileChooser.setSelectedFile(new File(originalName));

        int result = fileChooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File saveFile = fileChooser.getSelectedFile();
            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                fos.write(fileData);
                JOptionPane.showMessageDialog(frame, "Đã lưu file thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Lỗi khi lưu file!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void appendMessage(String sender, String message, boolean isFile, String fileId) {
        SwingUtilities.invokeLater(() -> {
            try {
                HTMLDocument doc = (HTMLDocument) chatPane.getDocument();
                HTMLEditorKit kit = (HTMLEditorKit) chatPane.getEditorKit();
                
                StringBuilder htmlBuilder = new StringBuilder();

                // FIX LỖI EFFECTIVELY FINAL TẠI ĐÂY: Dùng biến phụ displayMessage
                String displayMessage = message;

                if (isFile) {
                    displayMessage = "📁 <b>" + displayMessage + "</b><br>"
                            + "<a href='" + fileId + "' style='color: #0056b3; text-decoration: none;'>⬇ Nhấn vào đây để tải về</a>";
                }

                if (sender.equalsIgnoreCase("System")) {
                    htmlBuilder.append("<table width='100%'><tr><td align='center'>")
                               .append("<font face='Segoe UI, Arial' size='3' color='#888888'><i>")
                               .append(displayMessage)
                               .append("</i></font></td></tr></table><br>");
                } else if (sender.equals(clientName)) {
                    htmlBuilder.append("<table width='100%'><tr><td align='right'>")
                               .append("<table bgcolor='#DCF8C6' cellpadding='8' cellspacing='0'><tr><td>")
                               .append("<font face='Segoe UI Emoji, Segoe UI, Arial' size='4' color='black'>")
                               .append(displayMessage)
                               .append("</font></td></tr></table></td></tr></table><br>");
                } else {
                    htmlBuilder.append("<table width='100%'><tr><td align='left'>")
                               .append("<table bgcolor='#FFFFFF' cellpadding='8' cellspacing='0'><tr><td>")
                               .append("<font face='Segoe UI, Arial' size='3' color='#5682A3'><b>")
                               .append(sender)
                               .append("</b></font><br>")
                               .append("<font face='Segoe UI Emoji, Segoe UI, Arial' size='4' color='black'>")
                               .append(displayMessage)
                               .append("</font></td></tr></table></td></tr></table><br>");
                }
                
                kit.insertHTML(doc, doc.getLength(), htmlBuilder.toString(), 0, 0, null);
                chatPane.setCaretPosition(doc.getLength());

            // FIX CẢNH BÁO HINTS BẰNG CÁCH DÙNG MULTICATCH
            } catch (javax.swing.text.BadLocationException | java.io.IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void connectToServer() {
        clientName = JOptionPane.showInputDialog(
                frame, "Nhập tên hiển thị của bạn:", "Đăng nhập Chat", JOptionPane.PLAIN_MESSAGE
        );
        if (clientName == null || clientName.trim().isEmpty()) {
            clientName = "Guest_" + (int) (Math.random() * 1000);
        }
        frame.setTitle("Telegram Lite - " + clientName);

        new Thread(() -> {
            try {
                Socket socket = new Socket("127.0.0.1", 6666);
                
                // Ép chuẩn UTF-8 để hỗ trợ Emoji
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                
                // Thêm luồng đọc Byte để xử lý File
                DataInputStream dataIn = new DataInputStream(socket.getInputStream());
                dataOut = new DataOutputStream(socket.getOutputStream());

                SwingUtilities.invokeLater(() -> statusLabel.setText("🟢 Online: " + clientName));
                out.println("TEXT:System: " + clientName + " đã tham gia phòng chat!");

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("TEXT:")) {
                        // Xử lý Text bình thường
                        String actualMessage = line.substring(5); 
                        String[] parts = actualMessage.split(": ", 2);
                        if (parts.length == 2) {
                            appendMessage(parts[0], parts[1], false, null);
                        } else {
                            appendMessage("System", actualMessage, false, null);
                        }
                    } else if (line.startsWith("FILE:")) {
                        // Xử lý luồng File
                        String actualMessage = line.substring(5);
                        String[] parts = actualMessage.split(": ", 2);
                        if (parts.length == 2) {
                            String sender = parts[0];
                            String fileName = parts[1];
                            
                            // Đọc kích thước file
                            int fileLength = dataIn.readInt();
                            if (fileLength > 0) {
                                byte[] fileData = new byte[fileLength];
                                dataIn.readFully(fileData, 0, fileData.length); // Đọc trọn vẹn byte của file
                                
                                String fileId = System.currentTimeMillis() + "_" + fileName;
                                fileStorage.put(fileId, fileData);
                                
                                appendMessage(sender, fileName, true, fileId);
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("🔴 Mất kết nối"));
                appendMessage("System", "Mất kết nối tới máy chủ.", false, null);
            }
        }).start();
    }

    public static void main(String[] args) {
        ChatClientGUI client = new ChatClientGUI();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setSize(480, 680);
        client.frame.setLocationRelativeTo(null);
        client.frame.setVisible(true);

        client.connectToServer();
    }
}