/* Copyright © 2026; GPL version 2 or later. */
package altosui;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.altusmetrum.altoslib_14.*;
import org.altusmetrum.altosuilib_14.AltosUIMap;

/** An in-window map workspace using the installed AltOS cache and downloader. */
public final class AltosModernMapPreload extends JPanel
        implements AltosLaunchSiteListener, AltosMapLoaderListener {
    private static final int OFFLINE_DETAIL_ZOOM = AltosMap.default_zoom + 2;
    private final JComboBox<Object> sites = AltosDesktopTheme.combo();
    private final JTextField latitude = AltosDesktopTheme.field();
    private final JTextField longitude = AltosDesktopTheme.field();
    private final JButton download = AltosDesktopTheme.button("Save launch-area map offline");
    private final JButton cancel = AltosDesktopTheme.button("Cancel download");
    private final JButton locate = AltosDesktopTheme.button("Show on map");
    private final JLabel message = new JLabel("Choose a launch site or enter coordinates to begin.");
    private final JLabel location = new JLabel("NO LOCATION SELECTED");
    private final JLabel mapSource = new JLabel("MODERN STREETS  ·  ONLINE");
    private final JProgressBar progress = new JProgressBar();
    private final JPanel preview = new JPanel(new CardLayout());
    private AltosUIMap map;
    private AltosModernStreetMap streets;
    private AltosModernStreetMap aerialMap;
    private final JComboBox<String> sources = AltosDesktopTheme.combo(new String[] {
        "Modern streets", "USGS aerial (U.S.)", "AltOS hybrid (offline)"
    });
    private final JButton openFlight = AltosDesktopTheme.button("Open recorded flight");
    private final JButton path3D = AltosDesktopTheme.button("View in 3D");
    private List<AltosFlightPathPanel.Fix> recordedTrack;
    private boolean online = true;
    private boolean aerial;
    private int offlineZoom = 14;
    private volatile AltosMapLoader loader;
    private volatile int activeRequest;
    private boolean closing;

    public void setOpenFlightAction(Runnable action) { openFlight.addActionListener(e -> action.run()); }
    public void set3DAction(Runnable action) { path3D.addActionListener(e -> action.run()); }

    /** Recorded GPS paths always start on USGS aerial, not the older hybrid map. */
    public void showRecordedFlight(List<AltosFlightPathPanel.Fix> fixes, String name) {
        recordedTrack = new java.util.ArrayList<AltosFlightPathPanel.Fix>(fixes);
        if (recordedTrack.isEmpty()) {
            message.setForeground(AltosDesktopTheme.WARNING);
            message.setText("No valid GPS coordinates in " + name + "; a map route cannot be plotted.");
            return;
        }
        AltosFlightPathPanel.Fix first = recordedTrack.get(0);
        sites.setSelectedIndex(0);
        latitude.setText(String.format(Locale.US, "%.6f", first.lat));
        longitude.setText(String.format(Locale.US, "%.6f", first.lon));
        if (sources.getSelectedIndex() != 1) sources.setSelectedIndex(1);
        else selectSource(1);
        aerialMap.showFlight(recordedTrack);
        if (streets != null) streets.showFlight(recordedTrack);
        mapSource.setText("RECORDED FLIGHT  /  USGS AERIAL");
        message.setForeground(AltosDesktopTheme.MUTED_TEXT);
        message.setText(name + "  ·  " + recordedTrack.size() + " GPS fixes  ·  imagery year varies by location");
    }

    public void selectRecordedFix(int index) {
        if (aerialMap != null) aerialMap.selectFlightFix(index);
        if (streets != null) streets.selectFlightFix(index);
    }

    private static JLabel text(String value, int size, boolean bold, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(AltosDesktopTheme.font(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private static JPanel stack() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static JPanel field(String caption, JComponent control, String help) {
        JPanel row = stack();
        JLabel heading = text(caption, 12, true, AltosDesktopTheme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(heading);
        row.add(Box.createVerticalStrut(AltosDesktopTheme.dp(7)));
        row.add(control);
        if (help != null) {
            row.add(Box.createVerticalStrut(AltosDesktopTheme.dp(5)));
            JLabel hint = text(help, 11, false, AltosDesktopTheme.MUTED_TEXT);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(hint);
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static void input(JComponent component) {
        component.setFont(AltosDesktopTheme.font(Font.PLAIN, 13));
        component.setForeground(AltosDesktopTheme.TEXT);
        component.setBackground(AltosDesktopTheme.SURFACE_DARK);
        component.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
            new EmptyBorder(AltosDesktopTheme.dp(5), AltosDesktopTheme.dp(9),
                AltosDesktopTheme.dp(5), AltosDesktopTheme.dp(9))));
        if (component instanceof JTextField) {
            JTextField field = (JTextField) component;
            field.setCaretColor(AltosDesktopTheme.TEXT);
            field.setSelectionColor(AltosDesktopTheme.ACCENT_DARK);
            field.setSelectedTextColor(AltosDesktopTheme.TEXT);
        }
        component.setPreferredSize(new Dimension(AltosDesktopTheme.dp(185), AltosDesktopTheme.dp(37)));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, AltosDesktopTheme.dp(37)));
    }

    private void previewLocation() {
        double[] coordinates;
        try {
            coordinates = coordinates();
        } catch (IllegalArgumentException problem) {
            message.setForeground(AltosDesktopTheme.DANGER);
            message.setText(problem.getMessage());
            return;
        }
        if (online) {
            if (aerial && aerialMap == null) {
                aerialMap = new AltosModernStreetMap(true);
                preview.add(aerialMap, "aerial");
            } else if (!aerial && streets == null) {
                streets = new AltosModernStreetMap();
                preview.add(streets, "streets");
            }
            (aerial ? aerialMap : streets).center(coordinates[0], coordinates[1]);
            if (recordedTrack != null && !recordedTrack.isEmpty() &&
                (aerial ? aerialMap : streets).getTrackSize() == 0)
                (aerial ? aerialMap : streets).showFlight(recordedTrack);
            ((CardLayout) preview.getLayout()).show(preview, aerial ? "aerial" : "streets");
        } else {
            if (map == null) {
                map = new AltosUIMap() {
                    @Override public void set_zoom_label(String text) {
                        super.set_zoom_label(text);
                        if (AltosModernMapPreload.this.map != this || online || !text.startsWith("Zoom ")) return;
                        try {
                            int level = AltosMap.default_zoom + Integer.parseInt(text.substring(5).trim());
                            if (level > OFFLINE_DETAIL_ZOOM) {
                                super.set_zoom(OFFLINE_DETAIL_ZOOM);
                                SwingUtilities.invokeLater(() -> switchToAerial(level));
                            } else offlineZoom = level;
                        } catch (NumberFormatException ignored) { }
                    }
                };
                map.set_zoom(offlineZoom);
                preview.add(map, "hybrid");
            }
            AltosGPS gps = new AltosGPS();
            gps.lat = coordinates[0];
            gps.lon = coordinates[1];
            gps.locked = true;
            map.clear_marks();
            map.centre(gps);
            map.add_mark(gps.lat, gps.lon, 7, "Launch site");
            ((CardLayout) preview.getLayout()).show(preview, "hybrid");
        }
        location.setText(String.format(Locale.US, "%.5f°  /  %.5f°", coordinates[0], coordinates[1]));
        message.setForeground(AltosDesktopTheme.MUTED_TEXT);
        message.setText(online ? (aerial ? "USGS aerial imagery covers the continental U.S.; image year varies by site."
                                      : "Modern streets load online as you pan and zoom. Offline tiles use AltOS hybrid imagery.")
                               : "AltOS hybrid view. Save the launch area for offline use.");
    }

    static double[] parseCoordinates(String lat, String lon) {
        try {
            double north = Double.parseDouble(lat.trim());
            double east = Double.parseDouble(lon.trim());
            if (!Double.isFinite(north) || !Double.isFinite(east)
                    || north < -85 || north > 85 || east < -180 || east > 180)
                throw new NumberFormatException();
            return new double[] { north, east };
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Enter valid decimal coordinates (latitude -85 to 85, longitude -180 to 180).");
        }
    }

    private double[] coordinates() {
        return parseCoordinates(latitude.getText(), longitude.getText());
    }

    private void setWorking(boolean working) {
        sites.setEnabled(!working);
        latitude.setEnabled(!working);
        longitude.setEnabled(!working);
        locate.setEnabled(!working);
        download.setEnabled(!working);
        cancel.setEnabled(working);
        cancel.setVisible(working);
        progress.setVisible(working);
    }

    private void startDownload() {
        double[] position;
        try {
            position = coordinates();
        } catch (IllegalArgumentException problem) {
            message.setForeground(AltosDesktopTheme.DANGER);
            message.setText(problem.getMessage());
            return;
        }
        if (loader != null)
            return;
        previewLocation();
        // Automatic practical launch-area range. Manual browsing can zoom independently.
        double radiusMeters = 3000;
        int[] zoom = { -3, 2 };
        setWorking(true);
        progress.setIndeterminate(true);
        message.setForeground(AltosDesktopTheme.MUTED_TEXT);
        message.setText("Preparing hybrid map tiles…");
        // The bundled 1.9.22 downloader currently caches hybrid tiles only.
        final int request = ++activeRequest;
        AltosMapLoaderListener listener = new AltosMapLoaderListener() {
            public void loader_start(int total) {
                if (request == activeRequest) AltosModernMapPreload.this.loader_start(total);
            }
            public void loader_notify(int current, int total, String file) {
                if (request == activeRequest) AltosModernMapPreload.this.loader_notify(current, total, file);
            }
            public void loader_done(int total) {
                if (request == activeRequest) AltosModernMapPreload.this.loader_done(total);
            }
            public void debug(String format, Object... args) { }
        };
        try {
            loader = new AltosMapLoader(listener, position[0], position[1], zoom[0], zoom[1],
                                        radiusMeters, 1 << AltosMap.maptype_hybrid, 1);
        } catch (RuntimeException error) {
            ++activeRequest;
            setWorking(false);
            progress.setIndeterminate(false);
            message.setForeground(AltosDesktopTheme.DANGER);
            message.setText("Could not start the download: " + error.getMessage());
        }
    }

    private void stopDownload() {
        AltosMapLoader running = loader;
        if (running != null) {
            ++activeRequest;
            running.abort();
            loader = null;
            setWorking(false);
            progress.setIndeterminate(false);
            message.setText("Download stopped. Cached tiles are kept.");
        }
    }

    private void selectSite() {
        Object selected = sites.getSelectedItem();
        if (selected instanceof AltosLaunchSite) {
            AltosLaunchSite site = (AltosLaunchSite) selected;
            latitude.setText(String.format(Locale.US, "%.6f", site.latitude));
            longitude.setText(String.format(Locale.US, "%.6f", site.longitude));
            previewLocation();
        }
    }

    public void notify_launch_sites(final List<AltosLaunchSite> list) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (closing) return;
                Object selected = sites.getSelectedItem();
                sites.removeAllItems();
                sites.addItem("Enter coordinates manually");
                if (list != null) {
                    for (AltosLaunchSite site : list)
                        sites.addItem(site);
                }
                if (selected instanceof AltosLaunchSite)
                    sites.setSelectedItem(selected);
                else
                    sites.setSelectedIndex(0);
                sites.setToolTipText(list == null || list.isEmpty()
                    ? "Site list unavailable; enter coordinates manually." : "Select a saved launch site.");
                if ((list == null || list.isEmpty()) && latitude.getText().trim().isEmpty())
                    message.setText("Site list unavailable. Enter decimal coordinates manually.");
            }
        });
    }

    public void loader_start(final int count) {
        final int request = activeRequest;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (closing || request != activeRequest || !cancel.isEnabled()) return;
                progress.setIndeterminate(false);
                progress.setMaximum(Math.max(1, count));
                progress.setValue(0);
                message.setText("Preparing " + count + " map tiles…");
            }
        });
    }

    public void loader_notify(final int current, final int total, String filename) {
        final int request = activeRequest;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (closing || request != activeRequest || !cancel.isEnabled()) return;
                progress.setIndeterminate(false);
                progress.setMaximum(Math.max(1, total));
                progress.setValue(Math.min(current, total));
                message.setText("Processing hybrid map tiles  ·  " + current + " / " + total);
            }
        });
    }

    public void loader_done(final int total) {
        final int request = activeRequest;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (closing || request != activeRequest || !cancel.isEnabled()) return;
                loader = null;
                setWorking(false);
                progress.setIndeterminate(false);
                progress.setValue(progress.getMaximum());
                message.setText("Finished processing " + total + " tiles. Check the preview for any missing imagery.");
            }
        });
    }

    public void debug(String format, Object... args) {
        // AltOS reports map tile progress through the other listener methods.
    }

    public void disposeWorkspace() {
        closing = true;
        stopDownload();
        if (streets != null) streets.close();
        if (aerialMap != null) aerialMap.close();
    }

    private void selectSource(int source) {
        online = source != 2;
        aerial = source == 1;
        mapSource.setText(online ? (aerial ? "USGS AERIAL  ·  ONLINE" : "MODERN STREETS  ·  ONLINE")
                                 : "ALTOS HYBRID  ·  OFFLINE CACHE");
        if (latitude.getText().trim().isEmpty() || longitude.getText().trim().isEmpty())
            ((CardLayout)preview.getLayout()).show(preview, "empty");
        else previewLocation();
    }

    private void zoom(int direction) {
        if (online) {
            AltosModernStreetMap active = aerial ? aerialMap : streets;
            if (active != null) active.changeZoom(direction);
        } else if (map != null) {
            if (offlineZoom + direction > OFFLINE_DETAIL_ZOOM) {
                switchToAerial(offlineZoom + direction);
                return;
            }
            offlineZoom = Math.max(AltosMap.min_zoom, Math.min(AltosMap.max_zoom, offlineZoom + direction));
            map.set_zoom(offlineZoom);
        }
    }

    private void switchToAerial(int level) {
        if (closing || online) return;
        sources.setSelectedIndex(1);
        if (aerialMap != null) aerialMap.setZoom(level);
        message.setForeground(AltosDesktopTheme.MUTED_TEXT);
        message.setText("Offline hybrid detail ends at Zoom +2. Showing USGS aerial for closer zoom.");
    }

    public AltosModernMapPreload() {
        setBackground(AltosDesktopTheme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel heading = stack();
        heading.setBorder(new EmptyBorder(AltosDesktopTheme.dp(24), AltosDesktopTheme.dp(28), AltosDesktopTheme.dp(18), AltosDesktopTheme.dp(28)));
        heading.add(text("GROUND STATION / MAPS", 11, true, AltosDesktopTheme.ACCENT_LIGHT));
        heading.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
        heading.add(text("Maps and flight sites", 27, true, AltosDesktopTheme.TEXT));
        heading.add(Box.createVerticalStrut(AltosDesktopTheme.dp(5)));
        heading.add(text("View online streets or U.S. aerial imagery; save AltOS hybrid tiles for offline use.",
                         13, false, AltosDesktopTheme.MUTED_TEXT));
        add(heading, BorderLayout.NORTH);

        JPanel form = stack();
        form.setBackground(AltosDesktopTheme.SURFACE);
        form.setOpaque(true);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AltosDesktopTheme.BORDER),
            new EmptyBorder(AltosDesktopTheme.dp(11), AltosDesktopTheme.dp(17), AltosDesktopTheme.dp(10), AltosDesktopTheme.dp(17))));
        form.add(text("01  LOCATION", 11, true, AltosDesktopTheme.ACCENT_LIGHT));
        form.add(Box.createVerticalStrut(AltosDesktopTheme.dp(8)));
        sites.addItem("Enter coordinates manually");
        input(sites);
        sites.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { selectSite(); }
        });
        form.add(field("Launch site", sites, "Choose a site or enter decimal coordinates."));
        form.add(Box.createVerticalStrut(AltosDesktopTheme.dp(10)));
        input(latitude);
        input(longitude);
        latitude.setToolTipText("Latitude in decimal degrees, e.g. 37.167833");
        longitude.setToolTipText("Longitude in decimal degrees, e.g. -119.824000");
        JPanel coordinates = new JPanel(new GridLayout(1, 2, 10, 0));
        coordinates.setOpaque(false);
        coordinates.setAlignmentX(Component.LEFT_ALIGNMENT);
        coordinates.add(field("Latitude", latitude, "North + / South −"));
        coordinates.add(field("Longitude", longitude, "East + / West −"));
        coordinates.setMaximumSize(new Dimension(Integer.MAX_VALUE, coordinates.getPreferredSize().height));
        form.add(coordinates);
        form.add(Box.createVerticalStrut(AltosDesktopTheme.dp(8)));
        AltosDesktopTheme.styleSecondary(locate);
        locate.setAlignmentX(Component.LEFT_ALIGNMENT);
        locate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { previewLocation(); }
        });
        latitude.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { previewLocation(); }
        });
        longitude.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { previewLocation(); }
        });
        form.add(locate);
        form.add(Box.createVerticalStrut(AltosDesktopTheme.dp(8)));
        form.add(text("02  OFFLINE MAP", 11, true, AltosDesktopTheme.ACCENT_LIGHT));
        form.add(Box.createVerticalStrut(AltosDesktopTheme.dp(8)));
        JLabel offlineHint = text("3 km around your launch site.", 11, false, AltosDesktopTheme.MUTED_TEXT);
        offlineHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(offlineHint);
        form.add(Box.createVerticalStrut(AltosDesktopTheme.dp(4)));
        form.add(text("Zoom range selected automatically.", 11, false, AltosDesktopTheme.MUTED_TEXT));
        form.add(Box.createVerticalStrut(AltosDesktopTheme.dp(8)));
        AltosDesktopTheme.stylePrimary(download);
        download.setAlignmentX(Component.LEFT_ALIGNMENT);
        download.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { startDownload(); }
        });
        form.add(download);
		form.setPreferredSize(new Dimension(AltosDesktopTheme.dp(322), form.getPreferredSize().height));
        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(28), AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(28)));
        JScrollPane controls = AltosDesktopTheme.scroll(form);
        controls.setBorder(BorderFactory.createEmptyBorder());
        controls.setPreferredSize(new Dimension(AltosDesktopTheme.dp(327), AltosDesktopTheme.dp(405)));
        controls.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controls.getVerticalScrollBar().setUnitIncrement(18);
        body.add(controls, BorderLayout.WEST);

        JPanel mapCard = new JPanel(new BorderLayout());
        mapCard.setBackground(AltosDesktopTheme.SURFACE);
        mapCard.setBorder(BorderFactory.createLineBorder(AltosDesktopTheme.BORDER));
        JPanel mapHeading = new JPanel(new BorderLayout());
        mapHeading.setBackground(AltosDesktopTheme.SURFACE);
        mapHeading.setBorder(new EmptyBorder(AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(16)));
        mapSource.setFont(AltosDesktopTheme.font(Font.BOLD, 11));
        mapSource.setForeground(AltosDesktopTheme.MUTED_TEXT);
        mapHeading.add(mapSource, BorderLayout.WEST);
        location.setFont(AltosDesktopTheme.font(Font.PLAIN, 11));
        location.setForeground(AltosDesktopTheme.MUTED_TEXT);
        mapHeading.add(location, BorderLayout.EAST);
        JPanel mapToolbar = new JPanel(new BorderLayout());
        mapToolbar.setBackground(AltosDesktopTheme.SURFACE);
        mapToolbar.add(mapHeading, BorderLayout.NORTH);
        JPanel switches = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        switches.setOpaque(false);
        switches.setBorder(new EmptyBorder(AltosDesktopTheme.dp(0), AltosDesktopTheme.dp(10), AltosDesktopTheme.dp(10), AltosDesktopTheme.dp(10)));
        input(sources);
        sources.setPreferredSize(new Dimension(AltosDesktopTheme.dp(200), AltosDesktopTheme.dp(35)));
        sources.addActionListener(e -> selectSource(sources.getSelectedIndex()));
        JButton plus = AltosDesktopTheme.button("+");
        JButton minus = AltosDesktopTheme.button("-");
        for (JButton button : new JButton[] { minus, plus })
            AltosDesktopTheme.styleSecondary(button);
        for (JButton button : new JButton[] { minus, plus }) {
            button.setBorder(new EmptyBorder(AltosDesktopTheme.dp(5), AltosDesktopTheme.dp(7),
                AltosDesktopTheme.dp(5), AltosDesktopTheme.dp(7)));
            button.setPreferredSize(new Dimension(AltosDesktopTheme.dp(36), AltosDesktopTheme.dp(33)));
        }
        minus.setToolTipText("Zoom out");
        plus.setToolTipText("Zoom in");
        minus.addActionListener(e -> zoom(-1));
        plus.addActionListener(e -> zoom(1));
        switches.add(sources);
        switches.add(minus);
        switches.add(plus);
        AltosDesktopTheme.styleSecondary(openFlight);
        AltosDesktopTheme.styleSecondary(path3D);
        switches.add(openFlight);
        switches.add(path3D);
        mapToolbar.add(switches, BorderLayout.SOUTH);
        mapCard.add(mapToolbar, BorderLayout.NORTH);
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(AltosDesktopTheme.SURFACE_DARK);
        JPanel emptyText = stack();
        emptyText.add(text("Your launch site appears here", 18, true, AltosDesktopTheme.TEXT));
        emptyText.add(Box.createVerticalStrut(AltosDesktopTheme.dp(7)));
        emptyText.add(text("Select a site or enter latitude and longitude.", 12, false, AltosDesktopTheme.MUTED_TEXT));
        empty.add(emptyText);
        preview.add(empty, "empty");
        mapCard.add(preview, BorderLayout.CENTER);
        body.add(mapCard, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(15, 0));
        footer.setBackground(AltosDesktopTheme.BACKGROUND);
        footer.setBorder(new EmptyBorder(AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(28), AltosDesktopTheme.dp(22), AltosDesktopTheme.dp(28)));
        message.setForeground(AltosDesktopTheme.MUTED_TEXT);
        progress.setPreferredSize(new Dimension(AltosDesktopTheme.dp(180), AltosDesktopTheme.dp(10)));
        progress.setValue(0);
        cancel.setEnabled(false);
        AltosDesktopTheme.styleSecondary(cancel);
        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { stopDownload(); }
        });
        footer.add(message, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(progress);
        right.add(cancel);
        cancel.setVisible(false);
        progress.setVisible(false);
        footer.add(right, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        new AltosLaunchSites(this);
    }
}
