package dev.neoclient;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks explicit economy values reported by the server in chat. */
public final class DonutTracker {
    private record Entry(long epochMs, long cents) {}
    private static final Path FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("neoclient-transactions.csv");
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final long SESSION_START = System.currentTimeMillis();
    private static long currentBalanceCents = -1, baselineCents = -1;
    private DonutTracker() {}

    public static void init() {
        load();
        ClientReceiveMessageEvents.GAME.register((Text message, boolean overlay) -> capture(message.getString()));
    }

    public static synchronized void setEnabled(boolean enabled) {
        Config.I.donutHud = enabled;
        if (enabled) baselineCents = currentBalanceCents;
        Config.save();
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(FILE)) return;
            for (String line : Files.readAllLines(FILE)) {
                String[] p = line.split(",", 2);
                if (p.length == 2) ENTRIES.add(new Entry(Long.parseLong(p[0]), Long.parseLong(p[1])));
            }
        } catch (Exception e) { NeoClient.LOG.warn("Could not load donut transactions", e); }
    }

    private static synchronized void capture(String line) {
        Config c = Config.I;
        if (!c.donutHud) return;
        try {
            if (c.balanceRegex != null && !c.balanceRegex.isBlank()) {
                Matcher balance = Pattern.compile(c.balanceRegex).matcher(line);
                if (balance.find() && balance.groupCount() >= 1) {
                    long value = parseCents(balance.group(1));
                    if (value >= 0) {
                        currentBalanceCents = value;
                        if (baselineCents < 0) baselineCents = value;
                        return;
                    }
                }
            }
            if (c.donutRegex == null || c.donutRegex.isBlank()) return;
            if (captureTransaction(line, c.donutRegex, 1)) return;
            captureTransaction(line, c.donutSpendRegex, -1);
        } catch (Exception e) { NeoClient.LOG.debug("Ignoring unmatched balance/transaction chat line", e); }
    }

    /** Reads the visible sidebar objective locally and updates the balance snapshot when configured to match. */
    public static synchronized void readScoreboard(MinecraftClient client) {
        Config c = Config.I;
        if (!c.donutHud || client == null || client.world == null || c.balanceRegex == null || c.balanceRegex.isBlank()) return;
        try {
            var scoreboard = client.world.getScoreboard();
            var objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return;
            Pattern pattern = Pattern.compile(c.balanceRegex, Pattern.CASE_INSENSITIVE);
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                if (entry.hidden()) continue;
                String line = entry.name().getString() + " " + entry.owner();
                if (entry.display() != null) line += " " + entry.display().getString();
                Matcher matcher = pattern.matcher(line);
                if (!matcher.find() || matcher.groupCount() < 1) continue;
                long value = parseCents(matcher.group(1));
                if (value >= 0) {
                    currentBalanceCents = value;
                    if (baselineCents < 0) baselineCents = value;
                    return;
                }
            }
        } catch (Exception e) { NeoClient.LOG.debug("Could not read balance from scoreboard", e); }
    }

    private static boolean captureTransaction(String line, String regex, int sign) throws Exception {
        if (regex == null || regex.isBlank()) return false;
        Matcher payment = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(line);
        if (!payment.find() || payment.groupCount() < 1) return false;
        long cents = parseCents(payment.group(1));
        if (cents <= 0 || cents > 100_000_000_000L) return false;
        cents *= sign;
        long now = System.currentTimeMillis();
        ENTRIES.add(new Entry(now, cents));
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, now + "," + cents + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        return true;
    }

    private static long parseCents(String raw) {
        String amount = raw.replace(",", "").replace("$", "").trim();
        return Math.round(Double.parseDouble(amount) * 100.0);
    }

    public static synchronized String balanceChangeLabel() {
        if (baselineCents < 0 || currentBalanceCents < 0) return "Balance Δ  waiting for balance chat";
        long delta = currentBalanceCents - baselineCents;
        return "Balance Δ  " + (delta >= 0 ? "+" : "-") + money(Math.abs(delta));
    }
    private static String money(long cents) { return String.format(Locale.ROOT, "$%,d.%02d", cents / 100, Math.abs(cents % 100)); }
    public static synchronized long sessionCents() { return sessionRevenueCents() - sessionSpendCents(); }
    public static synchronized long todayCents() { return todayRevenueCents() - todaySpendCents(); }
    public static synchronized long weekCents() {
        long since = Instant.now().minusSeconds(7 * 24 * 60 * 60L).toEpochMilli();
        return sum(e -> e.epochMs >= since && e.cents > 0) - sum(e -> e.epochMs >= since && e.cents < 0);
    }
    public static synchronized long sessionRevenueCents() { return sum(e -> e.epochMs >= SESSION_START && e.cents > 0); }
    public static synchronized long sessionSpendCents() { return -sum(e -> e.epochMs >= SESSION_START && e.cents < 0); }
    public static synchronized long todayRevenueCents() { long since = todayStart(); return sum(e -> e.epochMs >= since && e.cents > 0); }
    public static synchronized long todaySpendCents() { long since = todayStart(); return -sum(e -> e.epochMs >= since && e.cents < 0); }
    private static long todayStart() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDateTime reset = today.atTime(LocalTime.of(Config.I.donutResetHour, 0));
        if (LocalDateTime.now(zone).isBefore(reset)) reset = reset.minusDays(1);
        return reset.atZone(zone).toInstant().toEpochMilli();
    }
    private interface Filter { boolean accept(Entry e); }
    private static long sum(Filter f) { return ENTRIES.stream().filter(f::accept).mapToLong(e -> e.cents).sum(); }
}
