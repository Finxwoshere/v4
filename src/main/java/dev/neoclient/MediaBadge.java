package dev.neoclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Adds a local-only MEDIA suffix to the local player's rendered name. */
public final class MediaBadge {
    private static final String SUFFIX = " [MEDIA]";

    private MediaBadge() {}

    public static boolean isLocalName(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        return Config.I.localMediaBadgeEnabled && client.player != null && name != null
                && client.player.getName().getString().equalsIgnoreCase(name);
    }

    public static Text decorate(Text name) {
        if (name == null || !isLocalName(name.getString()) || name.getString().toUpperCase(java.util.Locale.ROOT).endsWith(SUFFIX)) {
            return name;
        }
        return Text.empty().append(name).append(Text.literal(SUFFIX).formatted(Formatting.AQUA, Formatting.BOLD));
    }

    public static Text decorateScoreboard(String owner, Text name) {
        if (name == null || !isLocalName(owner) || name.getString().toUpperCase(java.util.Locale.ROOT).endsWith(SUFFIX)) {
            return name;
        }
        return Text.empty().append(name).append(Text.literal(SUFFIX).formatted(Formatting.AQUA, Formatting.BOLD));
    }
}