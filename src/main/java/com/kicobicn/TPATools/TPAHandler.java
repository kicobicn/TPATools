package com.kicobicn.TPATools;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TPAHandler {
    private static final Logger LOGGER = LogManager.getLogger("TPAtool");
    private static final Map<String, String> translations = new HashMap<>();
    private static final Map<UUID, List<TPARequest>> requests = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, Boolean> toggleStates = new HashMap<>();
    private static final Map<UUID, Set<UUID>> lockedPlayers = new HashMap<>();
    static final Map<String, Boolean> commandPermissions = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final ForgeConfigSpec CONFIG;
    public static ForgeConfigSpec.LongValue COOLDOWN_TIME;
    public static ForgeConfigSpec.IntValue TIMEOUT_TICKS;
    public static ForgeConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;
    public static ForgeConfigSpec.IntValue MAX_HOMES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tpa");
        COOLDOWN_TIME = builder.comment("Cooldown time for TPA commands in milliseconds")
                .defineInRange("cooldown", 3 * 1000L, 0, Long.MAX_VALUE);
        TIMEOUT_TICKS = builder.comment("Timeout for TPA requests in ticks")
                .defineInRange("timeout", 30 * 20, 0, Integer.MAX_VALUE);
        DEFAULT_LANGUAGE = builder.comment("Default language for messages (e.g., 'en_us', 'zh_cn')")
                .define("language", "zh_cn");
        MAX_HOMES = builder.comment("Maximum number of homes per player")
                .defineInRange("max_homes", 2, 1, Integer.MAX_VALUE);
        builder.pop();
        CONFIG = builder.build();
    }

    public static class TPARequest {
        public final ServerPlayer sender;
        public final ServerPlayer target;
        public final boolean isTPHere;
        public final long timestamp;

        public TPARequest(ServerPlayer sender, ServerPlayer target, boolean isTPHere) {
            this.sender = sender;
            this.target = target;
            this.isTPHere = isTPHere;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadTranslations(DEFAULT_LANGUAGE.get());
        HomeHandler.loadHomes();
        GraveHandler.loadGraves();
        loadToggleStates();
        loadLockedPlayers();
        loadCommandPermissions();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HomeHandler.saveHomes();
        GraveHandler.saveGraves();
        saveToggleStates();
        saveLockedPlayers();
        saveCommandPermissions();
    }

    // 加载翻译
    public static void loadTranslations(String lang) {
        translations.clear();
        ResourceLocation loc = new ResourceLocation("tpatool", "lang/" + lang + ".json");
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var resource = server.getResourceManager().getResource(loc).orElseThrow();
                String jsonContent = new String(resource.open().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> loadedTranslations = GSON.fromJson(jsonContent, new TypeToken<Map<String, String>>(){}.getType());
                translations.putAll(loadedTranslations);
                LOGGER.info("Loaded translations for language: {}", lang);
            } else {
                LOGGER.warn("Server not available, using fallback translations for {}", lang);
                loadFallbackTranslations();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load translations for {}: {}, using fallback", lang, e.getMessage());
            loadFallbackTranslations();
        }
    }

    private static void loadFallbackTranslations() {
        translations.put("command.tpatool.tpa.self", "You cannot teleport to yourself!");
        translations.put("command.tpatool.tpa.cooldown", "Please wait for the cooldown (60 seconds)!");
        translations.put("command.tpatool.tpa.accept", "Accept");
        translations.put("command.tpatool.tpa.deny", "Deny");
    }

    // 加载命令权限状态
    private static void loadCommandPermissions() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool.json");
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<String, Boolean> loadedPermissions = GSON.fromJson(jsonContent, new TypeToken<Map<String, Boolean>>(){}.getType());
                commandPermissions.clear();
                if (loadedPermissions != null) {
                    commandPermissions.putAll(loadedPermissions);
                    LOGGER.info("Loaded command permissions from tpatool.json");
                }
            }
            commandPermissions.putIfAbsent("tpa", false);
            commandPermissions.putIfAbsent("home", false);
            commandPermissions.putIfAbsent("grave", false);
            commandPermissions.putIfAbsent("back", false);
        } catch (IOException e) {
            LOGGER.error("Failed to load command permissions: {}", e.getMessage());
        }
    }

    // 保存命令权限状态
    private static void saveCommandPermissions() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool.json");
            Files.writeString(path, GSON.toJson(commandPermissions));
            LOGGER.info("Saved command permissions to tpatool.json");
        } catch (IOException e) {
            LOGGER.error("Failed to save command permissions: {}", e.getMessage());
        }
    }

    // 加载免打扰状态
    private static void loadToggleStates() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool_toggles.json");
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<UUID, Boolean> loadedToggles = GSON.fromJson(jsonContent, new TypeToken<Map<UUID, Boolean>>(){}.getType());
                toggleStates.clear();
                if (loadedToggles != null) {
                    toggleStates.putAll(loadedToggles);
                    LOGGER.info("Loaded toggle states from tpatool_toggles.json");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load toggle states: {}", e.getMessage());
        }
    }

    // 保存免打扰状态
    private static void saveToggleStates() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool_toggles.json");
            Files.writeString(path, GSON.toJson(toggleStates));
            LOGGER.info("Saved toggle states to tpatool_toggles.json");
        } catch (IOException e) {
            LOGGER.error("Failed to save toggle states: {}", e.getMessage());
        }
    }

    // 加载锁定玩家列表
    private static void loadLockedPlayers() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool_locks.json");
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<UUID, Set<UUID>> loadedLocks = GSON.fromJson(jsonContent, new TypeToken<Map<UUID, Set<UUID>>>(){}.getType());
                lockedPlayers.clear();
                if (loadedLocks != null) {
                    lockedPlayers.putAll(loadedLocks);
                    LOGGER.info("Loaded locked players from tpatool_locks.json");
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load locked players: {}", e.getMessage());
        }
    }

    // 保存锁定玩家列表
    private static void saveLockedPlayers() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("tpatool_locks.json");
            Files.writeString(path, GSON.toJson(lockedPlayers));
            LOGGER.info("Saved locked players to tpatool_locks.json");
        } catch (IOException e) {
            LOGGER.error("Failed to save locked players: {}", e.getMessage());
        }
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

    // Tab补全：/tpatool needop 的 command 参数
    private static final SuggestionProvider<CommandSourceStack> COMMAND_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("tpa").suggest("home").suggest("grave").suggest("back").buildFuture();
    };

    // Tab补全：/tpatool needop 的 enable 参数
    private static final SuggestionProvider<CommandSourceStack> BOOLEAN_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("true").suggest("false").buildFuture();
    };

    // Tab补全：/tpatool setlanguage 的 lang 参数
    private static final SuggestionProvider<CommandSourceStack> LANGUAGE_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("en_us").suggest("zh_cn").buildFuture();
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        int tpaPermLevel = commandPermissions.getOrDefault("tpa", false) ? 2 : 0;
        int homePermLevel = commandPermissions.getOrDefault("home", false) ? 2 : 0;
        int gravePermLevel = commandPermissions.getOrDefault("grave", false) ? 2 : 0;
        int backPermLevel = commandPermissions.getOrDefault("back", false) ? 2 : 0;

        event.getDispatcher().register(
                Commands.literal("tpa")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = context.getSource().getPlayerOrException();
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return sendTPARequest(sender, target, false);
                                    } catch (CommandSyntaxException e) {
                                        LOGGER.error("Failed to parse /tpa: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    } catch (Exception e) {
                                        LOGGER.error("Unexpected error in /tpa: ", e);
                                        context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpahere")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = context.getSource().getPlayerOrException();
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return sendTPARequest(sender, target, true);
                                    } catch (CommandSyntaxException e) {
                                        LOGGER.error("Failed to parse /tpahere: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    } catch (Exception e) {
                                        LOGGER.error("Unexpected error in /tpahere: ", e);
                                        context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpaccept")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .executes(context -> acceptTPARequest(context.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = EntityArgument.getPlayer(context, "player");
                                        return acceptTPARequest(context.getSource().getPlayerOrException(), sender);
                                    } catch (CommandSyntaxException e) {
                                        LOGGER.error("Failed to parse /tpaccept: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpadeny")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .executes(context -> denyTPARequest(context.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = EntityArgument.getPlayer(context, "player");
                                        return denyTPARequest(context.getSource().getPlayerOrException(), sender);
                                    } catch (CommandSyntaxException e) {
                                        LOGGER.error("Failed to parse /tpadeny: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpacancel")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .executes(context -> cancelTPARequest(context.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return cancelTPARequest(context.getSource().getPlayerOrException(), target);
                                    } catch (CommandSyntaxException e) {
                                        LOGGER.error("Failed to parse /tpacancel: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpatoggle")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .executes(context -> toggleTPA(context.getSource().getPlayerOrException()))
        );

        event.getDispatcher().register(
                Commands.literal("tpalock")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return lockTPA(context.getSource().getPlayerOrException(), target);
                                    } catch (CommandSyntaxException e) {
                                        LOGGER.error("Failed to parse /tpalock: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpaunlock")
                        .requires(source -> source.hasPermission(tpaPermLevel))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return unlockTPA(context.getSource().getPlayerOrException(), target);
                                    } catch (CommandSyntaxException e) {
                                        LOGGER.error("Failed to parse /tpaunlock: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpatool")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("setlanguage")
                                .then(Commands.argument("lang", StringArgumentType.string())
                                        .suggests(LANGUAGE_SUGGESTIONS)
                                        .executes(context -> {
                                            String lang = StringArgumentType.getString(context, "lang");
                                            if (lang.equals("en_us") || lang.equals("zh_cn")) {
                                                DEFAULT_LANGUAGE.set(lang);
                                                DEFAULT_LANGUAGE.save();
                                                loadTranslations(lang);
                                                context.getSource().sendSuccess(
                                                        () -> translateWithFallback("command.tpatool.setlanguage.success", "Language set to %s.", lang),
                                                        true
                                                );
                                                LOGGER.info("Language switched to {} by {}", lang, context.getSource().getDisplayName().getString());
                                                return 1;
                                            }
                                            context.getSource().sendFailure(
                                                    translateWithFallback("command.tpatool.setlanguage.invalid", "Invalid language. Use 'en_us' or 'zh_cn'.")
                                            );
                                            return 0;
                                        })))
                        .then(Commands.literal("setmaxhome")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int count = IntegerArgumentType.getInteger(context, "count");
                                            MAX_HOMES.set(count);
                                            MAX_HOMES.save();
                                            context.getSource().sendSuccess(
                                                    () -> translateWithFallback("command.tpatool.setmaxhome.success", "Maximum homes set to %s.", count),
                                                    true
                                            );
                                            LOGGER.info("Max homes set to {} by {}", count, context.getSource().getDisplayName().getString());
                                            return 1;
                                        })))
                        .then(Commands.literal("needop")
                                .then(Commands.argument("command", StringArgumentType.string())
                                        .suggests(COMMAND_SUGGESTIONS)
                                        .then(Commands.argument("enable", StringArgumentType.string())
                                                .suggests(BOOLEAN_SUGGESTIONS)
                                                .executes(context -> {
                                                    String command = StringArgumentType.getString(context, "command");
                                                    String enableStr = StringArgumentType.getString(context, "enable");
                                                    boolean enable = enableStr.equalsIgnoreCase("true");
                                                    if (!Arrays.asList("tpa", "home", "grave", "back").contains(command)) {
                                                        context.getSource().sendFailure(
                                                                translateWithFallback("command.tpatool.needop.invalid_command", "Invalid command. Use 'tpa', 'home', 'grave', or 'back'.")
                                                        );
                                                        return 0;
                                                    }
                                                    commandPermissions.put(command, enable);
                                                    saveCommandPermissions();
                                                    context.getSource().sendSuccess(
                                                            () -> translateWithFallback(
                                                                    enable ? "command.tpatool.needop.success_enabled" : "command.tpatool.needop.success_disabled",
                                                                    enable ? "%s commands now require OP permission." : "%s commands now do not require OP permission.",
                                                                    command
                                                            ),
                                                            true
                                                    );
                                                    LOGGER.info("{} commands set to {} OP by {}", command, enable ? "require" : "not require", context.getSource().getDisplayName().getString());
                                                    return 1;
                                                }))))
        );
    }

    private static boolean canSendRequest(ServerPlayer sender) {
        Long lastRequest = cooldowns.get(sender.getUUID());
        return lastRequest == null || System.currentTimeMillis() - lastRequest >= COOLDOWN_TIME.get();
    }

    private static int sendTPARequest(ServerPlayer sender, ServerPlayer target, boolean isTPHere) {
        if (sender == target) {
            sender.sendSystemMessage(translateWithFallback("command.tpatool.tpa.self", "You cannot teleport to yourself!"));
            return 0;
        }
        if (!canSendRequest(sender)) {
            sender.sendSystemMessage(translateWithFallback("command.tpatool.tpa.cooldown", "Please wait for the cooldown (60 seconds)!"));
            return 0;
        }
        if (!target.isAlive()) {
            sender.sendSystemMessage(translateWithFallback("command.tpatool.tpa.target_dead", "The target player is dead!"));
            return 0;
        }
        if (toggleStates.getOrDefault(target.getUUID(), false)) {
            sender.sendSystemMessage(translateWithFallback("command.tpatool.tpa.toggled_off", "%s has disabled TPA requests.", target.getName()));
            return 0;
        }
        Set<UUID> lockedByTarget = lockedPlayers.getOrDefault(target.getUUID(), new HashSet<>());
        if (lockedByTarget.contains(sender.getUUID())) {
            sender.sendSystemMessage(translateWithFallback("command.tpatool.tpa.locked", "%s has locked TPA requests from you.", target.getName()));
            return 0;
        }

        TPARequest request = new TPARequest(sender, target, isTPHere);
        requests.computeIfAbsent(target.getUUID(), k -> new ArrayList<>()).add(request);

        String senderName = sender.getName().getString();
        MutableComponent acceptText = Component.literal("[" + translations.getOrDefault("command.tpatool.tpa.accept", "Accept") + " " + senderName + "]")
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + senderName))
                        .withColor(ChatFormatting.GREEN));
        MutableComponent denyText = Component.literal("[" + translations.getOrDefault("command.tpatool.tpa.deny", "Deny") + " " + senderName + "]")
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpadeny " + senderName))
                        .withColor(ChatFormatting.RED));
        MutableComponent message = translateWithFallback(
                isTPHere ? "command.tpatool.tpahere.received" : "command.tpatool.tpa.received",
                isTPHere ? "%s wants you to teleport to them! Click to accept or deny." : "%s wants to teleport to you! Click to accept or deny.",
                sender.getName()
        ).append(Component.literal("\n")).append(acceptText).append(Component.literal(" ")).append(denyText);

        target.sendSystemMessage(message);
        sender.sendSystemMessage(translateWithFallback("command.tpatool.tpa.sent", "Teleport request sent to %s.", target.getName()));
        cooldowns.put(sender.getUUID(), System.currentTimeMillis());
        LOGGER.info("{} request sent: {} -> {}", isTPHere ? "TPAHere" : "TPA", senderName, target.getName().getString());
        return 1;
    }

    private static int acceptTPARequest(ServerPlayer target, ServerPlayer sender) {
        List<TPARequest> targetRequests = requests.get(target.getUUID());
        if (targetRequests == null || targetRequests.isEmpty()) {
            target.sendSystemMessage(translateWithFallback("command.tpatool.tpa.no_request", "You have no pending teleport requests."));
            return 0;
        }
        TPARequest request;
        if (sender != null) {
            request = targetRequests.stream()
                    .filter(req -> req.sender.getUUID().equals(sender.getUUID()))
                    .findFirst()
                    .orElse(null);
            if (request == null) {
                target.sendSystemMessage(translateWithFallback("command.tpatool.tpa.no_request_from", "No teleport request from %s.", sender.getName()));
                return 0;
            }
        } else {
            request = targetRequests.get(targetRequests.size() - 1);
        }
        if (!request.sender.isAlive()) {
            target.sendSystemMessage(translateWithFallback("command.tpatool.tpa.sender_dead", "The requesting player is dead!"));
            targetRequests.remove(request);
            if (targetRequests.isEmpty()) {
                requests.remove(target.getUUID());
            }
            return 0;
        }
        BackHandler.recordPosition(request.isTPHere ? target : request.sender);
        if (request.isTPHere) {
            target.teleportTo(
                    request.sender.serverLevel(),
                    request.sender.getX(), request.sender.getY(), request.sender.getZ(),
                    request.sender.getYRot(), request.sender.getXRot()
            );
        } else {
            request.sender.teleportTo(
                    target.serverLevel(),
                    target.getX(), target.getY(), target.getZ(),
                    target.getYRot(), target.getXRot()
            );
        }
        target.sendSystemMessage(translateWithFallback(
                request.isTPHere ? "command.tpatool.tpahere.accepted" : "command.tpatool.tpa.accepted",
                request.isTPHere ? "Accepted teleport request to %s." : "Accepted teleport request from %s.",
                request.sender.getName()
        ));
        request.sender.sendSystemMessage(translateWithFallback(
                request.isTPHere ? "command.tpatool.tpa.accepted_by" : "command.tpatool.tpa.accepted_by",
                "Your teleport request was accepted by %s!", target.getName()
        ));
        targetRequests.remove(request);
        if (targetRequests.isEmpty()) {
            requests.remove(target.getUUID());
        }
        LOGGER.info("TPA request accepted: {} teleported to {}", request.isTPHere ? target.getName().getString() : request.sender.getName().getString(), request.isTPHere ? request.sender.getName().getString() : target.getName().getString());
        return 1;
    }

    private static int denyTPARequest(ServerPlayer target, ServerPlayer sender) {
        List<TPARequest> targetRequests = requests.get(target.getUUID());
        if (targetRequests == null || targetRequests.isEmpty()) {
            target.sendSystemMessage(translateWithFallback("command.tpatool.tpa.no_request", "You have no pending teleport requests."));
            return 0;
        }
        TPARequest request;
        if (sender != null) {
            request = targetRequests.stream()
                    .filter(req -> req.sender.getUUID().equals(sender.getUUID()))
                    .findFirst()
                    .orElse(null);
            if (request == null) {
                target.sendSystemMessage(translateWithFallback("command.tpatool.tpa.no_request_from", "No teleport request from %s.", sender.getName()));
                return 0;
            }
        } else {
            request = targetRequests.get(targetRequests.size() - 1);
        }
        target.sendSystemMessage(translateWithFallback(
                request.isTPHere ? "command.tpatool.tpahere.denied" : "command.tpatool.tpa.denied",
                request.isTPHere ? "Denied teleport request to %s." : "Denied teleport request from %s.",
                request.sender.getName()
        ));
        request.sender.sendSystemMessage(translateWithFallback(
                request.isTPHere ? "command.tpatool.tpa.denied_by" : "command.tpatool.tpa.denied_by",
                "%s denied your teleport request.", target.getName()
        ));
        targetRequests.remove(request);
        if (targetRequests.isEmpty()) {
            requests.remove(target.getUUID());
        }
        LOGGER.info("TPA request denied: {} -> {}", request.sender.getName().getString(), target.getName().getString());
        return 1;
    }

    private static int cancelTPARequest(ServerPlayer sender, ServerPlayer target) {
        boolean cancelled = false;
        for (List<TPARequest> targetRequests : requests.values()) {
            Iterator<TPARequest> iterator = targetRequests.iterator();
            while (iterator.hasNext()) {
                TPARequest request = iterator.next();
                if (request.sender.getUUID().equals(sender.getUUID()) && (target == null || request.target.getUUID().equals(target.getUUID()))) {
                    iterator.remove();
                    request.target.sendSystemMessage(translateWithFallback(
                            "command.tpatool.tpa.cancelled", "%s cancelled their teleport request.", sender.getName()
                    ));
                    sender.sendSystemMessage(translateWithFallback(
                            "command.tpatool.tpa.cancelled_self", "Cancelled teleport request to %s.", request.target.getName()
                    ));
                    LOGGER.info("TPA request cancelled: {} -> {}", sender.getName().getString(), request.target.getName().getString());
                    cancelled = true;
                }
            }
        }
        requests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!cancelled) {
            sender.sendSystemMessage(translateWithFallback(
                    "command.tpatool.tpa.no_requests_to_cancel", "No teleport requests to cancel."
            ));
            return 0;
        }
        return 1;
    }

    private static int toggleTPA(ServerPlayer player) {
        boolean currentState = toggleStates.getOrDefault(player.getUUID(), false);
        boolean newState = !currentState;
        toggleStates.put(player.getUUID(), newState);
        saveToggleStates();
        player.sendSystemMessage(translateWithFallback(
                "command.tpatool.tpa.toggle_" + (newState ? "on" : "off"),
                newState ? "TPA requests are now disabled." : "TPA requests are now enabled."
        ));
        LOGGER.info("Player {} toggled TPA to {}", player.getName().getString(), newState ? "off" : "on");
        return 1;
    }

    private static int lockTPA(ServerPlayer player, ServerPlayer target) {
        if (player == target) {
            player.sendSystemMessage(translateWithFallback("command.tpatool.tpa.lock_self", "You cannot lock yourself!"));
            return 0;
        }
        Set<UUID> locked = lockedPlayers.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        if (locked.contains(target.getUUID())) {
            player.sendSystemMessage(translateWithFallback("command.tpatool.tpa.already_locked", "%s is already locked.", target.getName()));
            return 0;
        }
        locked.add(target.getUUID());
        saveLockedPlayers();
        player.sendSystemMessage(translateWithFallback("command.tpatool.tpa.locked_player", "Locked TPA requests from %s.", target.getName()));
        LOGGER.info("Player {} locked TPA from {}", player.getName().getString(), target.getName().getString());
        return 1;
    }

    private static int unlockTPA(ServerPlayer player, ServerPlayer target) {
        if (player == target) {
            player.sendSystemMessage(translateWithFallback("command.tpatool.tpa.lock_self", "You cannot unlock yourself!"));
            return 0;
        }
        Set<UUID> locked = lockedPlayers.get(player.getUUID());
        if (locked == null || !locked.contains(target.getUUID())) {
            player.sendSystemMessage(translateWithFallback("command.tpatool.tpa.not_locked", "%s is not locked.", target.getName()));
            return 0;
        }
        locked.remove(target.getUUID());
        if (locked.isEmpty()) {
            lockedPlayers.remove(player.getUUID());
        }
        saveLockedPlayers();
        player.sendSystemMessage(translateWithFallback("command.tpatool.tpa.unlocked_player", "Unlocked TPA requests from %s.", target.getName()));
        LOGGER.info("Player {} unlocked TPA from {}", player.getName().getString(), target.getName().getString());
        return 1;
    }

    public static void tick() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, List<TPARequest>> entry : requests.entrySet()) {
            Iterator<TPARequest> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                TPARequest request = iterator.next();
                if (System.currentTimeMillis() - request.timestamp >= TIMEOUT_TICKS.get() * 50) {
                    request.target.sendSystemMessage(translateWithFallback(
                            request.isTPHere ? "command.tpatool.tpahere.timeout" : "command.tpatool.tpa.timeout",
                            "Teleport request from %s has timed out.", request.sender.getName()
                    ));
                    request.sender.sendSystemMessage(translateWithFallback(
                            request.isTPHere ? "command.tpatool.tpa.timeout_self" : "command.tpatool.tpa.timeout_self",
                            "Your teleport request to %s has timed out.", request.target.getName()
                    ));
                    iterator.remove();
                    LOGGER.info("TPA request timed out: {} -> {}", request.sender.getName().getString(), request.target.getName().getString());
                }
            }
            if (entry.getValue().isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(requests::remove);
    }
}