package com.festival.vendorengine.view;

import java.awt.Color;
import java.awt.Font;
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
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to cross-platform default
        }
        UIManager.put("control", BG);
        UIManager.put("info", PANEL);
        UIManager.put("nimbusBase", ACCENT);
        UIManager.put("text", FG);
    }
}
