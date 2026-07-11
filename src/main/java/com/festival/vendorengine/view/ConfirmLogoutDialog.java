package com.festival.vendorengine.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Themed, undecorated modal confirmation dialog used by both
 * {@link KitchenView} and {@link AdminView} for the logout action.
 *
 * <p>Replaces the native {@code JOptionPane.showConfirmDialog} so the dialog
 * no longer shows light-grey OS chrome against the dark application.
 *
 * <h2>Usage</h2>
 * <pre>
 *     if (ConfirmLogoutDialog.confirm(this)) {
 *         cleanup();
 *         dispose();
 *         new LoginView(...).setVisible(true);
 *     }
 * </pre>
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>{@code setUndecorated(true)} — no OS title bar.</li>
 *   <li>Custom draggable title bar to compensate.</li>
 *   <li>Two buttons: "Yes, log out" (HOT-tinted destructive action) and
 *       "Stay" (normal accent border).</li>
 * </ul>
 */
public class ConfirmLogoutDialog extends JDialog {

    /** True if the user clicked "Yes, log out"; false for "Stay" or close. */
    private boolean confirmed = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Static factory — the only public entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Displays a modal themed confirmation dialog centered over {@code owner}.
     *
     * @param owner the parent frame (used for centering and modality)
     * @return {@code true} if the user chose "Yes, log out"; {@code false} otherwise
     */
    public static boolean confirm(Frame owner) {
        ConfirmLogoutDialog dlg = new ConfirmLogoutDialog(owner);
        dlg.setVisible(true);   // blocks (modal)
        return dlg.confirmed;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    private ConfirmLogoutDialog(Frame owner) {
        super(owner, Dialog.ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setSize(400, 210);
        setLocationRelativeTo(owner);

        // ── Root ─────────────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.PANEL);
        root.setBorder(BorderFactory.createLineBorder(UiTheme.ACCENT, 1));
        setContentPane(root);

        // ── Custom title bar ─────────────────────────────────────────────────
        JPanel titleBar = buildTitleBar();
        root.add(titleBar, BorderLayout.NORTH);
        makeDraggable(titleBar);

        // ── Body ─────────────────────────────────────────────────────────────
        JPanel body = buildBody();
        root.add(body, BorderLayout.CENTER);

        // ── Footer with buttons ───────────────────────────────────────────────
        JPanel footer = buildFooter();
        root.add(footer, BorderLayout.SOUTH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Panel builders
    // ─────────────────────────────────────────────────────────────────────────

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UiTheme.BG);
        bar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));

        JLabel lblTitle = new JLabel("Confirm Logout");
        lblTitle.setFont(UiTheme.SUBTITLE);
        lblTitle.setForeground(UiTheme.FG);
        bar.add(lblTitle, BorderLayout.WEST);

        // Close button (dismiss = "Stay")
        JButton btnClose = new JButton("✕");
        btnClose.setFont(UiTheme.BODY);
        btnClose.setForeground(UiTheme.FG_MUTED);
        btnClose.setBackground(UiTheme.BG);
        btnClose.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        btnClose.setFocusPainted(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btnClose.setForeground(UiTheme.HOT); }
            @Override public void mouseExited(MouseEvent e)  { btnClose.setForeground(UiTheme.FG_MUTED); }
        });
        btnClose.addActionListener(e -> dispose());
        bar.add(btnClose, BorderLayout.EAST);

        return bar;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setBackground(UiTheme.PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));

        // Warning icon
        JLabel icon = new JLabel("⚠", SwingConstants.CENTER);
        icon.setFont(UiTheme.TITLE.deriveFont(32f));
        icon.setForeground(UiTheme.WARN);
        icon.setPreferredSize(new Dimension(48, 48));
        body.add(icon, BorderLayout.WEST);

        // Message stack
        JPanel msgStack = new JPanel(new BorderLayout(0, 6));
        msgStack.setOpaque(false);

        JLabel lblHead = new JLabel("Log out of Vendor Engine?");
        lblHead.setFont(UiTheme.SUBTITLE);
        lblHead.setForeground(UiTheme.FG);

        JLabel lblSub = new JLabel("Any unsynced offline orders will stay queued.");
        lblSub.setFont(UiTheme.BODY);
        lblSub.setForeground(UiTheme.FG_MUTED);

        msgStack.add(lblHead, BorderLayout.NORTH);
        msgStack.add(lblSub,  BorderLayout.CENTER);
        body.add(msgStack, BorderLayout.CENTER);

        return body;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(UiTheme.PANEL);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BG),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        // "Stay" — normal accent button
        JButton btnStay = new JButton("Stay");
        UiTheme.styleButton(btnStay);
        btnStay.addActionListener(e -> dispose());   // confirmed stays false

        // "Yes, log out" — destructive: HOT-colored border
        JButton btnLogout = new JButton("Yes, log out");
        btnLogout.setFont(UiTheme.BUTTON);
        btnLogout.setBackground(UiTheme.PANEL);
        btnLogout.setForeground(UiTheme.HOT);
        btnLogout.setFocusPainted(false);
        btnLogout.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.HOT, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnLogout.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnLogout.setBackground(UiTheme.HOT);
                btnLogout.setForeground(UiTheme.FG);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btnLogout.setBackground(UiTheme.PANEL);
                btnLogout.setForeground(UiTheme.HOT);
            }
        });
        btnLogout.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        footer.add(btnStay);
        footer.add(btnLogout);
        return footer;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draggable title bar
    // ─────────────────────────────────────────────────────────────────────────

    /** Attaches mouse listeners to {@code handle} so the dialog can be dragged. */
    private void makeDraggable(JPanel handle) {
        final Point[] dragOrigin = {null};

        handle.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOrigin[0] = e.getPoint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragOrigin[0] = null;
            }
        });

        handle.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin[0] == null) return;
                Point loc = getLocation();
                setLocation(
                        loc.x + e.getX() - dragOrigin[0].x,
                        loc.y + e.getY() - dragOrigin[0].y);
            }
        });
    }
}
