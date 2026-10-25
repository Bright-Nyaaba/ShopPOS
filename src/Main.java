import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class Main extends JFrame {

    private final Auth.User currentUser;

    private final LinkedHashMap<Database.Product, Integer> cart =
            new LinkedHashMap<>();

    private DefaultTableModel productModel;
    private DefaultTableModel cartModel;
    private JTable productTable;
    private JTable cartTable;
    private JLabel totalLabel;
    private JLabel statusLabel;

    public Main(Auth.User currentUser) {
        this.currentUser = currentUser;
        setTitle("ShopPOS - Database Edition");
        setSize(1050, 650);
        setMinimumSize(new Dimension(850, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        buildInterface();
        refreshProducts();
        refreshCart();
    }

    private void buildInterface() {
        setLayout(new BorderLayout(10, 10));

        JLabel heading = new JLabel(
                "SHOP POS - SALES MANAGEMENT",
                SwingConstants.CENTER);
        heading.setFont(new Font("Arial", Font.BOLD, 23));
        heading.setOpaque(true);
        heading.setBackground(new Color(30, 90, 160));
        heading.setForeground(Color.WHITE);
        heading.setBorder(
                BorderFactory.createEmptyBorder(18, 10, 18, 10));

        add(heading, BorderLayout.NORTH);

        productModel = new DefaultTableModel(
                new String[]{"Image", "ID", "Product", "Price (GH₵)", "Stock"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        productTable = new JTable(productModel);
        productTable.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        productTable.setRowHeight(58);
        productTable.getColumnModel().getColumn(0).setPreferredWidth(64);
        productTable.getColumnModel().getColumn(0).setMaxWidth(72);
        productTable.getColumnModel().getColumn(0).setCellRenderer(
                new javax.swing.table.DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(
                            JTable table, Object value, boolean isSelected,
                            boolean hasFocus, int row, int column) {
                        JLabel label = (JLabel) super.getTableCellRendererComponent(
                                table, "", isSelected, hasFocus, row, column);
                        label.setHorizontalAlignment(SwingConstants.CENTER);
                        label.setIcon(value instanceof ImageIcon ? (ImageIcon) value : null);
                        return label;
                    }
                });

        JPanel productPanel = new JPanel(new BorderLayout(5, 5));
        productPanel.setBorder(
                BorderFactory.createTitledBorder("Available Products"));
        productPanel.add(new JScrollPane(productTable));

        JButton addButton = new JButton("Add to Cart");
        addButton.addActionListener(e -> addToCart());

        JPanel productButtons = new JPanel();
        productButtons.add(addButton);
        if (currentUser.isAdmin()) {
            JButton newProductButton = new JButton("Add New Product");
            newProductButton.addActionListener(e -> addNewProduct());
            productButtons.add(newProductButton);

            JButton editProductButton = new JButton("Edit Selected");
            editProductButton.addActionListener(e -> editSelectedProduct());
            productButtons.add(editProductButton);

            JButton deleteProductButton = new JButton("Delete Selected");
            deleteProductButton.addActionListener(e -> deleteSelectedProduct());
            productButtons.add(deleteProductButton);
        }
        productPanel.add(productButtons, BorderLayout.SOUTH);

        cartModel = new DefaultTableModel(
                new String[]{"Product", "Price", "Quantity", "Line Total"},
                0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        cartTable = new JTable(cartModel);
        cartTable.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        cartTable.setRowHeight(25);

        JPanel cartPanel = new JPanel(new BorderLayout(5, 5));
        cartPanel.setBorder(
                BorderFactory.createTitledBorder("Shopping Cart"));
        cartPanel.add(new JScrollPane(cartTable));

        JButton increaseButton = new JButton("+ Quantity");
        increaseButton.addActionListener(e -> changeQuantity(1));

        JButton decreaseButton = new JButton("- Quantity");
        decreaseButton.addActionListener(e -> changeQuantity(-1));

        JButton removeButton = new JButton("Remove Item");
        removeButton.addActionListener(e -> removeItem());

        JButton clearButton = new JButton("Clear Cart");
        clearButton.addActionListener(e -> {
            cart.clear();
            refreshCart();
        });

        JPanel cartButtons = new JPanel();
        cartButtons.add(increaseButton);
        cartButtons.add(decreaseButton);
        cartButtons.add(removeButton);
        cartButtons.add(clearButton);
        cartPanel.add(cartButtons, BorderLayout.SOUTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 10, 10));
        center.setBorder(
                BorderFactory.createEmptyBorder(10, 10, 5, 10));
        center.add(productPanel);
        center.add(cartPanel);
        add(center, BorderLayout.CENTER);

        totalLabel = new JLabel("Total: GH₵0.00");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 20));

        statusLabel = new JLabel("Database ready.");

        JButton checkoutButton = new JButton("CHECKOUT");
        checkoutButton.setFont(new Font("Arial", Font.BOLD, 16));
        checkoutButton.setBackground(new Color(30, 140, 75));
        checkoutButton.setForeground(Color.WHITE);
        checkoutButton.addActionListener(e -> checkout());

        JButton historyButton = new JButton("Sales History");
        historyButton.addActionListener(e -> showSalesHistory());

        JButton usersButton = new JButton("Manage Users");
        usersButton.addActionListener(e -> Auth.manageUsers(currentUser));

        JPanel actions = new JPanel();
        actions.add(historyButton);
        actions.add(checkoutButton);
        if (currentUser.isAdmin()) {
            actions.add(usersButton);
        }

        JPanel bottom = new JPanel(new BorderLayout(10, 5));
        bottom.setBorder(
                BorderFactory.createEmptyBorder(5, 15, 12, 15));

        bottom.add(totalLabel, BorderLayout.WEST);
        bottom.add(actions, BorderLayout.EAST);
        bottom.add(statusLabel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }


    private ImageIcon loadThumbnail(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        try {
            Path path = Path.of(imagePath);
            if (!Files.exists(path)) {
                return null;
            }
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                return null;
            }
            Image scaled = image.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private void addNewProduct() {
        JTextField nameField = new JTextField();
        JTextField priceField = new JTextField();
        JTextField stockField = new JTextField("0");
        JLabel imageLabel = new JLabel("No image selected");
        File[] selectedImage = new File[1];

        JButton chooseImageButton = new JButton("Choose Image...");
        chooseImageButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Product Image");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Image files (JPG, JPEG, PNG, GIF)", "jpg", "jpeg", "png", "gif"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File chosen = chooser.getSelectedFile();
                try {
                    BufferedImage image = ImageIO.read(chosen);
                    if (image == null) {
                        showMessage("Please select a valid JPG, PNG, or GIF image.");
                        return;
                    }
                    selectedImage[0] = chosen;
                    imageLabel.setText(chosen.getName());
                } catch (IOException ex) {
                    showMessage("Could not read the selected image.");
                }
            }
        });

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Product name:"));
        form.add(nameField);
        form.add(new JLabel("Price (GH₵):"));
        form.add(priceField);
        form.add(new JLabel("Opening stock:"));
        form.add(stockField);
        form.add(new JLabel("Product image:"));
        JPanel imagePanel = new JPanel(new BorderLayout(5, 5));
        imagePanel.add(chooseImageButton, BorderLayout.NORTH);
        imagePanel.add(imageLabel, BorderLayout.CENTER);
        form.add(imagePanel);

        int result = JOptionPane.showConfirmDialog(
                this, form, "Add New Product",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showMessage("Enter a product name.");
            return;
        }

        long priceCents;
        int stock;
        try {
            BigDecimal price = new BigDecimal(priceField.getText().trim())
                    .setScale(2, RoundingMode.UNNECESSARY);
            priceCents = price.movePointRight(2).longValueExact();
            stock = Integer.parseInt(stockField.getText().trim());
            if (priceCents < 0 || stock < 0) {
                showMessage("Price and stock cannot be negative.");
                return;
            }
        } catch (Exception ex) {
            showMessage("Enter a valid price (e.g. 12.50) and whole-number stock.");
            return;
        }

        String savedImagePath = null;
        if (selectedImage[0] != null) {
            savedImagePath = copyProductImage(selectedImage[0]);
            if (savedImagePath == null) return;
        }

        try {
            Database.addProduct(name, priceCents, stock, savedImagePath);
            refreshProducts();
            statusLabel.setText("Product '" + name + "' added.");
            showMessage("Product added successfully.");
        } catch (SQLException ex) {
            showError("Could not add product", ex);
        }
    }

    private Database.Product getSelectedProduct() {
        int viewRow = productTable.getSelectedRow();
        if (viewRow < 0) {
            showMessage("Select a product from the product table first.");
            return null;
        }
        int row = productTable.convertRowIndexToModel(viewRow);
        int id = ((Number) productModel.getValueAt(row, 1)).intValue();
        try {
            for (Database.Product product : Database.loadProducts()) {
                if (product.id == id) return product;
            }
        } catch (SQLException ex) {
            showError("Could not load selected product", ex);
        }
        showMessage("The selected product is no longer available.");
        return null;
    }

    private void editSelectedProduct() {
        if (!currentUser.isAdmin()) {
            showMessage("Only administrators can edit products.");
            return;
        }

        Database.Product product = getSelectedProduct();
        if (product == null) return;

        JTextField nameField = new JTextField(product.name);
        JTextField priceField = new JTextField(money(product.priceCents));
        JTextField stockField = new JTextField(String.valueOf(product.stock));
        JLabel imageLabel = new JLabel(product.imagePath == null
                ? "No image selected" : Path.of(product.imagePath).getFileName().toString());
        File[] selectedImage = new File[1];
        boolean[] removeExistingImage = {false};

        JButton chooseImageButton = new JButton("Choose Image...");
        chooseImageButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Product Image");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Image files (JPG, JPEG, PNG, GIF)", "jpg", "jpeg", "png", "gif"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File chosen = chooser.getSelectedFile();
                try {
                    BufferedImage image = ImageIO.read(chosen);
                    if (image == null) {
                        showMessage("Please select a valid JPG, PNG, or GIF image.");
                        return;
                    }
                    selectedImage[0] = chosen;
                    removeExistingImage[0] = false;
                    imageLabel.setText(chosen.getName());
                } catch (IOException ex) {
                    showMessage("Could not read the selected image.");
                }
            }
        });

        JButton removeImageButton = new JButton("Remove Image");
        removeImageButton.addActionListener(e -> {
            selectedImage[0] = null;
            removeExistingImage[0] = true;
            imageLabel.setText("No image selected");
        });

        JPanel imagePanel = new JPanel(new GridLayout(0, 1, 4, 4));
        imagePanel.add(chooseImageButton);
        imagePanel.add(removeImageButton);
        imagePanel.add(imageLabel);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Product name:"));
        form.add(nameField);
        form.add(new JLabel("Price (GH₵):"));
        form.add(priceField);
        form.add(new JLabel("Stock quantity:"));
        form.add(stockField);
        form.add(new JLabel("Product image:"));
        form.add(imagePanel);

        int result = JOptionPane.showConfirmDialog(
                this, form, "Edit Product #" + product.id,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showMessage("Enter a product name.");
            return;
        }

        long priceCents;
        int stock;
        try {
            BigDecimal price = new BigDecimal(priceField.getText().trim())
                    .setScale(2, RoundingMode.UNNECESSARY);
            priceCents = price.movePointRight(2).longValueExact();
            stock = Integer.parseInt(stockField.getText().trim());
            if (priceCents < 0 || stock < 0) {
                showMessage("Price and stock cannot be negative.");
                return;
            }
        } catch (Exception ex) {
            showMessage("Enter a valid price (e.g. 12.50) and whole-number stock.");
            return;
        }

        String imagePath = removeExistingImage[0] ? null : product.imagePath;
        if (selectedImage[0] != null) {
            imagePath = copyProductImage(selectedImage[0]);
            if (imagePath == null) return;
        }

        try {
            Database.updateProduct(product.id, name, priceCents, stock, imagePath);
            refreshProducts();
            // Update cart copy so its display reflects the new name and price.
            Database.Product updated = new Database.Product(
                    product.id, name, priceCents, stock, imagePath);
            if (cart.containsKey(product)) {
                int quantity = cart.remove(product);
                if (quantity > stock) {
                    showMessage("Product updated, but its cart quantity was removed because it exceeds the new stock.");
                } else if (quantity > 0) {
                    cart.put(updated, quantity);
                }
            }
            refreshCart();
            statusLabel.setText("Product '" + name + "' updated.");
            showMessage("Product updated successfully.");
        } catch (SQLException ex) {
            showError("Could not update product", ex);
        }
    }

    private void deleteSelectedProduct() {
        if (!currentUser.isAdmin()) {
            showMessage("Only administrators can delete products.");
            return;
        }
        Database.Product product = getSelectedProduct();
        if (product == null) return;

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete '" + product.name + "' from the active product list?\n"
                        + "Past sales records will be preserved.",
                "Confirm Product Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            Database.deleteProduct(product.id);
            cart.keySet().removeIf(item -> item.id == product.id);
            refreshProducts();
            refreshCart();
            statusLabel.setText("Product '" + product.name + "' deleted.");
            showMessage("Product deleted from the active inventory. Past sales were preserved.");
        } catch (SQLException ex) {
            showError("Could not delete product", ex);
        }
    }

    private String copyProductImage(File source) {
        try {
            Path imageFolder = Path.of("product_images");
            Files.createDirectories(imageFolder);
            String originalName = source.getName();
            String extension = "";
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) extension = originalName.substring(dot).toLowerCase();
            String newFileName = UUID.randomUUID() + extension;
            Path destination = imageFolder.resolve(newFileName);
            Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toString();
        } catch (IOException ex) {
            showMessage("Could not copy the product image: " + ex.getMessage());
            return null;
        }
    }

    private void refreshProducts() {
        try {
            productModel.setRowCount(0);

            for (Database.Product p : Database.loadProducts()) {
                productModel.addRow(new Object[]{
                        loadThumbnail(p.imagePath), p.id, p.name,
                        money(p.priceCents), p.stock
                });
            }
        } catch (SQLException ex) {
            showError("Could not load products", ex);
        }
    }

    private void refreshCart() {
        cartModel.setRowCount(0);

        for (Map.Entry<Database.Product, Integer> entry
                : cart.entrySet()) {

            Database.Product p = entry.getKey();
            int quantity = entry.getValue();
            long lineTotal = p.priceCents * quantity;

            cartModel.addRow(new Object[]{
                    p.name,
                    money(p.priceCents),
                    quantity,
                    money(lineTotal)
            });
        }

        totalLabel.setText("Total: GH₵" + money(totalCents()));
    }

    private long totalCents() {
        long total = 0;

        for (Map.Entry<Database.Product, Integer> entry
                : cart.entrySet()) {
            total = Math.addExact(
                    total,
                    Math.multiplyExact(
                            entry.getKey().priceCents,
                            entry.getValue()));
        }

        return total;
    }

    private void addToCart() {
        int row = productTable.getSelectedRow();

        if (row < 0) {
            showMessage("Select a product first.");
            return;
        }

        int id = (int) productModel.getValueAt(row, 1);

        try {
            for (Database.Product p : Database.loadProducts()) {
                if (p.id == id) {
                    int quantity = cart.getOrDefault(p, 0);

                    if (p.stock <= quantity) {
                        showMessage("No more available stock for " + p.name);
                        return;
                    }

                    cart.put(p, quantity + 1);
                    refreshCart();
                    statusLabel.setText(p.name + " added to cart.");
                    return;
                }
            }
        } catch (SQLException ex) {
            showError("Could not read product stock", ex);
        }
    }

    private Database.Product selectedCartProduct() {
        int row = cartTable.getSelectedRow();

        if (row < 0) {
            showMessage("Select a cart item first.");
            return null;
        }

        String name = (String) cartModel.getValueAt(row, 0);

        for (Database.Product p : cart.keySet()) {
            if (p.name.equals(name)) {
                return p;
            }
        }

        return null;
    }

    private void changeQuantity(int amount) {
        Database.Product p = selectedCartProduct();

        if (p == null) return;

        int quantity = cart.get(p) + amount;

        if (quantity <= 0) {
            cart.remove(p);
        } else {
            try {
                int stock = -1;

                for (Database.Product current
                        : Database.loadProducts()) {
                    if (current.id == p.id) {
                        stock = current.stock;
                        break;
                    }
                }

                if (quantity > stock) {
                    showMessage("Quantity exceeds available stock.");
                    return;
                }

                cart.put(p, quantity);
            } catch (SQLException ex) {
                showError("Could not check stock", ex);
                return;
            }
        }

        refreshCart();
    }

    private void removeItem() {
        Database.Product p = selectedCartProduct();

        if (p != null) {
            cart.remove(p);
            refreshCart();
        }
    }

    private void checkout() {
        if (cart.isEmpty()) {
            showMessage("Your cart is empty.");
            return;
        }

        long total = totalCents();
        JTextField paymentField = new JTextField();

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.add(new JLabel("Total:"));
        panel.add(new JLabel("GH₵" + money(total)));
        panel.add(new JLabel("Amount paid (GH₵):"));
        panel.add(paymentField);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    this, panel, "Checkout",
                    JOptionPane.OK_CANCEL_OPTION);

            if (choice != JOptionPane.OK_OPTION) return;

            long paid;

            try {
                BigDecimal amount = new BigDecimal(
                        paymentField.getText().trim());

                paid = amount.movePointRight(2).longValueExact();

                if (paid < 0) {
                    throw new NumberFormatException();
                }
            } catch (Exception ex) {
                showMessage(
                        "Enter a valid amount, e.g. 300 or 300.50.");
                continue;
            }

            if (paid < total) {
                showMessage("Insufficient payment. Balance due: GH₵"
                        + money(total - paid));
                continue;
            }

            try {
                long saleId = Database.saveSale(
                        new LinkedHashMap<>(cart), paid);

                String receipt = buildReceipt(
                        saleId, paid, paid - total);

                cart.clear();
                refreshProducts();
                refreshCart();

                statusLabel.setText("Sale #" + saleId
                        + " saved successfully.");

                JTextArea receiptArea = new JTextArea(receipt, 20, 35);
                receiptArea.setEditable(false);
                receiptArea.setFont(
                        new Font("Monospaced", Font.PLAIN, 12));

                JOptionPane.showMessageDialog(
                        this,
                        new JScrollPane(receiptArea),
                        "Saved Sales Receipt",
                        JOptionPane.INFORMATION_MESSAGE);

                return;

            } catch (SQLException ex) {
                showError("Sale was not saved", ex);
                refreshProducts();
                return;
            } catch (ArithmeticException ex) {
                showMessage("The sale amount is too large.");
                return;
            }
        }
    }

    private String buildReceipt(
            long saleId, long paid, long change) {

        StringBuilder out = new StringBuilder();

        out.append("================================\n");
        out.append("           SHOP POS\n");
        out.append("Sale number: ").append(saleId).append("\n");
        String saleDateTime = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss"));
        out.append("Date: ").append(saleDateTime.substring(0, 10)).append("\n");
        out.append("Time: ").append(saleDateTime.substring(12)).append("\n");
        out.append("================================\n");

        long total = 0;

        for (Map.Entry<Database.Product, Integer> entry
                : cart.entrySet()) {

            Database.Product p = entry.getKey();
            int quantity = entry.getValue();
            long line = p.priceCents * quantity;
            total += line;

            out.append(String.format(
                    "%-16s %3d %10s%n",
                    p.name, quantity, money(line)));
        }

        out.append("--------------------------------\n");
        out.append("Total:  GH₵").append(money(total)).append("\n");
        out.append("Paid:   GH₵").append(money(paid)).append("\n");
        out.append("Change: GH₵").append(money(change)).append("\n");
        out.append("================================\n");
        out.append("       Thank you!\n");

        return out.toString();
    }

    private void showSalesHistory() {
        try {
            List<String> history = Database.loadSalesHistory();

            JTextArea area = new JTextArea(20, 55);
            area.setEditable(false);

            if (history.isEmpty()) {
                area.setText("No sales have been recorded yet.");
            } else {
                area.setText(String.join("\n\n", history));
            }

            JOptionPane.showMessageDialog(
                    this,
                    new JScrollPane(area),
                    "Sales History",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException ex) {
            showError("Could not load sales history", ex);
        }
    }

    private String money(long cents) {
        return String.format("%,.2f", cents / 100.0);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    private void showError(String message, Exception ex) {
        JOptionPane.showMessageDialog(
                this,
                message + ":\n" + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
    }


    public static void main(String[] args) {

        try {
            // Preserve and initialize the existing products and sales database.
            Database.initialize();

            // Create the users table if it does not exist.
            Auth.initialize();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    null,
                    "Could not initialize ShopPOS:\n" + ex.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Show setup on first run, or the login screen thereafter.
        Auth.User user = Auth.loginOrSetup();

        if (user == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            Main app = new Main(user);
            app.setVisible(true);
        });
    }
}
