/*
 * Copyright © 2026
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 */

package altosui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import org.altusmetrum.altoslib_14.*;
import org.altusmetrum.altosuilib_14.*;

/** TeleMega-focused Windows desktop entry point for AltOS 1.9.x. */
public class AltosUI extends AltosUIFrame implements AltosEepromGrapher {

	public AltosVoice voice = new AltosVoice();
	private Container pane;
	private JTabbedPane workspace;
	private AltosModernMapPreload maps;
	private AltosFlightPathPanel flightPath;
	private JPanel dataWorkspace;
	private AltosEmbeddedFlightUI monitor;
	private boolean monitorReplay;
	private AltosRecoveryLink recoveryLink;
	private JPanel recoveryWorkspace;
	private JLabel recoveryStatus;
	private JLabel recoveryPhoneLocation;
	private JButton recoveryConnect;
	private JLabel sessionSummary;
	private JLabel sidebarConnection;
	private int pathLoadVersion;
	private java.util.Map<String, JButton> navigationButtons = new java.util.HashMap<String, JButton>();

	public static boolean load_library(Frame frame) {
		if (!Altos.load_library()) {
			if (!GraphicsEnvironment.isHeadless())
				JOptionPane.showMessageDialog(frame,
					String.format("No AltOS library in \"%s\"",
						System.getProperty("java.library.path", "<undefined>")),
					"Cannot load device access library",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}

	/* Swing's EventListenerList is stricter on some older Windows JREs. */
	private void addActionListener(AbstractButton button, final ActionListener listener) {
		button.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent event) {
				listener.actionPerformed(event);
			}
		});
	}

	private JLabel label(String text, int size, int style, Color color) {
		JLabel result = new JLabel(text);
		result.setFont(AltosDesktopTheme.font(style, size));
		result.setForeground(color);
		result.setAlignmentX(Component.LEFT_ALIGNMENT);
		return result;
	}

	private Badge badge(String text, Color color) {
		return new Badge(text, color);
	}

	private JPanel sidebarBrand() {
		JPanel brand = new JPanel();
		brand.setOpaque(false);
		brand.setLayout(new BoxLayout(brand, BoxLayout.X_AXIS));
		brand.setAlignmentX(Component.LEFT_ALIGNMENT);
		brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(40)));

		JLabel mark = label("A", 18, Font.BOLD, Color.WHITE);
		mark.setHorizontalAlignment(SwingConstants.CENTER);
		mark.setPreferredSize(new Dimension(AltosDesktopTheme.dp(34), AltosDesktopTheme.dp(34)));
		mark.setMaximumSize(new Dimension(AltosDesktopTheme.dp(34), AltosDesktopTheme.dp(34)));
		mark.setOpaque(true);
		mark.setBackground(AltosDesktopTheme.ACCENT);
		brand.add(mark);
		brand.add(Box.createHorizontalStrut(AltosDesktopTheme.dp(10)));

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		copy.add(label("AltOS", 15, Font.BOLD, Color.WHITE));
		copy.add(label("Windows ground station", 10, Font.PLAIN, AltosDesktopTheme.SIDEBAR_MUTED));
		brand.add(copy);
		return brand;
	}

	private JButton navItem(String icon, String text, boolean selected, ActionListener listener) {
		JButton item = AltosDesktopTheme.button(text);
		item.setOpaque(true);
		item.setAlignmentX(Component.LEFT_ALIGNMENT);
		item.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(36)));
		item.setPreferredSize(new Dimension(AltosDesktopTheme.dp(200), AltosDesktopTheme.dp(36)));
		item.setBorder(new EmptyBorder(AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(8)));
		item.setFont(AltosDesktopTheme.font(selected ? Font.BOLD : Font.PLAIN, 12));
		item.setForeground(selected ? AltosDesktopTheme.BACKGROUND : AltosDesktopTheme.SIDEBAR_TEXT);
		item.setBackground(selected ? AltosDesktopTheme.TEXT : AltosDesktopTheme.SIDEBAR);
		item.setHorizontalAlignment(SwingConstants.LEFT);
		item.setFocusPainted(false);
		item.putClientProperty("altos.theme.flat", Boolean.TRUE);
		item.setAlignmentX(Component.LEFT_ALIGNMENT);
		item.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(36)));
		if (listener != null)
			addActionListener(item, listener);
		if (navigationButtons == null) navigationButtons = new java.util.HashMap<String, JButton>();
		navigationButtons.put(text, item);
		return item;
	}

	private void updateNavigation() {
		String page = workspace.getTitleAt(workspace.getSelectedIndex());
		for (java.util.Map.Entry<String, JButton> entry : navigationButtons.entrySet()) {
			boolean selected = entry.getKey().equals(page) ||
				("Maps".equals(page) && "Offline maps".equals(entry.getKey()));
			JButton button = entry.getValue();
			button.setBackground(selected ? AltosDesktopTheme.TEXT : AltosDesktopTheme.SIDEBAR);
			button.setForeground(selected ? AltosDesktopTheme.BACKGROUND : AltosDesktopTheme.SIDEBAR_TEXT);
			button.setFont(AltosDesktopTheme.font(selected ? Font.BOLD : Font.PLAIN, 12));
		}
	}

	private JPanel navigation() {
		JPanel nav = new JPanel(new BorderLayout());
		nav.setBackground(AltosDesktopTheme.SIDEBAR);
		nav.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 0, 1, AltosDesktopTheme.BORDER),
			new EmptyBorder(AltosDesktopTheme.dp(18), AltosDesktopTheme.dp(10), AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(10))));
		nav.setPreferredSize(new Dimension(AltosDesktopTheme.dp(214), AltosDesktopTheme.dp(0)));
		JPanel menu = new JPanel();
		menu.setOpaque(false);
		menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
		menu.add(sidebarBrand());
		menu.add(Box.createVerticalStrut(AltosDesktopTheme.dp(28)));

		menu.add(label("MISSION", 10, Font.BOLD, AltosDesktopTheme.SIDEBAR_MUTED));
		menu.add(Box.createVerticalStrut(AltosDesktopTheme.dp(7)));
		menu.add(navItem("", "Mission control", true, new ActionListener() {
			public void actionPerformed(ActionEvent e) { workspace.setSelectedIndex(0); }
		}));
		menu.add(navItem("", "Live monitor", false, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int tab = workspace.indexOfTab("Live monitor");
				if (monitor != null && tab >= 0) workspace.setSelectedIndex(tab);
				else ConnectToDevice();
			}
		}));
		menu.add(navItem("", "Flight data", false, new ActionListener() {
			public void actionPerformed(ActionEvent e) { openDataWorkspace(); }
		}));
		menu.add(navItem("", "3D flight path", false, new ActionListener() {
			public void actionPerformed(ActionEvent e) { openFlightPath(); }
		}));
		menu.add(navItem("", "Offline maps", false, new ActionListener() {
			public void actionPerformed(ActionEvent e) { LoadMaps(); }
		}));

		menu.add(Box.createVerticalStrut(AltosDesktopTheme.dp(19)));
		menu.add(label("VEHICLE", 10, Font.BOLD, AltosDesktopTheme.SIDEBAR_MUTED));
		menu.add(Box.createVerticalStrut(AltosDesktopTheme.dp(7)));
		menu.add(navItem("", "TeleMega setup", false, new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConfigureTeleMetrum(); }
		}));
		menu.add(navItem("", "Ground tools", false, new ActionListener() {
			public void actionPerformed(ActionEvent e) { ScanChannels(); }
		}));
		menu.add(navItem("", "Preferences", false, new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConfigureAltosUI(); }
		}));
		nav.add(menu, BorderLayout.NORTH);
		JPanel bottom = new JPanel();
		bottom.setOpaque(false);
		bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
		bottom.add(label("AltOS 1.9.22", 10, Font.PLAIN, AltosDesktopTheme.SIDEBAR_MUTED));
		bottom.add(Box.createVerticalStrut(AltosDesktopTheme.dp(5)));
		sidebarConnection = label("Connect receiver to begin", 10, Font.PLAIN, AltosDesktopTheme.SIDEBAR_MUTED);
		bottom.add(sidebarConnection);
		nav.add(bottom, BorderLayout.SOUTH);
		return nav;
	}

	private JPanel sessionBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(AltosDesktopTheme.TOPBAR);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, AltosDesktopTheme.BORDER),
			new EmptyBorder(AltosDesktopTheme.dp(6), AltosDesktopTheme.dp(14), AltosDesktopTheme.dp(6), AltosDesktopTheme.dp(14))));
		bar.add(label("ALTUS METRUM  /  TELEMEGA GROUND STATION",
			10, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT), BorderLayout.WEST);
		bar.add(label("ALTOS WINDOWS DESKTOP", 10, Font.BOLD, AltosDesktopTheme.MUTED_TEXT), BorderLayout.EAST);
		return bar;
	}

	private JPanel pageHeader() {
		JPanel header = new JPanel(new BorderLayout(12, 0));
		header.setOpaque(false);
		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		copy.add(label("OVERVIEW", 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
		copy.add(Box.createVerticalStrut(AltosDesktopTheme.dp(3)));
		copy.add(label("Flight summary", 26, Font.BOLD, Color.WHITE));
		copy.add(Box.createVerticalStrut(AltosDesktopTheme.dp(3)));
		copy.add(label("Connect a ground station or open a recorded flight to populate telemetry.",
			12, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT));
		header.add(copy, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
		buttons.setOpaque(false);
		JButton open = AltosDesktopTheme.button("Open flight");
		AltosDesktopTheme.styleSecondary(open);
		addActionListener(open, new ActionListener() {
			public void actionPerformed(ActionEvent e) { openDataWorkspace(); }
		});
		JButton connect = AltosDesktopTheme.button("Connect");
		AltosDesktopTheme.stylePrimary(connect);
		addActionListener(connect, new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConnectToDevice(); }
		});
		buttons.add(open);
		buttons.add(connect);
		header.add(buttons, BorderLayout.EAST);
		return header;
	}

	private JPanel metric(String icon, String value, String unit, String title, Color color) {
		JPanel row = new JPanel(new BorderLayout(0, 8));
		row.setBackground(AltosDesktopTheme.SURFACE);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
			new EmptyBorder(AltosDesktopTheme.dp(14), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(14), AltosDesktopTheme.dp(16))));
		row.setPreferredSize(new Dimension(AltosDesktopTheme.dp(300), AltosDesktopTheme.dp(148)));
		row.setMinimumSize(new Dimension(AltosDesktopTheme.dp(220), AltosDesktopTheme.dp(148)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(148)));

		JLabel iconLabel = label(icon, 28, Font.PLAIN, AltosDesktopTheme.ICON);
		iconLabel.setHorizontalAlignment(SwingConstants.LEFT);
		row.add(iconLabel, BorderLayout.NORTH);

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JPanel valueLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		valueLine.setOpaque(false);
		valueLine.add(label(value, 46, Font.BOLD, Color.WHITE));
		valueLine.add(label(unit, 23, Font.PLAIN, Color.WHITE));
		copy.add(valueLine);
		copy.add(Box.createVerticalStrut(AltosDesktopTheme.dp(2)));
		copy.add(badge(title, color));
		row.add(copy, BorderLayout.CENTER);
		return row;
	}

	private JPanel miniMetric(String title, String value) {
		JPanel item = new JPanel();
		item.setOpaque(false);
		item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
		item.add(label(title, 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
		item.add(Box.createVerticalStrut(AltosDesktopTheme.dp(4)));
		item.add(label(value, 18, Font.BOLD, Color.WHITE));
		return item;
	}

	private JPanel miniMetrics() {
		JPanel row = new JPanel(new GridLayout(1, 2, 42, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(AltosDesktopTheme.dp(18), AltosDesktopTheme.dp(60), AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(0)));
		row.add(miniMetric("ANGLE AT BURNOUT", "—°"));
		row.add(miniMetric("TOUCHDOWN SPEED", "— m/s"));
		return row;
	}

	private JPanel statsRow(String leftName, String leftValue, String rightName, String rightValue, boolean shaded) {
		JPanel row = new JPanel(new GridBagLayout());
		row.setBackground(shaded ? AltosDesktopTheme.TABLE_ALT : AltosDesktopTheme.TABLE);
		row.setBorder(new EmptyBorder(AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(11), AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(11)));
		row.setPreferredSize(new Dimension(AltosDesktopTheme.dp(620), AltosDesktopTheme.dp(34)));
		row.setMinimumSize(new Dimension(AltosDesktopTheme.dp(420), AltosDesktopTheme.dp(34)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(34)));
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.insets = new Insets(AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(10));
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.weightx = 0.34;
		row.add(label(leftName, 11, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT), c);
		c.gridx = 1;
		c.weightx = 0.16;
		row.add(label(leftValue, 11, Font.BOLD, Color.WHITE), c);
		c.gridx = 2;
		c.weightx = 0.34;
		row.add(label(rightName, 11, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT), c);
		c.gridx = 3;
		c.weightx = 0.16;
		c.insets = new Insets(AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0));
		row.add(label(rightValue, 11, Font.BOLD, Color.WHITE), c);
		return row;
	}

	private JPanel statsTable() {
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(label("FLIGHT STATISTICS", 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
		panel.add(Box.createVerticalStrut(AltosDesktopTheme.dp(9)));
		String[][] rows = {
			{"Flight duration", "—", "Countdown duration", "—"},
			{"Ascent duration", "—", "Descent duration", "—"},
			{"Burnout #1 time", "—", "Apogee time", "—"},
			{"Touchdown time", "—", "Burnout #1 altitude", "—"},
			{"Apogee angle from vertical", "—", "Launch tower angle", "—"},
			{"Burnout angle from vertical", "—", "Peak drag deceleration", "—"},
			{"Max captured acceleration", "—", "Average descent speed", "—"},
			{"Average roll rate on ascent", "—", "Launch → touchdown distance", "—"},
			{"Total trajectory length", "—", "Minimum battery voltage", "—"},
			{"Pyro continuity at launch", "P1  —  P2  —  P3  —", "Hot spots", "None detected."}
		};
		for (int i = 0; i < rows.length; i++)
			panel.add(statsRow(rows[i][0], rows[i][1], rows[i][2], rows[i][3], (i & 1) == 1));
		return panel;
	}

	private JPanel systemStatus() {
		JPanel panel = new JPanel();
		panel.setBackground(AltosDesktopTheme.SURFACE);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
			new EmptyBorder(AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(17), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(17))));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(label("SYSTEM STATUS", 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
		panel.add(Box.createVerticalStrut(AltosDesktopTheme.dp(15)));
		panel.add(label("GROUND STATION", 10, Font.BOLD, AltosDesktopTheme.MUTED_TEXT));
		panel.add(Box.createVerticalStrut(AltosDesktopTheme.dp(5)));
		panel.add(label("●  Ready for connection", 15, Font.BOLD, AltosDesktopTheme.SUCCESS));
		panel.add(Box.createVerticalStrut(AltosDesktopTheme.dp(17)));
		panel.add(statusLine("Telemetry link", "Offline"));
		panel.add(statusLine("Flight session", "Not selected"));
		panel.add(statusLine("Native library", "Loaded"));
		panel.add(Box.createVerticalGlue());
		JButton monitor = AltosDesktopTheme.button("Monitor flight");
		AltosDesktopTheme.stylePrimary(monitor);
		monitor.setAlignmentX(Component.LEFT_ALIGNMENT);
		addActionListener(monitor, new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConnectToDevice(); }
		});
		panel.add(monitor);
		return panel;
	}

	private JPanel statusLine(String name, String value) {
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(27)));
		line.add(label(name, 11, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT), BorderLayout.WEST);
		line.add(label(value, 11, Font.BOLD, Color.WHITE), BorderLayout.EAST);
		return line;
	}

	private JPanel overviewContent() {
		JPanel content = new JPanel(new BorderLayout(0, 16));
		content.setOpaque(false);

		JPanel metrics = new JPanel(new GridLayout(1, 3, 14, 0));
		metrics.setOpaque(false);
		metrics.add(metric("↕", "—", "m AGL", "Altitude", AltosDesktopTheme.PURPLE));
		metrics.add(metric("⌁", "—", "m/s", "Max vertical speed", AltosDesktopTheme.ORANGE));
		metrics.add(metric("✣", "—", "G", "Max acceleration", AltosDesktopTheme.SUCCESS));
		content.add(metrics, BorderLayout.NORTH);

		JPanel lower = new JPanel(new GridBagLayout());
		lower.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.fill = GridBagConstraints.BOTH;
		c.weighty = 1.0;
		c.gridx = 0;
		c.weightx = 0.74;
		c.insets = new Insets(AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(14));
		lower.add(statsTable(), c);
		c.gridx = 1;
		c.weightx = 0.26;
		c.insets = new Insets(AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0));
		lower.add(systemStatus(), c);
		content.add(lower, BorderLayout.CENTER);
		return content;
	}

	private JPanel missionHeader() {
		JPanel header = new JPanel(new BorderLayout(18, 0));
		header.setOpaque(false);
		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		copy.add(label("TELEMEGA WORKSPACE", 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
		copy.add(Box.createVerticalStrut(AltosDesktopTheme.dp(4)));
		copy.add(label("Mission control", 28, Font.BOLD, Color.WHITE));
		copy.add(Box.createVerticalStrut(AltosDesktopTheme.dp(4)));
		copy.add(label("Configure your vehicle, connect telemetry, and review each flight.",
			12, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT));
		header.add(copy, BorderLayout.WEST);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		actions.setOpaque(false);
		JButton open = commandButton("Open flight", "Select a recorded flight for the 3D GPS view", new ActionListener() {
			public void actionPerformed(ActionEvent e) { OpenFlightPath(); }
		}, false);
		JButton connect = commandButton("Connect", "Connect TeleMega through a TeleDongle", new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConnectToDevice(); }
		}, true);
		actions.add(open);
		actions.add(connect);
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	private JPanel statusBlock(String eyebrow, String value, String detail, Color color) {
		JPanel block = new JPanel();
		block.setBackground(AltosDesktopTheme.SURFACE);
		block.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
			new EmptyBorder(AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(14), AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(14))));
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.add(label(eyebrow, 10, Font.BOLD, AltosDesktopTheme.MUTED_TEXT));
		block.add(Box.createVerticalStrut(AltosDesktopTheme.dp(5)));
		block.add(label("●  " + value, 15, Font.BOLD, color));
		block.add(Box.createVerticalStrut(AltosDesktopTheme.dp(3)));
		block.add(label(detail, 11, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT));
		block.setPreferredSize(new Dimension(AltosDesktopTheme.dp(250), AltosDesktopTheme.dp(78)));
		block.setMinimumSize(new Dimension(AltosDesktopTheme.dp(160), AltosDesktopTheme.dp(78)));
		block.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(78)));
		return block;
	}

	private JPanel missionStatus() {
		JPanel status = new JPanel(new GridLayout(1, 3, 12, 0));
		status.setOpaque(false);
		status.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(78)));
		status.add(statusBlock("VEHICLE PROFILE", "TeleMega tools", "No device status available", AltosDesktopTheme.ACCENT_LIGHT));
		status.add(statusBlock("TELEMETRY LINK", "Not connected", "Select a TeleDongle to begin", AltosDesktopTheme.WARNING));
		status.add(statusBlock("RECOVERY REVIEW", "Not checked", "Verify on the actual vehicle", AltosDesktopTheme.MUTED_TEXT));
		return status;
	}

	private JButton commandButton(String text, String tip, ActionListener listener, boolean primary) {
		JButton button = AltosDesktopTheme.button(text);
		if (primary)
			AltosDesktopTheme.stylePrimary(button);
		else
			AltosDesktopTheme.styleSecondary(button);
		button.setToolTipText(tip);
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setPreferredSize(new Dimension(AltosDesktopTheme.dp(150), AltosDesktopTheme.dp(34)));
		button.setMinimumSize(new Dimension(AltosDesktopTheme.dp(100), AltosDesktopTheme.dp(34)));
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(34)));
		addActionListener(button, listener);
		return button;
	}

	private JPanel workflowStep(String number, String title, String detail, String action, ActionListener listener) {
		JPanel step = new JPanel(new BorderLayout());
		step.setBackground(AltosDesktopTheme.SURFACE_DARK);
		step.setBorder(new EmptyBorder(AltosDesktopTheme.dp(9), AltosDesktopTheme.dp(9), AltosDesktopTheme.dp(9), AltosDesktopTheme.dp(9)));
		JButton button = commandButton(number + "   " + title, detail, listener, false);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		step.add(button, BorderLayout.CENTER);
		return step;
	}

	private JPanel quickStart() {
		JPanel card = new JPanel(new BorderLayout(0, 11));
		card.setBackground(AltosDesktopTheme.SURFACE);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
			new EmptyBorder(AltosDesktopTheme.dp(15), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(16))));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(151)));
		JPanel heading = new JPanel();
		heading.setOpaque(false);
		heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
		heading.add(label("TELEMEGA QUICK START", 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
		heading.add(Box.createVerticalStrut(AltosDesktopTheme.dp(3)));
		heading.add(label("Your preflight sequence", 16, Font.BOLD, Color.WHITE));
		card.add(heading, BorderLayout.NORTH);

		JPanel steps = new JPanel(new GridLayout(1, 3, 10, 0));
		steps.setOpaque(false);
		steps.add(workflowStep("01", "Connect receiver", "Choose the TeleDongle used for telemetry.", "Connect", new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConnectToDevice(); }
		}));
		steps.add(workflowStep("02", "Configure vehicle", "Open TeleMega flight and radio settings.", "Configure", new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConfigureTeleMetrum(); }
		}));
		steps.add(workflowStep("03", "Check readiness", "Review idle status before leaving the pad.", "Check idle", new ActionListener() {
			public void actionPerformed(ActionEvent e) { IdleMonitor(); }
		}));
		card.add(steps, BorderLayout.CENTER);
		return card;
	}

	private JPanel toolCard(String title, String description, JButton[] buttons) {
		JPanel card = new JPanel(new BorderLayout(0, 10));
		card.setBackground(AltosDesktopTheme.SURFACE);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
			new EmptyBorder(AltosDesktopTheme.dp(15), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(16))));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(168)));
		JPanel heading = new JPanel();
		heading.setOpaque(false);
		heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
		heading.add(label(title, 15, Font.BOLD, Color.WHITE));
		heading.add(Box.createVerticalStrut(AltosDesktopTheme.dp(3)));
		heading.add(label(description, 10, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT));
		card.add(heading, BorderLayout.NORTH);
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
		grid.setOpaque(false);
		for (JButton button : buttons)
			grid.add(button);
		card.add(grid, BorderLayout.CENTER);
		return card;
	}

	private JPanel missionDashboard() {
		JPanel body = new JPanel(new BorderLayout(0, AltosDesktopTheme.dp(14)));
		body.setOpaque(false);
		body.setBorder(new EmptyBorder(0, 0, AltosDesktopTheme.dp(4), 0));
		body.add(quickStart(), BorderLayout.NORTH);

		JButton[] flight = {
			commandButton("Live monitor", "Connect and monitor live TeleMega telemetry", new ActionListener() {
				public void actionPerformed(ActionEvent e) { ConnectToDevice(); }
			}, true),
			commandButton("Idle monitor", "Check TeleMega flight readiness", new ActionListener() {
				public void actionPerformed(ActionEvent e) { IdleMonitor(); }
			}, false),
			commandButton("Open flight data", "Open a recorded flight", new ActionListener() {
				public void actionPerformed(ActionEvent e) { openDataWorkspace(); }
			}, false),
			commandButton("Replay flight", "Replay recorded telemetry", new ActionListener() {
				public void actionPerformed(ActionEvent e) { Replay(); }
			}, false)
		};
		JButton[] vehicle = {
			commandButton("TeleMega setup", "Configure flight computer, radio, storage, and recovery settings", new ActionListener() {
				public void actionPerformed(ActionEvent e) { ConfigureTeleMetrum(); }
			}, true),
			commandButton("Firmware update", "Flash approved TeleMega firmware", new ActionListener() {
				public void actionPerformed(ActionEvent e) { FlashImage(); }
			}, false),
			commandButton("Launch controller", "Open launch-control tools", new ActionListener() {
				public void actionPerformed(ActionEvent e) { LaunchController(); }
			}, false),
			commandButton("Igniter test", "Open the existing igniter test tool", new ActionListener() {
				public void actionPerformed(ActionEvent e) { FireIgniter(); }
			}, false)
		};
		JButton[] data = {
			commandButton("Save flight data", "Download or manage onboard flight records", new ActionListener() {
				public void actionPerformed(ActionEvent e) { SaveFlightData(); }
			}, false),
			commandButton("Export CSV", "Export selected flight data", new ActionListener() {
				public void actionPerformed(ActionEvent e) { ExportData(); }
			}, false),
			commandButton("Ground-station setup", "Configure TeleDongle and radio settings", new ActionListener() {
				public void actionPerformed(ActionEvent e) { ConfigureTeleDongle(); }
			}, false),
			commandButton("Scan channels", "Find an active TeleMega telemetry channel", new ActionListener() {
				public void actionPerformed(ActionEvent e) { ScanChannels(); }
			}, false)
		};
		JButton[] tools = {
			commandButton("Load maps", "Prepare offline map data", new ActionListener() {
				public void actionPerformed(ActionEvent e) { LoadMaps(); }
			}, false),
			commandButton("phone recovery link", "Exchange recovery GPS and telemetry with the iPhone app", new ActionListener() {
				public void actionPerformed(ActionEvent e) { openRecoveryWorkspace(); }
			}, true),
			commandButton("AltOS preferences", "Set units, voices, fonts, and application preferences", new ActionListener() {
				public void actionPerformed(ActionEvent e) { ConfigureAltosUI(); }
			}, false),
			commandButton("Graph telemetry", "Plot selected flight records", new ActionListener() {
				public void actionPerformed(ActionEvent e) { GraphData(); }
			}, false),
			commandButton("Set callsign", "Set the callsign used by AltOS", new ActionListener() {
				public void actionPerformed(ActionEvent e) { ConfigureCallsign(); }
			}, false)
		};

		JPanel deck = new JPanel(new GridLayout(2, 2, 14, 14));
		deck.setOpaque(false);
		deck.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(350)));
		deck.add(toolCard("Flight operations", "Monitor and inspect a TeleMega mission.", flight));
		deck.add(toolCard("TeleMega controls", "Configure the flight computer and launch equipment.", vehicle));
		deck.add(toolCard("Data and receiver", "Move telemetry between the vehicle and desktop.", data));
		deck.add(toolCard("Utilities", "Maps, graphs, preferences, and callsign.", tools));
		body.add(deck, BorderLayout.CENTER);
		return body;
	}

	private JPanel footer() {
		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);
		footer.setBorder(new EmptyBorder(AltosDesktopTheme.dp(14), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(0)));
		sessionSummary = label("No receiver connected  ·  No flight selected", 11, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT);
		footer.add(sessionSummary, BorderLayout.WEST);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
		actions.setOpaque(false);
		JButton configure = AltosDesktopTheme.button("Configure");
		AltosDesktopTheme.styleSecondary(configure);
		configure.setBorder(new EmptyBorder(AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(15), AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(15)));
		addActionListener(configure, new ActionListener() {
			public void actionPerformed(ActionEvent e) { ConfigureTeleMetrum(); }
		});
		JButton firmware = AltosDesktopTheme.button("Firmware");
		AltosDesktopTheme.styleSecondary(firmware);
		firmware.setBorder(new EmptyBorder(AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(15), AltosDesktopTheme.dp(8), AltosDesktopTheme.dp(15)));
		addActionListener(firmware, new ActionListener() {
			public void actionPerformed(ActionEvent e) { FlashImage(); }
		});
		actions.add(configure);
		actions.add(firmware);
		footer.add(actions, BorderLayout.EAST);
		return footer;
	}

	private JPanel missionPage() {
		JPanel main = new JPanel(new BorderLayout(0, 20));
		main.setBackground(AltosDesktopTheme.BACKGROUND);
		main.setBorder(new EmptyBorder(AltosDesktopTheme.dp(22), AltosDesktopTheme.dp(28), AltosDesktopTheme.dp(18), AltosDesktopTheme.dp(28)));
		main.add(missionHeader(), BorderLayout.NORTH);
		JPanel dashboardWrapper = new JPanel(new BorderLayout());
		dashboardWrapper.setOpaque(false);
		dashboardWrapper.add(missionDashboard(), BorderLayout.CENTER);
		JScrollPane scroll = AltosDesktopTheme.scroll(dashboardWrapper);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setViewportBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		main.add(scroll, BorderLayout.CENTER);
		main.add(footer(), BorderLayout.SOUTH);
		return main;
	}

	private JTabbedPane mainView() {
		workspace = AltosDesktopTheme.tabs();
		workspace.setFont(AltosDesktopTheme.font(Font.BOLD, 12));
		workspace.setBackground(AltosDesktopTheme.SURFACE);
		workspace.setForeground(AltosDesktopTheme.TEXT);
		workspace.addTab("Mission control", missionPage());
		workspace.addChangeListener(e -> updateNavigation());
		return workspace;
	}

	private void showWorkspace(String title, Component view) {
		int index = workspace.indexOfTab(title);
		if (index < 0) {
			workspace.addTab(title, view);
			index = workspace.indexOfTab(title);
		}
		workspace.setSelectedIndex(index);
	}

	private void openDataWorkspace() {
		if (dataWorkspace == null) {
			dataWorkspace = new JPanel(new BorderLayout(0, 20));
			dataWorkspace.setBackground(AltosDesktopTheme.BACKGROUND);
			dataWorkspace.setBorder(new EmptyBorder(AltosDesktopTheme.dp(30), AltosDesktopTheme.dp(28), AltosDesktopTheme.dp(25), AltosDesktopTheme.dp(28)));
			JPanel header = new JPanel();
			header.setOpaque(false);
			header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
			header.add(label("FLIGHT ANALYSIS", 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
			header.add(Box.createVerticalStrut(AltosDesktopTheme.dp(7)));
			header.add(label("Flight data", 27, Font.BOLD, AltosDesktopTheme.TEXT));
			header.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
			header.add(label("Open recorded telemetry, inspect its GPS path, or use the original AltOS analysis tools.",
				12, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT));
			dataWorkspace.add(header, BorderLayout.NORTH);
			JPanel actions = new JPanel(new GridLayout(2, 2, 14, 14));
			actions.setOpaque(false);
			actions.add(toolCard("3D GPS path", "Inspect the measured route in the main workspace.", new JButton[] {
				commandButton("Open flight in 3D", "Select a saved TeleMega recording", e -> OpenFlightPath(), true)
			}));
			actions.add(toolCard("Telemetry graph", "Use AltOS's original graph controls and statistics.", new JButton[] {
				commandButton("Graph a flight", "Open the original AltOS flight graph", e -> GraphData(), false)
			}));
			actions.add(toolCard("Playback", "Replay recorded TeleMega telemetry in the monitor tab.", new JButton[] {
				commandButton("Replay telemetry", "Select a recorded flight for playback", e -> Replay(), false)
			}));
			actions.add(toolCard("Export", "Save the recorded flight data as CSV.", new JButton[] {
				commandButton("Export CSV", "Select a flight to export", e -> ExportData(), false)
			}));
			JPanel top = new JPanel(new BorderLayout());
			top.setOpaque(false);
			top.add(actions, BorderLayout.NORTH);
			dataWorkspace.add(top, BorderLayout.CENTER);
		}
		showWorkspace("Flight data", dataWorkspace);
	}

	private void openFlightPath() {
		if (flightPath == null) {
			flightPath = new AltosFlightPathPanel();
			flightPath.setOpenAction(new Runnable() {
				public void run() { OpenFlightPath(); }
			});
			flightPath.setConnectAction(new Runnable() {
				public void run() { ConnectToDevice(); }
			});
		}
		showWorkspace("3D flight path", flightPath);
	}

	private void OpenFlightPath() {
		if (monitor != null) {
			JOptionPane.showMessageDialog(this,
				monitorReplay ? "Close the replay in Live monitor before opening another recording." :
				"Use Disconnect in Live monitor before opening a saved flight. Your receiver remains connected until then.",
				"Monitor session active", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		openFlightPath();
		AltosDataChooser chooser = new AltosDataChooser(this);
		final AltosRecordSet set = chooser.runDialog();
		if (set == null) return;
		final int version = ++pathLoadVersion;
		final String name = chooser.file().getName();
		flightPath.loadRecorded(null, "Loading " + name + "…");
		setSession("Loading recorded flight: " + name, "Reading GPS recording");
		new SwingWorker<java.util.List<AltosGPSTimeValue>, Void>() {
			protected java.util.List<AltosGPSTimeValue> doInBackground() {
				return AltosFlightPathPanel.recorded(set);
			}
			protected void done() {
				if (version != pathLoadVersion) return;
				try {
					flightPath.loadRecorded(get(), name);
					setSession("Recorded flight: " + name, "Recorded flight ready");
				}
				catch (Exception e) {
					flightPath.loadRecorded(null, "Unable to load " + name);
					setSession("Unable to read recorded flight", "Check selected file");
					showError("Cannot read GPS flight path", e);
				}
			}
		}.execute();
	}

	public AltosUI() {
		AltosUIPreferences.set_component(this);

		pane = getContentPane();
		pane.setBackground(AltosDesktopTheme.BACKGROUND);
		pane.setLayout(new BorderLayout());
		pane.add(sessionBar(), BorderLayout.NORTH);
		pane.add(navigation(), BorderLayout.WEST);
		pane.add(mainView(), BorderLayout.CENTER);

		setTitle("AltOS");
		Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setMinimumSize(new Dimension(Math.min(workArea.width, AltosDesktopTheme.dp(1000)),
			Math.min(workArea.height, AltosDesktopTheme.dp(700))));
		int width = Math.min(workArea.width, Math.max(AltosDesktopTheme.dp(1000), (int) (workArea.width * 0.96)));
		int height = Math.min(workArea.height, Math.max(AltosDesktopTheme.dp(700), (int) (workArea.height * 0.94)));
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (monitor != null) monitor.closeWorkspace();
				if (maps != null) maps.disposeWorkspace();
				if (flightPath != null) flightPath.stop();
				if (recoveryLink != null) recoveryLink.stop();
				System.exit(0);
			}
		});
		setVisible(true);
		/* AltosUIFrame.setVisible(true) resets size and position; apply our bounds last. */
		setBounds(workArea.x + (workArea.width - width) / 2,
			workArea.y + (workArea.height - height) / 2, width, height);
	}

	@Override public void ui_changed(String lookAndFeel) {
		/* The original frame packs on every theme change, shrinking this workspace. */
		Dimension size = getSize();
		Point position = getLocation();
		AltosDesktopTheme.respectLookAndFeel();
		SwingUtilities.updateComponentTreeUI(this);
		setSize(size);
		setLocation(position);
		revalidate();
		repaint();
	}

	private void ConnectToDevice() {
		/* Construct the original picker ourselves so its existing device scan,
		 * selection, Bluetooth and cancel behavior stay intact while its buttons
		 * match the Windows workspace in both native and Nimbus modes. */
		AltosDeviceUIDialog dialog = new AltosDeviceUIDialog(this, this, Altos.product_basestation);
		AltosDesktopTheme.styleDeviceDialog(dialog);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
		AltosDevice device = dialog.getValue();
		dialog.dispose();
		if (device == null)
			return;
		telemetry_window(device);
	}

	public void scan_device_selected(AltosDevice device) {
		if (device != null)
			telemetry_window(device);
	}

	void telemetry_window(AltosDevice device) {
		try {
			AltosFlightReader reader = new AltosTelemetryReader(new AltosSerial(device));
			if (reader != null) openMonitor(reader, device.getSerial());
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot open device", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void setSession(String footerText, String sidebarText) {
		if (sessionSummary != null) sessionSummary.setText(footerText);
		if (sidebarConnection != null) sidebarConnection.setText(sidebarText);
	}

	private void openMonitor(AltosFlightReader reader, int serial) {
		openFlightPath();
		++pathLoadVersion;
		if (monitor != null) {
			monitor.closeWorkspace();
			monitor = null;
			int old = workspace.indexOfTab("Live monitor");
			if (old >= 0) workspace.removeTabAt(old);
		}
		flightPath.startLive();
		AltosFlightReader monitorReader = serial >= 0 ?
			new AltosRecoveryReader(reader, serial) : reader;
		AltosEmbeddedFlightUI opened = new AltosEmbeddedFlightUI(voice, monitorReader, serial, flightPath);
		monitor = opened;
		monitorReplay = serial < 0;
		setSession(serial >= 0 ? "Live receiver #" + serial + " · Monitor open" :
			"Recorded telemetry · Replay open", serial >= 0 ? "Receiver monitor open" : "Flight replay open");
		JPanel view = new JPanel(new BorderLayout());
		view.setBackground(AltosDesktopTheme.BACKGROUND);
		JPanel toolbar = new JPanel(new BorderLayout());
		toolbar.setBackground(AltosDesktopTheme.SURFACE);
		toolbar.setBorder(new EmptyBorder(AltosDesktopTheme.dp(10), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(10), AltosDesktopTheme.dp(16)));
		toolbar.add(label("LIVE TELEMEGA MONITOR", 13, Font.BOLD, AltosDesktopTheme.TEXT), BorderLayout.WEST);
		JButton disconnect = AltosDesktopTheme.button(serial < 0 ? "Close replay" : "Disconnect");
		AltosDesktopTheme.styleSecondary(disconnect);
		disconnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				opened.closeWorkspace();
				if (monitor == opened) {
					monitor = null;
					monitorReplay = false;
				}
				setSession("No receiver connected  ·  No flight selected", "Connect receiver to begin");
				int tab = workspace.indexOfTab("Live monitor");
				if (tab >= 0) workspace.removeTabAt(tab);
				openFlightPath();
			}
		});
		toolbar.add(disconnect, BorderLayout.EAST);
		view.add(toolbar, BorderLayout.NORTH);
		Container content = opened.workspaceContent();
		JScrollPane liveScroll = AltosDesktopTheme.scroll(content);
		liveScroll.getVerticalScrollBar().setUnitIncrement(18);
		view.add(liveScroll, BorderLayout.CENTER);
		showWorkspace("Live monitor", view);
	}

	private void openRecoveryWorkspace() {
		if (recoveryWorkspace == null) {
			recoveryWorkspace = new JPanel(new BorderLayout(0, AltosDesktopTheme.dp(18)));
			recoveryWorkspace.setBackground(AltosDesktopTheme.BACKGROUND);
			recoveryWorkspace.setBorder(new EmptyBorder(AltosDesktopTheme.dp(28),
				AltosDesktopTheme.dp(30), AltosDesktopTheme.dp(24), AltosDesktopTheme.dp(30)));

			JPanel heading = new JPanel();
			heading.setOpaque(false);
			heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
			heading.add(label("RECOVERY DATA LINK", 11, Font.BOLD, AltosDesktopTheme.ACCENT_LIGHT));
			heading.add(Box.createVerticalStrut(AltosDesktopTheme.dp(7)));
			heading.add(label("iPhone recovery", 27, Font.BOLD, AltosDesktopTheme.TEXT));
			heading.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
			heading.add(label("Share live rocket telemetry and the phone's GPS over Bluetooth LE.",
				12, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT));
			recoveryWorkspace.add(heading, BorderLayout.NORTH);

			JPanel details = new JPanel();
			details.setBackground(AltosDesktopTheme.SURFACE);
			details.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
				new EmptyBorder(AltosDesktopTheme.dp(20), AltosDesktopTheme.dp(22),
					AltosDesktopTheme.dp(20), AltosDesktopTheme.dp(22))));
			details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
			details.add(label("BLUETOOTH LINK", 10, Font.BOLD, AltosDesktopTheme.MUTED_TEXT));
			details.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
			recoveryStatus = label("Not connected", 16, Font.BOLD, AltosDesktopTheme.WARNING);
			details.add(recoveryStatus);
			details.add(Box.createVerticalStrut(AltosDesktopTheme.dp(20)));
			details.add(label("IPHONE LOCATION", 10, Font.BOLD, AltosDesktopTheme.MUTED_TEXT));
			details.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
			recoveryPhoneLocation = label("No phone location received", 13, Font.PLAIN, AltosDesktopTheme.TEXT);
			details.add(recoveryPhoneLocation);
			details.add(Box.createVerticalStrut(AltosDesktopTheme.dp(20)));
			details.add(label("ROCKET TELEMETRY", 10, Font.BOLD, AltosDesktopTheme.MUTED_TEXT));
			details.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
			details.add(label("Live telemetry is forwarded while a receiver is connected.",
				13, Font.PLAIN, AltosDesktopTheme.TEXT));
			details.add(Box.createVerticalStrut(AltosDesktopTheme.dp(22)));
			recoveryConnect = AltosDesktopTheme.button("Connect iPhone");
			AltosDesktopTheme.stylePrimary(recoveryConnect);
			recoveryConnect.setAlignmentX(Component.LEFT_ALIGNMENT);
			recoveryConnect.addActionListener(e -> {
				if (recoveryLink.isRunning())
					new Thread(() -> recoveryLink.stop(), "AltOS-BLE-stop").start();
				else
					recoveryLink.start();
			});
			details.add(recoveryConnect);
			recoveryWorkspace.add(details, BorderLayout.CENTER);

			JLabel safety = label("Recovery data only; the BLE link is unauthenticated and cannot arm, fire, or configure the flight computer. "
				+ "Verify the phone, coordinates, vehicle, and recovery system in person; GPS can be stale or unavailable.",
				11, Font.PLAIN, AltosDesktopTheme.MUTED_TEXT);
			recoveryWorkspace.add(safety, BorderLayout.SOUTH);

			recoveryLink = new AltosRecoveryLink(new AltosRecoveryLink.Listener() {
				public void status(String state, String detail) {
					SwingUtilities.invokeLater(() -> {
						if (recoveryStatus != null) {
							recoveryStatus.setText(state + "  ·  " + detail);
							recoveryStatus.setForeground("ERROR".equals(state) ?
								AltosDesktopTheme.WARNING : AltosDesktopTheme.TEXT);
						}
						if (recoveryConnect != null)
							recoveryConnect.setText("CONNECTED".equals(state) ? "Disconnect iPhone" :
								recoveryLink.isRunning() ? "Stop BLE link" : "Connect iPhone");
					});
				}

				public void phoneLocation(final String[] fields) {
					SwingUtilities.invokeLater(() -> {
						if (recoveryPhoneLocation != null) {
							try {
								long timestamp = Long.parseLong(fields[5]);
								long age = System.currentTimeMillis() - timestamp;
								String freshness = age > 10000 ? "STALE" :
									age < -5000 ? "CLOCK SKEW" : "CURRENT";
								String time = java.text.DateFormat.getTimeInstance(
									java.text.DateFormat.MEDIUM).format(new java.util.Date(timestamp));
								String location = freshness + " · " + fields[1] + ", " + fields[2]
									+ " · " + time;
								if (!fields[3].isEmpty())
									location += " · " + fields[3] + " m altitude";
								if (!fields[4].isEmpty())
									location += " · ±" + fields[4] + " m";
								recoveryPhoneLocation.setText(location);
								recoveryPhoneLocation.setForeground("CURRENT".equals(freshness) ?
									AltosDesktopTheme.SUCCESS : AltosDesktopTheme.WARNING);
							} catch (NumberFormatException exception) {
								recoveryStatus.setText("ERROR · Invalid phone timestamp");
								recoveryStatus.setForeground(AltosDesktopTheme.WARNING);
							}
						}
					});
				}
			});
		}
		showWorkspace("iPhone recovery", recoveryWorkspace);
	}

	private final class AltosRecoveryReader extends AltosFlightReader {
		private final AltosFlightReader delegate;
		private final int receiverSerial;

		AltosRecoveryReader(AltosFlightReader reader, int serial) {
			delegate = reader;
			receiverSerial = serial;
			name = reader.name;
			this.serial = reader.serial;
		}

		@Override public AltosCalData cal_data() { return delegate.cal_data(); }
		@Override public java.io.File backing_file() { return delegate.backing_file(); }
		@Override public void init() { delegate.init(); }
		@Override public void close(boolean interrupted) { delegate.close(interrupted); }

		@Override public AltosState read() throws InterruptedException, java.text.ParseException,
			AltosCRCException, java.io.IOException {
			AltosState state = delegate.read();
			if (state != null && recoveryLink != null)
				recoveryLink.sendState(state, receiverSerial);
			return state;
		}

		@Override public void save_telemetry() { delegate.save_telemetry(); }
		@Override public void set_frequency(double frequency)
			throws InterruptedException, java.util.concurrent.TimeoutException {
			delegate.set_frequency(frequency);
		}
		@Override public void set_telemetry(int telemetry) { delegate.set_telemetry(telemetry); }
		@Override public double monitor_battery() throws InterruptedException { return delegate.monitor_battery(); }
		@Override public void save_frequency() { delegate.save_frequency(); }
		@Override public void set_telemetry_rate(int rate)
			throws InterruptedException, java.util.concurrent.TimeoutException {
			delegate.set_telemetry_rate(rate);
		}
		@Override public boolean supports_telemetry(int telemetry) {
			return delegate.supports_telemetry(telemetry);
		}
		@Override public boolean has_monitor_battery() throws InterruptedException {
			return delegate.has_monitor_battery();
		}
		@Override public boolean supports_telemetry_rate(int rate) {
			return delegate.supports_telemetry_rate(rate);
		}
		@Override public void save_telemetry_rate() { delegate.save_telemetry_rate(); }
	}

	void ConfigureCallsign() {
		JPanel form = new JPanel(new BorderLayout(0, 12));
		form.setBackground(AltosDesktopTheme.SURFACE);
		form.setBorder(new EmptyBorder(AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(14), AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(14)));
		form.add(label("Callsign used for your AltOS ground station", 13,
			Font.PLAIN, AltosDesktopTheme.TEXT), BorderLayout.NORTH);
		JTextField input = AltosDesktopTheme.field();
		input.setText(AltosUIPreferences.callsign());
		input.setColumns(22);
		AltosDesktopTheme.styleField(input);
		form.add(input, BorderLayout.CENTER);
		int result = JOptionPane.showConfirmDialog(this, form, "Set callsign",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			String callsign = input.getText().trim();
			if (!callsign.isEmpty())
				AltosUIPreferences.set_callsign(callsign);
			else
				JOptionPane.showMessageDialog(this, "Enter a callsign before saving.",
					"Callsign required", JOptionPane.WARNING_MESSAGE);
		}
	}

	void ConfigureTeleMetrum() { new AltosConfigFC(this); }
	void ConfigureTeleDongle() { new AltosConfigTD(this); }
	void FlashImage() { AltosFlashUI.show(this); }
	void FireIgniter() { new AltosIgniteUI(this); }
	void ScanChannels() { new AltosScanUI(this, true); }
	void LoadMaps() {
		if (maps == null) maps = new AltosModernMapPreload();
		showWorkspace("Maps", maps);
	}
	void LaunchController() { new AltosLaunchUI(this); }
	void ConfigureAltosUI() { new AltosConfigureUI(this, voice); }

	private void IdleMonitor() {
		try { new AltosIdleMonitorUI(this); }
		catch (Exception e) { showError("Cannot monitor idle TeleMega", e); }
	}

	private void Replay() {
		if (monitor != null) {
			JOptionPane.showMessageDialog(this,
				monitorReplay ? "Close the current replay in Live monitor before opening another one." :
				"Use Disconnect in Live monitor before replaying a saved flight. Your receiver remains connected until then.",
				"Monitor session active", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		AltosDataChooser chooser = new AltosDataChooser(this);
		AltosRecordSet set = chooser.runDialog();
		if (set != null)
			openMonitor(new AltosReplayReader(set, chooser.file()), -1);
	}

	private void SaveFlightData() {
		new AltosEepromManage(this, this, AltosLib.product_any);
	}

	private static AltosFlightSeries make_series(AltosRecordSet set) {
		AltosFlightSeries series = new AltosFlightSeries(set.cal_data());
		set.capture_series(series);
		series.finish();
		return series;
	}

	private void ExportData() {
		AltosDataChooser chooser = new AltosDataChooser(this);
		AltosRecordSet set = chooser.runDialog();
		if (set != null)
			new AltosCSVUI(this, make_series(set), chooser.file());
	}

	private void GraphData() {
		AltosDataChooser chooser = new AltosDataChooser(this);
		AltosRecordSet set = chooser.runDialog();
		if (set == null)
			return;
		try { new AltosGraphUI(set, chooser.file()); }
		catch (Exception e) { showError("Cannot graph flight data", e); }
	}

	private void showError(String title, Exception e) {
		JOptionPane.showMessageDialog(this,
			e.getMessage() != null ? e.getMessage() : e.toString(), title, JOptionPane.ERROR_MESSAGE);
	}

	public void graph_flights(AltosEepromList flights) {
		for (AltosEepromLog flight : flights) {
			if (flight.graph_selected && flight.file != null) {
				try { new AltosGraphUI(AltosLib.record_set(flight.file), flight.file); }
				catch (Exception e) { showError("Cannot graph downloaded flight", e); }
			}
		}
	}

	public static void main(final String[] args) {
		if (args.length > 0 && args[0].equals("--help")) {
			System.out.println("AltOS Windows desktop application");
			return;
		}
		if (!load_library(null))
			return;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				AltosDesktopTheme.install();
				new AltosUI();
			}
		});
	}

	private static final class Badge extends JLabel {
		private final Color fill;

		Badge(String text, Color fill) {
			super(text);
			this.fill = fill;
			setFont(AltosDesktopTheme.font(Font.BOLD, 12));
			setForeground(Color.WHITE);
			setOpaque(false);
			setBorder(new EmptyBorder(AltosDesktopTheme.dp(4), AltosDesktopTheme.dp(9), AltosDesktopTheme.dp(4), AltosDesktopTheme.dp(9)));
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(fill);
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
			g.dispose();
			super.paintComponent(graphics);
		}
	}
}
