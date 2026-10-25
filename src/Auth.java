
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.*;
import java.awt.*;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class Auth {

    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Auth() {}

    public static final class User {
        public final int id;
        public final String username;
        public final String role;

        public User(int id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    public static void initialize() throws SQLException {
        try (Connection con = Database.connect();
             Statement st = con.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    role TEXT NOT NULL
                        CHECK(role IN ('ADMIN', 'CASHIER')),
                    active INTEGER NOT NULL DEFAULT 1
                        CHECK(active IN (0, 1)),
                    created_at TEXT NOT NULL
                        DEFAULT (datetime('now','localtime'))
                )
            """);
        }
    }

    public static boolean hasUsers() throws SQLException {
        try (Connection con = Database.connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public static User loginOrSetup() {
        try {
            if (!hasUsers()) {
                if (!createFirstAdmin()) {
                    return null;
                }
            }

            while (true) {
                JTextField usernameField = new JTextField();
                JPasswordField passwordField = new JPasswordField();

                JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
                panel.add(new JLabel("Username:"));
                panel.add(usernameField);
                panel.add(new JLabel("Password:"));
                panel.add(passwordField);

                int choice = JOptionPane.showConfirmDialog(
                        null,
                        panel,
                        "ShopPOS Login",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );

                if (choice != JOptionPane.OK_OPTION) {
                    return null;
                }

                try {
                    User user = authenticate(
                            usernameField.getText(),
                            passwordField.getPassword()
                    );

                    if (user != null) {
                        return user;
                    }

                    JOptionPane.showMessageDialog(
                            null,
                            "Invalid username or password, "
                                    + "or the account is disabled.",
                            "Login Failed",
                            JOptionPane.ERROR_MESSAGE
                    );

                } finally {
                    passwordField.setText("");
                }
            }

        } catch (SQLException | GeneralSecurityException ex) {
            JOptionPane.showMessageDialog(
                    null,
                    "Authentication error:\n" + ex.getMessage(),
                    "ShopPOS Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private static boolean createFirstAdmin()
            throws SQLException, GeneralSecurityException {

        while (true) {
            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();
            JPasswordField confirmField = new JPasswordField();

            JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
            panel.add(new JLabel("Create the first administrator"));
            panel.add(new JLabel("Username:"));
            panel.add(usernameField);
            panel.add(new JLabel("Password (at least 8 characters):"));
            panel.add(passwordField);
            panel.add(new JLabel("Confirm password:"));
            panel.add(confirmField);

            int choice = JOptionPane.showConfirmDialog(
                    null,
                    panel,
                    "ShopPOS Initial Setup",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (choice != JOptionPane.OK_OPTION) {
                passwordField.setText("");
                confirmField.setText("");
                return false;
            }

            char[] password = passwordField.getPassword();
            char[] confirmation = confirmField.getPassword();

            try {
                String username = normalizeUsername(
                        usernameField.getText());

                if (username.length() < 3
                        || username.length() > 40) {
                    throw new IllegalArgumentException(
                            "Username must be 3–40 characters.");
                }

                if (password.length < 8) {
                    throw new IllegalArgumentException(
                            "Password must contain at least 8 characters.");
                }

                if (!MessageDigest.isEqual(
                        new String(password).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8),
                        new String(confirmation).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException(
                            "The passwords do not match.");
                }

                createUser(username, password, "ADMIN");
                JOptionPane.showMessageDialog(
                        null,
                        "Administrator created. Please log in.");
                return true;

            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(
                        null, ex.getMessage(),
                        "Invalid Details",
                        JOptionPane.WARNING_MESSAGE
                );
            } catch (SQLException | GeneralSecurityException ex) {
                JOptionPane.showMessageDialog(
                        null,
                        "Could not create administrator:\n"
                                + ex.getMessage(),
                        "Setup Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return false;
            } finally {
                java.util.Arrays.fill(password, '\0');
                java.util.Arrays.fill(confirmation, '\0');
                passwordField.setText("");
                confirmField.setText("");
            }
        }
    }

    private static User authenticate(String username, char[] password)
            throws SQLException, GeneralSecurityException {

        String normalized;

        try {
            normalized = normalizeUsername(username);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String sql = """
            SELECT id, username, password_hash, password_salt, role
            FROM users
            WHERE username = ? AND active = 1
        """;

        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, normalized);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                byte[] salt = Base64.getDecoder().decode(
                        rs.getString("password_salt"));

                byte[] expected = Base64.getDecoder().decode(
                        rs.getString("password_hash"));

                byte[] actual = hashPassword(password, salt);

                if (!MessageDigest.isEqual(expected, actual)) {
                    return null;
                }

                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role")
                );
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public static void manageUsers(User currentUser) {
        if (currentUser == null || !currentUser.isAdmin()) {
            JOptionPane.showMessageDialog(
                    null,
                    "Only administrators can manage accounts.",
                    "Access Denied",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        while (true) {
            String[] options = {
                    "Create Cashier",
                    "Create Administrator",
                    "View Accounts",
                    "Disable Account",
                    "Enable Account",
                    "Back"
            };

            int choice = JOptionPane.showOptionDialog(
                    null,
                    "User Account Management",
                    "ShopPOS Administration",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            try {
                switch (choice) {
                    case 0 -> createAccountDialog("CASHIER");
                    case 1 -> createAccountDialog("ADMIN");
                    case 2 -> showAccounts();
                    case 3 -> setAccountStatus(false, currentUser);
                    case 4 -> setAccountStatus(true, currentUser);
                    default -> {
                        return;
                    }
                }
            } catch (SQLException | GeneralSecurityException ex) {
                JOptionPane.showMessageDialog(
                        null,
                        "Account operation failed:\n" + ex.getMessage(),
                        "Account Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private static void createAccountDialog(String role)
            throws SQLException, GeneralSecurityException {

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JPasswordField confirmField = new JPasswordField();

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Account type: " + role));
        panel.add(new JLabel("Username (3–40 characters):"));
        panel.add(usernameField);
        panel.add(new JLabel("Password (at least 8 characters):"));
        panel.add(passwordField);
        panel.add(new JLabel("Confirm password:"));
        panel.add(confirmField);

        int choice = JOptionPane.showConfirmDialog(
                null, panel,
                "Create " + role + " Account",
                JOptionPane.OK_CANCEL_OPTION
        );

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        char[] password = passwordField.getPassword();
        char[] confirmation = confirmField.getPassword();

        try {
            String username = normalizeUsername(usernameField.getText());

            if (username.length() < 3 || username.length() > 40) {
                throw new IllegalArgumentException(
                        "Username must be 3–40 characters.");
            }

            if (password.length < 8) {
                throw new IllegalArgumentException(
                        "Password must contain at least 8 characters.");
            }

            if (!MessageDigest.isEqual(
                    new String(password).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8),
                    new String(confirmation).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException(
                        "The passwords do not match.");
            }

            createUser(username, password, role);

            JOptionPane.showMessageDialog(
                    null, role + " account created successfully.");

        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                    null, ex.getMessage(),
                    "Invalid Details",
                    JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            if (ex.getMessage() != null
                    && ex.getMessage().toLowerCase(Locale.ROOT)
                    .contains("unique")) {
                JOptionPane.showMessageDialog(
                        null,
                        "That username already exists.",
                        "Duplicate Username",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                throw ex;
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
            java.util.Arrays.fill(confirmation, '\0');
            passwordField.setText("");
            confirmField.setText("");
        }
    }

    private static void createUser(
            String username, char[] password, String role)
            throws SQLException, GeneralSecurityException {

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] hash = hashPassword(password, salt);

        String sql = """
            INSERT INTO users
                (username, password_hash, password_salt, role)
            VALUES (?, ?, ?, ?)
        """;

        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, Base64.getEncoder().encodeToString(hash));
            ps.setString(3, Base64.getEncoder().encodeToString(salt));
            ps.setString(4, role);
            ps.executeUpdate();
        }
    }

    private static byte[] hashPassword(char[] password, byte[] salt)
            throws GeneralSecurityException {

        PBEKeySpec spec = new PBEKeySpec(
                password, salt, ITERATIONS, KEY_BITS);

        try {
            return SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username is required.");
        }

        String value = username.trim().toLowerCase(Locale.ROOT);

        if (!value.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "Use only letters, numbers, dots, underscores, "
                            + "or hyphens in usernames.");
        }

        return value;
    }

    private static void showAccounts() throws SQLException {
        String sql = """
            SELECT id, username, role, active, created_at
            FROM users ORDER BY id
        """;

        List<String> lines = new ArrayList<>();

        try (Connection con = Database.connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                lines.add(String.format(
                        "#%d | %s | %s | %s | Created: %s",
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getInt("active") == 1
                                ? "ACTIVE" : "DISABLED",
                        rs.getString("created_at")
                ));
            }
        }

        JTextArea area = new JTextArea(
                String.join("\n", lines), 15, 60);
        area.setEditable(false);

        JOptionPane.showMessageDialog(
                null,
                new JScrollPane(area),
                "Registered Accounts",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static void setAccountStatus(
            boolean active, User currentUser) throws SQLException {

        String sql = """
            SELECT id, username, role, active
            FROM users
            WHERE id <> ?
            ORDER BY username
        """;

        List<User> candidates = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, currentUser.id);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean currentlyActive =
                            rs.getInt("active") == 1;

                    if (currentlyActive != active) {
                        candidates.add(new User(
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getString("role")
                        ));

                        labels.add(rs.getString("username")
                                + " (" + rs.getString("role") + ")");
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(
                    null, "No matching accounts found.");
            return;
        }

        String selected = (String) JOptionPane.showInputDialog(
                null,
                active ? "Select account to disable:"
                        : "Select account to enable:",
                "Account Status",
                JOptionPane.PLAIN_MESSAGE,
                null,
                labels.toArray(),
                labels.get(0)
        );

        if (selected == null) {
            return;
        }

        int index = labels.indexOf(selected);
        User target = candidates.get(index);

        // Prevent accidentally locking out all administrators.
        if (!active && "ADMIN".equals(target.role)
                && countActiveAdmins() <= 1) {
            JOptionPane.showMessageDialog(
                    null,
                    "Cannot disable the last active administrator.");
            return;
        }

        try (Connection con = Database.connect();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE users SET active = ? WHERE id = ?")) {

            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, target.id);
            ps.executeUpdate();
        }

        JOptionPane.showMessageDialog(
                null,
                target.username + (active
                        ? " has been enabled."
                        : " has been disabled."));
    }

    private static int countActiveAdmins() throws SQLException {
        try (Connection con = Database.connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM users "
                             + "WHERE role='ADMIN' AND active=1")) {

            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
