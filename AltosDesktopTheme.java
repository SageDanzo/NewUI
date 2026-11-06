/*
 * Copyright © 2026
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 */

package altosui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JSlider;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.AbstractButton;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;

/**
 * The Windows desktop visual language for AltOS.
 *
 * This class intentionally lives in altosui rather than the firmware or
 * device libraries.  It keeps the visual redesign scoped to the desktop
 * application while all telemetry and hardware behavior remains unchanged.
 */
public final class AltosDesktopTheme {
	private static double scale = 1.0;

	public static int dp(int pixels) {
		return pixels == 0 ? 0 : Math.max(1, (int) Math.round(pixels * scale));
	}

	public static Font font(int style, int points) {
		return new Font("Segoe UI", style, dp(Math.max(points, 11)));
	}

	public static double scale() {
		return scale;
	}

	/** Let original AltOS dialogs use the native palette when Windows is selected. */
	public static void respectLookAndFeel() {
		if (UIManager.getLookAndFeel() == null) return;
		if (UIManager.getLookAndFeel().getClass().getName().contains("Nimbus")) {
			applyPalette();
			return;
		}
		String[] nativeColors = {
			"control", "info", "text", "controlText", "textText",
			"TextField.background", "TextField.foreground", "TextField.caretForeground",
			"TextField.selectionBackground", "TextArea.background", "TextArea.foreground",
			"FormattedTextField.background", "FormattedTextField.foreground",
			"PasswordField.background", "PasswordField.foreground",
			"ComboBox.background", "ComboBox.foreground", "ComboBox.selectionBackground",
			"ComboBox.selectionForeground", "Table.background", "Table.alternateRowColor",
			"List.background", "List.foreground", "List.selectionBackground",
			"List.selectionForeground", "Panel.background", "OptionPane.background",
			"OptionPane.messageForeground", "FileChooser.background", "FileChooser.foreground",
			"Viewport.background", "Menu.background", "Menu.foreground",
			"MenuItem.background", "MenuItem.foreground", "PopupMenu.background",
			"ToolTip.background", "ToolTip.foreground", "TabbedPane.contentAreaColor",
			"ScrollPane.background", "Button.background", "Button.foreground",
			"Label.foreground", "TextField.border", "FormattedTextField.border",
			"PasswordField.border"
		};
		for (String key : nativeColors) UIManager.put(key, null);
	}

	public static final Color BACKGROUND = new Color(0x0B1118);
	public static final Color SURFACE = new Color(0x121A24);
	public static final Color SURFACE_DARK = new Color(0x1A2530);
	public static final Color TOPBAR = new Color(0x0A1016);
	public static final Color SIDEBAR = new Color(0x111923);
	public static final Color SIDEBAR_TEXT = new Color(0xD4DEE8);
	public static final Color SIDEBAR_MUTED = new Color(0x9CACBD);
	public static final Color TEXT = new Color(0xFFFFFF);
	public static final Color MUTED_TEXT = new Color(0xA9BAC9);
	public static final Color ICON = new Color(0x718498);
	public static final Color BORDER = new Color(0x324253);
	public static final Color ACCENT = new Color(0x2F7FEA);
	public static final Color ACCENT_LIGHT = new Color(0x3A8DFF);
	public static final Color ACCENT_DARK = new Color(0x1B5DB5);
	public static final Color SUCCESS = new Color(0x16C784);
	public static final Color WARNING = new Color(0xFF8A00);
	public static final Color ORANGE = new Color(0xFF8A00);
	public static final Color PURPLE = new Color(0x5D35E8);
	public static final Color DANGER = new Color(0xF05252);
	public static final Color TABLE = new Color(0x0F171F);
	public static final Color TABLE_ALT = new Color(0x16222C);

	private AltosDesktopTheme() {
	}

	/** These controls must keep their paint delegates when Preferences changes the Look & Feel. */
	public static JButton button(String title) {
		return new ThemeButton(title);
	}

	public static <T> JComboBox<T> combo() { return new ThemeCombo<T>(); }
	public static <T> JComboBox<T> combo(T[] items) { return new ThemeCombo<T>(items); }
	public static JTextField field() { return new ThemeField(); }
	public static JSlider slider() { return new ThemeSlider(); }
	public static JScrollPane scroll(Component content) { return new ThemeScrollPane(content); }

