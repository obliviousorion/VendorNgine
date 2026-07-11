package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.controller.StallDataSource;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Stall;
import com.festival.vendorengine.model.UserRole;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * Login window for role selection and navigation (Section 9.1).
 */
public class LoginView extends JFrame {

    private final OrderController controller;
    private final StallDataSource dataSource;
    private final AppState appState;

    private JPanel tileKitchen;
    private JPanel tileAdmin;
    private JPanel stallPanelContainer;
    private JComboBox<String> comboStall;
    private JLabel lblStall;
    private JButton btnEnter;
    private JLabel lblStatus;
    private Timer statusTimer;

    private UserRole selectedRole = null;

    /**
     * Constructs the LoginView frame.
     */
    public LoginView(OrderController controller, StallDataSource dataSource, AppState appState) {
        this.controller = controller;
        this.dataSource = dataSource;
        this.appState = appState;

        setTitle("Vendor Engine — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(440, 420);
        setLocationRelativeTo(null); // center on screen

        // Root container with dark theme styling
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(UiTheme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setContentPane(root);

        // 1. Header Title
        JLabel lblTitle = new JLabel("Festival Vendor Engine", SwingConstants.CENTER);
        lblTitle.setFont(UiTheme.TITLE);
        lblTitle.setForeground(UiTheme.ACCENT);
        root.add(lblTitle, BorderLayout.NORTH);

        // 2. Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(UiTheme.PANEL);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BG, 2),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // Role field label
        gbc.gridy = 0;
        JLabel lblRole = new JLabel("CHOOSE YOUR ROLE:");
        lblRole.setFont(UiTheme.BODY);
        lblRole.setForeground(UiTheme.FG_MUTED);
        formPanel.add(lblRole, gbc);

        // Role field tiles grid
        gbc.gridy = 1;
        JPanel roleGrid = new JPanel(new GridLayout(1, 2, 12, 0));
        roleGrid.setBackground(UiTheme.PANEL);
        tileKitchen = createRoleTile("Kitchen Worker", "Run one stall's live queue.", UserRole.KITCHEN_WORKER);
        tileAdmin = createRoleTile("Merchant Admin", "Oversee every stall at once.", UserRole.MERCHANT_ADMIN);
        roleGrid.add(tileKitchen);
        roleGrid.add(tileAdmin);
        formPanel.add(roleGrid, gbc);

        // Stall Picker Panel (contains label and combobox so it hides cleanly as a unit)
        stallPanelContainer = new JPanel(new GridBagLayout());
        stallPanelContainer.setBackground(UiTheme.PANEL);
        GridBagConstraints sGbc = new GridBagConstraints();
        sGbc.fill = GridBagConstraints.HORIZONTAL;
        sGbc.weightx = 1.0;
        sGbc.gridx = 0;

        sGbc.gridy = 0;
        lblStall = new JLabel("YOUR STALL:");
        lblStall.setFont(UiTheme.BODY);
        lblStall.setForeground(UiTheme.FG_MUTED);
        stallPanelContainer.add(lblStall, sGbc);

        sGbc.gridy = 1;
        sGbc.insets = new Insets(4, 0, 0, 0);
        comboStall = new JComboBox<>();
        comboStall.setFont(UiTheme.BODY);
        comboStall.setBackground(UiTheme.BG);
        comboStall.setForeground(UiTheme.FG);
        populateStalls();
        comboStall.addActionListener(e -> updateEnterButtonState());
        stallPanelContainer.add(comboStall, sGbc);

        gbc.gridy = 2;
        formPanel.add(stallPanelContainer, gbc);

        root.add(formPanel, BorderLayout.CENTER);

        // 3. Actions Panel
        JPanel actionPanel = new JPanel(new BorderLayout(5, 8));
        actionPanel.setBackground(UiTheme.BG);

        btnEnter = new JButton("Enter Dashboard");
        UiTheme.styleButton(btnEnter);
        btnEnter.setPreferredSize(new Dimension(-1, 40));
        actionPanel.add(btnEnter, BorderLayout.CENTER);

        lblStatus = new JLabel("● Connection status...", SwingConstants.CENTER);
        lblStatus.setFont(UiTheme.BODY);
        lblStatus.setForeground(UiTheme.FG_MUTED);
        actionPanel.add(lblStatus, BorderLayout.SOUTH);

        root.add(actionPanel, BorderLayout.SOUTH);

        // 4. Interaction Triggers
        btnEnter.addActionListener(e -> handleLogin());

        // 5. Polling Timer for Network status
        statusTimer = new Timer(500, e -> updateNetworkStatus());
        statusTimer.start();
        updateNetworkStatus();

        // 6. Cleanup listener on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });

        // Initialize state (nothing selected by default)
        selectRole(null);
    }

    private JPanel createRoleTile(String name, String desc, UserRole role) {
        JPanel tile = new JPanel();
        tile.setLayout(new javax.swing.BoxLayout(tile, javax.swing.BoxLayout.Y_AXIS));
        tile.setBackground(UiTheme.PANEL);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BG, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(UiTheme.SUBTITLE);
        nameLabel.setForeground(UiTheme.FG);
        nameLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel("<html><body style='width: 120px;'>" + desc + "</body></html>");
        descLabel.setFont(UiTheme.BODY);
        descLabel.setForeground(UiTheme.FG_MUTED);
        descLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        tile.add(nameLabel);
        tile.add(javax.swing.Box.createRigidArea(new Dimension(0, 6)));
        tile.add(descLabel);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectRole(role);
            }
        });

