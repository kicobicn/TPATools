package com.kicobicn.TPATools.Commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kicobicn.TPATools.config.ModConfigs;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HomeHandler {
    private static final Map<UUID, Map<String, Home>> playerHomes = new HashMap<>();
    private static final Map<String, Map<String, PublicHomeInfo>> publicHomesByOwner = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    //PublicHomeInfo 类，用于存储公开家的 UUID 和用户名
    private static class PublicHomeInfo {
        public UUID ownerUUID;
        public String ownerName;
        public String homeName;
        public PublicHomeInfo(UUID ownerUUID, String ownerName, String homeName) {
            this.ownerUUID = ownerUUID;
            this.ownerName = ownerName;
            this.homeName = homeName;
        }
    }

    // 存储最后位置的映射
    private static final Map<UUID, Home.Position> lastPositions = new HashMap<>();

    public static class Home {
        public static class Position {
            public double x, y, z;
            public float xRot, yRot;
            public ResourceLocation dimension; // 确认类型为 ResourceLocation
            public Position(double x, double y, double z, float xRot, float yRot, ResourceLocation dimension) {
                this.x = x; this.y = y; this.z = z;
                this.xRot = xRot; this.yRot = yRot; this.dimension = dimension;
            }
        }
        public Position position;
        public List<UUID> sharedPlayers;
        public Home(Position position, List<UUID> sharedPlayers) {
            this.position = position;
            this.sharedPlayers = sharedPlayers != null ? sharedPlayers : new ArrayList<>();
        }
    }

    private static void recordLastPosition(ServerPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        lastPositions.put(player.getUUID(), new Home.Position(
                player.getX(), player.getY(), player.getZ(),
                player.getXRot(), player.getYRot(), dimension
        ));
    }


    public static void loadHomes() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool_homes.json"); // 使用统一配置目录
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<String, Object> data = GSON.fromJson(jsonContent, new TypeToken<Map<String, Object>>(){}.getType());
                playerHomes.clear();
                publicHomesByOwner.clear();

                if (data != null) {
                    // 加载 playerHomes
                    Map<UUID, Map<String, Home>> loadedHomes = GSON.fromJson(
                            GSON.toJson(data.get("playerHomes")), new TypeToken<Map<UUID, Map<String, Home>>>(){}.getType()
                    );
                    if (loadedHomes != null) {
                        playerHomes.putAll(loadedHomes);
                    }

                    // 加载 publicHomesByOwner
                    Map<String, Map<String, PublicHomeInfo>> loadedPublicHomes = GSON.fromJson(
                            GSON.toJson(data.get("publicHomesByOwner")),
                            new TypeToken<Map<String, Map<String, PublicHomeInfo>>>(){}.getType()
                    );
                    if (loadedPublicHomes != null) {
                        publicHomesByOwner.putAll(loadedPublicHomes);
                    }

                    ModConfigs.DebugLog.info("Loaded homes and public homes from {}", path.toString());
                }
            }
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to load homes: {}", e.getMessage());
        }
    }

    public static void saveHomes() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool_homes.json"); // 使用统一配置目录
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            Map<String, Object> data = new HashMap<>();
            data.put("playerHomes", playerHomes);

            // 公开家园的保存格式
            Map<String, Map<String, PublicHomeInfo>> publicHomesData = new HashMap<>();
            for (Map.Entry<String, Map<String, PublicHomeInfo>> ownerEntry : publicHomesByOwner.entrySet()) {
                String ownerUUID = ownerEntry.getKey();
                Map<String, PublicHomeInfo> homes = ownerEntry.getValue();

                Map<String, PublicHomeInfo> ownerPublicHomes = new HashMap<>();
                for (Map.Entry<String, PublicHomeInfo> homeEntry : homes.entrySet()) {
                    String homeName = homeEntry.getKey();
                    PublicHomeInfo info = homeEntry.getValue();

                    // 确保所有者名称是最新的
                    String ownerName = info.ownerName != null ? info.ownerName :
                            (server != null && server.getProfileCache() != null ?
                                    server.getProfileCache().get(UUID.fromString(ownerUUID))
                                            .map(GameProfile::getName).orElse("Unknown") : "Unknown");

                    ownerPublicHomes.put(homeName, new PublicHomeInfo(
                            UUID.fromString(ownerUUID), ownerName, homeName
                    ));
                }
                publicHomesData.put(ownerUUID, ownerPublicHomes);
            }
            data.put("publicHomesByOwner", publicHomesData);

            Files.writeString(path, GSON.toJson(data));
            ModConfigs.DebugLog.info("Saved homes and public homes to {}", path.toString());
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to save homes: {}", e.getMessage());
        }
    }

    // Tab补全：玩家自己的家
    private static final SuggestionProvider<CommandSourceStack> OWN_HOME_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes != null) {
            for (String homeName : homes.keySet()) {
                builder.suggest(homeName);
            }
        }
        return builder.buildFuture();
    };


    // Tab补全：公开的家
    private static final SuggestionProvider<CommandSourceStack> PUBLIC_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());

            if (ownerPublicHomes != null) {
                for (String homeName : ownerPublicHomes.keySet()) {
                    builder.suggest(homeName);
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
    };

    // Tab补全：非公开的家
    private static final SuggestionProvider<CommandSourceStack> NON_PUBLIC_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Map<String, Home> homes = playerHomes.get(player.getUUID());
            Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());

            if (homes != null) {
                for (String homeName : homes.keySet()) {
                    // 检查家园是否不是公开的
                    if (ownerPublicHomes == null || !ownerPublicHomes.containsKey(homeName)) {
                        builder.suggest(homeName);
                    }
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
    };

    // Tab补全：可访问的家（自己的）
    private static final SuggestionProvider<CommandSourceStack> ACCESSIBLE_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Map<String, Home> homes = playerHomes.get(player.getUUID());
            if (homes != null) {
                for (String homeName : homes.keySet()) {
                    builder.suggest(homeName);
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            ModConfigs.DebugLog.error("Failed to provide suggestions for /home tp: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    // Tab补全：/home otherhome 的 playername:homename
    private static final SuggestionProvider<CommandSourceStack> OTHER_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            // 处理公开家园
            for (Map.Entry<String, Map<String, PublicHomeInfo>> ownerEntry : publicHomesByOwner.entrySet()) {
                String ownerUUIDStr = ownerEntry.getKey();
                Map<String, PublicHomeInfo> ownerHomes = ownerEntry.getValue();

                UUID ownerUUID = UUID.fromString(ownerUUIDStr);
                String ownerName = player.getServer().getProfileCache().get(ownerUUID)
                        .map(GameProfile::getName).orElse("Unknown");

                for (String homeName : ownerHomes.keySet()) {
                    builder.suggest(ownerName + ":" + homeName);
                }
            }

            // 处理私有分享的家园
            for (Map.Entry<UUID, Map<String, Home>> entry : playerHomes.entrySet()) {
                UUID ownerUUID = entry.getKey();
                if (!ownerUUID.equals(player.getUUID())) {
                    String ownerName = player.getServer().getProfileCache().get(ownerUUID)
                            .map(GameProfile::getName).orElse("Unknown");
                    for (Map.Entry<String, Home> homeEntry : entry.getValue().entrySet()) {
                        if (homeEntry.getValue().sharedPlayers.contains(player.getUUID())) {
                            builder.suggest(ownerName + ":" + homeEntry.getKey());
                        }
                    }
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            ModConfigs.DebugLog.error("Failed to provide suggestions for /home otherhome: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    // Tab补全：/home sharelist 的 in/out 参数
    private static final SuggestionProvider<CommandSourceStack> SHARELIST_SUGGESTIONS = (context, builder) -> {
        builder.suggest("in");
        builder.suggest("out");
        return builder.buildFuture();
    };

    // Tab 补全：/home unshare 的玩家名（支持离线玩家）
    private static final SuggestionProvider<CommandSourceStack> PLAYER_NAME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server != null) {
                // 从 playerHomes 获取所有 UUID，转换为玩家名
                for (UUID uuid : playerHomes.keySet()) {
                    server.getProfileCache().get(uuid)
                            .map(GameProfile::getName)
                            .ifPresent(builder::suggest);
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            ModConfigs.DebugLog.error("Failed to provide player name suggestions: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("home")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "home"))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(ACCESSIBLE_HOME_SUGGESTIONS)
                                        .executes(context -> teleportToHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> setHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("list")
                                .executes(context -> listHomes(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .executes(context -> removeHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .then(Commands.argument("newName", StringArgumentType.string())
                                                .executes(context -> renameHome(
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "newName")
                                                )))))
                        .then(Commands.literal("share")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> shareHome(
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "name"),
                                                        EntityArgument.getPlayer(context, "player")
                                                )))))
                        .then(Commands.literal("public")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(NON_PUBLIC_HOME_SUGGESTIONS)
                                        .executes(context -> setPublicHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("private")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(PUBLIC_HOME_SUGGESTIONS)
                                        .executes(context -> setPrivateHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("otherhome")
                                .then(Commands.argument("home", StringArgumentType.greedyString())
                                        .suggests(OTHER_HOME_SUGGESTIONS)
                                        .executes(context -> teleportToOtherHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "home")))))
                        .then(Commands.literal("otherlist")
                                .executes(context -> listOtherHomes(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("sharelist")
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .suggests(SHARELIST_SUGGESTIONS)
                                        .executes(context -> shareList(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "type")
                                        ))))
                        .then(Commands.literal("unshare")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .executes(context -> unshareHome(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "name"),
                                                null,
                                                null
                                        ))
                                        .then(Commands.argument("player", StringArgumentType.string())
                                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                                .executes(context -> unshareHome(
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "player"),
                                                        context.getSource()
                                                )))))


        );
    }

    private static int setHome(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        if (homes.size() >= ModConfigs.MAX_HOMES.get()) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.limit_exceeded", "You have reached the maximum number of homes (%s)!", ModConfigs.MAX_HOMES.get()
            ));
            return 0;
        }
        if (homes.containsKey(homeName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.name_exists", "A home named %s already exists!", homeName
            ));
            return 0;
        }
        ResourceLocation dimension = player.level().dimension().location();
        Home.Position position = new Home.Position(
                player.getX(), player.getY(), player.getZ(),
                player.getXRot(), player.getYRot(), dimension
        );
        homes.put(homeName, new Home(position, new ArrayList<>()));
        saveHomes();
        player.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.set", "Home %s set at your current position.", homeName
        ));
        ModConfigs.DebugLog.info("Player {} set home {} at {}", player.getName().getString(), homeName, dimension);
        return 1;
    }


    private static int teleportToHome(ServerPlayer player, String name) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes != null && homes.containsKey(name)) {
            Home home = homes.get(name);
            ServerLevel level = player.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, home.position.dimension));
            if (level == null) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.home.invalid_dimension", "Invalid dimension for home %s!", name
                ));
                return 0;
            }
            BackHandler.recordPosition(player);
            player.teleportTo(level, home.position.x, home.position.y, home.position.z,
                    home.position.yRot, home.position.xRot);
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.teleported", "Teleported to home %s.", name
            ));
            ModConfigs.DebugLog.info("Player {} teleported to home {} at dimension={}, x={}, y={}, z={}",
                    player.getName().getString(), name, home.position.dimension,
                    home.position.x, home.position.y, home.position.z);
            return 1;
        }
        return teleportToOtherHome(player, name); // 尝试作为他人家处理
    }

    private static int listHomes(ServerPlayer player) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || homes.isEmpty()) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.list_empty", "You have no homes set."
            ));
            return 0;
        }
        player.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.list", "Your homes:"
        ));
        for (Map.Entry<String, Home> entry : homes.entrySet()) {
            Home home = entry.getValue();
            player.sendSystemMessage(Component.literal(String.format(
                    "- %s: %s (x=%.2f, y=%.2f, z=%.2f)",
                    entry.getKey(), home.position.dimension, home.position.x, home.position.y, home.position.z
            )));
        }
        return 1;
    }

    private static int shareList(ServerPlayer player, String type) {
        try {
            if (!type.equals("in") && !type.equals("out")) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.sharelist.invalid_type", "Invalid type! Use 'in' or 'out'."
                ));
                return 0;
            }
            if (type.equals("in")) {
                boolean found = false;
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.sharelist.in", "Homes shared with you:"
                ));
                for (Map.Entry<UUID, Map<String, Home>> entry : playerHomes.entrySet()) {
                    UUID ownerUUID = entry.getKey();
                    String ownerName = player.getServer().getProfileCache().get(ownerUUID)
                            .map(GameProfile::getName).orElse("Unknown");
                    for (Map.Entry<String, Home> homeEntry : entry.getValue().entrySet()) {
                        String homeName = homeEntry.getKey();
                        Home home = homeEntry.getValue();
                        if (home.sharedPlayers.contains(player.getUUID())) {
                            String dimensionName = home.position.dimension != null
                                    ? home.position.dimension.toString()
                                    : "unknown";
                            player.sendSystemMessage(Component.literal(String.format(
                                    "- %s (%s): %s (x=%.2f, y=%.2f, z=%.2f)",
                                    homeName, ownerName, dimensionName,
                                    home.position.x, home.position.y, home.position.z
                            )));
                            found = true;
                        }
                    }
                }
                if (!found) {
                    player.sendSystemMessage(ModConfigs.translateWithFallback(
                            "command.tpatool.sharelist.in_empty", "No homes are shared with you."
                    ));
                    return 0;
                }
                return 1;
            } else {
                Map<String, Home> homes = playerHomes.get(player.getUUID());
                if (homes == null || homes.isEmpty()) {
                    player.sendSystemMessage(ModConfigs.translateWithFallback(
                            "command.tpatool.sharelist.out_empty", "You have not shared any homes."
                    ));
                    return 0;
                }
                boolean found = false;
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.sharelist.out", "Homes you have shared:"
                ));
                for (Map.Entry<String, Home> homeEntry : homes.entrySet()) {
                    String homeName = homeEntry.getKey();
                    Home home = homeEntry.getValue();
                    if (!home.sharedPlayers.isEmpty()) {
                        List<String> sharedPlayerNames = new ArrayList<>();
                        for (UUID sharedUUID : home.sharedPlayers) {
                            String playerName = player.getServer().getProfileCache().get(sharedUUID)
                                    .map(GameProfile::getName).orElse("Unknown");
                            sharedPlayerNames.add(playerName);
                        }
                        String dimensionName = home.position.dimension != null
                                ? home.position.dimension.toString()
                                : "unknown";
                        player.sendSystemMessage(Component.literal(String.format(
                                "- %s: Shared with %s (%s, x=%.2f, y=%.2f, z=%.2f)",
                                homeName, String.join(", ", sharedPlayerNames),
                                dimensionName, home.position.x, home.position.y, home.position.z
                        )));
                        found = true;
                    }
                }
                if (!found) {
                    player.sendSystemMessage(ModConfigs.translateWithFallback(
                            "command.tpatool.sharelist.out_empty", "You have not shared any homes."
                    ));
                    return 0;
                }
                return 1;
            }
        } catch (Exception e) {
            ModConfigs.DebugLog.error("Error executing /home sharelist {}: {}", type, e.getMessage());
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.error", "An unexpected error occurred while executing the command."
            ));
            return 0;
        }
    }


    private static int removeHome(ServerPlayer player, String name) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(name)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", name
            ));
            return 0;
        }

        homes.remove(name);

        // 同时从公开家园列表中移除
        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes != null) {
            ownerPublicHomes.remove(name);
            if (ownerPublicHomes.isEmpty()) {
                publicHomesByOwner.remove(player.getUUID().toString());
            }
        }

        if (homes.isEmpty()) {
            playerHomes.remove(player.getUUID());
        }

        saveHomes();
        player.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.removed", "Home %s removed.", name
        ));
        ModConfigs.DebugLog.info("Player {} removed home {}", player.getName().getString(), name);
        return 1;
    }

    private static int renameHome(ServerPlayer player, String oldName, String newName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(oldName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", oldName
            ));
            return 0;
        }
        if (homes.containsKey(newName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.name_exists", "A home named %s already exists!", newName
            ));
            return 0;
        }

        Home home = homes.remove(oldName);
        homes.put(newName, home);

        // 更新公开家园列表中的名称（如果这个家园是公开的）
        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes != null && ownerPublicHomes.containsKey(oldName)) {
            PublicHomeInfo info = ownerPublicHomes.remove(oldName);
            // 使用三个参数的构造函数
            ownerPublicHomes.put(newName, new PublicHomeInfo(info.ownerUUID, info.ownerName, newName));
        }

        saveHomes();
        player.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.renamed", "Home %s renamed to %s.", oldName, newName
        ));
        ModConfigs.DebugLog.info("Player {} renamed home {} to {}", player.getName().getString(), oldName, newName);
        return 1;
    }


    private static int shareHome(ServerPlayer player, String name, ServerPlayer target) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(name)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", name
            ));
            return 0;
        }
        Home home = homes.get(name);
        if (home.sharedPlayers.contains(target.getUUID())) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.already_shared", "Home %s is already shared with %s!", name, target.getName()
            ));
            return 0;
        }
        home.sharedPlayers.add(target.getUUID());
        saveHomes();
        player.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.shared", "Home %s shared with %s.", name, target.getName()
        ));
        target.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.shared_received", "%s shared their home %s with you.", player.getName(), name
        ));
        ModConfigs.DebugLog.info("Player {} shared home {} with {}", player.getName().getString(), name, target.getName().getString());
        return 1;
    }

    //unshareHome 方法
    private static int unshareHome(ServerPlayer player, String homeName, String targetPlayerName, CommandSourceStack source) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }
        Home home = homes.get(homeName);
        if (targetPlayerName == null) {
            if (home.sharedPlayers.isEmpty()) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.unshare.no_players", "Home %s is not shared with anyone!", homeName
                ));
                return 0;
            }
            home.sharedPlayers.clear();
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.unshare.all", "Removed sharing of home %s for all players.", homeName
            ));
            saveHomes();
            return 1;
        } else {
            MinecraftServer server = player.getServer();
            Optional<GameProfile> profile = server.getProfileCache().get(targetPlayerName);
            if (profile.isEmpty()) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.unshare.player_not_found", "Player %s not found!", targetPlayerName
                ));
                return 0;
            }
            UUID targetUUID = profile.get().getId();
            if (!home.sharedPlayers.contains(targetUUID)) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.unshare.not_shared", "Home %s is not shared with %s!", homeName, targetPlayerName
                ));
                return 0;
            }
            home.sharedPlayers.remove(targetUUID);
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.unshare.player", "Removed sharing of home %s with %s.", homeName, targetPlayerName
            ));
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUUID);
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.unshare.notify", "%s has removed sharing of their home %s with you.", player.getName().getString(), homeName
                ));
            }
            saveHomes();
            return 1;
        }
    }


    private static int setPublicHome(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }

        // 检查是否已经是公开的
        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes != null && ownerPublicHomes.containsKey(homeName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.already_public", "Home %s is already public!", homeName
            ));
            return 0;
        }

        // 添加公开家园信息 - 使用三个参数的构造函数
        String ownerName = player.getName().getString();
        PublicHomeInfo info = new PublicHomeInfo(player.getUUID(), ownerName, homeName);

        // 使用玩家UUID作为外层键，家园名称作为内层键
        publicHomesByOwner.computeIfAbsent(player.getUUID().toString(), k -> new HashMap<>())
                .put(homeName, info);

        saveHomes();
        player.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.publicized", "Home %s is now public.", homeName
        ));
        ModConfigs.DebugLog.info("Player {} set home {} as public.", player.getName().getString(), homeName);
        return 1;
    }

    private static int setPrivateHome(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }

        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes == null || !ownerPublicHomes.containsKey(homeName)) {
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.not_public", "Home %s is not public!", homeName
            ));
            return 0;
        }

        // 移除公开家园信息
        ownerPublicHomes.remove(homeName);
        if (ownerPublicHomes.isEmpty()) {
            publicHomesByOwner.remove(player.getUUID().toString());
        }

        saveHomes();
        player.sendSystemMessage(ModConfigs.translateWithFallback(
                "command.tpatool.home.privatized", "Home %s is no longer public.", homeName
        ));
        ModConfigs.DebugLog.info("Player {} set home {} as private.", player.getName().getString(), homeName);
        return 1;
    }

    private static int teleportToOtherHome(ServerPlayer player, String homeArg) {
        try {
            String[] parts = homeArg.split(":", 2);
            if (parts.length != 2) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.home.invalid_format", "Invalid format! Use playername:homename."
                ));
                return 0;
            }
            String ownerName = parts[0];
            String homeName = parts[1];

            Optional<GameProfile> profile = player.getServer().getProfileCache().get(ownerName);
            if (profile.isEmpty()) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }
            UUID ownerUUID = profile.get().getId();

            // 检查是否是公开家园
            Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(ownerUUID.toString());
            boolean isPublic = ownerPublicHomes != null && ownerPublicHomes.containsKey(homeName);

            // 检查是否是分享的家园
            Map<String, Home> homes = playerHomes.get(ownerUUID);
            if (homes == null || !homes.containsKey(homeName)) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }
            Home home = homes.get(homeName);

            if (!isPublic && !home.sharedPlayers.contains(player.getUUID())) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }

            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, home.position.dimension);
            ServerLevel targetLevel = player.getServer().getLevel(dimensionKey);
            if (targetLevel == null) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.home.invalid_dimension", "Invalid dimension for home %s!", homeArg
                ));
                return 0;
            }

            recordLastPosition(player);
            player.teleportTo(
                    targetLevel,
                    home.position.x, home.position.y, home.position.z,
                    home.position.xRot, home.position.yRot
            );
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.other_teleported", "Teleported to %s's home %s.", ownerName, homeName
            ));
            ModConfigs.DebugLog.info("Player {} teleported to {}'s home {}", player.getName().getString(), ownerName, homeName);
            return 1;
        } catch (Exception e) {
            ModConfigs.DebugLog.error("Error executing /home otherhome {}: {}", homeArg, e.getMessage());
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.error", "An unexpected error occurred while executing the command."
            ));
            return 0;
        }
    }



    private static int listOtherHomes(ServerPlayer player) {
        try {
            if (publicHomesByOwner.isEmpty()) {
                player.sendSystemMessage(ModConfigs.translateWithFallback(
                        "command.tpatool.home.otherlist_empty", "No public or shared homes available."
                ));
                return 0;
            }
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.home.otherlist", "Public homes:"
            ));

            for (Map.Entry<String, Map<String, PublicHomeInfo>> ownerEntry : publicHomesByOwner.entrySet()) {
                String ownerUUIDStr = ownerEntry.getKey();
                Map<String, PublicHomeInfo> homes = ownerEntry.getValue();

                UUID ownerUUID = UUID.fromString(ownerUUIDStr);
                String ownerName = player.getServer().getProfileCache().get(ownerUUID)
                        .map(GameProfile::getName).orElse("Unknown");

                for (Map.Entry<String, PublicHomeInfo> homeEntry : homes.entrySet()) {
                    String homeName = homeEntry.getKey();
                    PublicHomeInfo info = homeEntry.getValue();

                    Map<String, Home> ownerHomes = playerHomes.get(ownerUUID);
                    if (ownerHomes != null && ownerHomes.containsKey(homeName)) {
                        Home home = ownerHomes.get(homeName);
                        String dimensionName = home.position.dimension != null
                                ? home.position.dimension.toString()
                                : "unknown";
                        player.sendSystemMessage(Component.literal(String.format(
                                "- %s (%s): %s (x=%.2f, y=%.2f, z=%.2f)",
                                homeName, ownerName, dimensionName,
                                home.position.x, home.position.y, home.position.z
                        )));
                    }
                }
            }
            return 1;
        } catch (Exception e) {
            ModConfigs.DebugLog.error("Error executing /home otherlist: {}", e.getMessage());
            player.sendSystemMessage(ModConfigs.translateWithFallback(
                    "command.tpatool.error", "An unexpected error occurred while executing the command."
            ));
            return 0;
        }
    }
}