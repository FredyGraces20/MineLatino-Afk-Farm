package com.minelatino.afkfarm.client.mixin;

import com.minelatino.afkfarm.client.AiAssistantKeybind;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;
    @Inject(method = "load", at = @At("HEAD"))
    private void minelatinoAfk$registerAssistantKey(CallbackInfo ci) {
        keyMappings = AiAssistantKeybind.appendTo(keyMappings);
    }
}