	public static void styleField(JTextField field) {
		field.setFont(font(Font.PLAIN, 13));
		field.setForeground(TEXT);
		field.setBackground(SURFACE_DARK);
		field.setCaretColor(TEXT);
		field.setSelectionColor(ACCENT_DARK);
		field.setSelectedTextColor(TEXT);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(dp(8), dp(11), dp(8), dp(11))));
	}

	public static JTabbedPane tabs() {
		return new ThemeTabs();
	}

	private static final class ThemeButton extends JButton {
		ThemeButton(String title) { super(title); }

		@Override public void updateUI() {
			setUI(new DesktopButtonUI());
			setOpaque(false);
			setContentAreaFilled(false);
			setRolloverEnabled(true);
		}
	}

	private static final class DesktopButtonUI extends BasicButtonUI {
		@Override public void update(Graphics graphics, JComponent component) {
			AbstractButton button = (AbstractButton) component;
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color fill = button.getBackground();
			if (!button.isEnabled()) fill = SURFACE_DARK;
			else if (button.getModel().isPressed()) fill = fill.darker();
			else if (button.getModel().isRollover()) fill = fill.brighter();
			int inset = dp(1);
			int radius = dp(8);
			if (Boolean.TRUE.equals(button.getClientProperty("altos.theme.flat"))) {
				/* Opaque sidebar rows must repaint every pixel. An inset rounded
				 * fill leaves stale native-LAF border fragments at the edges. */
				g.setColor(SIDEBAR);
				g.fillRect(0, 0, button.getWidth(), button.getHeight());
				g.setColor(fill);
				g.fillRoundRect(0, 0, button.getWidth(), button.getHeight(), radius, radius);
				g.dispose();
				paint(graphics, component);
				return;
			}
			g.setColor(fill);
			g.fillRoundRect(inset, inset, Math.max(0, button.getWidth() - inset * 2),
				Math.max(0, button.getHeight() - inset * 2), radius, radius);
			if (!Boolean.TRUE.equals(button.getClientProperty("altos.theme.flat"))) {
				g.setColor(button.isFocusOwner() ? ACCENT_LIGHT :
					button.isEnabled() && fill.equals(ACCENT) ? ACCENT : BORDER);
				g.drawRoundRect(inset, inset, Math.max(0, button.getWidth() - inset * 2 - 1),
					Math.max(0, button.getHeight() - inset * 2 - 1), radius, radius);
			}
			g.dispose();
			paint(graphics, component);
		}

		@Override protected void paintText(Graphics g, AbstractButton button,
			Rectangle textRect, String text) {
			g.setFont(button.getFont());
			g.setColor(button.isEnabled() ? button.getForeground() : MUTED_TEXT);
			BasicGraphicsUtils.drawStringUnderlineCharAt(g, text,
				button.getDisplayedMnemonicIndex(), textRect.x, textRect.y + g.getFontMetrics().getAscent());
		}
	}

	private static final class ThemeField extends JTextField {
		@Override public void updateUI() { setUI(new BasicTextFieldUI()); }
	}

	private static final class ThemeCombo<T> extends JComboBox<T> {
		ThemeCombo() { super(); setRenderer(renderer()); }
		ThemeCombo(T[] items) { super(items); setRenderer(renderer()); }
		@Override public void updateUI() {
			setUI(new BasicComboBoxUI());
			setBackground(SURFACE_DARK);
			setForeground(TEXT);
		}
		@Override public void paint(Graphics graphics) {
			super.paint(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int width = getWidth(), height = getHeight(), radius = dp(7);
			g.setColor(SURFACE_DARK);
			g.fillRoundRect(0, 0, width - 1, height - 1, radius, radius);
			g.setColor(isFocusOwner() ? ACCENT_LIGHT : BORDER);
			g.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
			g.setColor(isEnabled() ? TEXT : MUTED_TEXT);
			g.setFont(getFont());
			Object selection = getSelectedItem();
			String selected = selection == null ? "" : selection.toString();
			int available = Math.max(0, width - dp(48));
			while (!selected.isEmpty() && g.getFontMetrics().stringWidth(selected) > available)
				selected = selected.substring(0, selected.length() - 1);
			if (selection != null && !selected.equals(selection.toString()) && selected.length() > 1)
				selected = selected.substring(0, selected.length() - 1) + "…";
			g.drawString(selected, dp(12), (height - g.getFontMetrics().getHeight()) / 2 +
				g.getFontMetrics().getAscent());
			int arrowX = width - dp(19), arrowY = height / 2;
			g.drawLine(arrowX - dp(4), arrowY - dp(2), arrowX, arrowY + dp(2));
			g.drawLine(arrowX, arrowY + dp(2), arrowX + dp(4), arrowY - dp(2));
			g.dispose();
		}
		private DefaultListCellRenderer renderer() {
			return new DefaultListCellRenderer() {
				@Override public java.awt.Component getListCellRendererComponent(JList<?> list,
					Object value, int index, boolean selected, boolean focused) {
					super.getListCellRendererComponent(list, value, index, selected, focused);
					setBackground(selected ? ACCENT_DARK : SURFACE_DARK);
					setForeground(TEXT);
					setBorder(BorderFactory.createEmptyBorder(dp(7), dp(10), dp(7), dp(10)));
					return this;
				}
			};
		}
	}

	private static final class ThemeSlider extends JSlider {
		@Override public void updateUI() {
			setUI(new BasicSliderUI(this));
			setOpaque(false);
		}
		@Override public void paint(Graphics graphics) {
			super.paint(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int width = getWidth(), height = getHeight();
			g.setColor(BACKGROUND);
			g.fillRect(0, 0, width, height);
			int start = dp(12), end = Math.max(start + 1, width - dp(12));
			int y = height / 2;
			g.setColor(BORDER);
			g.fillRoundRect(start, y - dp(3), end - start, dp(6), dp(6), dp(6));
			int range = getMaximum() - getMinimum();
			int thumb = range == 0 ? start : start +
				(int)((end - start) * ((getValue() - getMinimum()) / (double)range));
			g.setColor(ACCENT);
			g.fillRoundRect(start, y - dp(3), Math.max(0, thumb - start), dp(6), dp(6), dp(6));
			g.fillOval(thumb - dp(7), y - dp(7), dp(14), dp(14));
			g.dispose();
		}
	}

	private static final class ThemeScrollPane extends JScrollPane {
		ThemeScrollPane(Component content) {
			super(content);
			setBorder(BorderFactory.createEmptyBorder());
			setViewportBorder(BorderFactory.createEmptyBorder());
		}
		@Override public JScrollBar createVerticalScrollBar() { return new ThemeScrollBar(JScrollBar.VERTICAL); }
		@Override public JScrollBar createHorizontalScrollBar() { return new ThemeScrollBar(JScrollBar.HORIZONTAL); }
	}

	private static final class ThemeScrollBar extends JScrollBar {
		ThemeScrollBar(int orientation) {
			super(orientation);
			setPreferredSize(orientation == JScrollBar.VERTICAL
				? new Dimension(dp(12), dp(80)) : new Dimension(dp(80), dp(12)));
		}
		@Override public void updateUI() { setUI(new DesktopScrollBarUI()); }
	}

	private static final class DesktopScrollBarUI extends BasicScrollBarUI {
		@Override protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
			graphics.setColor(SURFACE);
			graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}
		@Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
			if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(0x61798F));
			g.fillRoundRect(bounds.x + dp(2), bounds.y + dp(2),
				Math.max(0, bounds.width - dp(4)), Math.max(0, bounds.height - dp(4)),
				dp(8), dp(8));
			g.dispose();
		}
		@Override protected JButton createDecreaseButton(int orientation) { return arrow(orientation); }
		@Override protected JButton createIncreaseButton(int orientation) { return arrow(orientation); }
		private JButton arrow(int direction) {
			JButton button = button(direction == javax.swing.SwingConstants.NORTH ? "⌃" :
				direction == javax.swing.SwingConstants.SOUTH ? "⌄" :
				direction == javax.swing.SwingConstants.WEST ? "‹" : "›");
			styleSecondary(button);
			button.setBorder(BorderFactory.createEmptyBorder());
			button.setFont(font(Font.PLAIN, 11));
			button.setPreferredSize(new Dimension(dp(12), dp(12)));
			return button;
		}
	}

	private static final class ThemeTabs extends JTabbedPane {
		@Override public void updateUI() {
			setUI(new BasicTabbedPaneUI() {
				@Override protected void paintTabBackground(Graphics g, int placement, int index,
					int x, int y, int width, int height, boolean selected) {
					g.setColor(selected ? ACCENT : SURFACE);
					g.fillRect(x, y, width, height);
				}
				@Override protected void paintTabBorder(Graphics g, int placement, int index,
					int x, int y, int width, int height, boolean selected) {
					g.setColor(BORDER);
					g.drawRect(x, y, width - 1, height - 1);
				}
				@Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
					g.setColor(BORDER);
					int top = calculateTabAreaHeight(placement, runCount, maxTabHeight);
					g.drawRect(0, top, tabPane.getWidth() - 1, tabPane.getHeight() - top - 1);
				}
			});
			setBackground(SURFACE);
			setForeground(TEXT);
		}
	}

	public static void install() {
		if (!GraphicsEnvironment.isHeadless()) {
			java.awt.Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
			scale = Math.max(1.0, Math.min(1.5, Math.min(work.width / 1440.0, work.height / 900.0)));
		}
		String override = System.getProperty("altos.ui.scale");
		if (override != null) {
			try {
				double requested = Double.parseDouble(override);
				if (!Double.isNaN(requested) && !Double.isInfinite(requested))
					scale = Math.max(0.9, Math.min(1.75, requested));
			} catch (NumberFormatException ignored) {
				/* Ignore malformed optional scaling overrides. */
			}
		}
		try {
			UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception ignored) {
			/* Keep the platform look and feel if Nimbus is unavailable. */
		}

		applyPalette();
		org.altusmetrum.altosuilib_14.AltosUILib.set_fonts(
			org.altusmetrum.altosuilib_14.AltosUILib.font_size_medium);
	}

	private static void applyPalette() {
		Font body = font(Font.PLAIN, 13);
		Font heading = font(Font.BOLD, 13);

		UIManager.put("defaultFont", body);
		UIManager.put("Label.font", body);
		UIManager.put("Button.font", heading);
		UIManager.put("TextField.font", body);
		UIManager.put("ComboBox.font", body);
		UIManager.put("Table.font", body);
		UIManager.put("TableHeader.font", heading);
		UIManager.put("control", SURFACE_DARK);
		UIManager.put("info", SURFACE);
		UIManager.put("text", TEXT);
		UIManager.put("nimbusBase", ACCENT_DARK);
		UIManager.put("nimbusBlueGrey", new Color(0x83919C));
		UIManager.put("nimbusLightBackground", SURFACE);
		UIManager.put("nimbusFocus", ACCENT);
		UIManager.put("nimbusSelectionBackground", ACCENT_DARK);
		UIManager.put("nimbusSelectedText", TEXT);
		UIManager.put("nimbusDisabledText", MUTED_TEXT);
		UIManager.put("controlText", TEXT);
		UIManager.put("textText", TEXT);
		UIManager.put("TextField.background", SURFACE_DARK);
		UIManager.put("TextField.foreground", TEXT);
		UIManager.put("TextField.caretForeground", TEXT);
		UIManager.put("TextField.selectionBackground", ACCENT_DARK);
		UIManager.put("TextArea.background", SURFACE_DARK);
		UIManager.put("TextArea.foreground", TEXT);
		UIManager.put("FormattedTextField.background", SURFACE_DARK);
		UIManager.put("FormattedTextField.foreground", TEXT);
		UIManager.put("PasswordField.background", SURFACE_DARK);
		UIManager.put("PasswordField.foreground", TEXT);
		UIManager.put("ComboBox.background", SURFACE_DARK);
		UIManager.put("ComboBox.foreground", TEXT);
		UIManager.put("ComboBox.selectionBackground", ACCENT_DARK);
		UIManager.put("ComboBox.selectionForeground", TEXT);
		UIManager.put("Table.background", TABLE);
		UIManager.put("Table.alternateRowColor", TABLE_ALT);
		UIManager.put("List.background", SURFACE_DARK);
		UIManager.put("List.foreground", TEXT);
		UIManager.put("List.selectionBackground", ACCENT_DARK);
		UIManager.put("List.selectionForeground", TEXT);
		UIManager.put("Panel.background", BACKGROUND);
		UIManager.put("OptionPane.background", BACKGROUND);
		UIManager.put("OptionPane.messageForeground", TEXT);
		UIManager.put("OptionPane.font", body);
		UIManager.put("OptionPane.buttonFont", heading);
		UIManager.put("FileChooser.background", BACKGROUND);
		UIManager.put("FileChooser.foreground", TEXT);
		UIManager.put("Viewport.background", BACKGROUND);
		UIManager.put("Menu.background", SURFACE);
		UIManager.put("Menu.foreground", TEXT);
		UIManager.put("MenuItem.background", SURFACE);
		UIManager.put("MenuItem.foreground", TEXT);
		UIManager.put("PopupMenu.background", SURFACE);
		UIManager.put("ToolTip.background", SURFACE_DARK);
		UIManager.put("ToolTip.foreground", TEXT);
		UIManager.put("TabbedPane.contentAreaColor", SURFACE_DARK);
		UIManager.put("ScrollPane.background", BACKGROUND);
		UIManager.put("Button.background", SURFACE_DARK);
		UIManager.put("Button.foreground", TEXT);
		UIManager.put("Label.foreground", TEXT);

		Border fieldBorder = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER, 1),
			BorderFactory.createEmptyBorder(dp(5), dp(9), dp(5), dp(9)));
		UIManager.put("TextField.border", fieldBorder);
		UIManager.put("FormattedTextField.border", fieldBorder);
		UIManager.put("PasswordField.border", fieldBorder);
		UIManager.put("Button.margin", new Insets(dp(8), dp(14), dp(8), dp(14)));

	}

	public static void stylePrimary(JButton button) {
		button.setForeground(Color.WHITE);
		button.setBackground(ACCENT);
		button.setBorder(BorderFactory.createEmptyBorder(dp(9), dp(16), dp(9), dp(16)));
		button.setMargin(new Insets(dp(8), dp(14), dp(8), dp(14)));
		button.setFocusPainted(false);
		button.setFont(font(Font.BOLD, 13));
	}

	public static void styleSecondary(JButton button) {
		button.setForeground(Color.WHITE);
		button.setBackground(SURFACE_DARK);
		button.setBorder(BorderFactory.createEmptyBorder(dp(9), dp(13), dp(9), dp(13)));
		button.setMargin(new Insets(dp(8), dp(14), dp(8), dp(14)));
		button.setFocusPainted(false);
		button.setFont(font(Font.BOLD, 13));
	}

	/** Give the existing device picker readable controls in either Windows LAF. */
	public static void styleDeviceDialog(JDialog dialog) {
		dialog.getContentPane().setBackground(BACKGROUND);
		styleDeviceChildren(dialog.getContentPane());
		dialog.getRootPane().setBackground(BACKGROUND);
		dialog.pack();
	}

	private static void styleDeviceChildren(Container container) {
		for (Component child : container.getComponents()) {
			if (child instanceof JButton) {
				JButton button = (JButton) child;
				boolean primary = "select".equals(button.getActionCommand());
				button.setUI(new DesktopButtonUI());
				button.setOpaque(false);
				button.setContentAreaFilled(false);
				if (primary) stylePrimary(button);
				else styleSecondary(button);
			} else if (child instanceof JList) {
				child.setBackground(SURFACE_DARK);
				child.setForeground(TEXT);
				JList<?> list = (JList<?>) child;
				list.setSelectionBackground(ACCENT_DARK);
				list.setSelectionForeground(TEXT);
			} else if (child instanceof JScrollPane) {
				child.setBackground(SURFACE_DARK);
				((JScrollPane) child).setBorder(BorderFactory.createLineBorder(BORDER));
			} else if (child instanceof JViewport) {
				child.setBackground(SURFACE_DARK);
			} else if (child instanceof JLabel) {
				child.setForeground(TEXT);
				child.setFont(font(Font.BOLD, 14));
			} else if (child instanceof JPanel) {
				child.setBackground(BACKGROUND);
			}
			if (child instanceof Container) styleDeviceChildren((Container) child);
		}
	}

	public static void styleDanger(JButton button) {
		button.setForeground(Color.WHITE);
		button.setBackground(DANGER);
		button.setBorder(BorderFactory.createEmptyBorder(dp(9), dp(16), dp(9), dp(16)));
		button.setMargin(new Insets(dp(8), dp(14), dp(8), dp(14)));
		button.setFocusPainted(false);
		button.setFont(font(Font.BOLD, 13));
	}
}
