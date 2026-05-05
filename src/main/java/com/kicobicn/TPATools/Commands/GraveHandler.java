package com.kicobicn.TPATools.Commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.kicobicn.TPATools.util.ModUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GraveHandler {
    private static final Map<UUID, BackHandler.PlayerPosition> gravePositions = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    //加载死亡位置
    public static void loadGraves() {
        if (DatabaseManager.isUsingMySQL()) {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_graves");
                 ResultSet rs = stmt.executeQuery()) {
                
                gravePositions.clear();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String dimension = rs.getString("dimension");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float xRot = rs.getFloat("x_rot");
                    float yRot = rs.getFloat("y_rot");
                    
                    BackHandler.PlayerPosition position = new BackHandler.PlayerPosition(
                            null, // level will be resolved later
                            x, y, z, yRot, xRot
                    );
                    // 设置维度
                    position.dimension = ResourceLocation.fromNamespaceAndPath("minecraft", dimension);
                    
                    gravePositions.put(uuid, position);
                }
                ModConfigs.DebugLog.info("Loaded graves from MySQL");
            } catch (SQLException e) {
                ModConfigs.DebugLog.error("Failed to load graves from MySQL: {}", e.getMessage());
            }
        } else {
            try {
                Path path = ModConfigs.getConfigDir().resolve("tpatool_graves.json");
                if (Files.exists(path)) {
                    String jsonContent = Files.readString(path);
                    Map<UUID, BackHandler.PlayerPosition> loadedGraves = GSON.fromJson(jsonContent, new TypeToken<Map<UUID, BackHandler.PlayerPosition>>(){}.getType());
                    gravePositions.clear();
                    if (loadedGraves != null) {
                        gravePositions.putAll(loadedGraves);
                        ModConfigs.DebugLog.info("Loaded graves from tpatool_graves.json");
                    }
                }
            } catch (IOException e) {
                ModConfigs.DebugLog.error("Failed to load graves: {}", e.getMessage());
            }
        }
    }

    //保存死亡位置
    public static void saveGraves() {
        if (DatabaseManager.isUsingMySQL()) {
            try (Connection conn = DatabaseManager.getConnection()) {
                // 清除现有数据
                conn.createStatement().execute("DELETE FROM player_graves");
                
                String insertGrave = "INSERT INTO player_graves (player_uuid, dimension, x, y, z, x_rot, y_rot) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertGrave)) {
                    for (Map.Entry<UUID, BackHandler.PlayerPosition> entry : gravePositions.entrySet()) {
                        UUID uuid = entry.getKey();
                        BackHandler.PlayerPosition position = entry.getValue();
                        
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, position.dimension.toString());
                        stmt.setDouble(3, position.x);
                        stmt.setDouble(4, position.y);
                        stmt.setDouble(5, position.z);
                        stmt.setFloat(6, position.xRot);
                        stmt.setFloat(7, position.yRot);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                ModConfigs.DebugLog.info("Saved graves to MySQL");
            } catch (SQLException e) {
                ModConfigs.DebugLog.error("Failed to save graves to MySQL: {}", e.getMessage());
            }
        } else {
            try {
                Path path = ModConfigs.getConfigDir().resolve("tpatool_graves.json");
                Files.writeString(path, GSON.toJson(gravePositions));
                ModConfigs.DebugLog.info("Saved graves to tpatool_graves.json");
            } catch (IOException e) {
                ModConfigs.DebugLog.error("Failed to save graves: {}", e.getMessage());
            }
        }
    }

    //注册指令
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("grave")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "grave"))
                        .executes(context -> {
                            try {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ModConfigs.DebugLog.info("Executing /grave command for player: {}", player.getName().getString());
                                return teleportToGrave(player);
                            } catch (Exception e) {
                                ModConfigs.DebugLog.error("Unexpected error in /grave command: ", e);
                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                return 0;
                            }
                        })
        );
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            gravePositions.put(player.getUUID(), new BackHandler.PlayerPosition(
                    player.serverLevel(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            ));
            saveGraves();
            ModConfigs.DebugLog.info("Recorded grave for {} at dimension={}, x={}, y={}, z={}",
                    player.getName().getString(), player.serverLevel().dimension().location(),
                    player.getX(), player.getY(), player.getZ());
        }
    }

    //传送栏
    private static int teleportToGrave(ServerPlayer player) {
        BackHandler.PlayerPosition pos = gravePositions.get(player.getUUID());
        if (pos == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatool.grave.no_position", "No previous death position recorded!"
            ));
            return 0;
        }
        ServerLevel level = player.getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION, pos.dimension));
        if (level == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatool.grave.invalid_dimension", "Invalid dimension for death position!"
            ));
            gravePositions.remove(player.getUUID());
            saveGraves();
            return 0;
        }
        BackHandler.recordPosition(player);
        // 使用支持骑乘链和拴绳链的传送方法
        ModUtils.teleportWithAllChains(player, level, pos.x, pos.y, pos.z, pos.yRot, pos.xRot);
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatool.grave.success", "Teleported to last death position."
        ));
        ModConfigs.DebugLog.info("Player {} teleported to grave at dimension={}, x={}, y={}, z={}",
                player.getName().getString(), pos.dimension, pos.x, pos.y, pos.z);
        return 1;
    }
}