package dev.neoclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Compact ClickGUI with module groups and dedicated HUD editor. */
public class SettingsScreen extends Screen {
    private static int page = 0;
    private static final String[] PAGES = {"Visual", "Camera", "Spotify", "HUD", "View", "Points", "Auction"};
    private static final String[] THEMES = {"Blue", "Violet", "Mint", "Amber"};
    private static final String[] CORNERS = {"Top-left", "Top-right", "Bottom-left", "Bottom-right"};
    private static final float[] SENS = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final float[] SPEEDS = {0.1f, 0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f};

    public SettingsScreen() { super(Text.literal("f2aQ Client")); }

    @Override
    protected void init() {
        Config c = Config.I;
        for (int i = 0; i < PAGES.length; i++) {
            final int target = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(PAGES[i]), b -> { page = target; clearAndInit(); })
                    .dimensions(panelX() + 16, panelY() + 60 + i * 31, 148, 25).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX() + panelWidth() - 72, panelY() + 13, 56, 22).build());

        if (page == 0) {
            toggle(0, "Info HUD", () -> c.infoHud, v -> c.infoHud = v);
            toggle(1, "Show FPS", () -> c.showFps, v -> c.showFps = v);
            toggle(2, "Coordinates", () -> c.showCoords, v -> c.showCoords = v);
            toggle(3, "Facing direction", () -> c.showFacing, v -> c.showFacing = v);
            toggle(4, "Chroma text", () -> c.chroma, v -> c.chroma = v);
            toggle(5, "Zoom", () -> c.zoomEnabled, v -> c.zoomEnabled = v);
            cycleInt(6, "Zoom FOV", new int[]{30, 40, 50, 60, 70}, () -> c.zoomFov, v -> c.zoomFov = v);
            toggle(7, "Fullbright", () -> c.fullbright, v -> c.fullbright = v);
            toggle(8, "Clear weather", () -> c.clearWeather, v -> c.clearWeather = v);
            toggle(9, "Cinematic bars", () -> c.cinematic, v -> c.cinematic = v);
            cycleNamed(10, "Screen tint", Hud.TINT_NAMES, () -> c.tint, v -> c.tint = v);
            toggle(11, "Donut earnings HUD", () -> c.donutHud, DonutTracker::setEnabled);
            toggle(12, "Show invisibility potion entities", () -> c.antiInvis, v -> c.antiInvis = v);
            toggle(13, "Local-only MEDIA badge", () -> c.localMediaBadgeEnabled, v -> c.localMediaBadgeEnabled = v);
            toggle(14, "Frosted glass UI", () -> c.glassUi, v -> c.glassUi = v);
            toggle(15, "Compact Donut HUD", () -> c.donutCompact, v -> c.donutCompact = v);
            cycleInt(16, "Daily reset hour", range(24), () -> c.donutResetHour, v -> c.donutResetHour = v);
            TextFieldWidget balancePattern = new TextFieldWidget(textRenderer, contentX(), panelY() + panelHeight() - 58, contentWidth(), 20, Text.literal("Balance scoreboard pattern"));
            balancePattern.setMaxLength(256);
            balancePattern.setPlaceholder(Text.literal("Balance regex (capture group 1 = amount)"));
            balancePattern.setText(c.balanceRegex);
            balancePattern.setChangedListener(v -> c.balanceRegex = v);
            addDrawableChild(balancePattern);
        } else if (page == 1) {
            toggle(0, "Freecam", () -> c.freecamEnabled, v -> c.freecamEnabled = v);
            toggle(1, "Freelook", () -> c.freelookEnabled, v -> c.freelookEnabled = v);
            cycleFloat(2, "Freecam sensitivity", SENS, () -> c.freecamSens, v -> c.freecamSens = v);
            cycleFloat(3, "Freelook sensitivity", SENS, () -> c.freelookSens, v -> c.freelookSens = v);
            cycleFloat(4, "Freecam speed", SPEEDS, () -> c.freecamSpeed, v -> c.freecamSpeed = v);
            toggle(5, "Scroll adjusts speed", () -> c.freecamScroll, v -> c.freecamScroll = v);
            cycleInt(6, "Sprint boost", new int[]{1, 2, 3, 4, 6, 8}, () -> c.freecamBoost, v -> c.freecamBoost = v);
            cycleNamed(7, "Freelook mode", new String[]{"Hold", "Toggle"}, () -> c.freelookToggle ? 1 : 0, v -> c.freelookToggle = v == 1);
            cycleNamed(8, "Freelook view", new String[]{"Behind", "In front"}, () -> c.freelookFront ? 1 : 0, v -> c.freelookFront = v == 1);
            toggle(9, "Freecam invert Y", () -> c.freecamInvertY, v -> c.freecamInvertY = v);
            toggle(10, "Freelook invert Y", () -> c.freelookInvertY, v -> c.freelookInvertY = v);
        } else if (page == 2) {
            toggle(0, "Spotify HUD", () -> c.spotifyEnabled, v -> c.spotifyEnabled = v);
            toggle(1, "Album art", () -> c.showCover, v -> c.showCover = v);
            toggle(2, "Track title", () -> c.showTitle, v -> c.showTitle = v);
            toggle(3, "Artist", () -> c.showArtist, v -> c.showArtist = v);
            toggle(4, "Album", () -> c.showAlbum, v -> c.showAlbum = v);
            toggle(5, "Progress bar", () -> c.showProgress, v -> c.showProgress = v);
            toggle(6, "Synced lyrics", () -> c.showLyrics, v -> c.showLyrics = v);
            cycleInt(7, "Lyric lines", new int[]{1, 3}, () -> c.lyricLines, v -> c.lyricLines = v);
            cycleInt(8, "Cover size", new int[]{32, 48, 64, 80}, () -> c.coverSize, v -> c.coverSize = v);
            cycleNamed(9, "Position", CORNERS, () -> c.corner, v -> c.corner = v);
            cycleInt(10, "Panel opacity", new int[]{0, 80, 140, 200}, () -> c.bgOpacity, v -> c.bgOpacity = v);
            cycleInt(11, "Lyric offset", new int[]{-1000, -500, 0, 500, 1000}, () -> c.lyricOffsetMs, v -> c.lyricOffsetMs = v);
            toggle(12, "Floating lyrics", () -> c.floatingLyrics, v -> c.floatingLyrics = v);
            TextFieldWidget id = new TextFieldWidget(textRenderer, contentX(), panelY() + panelHeight() - 83, contentWidth(), 20, Text.literal("Spotify Client ID"));
            id.setMaxLength(64); id.setPlaceholder(Text.literal("Paste Spotify Client ID")); id.setText(c.spotifyClientId);
            id.setChangedListener(s -> c.spotifyClientId = s.trim()); addDrawableChild(id);
            boolean connected = !c.refreshToken.isEmpty();
            addDrawableChild(ButtonWidget.builder(Text.literal(connected ? "Disconnect Spotify" : "Connect Spotify"), b -> {
                if (connected) SpotifyClient.disconnect(); else SpotifyClient.connect(); Config.save(); clearAndInit();
            }).dimensions(contentX(), panelY() + panelHeight() - 56, contentWidth(), 22).build());
        } else if (page == 3) {
            toggle(0, "Keystrokes", () -> c.keystrokesHud, v -> c.keystrokesHud = v);
            toggle(1, "Armor status", () -> c.armorHud, v -> c.armorHud = v);
            toggle(2, "Potion effects", () -> c.potionHud, v -> c.potionHud = v);
            toggle(3, "Custom crosshair", () -> c.customCrosshair, v -> c.customCrosshair = v);
            toggle(4, "No hurt camera", () -> c.noHurtCamera, v -> c.noHurtCamera = v);
            toggle(5, "No view bobbing", () -> c.noBobbing, v -> c.noBobbing = v);
            cycleNamed(6, "Theme", THEMES, () -> c.theme, SettingsScreen::setTheme);
            cycleInt(7, "Crosshair size", new int[]{3, 5, 7, 9, 12}, () -> c.crosshairSize, v -> c.crosshairSize = v);
            cycleInt(8, "Crosshair gap", new int[]{1, 2, 3, 4, 6}, () -> c.crosshairGap, v -> c.crosshairGap = v);
            cycleFloat(9, "Waypoint scale", new float[]{0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f}, () -> c.waypointScale, v -> c.waypointScale = v);
            toggle(10, "Auction tracker", () -> c.auctionEnabled, v -> { c.auctionEnabled = v; Config.save(); });
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit HUD layout"), b -> client.setScreen(new HudEditorScreen()))
                    .dimensions(contentX(), panelY() + panelHeight() - 78, contentWidth(), 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Reset HUD positions"), b -> {
                c.hudX = 4; c.hudY = 4; c.donutX = 4; c.donutY = 40; c.waypointX = 4; c.waypointY = 88;
                c.spotifyX = -1; c.spotifyY = -1; c.keystrokesX = 6; c.keystrokesY = 120; c.armorX = 6; c.armorY = 170; c.potionX = 6; c.potionY = 220; Config.save();
            }).dimensions(contentX(), panelY() + panelHeight() - 48, contentWidth(), 24).build());
        } else if (page == 4) {
            cycleFloat(0, "Viewmodel X", new float[]{-0.3f, -0.2f, -0.1f, 0f, 0.1f, 0.2f, 0.3f}, () -> c.viewmodelX, v -> c.viewmodelX = v);
            cycleFloat(1, "Viewmodel Y", new float[]{-0.3f, -0.2f, -0.1f, 0f, 0.1f, 0.2f, 0.3f}, () -> c.viewmodelY, v -> c.viewmodelY = v);
            cycleFloat(2, "Viewmodel Z", new float[]{-0.3f, -0.2f, -0.1f, 0f, 0.1f, 0.2f, 0.3f}, () -> c.viewmodelZ, v -> c.viewmodelZ = v);
        } else if (page == 5) {
            TextFieldWidget name = new TextFieldWidget(textRenderer, contentX(), panelY() + 66, contentWidth(), 20, Text.literal("Waypoint name"));
            name.setMaxLength(48); name.setPlaceholder(Text.literal("Name")); addDrawableChild(name);
            int third = (contentWidth() - 16) / 3;
            TextFieldWidget x = new TextFieldWidget(textRenderer, contentX(), panelY() + 92, third, 20, Text.literal("X")); x.setPlaceholder(Text.literal("X")); addDrawableChild(x);
            TextFieldWidget y = new TextFieldWidget(textRenderer, contentX() + third + 8, panelY() + 92, third, 20, Text.literal("Y")); y.setPlaceholder(Text.literal("Y")); addDrawableChild(y);
            TextFieldWidget z = new TextFieldWidget(textRenderer, contentX() + (third + 8) * 2, panelY() + 92, third, 20, Text.literal("Z")); z.setPlaceholder(Text.literal("Z")); addDrawableChild(z);
            addDrawableChild(ButtonWidget.builder(Text.literal("Add waypoint"), b -> {
                MinecraftClient mc = MinecraftClient.getInstance(); if (mc.player == null || mc.world == null) return;
                try { WaypointManager.add(name.getText(), Double.parseDouble(x.getText()), Double.parseDouble(y.getText()), Double.parseDouble(z.getText()), mc.world.getRegistryKey().getValue().toString()); }
                catch (NumberFormatException ex) { return; } clearAndInit();
            }).dimensions(contentX(), panelY() + 119, contentWidth(), 22).build());
            java.util.List<WaypointManager.Waypoint> points = WaypointManager.all();
            for (int i = 0; i < Math.min(5, points.size()); i++) { final int ix = i; WaypointManager.Waypoint w = points.get(i);
                addDrawableChild(ButtonWidget.builder(Text.literal("Remove  " + w.name()), b -> { WaypointManager.remove(ix); clearAndInit(); })
                        .dimensions(contentX(), panelY() + 153 + i * 28, contentWidth(), 22).build()); }
        } else {
            TextFieldWidget item = new TextFieldWidget(textRenderer, contentX(), panelY() + 66, contentWidth(), 20, Text.literal("Auction item"));
            item.setPlaceholder(Text.literal("Item being auctioned")); item.setMaxLength(64); item.setText(c.auctionItem); addDrawableChild(item);
            int half = (contentWidth() - 8) / 2;
            TextFieldWidget price = new TextFieldWidget(textRenderer, contentX(), panelY() + 92, half, 20, Text.literal("Starting bid"));
            price.setPlaceholder(Text.literal("Amount, e.g. 1000")); price.setText("0"); addDrawableChild(price);
            TextFieldWidget duration = new TextFieldWidget(textRenderer, contentX() + half + 8, panelY() + 92, half, 20, Text.literal("Minutes"));
            duration.setPlaceholder(Text.literal("Duration minutes")); duration.setText("5"); addDrawableChild(duration);
            TextFieldWidget regex = new TextFieldWidget(textRenderer, contentX(), panelY() + 119, contentWidth(), 20, Text.literal("Payment chat pattern"));
            regex.setMaxLength(256); regex.setText(c.auctionRegex); regex.setChangedListener(v -> c.auctionRegex = v); addDrawableChild(regex);
            addDrawableChild(ButtonWidget.builder(Text.literal("Start / reset auction"), b -> {
                try { long cents = Math.round(Double.parseDouble(price.getText().replace(",", "").replace("$", "")) * 100.0); AuctionTracker.start(item.getText(), cents, Integer.parseInt(duration.getText())); clearAndInit(); }
                catch (NumberFormatException ex) { }
            }).dimensions(contentX(), panelY() + 147, contentWidth(), 24).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Stop auction tracker"), b -> { AuctionTracker.stop(); clearAndInit(); })
                    .dimensions(contentX(), panelY() + 178, contentWidth(), 24).build());
        }
    }

    private static void setTheme(int theme) { Config.I.theme = theme; Config.I.accentColor = new int[]{0xFF438BCA, 0xFF9A6BFF, 0xFF45C995, 0xFFFF9E4A}[theme]; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int x = panelX(), y = panelY(), pw = panelWidth(), ph = panelHeight();
        renderBackground(ctx, mx, my, delta);
        ctx.fill(0, 0, width, height, 0x66060A10);
        rounded(ctx, x - 8, y - 8, x + pw + 8, y + ph + 8, 0x50000000, 24);
        rounded(ctx, x, y, x + pw, y + ph, Config.I.glassUi ? 0xD91A2029 : 0xF0151A20, 18);
        rounded(ctx, x + 1, y + 1, x + pw - 1, y + 66, Config.I.glassUi ? 0x7AFFFFFF : 0xFF202833, 16);
        rounded(ctx, x + 20, y + 67, x + 174, y + ph - 18, Config.I.glassUi ? 0x341FFFFF : 0xFF11161D, 12);
        rounded(ctx, x + 186, y + 67, x + pw - 16, y + ph - 18, Config.I.glassUi ? 0x281FFFFF : 0xFF191F27, 12);
        ctx.fill(x + 18, y + 54, x + pw - 18, y + 56, (Config.I.accentColor & 0x00FFFFFF) | 0xA0000000);
        super.render(ctx, mx, my, delta);
        // Draw the buttons again with rounded glass styling; their actual widgets remain clickable.
        for (var child : children()) {
            if (!(child instanceof ButtonWidget button)) continue;
            int bx = button.getX(), by = button.getY(), bw = button.getWidth(), bh = button.getHeight();
            boolean hovered = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
            boolean active = button.active;
            int fill = !active ? 0x55343B45 : hovered ? 0xCC35475C : 0x9C232B35;
            rounded(ctx, bx, by, bx + bw, by + bh, fill, Math.min(8, bh / 2));
            if (active) rounded(ctx, bx, by, bx + 2, by + bh, hovered ? Config.I.accentColor : (Config.I.accentColor & 0x70FFFFFF), 1);
            ctx.drawCenteredTextWithShadow(textRenderer, button.getMessage(), bx + bw / 2, by + (bh - 8) / 2, active ? 0xFFF0F4F8 : 0xFF858B93);
        }
        ctx.drawTextWithShadow(textRenderer, "f2aQ  /  CLIENT", x + 22, y + 17, Config.I.accentColor);
        ctx.drawTextWithShadow(textRenderer, PAGES[page], contentX(), y + 18, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Select a module or setting to configure it", contentX(), y + 39, 0xFF9EA7B2);
        ctx.drawTextWithShadow(textRenderer, "MODULES", x + 31, y + 47, 0xFF929BA7);
        if (page == 1) ctx.drawTextWithShadow(textRenderer, "Mouse rotates camera only; movement keys control the player.", contentX(), y + ph - 32, 0xFFB3C1D1);
        if (page == 2) ctx.drawTextWithShadow(textRenderer, "Spotify: " + SpotifyClient.status + "  •  Redirect: " + SpotifyClient.REDIRECT, contentX(), y + ph - 32, 0xFF9CC8E8);
        if (page == 3) ctx.drawTextWithShadow(textRenderer, "Drag HUD panels to position them. Keybinds: Options > Controls.", contentX(), y + ph - 32, 0xFFB3C1D1);
    }

    private static void rounded(DrawContext g, int x1, int y1, int x2, int y2, int color, int r) {
        for (int row = 0; row < r; row++) { int inset = r - row; g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color); g.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color); }
        g.fill(x1, y1 + r, x2, y2 - r, color);
    }

    @Override public void removed() { Config.save(); }
    private int panelWidth() { return Math.min(860, width - 28); }
    private int panelHeight() { return Math.min(620, height - 24); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private int contentX() { return panelX() + 198; }
    private int contentWidth() { return Math.max(200, panelWidth() - 222); }
    private int gx(int i) { int colWidth = (contentWidth() - 12) / 2; return contentX() + (i % 2) * (colWidth + 12); }
    private int gy(int i) { return panelY() + 76 + (i / 2) * 32; }
    private static int[] range(int count) { int[] values = new int[count]; for (int i = 0; i < count; i++) values[i] = i; return values; }
    private void toggle(int i, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(onOff(name, get.getAsBoolean()), b -> { set.accept(!get.getAsBoolean()); b.setMessage(onOff(name, get.getAsBoolean())); })
                .dimensions(gx(i), gy(i), (contentWidth() - 12) / 2, 24).build());
    }
    private void cycleInt(int i, String name, int[] vals, IntSupplier get, IntConsumer set) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + get.getAsInt()), b -> { int cur = 0; for (int k = 0; k < vals.length; k++) if (vals[k] == get.getAsInt()) cur = k; set.accept(vals[(cur + 1) % vals.length]); b.setMessage(Text.literal(name + ": " + get.getAsInt())); })
                .dimensions(gx(i), gy(i), (contentWidth() - 12) / 2, 24).build());
    }
    private interface FloatGet { float get(); } private interface FloatSet { void set(float value); }
    private static String f2(float v) { return String.format(Locale.ROOT, "%.2f", v); }
    private void cycleFloat(int i, String name, float[] vals, FloatGet get, FloatSet set) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + f2(get.get())), b -> { int cur = 0; for (int k = 0; k < vals.length; k++) if (Math.abs(vals[k] - get.get()) < Math.abs(vals[cur] - get.get())) cur = k; set.set(vals[(cur + 1) % vals.length]); b.setMessage(Text.literal(name + ": " + f2(get.get()))); })
                .dimensions(gx(i), gy(i), (contentWidth() - 12) / 2, 24).build());
    }
    private void cycleNamed(int i, String name, String[] labels, IntSupplier get, IntConsumer set) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + labels[get.getAsInt()]), b -> { set.accept((get.getAsInt() + 1) % labels.length); b.setMessage(Text.literal(name + ": " + labels[get.getAsInt()])); })
                .dimensions(gx(i), gy(i), (contentWidth() - 12) / 2, 24).build());
    }
    private static Text onOff(String name, boolean value) { return Text.literal(name + ": " + (value ? "ON" : "OFF")); }
}
