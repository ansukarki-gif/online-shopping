package Shop;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;

/**
 * ============================================================
 *  ShopEase — Online Shopping System
 *  Single-file Java Swing + MySQL Desktop Application
 *
 *  HOW TO RUN IN ECLIPSE:
 *  1. Create a new Java Project in Eclipse
 *  2. Add mysql-connector-j-8.x.x.jar to Build Path
 *     (Right-click project > Build Path > Add External JARs)
 *  3. Run your MySQL server and execute the SQL in setupDatabase()
 *     OR let the app auto-create everything on first run
 *  4. Update DB_USER and DB_PASS below to match your MySQL
 *  5. Right-click ShopEase.java > Run As > Java Application
 * ============================================================
 */
public class ShopEase {

    // ── Database config — change DB_PASS to your MySQL password ──
    static final String DB_URL  = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    static final String DB_USER = "root";
    static final String DB_PASS = "DELL#1#2#3";   // ← CHANGE THIS

    // ── Theme colours ─────────────────────────────────────────────
    static final Color BG_DARK    = new Color(15, 25, 35);
    static final Color BG_CARD    = new Color(26, 37, 53);
    static final Color BG_FIELD   = new Color(36, 48, 68);
    static final Color ACCENT     = new Color(201, 168, 76);
    static final Color TEXT_LIGHT = new Color(232, 232, 232);
    static final Color TEXT_MUTED = new Color(138, 151, 168);
    static final Color SUCCESS    = new Color(46, 204, 113);
    static final Color DANGER     = new Color(231, 76, 60);
    static final Color BORDER     = new Color(42, 58, 80);

    // ── Session ───────────────────────────────────────────────────
    static int    currentUserId   = -1;
    static String currentUsername = "";
    static String currentFullName = "";
    static String currentRole     = "";
    static String currentAddress  = "";

    static JFrame mainFrame;

    // =============================================================
    //  MAIN
    // =============================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
            catch (Exception ignored) {}

