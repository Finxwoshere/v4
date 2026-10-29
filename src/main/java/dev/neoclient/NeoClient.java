package dev.neoclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeoClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("neoclient");

    private static KeyBinding zoomKey, menuKey, freecamKey, freelookKey;
    private static final java.util.List<KeyBinding> moduleKeys = new java.util.ArrayList<>();
    private static boolean zooming, nightVisionByUs;
    private static int savedFov;
    private static double savedSens;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("neoclient", "keys"));
        zoomKey = reg("key.neoclient.zoom", GLFW.GLFW_KEY_C, cat);
        menuKey = reg("key.neoclient.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, cat);
        freecamKey = reg("key.neoclient.freecam", GLFW.GLFW_KEY_F6, cat);
        freelookKey = reg("key.neoclient.freelook", GLFW.GLFW_KEY_LEFT_ALT, cat);
        registerModuleKeys(cat);

        ClientTickEvents.START_CLIENT_TICK.register(NeoClient::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(NeoClient::endTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            CameraControl.stop(mc);
            resetZoom(mc);
        });

        Hud.init();
        SpotifyClient.start();
        DonutTracker.init();
        AuctionTracker.init();
        WaypointManager.load();
    }

    private static void registerModuleKeys(KeyBinding.Category cat) {
        String[] names = {"key.neoclient.toggle.info", "key.neoclient.toggle.coords", "key.neoclient.toggle.keystrokes", "key.neoclient.toggle.armor", "key.neoclient.toggle.potions", "key.neoclient.toggle.crosshair", "key.neoclient.toggle.fullbright", "key.neoclient.toggle.nobobbing", "key.neoclient.toggle.nohurt", "key.neoclient.toggle.spotify", "key.neoclient.toggle.donut"};
        for (String name : names) moduleKeys.add(reg(name, GLFW.GLFW_KEY_UNKNOWN, cat));
    }

    private static void toggleModule(String key) {
        Config c = Config.I;
        switch (key) {
            case "key.neoclient.toggle.info" -> c.infoHud = !c.infoHud;
            case "key.neoclient.toggle.coords" -> c.showCoords = !c.showCoords;
            case "key.neoclient.toggle.keystrokes" -> c.keystrokesHud = !c.keystrokesHud;
            case "key.neoclient.toggle.armor" -> c.armorHud = !c.armorHud;
            case "key.neoclient.toggle.potions" -> c.potionHud = !c.potionHud;
            case "key.neoclient.toggle.crosshair" -> c.customCrosshair = !c.customCrosshair;
            case "key.neoclient.toggle.fullbright" -> c.fullbright = !c.fullbright;
            case "key.neoclient.toggle.nobobbing" -> c.noBobbing = !c.noBobbing;
            case "key.neoclient.toggle.nohurt" -> c.noHurtCamera = !c.noHurtCamera;
            case "key.neoclient.toggle.spotify" -> c.spotifyEnabled = !c.spotifyEnabled;
            case "key.neoclient.toggle.donut" -> DonutTracker.setEnabled(!c.donutHud);
        }
        Config.save();
    }

    private static KeyBinding reg(String name, int key, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, key, cat));
    }

    /** Runs before the game processes input this tick. */
    private static void startTick(MinecraftClient mc) {
        Config c = Config.I;
        boolean inGame = mc.player != null && mc.currentScreen == null;

        while (freecamKey.wasPressed()) {
            if (inGame) CameraControl.toggleFreecam(mc);
        }

        if (c.freelookToggle) {
            while (freelookKey.wasPressed()) {
                if (!inGame) continue;
                if (CameraControl.mode() == CameraControl.Mode.FREELOOK) CameraControl.stopFreelook(mc);
                else CameraControl.startFreelook(mc);
            }
        } else {
            while (freelookKey.wasPressed()) { } // drain
            if (inGame && freelookKey.isPressed()) CameraControl.startFreelook(mc);
            else CameraControl.stopFreelook(mc);
        }

        CameraControl.tickStart(mc);
    }

    private static void endTick(MinecraftClient mc) {
        Config c = Config.I;
        CameraControl.tickEnd(mc);

        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new SettingsScreen());
        }

        // Ease FOV in and out to avoid a hard camera snap; preserve the user's sensitivity.
        boolean wantZoom = c.zoomEnabled && mc.currentScreen == null && zoomKey.isPressed();
        if (wantZoom && !zooming) {
            savedFov = mc.options.getFov().getValue();
            savedSens = mc.options.getMouseSensitivity().getValue();
            zooming = true;
        }
        if (zooming) {
            int current = mc.options.getFov().getValue();
            int target = wantZoom ? c.zoomFov : savedFov;
            int distance = target - current;
            int step = Math.max(1, Math.round(Math.abs(distance) * 0.24f));
            if (Math.abs(distance) <= step) current = target;
            else current += Integer.signum(distance) * step;
            mc.options.getFov().setValue(current);
            mc.options.getMouseSensitivity().setValue(wantZoom ? savedSens * 0.4 : savedSens);
            if (!wantZoom && current == savedFov) zooming = false;
        }

        // Fullbright: client-side night vision (purely visual, the server never sees it)
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            nightVisionByUs = false;
        } else if (c.fullbright) {
            StatusEffectInstance e = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (e == null || e.getDuration() < 300) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
                nightVisionByUs = true;
            }
        } else if (nightVisionByUs) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            nightVisionByUs = false;
        }

        if (c.clearWeather && mc.world != null) {
            mc.world.setRainGradient(0f);
            mc.world.setThunderGradient(0f);
        }
    }

    private static void resetZoom(MinecraftClient mc) {
        if (!zooming) return;
        mc.options.getFov().setValue(savedFov);
        mc.options.getMouseSensitivity().setValue(savedSens);
        zooming = false;
    }
}
