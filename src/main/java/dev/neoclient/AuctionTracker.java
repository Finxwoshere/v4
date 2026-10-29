package dev.neoclient;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local countdown and payment tally for an auction the player starts manually. */
public final class AuctionTracker {
    private AuctionTracker() {}
    public static void init() { ClientReceiveMessageEvents.GAME.register((Text message, boolean overlay) -> capture(message.getString())); }
    public static synchronized void start(String item, long startBidCents, int durationMinutes) {
        Config c = Config.I;
        c.auctionItem = item == null ? "" : item.trim();
        c.auctionTopBidCents = Math.max(0, startBidCents);
        c.auctionWinningBidCents = Math.max(0, startBidCents);
        c.auctionWinner = "";
        c.auctionEndEpochMs = System.currentTimeMillis() + Math.max(1, Math.min(240, durationMinutes)) * 60_000L;
        c.auctionEnabled = true;
        Config.save();
    }
    public static synchronized void stop() { Config.I.auctionEnabled = false; Config.save(); }
    private static synchronized void capture(String line) {
        Config c = Config.I;
        if (!c.auctionEnabled || isEnded() || c.auctionRegex == null || c.auctionRegex.isBlank()) return;
        try {
            Matcher m = Pattern.compile(c.auctionRegex).matcher(line);
            if (!m.find() || m.groupCount() < 1) return;
            String amount = m.group(m.groupCount());
            if (amount == null) return;
            long cents = Math.round(Double.parseDouble(amount.replace(",", "").replace("$", "")) * 100.0);
            if (cents > 0 && cents < 100_000_000_000L) {
                c.auctionTopBidCents += cents;
                String bidder = m.groupCount() > 1 ? m.group(1) : null;
                if (bidder == null || bidder.isBlank()) bidder = extractBidder(line);
                if (cents > c.auctionWinningBidCents) {
                    c.auctionWinningBidCents = cents;
                    if (bidder != null && !bidder.isBlank()) c.auctionWinner = bidder.trim();
                }
                Config.save();
            }
        } catch (Exception e) { NeoClient.LOG.debug("Ignoring unmatched auction payment chat line", e); }
    }
    private static String extractBidder(String line) {
        Matcher m = Pattern.compile("(?i)^\\s*([A-Za-z0-9_]{2,16})\\s+(?:paid|sent|bid|offered|purchased)\\b").matcher(line);
        if (m.find()) return m.group(1);
        m = Pattern.compile("(?i)\\b(?:from|by)\\s+([A-Za-z0-9_]{2,16})\\b").matcher(line);
        return m.find() ? m.group(1) : "";
    }
    public static boolean isEnded() {
        Config c = Config.I;
        return c.auctionEnabled && c.auctionEndEpochMs > 0 && System.currentTimeMillis() >= c.auctionEndEpochMs;
    }
    public static String winner() { return Config.I.auctionWinner == null || Config.I.auctionWinner.isBlank() ? "Highest bidder" : Config.I.auctionWinner; }
    public static String winningBid() { long cents = Config.I.auctionWinningBidCents; return String.format(java.util.Locale.ROOT, "$%,d.%02d", cents / 100, Math.abs(cents % 100)); }
    public static String status() {
        Config c = Config.I;
        long remaining = Math.max(0, c.auctionEndEpochMs - System.currentTimeMillis()) / 1000;
        String clock = String.format(java.util.Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60);
        return c.auctionEnabled ? clock : "Stopped";
    }
    public static String money() { long cents = Config.I.auctionTopBidCents; return String.format(java.util.Locale.ROOT, "$%,d.%02d", cents / 100, Math.abs(cents % 100)); }
}
