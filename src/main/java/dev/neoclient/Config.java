package dev.neoclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("neoclient.json");
    public static Config I = new Config();

    // ---- Visuals ----
    public boolean infoHud = true, showFps = true, showCoords = true, showFacing = true, chroma = true;
    public boolean zoomEnabled = true;
    public int zoomFov = 30;            // 30..110 (vanilla FOV slider range)
    public boolean fullbright = false;
    public boolean clearWeather = false;
    public boolean cinematic = false;
    /** Locally render living entities affected by the Invisibility potion effect. */
    public boolean antiInvis = false;
    /** Local cosmetic MEDIA label; never sent to the server. */
    public boolean localMediaBadgeEnabled = true;
    public int tint = 0;                // index into Hud.TINTS

    // ---- Freecam ----
    public boolean freecamEnabled = true;
    public float freecamSpeed = 1.0f;       // blocks per tick (scroll wheel changes this)
    public float freecamSens = 1.0f;        // multiplier on top of vanilla mouse sensitivity
    public int freecamBoost = 3;            // speed multiplier while holding sprint
    public boolean freecamScroll = true;    // scroll wheel adjusts speed
    public boolean freecamInvertY = false;

    // ---- Freelook ----
    public boolean freelookEnabled = true;
    public float freelookSens = 1.0f;
    public boolean freelookToggle = false;  // false = hold key, true = press to toggle
    public boolean freelookFront = false;   // false = behind player, true = in front
    public boolean freelookInvertY = false;

    // ---- Spotify ----
    public boolean spotifyEnabled = true;
    public boolean showCover = true, showTitle = true, showArtist = true, showAlbum = false, showProgress = true;
    public boolean showLyrics = true;
    public int lyricLines = 1;          // 1 or 3
    public int lyricOffsetMs = 0;       // shift lyrics earlier/later to fix latency
    public boolean floatingLyrics = true;
    public int floatingLyricX = -1, floatingLyricY = 0;
    public int hudX = 4, hudY = 4;
    public int donutX = 4, donutY = 40, waypointX = 4, waypointY = 88;
    public int spotifyX = -1, spotifyY = -1;
    public int keystrokesX = 6, keystrokesY = 120, armorX = 6, armorY = 170, potionX = 6, potionY = 220;
    public int accentColor = 0xFF438BCA;
    public int theme = 0;
    /** Uses layered translucent panels and soft glass-like controls in the client UI. */
    public boolean glassUi = true;
    public boolean keystrokesHud = true, armorHud = true, potionHud = true, customCrosshair = false, noHurtCamera = false, noBobbing = false;
    public int crosshairSize = 6, crosshairGap = 3, crosshairThickness = 1;
    public float viewmodelX = 0f, viewmodelY = 0f, viewmodelZ = 0f;
    public float waypointScale = 1.0f;
    public float hudScale = 1.0f;
    public boolean donutHud = true;
    public boolean donutCompact = false;
    /** Local hour at which the daily tracker window rolls over. */
    public int donutResetHour = 0;
    public boolean auctionEnabled = false;
    public String auctionItem = "";
    public String auctionWinner = "";
    // Optional group 1 is bidder; final capture group is the payment amount.
    public String auctionRegex = "(?i)(?:([A-Za-z0-9_]{2,16})\\s+)?(?:paid|payment|bid|received).*?\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    public long auctionTopBidCents = 0, auctionWinningBidCents = 0, auctionEndEpochMs = 0;
    public int auctionX = 6, auctionY = 270;
    // Capture group 1 must be the numeric amount from a matching server chat line.
    public String donutRegex = "(?:\\+|earned|received|sold).*?\\$([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    // Capture group 1 is the amount spent, as reported in server chat.
    public String donutSpendRegex = "(?i)(?:spent|purchase|purchased|bought|paid).*?\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    public String balanceRegex = "(?i)(?:balance|bal)[^0-9$]{0,24}\\$?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    public int coverSize = 48;
    public int corner = 1;              // 0 TL, 1 TR, 2 BL, 3 BR
    public int bgOpacity = 140;         // 0..255
    public String spotifyClientId = "";
    public String refreshToken = "";    // keep this file private!

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            NeoClient.LOG.warn("Could not read config, using defaults", e);
        }
        I.clamp();
        save();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(I));
        } catch (Exception e) {
            NeoClient.LOG.warn("Could not save config", e);
        }
    }

    private void clamp() {
        zoomFov = Math.max(30, Math.min(110, zoomFov));
        tint = Math.max(0, Math.min(Hud.TINTS.length - 1, tint));
        theme = Math.max(0, Math.min(3, theme));
        donutResetHour = Math.max(0, Math.min(23, donutResetHour));
        accentColor = new int[]{0xFF438BCA, 0xFF9A6BFF, 0xFF45C995, 0xFFFF9E4A}[theme];
        lyricLines = lyricLines == 3 ? 3 : 1;
        coverSize = Math.max(16, Math.min(96, coverSize));
        corner = Math.max(0, Math.min(3, corner));
        freecamSpeed = Math.max(0.05f, Math.min(12f, freecamSpeed));
        freecamSens = Math.max(0.1f, Math.min(3f, freecamSens));
        freelookSens = Math.max(0.1f, Math.min(3f, freelookSens));
        freecamBoost = Math.max(1, Math.min(8, freecamBoost));
        bgOpacity = Math.max(0, Math.min(255, bgOpacity));
        hudScale = Math.max(0.5f, Math.min(2.0f, hudScale));
        waypointScale = Math.max(0.5f, Math.min(2.0f, waypointScale));
        hudX = Math.max(0, Math.min(10000, hudX));
        hudY = Math.max(0, Math.min(10000, hudY));
        if (spotifyClientId == null) spotifyClientId = "";
        if (refreshToken == null) refreshToken = "";
        if (donutRegex == null || donutRegex.length() > 256) donutRegex = "";
        if (donutSpendRegex == null || donutSpendRegex.length() > 256) donutSpendRegex = "";
        if (balanceRegex == null || balanceRegex.length() > 256) balanceRegex = "";
        if (auctionRegex == null || auctionRegex.length() > 256) auctionRegex = "";
        if (auctionItem == null) auctionItem = "";
        if (auctionWinner == null) auctionWinner = "";
    }
}
