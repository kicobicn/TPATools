package com.kicobicn.TPATools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HomeHandler {
    private static final Logger LOGGER = LogManager.getLogger("TPAtool");
    private static final Map<UUID, Map<String, Home>> playerHomes = new HashMap<>();
    private static final Map<String, PublicHomeInfo> publicHomes = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    //PublicHomeInfo 类，用于存储公开家的 UUID 和用户名
    private static class PublicHomeInfo {
        public UUID ownerUUID;
        public String ownerName;
        public PublicHomeInfo(UUID ownerUUID, String ownerName) {
            this.ownerUUID = ownerUUID;
            this.ownerName = ownerName;
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
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool_homes.json");
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<String, Object> data = GSON.fromJson(jsonContent, new TypeToken<Map<String, Object>>(){}.getType());
                playerHomes.clear();
                publicHomes.clear();
                if (data != null) {
                    // 加载 playerHomes
                    Map<UUID, Map<String, Home>> loadedHomes = GSON.fromJson(
                            GSON.toJson(data.get("playerHomes")), new TypeToken<Map<UUID, Map<String, Home>>>(){}.getType()
                    );
                    if (loadedHomes != null) {
                        playerHomes.putAll(loadedHomes);
                    }
                    // 加载 publicHomes
                    Map<String, PublicHomeInfo> loadedPublicHomes = GSON.fromJson(
                            GSON.toJson(data.get("publicHomes")), new TypeToken<Map<String, PublicHomeInfo>>(){}.getType()
                    );
                    if (loadedPublicHomes != null) {
                        publicHomes.putAll(loadedPublicHomes);
                    }
                    LOGGER.info("Loaded homes and public homes from tpatool_homes.json");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load homes: {}", e.getMessage());
        }
    }


    public static void saveHomes() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool_homes.json");
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            Map<String, Object> data = new HashMap<>();
            data.put("playerHomes", playerHomes);
            Map<String, PublicHomeInfo> publicHomesData = new HashMap<>();
            for (Map.Entry<String, PublicHomeInfo> entry : publicHomes.entrySet()) {
                String homeName = entry.getKey();
                PublicHomeInfo info = entry.getValue();
                String ownerName = info.ownerName != null ? info.ownerName :
                        (server != null && server.getProfileCache() != null ?
                                server.getProfileCache().get(info.ownerUUID)
                                        .map(GameProfile::getName).orElse("Unknown") : "Unknown");
                publicHomesData.put(homeName, new PublicHomeInfo(info.ownerUUID, ownerName));
            }
            data.put("publicHomes", publicHomesData);
            Files.writeString(path, GSON.toJson(data));
            LOGGER.info("Saved homes and public homes to tpatool_homes.json");
        } catch (IOException e) {
            LOGGER.error("Failed to save homes: {}", e.getMessage());
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
            Map<String, Home> homes = playerHomes.get(player.getUUID());
            if (homes != null) {
                for (String homeName : homes.keySet()) {
                    if (publicHomes.containsKey(homeName) && publicHomes.get(homeName).equals(player.getUUID())) {
                        builder.suggest(homeName);
                    }
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
            if (homes != null) {
                for (String homeName : homes.keySet()) {
                    if (!publicHomes.containsKey(homeName)) {
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
            LOGGER.error("Failed to provide suggestions for /home tp: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    // Tab补全：/home otherhome 的 playername:homename
    private static final SuggestionProvider<CommandSourceStack> OTHER_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            for (Map.Entry<String, PublicHomeInfo> entry : publicHomes.entrySet()) {
                String homeName = entry.getKey();
                UUID ownerUUID = entry.getValue().ownerUUID;
                String ownerName = entry.getValue().ownerName != null ? entry.getValue().ownerName :
                        player.getServer().getProfileCache().get(ownerUUID)
                                .map(GameProfile::getName).orElse("Unknown");
                builder.suggest(ownerName + ":" + homeName);
            }
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
            LOGGER.error("Failed to provide suggestions for /home otherhome: {}", e.getMessage());
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
            LOGGER.error("Failed to provide player name suggestions: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        int homePermLevel = TPAHandler.commandPermissions.getOrDefault("home", false) ? 2 : 0;
        event.getDispatcher().register(
                Commands.literal("home")
                        .requires(source -> source.hasPermission(homePermLevel))
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
        if (homes.size() >= TPAHandler.MAX_HOMES.get()) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.limit_exceeded", "You have reached the maximum number of homes (%s)!", TPAHandler.MAX_HOMES.get()
            ));
            return 0;
        }
        if (homes.containsKey(homeName)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
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
        player.sendSystemMessage(TPAHandler.translateWithFallback(
                "command.tpatool.home.set", "Home %s set at your current position.", homeName
        ));
        LOGGER.info("Player {} set home {} at {}", player.getName().getString(), homeName, dimension);
        return 1;
    }


    private static int teleportToHome(ServerPlayer player, String name) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes != null && homes.containsKey(name)) {
            Home home = homes.get(name);
            ServerLevel level = player.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, home.position.dimension));
            if (level == null) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.home.invalid_dimension", "Invalid dimension for home %s!", name
                ));
                return 0;
            }
            BackHandler.recordPosition(player);
            player.teleportTo(level, home.position.x, home.position.y, home.position.z,
                    home.position.yRot, home.position.xRot);
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.teleported", "Teleported to home %s.", name
            ));
            LOGGER.info("Player {} teleported to home {} at dimension={}, x={}, y={}, z={}",
                    player.getName().getString(), name, home.position.dimension,
                    home.position.x, home.position.y, home.position.z);
            return 1;
        }
        return teleportToOtherHome(player, name); // 尝试作为他人家处理
    }

    private static int listHomes(ServerPlayer player) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || homes.isEmpty()) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.list_empty", "You have no homes set."
            ));
            return 0;
        }
        player.sendSystemMessage(TPAHandler.translateWithFallback(
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
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.sharelist.invalid_type", "Invalid type! Use 'in' or 'out'."
                ));
                return 0;
            }
            if (type.equals("in")) {
                boolean found = false;
                player.sendSystemMessage(TPAHandler.translateWithFallback(
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
                    player.sendSystemMessage(TPAHandler.translateWithFallback(
                            "command.tpatool.sharelist.in_empty", "No homes are shared with you."
                    ));
                    return 0;
                }
                return 1;
            } else {
                Map<String, Home> homes = playerHomes.get(player.getUUID());
                if (homes == null || homes.isEmpty()) {
                    player.sendSystemMessage(TPAHandler.translateWithFallback(
                            "command.tpatool.sharelist.out_empty", "You have not shared any homes."
                    ));
                    return 0;
                }
                boolean found = false;
                player.sendSystemMessage(TPAHandler.translateWithFallback(
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
                    player.sendSystemMessage(TPAHandler.translateWithFallback(
                            "command.tpatool.sharelist.out_empty", "You have not shared any homes."
                    ));
                    return 0;
                }
                return 1;
            }
        } catch (Exception e) {
            LOGGER.error("Error executing /home sharelist {}: {}", type, e.getMessage());
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.error", "An unexpected error occurred while executing the command."
            ));
            return 0;
        }
    }


    private static int removeHome(ServerPlayer player, String name) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(name)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", name
            ));
            return 0;
        }
        homes.remove(name);
        publicHomes.remove(name);
        if (homes.isEmpty()) {
            playerHomes.remove(player.getUUID());
        }
        saveHomes();
        player.sendSystemMessage(TPAHandler.translateWithFallback(
                "command.tpatool.home.removed", "Home %s removed.", name
        ));
        LOGGER.info("Player {} removed home {}", player.getName().getString(), name);
        return 1;
    }

    private static int renameHome(ServerPlayer player, String oldName, String newName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(oldName)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", oldName
            ));
            return 0;
        }
        if (homes.containsKey(newName)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.name_exists", "A home named %s already exists!", newName
            ));
            return 0;
        }
        Home home = homes.remove(oldName);
        homes.put(newName, home);
        PublicHomeInfo publicInfo = publicHomes.remove(oldName);
        if (publicInfo != null) {
            publicHomes.put(newName, new PublicHomeInfo(publicInfo.ownerUUID, publicInfo.ownerName));
        }
        saveHomes();
        player.sendSystemMessage(TPAHandler.translateWithFallback(
                "command.tpatool.home.renamed", "Home %s renamed to %s.", oldName, newName
        ));
        LOGGER.info("Player {} renamed home {} to {}", player.getName().getString(), oldName, newName);
        return 1;
    }


    private static int shareHome(ServerPlayer player, String name, ServerPlayer target) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(name)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", name
            ));
            return 0;
        }
        Home home = homes.get(name);
        if (home.sharedPlayers.contains(target.getUUID())) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.already_shared", "Home %s is already shared with %s!", name, target.getName()
            ));
            return 0;
        }
        home.sharedPlayers.add(target.getUUID());
        saveHomes();
        player.sendSystemMessage(TPAHandler.translateWithFallback(
                "command.tpatool.home.shared", "Home %s shared with %s.", name, target.getName()
        ));
        target.sendSystemMessage(TPAHandler.translateWithFallback(
                "command.tpatool.home.shared_received", "%s shared their home %s with you.", player.getName(), name
        ));
        LOGGER.info("Player {} shared home {} with {}", player.getName().getString(), name, target.getName().getString());
        return 1;
    }

    //unshareHome 方法
    private static int unshareHome(ServerPlayer player, String homeName, String targetPlayerName, CommandSourceStack source) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }
        Home home = homes.get(homeName);
        if (targetPlayerName == null) {
            if (home.sharedPlayers.isEmpty()) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.unshare.no_players", "Home %s is not shared with anyone!", homeName
                ));
                return 0;
            }
            home.sharedPlayers.clear();
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.unshare.all", "Removed sharing of home %s for all players.", homeName
            ));
            saveHomes();
            return 1;
        } else {
            MinecraftServer server = player.getServer();
            Optional<GameProfile> profile = server.getProfileCache().get(targetPlayerName);
            if (profile.isEmpty()) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.unshare.player_not_found", "Player %s not found!", targetPlayerName
                ));
                return 0;
            }
            UUID targetUUID = profile.get().getId();
            if (!home.sharedPlayers.contains(targetUUID)) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.unshare.not_shared", "Home %s is not shared with %s!", homeName, targetPlayerName
                ));
                return 0;
            }
            home.sharedPlayers.remove(targetUUID);
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.unshare.player", "Removed sharing of home %s with %s.", homeName, targetPlayerName
            ));
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUUID);
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(TPAHandler.translateWithFallback(
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
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }
        if (publicHomes.containsKey(homeName)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.already_public", "Home %s is already public!", homeName
            ));
            return 0;
        }
        String ownerName = player.getName().getString();
        publicHomes.put(homeName, new PublicHomeInfo(player.getUUID(), ownerName));
        saveHomes();
        player.sendSystemMessage(TPAHandler.translateWithFallback(
                "command.tpatool.home.publicized", "Home %s is now public.", homeName
        ));
        LOGGER.info("Player {} set home {} as public.", player.getName().getString(), homeName);
        return 1;
    }

    private static int setPrivateHome(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }
        if (!publicHomes.containsKey(homeName)) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_public", "Home %s is not public!", homeName
            ));
            return 0;
        }
        PublicHomeInfo info = publicHomes.get(homeName);
        if (!info.ownerUUID.equals(player.getUUID())) {
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.not_owner", "You are not the owner of home %s!", homeName
            ));
            return 0;
        }
        publicHomes.remove(homeName);
        saveHomes();
        player.sendSystemMessage(TPAHandler.translateWithFallback(
                "command.tpatool.home.privatized", "Home %s is no longer public.", homeName
        ));
        LOGGER.info("Player {} set home {} as private.", player.getName().getString(), homeName);
        return 1;
    }

    private static int teleportToOtherHome(ServerPlayer player, String homeArg) {
        try {
            String[] parts = homeArg.split(":", 2);
            if (parts.length != 2) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.home.invalid_format", "Invalid format! Use playername:homename."
                ));
                return 0;
            }
            String ownerName = parts[0];
            String homeName = parts[1];
            Optional<GameProfile> profile = player.getServer().getProfileCache().get(ownerName);
            if (profile.isEmpty()) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }
            UUID ownerUUID = profile.get().getId();
            Map<String, Home> homes = playerHomes.get(ownerUUID);
            if (homes == null || !homes.containsKey(homeName)) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }
            Home home = homes.get(homeName);
            PublicHomeInfo publicInfo = publicHomes.get(homeName);
            if (publicInfo == null || !publicInfo.ownerUUID.equals(ownerUUID)) {
                if (!home.sharedPlayers.contains(player.getUUID())) {
                    player.sendSystemMessage(TPAHandler.translateWithFallback(
                            "command.tpatool.home.other_not_found", "Home %s not found or not accessible!", homeArg
                    ));
                    return 0;
                }
            }
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, home.position.dimension);
            ServerLevel targetLevel = player.getServer().getLevel(dimensionKey);
            if (targetLevel == null) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.home.invalid_dimension", "Invalid dimension for home %s!", homeArg
                ));
                return 0;
            }
            // 假设 recordLastPosition 在 BackHandler 中
            recordLastPosition(player);
            player.teleportTo(
                    targetLevel,
                    home.position.x, home.position.y, home.position.z,
                    home.position.xRot, home.position.yRot
            );
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.other_teleported", "Teleported to %s's home %s.", ownerName, homeName
            ));
            LOGGER.info("Player {} teleported to {}'s home {}", player.getName().getString(), ownerName, homeName);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error executing /home otherhome {}: {}", homeArg, e.getMessage());
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.error", "An unexpected error occurred while executing the command."
            ));
            return 0;
        }
    }



    private static int listOtherHomes(ServerPlayer player) {
        try {
            if (publicHomes.isEmpty()) {
                player.sendSystemMessage(TPAHandler.translateWithFallback(
                        "command.tpatool.home.otherlist_empty", "No public or shared homes available."
                ));
                return 0;
            }
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.home.otherlist", "Public homes:"
            ));
            for (Map.Entry<String, PublicHomeInfo> entry : publicHomes.entrySet()) {
                String homeName = entry.getKey();
                PublicHomeInfo info = entry.getValue();
                UUID ownerUUID = info.ownerUUID;
                String ownerName = info.ownerName != null ? info.ownerName :
                        player.getServer().getProfileCache().get(ownerUUID)
                                .map(GameProfile::getName).orElse("Unknown");
                Map<String, Home> homes = playerHomes.get(ownerUUID);
                if (homes != null && homes.containsKey(homeName)) {
                    Home home = homes.get(homeName);
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
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error executing /home otherlist: {}", e.getMessage());
            player.sendSystemMessage(TPAHandler.translateWithFallback(
                    "command.tpatool.error", "An unexpected error occurred while executing the command."
            ));
            return 0;
        }
    }
}