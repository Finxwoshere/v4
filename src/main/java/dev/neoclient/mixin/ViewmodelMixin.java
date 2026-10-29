package dev.neoclient.mixin;

import dev.neoclient.Config;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class ViewmodelMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void neoclient$viewmodel(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
            float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        Config c = Config.I;
        matrices.push();
        matrices.translate(c.viewmodelX, c.viewmodelY, c.viewmodelZ);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("TAIL"))
    private void neoclient$restoreMatrix(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
            float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.pop();
    }
}
