package dev.neoclient.mixin;

import dev.neoclient.MediaBadge;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the local badge to the local player's entry in the sidebar objective scoreboard. */
@Mixin(ScoreboardEntry.class)
public abstract class ScoreboardEntryMixin {
    @Inject(method = "name", at = @At("RETURN"), cancellable = true)
    private void neoclient$localMediaLabel(CallbackInfoReturnable<Text> cir) {
        ScoreboardEntry entry = (ScoreboardEntry) (Object) this;
        cir.setReturnValue(MediaBadge.decorateScoreboard(entry.owner(), cir.getReturnValue()));
    }
}