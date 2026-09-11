package com.minelatino.afkfarm.client.mixin;

import com.minelatino.afkfarm.client.AfkFarmClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void minelatinoAfk$tick(CallbackInfo ci) { AfkFarmClient.instance().tick(); }
}

