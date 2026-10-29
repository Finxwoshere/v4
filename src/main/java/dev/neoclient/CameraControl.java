package dev.neoclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/**
 * Freecam + Freelook.
 * Both work by pointing the game camera at a detached, invisible "camera entity" (never added to the world),
 * while the real player is left alone:
 *  - FREECAM : camera flies freely (noclip); movement keys are redirected to the camera, the player stands still.
 *  - FREELOOK: camera orbits the player in third person; the player keeps facing/moving the old direction.
 */
public final class CameraControl {
    public enum Mode { NONE, FREECAM, FREELOOK }

    private static Mode mode = Mode.NONE;
    private static ArmorStandEntity cam;
    private static Perspective savedPerspective = Perspective.FIRST_PERSON;

    private CameraControl() {}

    public static Mode mode() { return mode; }
    public static boolean isCamera(Entity entity) { return mode != Mode.NONE && entity == cam; }

    // ------------------------------------------------------------ start / stop
    public static void toggleFreecam(MinecraftClient mc) {
        if (mode == Mode.FREECAM) { stop(mc); return; }
        if (!Config.I.freecamEnabled) return;
        if (mode != Mode.NONE) stop(mc);
        start(mc, Mode.FREECAM);
    }

    public static void startFreelook(MinecraftClient mc) {
        if (Config.I.freelookEnabled && mode == Mode.NONE) start(mc, Mode.FREELOOK);
    }

    public static void stopFreelook(MinecraftClient mc) {
        if (mode == Mode.FREELOOK) stop(mc);
    }

    private static void start(MinecraftClient mc, Mode m) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;

        cam = new ArmorStandEntity(mc.world, p.getX(), p.getY(), p.getZ());
        cam.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
        double off = cam.getEyeY() - cam.getY();
        cam.refreshPositionAndAngles(p.getX(), p.getEyeY() - off, p.getZ(), p.getYaw(), p.getPitch());

        savedPerspective = mc.options.getPerspective();
        if (m == Mode.FREECAM) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        } else {
            mc.options.setPerspective(Config.I.freelookFront ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);
        }
        mode = m;
        mc.setCameraEntity(cam);
    }

    public static void stop(MinecraftClient mc) {
        if (mode == Mode.NONE) return;
        mode = Mode.NONE;
        cam = null;
        mc.options.setPerspective(savedPerspective);
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    // ------------------------------------------------------------ per tick
    /** Called at the START of every client tick (before the game reads movement keys). */
    public static void tickStart(MinecraftClient mc) {
        if (mode == Mode.NONE) return;
        if (mc.player == null || mc.world == null || cam == null || mc.getCameraEntity() != cam) {
            stop(mc); // death, dimension change, disconnect...
            return;
        }
        if (mode != Mode.FREECAM || mc.currentScreen != null) return;

        Config c = Config.I;
        GameOptions o = mc.options;

        KeyBinding.updatePressedStates(); // sync with real key state, then read it
        float fwd = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        float side = (o.rightKey.isPressed() ? 1 : 0) - (o.leftKey.isPressed() ? 1 : 0);
        float up = (o.jumpKey.isPressed() ? 1 : 0) - (o.sneakKey.isPressed() ? 1 : 0);
        boolean boost = o.sprintKey.isPressed();

        // stop the real player from reacting to these keys
        KeyBinding[] block = {o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey,
                o.attackKey, o.useKey};
        for (KeyBinding k : block) k.setPressed(false);
        while (o.attackKey.wasPressed()) { }
        while (o.useKey.wasPressed()) { }

        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);

        double mx = fx * fwd + rx * side, my = fy * fwd + up, mz = fz * fwd + rz * side;
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 1e-6) {
            double speed = c.freecamSpeed * (boost ? c.freecamBoost : 1);
            mx = mx / Math.max(1, len) * speed;
            my = my / Math.max(1, len) * speed;
            mz = mz / Math.max(1, len) * speed;
        }

        cam.lastX = cam.getX();
        cam.lastY = cam.getY();
        cam.lastZ = cam.getZ();
        cam.setPosition(cam.getX() + mx, cam.getY() + my, cam.getZ() + mz);
    }

    /** Called at the END of every client tick (after the player has moved). */
    public static void tickEnd(MinecraftClient mc) {
        if (mode != Mode.FREELOOK || cam == null || mc.player == null) return;
        ClientPlayerEntity p = mc.player;
        double off = cam.getEyeY() - cam.getY();
        double eye = p.getEyeY() - p.getY();
        cam.lastX = p.lastX;
        cam.lastY = p.lastY + eye - off;
        cam.lastZ = p.lastZ;
        cam.setPosition(p.getX(), p.getY() + eye - off, p.getZ());
    }

    // ------------------------------------------------------------ input hooks (called from mixins)
    /** Mouse look. Returns true if consumed (the real player must not turn). */
    public static boolean look(double dx, double dy) {
        if (mode == Mode.NONE || cam == null) return false;
        boolean fc = mode == Mode.FREECAM;
        float sens = fc ? Config.I.freecamSens : Config.I.freelookSens;
        boolean invert = fc ? Config.I.freecamInvertY : Config.I.freelookInvertY;

        float dyaw = (float) (dx * 0.15 * sens);
        float dpitch = (float) (dy * 0.15 * sens) * (invert ? -1f : 1f);
        float oldPitch = cam.getPitch();
        float newPitch = MathHelper.clamp(oldPitch + dpitch, -90f, 90f);

        cam.setYaw(cam.getYaw() + dyaw);
        cam.lastYaw += dyaw;
        cam.setPitch(newPitch);
        cam.lastPitch += newPitch - oldPitch;
        return true;
    }

    /** Scroll wheel. Returns true if consumed (hotbar must not scroll). */
    public static boolean onScroll(double vertical) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mode != Mode.FREECAM || !Config.I.freecamScroll || mc.currentScreen != null || vertical == 0) return false;
        float f = vertical > 0 ? 1.2f : 1f / 1.2f;
        Config.I.freecamSpeed = MathHelper.clamp(Config.I.freecamSpeed * f, 0.05f, 12f);
        mc.inGameHud.setOverlayMessage(
                Text.literal(String.format(Locale.ROOT, "Freecam speed: %.2f", Config.I.freecamSpeed)), false);
        return true;
    }
}
