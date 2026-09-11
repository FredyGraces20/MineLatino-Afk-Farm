package com.minelatino.afkfarm.forge;

import com.minelatino.afkfarm.client.AfkFarmClient;
import com.minelatino.afkfarm.client.AfkFarmPauseMenu;
import com.minelatino.afkfarm.client.AutoReconnect;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "minelatino_afk_farm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    @SubscribeEvent public static void afterScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen screen) AfkFarmPauseMenu.install(screen, event::addListener);
        if (event.getScreen() instanceof DisconnectedScreen screen) AutoReconnect.install(screen, event::addListener);
    }
    @SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) AfkFarmClient.instance().tick();
    }
}