            // Auto-setup database and tables
            try {
                setupDatabase();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                    "Cannot connect to MySQL!\n\n" +
                    "Please check:\n" +
                    "  1. MySQL server is running\n" +
                    "  2. DB_PASS is correct in ShopEase.java\n" +
                    "  3. mysql-connector-j.jar is in Build Path\n\n" +
                    "Error: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            mainFrame = new JFrame("ShopEase — Online Shopping System");
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setSize(1000, 680);
            mainFrame.setLocationRelativeTo(null);
            mainFrame.getContentPane().setBackground(BG_DARK);

            showLoginScreen();
            mainFrame.setVisible(true);
        });
    }

    // =============================================================
    //  DATABASE SETUP
    // =============================================================
    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    static void setupDatabase() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS shopease");
            st.execute("USE shopease");

            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(50) NOT NULL UNIQUE," +
                "password VARCHAR(100) NOT NULL," +
                "full_name VARCHAR(100) NOT NULL," +
                "email VARCHAR(100) NOT NULL UNIQUE," +
                "address TEXT," +
                "role ENUM('customer','admin') DEFAULT 'customer'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            st.execute("CREATE TABLE IF NOT EXISTS categories (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(50) NOT NULL UNIQUE)");

            st.execute("CREATE TABLE IF NOT EXISTS products (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(150) NOT NULL," +
                "description TEXT," +
                "price DECIMAL(10,2) NOT NULL," +
                "stock INT DEFAULT 0," +
                "category_id INT," +
                "FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS cart (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "product_id INT NOT NULL," +
                "quantity INT DEFAULT 1," +
                "UNIQUE KEY uq_cart(user_id, product_id)," +
                "FOREIGN KEY (user_id)   REFERENCES users(id)    ON DELETE CASCADE," +
                "FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE)");

            st.execute("CREATE TABLE IF NOT EXISTS orders (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "user_id INT NOT NULL," +
                "total_amount DECIMAL(10,2) NOT NULL," +
                "shipping_address TEXT NOT NULL," +
                "status VARCHAR(30) DEFAULT 'Pending'," +
                "order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");

            st.execute("CREATE TABLE IF NOT EXISTS order_items (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "order_id INT NOT NULL," +
                "product_id INT NOT NULL," +
                "quantity INT NOT NULL," +
                "unit_price DECIMAL(10,2) NOT NULL," +
                "FOREIGN KEY (order_id)   REFERENCES orders(id)   ON DELETE CASCADE," +
                "FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE)");

            // Seed only if empty
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM shopease.users");
            rs.next();
            if (rs.getInt(1) == 0) seedData(conn);
        }
    }

    static void seedData(Connection conn) throws SQLException {
        conn.createStatement().execute("USE shopease");

        // Categories
        String[] cats = {"Electronics","Clothing","Books","Home & Living","Beauty"};
        for (String c : cats)
            conn.createStatement().execute("INSERT IGNORE INTO categories(name) VALUES('" + c + "')");

        // Users
        String[][] users = {
            {"admin","admin123","Admin User","admin@shopease.com","Kathmandu, Nepal","admin"},
            {"anshu","pass123","Anshu Karki","anshu@patan.edu.np","Patan, Lalitpur","customer"},
            {"ramesh","ram123","Ramesh Shah","ramesh@example.com","Bhaktapur, Nepal","customer"}
        };
        for (String[] u : users) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO users(username,password,full_name,email,address,role) VALUES(?,?,?,?,?,?)")) {
                for (int i = 0; i < u.length; i++) ps.setString(i+1, u[i]);
                ps.executeUpdate();
            }
        }

        // Products: name, desc, price, stock, category
        Object[][] products = {
            {"Samsung Galaxy S24","Flagship phone 50MP camera 6.2\" display",85000,15,"Electronics"},
            {"Apple iPhone 15","A16 Bionic chip Dynamic Island 48MP camera",135000,10,"Electronics"},
            {"Sony WH-1000XM5 Headphones","Noise canceling 30-hour battery",35000,25,"Electronics"},
            {"Bluetooth Speaker","360 sound waterproof IPX7 12hr playback",4500,30,"Electronics"},
            {"Men's Casual T-Shirt","100% cotton sizes S-XXL 5 colors",799,100,"Clothing"},
            {"Women's Denim Jacket","Classic slim-fit premium quality",2499,50,"Clothing"},
            {"Nike Running Shoes","Lightweight mesh responsive cushioning",7500,40,"Clothing"},
            {"The Alchemist","Paulo Coelho bestseller about dreams",450,200,"Books"},
            {"Java Programming Guide","Comprehensive Java beginner to expert",1200,75,"Books"},
            {"Stainless Steel Bottle","1L insulated cold 24hrs hot 12hrs",899,150,"Home & Living"},
            {"40L Travel Backpack","Durable nylon laptop compartment",3200,60,"Home & Living"},
            {"Organic Face Moisturizer","Natural ingredients SPF 30 all skin types",1299,80,"Beauty"}
        };
        for (Object[] p : products) {
            ResultSet catRs = conn.createStatement().executeQuery(
                "SELECT id FROM shopease.categories WHERE name='" + p[4] + "'");
            catRs.next();
            int catId = catRs.getInt(1);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO shopease.products(name,description,price,stock,category_id) VALUES(?,?,?,?,?)")) {
                ps.setString(1, (String)p[0]);
                ps.setString(2, (String)p[1]);
                ps.setInt(3, (int)p[2]);
                ps.setInt(4, (int)p[3]);
                ps.setInt(5, catId);
                ps.executeUpdate();
            }
        }
    }

    // =============================================================
    //  NAVIGATION HELPER
    // =============================================================
    static void showPanel(JPanel panel) {
        mainFrame.getContentPane().removeAll();
        mainFrame.getContentPane().add(panel);
        mainFrame.revalidate();
        mainFrame.repaint();
    }

    // =============================================================
    //  REUSABLE UI HELPERS
    // =============================================================
    static JPanel darkPanel() {
        JPanel p = new JPanel(); p.setBackground(BG_DARK); return p;
    }
    static JPanel cardPanel() {
        JPanel p = new JPanel(); p.setBackground(BG_CARD);
        p.setBorder(new LineBorder(BORDER, 1, true)); return p;
    }
    static JLabel titleLabel(String text) {
        JLabel l = new JLabel(text); l.setFont(new Font("SansSerif", Font.BOLD, 22));
        l.setForeground(TEXT_LIGHT); return l;
    }
    static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text); l.setFont(new Font("SansSerif", Font.BOLD, 14));
        l.setForeground(ACCENT); return l;
    }
    static JLabel bodyLabel(String text) {
        JLabel l = new JLabel(text); l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        l.setForeground(TEXT_LIGHT); return l;
    }
    static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text); l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(TEXT_MUTED); return l;
    }
    static JTextField styledField() {
        JTextField f = new JTextField();
        f.setBackground(BG_FIELD); f.setForeground(TEXT_LIGHT);
        f.setCaretColor(TEXT_LIGHT); f.setFont(new Font("SansSerif", Font.PLAIN, 13));
        f.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                    new EmptyBorder(6, 10, 6, 10)));
        return f;
    }
    static JPasswordField styledPassField() {
        JPasswordField f = new JPasswordField();
        f.setBackground(BG_FIELD); f.setForeground(TEXT_LIGHT);
        f.setCaretColor(TEXT_LIGHT); f.setFont(new Font("SansSerif", Font.PLAIN, 13));
        f.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                    new EmptyBorder(6, 10, 6, 10)));
        return f;
    }
    static JButton primaryBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT); b.setForeground(new Color(30, 30, 30));
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(9, 20, 9, 20));
        return b;
    }
    static JButton secondaryBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(BG_FIELD); b.setForeground(ACCENT);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false); b.setBorder(new LineBorder(ACCENT, 1, true));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new CompoundBorder(new LineBorder(ACCENT, 1, true), new EmptyBorder(7, 16, 7, 16)));
        return b;
    }
    static JButton dangerBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(DANGER); b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(6, 14, 6, 14));
        return b;
    }
    static JSeparator darkSep() {
        JSeparator s = new JSeparator(); s.setForeground(BORDER); return s;
    }

    // ── Styled dark table ─────────────────────────────────────────
    static void styleTable(JTable table) {
        table.setBackground(BG_CARD);
        table.setForeground(TEXT_LIGHT);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setRowHeight(32);
        table.setGridColor(BORDER);
        table.setSelectionBackground(BG_FIELD);
        table.setSelectionForeground(ACCENT);
        table.setShowGrid(true);
        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_DARK);
        header.setForeground(ACCENT);
        header.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.setBorder(new LineBorder(BORDER));
    }

    // ── NavBar ────────────────────────────────────────────────────
    static JPanel buildNavBar() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(BG_CARD);
        nav.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(10, 20, 10, 20)));
        nav.setPreferredSize(new Dimension(1000, 52));

        JLabel brand = new JLabel("🛍 ShopEase");
        brand.setFont(new Font("SansSerif", Font.BOLD, 20));
        brand.setForeground(ACCENT);
        brand.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        brand.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { showProductsScreen(); }
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        JButton productsBtn = navBtn("Products");
        productsBtn.addActionListener(e -> showProductsScreen());
        right.add(productsBtn);

        if (currentUserId > 0) {
            JButton cartBtn = navBtn("🛒 Cart");
            cartBtn.addActionListener(e -> showCartScreen());
            JButton ordersBtn = navBtn("My Orders");
            ordersBtn.addActionListener(e -> showOrdersScreen());
            JLabel userLbl = mutedLabel("  👤 " + currentFullName.split(" ")[0] + "  ");
            JButton logoutBtn = secondaryBtn("Logout");
            logoutBtn.addActionListener(e -> {
                currentUserId = -1; currentUsername = ""; currentFullName = "";
                currentRole = ""; currentAddress = "";
                showLoginScreen();
            });
            right.add(cartBtn); right.add(ordersBtn); right.add(userLbl);
            if ("admin".equals(currentRole)) {
                JButton adminBtn = navBtn("⚙ Admin");
                adminBtn.addActionListener(ev -> showAdminScreen());
                right.add(adminBtn);
            }
            right.add(logoutBtn);
        } else {
            JButton loginBtn = primaryBtn("Login");
            loginBtn.addActionListener(e -> showLoginScreen());
            right.add(loginBtn);
        }

        nav.add(brand, BorderLayout.WEST);
        nav.add(right,  BorderLayout.EAST);
        return nav;
    }

    static JButton navBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(BG_CARD); b.setForeground(TEXT_LIGHT);
        b.setFont(new Font("SansSerif", Font.PLAIN, 13));
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(6, 10, 6, 10));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(ACCENT); }
            public void mouseExited(MouseEvent e)  { b.setForeground(TEXT_LIGHT); }
        });
        return b;
    }

    // =============================================================
    //  LOGIN SCREEN
    // =============================================================
    static void showLoginScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);

        // Left brand panel
        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBackground(BG_CARD);
        brand.setPreferredSize(new Dimension(300, 600));
        brand.setBorder(new EmptyBorder(80, 40, 80, 40));
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel icon = new JLabel("🛍", SwingConstants.CENTER);
        icon.setFont(new Font("SansSerif", Font.PLAIN, 64));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("ShopEase", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(ACCENT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("<html><center>Your premium<br>shopping destination</center></html>", SwingConstants.CENTER);
        sub.setForeground(TEXT_MUTED);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("<html><hr><br><b style='color:#8a97a8'>Demo credentials:</b><br>" +
            "User: anshu / pass123<br>Admin: admin / admin123</html>");
        hint.setForeground(TEXT_MUTED);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        brand.add(Box.createVerticalGlue());
        brand.add(icon); brand.add(Box.createVerticalStrut(12));
        brand.add(title); brand.add(Box.createVerticalStrut(8));
        brand.add(sub); brand.add(Box.createVerticalStrut(24));
        brand.add(hint);
        brand.add(Box.createVerticalGlue());

        // Right form panel
        JPanel formWrap = new JPanel(new GridBagLayout());
        formWrap.setBackground(BG_DARK);

        JPanel form = cardPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(36, 40, 36, 40));
        form.setPreferredSize(new Dimension(380, 380));

        JLabel formTitle = titleLabel("Welcome Back");
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel formSub = mutedLabel("Sign in to your account");
        formSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField userField = styledField();
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        JPasswordField passField = styledPassField();
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel errorLbl = new JLabel(" ");
        errorLbl.setForeground(DANGER);
        errorLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        errorLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton loginBtn = primaryBtn("Sign In");
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        regRow.setOpaque(false);
        regRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel noAcc = mutedLabel("Don't have an account?  ");
        JButton regBtn = new JButton("Register");
        regBtn.setContentAreaFilled(false); regBtn.setBorderPainted(false);
        regBtn.setForeground(ACCENT); regBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        regBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        regRow.add(noAcc); regRow.add(regBtn);

        form.add(formTitle); form.add(Box.createVerticalStrut(4));
        form.add(formSub); form.add(Box.createVerticalStrut(20));
        form.add(mutedLabelLeft("Username")); form.add(Box.createVerticalStrut(4));
        form.add(userField); form.add(Box.createVerticalStrut(12));
        form.add(mutedLabelLeft("Password")); form.add(Box.createVerticalStrut(4));
        form.add(passField); form.add(Box.createVerticalStrut(8));
        form.add(errorLbl); form.add(Box.createVerticalStrut(4));
        form.add(loginBtn); form.add(Box.createVerticalStrut(16));
        form.add(darkSep()); form.add(Box.createVerticalStrut(12));
        form.add(regRow);

        formWrap.add(form);
        root.add(brand, BorderLayout.WEST);
        root.add(formWrap, BorderLayout.CENTER);

        // Actions
        loginBtn.addActionListener(e -> doLogin(userField.getText().trim(),
                new String(passField.getPassword()).trim(), errorLbl));
        passField.addActionListener(e -> loginBtn.doClick());
        regBtn.addActionListener(e -> showRegisterScreen());

        showPanel(root);
        mainFrame.setTitle("ShopEase — Login");
    }

    static JLabel mutedLabelLeft(String t) {
        JLabel l = mutedLabel(t); l.setAlignmentX(Component.LEFT_ALIGNMENT); return l;
    }

    static void doLogin(String username, String password, JLabel errorLbl) {
        if (username.isEmpty() || password.isEmpty()) {
            errorLbl.setText("Please enter username and password."); return;
        }
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM shopease.users WHERE username=? AND password=?")) {
            ps.setString(1, username); ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                currentUserId   = rs.getInt("id");
                currentUsername = rs.getString("username");
                currentFullName = rs.getString("full_name");
                currentRole     = rs.getString("role");
                currentAddress  = rs.getString("address") != null ? rs.getString("address") : "";
                mainFrame.setTitle("ShopEase — " + currentFullName);
                showProductsScreen();
            } else {
                errorLbl.setText("Invalid username or password.");
            }
        } catch (Exception ex) { errorLbl.setText("DB Error: " + ex.getMessage()); }
    }

    // =============================================================
    //  REGISTER SCREEN
    // =============================================================
    static void showRegisterScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);

        JPanel formWrap = new JPanel(new GridBagLayout());
        formWrap.setBackground(BG_DARK);

        JPanel form = cardPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 40, 30, 40));
        form.setPreferredSize(new Dimension(420, 560));

        JLabel t = titleLabel("Create Account");
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = mutedLabel("Join ShopEase today");
        s.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField nameField  = styledField(); nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JTextField userField  = styledField(); userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JTextField emailField = styledField(); emailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JPasswordField passField    = styledPassField(); passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JPasswordField confirmField = styledPassField(); confirmField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JTextArea addrArea = new JTextArea(3, 20);
        addrArea.setBackground(BG_FIELD); addrArea.setForeground(TEXT_LIGHT);
        addrArea.setCaretColor(TEXT_LIGHT); addrArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        addrArea.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true), new EmptyBorder(6,10,6,10)));
        JScrollPane addrScroll = new JScrollPane(addrArea);
        addrScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        addrScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        addrScroll.setBorder(null);

        JLabel errorLbl = new JLabel(" ");
        errorLbl.setForeground(DANGER); errorLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        errorLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton regBtn = primaryBtn("Create Account");
        regBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        regBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel loginRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        loginRow.setOpaque(false); loginRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hasAcc = mutedLabel("Already have an account?  ");
        JButton loginBtn = new JButton("Sign In");
        loginBtn.setContentAreaFilled(false); loginBtn.setBorderPainted(false);
        loginBtn.setForeground(ACCENT); loginBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        loginBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loginRow.add(hasAcc); loginRow.add(loginBtn);

        for (Component c : new Component[]{t,s}) ((JLabel)c).setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(t); form.add(Box.createVerticalStrut(4)); form.add(s);
        form.add(Box.createVerticalStrut(16));
        addFormRow(form, "Full Name",         nameField);
        addFormRow(form, "Username",          userField);
        addFormRow(form, "Email",             emailField);
        addFormRow(form, "Password",          passField);
        addFormRow(form, "Confirm Password",  confirmField);
        addFormRow(form, "Address",           addrScroll);
        form.add(Box.createVerticalStrut(4));
        form.add(errorLbl); form.add(Box.createVerticalStrut(8));
        form.add(regBtn); form.add(Box.createVerticalStrut(12));
        form.add(darkSep()); form.add(Box.createVerticalStrut(10));
        form.add(loginRow);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        formWrap.add(scroll);

        root.add(formWrap, BorderLayout.CENTER);

        regBtn.addActionListener(e -> {
            String name    = nameField.getText().trim();
            String user    = userField.getText().trim();
            String email   = emailField.getText().trim();
            String pass    = new String(passField.getPassword());
            String confirm = new String(confirmField.getPassword());
            String addr    = addrArea.getText().trim();

            if (name.isEmpty()||user.isEmpty()||email.isEmpty()||pass.isEmpty()) {
                errorLbl.setText("All fields except address are required."); return;
            }
            if (!pass.equals(confirm)) { errorLbl.setText("Passwords do not match."); return; }
            if (pass.length() < 4)     { errorLbl.setText("Password must be at least 4 characters."); return; }

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO shopease.users(username,password,full_name,email,address,role) VALUES(?,?,?,?,?,'customer')")) {
                ps.setString(1,user); ps.setString(2,pass); ps.setString(3,name);
                ps.setString(4,email); ps.setString(5,addr);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(mainFrame,
                    "Registration successful! Please log in.", "Welcome!", JOptionPane.INFORMATION_MESSAGE);
                showLoginScreen();
            } catch (SQLIntegrityConstraintViolationException ex) {
                errorLbl.setText("Username or email already exists.");
            } catch (Exception ex) {
                errorLbl.setText("Error: " + ex.getMessage());
            }
        });

        loginBtn.addActionListener(e -> showLoginScreen());
        showPanel(root);
        mainFrame.setTitle("ShopEase — Register");
    }

    static void addFormRow(JPanel form, String label, JComponent field) {
        JLabel lbl = mutedLabel(label); lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lbl); form.add(Box.createVerticalStrut(3));
        form.add(field); form.add(Box.createVerticalStrut(10));
    }

    // =============================================================
    //  PRODUCTS SCREEN
    // =============================================================
    static void showProductsScreen() { showProductsScreen("", ""); }

    static void showProductsScreen(String keyword, String category) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.add(buildNavBar(), BorderLayout.NORTH);

        // ── Filter bar ────────────────────────────────────────
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        filterBar.setBackground(BG_CARD);
        filterBar.setBorder(new MatteBorder(0,0,1,0,BORDER));

        JTextField searchField = styledField();
        searchField.setPreferredSize(new Dimension(240, 34));
        searchField.setText(keyword);

        JButton searchBtn = primaryBtn("Search");

        // Category combo
        JComboBox<String> catCombo = new JComboBox<>();
        catCombo.setBackground(BG_FIELD); catCombo.setForeground(TEXT_LIGHT);
        catCombo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        catCombo.addItem("All Categories");
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT name FROM shopease.categories ORDER BY name")) {
            while (rs.next()) catCombo.addItem(rs.getString("name"));
        } catch (Exception ignored) {}
        if (!category.isEmpty()) catCombo.setSelectedItem(category);

        JButton clearBtn = secondaryBtn("Clear");
        JLabel resultLbl = mutedLabel("Loading...");

        filterBar.add(searchField); filterBar.add(searchBtn);
        filterBar.add(catCombo);    filterBar.add(clearBtn);
        filterBar.add(resultLbl);

        // ── Product grid ──────────────────────────────────────
        JPanel gridPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 14));
        gridPanel.setBackground(BG_DARK);
        gridPanel.setBorder(new EmptyBorder(14, 20, 14, 20));

        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        root.add(filterBar, BorderLayout.NORTH);
        root.add(scroll,    BorderLayout.CENTER);

        // Load products
        loadProductGrid(gridPanel, resultLbl, keyword, category);

        searchBtn.addActionListener(e ->
            loadProductGrid(gridPanel, resultLbl, searchField.getText().trim(),
                "All Categories".equals(catCombo.getSelectedItem()) ? "" : (String)catCombo.getSelectedItem()));
        searchField.addActionListener(e -> searchBtn.doClick());
        clearBtn.addActionListener(e -> {
            searchField.setText(""); catCombo.setSelectedIndex(0);
            loadProductGrid(gridPanel, resultLbl, "", "");
        });
        catCombo.addActionListener(e -> {
            searchField.setText("");
            String sel = (String)catCombo.getSelectedItem();
            loadProductGrid(gridPanel, resultLbl, "", "All Categories".equals(sel) ? "" : sel);
        });

        showPanel(root);
        mainFrame.setTitle("ShopEase — Products");
    }

    static void loadProductGrid(JPanel grid, JLabel resultLbl, String keyword, String category) {
        grid.removeAll();
        try (Connection conn = getConnection()) {
            String sql = "SELECT p.*, c.name AS cat_name FROM shopease.products p " +
                         "LEFT JOIN shopease.categories c ON p.category_id=c.id WHERE 1=1";
            if (!keyword.isEmpty())  sql += " AND (p.name LIKE ? OR p.description LIKE ?)";
            if (!category.isEmpty()) sql += " AND c.name=?";
            sql += " ORDER BY p.name";

            PreparedStatement ps = conn.prepareStatement(sql);
            int idx = 1;
            if (!keyword.isEmpty())  { ps.setString(idx++,"%"+keyword+"%"); ps.setString(idx++,"%"+keyword+"%"); }
            if (!category.isEmpty()) ps.setString(idx, category);

            ResultSet rs = ps.executeQuery();
            int count = 0;
            while (rs.next()) { grid.add(buildProductCard(rs)); count++; }
            resultLbl.setText(count + " products found");
        } catch (Exception ex) {
            resultLbl.setText("Error: " + ex.getMessage());
        }
        grid.revalidate(); grid.repaint();
    }

    static JPanel buildProductCard(ResultSet rs) throws SQLException {
        int    pid   = rs.getInt("id");
        String name  = rs.getString("name");
        String desc  = rs.getString("description");
        double price = rs.getDouble("price");
        int    stock = rs.getInt("stock");
        String cat   = rs.getString("cat_name");

        JPanel card = cardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(210, 230));
        card.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(14, 14, 14, 14)));

        JLabel catBadge = new JLabel(cat != null ? cat : "");
        catBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        catBadge.setForeground(TEXT_LIGHT); catBadge.setBackground(new Color(138,110,47));
        catBadge.setOpaque(true); catBadge.setBorder(new EmptyBorder(2,6,2,6));
        catBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLbl = new JLabel("<html><b>" + name + "</b></html>");
        nameLbl.setForeground(TEXT_LIGHT); nameLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        String shortDesc = (desc != null && desc.length() > 60) ? desc.substring(0,60)+"…" : (desc!=null?desc:"");
        JLabel descLbl = new JLabel("<html><font color='#8a97a8'>" + shortDesc + "</font></html>");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel priceLbl = new JLabel(String.format("Rs. %,.0f", price));
        priceLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        priceLbl.setForeground(ACCENT); priceLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel stockLbl = new JLabel(stock>0 ? "✓ In stock ("+stock+")" : "✗ Out of stock");
        stockLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        stockLbl.setForeground(stock>0 ? SUCCESS : DANGER);
        stockLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton addBtn = primaryBtn(stock>0 ? "Add to Cart" : "Out of Stock");
        addBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        addBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addBtn.setEnabled(stock>0);

        card.add(catBadge); card.add(Box.createVerticalStrut(8));
        card.add(nameLbl);  card.add(Box.createVerticalStrut(4));
        card.add(descLbl);  card.add(Box.createVerticalStrut(8));
        card.add(priceLbl); card.add(Box.createVerticalStrut(4));
        card.add(stockLbl); card.add(Box.createVerticalGlue());
        card.add(addBtn);

        addBtn.addActionListener(e -> {
            if (currentUserId < 0) { showLoginScreen(); return; }
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO shopease.cart(user_id,product_id,quantity) VALUES(?,?,1) " +
                    "ON DUPLICATE KEY UPDATE quantity=quantity+1")) {
                ps.setInt(1, currentUserId); ps.setInt(2, pid);
                ps.executeUpdate();
                addBtn.setText("✓ Added!");
                addBtn.setBackground(SUCCESS);
                Timer timer = new Timer(1500, ev -> { addBtn.setText("Add to Cart"); addBtn.setBackground(ACCENT); });
                timer.setRepeats(false); timer.start();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Hover highlight
        card.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { card.setBorder(new CompoundBorder(new LineBorder(ACCENT,1,true), new EmptyBorder(14,14,14,14))); }
            public void mouseExited(MouseEvent e)  { card.setBorder(new CompoundBorder(new LineBorder(BORDER,1,true), new EmptyBorder(14,14,14,14))); }
        });

        return card;
    }

    // =============================================================
    //  CART SCREEN
    // =============================================================
    static void showCartScreen() {
        if (currentUserId < 0) { showLoginScreen(); return; }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.add(buildNavBar(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(20,0));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(24, 32, 24, 32));

        JLabel title = titleLabel("Shopping Cart");
        title.setBorder(new EmptyBorder(0,0,16,0));

        // ── Table ─────────────────────────────────────────────
        String[] cols = {"Product","Unit Price","Qty","Subtotal","Action"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        styleTable(table);

        List<int[]> cartIds = new ArrayList<>(); // [cartItemId, productId, qty]
        double[] totalArr = {0};

        JLabel totalLbl = new JLabel("Total: Rs. 0");
        totalLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        totalLbl.setForeground(ACCENT);

        Runnable reloadCart = () -> {
            model.setRowCount(0); cartIds.clear(); totalArr[0] = 0;
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT c.id, c.quantity, p.id AS pid, p.name, p.price " +
                    "FROM shopease.cart c JOIN shopease.products p ON c.product_id=p.id " +
                    "WHERE c.user_id=?")) {
                ps.setInt(1, currentUserId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int    cid = rs.getInt("id");
                    int    qty = rs.getInt("quantity");
                    int    pid = rs.getInt("pid");
                    String nm  = rs.getString("name");
                    double prc = rs.getDouble("price");
                    double sub = prc * qty;
                    totalArr[0] += sub;
                    model.addRow(new Object[]{nm, String.format("Rs. %,.0f",prc), qty,
                        String.format("Rs. %,.0f",sub), "Edit"});
                    cartIds.add(new int[]{cid, pid, qty});
                }
            } catch (Exception ex) { ex.printStackTrace(); }
            totalLbl.setText(String.format("Total: Rs. %,.0f", totalArr[0]));
        };
        reloadCart.run();

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBackground(BG_CARD);
        tableScroll.getViewport().setBackground(BG_CARD);
        tableScroll.setBorder(new LineBorder(BORDER));

        // Action column: +/- /Remove buttons
        table.getColumn("Action").setCellRenderer((t, value, sel, focus, row, col) -> {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
            p.setBackground(BG_CARD);
            JButton m = secondaryBtn("−"); m.setPreferredSize(new Dimension(30,24));
            JButton pl = secondaryBtn("+"); pl.setPreferredSize(new Dimension(30,24));
            JButton rm = dangerBtn("Remove"); rm.setFont(new Font("SansSerif",Font.PLAIN,11));
            p.add(m); p.add(pl); p.add(rm);
            return p;
        });
        table.getColumn("Action").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            JPanel panel; int editRow;
            public Component getTableCellEditorComponent(JTable t,Object v,boolean sel,int row,int col) {
                editRow = row;
                panel = new JPanel(new FlowLayout(FlowLayout.CENTER,4,2));
                panel.setBackground(BG_CARD);
                JButton m  = secondaryBtn("−"); m.setPreferredSize(new Dimension(30,24));
                JButton pl = secondaryBtn("+"); pl.setPreferredSize(new Dimension(30,24));
                JButton rm = dangerBtn("Remove"); rm.setFont(new Font("SansSerif",Font.PLAIN,11));
                m.addActionListener(e  -> changeCartQty(cartIds.get(editRow)[0], cartIds.get(editRow)[2]-1, reloadCart));
                pl.addActionListener(e -> changeCartQty(cartIds.get(editRow)[0], cartIds.get(editRow)[2]+1, reloadCart));
                rm.addActionListener(e -> removeCartItem(cartIds.get(editRow)[0], reloadCart));
                panel.add(m); panel.add(pl); panel.add(rm);
                return panel;
            }
            public Object getCellEditorValue() { return ""; }
        });
        table.setRowHeight(40);

        // ── Summary panel ──────────────────────────────────────
        JPanel summary = cardPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setPreferredSize(new Dimension(260, 200));
        summary.setBorder(new EmptyBorder(20,20,20,20));

        JLabel sumTitle = sectionLabel("Order Summary");
        sumTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        totalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton checkoutBtn = primaryBtn("Proceed to Checkout →");
        checkoutBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        checkoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton clearBtn = secondaryBtn("Clear Cart");
        clearBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        clearBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        summary.add(sumTitle); summary.add(Box.createVerticalStrut(10));
        summary.add(new JSeparator()); summary.add(Box.createVerticalStrut(10));
        summary.add(totalLbl); summary.add(Box.createVerticalStrut(14));
        summary.add(checkoutBtn); summary.add(Box.createVerticalStrut(8));
        summary.add(clearBtn);

        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setBackground(BG_DARK);
        center.add(tableScroll, BorderLayout.CENTER);
        center.add(summary,     BorderLayout.EAST);

        content.add(title,  BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);

        root.add(content, BorderLayout.CENTER);

        checkoutBtn.addActionListener(e -> {
            if (model.getRowCount() == 0) {
                JOptionPane.showMessageDialog(mainFrame, "Your cart is empty."); return;
            }
            showCheckoutScreen();
        });
        clearBtn.addActionListener(e -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM shopease.cart WHERE user_id=?")) {
                ps.setInt(1, currentUserId); ps.executeUpdate();
                reloadCart.run();
            } catch (Exception ex) { ex.printStackTrace(); }
        });

        showPanel(root);
        mainFrame.setTitle("ShopEase — Cart");
    }

    static void changeCartQty(int cartId, int newQty, Runnable reload) {
        try (Connection conn = getConnection()) {
            if (newQty <= 0) {
                PreparedStatement ps = conn.prepareStatement("DELETE FROM shopease.cart WHERE id=?");
                ps.setInt(1, cartId); ps.executeUpdate();
            } else {
                PreparedStatement ps = conn.prepareStatement("UPDATE shopease.cart SET quantity=? WHERE id=?");
                ps.setInt(1, newQty); ps.setInt(2, cartId); ps.executeUpdate();
            }
        } catch (Exception ex) { ex.printStackTrace(); }
        reload.run();
    }

    static void removeCartItem(int cartId, Runnable reload) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM shopease.cart WHERE id=?")) {
            ps.setInt(1, cartId); ps.executeUpdate();
        } catch (Exception ex) { ex.printStackTrace(); }
        reload.run();
    }

    // =============================================================
    //  CHECKOUT SCREEN
    // =============================================================
    static void showCheckoutScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.add(buildNavBar(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(28, 0));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(28, 36, 28, 36));

        // ── Left: shipping form ───────────────────────────────
        JPanel formCard = cardPanel();
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(new EmptyBorder(24, 28, 24, 28));
        formCard.setPreferredSize(new Dimension(450, 300));

        JLabel formTitle = sectionLabel("Shipping Details");
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField nameField = styledField(); nameField.setText(currentFullName);
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JTextArea addrArea = new JTextArea(4, 20); addrArea.setText(currentAddress);
        addrArea.setBackground(BG_FIELD); addrArea.setForeground(TEXT_LIGHT);
        addrArea.setCaretColor(TEXT_LIGHT); addrArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        addrArea.setBorder(new CompoundBorder(new LineBorder(BORDER,1), new EmptyBorder(6,10,6,10)));
        JScrollPane addrScroll = new JScrollPane(addrArea);
        addrScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        addrScroll.setAlignmentX(Component.LEFT_ALIGNMENT); addrScroll.setBorder(null);

        JLabel errorLbl = new JLabel(" ");
        errorLbl.setForeground(DANGER); errorLbl.setFont(new Font("SansSerif",Font.PLAIN,12));
        errorLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton placeBtn = primaryBtn("Place Order  →");
        placeBtn.setFont(new Font("SansSerif",Font.BOLD,15));
        placeBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        placeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton backBtn = secondaryBtn("← Back to Cart");
        backBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        backBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        backBtn.addActionListener(e -> showCartScreen());

        formCard.add(formTitle); formCard.add(Box.createVerticalStrut(16));
        addFormRow(formCard, "Full Name", nameField);
        addFormRow(formCard, "Shipping Address", addrScroll);
        formCard.add(errorLbl); formCard.add(Box.createVerticalStrut(8));
        formCard.add(placeBtn); formCard.add(Box.createVerticalStrut(8));
        formCard.add(backBtn);

        // ── Right: order summary ──────────────────────────────
        JPanel summaryCard = cardPanel();
        summaryCard.setLayout(new BoxLayout(summaryCard, BoxLayout.Y_AXIS));
        summaryCard.setPreferredSize(new Dimension(300, 300));
        summaryCard.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel sumTitle = sectionLabel("Order Summary");
        sumTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryCard.add(sumTitle); summaryCard.add(Box.createVerticalStrut(8));
        summaryCard.add(new JSeparator()); summaryCard.add(Box.createVerticalStrut(8));

        double[] totalArr = {0};
        List<int[]> cartData = new ArrayList<>(); // [productId, qty, unitPrice]

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT c.quantity, p.id, p.name, p.price FROM shopease.cart c " +
                "JOIN shopease.products p ON c.product_id=p.id WHERE c.user_id=?")) {
            ps.setInt(1, currentUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int qty = rs.getInt("quantity"); double price = rs.getDouble("price");
                String name = rs.getString("name"); int pid = rs.getInt("id");
                double sub = qty * price; totalArr[0] += sub;
                cartData.add(new int[]{pid, qty, (int)price});
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
                row.add(bodyLabel(name + " × " + qty), BorderLayout.WEST);
                row.add(mutedLabel(String.format("Rs. %,.0f", sub)), BorderLayout.EAST);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                summaryCard.add(row); summaryCard.add(Box.createVerticalStrut(4));
            }
        } catch (Exception ex) { errorLbl.setText("Error: " + ex.getMessage()); }

        summaryCard.add(new JSeparator()); summaryCard.add(Box.createVerticalStrut(8));
        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setOpaque(false); totalRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel totalLbl = new JLabel(String.format("Rs. %,.0f", totalArr[0]));
        totalLbl.setFont(new Font("SansSerif",Font.BOLD,18)); totalLbl.setForeground(ACCENT);
        totalRow.add(sectionLabel("Total"), BorderLayout.WEST);
        totalRow.add(totalLbl, BorderLayout.EAST);
        summaryCard.add(totalRow);

        content.add(formCard,    BorderLayout.CENTER);
        content.add(summaryCard, BorderLayout.EAST);
        root.add(content, BorderLayout.CENTER);

        placeBtn.addActionListener(e -> {
            String addr = addrArea.getText().trim();
            if (addr.isEmpty()) { errorLbl.setText("Please enter a shipping address."); return; }
            try {
                int orderId = placeOrder(cartData, totalArr[0], addr);
                showOrderConfirmScreen(orderId);
            } catch (Exception ex) {
                errorLbl.setText("Order failed: " + ex.getMessage());
            }
        });

        showPanel(root);
        mainFrame.setTitle("ShopEase — Checkout");
    }

    static int placeOrder(List<int[]> items, double total, String address) throws SQLException {
        Connection conn = getConnection();
        conn.setAutoCommit(false);
        try {
            conn.createStatement().execute("USE shopease");
            PreparedStatement orderPs = conn.prepareStatement(
                "INSERT INTO shopease.orders(user_id,total_amount,shipping_address,status) VALUES(?,?,'"+address.replace("'","''")+"','Pending')",
                Statement.RETURN_GENERATED_KEYS);
            orderPs.setInt(1, currentUserId); orderPs.setDouble(2, total);
            orderPs.executeUpdate();
            ResultSet keys = orderPs.getGeneratedKeys(); keys.next();
            int orderId = keys.getInt(1);

            for (int[] item : items) {
                PreparedStatement itemPs = conn.prepareStatement(
                    "INSERT INTO shopease.order_items(order_id,product_id,quantity,unit_price) VALUES(?,?,?,?)");
                itemPs.setInt(1,orderId); itemPs.setInt(2,item[0]);
                itemPs.setInt(3,item[1]); itemPs.setInt(4,item[2]);
                itemPs.executeUpdate();
                PreparedStatement stockPs = conn.prepareStatement(
                    "UPDATE shopease.products SET stock=stock-? WHERE id=?");
                stockPs.setInt(1,item[1]); stockPs.setInt(2,item[0]);
                stockPs.executeUpdate();
            }
            PreparedStatement clearPs = conn.prepareStatement(
                "DELETE FROM shopease.cart WHERE user_id=?");
            clearPs.setInt(1, currentUserId); clearPs.executeUpdate();

            conn.commit();
            return orderId;
        } catch (SQLException ex) {
            conn.rollback(); throw ex;
        } finally { conn.setAutoCommit(true); conn.close(); }
    }

    // =============================================================
    //  ORDER CONFIRM SCREEN
    // =============================================================
    static void showOrderConfirmScreen(int orderId) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.add(buildNavBar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(BG_DARK);

        JPanel card = cardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(560, 460));
        card.setBorder(new CompoundBorder(new LineBorder(SUCCESS,2,true), new EmptyBorder(28,36,28,36)));

        JLabel tick  = new JLabel("✓", SwingConstants.CENTER);
        tick.setFont(new Font("SansSerif",Font.BOLD,52)); tick.setForeground(SUCCESS);
        tick.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel successLbl = new JLabel("Order Placed Successfully!", SwingConstants.CENTER);
        successLbl.setFont(new Font("SansSerif",Font.BOLD,22)); successLbl.setForeground(SUCCESS);
        successLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel orderIdLbl = mutedLabel("Order ID: #" + orderId);
        orderIdLbl.setFont(new Font("SansSerif",Font.PLAIN,14));
        orderIdLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(tick); card.add(Box.createVerticalStrut(8));
        card.add(successLbl); card.add(Box.createVerticalStrut(6));
        card.add(orderIdLbl); card.add(Box.createVerticalStrut(20));
        card.add(new JSeparator()); card.add(Box.createVerticalStrut(14));

        // Order items
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT oi.quantity, oi.unit_price, p.name FROM shopease.order_items oi " +
                "JOIN shopease.products p ON oi.product_id=p.id WHERE oi.order_id=?")) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JPanel row = new JPanel(new BorderLayout(10,0));
                row.setOpaque(false); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.add(bodyLabel("• " + rs.getString("name") + " × " + rs.getInt("quantity")), BorderLayout.WEST);
                row.add(mutedLabel(String.format("Rs. %,.0f", rs.getDouble("unit_price") * rs.getInt("quantity"))), BorderLayout.EAST);
                card.add(row); card.add(Box.createVerticalStrut(4));
            }
        } catch (Exception ex) { card.add(mutedLabel("Error loading items")); }

        card.add(new JSeparator()); card.add(Box.createVerticalStrut(12));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnRow.setOpaque(false); btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton shopBtn = primaryBtn("Continue Shopping");
        JButton ordersBtn = secondaryBtn("View My Orders");
        shopBtn.addActionListener(e  -> showProductsScreen());
        ordersBtn.addActionListener(e -> showOrdersScreen());
        btnRow.add(shopBtn); btnRow.add(ordersBtn);
        card.add(btnRow);

        center.add(card);
        root.add(center, BorderLayout.CENTER);
        showPanel(root);
        mainFrame.setTitle("ShopEase — Order Confirmed");
    }

    // =============================================================
    //  ORDER HISTORY SCREEN
    // =============================================================
    static void showOrdersScreen() {
        if (currentUserId < 0) { showLoginScreen(); return; }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.add(buildNavBar(), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(28, 36, 28, 36));

        JLabel title = titleLabel("My Orders");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title); content.add(Box.createVerticalStrut(16));

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM shopease.orders WHERE user_id=? ORDER BY order_date DESC")) {
            ps.setInt(1, currentUserId);
            ResultSet rs = ps.executeQuery();
            boolean any = false;
            while (rs.next()) {
                any = true;
                content.add(buildOrderCard(conn, rs));
                content.add(Box.createVerticalStrut(12));
            }
            if (!any) {
                JPanel empty = new JPanel(new GridBagLayout());
                empty.setBackground(BG_DARK);
                JLabel noOrders = bodyLabel("No orders yet. Start shopping!");
                noOrders.setFont(new Font("SansSerif",Font.PLAIN,16));
                JButton shopBtn = primaryBtn("Browse Products");
                shopBtn.addActionListener(e -> showProductsScreen());
                JPanel ep = new JPanel(); ep.setOpaque(false);
                ep.setLayout(new BoxLayout(ep, BoxLayout.Y_AXIS));
                noOrders.setAlignmentX(Component.CENTER_ALIGNMENT);
                shopBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
                ep.add(noOrders); ep.add(Box.createVerticalStrut(12)); ep.add(shopBtn);
                empty.add(ep);
                content.add(empty);
            }
        } catch (Exception ex) {
            content.add(new JLabel("Error: " + ex.getMessage()));
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBackground(BG_DARK); scroll.getViewport().setBackground(BG_DARK);
        scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(12);
        root.add(scroll, BorderLayout.CENTER);

        showPanel(root);
        mainFrame.setTitle("ShopEase — My Orders");
    }

    static JPanel buildOrderCard(Connection conn, ResultSet rs) throws SQLException {
        int    oid     = rs.getInt("id");
        double total   = rs.getDouble("total_amount");
        String status  = rs.getString("status");
        String date    = rs.getString("order_date");
        String address = rs.getString("shipping_address");

        JPanel card = cardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);

        JLabel orderIdLbl = bodyLabel("Order #" + oid + "   ");
        orderIdLbl.setFont(new Font("SansSerif",Font.BOLD,15));

        JLabel dateLbl = mutedLabel(date.substring(0,16));

        Color statusColor = status.equals("Delivered") ? SUCCESS
                          : status.equals("Cancelled") ? DANGER
                          : status.equals("Shipped")   ? new Color(52,152,219) : ACCENT;
        JLabel statusBadge = new JLabel(status);
        statusBadge.setFont(new Font("SansSerif",Font.BOLD,11));
        statusBadge.setForeground(Color.WHITE); statusBadge.setBackground(statusColor);
        statusBadge.setOpaque(true); statusBadge.setBorder(new EmptyBorder(2,8,2,8));

        JLabel totalLbl = new JLabel(String.format("Rs. %,.0f", total));
        totalLbl.setFont(new Font("SansSerif",Font.BOLD,15)); totalLbl.setForeground(ACCENT);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        rightHeader.setOpaque(false);
        rightHeader.add(statusBadge); rightHeader.add(totalLbl);

        headerRow.add(new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)){{
            setOpaque(false); add(orderIdLbl); add(dateLbl);
        }}, BorderLayout.WEST);
        headerRow.add(rightHeader, BorderLayout.EAST);

        card.add(headerRow); card.add(Box.createVerticalStrut(8));
        card.add(new JSeparator()); card.add(Box.createVerticalStrut(8));

        // Items in this order
        try (PreparedStatement ps2 = conn.prepareStatement(
                "SELECT oi.quantity, oi.unit_price, p.name FROM shopease.order_items oi " +
                "JOIN shopease.products p ON oi.product_id=p.id WHERE oi.order_id=?")) {
            ps2.setInt(1, oid);
            ResultSet rs2 = ps2.executeQuery();
            while (rs2.next()) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.add(mutedLabel("  • " + rs2.getString("name") + " × " + rs2.getInt("quantity")), BorderLayout.WEST);
                row.add(mutedLabel(String.format("Rs. %,.0f", rs2.getDouble("unit_price")*rs2.getInt("quantity"))), BorderLayout.EAST);
                card.add(row);
            }
        }
        card.add(Box.createVerticalStrut(6));
        JLabel addrLbl = mutedLabel("📍 " + address);
        addrLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(addrLbl);

        return card;
    }

    // =============================================================
    //  ADMIN SCREEN
    // =============================================================
    static void showAdminScreen() {
        if (!"admin".equals(currentRole)) { showProductsScreen(); return; }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DARK);
        root.add(buildNavBar(), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(24, 32, 24, 32));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JLabel title = titleLabel("Product Management");
        JButton addBtn = primaryBtn("+ Add Product");
        titleBar.add(title,  BorderLayout.WEST);
        titleBar.add(addBtn, BorderLayout.EAST);

        // Table
        String[] cols = {"ID","Name","Category","Price","Stock"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r,int c) { return false; }
        };
        JTable table = new JTable(model);
        styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(BG_CARD);
        scroll.setBorder(new LineBorder(BORDER));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        btnRow.setBackground(BG_DARK);
        JButton editBtn = secondaryBtn("Edit Selected");
        JButton delBtn  = dangerBtn("Delete Selected");
        btnRow.add(editBtn); btnRow.add(delBtn);

        content.add(titleBar, BorderLayout.NORTH);
        content.add(scroll,   BorderLayout.CENTER);
        content.add(btnRow,   BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        Runnable reload = () -> {
            model.setRowCount(0);
            try (Connection conn = getConnection();
                 ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT p.id, p.name, c.name AS cat, p.price, p.stock " +
                    "FROM shopease.products p LEFT JOIN shopease.categories c ON p.category_id=c.id ORDER BY p.name")) {
                while (rs.next()) model.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("name"), rs.getString("cat"),
                    String.format("Rs. %,.0f", rs.getDouble("price")), rs.getInt("stock")});
            } catch (Exception ex) { ex.printStackTrace(); }
        };
        reload.run();

        addBtn.addActionListener(e  -> showProductDialog(-1, reload));
        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame,"Select a product first."); return; }
            showProductDialog((int)model.getValueAt(row,0), reload);
        });
        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(mainFrame,"Select a product first."); return; }
            int pid = (int)model.getValueAt(row,0);
            String name = (String)model.getValueAt(row,1);
            int confirm = JOptionPane.showConfirmDialog(mainFrame,
                "Delete \""+name+"\"?","Confirm Delete",JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try (Connection conn = getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM shopease.products WHERE id=?")) {
                    ps.setInt(1, pid); ps.executeUpdate(); reload.run();
                } catch (Exception ex) { JOptionPane.showMessageDialog(mainFrame,"Error: "+ex.getMessage()); }
            }
        });

        showPanel(root);
        mainFrame.setTitle("ShopEase — Admin");
    }

    static void showProductDialog(int productId, Runnable reload) {
        boolean isEdit = productId > 0;
        JDialog dialog = new JDialog(mainFrame, isEdit ? "Edit Product" : "Add Product", true);
        dialog.setSize(440, 400);
        dialog.setLocationRelativeTo(mainFrame);
        dialog.getContentPane().setBackground(BG_CARD);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_CARD);
        panel.setBorder(new EmptyBorder(24,28,24,28));

        JTextField nameField  = styledField(); nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JTextField priceField = styledField(); priceField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JTextField stockField = styledField(); stockField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JTextArea  descArea   = new JTextArea(3,20);
        descArea.setBackground(BG_FIELD); descArea.setForeground(TEXT_LIGHT);
        descArea.setFont(new Font("SansSerif",Font.PLAIN,13));
        descArea.setBorder(new CompoundBorder(new LineBorder(BORDER,1), new EmptyBorder(6,10,6,10)));
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        descScroll.setAlignmentX(Component.LEFT_ALIGNMENT); descScroll.setBorder(null);

        JComboBox<String> catCombo = new JComboBox<>();
        catCombo.setBackground(BG_FIELD); catCombo.setForeground(TEXT_LIGHT);
        catCombo.setFont(new Font("SansSerif",Font.PLAIN,13));
        catCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));
        List<Integer> catIds = new ArrayList<>();
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT id,name FROM shopease.categories ORDER BY name")) {
            while (rs.next()) { catCombo.addItem(rs.getString("name")); catIds.add(rs.getInt("id")); }
        } catch (Exception ignored) {}

        // If editing, pre-fill fields
        if (isEdit) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.*,c.name AS cat FROM shopease.products p LEFT JOIN shopease.categories c ON p.category_id=c.id WHERE p.id=?")) {
                ps.setInt(1, productId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    nameField.setText(rs.getString("name"));
                    priceField.setText(String.valueOf(rs.getInt("price")));
                    stockField.setText(String.valueOf(rs.getInt("stock")));
                    descArea.setText(rs.getString("description") != null ? rs.getString("description") : "");
                    String cat = rs.getString("cat");
                    if (cat != null) catCombo.setSelectedItem(cat);
                }
            } catch (Exception ignored) {}
        }

        addFormRow(panel, "Product Name", nameField);
        addFormRow(panel, "Price (Rs.)",  priceField);
        addFormRow(panel, "Stock",        stockField);
        addFormRow(panel, "Category",     catCombo);
        addFormRow(panel, "Description",  descScroll);

        JButton saveBtn = primaryBtn("Save Product");
        saveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        saveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(6)); panel.add(saveBtn);

        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String desc = descArea.getText().trim();
            int catIdx  = catCombo.getSelectedIndex();
            try {
                double price = Double.parseDouble(priceField.getText().trim());
                int stock    = Integer.parseInt(stockField.getText().trim());
                int catId    = catIds.isEmpty() ? 1 : catIds.get(catIdx);

                try (Connection conn = getConnection()) {
                    if (isEdit) {
                        PreparedStatement ps = conn.prepareStatement(
                            "UPDATE shopease.products SET name=?,description=?,price=?,stock=?,category_id=? WHERE id=?");
                        ps.setString(1,name); ps.setString(2,desc); ps.setDouble(3,price);
                        ps.setInt(4,stock); ps.setInt(5,catId); ps.setInt(6,productId);
                        ps.executeUpdate();
                    } else {
                        PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO shopease.products(name,description,price,stock,category_id) VALUES(?,?,?,?,?)");
                        ps.setString(1,name); ps.setString(2,desc); ps.setDouble(3,price);
                        ps.setInt(4,stock); ps.setInt(5,catId);
                        ps.executeUpdate();
                    }
                }
                reload.run();
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog,"Price and Stock must be valid numbers.","Validation",JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,"Error: "+ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.add(new JScrollPane(panel) {{ setBorder(null); getViewport().setBackground(BG_CARD); }});
        dialog.setVisible(true);
    }

    // =============================================================
    //  WRAP LAYOUT  (FlowLayout that wraps to next line properly)
    // =============================================================
    static class WrapLayout extends FlowLayout {
        public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }
        @Override public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean pref) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - insets.left - insets.right - hgap * 2;
                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;

                for (Component comp : target.getComponents()) {
                    if (!comp.isVisible()) continue;
                    Dimension d = pref ? comp.getPreferredSize() : comp.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        dim.width = Math.max(dim.width, rowWidth);
                        dim.height += rowHeight + vgap;
                        rowWidth = 0; rowHeight = 0;
                    }
                    rowWidth += d.width + hgap;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                dim.width  = Math.max(dim.width, rowWidth);
                dim.height += rowHeight + insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }
    }
}