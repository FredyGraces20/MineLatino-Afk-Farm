package com.minelatino.afkfarm.forge.mixin;

import com.minelatino.afkfarm.client.AfkFarmOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class ForgeGuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void minelatinoAfk$render(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        AfkFarmOverlay.render(graphics);
    }
}
