package com.minelatino.afkfarm.client.mixin;

import com.minelatino.afkfarm.client.AutoReconnect;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    protected DisconnectedScreenMixin(Component title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    private void minelatinoAfk$init(CallbackInfo ci) {
        AutoReconnect.install((DisconnectedScreen)(Object)this, widget -> addRenderableWidget(widget));
    }
}

