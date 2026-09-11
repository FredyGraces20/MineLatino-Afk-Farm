package com.minelatino.afkfarm.forge.mixin;

import com.minelatino.afkfarm.client.AfkFarmOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class ForgeGuiMixin {
    @Inject(method = {"render", "m_280421_"}, at = @At("TAIL"), remap = false)
    private void minelatinoAfk$render(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        AfkFarmOverlay.render(graphics);
    }
}

