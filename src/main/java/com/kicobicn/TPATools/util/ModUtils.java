package com.kicobicn.TPATools.util;

import com.google.common.reflect.TypeToken;
import com.kicobicn.TPATools.Commands.GraveHandler;
import com.kicobicn.TPATools.Commands.HomeHandler;
import com.kicobicn.TPATools.Commands.TPAHandler;
import com.kicobicn.TPATools.Commands.WarpHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.kicobicn.TPATools.Commands.TPAHandler.*;
import static com.kicobicn.TPATools.config.ModConfigs.DEFAULT_LANGUAGE;
import static com.kicobicn.TPATools.config.ModConfigs.TIMEOUT_TICKS;

public class ModUtils {

    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    public static final Map<String, String> translations = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadTranslations(DEFAULT_LANGUAGE.get());
        HomeHandler.loadHomes();
        GraveHandler.loadGraves();
        loadToggleStates();
        loadLockedPlayers();
        loadCommandPermissions();
        WarpHandler.loadWarpPoints();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HomeHandler.saveHomes();
        GraveHandler.saveGraves();
        saveToggleStates();
        saveLockedPlayers();
        saveCommandPermissions();
        WarpHandler.saveWarpPoints();
    }

    public static MutableComponent translateWithFallback(String key, String fallback, Object... args) {
        String translated = translations.getOrDefault(key, fallback);
        Object[] stringArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Component component) {
                stringArgs[i] = component.getString();
            } else {
                stringArgs[i] = args[i];
            }
        }
        return Component.literal(String.format(translated, stringArgs));
    }

    // 加载翻译
    public static void loadTranslations(String lang) {
        translations.clear();
        
        try {
            // 方法1：直接使用类路径加载（最可靠的方法）
            String fileName = "assets/tpatools/lang/" + lang + ".json";
            var inputStream = ModUtils.class.getClassLoader().getResourceAsStream(fileName);
            
            if (inputStream != null) {
                String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> loadedTranslations = GSON.fromJson(jsonContent, new TypeToken<Map<String, String>>(){}.getType());
                translations.putAll(loadedTranslations);
                ModConfigs.DebugLog.info("Loaded translations for language: {} using classpath", lang);
                return;
            }
            
            // 方法2：尝试使用ResourceLocation（如果服务器已启动）
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("tpatools", "lang/" + lang + ".json");
                var resource = server.getResourceManager().getResource(loc);
                
                if (resource.isPresent()) {
                    String jsonContent = new String(resource.get().open().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> loadedTranslations = GSON.fromJson(jsonContent, new TypeToken<Map<String, String>>(){}.getType());
                    translations.putAll(loadedTranslations);
                    ModConfigs.DebugLog.info("Loaded translations for language: {} using ResourceLocation", lang);
                    return;
                }
            }
            
            // 方法3：尝试从文件系统读取（开发环境）
            Path configPath = ModConfigs.getConfigDir().resolve("lang").resolve(lang + ".json");
            if (Files.exists(configPath)) {
                String jsonContent = Files.readString(configPath, StandardCharsets.UTF_8);
                Map<String, String> loadedTranslations = GSON.fromJson(jsonContent, new TypeToken<Map<String, String>>(){}.getType());
                translations.putAll(loadedTranslations);
                ModConfigs.DebugLog.info("Loaded translations for language: {} from config directory", lang);
                return;
            }
            
            // 如果所有方法都失败，使用回退翻译
            LOGGER.warn("All translation loading methods failed for language: {}", lang);
            loadFallbackTranslations();
            
        } catch (Exception e) {
            LOGGER.error("Failed to load translations for {}: {}", lang, e.getMessage());
            loadFallbackTranslations();
        }
    }
    
    // 加载回退翻译
    private static void loadFallbackTranslations() {
        try {
            // 尝试加载英文作为回退
            String fileName = "assets/tpatools/lang/en_us.json";
            var inputStream = ModUtils.class.getClassLoader().getResourceAsStream(fileName);
            
            if (inputStream != null) {
                String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> loadedTranslations = GSON.fromJson(jsonContent, new TypeToken<Map<String, String>>(){}.getType());
                translations.putAll(loadedTranslations);
                ModConfigs.DebugLog.info("Loaded fallback translations (en_us)");
            } else {
                LOGGER.warn("Fallback translation file not found");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load fallback translations: {}", e.getMessage());
        }
    }

    private static final Set<String> availableLanguages = new HashSet<>(Set.of("en_us", "zh_cn", "fr_fr", "pt_br", "es_es", "zh_cn_cute"));

    public static void detectAvailableLanguages() {
        availableLanguages.clear();
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var resources = server.getResourceManager().listResources("lang", path -> path.getPath().endsWith(".json"));
                for (var entry : resources.entrySet()) {
                    ResourceLocation loc = entry.getKey();
                    String path = loc.getPath();
                    if (path.startsWith("lang/") && path.endsWith(".json")) {
                        String langCode = path.substring(5, path.length() - 5); // 去掉 "lang/" 和 ".json"
                        availableLanguages.add(langCode);
                    }
                }
                if (availableLanguages.isEmpty()) {
                    ModConfigs.DebugLog.warn("No languages detected, fallback to en_us/zh_cn");
                    availableLanguages.add("en_us");
                    availableLanguages.add("zh_cn");
                    availableLanguages.add("fr_fr");
                    availableLanguages.add("pt_br");
                    availableLanguages.add("es_es");
                    availableLanguages.add("zh_cn_cute");
                }
                ModConfigs.DebugLog.info("Detected languages: {}", availableLanguages);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to detect languages: {}", e.getMessage());
        }
    }

    /**
     * 获取玩家骑乘的所有实体（包括链式骑乘）
     */
    public static List<Entity> getRiddenEntities(Entity rider) {
        List<Entity> entities = new ArrayList<>();
        Entity current = rider;

        while (current != null) {
            entities.add(current);
            current = current.getVehicle();
        }

        return entities;
    }

    /**
     * 传送玩家及其骑乘的所有实体
     */
    public static void teleportWithRideChain(Entity player, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        if (!ModConfigs.ALLOW_TELEPORT_RIDE_ENTITY.get()) {
            // 如果配置禁用骑乘传送，只传送玩家
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.teleportTo(targetLevel, x, y, z, yRot, xRot);
            } else {
                // 对于非玩家实体，使用通用传送方法
                player.unRide();
                teleportEntityToPosition(player, targetLevel, x, y, z, yRot, xRot);
            }
            return;
        }

        // 获取骑乘链中的所有实体
        List<Entity> rideChain = getRiddenEntities(player);

        // 记录所有实体的相对位置和旋转
        Map<Entity, Vec3> relativePositions = new HashMap<>();
        Map<Entity, Float> relativeRotations = new HashMap<>();

        for (int i = 0; i < rideChain.size() - 1; i++) {
            Entity rider = rideChain.get(i);
            Entity vehicle = rideChain.get(i + 1);

            // 计算相对位置和旋转
            Vec3 relativePos = rider.position().subtract(vehicle.position());
            float relativeYRot = rider.getYRot() - vehicle.getYRot();

            relativePositions.put(rider, relativePos);
            relativeRotations.put(rider, relativeYRot);
        }

        // 从最底层的实体开始传送
        for (int i = rideChain.size() - 1; i >= 0; i--) {
            Entity entity = rideChain.get(i);

            if (i == rideChain.size() - 1) {
                // 最底层的实体传送到目标位置
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.teleportTo(targetLevel, x, y, z, yRot, xRot);
                } else {
                    // 对于非玩家实体，使用通用传送方法
                    entity.unRide();
                    teleportEntityToPosition(entity, targetLevel, x, y, z, yRot, xRot);
                }
            } else {
                // 其他实体根据相对位置传送
                Entity vehicle = rideChain.get(i + 1);
                Vec3 relativePos = relativePositions.get(entity);
                float relativeYRot = relativeRotations.get(entity);

                if (relativePos != null) {
                    double newX = vehicle.getX() + relativePos.x;
                    double newY = vehicle.getY() + relativePos.y;
                    double newZ = vehicle.getZ() + relativePos.z;
                    float newYRot = vehicle.getYRot() + relativeYRot;

                    if (entity instanceof ServerPlayer serverPlayer) {
                        serverPlayer.teleportTo(targetLevel, newX, newY, newZ, newYRot, entity.getXRot());
                    } else {
                        // 对于非玩家实体，使用通用传送方法
                        entity.unRide();
                        teleportEntityToPosition(entity, targetLevel, newX, newY, newZ, newYRot, entity.getXRot());
                    }
                }
            }
        }
    }

    /**
     * 传送玩家及其骑乘实体
     */
    public static void teleportWithAllChains(Entity player, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        // 首先传送骑乘链
        teleportWithRideChain(player, targetLevel, x, y, z, yRot, xRot);
    }

    /**
     * 传送实体到指定位置（用于非ServerPlayer实体）
     */
    private static void teleportEntityToPosition(Entity entity, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        // 移除实体的骑乘关系
        entity.unRide();

        // 检查目标维度是否相同
        if (entity.level() != targetLevel) {
            DimensionTransition transition = new DimensionTransition(
                    targetLevel,
                    new Vec3(x, y, z),
                    Vec3.ZERO,
                    yRot,
                    xRot,
                    DimensionTransition.DO_NOTHING
            );
            entity.changeDimension(transition);
            // changeDimension 方法会处理实体的传送和位置更新，因此无需再调用 moveTo
            return;
        }

        // 如果在同一维度，则直接设置实体位置和旋转
        entity.moveTo(x, y, z, yRot, xRot);
    }

    public static void tick() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, List<TPAHandler.TPARequest>> entry : requests.entrySet()) {
            Iterator<TPAHandler.TPARequest> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                TPAHandler.TPARequest request = iterator.next();
                if (System.currentTimeMillis() - request.timestamp >= TIMEOUT_TICKS.get() * 50) {
                    request.target.sendSystemMessage(translateWithFallback(
                            request.isTPHere ? "command.tpatool.tpahere.timeout" : "command.tpatool.tpa.timeout",
                            "Teleport request from %s has timed out.", request.sender.getName()
                    ));
                    request.sender.sendSystemMessage(translateWithFallback(
                            "command.tpatool.tpa.timeout_self",
                            "Your teleport request to %s has timed out.", request.target.getName()
                    ));
                    iterator.remove();
                    ModConfigs.DebugLog.info("TPA request timed out: {} -> {}",
                            request.sender.getName().getString(), request.target.getName().getString());
                }
            }
            if (entry.getValue().isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(requests::remove);
    }
}