        return tile;
    }

    private void selectRole(UserRole role) {
        this.selectedRole = role;

        if (role == UserRole.KITCHEN_WORKER) {
            tileKitchen.setBackground(new Color(0x352818)); // subtle marigold wash
            tileKitchen.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.ACCENT, 2),
                    BorderFactory.createEmptyBorder(11, 11, 11, 11) // border expansion adjustment
            ));

            tileAdmin.setBackground(UiTheme.PANEL);
            tileAdmin.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.BG, 1),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));

            populateStalls(); // refresh: picks up any stalls added by the simulator since startup
            stallPanelContainer.setVisible(true);
        } else if (role == UserRole.MERCHANT_ADMIN) {
            tileKitchen.setBackground(UiTheme.PANEL);
            tileKitchen.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.BG, 1),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));

            tileAdmin.setBackground(new Color(0x352818)); // subtle marigold wash
            tileAdmin.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.ACCENT, 2),
                    BorderFactory.createEmptyBorder(11, 11, 11, 11)
            ));

            stallPanelContainer.setVisible(false);
        } else {
            tileKitchen.setBackground(UiTheme.PANEL);
            tileKitchen.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.BG, 1),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));

            tileAdmin.setBackground(UiTheme.PANEL);
            tileAdmin.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiTheme.BG, 1),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));

            stallPanelContainer.setVisible(false);
        }

        getContentPane().revalidate();
        getContentPane().repaint();
        updateEnterButtonState();
    }

    private void updateEnterButtonState() {
        if (selectedRole == null) {
            btnEnter.setEnabled(false);
            return;
        }
        if (selectedRole == UserRole.KITCHEN_WORKER) {
            btnEnter.setEnabled(comboStall.getSelectedItem() != null);
        } else {
            btnEnter.setEnabled(true);
        }
    }

    private void populateStalls() {
        List<String> stallIds = dataSource.getAllStallIds();
        comboStall.removeAllItems();
        for (String id : stallIds) {
            Stall stall = dataSource.getStall(id);
            if (stall != null) {
                comboStall.addItem(stall.getStallName() + " (" + id + ")");
            } else {
                comboStall.addItem(id);
            }
        }
    }

    private void updateNetworkStatus() {
        boolean online = appState.isOnline();
        if (online) {
            lblStatus.setText("● All stalls online · synced");
            lblStatus.setForeground(UiTheme.OK);
        } else {
            lblStatus.setText("● System offline · caching locally");
            lblStatus.setForeground(UiTheme.HOT);
        }
    }

    private void cleanup() {
        if (statusTimer != null && statusTimer.isRunning()) {
            statusTimer.stop();
        }
    }

    private void handleLogin() {
        if (selectedRole == null) {
            JOptionPane.showMessageDialog(this, "Please select a user role.", "Login Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        cleanup();

        if (selectedRole == UserRole.KITCHEN_WORKER) {
            String selectedItem = (String) comboStall.getSelectedItem();
            if (selectedItem == null) {
                JOptionPane.showMessageDialog(this, "No stalls are registered in the system.", "Login Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Extract stall ID from format: "Stall Name (Sxx)"
            String selectedStallId = null;
            int start = selectedItem.lastIndexOf('(');
            int end = selectedItem.lastIndexOf(')');
            if (start >= 0 && end > start) {
                selectedStallId = selectedItem.substring(start + 1, end).trim();
            } else {
                selectedStallId = selectedItem.trim();
            }

            Stall stall = dataSource.getStall(selectedStallId);
            if (stall == null) {
                JOptionPane.showMessageDialog(this, "Selected stall does not exist.", "Login Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Launch KitchenView
            KitchenView kitchenView = new KitchenView(stall, controller, dataSource, appState);
            kitchenView.setVisible(true);
        } else {
            // MERCHANT_ADMIN
            // Launch AdminView
            AdminView adminView = new AdminView(controller, dataSource, appState);
            adminView.setVisible(true);
        }

        // Close/dispose this login frame
        dispose();
    }
}
