package dev.neoclient.mixin;

import dev.neoclient.MediaBadge;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decorates only this client's own row in the player-list scoreboard. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void neoclient$localMediaLabel(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(MediaBadge.decorateScoreboard(entry.getProfile().name(), cir.getReturnValue()));
    }
}