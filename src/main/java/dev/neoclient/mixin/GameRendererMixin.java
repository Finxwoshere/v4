package dev.neoclient.mixin;

import dev.neoclient.Config;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void neoclient$disableHurtCamera(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (Config.I.noHurtCamera) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void neoclient$disableViewBobbing(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (Config.I.noBobbing) ci.cancel();
    }
}
