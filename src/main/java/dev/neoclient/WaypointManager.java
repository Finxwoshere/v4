package dev.neoclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Local waypoint store. Coordinates are client-side metadata and never sent to servers. */
public final class WaypointManager {
    public record Waypoint(String name, double x, double y, double z, String dimension) {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("neoclient-waypoints.json");
    private static final List<Waypoint> POINTS = new ArrayList<>();

    private WaypointManager() {}

    public static synchronized List<Waypoint> all() { return List.copyOf(POINTS); }

    public static synchronized void add(String name, double x, double y, double z, String dimension) {
        if (name == null || name.isBlank() || name.length() > 48 || dimension == null || dimension.length() > 128) return;
        POINTS.add(new Waypoint(name.trim(), x, y, z, dimension));
        save();
    }

    public static synchronized void remove(int index) {
        if (index >= 0 && index < POINTS.size()) { POINTS.remove(index); save(); }
    }

    public static synchronized void load() {
        try {
            if (!Files.exists(FILE)) return;
            Waypoint[] saved = GSON.fromJson(Files.readString(FILE), Waypoint[].class);
            POINTS.clear();
            if (saved != null) for (Waypoint w : saved) if (w != null && w.name() != null) POINTS.add(w);
        } catch (Exception e) { NeoClient.LOG.warn("Could not load waypoints", e); }
    }

    private static void save() {
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(POINTS)); }
        catch (Exception e) { NeoClient.LOG.warn("Could not save waypoints", e); }
    }

    public static String nearestLabel(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return "";
        String here = mc.world.getRegistryKey().getValue().toString();
        Waypoint best = null;
        double bestD = Double.MAX_VALUE;
        boolean nether = here.endsWith(":the_nether");
        for (Waypoint w : all()) {
            double px = mc.player.getX(), pz = mc.player.getZ(), wx = w.x(), wz = w.z();
            if (!w.dimension().equals(here)) {
                if (nether && w.dimension().endsWith(":overworld")) { wx /= 8.0; wz /= 8.0; }
                else if (!nether && here.endsWith(":overworld") && w.dimension().endsWith(":the_nether")) { px /= 8.0; pz /= 8.0; }
                else continue;
            }
            double d = Math.sqrt((px - wx) * (px - wx) + (mc.player.getY() - w.y()) * (mc.player.getY() - w.y()) + (pz - wz) * (pz - wz));
            if (d < bestD) { bestD = d; best = w; }
        }
        if (best == null) return "";
        return String.format(java.util.Locale.ROOT, "%s  %.0fm%s", best.name(), bestD,
                best.dimension().equals(here) ? "" : " nether route");
    }
}
