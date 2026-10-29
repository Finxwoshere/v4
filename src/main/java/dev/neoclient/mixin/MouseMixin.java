package dev.neoclient.mixin;

import dev.neoclient.CameraControl;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scroll wheel adjusts freecam speed instead of the hotbar. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void neoclient$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CameraControl.onScroll(vertical)) ci.cancel();
    }
}
