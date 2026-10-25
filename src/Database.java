import java.sql.*;
import java.util.*;

public class Database {

    private static final String URL = "jdbc:sqlite:shoppos.db";

    public static class Product {
        public final int id;
        public final String name;
        public final long priceCents;
        public final int stock;
        public final String imagePath;

        public Product(int id, String name,
                       long priceCents, int stock) {
            this(id, name, priceCents, stock, null);
        }

        public Product(int id, String name,
                       long priceCents, int stock, String imagePath) {
            this.id = id;
            this.name = name;
            this.priceCents = priceCents;
            this.stock = stock;
            this.imagePath = imagePath;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Product)) return false;
            return id == ((Product) other).id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initialize() throws SQLException {
        try (Connection con = connect();
             Statement st = con.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    price_cents INTEGER NOT NULL CHECK(price_cents >= 0),
                    stock INTEGER NOT NULL CHECK(stock >= 0),
                    image_path TEXT,
                    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1))
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sales (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sale_date TEXT NOT NULL DEFAULT
                        (datetime('now','localtime')),
                    total_cents INTEGER NOT NULL,
                    paid_cents INTEGER NOT NULL,
                    change_cents INTEGER NOT NULL
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sale_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sale_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    product_name TEXT NOT NULL,
                    unit_price_cents INTEGER NOT NULL,
                    quantity INTEGER NOT NULL,
                    line_total_cents INTEGER NOT NULL,
                    FOREIGN KEY(sale_id) REFERENCES sales(id),
                    FOREIGN KEY(product_id) REFERENCES products(id)
                )
            """);

            st.execute("PRAGMA foreign_keys = ON");
        }

        // Safely upgrade existing databases without deleting products or sales.
        ensureImageColumn();
        ensureActiveColumn();
        seedProducts();
    }


    private static void ensureImageColumn() throws SQLException {
        boolean found = false;
        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(products)")) {
            while (rs.next()) {
                if ("image_path".equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            try (Connection con = connect();
                 Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE products ADD COLUMN image_path TEXT");
            }
        }
    }

    private static void ensureActiveColumn() throws SQLException {
        boolean found = false;
        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(products)")) {
            while (rs.next()) {
                if ("is_active".equalsIgnoreCase(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            try (Connection con = connect();
                 Statement st = con.createStatement()) {
                st.executeUpdate(
                        "ALTER TABLE products ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1");
            }
        }
    }

    public static void updateProduct(int id, String name, long priceCents,
                                     int stock, String imagePath)
            throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new SQLException("Product name is required.");
        }
        if (priceCents < 0) {
            throw new SQLException("Price cannot be negative.");
        }
        if (stock < 0) {
            throw new SQLException("Stock cannot be negative.");
        }

        String sql = "UPDATE products SET name = ?, price_cents = ?, stock = ?, image_path = ? "
                + "WHERE id = ? AND is_active = 1";
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setLong(2, priceCents);
            ps.setInt(3, stock);
            if (imagePath == null || imagePath.isBlank()) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, imagePath);
            }
            ps.setInt(5, id);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Product not found or already deleted.");
            }
        }
    }

    /** Archives a product so it disappears from selling screens but old sales remain intact. */
    public static void deleteProduct(int id) throws SQLException {
        String sql = "UPDATE products SET is_active = 0 WHERE id = ? AND is_active = 1";
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Product not found or already deleted.");
            }
        }
    }

    public static void addProduct(String name, long priceCents,
                                  int stock, String imagePath)
            throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new SQLException("Product name is required.");
        }
        if (priceCents < 0) {
            throw new SQLException("Price cannot be negative.");
        }
        if (stock < 0) {
            throw new SQLException("Stock cannot be negative.");
        }

        String sql = """
            INSERT INTO products (name, price_cents, stock, image_path)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setLong(2, priceCents);
            ps.setInt(3, stock);
            if (imagePath == null || imagePath.isBlank()) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, imagePath);
            }
            ps.executeUpdate();
        }
    }

    private static void seedProducts() throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO products
                (name, price_cents, stock)
            VALUES (?, ?, ?)
        """;

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {

            String[][] initial = {
                    {"Rice", "12000", "20"},
                    {"Bread", "2500", "30"},
                    {"Milk", "3500", "15"},
                    {"Sugar", "4000", "25"},
                    {"Cooking Oil", "8500", "12"},
                    {"Bottled Water", "800", "50"}
            };

            for (String[] item : initial) {
                ps.setString(1, item[0]);
                ps.setLong(2, Long.parseLong(item[1]));
                ps.setInt(3, Integer.parseInt(item[2]));
                ps.executeUpdate();
            }
        }
    }

    public static List<Product> loadProducts()
            throws SQLException {

        List<Product> result = new ArrayList<>();

        String sql = """
            SELECT id, name, price_cents, stock, image_path
            FROM products
            WHERE is_active = 1
            ORDER BY id
        """;

        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                result.add(new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getLong("price_cents"),
                        rs.getInt("stock"),
                        rs.getString("image_path")
                ));
            }
        }

        return result;
    }

    public static long saveSale(
            Map<Product, Integer> cart, long paidCents)
            throws SQLException {

        if (cart.isEmpty()) {
            throw new SQLException("The cart is empty.");
        }

        try (Connection con = connect()) {
            con.setAutoCommit(false);

            try {
                long totalCents = 0;

                // Re-read current prices and stock from the database.
                String productSql = """
                    SELECT name, price_cents, stock
                    FROM products WHERE id = ? AND is_active = 1
                """;

                try (PreparedStatement ps =
                             con.prepareStatement(productSql)) {

                    for (Map.Entry<Product, Integer> entry
                            : cart.entrySet()) {

                        int productId = entry.getKey().id;
                        int quantity = entry.getValue();

                        if (quantity <= 0) {
                            throw new SQLException(
                                    "Invalid product quantity.");
                        }

                        ps.setInt(1, productId);

                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException(
                                        "Product no longer exists.");
                            }

                            int stock = rs.getInt("stock");
                            long price = rs.getLong("price_cents");

                            if (quantity > stock) {
                                throw new SQLException(
                                        "Insufficient stock for "
                                                + rs.getString("name"));
                            }

                            totalCents = Math.addExact(
                                    totalCents,
                                    Math.multiplyExact(price, quantity)
                            );
                        }
                    }
                }

                if (paidCents < totalCents) {
                    throw new SQLException(
                            "Payment is less than the sale total.");
                }

                long changeCents = paidCents - totalCents;

                String saleSql = """
                    INSERT INTO sales
                        (total_cents, paid_cents, change_cents)
                    VALUES (?, ?, ?)
                """;

                long saleId;

                try (PreparedStatement ps = con.prepareStatement(
                        saleSql,
                        Statement.RETURN_GENERATED_KEYS)) {

                    ps.setLong(1, totalCents);
                    ps.setLong(2, paidCents);
                    ps.setLong(3, changeCents);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not obtain sale ID.");
                        }
                        saleId = keys.getLong(1);
                    }
                }

                String itemSql = """
                    INSERT INTO sale_items
                        (sale_id, product_id, product_name,
                         unit_price_cents, quantity, line_total_cents)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;

                String stockSql = """
                    UPDATE products
                    SET stock = stock - ?
                    WHERE id = ? AND stock >= ? AND is_active = 1
                """;

                try (PreparedStatement itemPs =
                             con.prepareStatement(itemSql);
                     PreparedStatement stockPs =
                             con.prepareStatement(stockSql)) {

                    for (Map.Entry<Product, Integer> entry
                            : cart.entrySet()) {

                        Product product = entry.getKey();
                        int quantity = entry.getValue();

                        long price;

                        try (PreparedStatement pricePs =
                                     con.prepareStatement(
                                             "SELECT price_cents FROM products "
                                                     + "WHERE id = ?")) {
                            pricePs.setInt(1, product.id);

                            try (ResultSet rs =
                                         pricePs.executeQuery()) {
                                if (!rs.next()) {
                                    throw new SQLException(
                                            "Product not found.");
                                }
                                price = rs.getLong(1);
                            }
                        }

                        long lineTotal = Math.multiplyExact(
                                price, quantity);

                        itemPs.setLong(1, saleId);
                        itemPs.setInt(2, product.id);
                        itemPs.setString(3, product.name);
                        itemPs.setLong(4, price);
                        itemPs.setInt(5, quantity);
                        itemPs.setLong(6, lineTotal);
                        itemPs.executeUpdate();

                        stockPs.setInt(1, quantity);
                        stockPs.setInt(2, product.id);
                        stockPs.setInt(3, quantity);

                        if (stockPs.executeUpdate() != 1) {
                            throw new SQLException(
                                    "Stock changed. Please try again.");
                        }
                    }
                }

                con.commit();
                return saleId;

            } catch (Exception ex) {
                con.rollback();

                if (ex instanceof SQLException) {
                    throw (SQLException) ex;
                }

                throw new SQLException(
                        "Sale could not be saved.", ex);

            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public static List<String> loadSalesHistory()
            throws SQLException {

        List<String> history = new ArrayList<>();

        String sql = """
            SELECT id, sale_date, total_cents,
                   paid_cents, change_cents
            FROM sales ORDER BY id DESC
        """;

        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                history.add(String.format(
                        "Sale #%d | %s | Total GH₵%.2f | Paid GH₵%.2f",
                        rs.getLong("id"),
                        rs.getString("sale_date"),
                        rs.getLong("total_cents") / 100.0,
                        rs.getLong("paid_cents") / 100.0
                ));
            }
        }

        return history;
    }
}
