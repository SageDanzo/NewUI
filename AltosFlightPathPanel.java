/* Copyright © 2026; GPL version 2 or later. */
package altosui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.altusmetrum.altoslib_14.*;

/** GPS-only, rotatable three-dimensional trajectory. Never extrapolates missing fixes. */
public final class AltosFlightPathPanel extends JPanel {
    static final class Fix {
        final double time, lat, lon, altitude, east, north, up;
        Fix(double time, double lat, double lon, double altitude, double east, double north, double up) {
            this.time = time;
            this.lat = lat;
            this.lon = lon;
            this.altitude = altitude;
            this.east = east;
            this.north = north;
            this.up = up;
        }
    }

    private final List<Fix> fixes = new ArrayList<Fix>();
    private final PathCanvas canvas = new PathCanvas();
    private final JSlider timeline = AltosDesktopTheme.slider();
    private final JLabel summary = label("No GPS flight selected", 15, true, AltosDesktopTheme.TEXT);
    private final JLabel detail = label("Open a recorded TeleMega flight, or connect for live GPS fixes.", 12, false, AltosDesktopTheme.MUTED_TEXT);
    private final JLabel position = label("WAITING FOR GPS", 11, true, AltosDesktopTheme.ACCENT_LIGHT);
    private final JButton play = AltosDesktopTheme.button("Play flight");
    private final JButton live = AltosDesktopTheme.button("Follow latest");
    private final JButton open = AltosDesktopTheme.button("Open recorded flight");
    private final JButton connect = AltosDesktopTheme.button("Connect receiver");
    private final JButton map = AltosDesktopTheme.button("Show on USGS map");
    private final JButton reset = AltosDesktopTheme.button("Reset view");
    private final JButton topView = AltosDesktopTheme.button("Top view");
    private final JButton sideView = AltosDesktopTheme.button("Side view");
    private final JComboBox<String> ground = AltosDesktopTheme.combo(new String[] { "Grid", "USGS aerial" });
    private final JComboBox<String> speed = AltosDesktopTheme.combo(new String[] { "1×", "5×", "20×" });
    private final Timer timer;
    private boolean following = true;
    private boolean adjusting;
    private double originLat, originLon, originAlt;
    private double[] originECEF;
    private double maxUp, maxAbsUp, maxRange;
    private double playbackTime;
    private long previousTick;
    private Runnable selectionListener;
    private AltosModernStreetMap groundMap;

