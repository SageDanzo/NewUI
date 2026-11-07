/* Copyright © 2026; GPL version 2 or later. */
package altosui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.altusmetrum.altoslib_14.AltosPreferences;

/** Interactive online map tiles. Never prefetches offline areas. */
public final class AltosModernStreetMap extends JPanel {
    private static final int TILE = 256;
    // The USGS basemap cache serves normal browsing quickly. Closer views
    // need the current NAIP image service, which renders each request.
    private static final int AERIAL_CACHED_MAX_ZOOM = 16;
    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    private static final String USER_AGENT = "AltOS-Windows-Workspace/1.9.22 (+https://github.com/ajtowns/altos)";
    private final Map<String, BufferedImage> images = new LinkedHashMap<String, BufferedImage>(256, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> entry) { return size() > 256; }
    };
    private final Set<String> pending = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ThreadPoolExecutor network = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(24), new ThreadFactory() {
            private int sequence;
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AltOS-street-map-" + (++sequence));
                t.setDaemon(true);
                return t;
            }
        }, new ThreadPoolExecutor.AbortPolicy());
    private boolean imageryOnly;
    private Runnable imageryUpdated;
    private final javax.swing.Timer retryTimer = new javax.swing.Timer(30000, e -> {
        repaint();
        scheduleRetry();
        if (imageryUpdated != null) imageryUpdated.run();
    });
    private final boolean aerial;
    private final File directory;
    private double latitude, longitude;
    private double launchLatitude, launchLongitude;
    private int zoom = 13;
    private boolean centered;
    private Point drag;
    private volatile long retryAfter;
    private volatile long detailRetryAfter;
    private volatile int generation;
    private java.util.List<AltosFlightPathPanel.Fix> flightTrack = Collections.emptyList();
    private int selectedFlightFix = -1;

    public AltosModernStreetMap() { this(false); }

    public AltosModernStreetMap(boolean useAerial) {
        aerial = useAerial;
        directory = new File(AltosPreferences.mapdir(), aerial ? "usgs-aerial" : "osm-streets");
        retryTimer.setRepeats(false);
        setBackground(new Color(0x111A22));
        setToolTipText("Drag to pan · mouse wheel to zoom · tiles viewed online are cached locally");
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { drag = e.getPoint(); }
            public void mouseReleased(MouseEvent e) { drag = null; }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (drag == null || !centered) return;
                double world = TILE * (1 << zoom);
                double x = xForLon(longitude, zoom) - (e.getX() - drag.x);
                double y = yForLat(latitude, zoom) - (e.getY() - drag.y);
                longitude = lonForX(x, zoom);
                latitude = latForY(Math.max(0, Math.min(world - 1, y)), zoom);
                drag = e.getPoint();
                generation++;
                repaint();
            }
        });
        addMouseWheelListener(e -> changeZoom(-e.getWheelRotation()));
    }

    /** Off-screen imagery for the flat ground plane in the 3D view. */
    void setImageryOnly(Runnable listener) {
        imageryOnly = true;
        imageryUpdated = listener;
        setToolTipText(null);
    }

    boolean hasVisibleImagery() {
        if (!centered) return false;
        String key = zoom + "/" + (int)Math.floor(xForLon(longitude, zoom) / TILE)
            + "/" + (int)Math.floor(yForLat(latitude, zoom) / TILE);
        return images.containsKey(key);
    }

    boolean imageryDelayed() { return System.currentTimeMillis() < retryTime(zoom); }

    public void center(double lat, double lon) {
        latitude = Math.max(-85, Math.min(85, lat));
        longitude = lon;
        launchLatitude = latitude;
        launchLongitude = lon;
        if (!flightTrack.isEmpty()) {
            launchLatitude = flightTrack.get(0).lat;
            launchLongitude = flightTrack.get(0).lon;
        }
        centered = true;
        generation++;
        repaint();
    }

    void showFlight(java.util.List<AltosFlightPathPanel.Fix> fixes) {
        flightTrack = new ArrayList<AltosFlightPathPanel.Fix>(fixes);
        selectedFlightFix = flightTrack.size() - 1;
        if (!flightTrack.isEmpty()) {
            AltosFlightPathPanel.Fix first = flightTrack.get(0);
            center(first.lat, first.lon);
            fitFlight();
        }
        repaint();
    }

    int getTrackSize() { return flightTrack.size(); }

    void selectFlightFix(int index) {
        selectedFlightFix = index;
        repaint();
    }

    private void fitFlight() {
        if (flightTrack.isEmpty()) return;
        double firstLon = flightTrack.get(0).lon;
        for (int level = 16; level >= 0; level--) {
            double firstX = xForLon(firstLon, level), world = TILE * (1 << level);
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (AltosFlightPathPanel.Fix fix : flightTrack) {
                double x = xForLon(fix.lon, level);
                x -= Math.rint((x - firstX) / world) * world;
                double y = yForLat(fix.lat, level);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            if (level == 0 || (maxX - minX <= Math.max(300, getWidth() - 100)
                    && maxY - minY <= Math.max(260, getHeight() - 100))) {
                zoom = level;
                longitude = lonForX((minX + maxX) / 2, level);
                latitude = latForY((minY + maxY) / 2, level);
                generation++;
                repaint();
                break;
            }
        }
    }

    public void changeZoom(int steps) {
        if (!centered) return;
        setZoom(zoom + steps);
    }

    public void setZoom(int level) {
        if (!centered) return;
        zoom = Math.max(0, Math.min(19, level));
        generation++;
        repaint();
    }

    int getZoom() { return zoom; }

    public void close() { generation++; imageryUpdated = null; retryTimer.stop(); network.shutdownNow(); }

    static double xForLon(double lon, int level) { return (lon + 180) / 360 * TILE * (1 << level); }
    static double lonForX(double x, int level) {
        double degrees = x / (TILE * (1 << level)) * 360;
        return ((degrees % 360 + 360) % 360) - 180;
    }
    static double yForLat(double lat, int level) {
        double rad = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        return (1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2 * TILE * (1 << level);
    }
    static double latForY(double y, int level) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y / (TILE * (1 << level))))));
    }

    static String aerialUrl(int z, int x, int y) {
        if (z <= AERIAL_CACHED_MAX_ZOOM)
            return "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/"
                + z + "/" + y + "/" + x;
        double extent = 40075016.68557849;
        double n = (double)(1 << z);
        return String.format(Locale.US,
            "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer/exportImage" +
            "?bbox=%.3f,%.3f,%.3f,%.3f&bboxSR=3857&imageSR=3857&size=256,256&format=jpg&f=image",
            x / n * extent - extent / 2, extent / 2 - (y + 1) / n * extent,
            (x + 1) / n * extent - extent / 2, extent / 2 - y / n * extent);
    }

    private void request(final int level, final int x, final int y, final String key) {
        if (System.currentTimeMillis() < retryTime(level) || !pending.add(key)) return;
        final int requestedGeneration = generation;
        try {
            network.execute(new Runnable() {
                public void run() {
                    try {
                        if (requestedGeneration != generation || network.isShutdown()) return;
                        BufferedImage loaded = tile(level, x, y);
                        if (loaded != null) SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (network.isShutdown()) return;
                                images.put(key, loaded);
                                repaint();
                                if (imageryUpdated != null) imageryUpdated.run();
                            }
                        });
                    } finally {
                        pending.remove(key);
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (network.isShutdown()) return;
                                scheduleRetry();
                                // A stale request may have been skipped while the same tile is
                                // still visible. Repaint gives the current viewport another chance.
                                repaint();
                                if (imageryUpdated != null) imageryUpdated.run();
                            }
                        });
                    }
                }
            });
        } catch (RejectedExecutionException ignored) { pending.remove(key); }
    }

    private void scheduleRetry() {
        if (network.isShutdown()) return;
        long now = System.currentTimeMillis();
        long next = Math.min(retryAfter > now ? retryAfter : Long.MAX_VALUE,
                             detailRetryAfter > now ? detailRetryAfter : Long.MAX_VALUE);
        if (next != Long.MAX_VALUE) {
            retryTimer.setInitialDelay((int)Math.min(Integer.MAX_VALUE, next - now + 100));
            retryTimer.restart();
        }
    }

    private long retryTime(int z) {
        return aerial && z > AERIAL_CACHED_MAX_ZOOM ? detailRetryAfter : retryAfter;
    }

    private void pauseRequests(int z, long milliseconds) {
        if (aerial && z > AERIAL_CACHED_MAX_ZOOM)
            detailRetryAfter = System.currentTimeMillis() + milliseconds;
        else
            retryAfter = System.currentTimeMillis() + milliseconds;
    }

    private BufferedImage tile(int z, int x, int y) {
        File file = new File(directory, z + "/" + x + "/" + y + (aerial ? ".jpg" : ".png"));
        File metadata = new File(file.getPath() + ".properties");
        Properties properties = new Properties();
        if (metadata.isFile()) {
            try (InputStream input = new FileInputStream(metadata)) { properties.load(input); }
            catch (IOException ignored) { }
        }
        long expiry = 0;
        try { expiry = Long.parseLong(properties.getProperty("expires", "0")); }
        catch (NumberFormatException ignored) { }
        if (file.isFile() && (expiry > System.currentTimeMillis()
                || (expiry == 0 && System.currentTimeMillis() - file.lastModified() < WEEK))) {
            BufferedImage cached = readImage(file);
            if (cached != null) return cached;
            // A truncated disk tile should be fetched again instead of
            // triggering an endless read/repaint cycle.
            file.delete();
            metadata.delete();
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(aerial ? aerialUrl(z, x, y) :
                "https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png").openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(7500);
            if (file.isFile()) {
                String etag = properties.getProperty("etag");
                if (etag != null) connection.setRequestProperty("If-None-Match", etag);
                if (file.lastModified() > 0) connection.setIfModifiedSince(file.lastModified());
            }
            int status = connection.getResponseCode();
            if (status == 304 && file.isFile()) {
                BufferedImage cached = readImage(file);
                if (cached != null) {
                    properties.setProperty("expires", String.valueOf(expiry(connection, aerial)));
                    save(properties, metadata);
                    return cached;
                }
                // The server still recognizes the ETag, but our local copy is
                // damaged. Discard both so the next request can fetch a full tile.
                file.delete();
                metadata.delete();
                pauseRequests(z, 1000);
                return null;
            }
            if (status != 200 || connection.getContentType() == null
                    || !connection.getContentType().toLowerCase(Locale.ROOT).contains("image/")) {
                pauseRequests(z, 30000);
                return readImage(file);
            }
            if (connection.getContentLength() > 2_000_000) {
                pauseRequests(z, 30000);
                return readImage(file);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream source = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = source.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 2_000_000) {
                        pauseRequests(z, 30000);
                        return readImage(file);
                    }
                }
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
            if (image == null) {
                pauseRequests(z, 30000);
                return readImage(file);
            }
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) return image;
            File temp = File.createTempFile("altos-tile-", ".png", folder);
            try {
                try (OutputStream out = new FileOutputStream(temp)) { out.write(bytes.toByteArray()); }
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally { temp.delete(); }
            long modified = connection.getLastModified();
            if (modified > 0) file.setLastModified(modified);
            String etag = connection.getHeaderField("ETag");
            if (etag != null) properties.setProperty("etag", etag);
            properties.setProperty("expires", String.valueOf(expiry(connection, aerial)));
            save(properties, metadata);
            return image;
        } catch (IOException | SecurityException error) {
            pauseRequests(z, 30000);
            return readImage(file);
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static BufferedImage readImage(File file) {
        if (!file.isFile()) return null;
        try { return ImageIO.read(file); } catch (IOException ignored) { return null; }
    }

    private static long expiry(HttpURLConnection connection, boolean aerial) {
        String control = connection.getHeaderField("Cache-Control");
        if (control != null) for (String part : control.split(",")) {
            part = part.trim();
            if (part.startsWith("max-age=")) try {
                return System.currentTimeMillis() + Math.max(0, Long.parseLong(part.substring(8))) * 1000;
            } catch (NumberFormatException ignored) { }
        }
        long expires = connection.getExpiration();
        return expires > 0 ? expires : System.currentTimeMillis() + (aerial ? 24*60*60*1000L : WEEK);
    }

    private static void save(Properties properties, File file) {
        try (OutputStream out = new FileOutputStream(file)) { properties.store(out, "AltOS street tile cache"); }
        catch (IOException ignored) { }
    }

    /** Keep map messages readable over both bright aerial tiles and dark gaps. */
    private void drawNotice(Graphics2D g, String message, Color accent) {
        g.setFont(AltosDesktopTheme.font(Font.BOLD, 12));
        FontMetrics metrics = g.getFontMetrics();
        int pad = AltosDesktopTheme.dp(12);
        int height = metrics.getHeight() + AltosDesktopTheme.dp(14);
        int width = Math.min(getWidth() - AltosDesktopTheme.dp(24),
            metrics.stringWidth(message) + 2 * pad + AltosDesktopTheme.dp(5));
        if (width <= 0) return;
        g.setColor(new Color(8, 17, 27, 242));
        g.fillRoundRect(AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(12), width, height,
            AltosDesktopTheme.dp(9), AltosDesktopTheme.dp(9));
        g.setColor(accent);
        g.fillRoundRect(AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(12), AltosDesktopTheme.dp(4), height,
            AltosDesktopTheme.dp(4), AltosDesktopTheme.dp(4));
        g.setColor(Color.WHITE);
        Shape previousClip = g.getClip();
        g.clipRect(AltosDesktopTheme.dp(12) + pad, AltosDesktopTheme.dp(12),
            Math.max(0, width - 2 * pad), height);
        g.drawString(message, AltosDesktopTheme.dp(12) + pad,
            AltosDesktopTheme.dp(12) + (height - metrics.getHeight()) / 2 + metrics.getAscent());
        g.setClip(previousClip);
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (!centered) { g.dispose(); return; }
        if (aerial && (latitude < 24 || latitude > 50 || longitude < -126 || longitude > -66)) {
            if (!imageryOnly) drawNotice(g, "USGS aerial covers the continental U.S.", AltosDesktopTheme.WARNING);
            g.dispose(); return;
        }
        int count = 1 << zoom;
        double left = xForLon(longitude, zoom) - getWidth() / 2.0;
        double top = yForLat(latitude, zoom) - getHeight() / 2.0;
        int firstX = (int)Math.floor(left / TILE), firstY = (int)Math.floor(top / TILE);
        int lastX = (int)Math.floor((left + getWidth()) / TILE);
        int lastY = (int)Math.floor((top + getHeight()) / TILE);
        java.util.List<int[]> missing = new ArrayList<int[]>();
        for (int row = firstY; row <= lastY; row++) {
            if (row < 0 || row >= count) continue;
            for (int column = firstX; column <= lastX; column++) {
                int x = ((column % count) + count) % count;
                String key = zoom + "/" + x + "/" + row;
                int px = (int)Math.round(column * TILE - left);
                int py = (int)Math.round(row * TILE - top);
                BufferedImage image = images.get(key);
                if (image == null) {
                    // At high aerial zooms the dynamic service can take time.
                    // Keep a cached overview underneath it as detail arrives.
                    boolean previewDrawn = false;
                    int minimumParent = aerial && zoom > AERIAL_CACHED_MAX_ZOOM
                        ? AERIAL_CACHED_MAX_ZOOM : zoom - 1;
                    for (int level = zoom - 1; level >= Math.max(0, minimumParent); level--) {
                        int shift = zoom - level;
                        BufferedImage parent = images.get(level + "/" + (x >> shift) + "/" + (row >> shift));
                        if (parent != null) {
                            int side = TILE >> shift;
                            int sx = (x & ((1 << shift) - 1)) * side;
                            int sy = (row & ((1 << shift) - 1)) * side;
                            g.drawImage(parent, px, py, px + TILE, py + TILE,
                                sx, sy, sx + side, sy + side, null);
                            previewDrawn = true;
                            break;
                        }
                    }
                    if (aerial && zoom > AERIAL_CACHED_MAX_ZOOM && !previewDrawn) {
                        int shift = zoom - AERIAL_CACHED_MAX_ZOOM;
                        int parentX = x >> shift, parentY = row >> shift;
                        request(AERIAL_CACHED_MAX_ZOOM, parentX, parentY,
                                AERIAL_CACHED_MAX_ZOOM + "/" + parentX + "/" + parentY);
                    }
                    missing.add(new int[] { x, row, px, py });
                } else g.drawImage(image, px, py, TILE, TILE, null);
            }
        }
        Collections.sort(missing, (a, b) -> {
            double ax = a[2] + TILE / 2.0 - getWidth() / 2.0;
            double ay = a[3] + TILE / 2.0 - getHeight() / 2.0;
            double bx = b[2] + TILE / 2.0 - getWidth() / 2.0;
            double by = b[3] + TILE / 2.0 - getHeight() / 2.0;
            return Double.compare(ax * ax + ay * ay, bx * bx + by * by);
        });
        for (int[] tile : missing)
            request(zoom, tile[0], tile[1], zoom + "/" + tile[0] + "/" + tile[1]);
        if (imageryOnly) { g.dispose(); return; }
        paintFlightTrack(g);
        Point marker = launchMarker();
        if (marker.x >= -20 && marker.x <= getWidth() + 20 &&
            marker.y >= -20 && marker.y <= getHeight() + 20) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 190));
            g.setFont(AltosDesktopTheme.font(Font.BOLD, 11));
            String pinLabel = flightTrack.isEmpty() ? "Launch site" : "First GPS fix";
            if (!flightTrack.isEmpty() && selectedFlightFix >= 0) {
                Point active = flightPoint(flightTrack.get(Math.min(selectedFlightFix, flightTrack.size() - 1)));
                if (active.distance(marker) < AltosDesktopTheme.dp(130)) pinLabel = null;
            }
            if (pinLabel != null) {
                int labelWidth = g.getFontMetrics().stringWidth(pinLabel) + AltosDesktopTheme.dp(18);
                g.fillRoundRect(marker.x + 14, marker.y - 35, labelWidth, AltosDesktopTheme.dp(25), 6, 6);
                g.setColor(Color.WHITE);
                g.drawString(pinLabel, marker.x + 23, marker.y - 18);
            }
            g.setColor(AltosDesktopTheme.ACCENT_LIGHT);
            g.fillOval(marker.x - 9, marker.y - 27, 18, 18);
            g.fillPolygon(new int[] { marker.x - 6, marker.x + 6, marker.x },
                          new int[] { marker.y - 15, marker.y - 15, marker.y }, 3);
            g.setColor(Color.WHITE);
            g.fillOval(marker.x - 3, marker.y - 21, 6, 6);
        }
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRoundRect(12, getHeight()-34, 230, 24, 5, 5);
        g.setFont(AltosDesktopTheme.font(Font.PLAIN, 11));
        g.setColor(Color.WHITE);
        g.drawString(aerial ? "USGS aerial · year varies · z" + zoom :
            "© OpenStreetMap contributors · z" + zoom, 20, getHeight()-17);
        if (aerial && zoom > AERIAL_CACHED_MAX_ZOOM && System.currentTimeMillis() < detailRetryAfter) {
            drawNotice(g, "Detailed aerial is delayed · showing cached imagery", AltosDesktopTheme.WARNING);
        } else if (System.currentTimeMillis() < retryAfter) {
            drawNotice(g, "Map tiles unavailable · try offline map", AltosDesktopTheme.DANGER);
        } else if (aerial && zoom > AERIAL_CACHED_MAX_ZOOM && !missing.isEmpty()) {
            drawNotice(g, "Loading high-resolution aerial detail…", AltosDesktopTheme.ACCENT_LIGHT);
        } else if (images.isEmpty() && !pending.isEmpty()) {
            drawNotice(g, "Loading map imagery…", AltosDesktopTheme.ACCENT_LIGHT);
        }
        g.dispose();
    }

    private Point flightPoint(AltosFlightPathPanel.Fix fix) {
        double world = TILE * (1 << zoom);
        double deltaX = xForLon(fix.lon, zoom) - xForLon(longitude, zoom);
        deltaX -= Math.rint(deltaX / world) * world;
        return new Point((int)Math.round(getWidth() / 2.0 + deltaX),
            (int)Math.round(getHeight() / 2.0 + yForLat(fix.lat, zoom) - yForLat(latitude, zoom)));
    }

    private void paintFlightTrack(Graphics2D g) {
        if (flightTrack.isEmpty()) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Point previous = null;
        AltosFlightPathPanel.Fix previousFix = null;
        for (AltosFlightPathPanel.Fix fix : flightTrack) {
            Point point = flightPoint(fix);
            if (previous != null && AltosFlightPathPanel.continuous(previousFix, fix)
                    && Math.abs(point.x - previous.x) < getWidth() / 2) {
                g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0, 0, 0, 220));
                g.drawLine(previous.x, previous.y, point.x, point.y);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(fix.up >= previousFix.up ? new Color(0x40C9FF) : new Color(0xFFAC59));
                g.drawLine(previous.x, previous.y, point.x, point.y);
            }
            previous = point;
            previousFix = fix;
        }
        int selectedIndex = Math.min(flightTrack.size() - 1, Math.max(0, selectedFlightFix));
        AltosFlightPathPanel.Fix selected = flightTrack.get(selectedIndex);
        Point point = flightPoint(selected);
        g.setColor(Color.BLACK);
        g.fillOval(point.x - 10, point.y - 10, 20, 20);
        g.setColor(Color.WHITE);
        g.fillOval(point.x - 6, point.y - 6, 12, 12);
        String caption = String.format(Locale.US, "GPS fix %d/%d  ·  %.0f m MSL",
            selectedIndex + 1, flightTrack.size(), selected.altitude);
        g.setFont(AltosDesktopTheme.font(Font.BOLD, 11));
        int x = Math.max(8, Math.min(getWidth() - g.getFontMetrics().stringWidth(caption) - 22, point.x + 13));
        int y = Math.max(35, Math.min(getHeight() - 45, point.y - 15));
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRoundRect(x - 6, y - 17, g.getFontMetrics().stringWidth(caption) + 12, 24, 7, 7);
        g.setColor(Color.WHITE);
        g.drawString(caption, x, y);
    }

    // The launch coordinates stay fixed when the camera pans or changes zoom.
    Point launchMarker() {
        double world = TILE * (1 << zoom);
        double deltaX = xForLon(launchLongitude, zoom) - xForLon(longitude, zoom);
        deltaX -= Math.rint(deltaX / world) * world;
        return new Point((int)Math.round(getWidth() / 2.0 + deltaX),
                         (int)Math.round(getHeight() / 2.0 +
                             yForLat(launchLatitude, zoom) - yForLat(latitude, zoom)));
    }
}
