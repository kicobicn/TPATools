package com.kicobicn.TPATools.Commands;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.kicobicn.TPATools.config.ModConfigs;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.kicobicn.TPATools.config.ModConfigs.*;

public class TPAHandler {
    private static final Map<UUID, List<TPARequest>> requests = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, Boolean> toggleStates = new HashMap<>();
    private static final Map<UUID, Set<UUID>> lockedPlayers = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

    // 加载命令权限状态
    private static void loadCommandPermissions() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool.json");
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<String, Boolean> loadedPermissions = GSON.fromJson(jsonContent, new TypeToken<Map<String, Boolean>>(){}.getType());
                ModConfigs.commandPermissions.clear();
                if (loadedPermissions != null) {
                    ModConfigs.commandPermissions.putAll(loadedPermissions);
                    ModConfigs.DebugLog.info("Loaded command permissions from {}", path.toString());
                }
            }
            ModConfigs.initCommandPermissions();
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to load command permissions: {}", e.getMessage());
        }
    }

    // 保存命令权限状态
    public static void saveCommandPermissions() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool.json");
            Files.writeString(path, GSON.toJson(ModConfigs.commandPermissions));
            ModConfigs.DebugLog.info("Saved command permissions to {}", path.toString());
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to save command permissions: {}", e.getMessage());
        }
    }

    // 加载免打扰状态
    private static void loadToggleStates() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool_toggles.json");
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<UUID, Boolean> loadedToggles = GSON.fromJson(jsonContent, new TypeToken<Map<UUID, Boolean>>(){}.getType());
                toggleStates.clear();
                if (loadedToggles != null) {
                    toggleStates.putAll(loadedToggles);
                    ModConfigs.DebugLog.info("Loaded toggle states from {}", path.toString());
                }
            }
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to load toggle states: {}", e.getMessage());
        }
    }

    // 保存免打扰状态
    private static void saveToggleStates() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool_toggles.json");
            Files.writeString(path, GSON.toJson(toggleStates));
            ModConfigs.DebugLog.info("Saved toggle states to {}", path.toString());
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to save toggle states: {}", e.getMessage());
        }
    }

    // 加载锁定玩家列表
    private static void loadLockedPlayers() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool_locks.json");
            if (Files.exists(path)) {
                String jsonContent = Files.readString(path);
                Map<UUID, Set<UUID>> loadedLocks = GSON.fromJson(jsonContent, new TypeToken<Map<UUID, Set<UUID>>>(){}.getType());
                lockedPlayers.clear();
                if (loadedLocks != null) {
                    lockedPlayers.putAll(loadedLocks);
                    ModConfigs.DebugLog.info("Loaded locked players from {}", path.toString());
                }
            }
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to load locked players: {}", e.getMessage());
        }
    }

    // 保存锁定玩家列表
    private static void saveLockedPlayers() {
        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool_locks.json");
            Files.writeString(path, GSON.toJson(lockedPlayers));
            ModConfigs.DebugLog.info("Saved locked players to {}", path.toString());
        } catch (IOException e) {
            ModConfigs.DebugLog.error("Failed to save locked players: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("tpa")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = context.getSource().getPlayerOrException();
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return sendTPARequest(sender, target, false);
                                    } catch (CommandSyntaxException e) {
                                        ModConfigs.DebugLog.error("Failed to parse /tpa: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    } catch (Exception e) {
                                        ModConfigs.DebugLog.error("Unexpected error in /tpa: ", e);
                                        context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpahere")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = context.getSource().getPlayerOrException();
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return sendTPARequest(sender, target, true);
                                    } catch (CommandSyntaxException e) {
                                        ModConfigs.DebugLog.error("Failed to parse /tpahere: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    } catch (Exception e) {
                                        ModConfigs.DebugLog.error("Unexpected error in /tpahere: ", e);
                                        context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpaccept")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .executes(context -> acceptTPARequest(context.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = EntityArgument.getPlayer(context, "player");
                                        return acceptTPARequest(context.getSource().getPlayerOrException(), sender);
                                    } catch (CommandSyntaxException e) {
                                        ModConfigs.DebugLog.error("Failed to parse /tpaccept: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpadeny")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .executes(context -> denyTPARequest(context.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer sender = EntityArgument.getPlayer(context, "player");
                                        return denyTPARequest(context.getSource().getPlayerOrException(), sender);
                                    } catch (CommandSyntaxException e) {
                                        ModConfigs.DebugLog.error("Failed to parse /tpadeny: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpacancel")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .executes(context -> cancelTPARequest(context.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return cancelTPARequest(context.getSource().getPlayerOrException(), target);
                                    } catch (CommandSyntaxException e) {
                                        ModConfigs.DebugLog.error("Failed to parse /tpacancel: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpatoggle")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .executes(context -> toggleTPA(context.getSource().getPlayerOrException()))
        );

        event.getDispatcher().register(
                Commands.literal("tpalock")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return lockTPA(context.getSource().getPlayerOrException(), target);
                                    } catch (CommandSyntaxException e) {
                                        ModConfigs.DebugLog.error("Failed to parse /tpalock: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("tpaunlock")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "tpa"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        return unlockTPA(context.getSource().getPlayerOrException(), target);
                                    } catch (CommandSyntaxException e) {
                                        ModConfigs.DebugLog.error("Failed to parse /tpaunlock: {}", e.getMessage());
                                        context.getSource().sendFailure(Component.literal("Invalid player name."));
                                        return 0;
                                    }
                                }))
        );
    }

    private static int sendTPARequest(ServerPlayer sender, ServerPlayer target, boolean isTPHere) {
        if (sender == target) {
            sender.sendSystemMessage(translateWithFallback("command.tpatool.tpa.self", "You cannot teleport to yourself!"));
            return 0;
        }

        Long lastRequest = cooldowns.get(sender.getUUID());
        long currentTime = System.currentTimeMillis();
        long cooldownMs = COOLDOWN_TIME.get();

        if (lastRequest != null && currentTime - lastRequest < cooldownMs) {
            long remainingSeconds = (cooldownMs - (currentTime - lastRequest)) / 1000;
            sender.sendSystemMessage(translateWithFallback(
                    "command.tpatool.tpa.cooldown",
                    "Please wait for the cooldown (%d seconds)!",
                    remainingSeconds
            ));
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
        ModConfigs.DebugLog.log("{} request sent: {} -> {}", isTPHere ? "TPAHere" : "TPA", senderName, target.getName().getString());
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
                "command.tpatool.tpa.accepted_by",
                "Your teleport request was accepted by %s!", target.getName()
        ));
        targetRequests.remove(request);
        if (targetRequests.isEmpty()) {
            requests.remove(target.getUUID());
        }
        ModConfigs.DebugLog.log("TPA request accepted: {} teleported to {}",
                request.isTPHere ? target.getName().getString() : request.sender.getName().getString(),
                request.isTPHere ? request.sender.getName().getString() : target.getName().getString());
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
                "command.tpatool.tpa.denied_by",
                "%s denied your teleport request.", target.getName()
        ));
        targetRequests.remove(request);
        if (targetRequests.isEmpty()) {
            requests.remove(target.getUUID());
        }
        ModConfigs.DebugLog.info("TPA request denied: {} -> {}", request.sender.getName().getString(), target.getName().getString());
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
                    ModConfigs.DebugLog.info("TPA request cancelled: {} -> {}", sender.getName().getString(), request.target.getName().getString());
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
        ModConfigs.DebugLog.info("Player {} toggled TPA to {}", player.getName().getString(), newState ? "off" : "on");
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
        ModConfigs.DebugLog.info("Player {} locked TPA from {}", player.getName().getString(), target.getName().getString());
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
        ModConfigs.DebugLog.info("Player {} unlocked TPA from {}", player.getName().getString(), target.getName().getString());
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