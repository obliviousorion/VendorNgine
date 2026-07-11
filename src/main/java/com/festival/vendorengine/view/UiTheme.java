package com.festival.vendorengine.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;

/**
 * Aesthetic styling definitions for the Swing GUI (Section 9.4).
 * Reuses curated, dark colors and modern typography to design a premium UI.
 */
public final class UiTheme {

    // Palette colors (vibrant, sleek, dark mode style)
    public static final Color BG = new Color(0x1E1E2E);
    public static final Color PANEL = new Color(0x2A2A3D);
    public static final Color ACCENT = new Color(0xF5A623);   // Priority/VIP highlight (amber/orange)
    public static final Color OK = new Color(0x4CAF50);       // Success status (green)
    public static final Color WARN = new Color(0xE94E4E);     // High latency warning / network offline (red)
    public static final Color FG = Color.WHITE;               // Primary text (white)
    public static final Color FG_MUTED = new Color(0xA5A5B5); // Muted secondary text

    // Typography
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BUTTON = new Font("Segoe UI", Font.BOLD, 13);

    private UiTheme() {
        // Prevent instantiation
    }

    /**
     * Applies UI manager overrides for custom look-and-feel look before
     * any Swing window frames are constructed.
     */
    public static void apply() {
        try {
            // Use the cross-platform vector-based Nimbus L&F
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored2) {}
        }

        // Apply dark mode overrides to the Nimbus look-and-feel keys
        UIManager.put("control", BG);
        UIManager.put("info", PANEL);
        UIManager.put("nimbusBase", ACCENT);
        UIManager.put("nimbusBlueGrey", PANEL);
        UIManager.put("nimbusLightBackground", PANEL);
        UIManager.put("text", FG);
        UIManager.put("nimbusDisabledText", FG_MUTED);
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("nimbusSelectedText", FG);
        UIManager.put("nimbusSelectionBackground", ACCENT.darker().darker());

        // Component Specific defaults
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.alternateRowColor", new Color(0x232335));
        UIManager.put("Table.gridColor", BG);
        UIManager.put("Table.textForeground", FG);
        UIManager.put("TableHeader.background", PANEL);
        UIManager.put("TableHeader.foreground", FG);
        UIManager.put("List.background", PANEL);
        UIManager.put("List.foreground", FG);
        UIManager.put("List.selectionBackground", ACCENT.darker().darker());
        UIManager.put("List.selectionForeground", FG);
        UIManager.put("ComboBox.background", PANEL);
        UIManager.put("ComboBox.foreground", FG);
        UIManager.put("ComboBox.selectionBackground", ACCENT.darker().darker());
        UIManager.put("ComboBox.selectionForeground", FG);
        UIManager.put("Button.background", PANEL);
        UIManager.put("Button.foreground", FG);
        UIManager.put("Label.foreground", FG);
        UIManager.put("Panel.background", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Viewport.background", BG);
    }

    /**
     * Styles a JButton with custom theme properties and mouse hover transitions.
     *
     * @param btn the button to style
     */
    public static void styleButton(JButton btn) {
        btn.setFont(BUTTON);
        btn.setBackground(PANEL);
        btn.setForeground(FG);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(ACCENT);
                    btn.setForeground(BG);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(PANEL);
                    btn.setForeground(FG);
                }
            }
        });
    }
}
