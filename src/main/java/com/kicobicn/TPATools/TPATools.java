package com.kicobicn.TPATools;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TPATools.MODID)
public class TPATools {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "tpatools";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public TPATools() {
        FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(TPAHandler.class);
        MinecraftForge.EVENT_BUS.register(BackHandler.class);
        MinecraftForge.EVENT_BUS.register(HomeHandler.class);
        MinecraftForge.EVENT_BUS.register(GraveHandler.class);
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onServerTick(TickEvent.ServerTickEvent event) {
                if (event.phase == TickEvent.Phase.END) {
                    TPAHandler.tick();
                }
            }
        });

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TPAHandler.CONFIG,"tpatool.toml");
    }
}
