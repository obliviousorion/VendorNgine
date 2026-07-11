package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderController;
import com.festival.vendorengine.controller.StallDataSource;
import com.festival.vendorengine.model.AppState;
import com.festival.vendorengine.model.Stall;
import com.festival.vendorengine.model.UserRole;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Login window for role selection and navigation (Section 9.1).
 */
public class LoginView extends JFrame {

    private final OrderController controller;
    private final StallDataSource dataSource;
    private final AppState appState;

    private JComboBox<UserRole> comboRole;
    private JComboBox<String> comboStall;
    private JLabel lblStall;
    private JButton btnEnter;

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
        setSize(400, 280);
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

        // Role field label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel lblRole = new JLabel("Role:");
        lblRole.setFont(UiTheme.BODY);
        lblRole.setForeground(UiTheme.FG);
        formPanel.add(lblRole, gbc);

        // Role field combo
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.7;
        comboRole = new JComboBox<>(UserRole.values());
        comboRole.setFont(UiTheme.BODY);
        comboRole.setBackground(UiTheme.BG);
        comboRole.setForeground(UiTheme.FG);
        formPanel.add(comboRole, gbc);

        // Stall field label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        lblStall = new JLabel("Stall:");
        lblStall.setFont(UiTheme.BODY);
        lblStall.setForeground(UiTheme.FG);
        formPanel.add(lblStall, gbc);

        // Stall field combo
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0.7;
        comboStall = new JComboBox<>();
        comboStall.setFont(UiTheme.BODY);
        comboStall.setBackground(UiTheme.BG);
        comboStall.setForeground(UiTheme.FG);
        populateStalls();
        formPanel.add(comboStall, gbc);

        root.add(formPanel, BorderLayout.CENTER);

        // 3. Actions Panel
        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.setBackground(UiTheme.BG);

        btnEnter = new JButton("Enter Dashboard");
        btnEnter.setFont(UiTheme.BUTTON);
        btnEnter.setPreferredSize(new Dimension(-1, 40));
        btnEnter.setBackground(UiTheme.PANEL);
        btnEnter.setForeground(UiTheme.FG);
        btnEnter.setBorder(BorderFactory.createLineBorder(UiTheme.ACCENT, 1));
        btnEnter.setFocusPainted(false);
        actionPanel.add(btnEnter, BorderLayout.CENTER);

        root.add(actionPanel, BorderLayout.SOUTH);

        // 4. Interaction Triggers
        comboRole.addActionListener(e -> handleRoleChanged());
        btnEnter.addActionListener(e -> handleLogin());

        // Initialize state
        handleRoleChanged();
    }

    private void populateStalls() {
        List<String> stallIds = dataSource.getAllStallIds();
        comboStall.removeAllItems();
        for (String id : stallIds) {
            comboStall.addItem(id);
        }
    }

    private void handleRoleChanged() {
        UserRole role = (UserRole) comboRole.getSelectedItem();
        boolean isKitchen = (role == UserRole.KITCHEN_WORKER);
        lblStall.setEnabled(isKitchen);
        comboStall.setEnabled(isKitchen);
    }

    private void handleLogin() {
        UserRole role = (UserRole) comboRole.getSelectedItem();
        if (role == null) {
            JOptionPane.showMessageDialog(this, "Please select a user role.", "Login Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (role == UserRole.KITCHEN_WORKER) {
            String selectedStallId = (String) comboStall.getSelectedItem();
            if (selectedStallId == null) {
                JOptionPane.showMessageDialog(this, "No stalls are registered in the system.", "Login Error", JOptionPane.ERROR_MESSAGE);
                return;
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
