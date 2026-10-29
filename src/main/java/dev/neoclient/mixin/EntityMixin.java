package dev.neoclient.mixin;

import dev.neoclient.CameraControl;
import dev.neoclient.Config;
import dev.neoclient.MediaBadge;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redirects mouse look to the detached camera, keeping mouse sensitivity and freelook angles under client control. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void neoclient$showPotionInvisibleEntities(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (Config.I.antiInvis && entity instanceof LivingEntity living
                && living.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            cir.setReturnValue(false);
        }
    }
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void neoclient$localMediaName(CallbackInfoReturnable<Text> cir) {
        if ((Object) this == net.minecraft.client.MinecraftClient.getInstance().player) {
            cir.setReturnValue(MediaBadge.decorate(cir.getReturnValue()));
        }
    }
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void neoclient$look(double dx, double dy, CallbackInfo ci) {
        if (CameraControl.isCamera((Entity) (Object) this) && CameraControl.look(dx, dy)) ci.cancel();
    }
}
