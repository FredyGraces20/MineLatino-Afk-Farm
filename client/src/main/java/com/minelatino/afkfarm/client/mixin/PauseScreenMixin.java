package com.minelatino.afkfarm.client.mixin;

import com.minelatino.afkfarm.client.AfkFarmPauseMenu;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    private void minelatinoAfk$init(CallbackInfo ci) {
        AfkFarmPauseMenu.install(this, widget -> addRenderableWidget(widget));
    }
}

