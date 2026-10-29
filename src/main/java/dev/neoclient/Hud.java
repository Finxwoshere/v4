package dev.neoclient;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Hud {
    public static final int[] TINTS = {0, 0x22FF8800, 0x2200AAFF, 0x22FF2D95, 0x2200FF66, 0x229933FF};
    public static final String[] TINT_NAMES = {"Off", "Warm", "Cool", "Rose", "Mint", "Violet"};
    private static final int GREEN = 0xFF1DB954;
    private static final int W = 230, PAD = 5, LH = 11;

    private Hud() {}

    public static void init() {
        HudRenderCallback.EVENT.register(Hud::render);
    }

    private static void render(DrawContext g, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        Config c = Config.I;
        int sw = g.getScaledWindowWidth(), sh = g.getScaledWindowHeight();

        if (c.tint > 0) g.fill(0, 0, sw, sh, TINTS[c.tint]);
        if (c.cinematic) {
            int bar = sh / 9;
            g.fill(0, 0, sw, bar, 0xFF000000);
            g.fill(0, sh - bar, sw, sh, 0xFF000000);
        }
        if (CameraControl.mode() != CameraControl.Mode.NONE) {
            String label = CameraControl.mode() == CameraControl.Mode.FREECAM
                    ? String.format(Locale.ROOT, "FREECAM  speed %.2f", c.freecamSpeed) : "FREELOOK";
            int ly = c.cinematic ? sh / 9 + 4 : 4;
            g.drawCenteredTextWithShadow(mc.textRenderer, label, sw / 2, ly, 0xFFFFFFFF);
        }
        if (c.infoHud) info(g, mc);
        if (c.keystrokesHud) keystrokes(g, mc);
        if (c.armorHud) armor(g, mc);
        if (c.potionHud) potions(g, mc);
        if (c.customCrosshair) crosshair(g, sw, sh);
        if (c.donutHud) { DonutTracker.readScoreboard(mc); donut(g, mc, sw, sh); }
        if (c.auctionEnabled) auction(g, mc);
        waypoint(g, mc);
        if (c.spotifyEnabled) spotify(g, mc, sw, sh);
        if (c.spotifyEnabled && c.showLyrics && c.floatingLyrics) floatingLyrics(g, mc, sw, sh);
    }


    /** Karaoke-style lyric line floating above the hotbar. */
    private static void floatingLyrics(DrawContext g, MinecraftClient mc, int sw, int sh) {
        if (!SpotifyClient.hasTrack || SpotifyClient.lyrics.isEmpty()) return;
        Config c = Config.I;
        long t = elapsed() + c.lyricOffsetMs;
        List<SpotifyClient.Line> lines = SpotifyClient.lyrics;
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= t) idx = i; else break;
        }
        if (idx < 0) return;
        String current = lines.get(idx).text();
        long start = lines.get(idx).timeMs();
        long next = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
        double fadeIn = Math.min(1.0, Math.max(0.0, (t - start) / 220.0));
        double fadeOut = Math.min(1.0, Math.max(0.0, (next - t) / 260.0));
        int alpha = (int) (255 * Math.min(fadeIn, fadeOut));
        int y = c.floatingLyricY > 0 ? c.floatingLyricY : sh - 54;
        String shown = mc.textRenderer.trimToWidth(current, Math.max(80, sw - 32));
        int width = mc.textRenderer.getWidth(shown);
        int cx = c.floatingLyricX >= 0 ? c.floatingLyricX + width / 2 : sw / 2;
        roundedFill(g, cx - width / 2 - 10, y - 4, cx + width / 2 + 10, y + 13, ((alpha * 120 / 255) << 24) | (c.accentColor & 0x00FFFFFF), 5);
        g.drawCenteredTextWithShadow(mc.textRenderer, shown, cx, y, (alpha << 24) | 0xFFFFFF);
    }

    private static void donut(DrawContext g, MinecraftClient mc, int sw, int sh) {
        TextRenderer tr = mc.textRenderer;
        Config c = Config.I;
        int x = c.donutX;
        int y = c.donutY;
        String[] lines = c.donutCompact
                ? new String[]{"Donut Profit", "Today net  " + money(DonutTracker.todayCents()), "Session net  " + money(DonutTracker.sessionCents())}
                : new String[]{"Donut Profit Tracker", DonutTracker.balanceChangeLabel(),
                    "Today net  " + money(DonutTracker.todayCents()), "Revenue  " + money(DonutTracker.todayRevenueCents()),
                    "Spent      " + money(DonutTracker.todaySpendCents()), "Session net  " + money(DonutTracker.sessionCents()),
                    "7 day net  " + money(DonutTracker.weekCents())};
        int w = 0;
        for (String line : lines) w = Math.max(w, tr.getWidth(line));
        roundedFill(g, x - 5, y - 4, x + w + 9, y + lines.length * 11 + 4, c.glassUi ? 0xB8141C29 : 0xE8191D24, 7);
        g.fill(x - 3, y, x - 1, y + lines.length * 10 - 1, Config.I.accentColor);
        for (int i = 0; i < lines.length; i++) g.drawTextWithShadow(tr, lines[i], x, y + i * 11, i == 0 ? 0xFF76C7FF : 0xFFE8E8E8);
    }


    private static void keystrokes(DrawContext g, MinecraftClient mc) {
        Config c = Config.I;
        int x = c.keystrokesX, y = c.keystrokesY, cell = 18, gap = 2;
        drawKey(g, mc, "W", x + cell + gap, y, mc.options.forwardKey.isPressed(), cell);
        drawKey(g, mc, "A", x, y + cell + gap, mc.options.leftKey.isPressed(), cell);
        drawKey(g, mc, "S", x + cell + gap, y + cell + gap, mc.options.backKey.isPressed(), cell);
        drawKey(g, mc, "D", x + (cell + gap) * 2, y + cell + gap, mc.options.rightKey.isPressed(), cell);
        drawKey(g, mc, "JUMP", x, y + (cell + gap) * 2, mc.options.jumpKey.isPressed(), cell * 3 + gap * 2);
        String mouse = "LMB " + (mc.options.attackKey.isPressed() ? "●" : "○") + "   RMB " + (mc.options.useKey.isPressed() ? "●" : "○");
        g.drawTextWithShadow(mc.textRenderer, mouse, x, y + (cell + gap) * 3, 0xFFE8EDF4);
    }

    private static void drawKey(DrawContext g, MinecraftClient mc, String label, int x, int y, boolean down, int size) {
        roundedFill(g, x, y, x + size, y + size, down ? Config.I.accentColor : 0xC0191D24, 3);
        g.drawCenteredTextWithShadow(mc.textRenderer, label, x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
    }

    private static void armor(DrawContext g, MinecraftClient mc) {
        int x = Config.I.armorX, y = Config.I.armorY, i = 0;
        net.minecraft.entity.EquipmentSlot[] armorSlots = {
                net.minecraft.entity.EquipmentSlot.FEET,
                net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.HEAD
        };
        for (net.minecraft.entity.EquipmentSlot slot : armorSlots) {
            net.minecraft.item.ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) { i++; continue; }
            int ix = x + i * 34;
            roundedFill(g, ix, y, ix + 30, y + 34, 0xA8191D24, 4);
            g.drawItem(stack, ix + 7, y + 2);
            int max = stack.getMaxDamage();
            String text = max > 0 ? Math.round((1f - (float) stack.getDamage() / max) * 100) + "%" : "";
            g.drawCenteredTextWithShadow(mc.textRenderer, text, ix + 15, y + 22, 0xFFFFFFFF);
            i++;
        }
    }

    private static void potions(DrawContext g, MinecraftClient mc) {
        int x = Config.I.potionX, y = Config.I.potionY, row = 0;
        for (net.minecraft.entity.effect.StatusEffectInstance effect : mc.player.getStatusEffects()) {
            String name = effect.getEffectType().value().getName().getString();
            if (effect.getAmplifier() > 0) name += " " + (effect.getAmplifier() + 1);
            int seconds = Math.max(0, effect.getDuration() / 20);
            String line = mc.textRenderer.trimToWidth(name + "  " + (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60), 150);
            roundedFill(g, x, y + row * 13, x + mc.textRenderer.getWidth(line) + 12, y + row * 13 + 12, 0xA8191D24, 3);
            g.drawTextWithShadow(mc.textRenderer, line, x + 6, y + row * 13 + 2, 0xFFE8EDF4);
            row++;
            if (row >= 8) break;
        }
    }

    private static void crosshair(DrawContext g, int sw, int sh) {
        Config c = Config.I;
        int cx = sw / 2, cy = sh / 2, t = Math.max(1, c.crosshairThickness), size = Math.max(2, c.crosshairSize), gap = Math.max(1, c.crosshairGap);
        int color = c.accentColor;
        g.fill(cx - t / 2, cy - size - gap, cx + (t + 1) / 2, cy - gap, color);
        g.fill(cx - t / 2, cy + gap + 1, cx + (t + 1) / 2, cy + gap + size + 1, color);
        g.fill(cx - size - gap, cy - t / 2, cx - gap, cy + (t + 1) / 2, color);
        g.fill(cx + gap + 1, cy - t / 2, cx + gap + size + 1, cy + (t + 1) / 2, color);
    }

    private static void roundedFill(DrawContext g, int x1, int y1, int x2, int y2, int color, int radius) {
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - (r - row) * (r - row)));
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            g.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
        g.fill(x1, y1 + r, x2, y2 - r, color);
    }
    private static String money(long cents) {
        return String.format(Locale.ROOT, "$%,d.%02d", cents / 100, Math.abs(cents % 100));
    }


    private static void auction(DrawContext g, MinecraftClient mc) {
        Config c = Config.I;
        int x = c.auctionX, y = c.auctionY;
        String item = mc.textRenderer.trimToWidth(c.auctionItem.isBlank() ? "Auction" : c.auctionItem, 170);
        boolean ended = AuctionTracker.isEnded();
        String result = AuctionTracker.winner() + " won with " + AuctionTracker.winningBid();
        String top = "Top bid  " + AuctionTracker.money();
        int w = Math.max(mc.textRenderer.getWidth(item), Math.max(mc.textRenderer.getWidth(top), ended ? mc.textRenderer.getWidth(result) : 0)) + 18;
        int h = ended ? 48 : 39;
        roundedFill(g, x, y, x + w, y + h, c.glassUi ? 0xD8141C29 : 0xE8191D24, 7);
        g.fill(x, y + 4, x + 2, y + h - 4, ended ? 0xFFFF414D : c.accentColor);
        if (ended) {
            g.drawTextWithShadow(mc.textRenderer, "AUCTION ENDED", x + 8, y + 5, 0xFFFF3D4A);
            g.drawTextWithShadow(mc.textRenderer, result, x + 8, y + 18, 0xFFFFFFFF);
            g.drawTextWithShadow(mc.textRenderer, item, x + 8, y + 33, 0xFFB3C1D1);
        } else {
            g.drawTextWithShadow(mc.textRenderer, item, x + 8, y + 5, 0xFFFFFFFF);
            g.drawTextWithShadow(mc.textRenderer, top, x + 8, y + 17, 0xFFE3EAF2);
            g.drawTextWithShadow(mc.textRenderer, "Time  " + AuctionTracker.status(), x + 8, y + 28, 0xFFB3C1D1);
        }
    }
    private static void waypoint(DrawContext g, MinecraftClient mc) {
        String label = WaypointManager.nearestLabel(mc);
        if (label.isEmpty()) return;
        int y = Config.I.waypointY;
        int x = Config.I.waypointX;
        var matrices = g.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(Config.I.waypointScale, Config.I.waypointScale);
        g.fill(0, 3, 6, 9, Config.I.accentColor);
        g.drawTextWithShadow(mc.textRenderer, label, 10, 0, 0xFFFFD36A);
        matrices.popMatrix();
    }
    // ---------------------------------------------------------------- info HUD
    private static void info(DrawContext g, MinecraftClient mc) {
        Config c = Config.I;
        TextRenderer tr = mc.textRenderer;
        List<String> l = new ArrayList<>();
        if (c.showFps) l.add("FPS " + mc.getCurrentFps());
        if (c.showCoords) l.add(String.format(Locale.ROOT, "XYZ %.1f %.1f %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        if (c.showFacing) l.add("Facing " + mc.player.getHorizontalFacing().asString());
        if (l.isEmpty()) return;

        int w = 0;
        for (String s : l) w = Math.max(w, tr.getWidth(s));
        int x = Config.I.hudX, y = Config.I.hudY;
        roundedFill(g, x - 2, y - 2, x + w + 2, y + l.size() * 10 + 1, 0x90000000, 4);
        for (int i = 0; i < l.size(); i++) g.drawTextWithShadow(tr, l.get(i), x, y + i * 10, color(i));
    }

    private static int color(int i) {
        if (!Config.I.chroma) return 0xFFFFFFFF;
        float h = ((System.currentTimeMillis() % 4000) / 4000f) + i * 0.08f;
        return Color.HSBtoRGB(h, 0.6f, 1f) | 0xFF000000;
    }

    // ---------------------------------------------------------------- Spotify HUD
    private static long elapsed() {
        long e = SpotifyClient.progressMs + (SpotifyClient.playing ? System.currentTimeMillis() - SpotifyClient.fetchedAt : 0);
        long d = SpotifyClient.durationMs;
        return d > 0 ? Math.min(e, d) : e;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static void spotify(DrawContext g, MinecraftClient mc, int sw, int sh) {
        Config c = Config.I;
        TextRenderer tr = mc.textRenderer;

        if (!SpotifyClient.hasTrack) {
            if (c.refreshToken.isEmpty()) {
                String s = "Spotify: not connected (Right Shift > Spotify)";
                int hx = (c.corner % 2 == 0) ? 6 : sw - 6 - tr.getWidth(s);
                int hy = c.corner < 2 ? 6 : sh - 16;
                g.drawTextWithShadow(tr, s, hx, hy, 0xFFB3B3B3);
            }
            return;
        }

        int cover = (c.showCover && SpotifyClient.coverReady) ? c.coverSize : 0;
        int textX0 = PAD + (cover > 0 ? cover + PAD : 0);
        int textW = W - textX0 - PAD;
        int rows = (c.showTitle ? 1 : 0) + (c.showArtist ? 1 : 0) + (c.showAlbum ? 1 : 0) + (c.showProgress ? 1 : 0);
        int panelH = PAD * 2 + Math.max(cover, rows * LH);

        int lyRows = c.showLyrics ? c.lyricLines : 0;
        int lyH = lyRows > 0 ? lyRows * 12 + 8 : 0;
        int totalH = panelH + (lyH > 0 ? 3 + lyH : 0);

        int x = c.spotifyX >= 0 ? c.spotifyX : ((c.corner % 2 == 0) ? 6 : sw - 6 - W);
        int y = c.spotifyY >= 0 ? c.spotifyY : (c.corner < 2 ? 6 : sh - 6 - totalH);
        int bg = (c.bgOpacity & 0xFF) << 24;

        roundedFill(g, x, y, x + W, y + panelH, bg, 6);
        g.fill(x, y, x + 2, y + panelH, c.accentColor);

        if (cover > 0) {
            g.drawTexture(RenderPipelines.GUI_TEXTURED, SpotifyClient.COVER_ID,
                    x + PAD, y + PAD, 0f, 0f, cover, cover, 128, 128, 128, 128);
        }

        int tx = x + textX0;
        int ty = y + PAD + Math.max(0, (cover - rows * LH) / 2);
        if (c.showTitle) {
            g.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(SpotifyClient.title, textW - 8)).formatted(Formatting.BOLD),
                    tx, ty, 0xFFFFFFFF);
            ty += LH;
        }
        if (c.showArtist) {
            g.drawTextWithShadow(tr, tr.trimToWidth(SpotifyClient.artist, textW), tx, ty, 0xFFB3B3B3);
            ty += LH;
        }
        if (c.showAlbum) {
            g.drawTextWithShadow(tr, tr.trimToWidth(SpotifyClient.album, textW), tx, ty, 0xFF808080);
            ty += LH;
        }
        if (c.showProgress) {
            long el = elapsed(), d = SpotifyClient.durationMs;
            String a = fmt(el), b = fmt(d);
            int aw = tr.getWidth(a), bw = tr.getWidth(b);
            g.drawTextWithShadow(tr, a, tx, ty, 0xFFB3B3B3);
            g.drawTextWithShadow(tr, b, tx + textW - bw, ty, 0xFFB3B3B3);
            int bx1 = tx + aw + 4, bx2 = tx + textW - bw - 4, by = ty + 3;
            if (bx2 > bx1) {
                g.fill(bx1, by, bx2, by + 3, 0x80FFFFFF);
                if (d > 0) g.fill(bx1, by, bx1 + (int) ((bx2 - bx1) * Math.min(1.0, (double) el / d)), by + 3, GREEN);
            }
        }

        if (lyH > 0) {
            int ly0 = y + panelH + 3;
            int cx = x + W / 2;
            roundedFill(g, x, ly0, x + W, ly0 + lyH, bg, 5);
            List<SpotifyClient.Line> ly = SpotifyClient.lyrics;
            if (ly.isEmpty()) {
                String s = SpotifyClient.lyricsLoading ? "Loading lyrics..." : "No synced lyrics found";
                g.drawCenteredTextWithShadow(tr, s, cx, ly0 + 4, 0xFF808080);
            } else {
                long t = elapsed() + c.lyricOffsetMs;
                int idx = -1;
                for (int i = 0; i < ly.size(); i++) {
                    if (ly.get(i).timeMs() <= t) idx = i; else break;
                }
                int start = lyRows == 3 ? idx - 1 : idx;
                for (int r = 0; r < lyRows; r++) {
                    int i = start + r;
                    boolean cur = lyRows == 1 || r == 1;
                    String s = (i < 0 || i >= ly.size()) ? "" : ly.get(i).text();
                    if (s.isEmpty() && cur) s = "\u266A";
                    g.drawCenteredTextWithShadow(tr, tr.trimToWidth(s, W - 12), cx, ly0 + 4 + r * 12,
                            cur ? 0xFFFFFFFF : 0xFF808080);
                }
            }
        }
    }
}
