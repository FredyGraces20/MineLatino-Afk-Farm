package com.minelatino.afkfarm.forge;

import com.minelatino.afkfarm.client.AiAssistantKeybind;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "minelatino_afk_farm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(AiAssistantKeybind.mapping());
    }
}
