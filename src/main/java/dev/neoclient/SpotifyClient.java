package dev.neoclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spotify "currently playing" poller (Authorization Code + PKCE, no client secret needed)
 * plus synced lyrics from LRCLIB (https://lrclib.net, free, no key).
 */
public final class SpotifyClient {
    public static final String REDIRECT = "http://127.0.0.1:8888/callback";
    public static final Identifier COVER_ID = Identifier.of("neoclient", "spotify_cover");
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String UA = "NeoClient/1.0 (Minecraft Fabric mod)";

    public record Line(long timeMs, String text) {}

    // state read by the HUD
    public static volatile String status = "Not connected";
    public static volatile boolean hasTrack, playing, coverReady, lyricsLoading;
    public static volatile String title = "", artist = "", album = "", trackId = "";
    public static volatile long progressMs, durationMs, fetchedAt;
    public static volatile List<Line> lyrics = List.of();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(3, r -> {
        Thread t = new Thread(r, "NeoClient-Spotify");
        t.setDaemon(true);
        return t;
    });
    private static final Pattern LRC = Pattern.compile("^\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)$");

    private static String accessToken, verifier, coverUrl = "";
    private static long accessExpiry;
    private static HttpServer authServer;

    private SpotifyClient() {}

    public static void start() {
        if (!Config.I.refreshToken.isEmpty()) status = "Connecting...";
        EXEC.scheduleWithFixedDelay(SpotifyClient::poll, 2, 1, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ auth
    public static void connect() {
        Config c = Config.I;
        if (c.spotifyClientId.isBlank()) {
            status = "Enter your Client ID first";
            return;
        }
        try {
            stopServer();
            verifier = random(64);
            String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            String state = random(16);

            authServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8888), 0);
            authServer.createContext("/callback", ex -> {
                Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
                String msg;
                if (state.equals(q.get("state")) && q.containsKey("code")) {
                    msg = "Spotify connected! You can close this tab and go back to Minecraft.";
                    String code = q.get("code");
                    EXEC.submit(() -> exchange(code));
                } else {
                    msg = "Authorization failed or was cancelled.";
                    status = "Authorization cancelled";
                }
                byte[] b = ("<html><body style='font-family:sans-serif;background:#121212;color:#fff;text-align:center;padding-top:80px'><h2>"
                        + msg + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                EXEC.schedule(SpotifyClient::stopServer, 2, TimeUnit.SECONDS);
            });
            authServer.start();
            EXEC.schedule(SpotifyClient::stopServer, 3, TimeUnit.MINUTES);

            String url = "https://accounts.spotify.com/authorize?response_type=code"
                    + "&client_id=" + enc(c.spotifyClientId)
                    + "&redirect_uri=" + enc(REDIRECT)
                    + "&scope=" + enc("user-read-currently-playing user-read-playback-state")
                    + "&code_challenge_method=S256&code_challenge=" + challenge
                    + "&state=" + state;
            status = "Waiting for browser login...";
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                mc.keyboard.setClipboard(url); // fallback if the browser doesn't open
                Util.getOperatingSystem().open(URI.create(url));
            });
        } catch (Exception e) {
            status = "Could not start login (port 8888 busy?)";
            NeoClient.LOG.warn("Spotify connect failed", e);
        }
    }

    public static void disconnect() {
        Config.I.refreshToken = "";
        Config.save();
        accessToken = null;
        hasTrack = false;
        playing = false;
        coverReady = false;
        coverUrl = "";
        trackId = "";
        lyrics = List.of();
        status = "Not connected";
    }

    private static void stopServer() {
        if (authServer != null) {
            authServer.stop(0);
            authServer = null;
        }
    }

    private static void exchange(String code) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("grant_type", "authorization_code");
            m.put("code", code);
            m.put("redirect_uri", REDIRECT);
            m.put("client_id", Config.I.spotifyClientId);
            m.put("code_verifier", verifier);
            HttpResponse<String> r = postForm(m);
            if (r.statusCode() != 200) {
                status = "Login failed (" + r.statusCode() + ")";
                return;
            }
            applyToken(JsonParser.parseString(r.body()).getAsJsonObject());
            status = "Connected";
        } catch (Exception e) {
            status = "Login error";
            NeoClient.LOG.warn("Spotify token exchange failed", e);
        }
    }

    private static boolean ensureToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < accessExpiry - 30_000) return true;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("grant_type", "refresh_token");
        m.put("refresh_token", Config.I.refreshToken);
        m.put("client_id", Config.I.spotifyClientId);
        HttpResponse<String> r = postForm(m);
        if (r.statusCode() == 200) {
            applyToken(JsonParser.parseString(r.body()).getAsJsonObject());
            return true;
        }
        if (r.statusCode() == 400 || r.statusCode() == 401) {
            Config.I.refreshToken = "";
            Config.save();
            status = "Session expired - reconnect Spotify";
        } else {
            status = "Token error " + r.statusCode();
        }
        hasTrack = false;
        return false;
    }

    private static void applyToken(JsonObject o) {
        accessToken = o.get("access_token").getAsString();
        accessExpiry = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000L;
        if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
            Config.I.refreshToken = o.get("refresh_token").getAsString();
            Config.save();
        }
    }

    // ------------------------------------------------------------------ polling
    private static void poll() {
        try {
            Config c = Config.I;
            if (!c.spotifyEnabled || c.refreshToken.isEmpty() || c.spotifyClientId.isBlank()) {
                hasTrack = false;
                return;
            }
            if (!ensureToken()) return;

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            long now = System.currentTimeMillis();

            switch (r.statusCode()) {
                case 200 -> parse(r.body(), now);
                case 204 -> {
                    hasTrack = false;
                    playing = false;
                    status = "Nothing playing";
                }
                case 401 -> accessToken = null;
                case 429 -> {
                    status = "Rate limited";
                    Thread.sleep(5000);
                }
                default -> status = "Spotify error " + r.statusCode();
            }
        } catch (Exception e) {
            status = "Offline";
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static void parse(String body, long now) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        if (!o.has("item") || o.get("item").isJsonNull()) {
            hasTrack = false;
            status = "Nothing playing";
            return;
        }
        JsonObject item = o.getAsJsonObject("item");
        String name = str(item, "name");
        String id = str(item, "id");
        if (id.isEmpty()) id = name; // local files have no id

        List<String> names = new ArrayList<>();
        if (item.has("artists") && item.get("artists").isJsonArray()) {
            for (JsonElement a : item.getAsJsonArray("artists")) names.add(str(a.getAsJsonObject(), "name"));
        }
        String artists = String.join(", ", names);
        String albumName = "";
        String img = "";
        if (item.has("album") && item.get("album").isJsonObject()) {
            JsonObject al = item.getAsJsonObject("album");
            albumName = str(al, "name");
            if (al.has("images") && al.get("images").isJsonArray()) {
                JsonArray arr = al.getAsJsonArray("images");
                if (arr.size() > 0) img = str(arr.get(Math.min(1, arr.size() - 1)).getAsJsonObject(), "url");
            }
        }

        long dur = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
        title = name;
        artist = artists;
        album = albumName;
        durationMs = dur;
        progressMs = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
        playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
        fetchedAt = now;
        hasTrack = true;
        status = playing ? "Playing" : "Paused";

        if (!id.equals(trackId)) {
            trackId = id;
            lyrics = List.of();
            loadLyrics(id, name, artists, albumName, dur);
        }
        if (!img.isEmpty() && !img.equals(coverUrl)) {
            coverUrl = img;
            loadCover(img);
        }
    }

    // ------------------------------------------------------------------ cover art
    private static void loadCover(String url) {
        EXEC.submit(() -> {
            try {
                byte[] bytes = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).build(),
                        HttpResponse.BodyHandlers.ofByteArray()).body();
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes)); // Spotify serves JPEG
                if (src == null) return;
                BufferedImage dst = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = dst.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, 128, 128, null);
                g.dispose();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(dst, "png", bo); // NativeImage only reads PNG
                NativeImage ni = NativeImage.read(new ByteArrayInputStream(bo.toByteArray()));

                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    // (1.21.11: constructor takes a name supplier)
                    mc.getTextureManager().registerTexture(COVER_ID, new NativeImageBackedTexture(() -> "neoclient_cover", ni));
                    coverReady = true;
                });
            } catch (Exception e) {
                NeoClient.LOG.warn("Cover load failed", e);
            }
        });
    }

    // ------------------------------------------------------------------ lyrics (LRCLIB)
    private static void loadLyrics(String id, String t, String a, String al, long durMs) {
        lyricsLoading = true;
        EXEC.submit(() -> {
            List<Line> out = List.of();
            try {
                String firstArtist = a.contains(", ") ? a.substring(0, a.indexOf(", ")) : a;
                String url = "https://lrclib.net/api/get?track_name=" + enc(t) + "&artist_name=" + enc(firstArtist)
                        + "&album_name=" + enc(al) + "&duration=" + (durMs / 1000);
                HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA)
                        .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
                String synced = null;
                if (r.statusCode() == 200) {
                    synced = str(JsonParser.parseString(r.body()).getAsJsonObject(), "syncedLyrics");
                }
                if (synced == null || synced.isEmpty()) { // fallback: fuzzy search
                    String su = "https://lrclib.net/api/search?track_name=" + enc(t) + "&artist_name=" + enc(firstArtist);
                    HttpResponse<String> sr = HTTP.send(HttpRequest.newBuilder(URI.create(su)).header("User-Agent", UA)
                            .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
                    if (sr.statusCode() == 200) {
                        for (JsonElement e : JsonParser.parseString(sr.body()).getAsJsonArray()) {
                            String s = str(e.getAsJsonObject(), "syncedLyrics");
                            if (!s.isEmpty()) { synced = s; break; }
                        }
                    }
                }
                if (synced != null && !synced.isEmpty()) out = parseLrc(synced);
            } catch (Exception e) {
                NeoClient.LOG.debug("Lyrics lookup failed", e);
            }
            if (id.equals(trackId)) {
                lyrics = out;
                lyricsLoading = false;
            }
        });
    }

    private static List<Line> parseLrc(String s) {
        List<Line> out = new ArrayList<>();
        for (String raw : s.split("\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
            long frac = m.group(3) == null ? 0 : Long.parseLong((m.group(3) + "00").substring(0, 3));
            out.add(new Line(min * 60_000 + sec * 1000 + frac, m.group(4).trim()));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }

    // ------------------------------------------------------------------ helpers
    private static HttpResponse<String> postForm(Map<String, String> m) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (var e : m.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return HTTP.send(HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> out = new HashMap<>();
        if (q == null) return out;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) out.put(java.net.URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String random(int n) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}