    private static JLabel label(String value, int size, boolean bold, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(AltosDesktopTheme.font(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    static boolean usable(AltosGPS gps) {
        // Some older GPS records report a valid lock and coordinates without
        // including the satellite count (zero means unspecified in that form).
        return gps != null && gps.locked && (gps.nsat == 0 || gps.nsat >= 4)
            && Double.isFinite(gps.lat) && Double.isFinite(gps.lon) && Double.isFinite(gps.alt)
            && Math.abs(gps.lat) <= 85 && Math.abs(gps.lon) <= 180
            && gps.alt > -1000 && gps.alt < 100000;
    }

    static List<AltosGPSTimeValue> recorded(AltosRecordSet record) {
        AltosFlightSeries series = new AltosFlightSeries(record.cal_data());
        record.capture_series(series);
        series.finish();
        return series.gps_series == null ? new ArrayList<AltosGPSTimeValue>() :
               new ArrayList<AltosGPSTimeValue>(series.gps_series);
    }

    /** WGS84 Earth-centred coordinates, transformed into east/north/up at the first fix. */
    static double[] ecef(double lat, double lon, double altitude) {
        double phi = Math.toRadians(lat), lambda = Math.toRadians(lon);
        double sinPhi = Math.sin(phi), cosPhi = Math.cos(phi);
        double radius = 6378137.0 / Math.sqrt(1 - 0.00669437999014 * sinPhi * sinPhi);
        return new double[] {
            (radius + altitude) * cosPhi * Math.cos(lambda),
            (radius + altitude) * cosPhi * Math.sin(lambda),
            (radius * (1 - 0.00669437999014) + altitude) * sinPhi
        };
    }

    static double[] relativeENU(double originLat, double originLon, double originAltitude,
                                double latitude, double longitude, double altitude) {
        double[] a = ecef(originLat, originLon, originAltitude);
        double[] b = ecef(latitude, longitude, altitude);
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double phi = Math.toRadians(originLat), lambda = Math.toRadians(originLon);
        double sp = Math.sin(phi), cp = Math.cos(phi), sl = Math.sin(lambda), cl = Math.cos(lambda);
        return new double[] {
            -sl * dx + cl * dy,
            -sp * cl * dx - sp * sl * dy + cp * dz,
            cp * cl * dx + cp * sl * dy + sp * dz
        };
    }

    List<Fix> track() { return new ArrayList<Fix>(fixes); }
    int selectedFix() { return Math.min(timeline.getValue(), fixes.size() - 1); }
    void setSelectionListener(Runnable listener) { selectionListener = listener; }
    void setMapAction(Runnable action) { map.addActionListener(e -> action.run()); }

    static boolean continuous(Fix previous, Fix next) {
        // Recorded data cannot establish a route across a long reception gap.
        return next.time > previous.time && next.time - previous.time <= 30.0;
    }

    private void add(double time, AltosGPS gps, boolean updateUI) {
        if (!usable(gps) || !Double.isFinite(time)) return;
        if (fixes.isEmpty()) {
            originLat = gps.lat;
            originLon = gps.lon;
            originAlt = gps.alt;
            originECEF = ecef(originLat, originLon, originAlt);
        } else {
            Fix last = fixes.get(fixes.size() - 1);
            // The same fix is often rebroadcast in multiple telemetry packets.
            if (last.lat == gps.lat && last.lon == gps.lon && last.altitude == gps.alt) return;
            if (time < last.time) return;
        }
        double phi = Math.toRadians(originLat), lambda = Math.toRadians(originLon);
        double sp = Math.sin(phi), cp = Math.cos(phi), sl = Math.sin(lambda), cl = Math.cos(lambda);
        double[] point = ecef(gps.lat, gps.lon, gps.alt);
        double dx = point[0] - originECEF[0], dy = point[1] - originECEF[1], dz = point[2] - originECEF[2];
        double east = -sl * dx + cl * dy;
        double north = -sp * cl * dx - sp * sl * dy + cp * dz;
        double up = cp * cl * dx + cp * sl * dy + sp * dz;
        if (!Double.isFinite(north) || !Double.isFinite(east) || !Double.isFinite(up)) return;
        fixes.add(new Fix(time, gps.lat, gps.lon, gps.alt, east, north, up));
        maxUp = Math.max(maxUp, up);
        maxAbsUp = Math.max(maxAbsUp, Math.abs(up));
        maxRange = Math.max(maxRange, Math.hypot(east, north));
        if (updateUI) {
            boolean update = following || timeline.getMaximum() == 0;
            timeline.setMaximum(Math.max(0, fixes.size() - 1));
            if (update) {
                adjusting = true;
                timeline.setValue(fixes.size() - 1);
                adjusting = false;
            }
            updateReadout();
            canvas.repaint();
        }
    }

    public void addLive(final AltosState state) {
        if (state == null || !usable(state.gps)) return;
        final AltosGPS gps = new AltosGPS(state.gps);
        final double time = state.time();
        if (SwingUtilities.isEventDispatchThread()) add(time, gps, true);
        else SwingUtilities.invokeLater(new Runnable() {
            public void run() { add(time, gps, true); }
        });
    }

    public void loadRecorded(List<AltosGPSTimeValue> values, String name) {
        timer.stop();
        closeGroundMap();
        play.setText("Play flight");
        fixes.clear();
        maxUp = maxAbsUp = maxRange = 0;
        following = true;
        timeline.setMaximum(0);
        timeline.setValue(0);
        if (values != null) {
            for (AltosGPSTimeValue item : values)
                if (item != null) add(item.time, item.gps, false);
        }
        adjusting = true;
        timeline.setMaximum(Math.max(0, fixes.size() - 1));
        timeline.setValue(Math.max(0, fixes.size() - 1));
        adjusting = false;
        summary.setText(name);
        detail.setText(fixes.isEmpty()
            ? "No usable GPS fixes in this recording. A 3D track requires GPS telemetry."
            : String.format(Locale.US, "%d GPS fixes  ·  Peak %.0f m above first fix  ·  %.0f m from first fix",
                            fixes.size(), maxUp, maxRange));
        updateReadout();
        canvas.repaint();
    }

    public void startLive() {
        timer.stop();
        closeGroundMap();
        play.setText("Play flight");
        fixes.clear();
        maxUp = maxAbsUp = maxRange = 0;
        following = true;
        timeline.setMaximum(0);
        timeline.setValue(0);
        summary.setText("Live TeleMega flight");
        detail.setText("Waiting for a valid GPS fix. The track follows received coordinates only.");
        position.setText("WAITING FOR GPS");
        canvas.repaint();
    }

    public void setOpenAction(Runnable action) {
        open.addActionListener(e -> action.run());
    }

    public void setConnectAction(Runnable action) {
        connect.addActionListener(e -> action.run());
    }

    public void stop() { timer.stop(); closeGroundMap(); }

    private void closeGroundMap() {
        if (groundMap != null) groundMap.close();
        groundMap = null;
        canvas.clearGroundImage();
    }

    private void updateReadout() {
        if (fixes.isEmpty()) {
            position.setText("WAITING FOR GPS");
            return;
        }
        int index = Math.min(Math.max(0, timeline.getValue()), fixes.size() - 1);
        Fix selected = fixes.get(index);
        position.setText(String.format(Locale.US,
            "FIX %d/%d  ·  T+%.1f s  ·  %.6f°, %.6f°  ·  GPS %.0f m MSL  ·  ΔHEIGHT %+.0f m",
            index + 1, fixes.size(), selected.time - fixes.get(0).time,
            selected.lat, selected.lon, selected.altitude, selected.up));
        if ("Live TeleMega flight".equals(summary.getText()))
            detail.setText(fixes.size() + " valid GPS fixes received · height relative to first fix");
        canvas.repaint();
        if (selectionListener != null) selectionListener.run();
    }

    public AltosFlightPathPanel() {
        super(new BorderLayout(0, 14));
        setBackground(AltosDesktopTheme.BACKGROUND);
        setBorder(new EmptyBorder(AltosDesktopTheme.dp(22), AltosDesktopTheme.dp(28), AltosDesktopTheme.dp(20), AltosDesktopTheme.dp(28)));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label("FLIGHT ANALYSIS / GPS", 11, true, AltosDesktopTheme.ACCENT_LIGHT));
        text.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
        text.add(label("3D flight path", 27, true, AltosDesktopTheme.TEXT));
        text.add(Box.createVerticalStrut(AltosDesktopTheme.dp(6)));
        text.add(label("Drag to orbit · scroll to zoom · click a fix · use the timeline to inspect GPS data.", 12, false,
                       AltosDesktopTheme.MUTED_TEXT));
        top.add(text, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        AltosDesktopTheme.styleSecondary(open);
        AltosDesktopTheme.stylePrimary(connect);
        AltosDesktopTheme.styleSecondary(map);
        actions.add(open);
        actions.add(map);
        actions.add(connect);
        top.add(actions, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel plot = new JPanel(new BorderLayout(0, 10));
        plot.setBackground(AltosDesktopTheme.SURFACE);
        plot.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AltosDesktopTheme.BORDER), new EmptyBorder(AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(16), AltosDesktopTheme.dp(14), AltosDesktopTheme.dp(16))));
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(summary);
        info.add(Box.createVerticalStrut(AltosDesktopTheme.dp(5)));
        info.add(detail);
        plot.add(info, BorderLayout.NORTH);
        canvas.setBackground(AltosDesktopTheme.TOPBAR);
        plot.add(canvas, BorderLayout.CENTER);
        add(plot, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 8));
        bottom.setOpaque(false);
        bottom.add(position, BorderLayout.NORTH);
        JPanel transport = new JPanel(new BorderLayout(12, 0));
        transport.setOpaque(false);
        AltosDesktopTheme.styleSecondary(play);
        AltosDesktopTheme.styleSecondary(live);
        JPanel playback = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        playback.setOpaque(false);
        for (JButton view : new JButton[] { reset, topView, sideView })
            AltosDesktopTheme.styleSecondary(view);
        speed.setToolTipText("Replay at the actual time between GPS fixes, multiplied by this speed");
        playback.add(play);
        playback.add(speed);
        playback.add(reset);
        playback.add(topView);
        playback.add(sideView);
        ground.setToolTipText("Switch the flat ground plane between the grid and USGS aerial at the first GPS fix");
        playback.add(label("GROUND", 11, true, AltosDesktopTheme.MUTED_TEXT));
        playback.add(ground);
        transport.add(playback, BorderLayout.NORTH);
        transport.add(timeline, BorderLayout.CENTER);
        transport.add(live, BorderLayout.EAST);
        bottom.add(transport, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        timeline.setMaximum(0);
        timer = new Timer(50, e -> {
            if (fixes.isEmpty() || timeline.getValue() >= fixes.size() - 1) {
                ((Timer)e.getSource()).stop(); play.setText("Play flight"); return;
            }
            long now = System.nanoTime();
            double elapsed = Math.min(0.5, (now - previousTick) / 1e9);
            previousTick = now;
            playbackTime += elapsed * new double[] { 1, 5, 20 }[speed.getSelectedIndex()];
            int index = timeline.getValue();
            while (index + 1 < fixes.size() && fixes.get(index + 1).time <= playbackTime)
                index++;
            if (index != timeline.getValue()) timeline.setValue(index);
        });
        timeline.addChangeListener(e -> {
            if (!adjusting && timeline.getValueIsAdjusting()) {
                following = false;
                if (timer.isRunning() && !fixes.isEmpty()) {
                    playbackTime = fixes.get(timeline.getValue()).time;
                    previousTick = System.nanoTime();
                }
            }
            updateReadout();
        });
        play.addActionListener(e -> {
            if (fixes.isEmpty()) return;
            if (timer.isRunning()) { timer.stop(); play.setText("Play flight"); }
            else {
                if (timeline.getValue() >= fixes.size() - 1) timeline.setValue(0);
                following = false;
                playbackTime = fixes.get(timeline.getValue()).time;
                previousTick = System.nanoTime();
                timer.start(); play.setText("Pause");
            }
        });
        live.addActionListener(e -> {
            timer.stop(); play.setText("Play flight");
            following = true;
            timeline.setValue(Math.max(0, fixes.size() - 1));
        });
        reset.addActionListener(e -> canvas.resetView());
        topView.addActionListener(e -> canvas.setView(0, 1.35));
        sideView.addActionListener(e -> canvas.setView(-0.7, 0.25));
        ground.addActionListener(e -> {
            if (ground.getSelectedIndex() == 0) closeGroundMap();
            canvas.repaint();
        });
    }

    private final class PathCanvas extends JPanel {
        private static final int GROUND_SIZE = 512;
        private double yaw = -0.7, elevation = 0.52, zoom = 1;
        private Point drag;
        private BufferedImage groundImage;
        private String groundStatus = "";

        void clearGroundImage() { groundImage = null; groundStatus = ""; repaint(); }

        void setView(double angle, double tilt) {
            yaw = angle;
            elevation = tilt;
            repaint();
        }

        void resetView() { yaw = -0.7; elevation = 0.52; zoom = 1; repaint(); }

        PathCanvas() {
            setPreferredSize(new Dimension(AltosDesktopTheme.dp(650), AltosDesktopTheme.dp(340)));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { drag = e.getPoint(); requestFocusInWindow(); }
                public void mouseReleased(MouseEvent e) { drag = null; }
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) { resetView(); return; }
                    if (fixes.isEmpty()) return;
                    int nearest = -1;
                    double distance = Math.pow(AltosDesktopTheme.dp(20), 2);
                    double scale = plotScale(getWidth(), getHeight());
                    for (int i = 0; i < fixes.size(); i++) {
                        Fix fix = fixes.get(i);
                        Point2D.Double point = project(fix.east, fix.north, fix.up, scale,
                            getWidth() * .5, getHeight() * .64);
                        double squared = point.distanceSq(e.getPoint());
                        if (squared < distance) { nearest = i; distance = squared; }
                    }
                    if (nearest >= 0) { following = false; timeline.setValue(nearest); }
                    if (nearest >= 0) { timer.stop(); play.setText("Play flight"); }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    if (drag == null) return;
                    yaw += (e.getX() - drag.x) * 0.008;
                    elevation = Math.max(0.08, Math.min(1.35, elevation - (e.getY() - drag.y) * 0.007));
                    drag = e.getPoint();
                    repaint();
                }
            });
            addMouseWheelListener(e -> {
                zoom = Math.max(0.35, Math.min(6, zoom * Math.pow(1.12, -e.getPreciseWheelRotation())));
                repaint();
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "previousFix");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextFix");
            getActionMap().put("previousFix", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { following = false; timeline.setValue(Math.max(0, timeline.getValue() - 1)); }
            });
            getActionMap().put("nextFix", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { following = false; timeline.setValue(Math.min(timeline.getMaximum(), timeline.getValue() + 1)); }
            });
        }

        private double plotScale(int width, int height) {
            double bound = Math.max(80, Math.max(maxRange * 1.3, maxAbsUp * 1.2));
            return Math.min(width * .45, height * .70) / bound * zoom;
        }

        private Point2D.Double project(double east, double north, double up, double scale,
                                        double cx, double cy) {
            double x = Math.cos(yaw) * east - Math.sin(yaw) * north;
            double y = Math.sin(yaw) * east + Math.cos(yaw) * north;
            return new Point2D.Double(cx + x * scale,
                cy - (up * Math.cos(elevation) + y * Math.sin(elevation)) * scale);
        }

        private void segment(Graphics2D g, Point2D.Double a, Point2D.Double b) {
            g.drawLine((int)a.x, (int)a.y, (int)b.x, (int)b.y);
        }

        private double[] groundMetersPerPixel(int level) {
            double latitude = Math.toRadians(originLat);
            double sin = Math.sin(latitude);
            double eccentricity = 0.00669437999014;
            double denominator = 1 - eccentricity * sin * sin;
            double majorRadius = 6378137.0;
            double eastRadius = majorRadius / Math.sqrt(denominator) + originAlt;
            double northRadius = majorRadius * (1 - eccentricity) / Math.pow(denominator, 1.5) + originAlt;
            double radiansPerPixel = 2 * Math.PI * Math.cos(latitude) / (256.0 * (1 << level));
            return new double[] { eastRadius * radiansPerPixel, northRadius * radiansPerPixel };
        }

        /** Paint USGS tiles onto the plane at the first GPS fix, without implying terrain elevation. */
        private boolean paintAerialGround(Graphics2D g, double scale, double cx, double cy) {
            groundStatus = "";
            if (ground.getSelectedIndex() != 1 || fixes.isEmpty()) return false;
            if (originLat < 24 || originLat > 50 || originLon < -126 || originLon > -66) {
                groundStatus = "USGS aerial unavailable at this flight location · grid shown";
                return false;
            }
            int level = 16;
            double[] meters = groundMetersPerPixel(level);
            double needed = Math.max(500, maxRange * 2.8);
            while (level > 0 && GROUND_SIZE * Math.min(meters[0], meters[1]) < needed)
                meters = groundMetersPerPixel(--level);
            if (groundMap == null) {
                groundMap = new AltosModernStreetMap(true);
                groundMap.setImageryOnly(new Runnable() {
                    public void run() { clearGroundImage(); }
                });
                groundMap.setSize(GROUND_SIZE, GROUND_SIZE);
                groundMap.center(originLat, originLon);
                groundMap.setZoom(level);
            } else if (groundMap.getZoom() != level) {
                groundMap.setZoom(level);
                groundImage = null;
            }
            if (groundImage == null) {
                groundImage = new BufferedImage(GROUND_SIZE, GROUND_SIZE, BufferedImage.TYPE_INT_RGB);
                Graphics2D tiles = groundImage.createGraphics();
                groundMap.paint(tiles);
                tiles.dispose();
            }
            if (!groundMap.hasVisibleImagery()) {
                groundStatus = groundMap.imageryDelayed()
                    ? "USGS aerial delayed · grid shown" : "Loading USGS aerial · grid shown";
                return false;
            }
            double east = meters[0] * GROUND_SIZE / 2.0;
            double north = meters[1] * GROUND_SIZE / 2.0;
            Point2D.Double topLeft = project(-east, north, 0, scale, cx, cy);
            Point2D.Double topRight = project(east, north, 0, scale, cx, cy);
            Point2D.Double bottomLeft = project(-east, -north, 0, scale, cx, cy);
            AffineTransform transform = new AffineTransform(
                (topRight.x - topLeft.x) / GROUND_SIZE, (topRight.y - topLeft.y) / GROUND_SIZE,
                (bottomLeft.x - topLeft.x) / GROUND_SIZE, (bottomLeft.y - topLeft.y) / GROUND_SIZE,
                topLeft.x, topLeft.y);
            Graphics2D plane = (Graphics2D) g.create();
            plane.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            plane.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .86f));
            plane.drawImage(groundImage, transform, null);
            plane.dispose();
            return true;
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 40) { g.dispose(); return; }
            if (fixes.isEmpty()) {
                g.setFont(AltosDesktopTheme.font(Font.BOLD, 19));
                g.setColor(AltosDesktopTheme.TEXT);
                String line = "Your flight path will appear here";
                g.drawString(line, (w - g.getFontMetrics().stringWidth(line))/2, h/2);
                g.setFont(AltosDesktopTheme.font(Font.PLAIN, 12));
                g.setColor(AltosDesktopTheme.MUTED_TEXT);
                String hint = "GPS fixes create the east / north / height track";
                g.drawString(hint, (w - g.getFontMetrics().stringWidth(hint))/2, h/2 + 26);
                g.dispose(); return;
            }
            double scale = plotScale(w, h);
            double cx = w * .5, cy = h * .64;
            boolean aerialBase = paintAerialGround(g, scale, cx, cy);
            if (!aerialBase) {
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(0x243443));
                double grid = Math.max(50, maxRange * 1.1);
                for (int i = -2; i <= 2; i++) {
                    double t = grid * i / 2.0;
                    segment(g, project(-grid, t, 0, scale, cx, cy), project(grid, t, 0, scale, cx, cy));
                    segment(g, project(t, -grid, 0, scale, cx, cy), project(t, grid, 0, scale, cx, cy));
                }
            }
            int end = Math.min(fixes.size() - 1, timeline.getValue());
            if (aerialBase) {
                g.setStroke(new BasicStroke(5.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0, 0, 0, 200));
                for (int i = 1; i < fixes.size(); i++) {
                    Fix a = fixes.get(i - 1), b = fixes.get(i);
                    if (continuous(a, b)) segment(g, project(a.east, a.north, a.up, scale, cx, cy),
                        project(b.east, b.north, b.up, scale, cx, cy));
                }
            }
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < fixes.size(); i++) {
                Fix a = fixes.get(i - 1), b = fixes.get(i);
                // Missing telemetry remains a visible break instead of a fabricated straight segment.
                if (!continuous(a, b)) continue;
                g.setColor(i <= end ? (b.up >= a.up ? AltosDesktopTheme.ACCENT_LIGHT : AltosDesktopTheme.ORANGE)
                                    : new Color(0x4D5A67));
                segment(g, project(a.east, a.north, a.up, scale, cx, cy),
                           project(b.east, b.north, b.up, scale, cx, cy));
            }
            Fix selected = fixes.get(end);
            Fix first = fixes.get(0);
            Point2D.Double start = project(first.east, first.north, first.up, scale, cx, cy);
            g.setColor(AltosDesktopTheme.TEXT);
            g.fillOval((int)start.x - 4, (int)start.y - 4, 8, 8);
            if (aerialBase) {
                g.setColor(new Color(0, 0, 0, 230));
                g.drawString("FIRST GPS FIX", (int)start.x + 11, (int)start.y + 16);
            }
            g.setColor(AltosDesktopTheme.TEXT);
            g.drawString("FIRST GPS FIX", (int)start.x + 10, (int)start.y + 15);
            Point2D.Double p = project(selected.east, selected.north, selected.up, scale, cx, cy);
            Point2D.Double base = project(selected.east, selected.north, 0, scale, cx, cy);
            g.setColor(new Color(0x859AAF));
            g.setStroke(new BasicStroke(1));
            segment(g, p, base);
            g.setColor(AltosDesktopTheme.SUCCESS);
            g.fillOval((int)p.x - 6, (int)p.y - 6, 12, 12);
            g.setColor(AltosDesktopTheme.TEXT);
            g.setFont(AltosDesktopTheme.font(Font.BOLD, 12));
            if (aerialBase) {
                g.setColor(new Color(0, 0, 0, 230));
                g.drawString(String.format(Locale.US, "%+.0f m", selected.up), (int)p.x + 12, (int)p.y - 7);
            }
            g.setColor(AltosDesktopTheme.TEXT);
            g.drawString(String.format(Locale.US, "%+.0f m", selected.up), (int)p.x + 11, (int)p.y - 8);
            g.setColor(AltosDesktopTheme.MUTED_TEXT);
            g.setFont(AltosDesktopTheme.font(Font.PLAIN, 11));
            String legend = aerialBase
                ? "USGS AERIAL · FLAT PLANE AT FIRST GPS FIX · BLUE ASCENT · ORANGE DESCENT · GAPS ARE MISSING DATA"
                : "EAST / NORTH / HEIGHT  ·  BLUE ASCENT  ·  ORANGE DESCENT  ·  BREAKS = MISSING DATA";
            if (aerialBase) {
                int legendWidth = Math.min(w - 20, g.getFontMetrics().stringWidth(legend) + 16);
                g.setColor(new Color(9, 17, 25, 235));
                g.fillRoundRect(8, h - 32, legendWidth, 26, 9, 9);
                g.setColor(Color.WHITE);
            }
            g.drawString(groundStatus.isEmpty() ? legend : groundStatus, 16, h - 16);
            g.dispose();
        }
    }
}
