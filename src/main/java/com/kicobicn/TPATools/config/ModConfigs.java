package com.kicobicn.TPATools.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kicobicn.TPATools.TPATools;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.kicobicn.TPATools.Commands.TPAHandler;

public class ModConfigs {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    // 配置项定义
    public static final Map<String, String> translations = new HashMap<>();

    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.LongValue COOLDOWN_TIME;
    public static final ForgeConfigSpec.IntValue TIMEOUT_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;
    public static final ForgeConfigSpec.IntValue MAX_HOMES;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue WAIT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    //配置路径检查
    public static Path getConfigDir() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("tpatools");
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                DebugLog.info("Created config directory: {}", configDir.toString());
            }
        } catch (IOException e) {
            DebugLog.error("Failed to create config directory: {}", e.getMessage());
        }
        return configDir;
    }

    public static boolean checkCommandPermission(CommandSourceStack source, String command) {
        boolean needOp = commandPermissions.getOrDefault(command, false);
        if (needOp) {
            return source.hasPermission(2); // 需要OP权限
        } else {
            return true; // 不需要OP权限，所有玩家都可以使用
        }
    }

    // 命令权限状态
    public static final Map<String, Boolean> commandPermissions = new HashMap<>();

    // Tab补全提供器
    private static final SuggestionProvider<CommandSourceStack> COMMAND_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("tpa").suggest("home").suggest("grave").suggest("back").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> BOOLEAN_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("true").suggest("false").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> LANGUAGE_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("en_us").suggest("zh_cn").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TIME_SUGGESTIONS = (context, builder) -> {
        builder.suggest("10");
        builder.suggest("30");
        builder.suggest("60");
        builder.suggest("120");
        builder.suggest("300");
        return builder.buildFuture();
    };

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
        COOLDOWN_SECONDS = builder.comment("TPA cooldown time in seconds")
                .defineInRange("cooldown_seconds", 60, 0, Integer.MAX_VALUE);
        WAIT_SECONDS = builder.comment("TPA wait time in seconds")
                .defineInRange("wait_seconds", 30, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("debug");
        DEBUG_MODE = builder.comment("Enable debug logging for detailed output")
                .define("debugMode", false); // 默认关闭
        builder.pop();

        CONFIG = builder.build();
    }

    //调试模式
    public static boolean isDebugEnabled() {
        return DEBUG_MODE.get();
    }

    public static class DebugLog {
        public static void info(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.info("[DEBUG INFO] " + message, args);
            }
        }

        public static void warn(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.warn("[DEBUG WARN] " + message, args);
            }
        }

        public static void error(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.error("[DEBUG ERROR] " + message, args);
            }
        }

        public static void log(String message, Object... args) {
            info(message, args);
        }

    }

    //初始化命令权限
    public static void initCommandPermissions() {
        commandPermissions.putIfAbsent("tpa", false);
        commandPermissions.putIfAbsent("home", false);
        commandPermissions.putIfAbsent("grave", false);
        commandPermissions.putIfAbsent("back", false);
        commandPermissions.putIfAbsent("debug", false);
    }

    //lang类
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en_us", "zh_cn");

    public static void loadTranslations(String lang) {
        translations.clear();
        String fileName = String.format("%s.json", lang);
        String modid = TPATools.MODID; // 用你在 TPATools.java 里定义的 MODID 常量

        LOGGER.info("Attempting to load language file for '{}'", lang);

        try {
            Path candidate = ModList.get()
                    .getModFileById(modid)
                    .getFile()
                    .findResource("assets/" + modid + "/lang/" + fileName);
            if (candidate != null && Files.exists(candidate)) {
                try (BufferedReader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (String key : json.keySet()) translations.put(key, json.get(key).getAsString());
                    LOGGER.info("Loaded language from mod file path: assets/{}/lang/{}", modid, fileName);
                    return;
                }
            } else {
                LOGGER.debug("ModFile.findResource did not return existing path for assets/{}/lang/{}", modid, fileName);
            }
        } catch (Exception e) {
            LOGGER.warn("ModFile lookup failed for assets/{}/lang/{}: {}", modid, fileName, e.getMessage());
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

    //注册tpatool管理命令
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("tpatools")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("setlanguage")
                                .then(Commands.argument("lang", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            for (String lang : SUPPORTED_LANGUAGES) builder.suggest(lang);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String lang = StringArgumentType.getString(context, "lang");
                                            if (!SUPPORTED_LANGUAGES.contains(lang)) {
                                                context.getSource().sendFailure(
                                                        translateWithFallback(
                                                                "command.tpatool.setlanguage.invalid",
                                                                "Invalid language. Available: en_us, zh_cn"
                                                        )
                                                );
                                                return 0;
                                            }

                                            DEFAULT_LANGUAGE.set(lang);
                                            DEFAULT_LANGUAGE.save();
                                            loadTranslations(lang);

                                            context.getSource().sendSuccess(
                                                    () -> translateWithFallback(
                                                            "command.tpatool.setlanguage.success",
                                                            "Language set to %s.",
                                                            lang
                                                    ),
                                                    true
                                            );

                                            LOGGER.info("Language switched to {}", lang);
                                            return 1;
                                        })
                                ))
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
                                            DebugLog.info("Max homes set to {} by {}", count, context.getSource().getDisplayName().getString());
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
                                                    TPAHandler.saveCommandPermissions();
                                                    context.getSource().sendSuccess(
                                                            () -> translateWithFallback(
                                                                    enable ? "command.tpatool.needop.success_enabled" : "command.tpatool.needop.success_disabled",
                                                                    enable ? "%s commands now require OP permission." : "%s commands now do not require OP permission.",
                                                                    command
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("{} commands set to {} OP by {}", command, enable ? "require" : "not require", context.getSource().getDisplayName().getString());
                                                    return 1;
                                                }))))
                        .then(Commands.literal("tpacdtime")
                                .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                        .suggests(TIME_SUGGESTIONS)
                                        .executes(context -> {
                                            int time = IntegerArgumentType.getInteger(context, "time");
                                            COOLDOWN_TIME.set((long) time * 1000); // 转换为毫秒
                                            COOLDOWN_TIME.save();
                                            context.getSource().sendSuccess(
                                                    () -> translateWithFallback(
                                                            "command.tpatool.tpacdtime.success",
                                                            "TPA cooldown time set to %d seconds.",
                                                            time
                                                    ),
                                                    true
                                            );
                                            DebugLog.info("TPA cooldown time set to {} seconds by {}",
                                                    time, context.getSource().getDisplayName().getString());
                                            return 1;
                                        })))
                        .then(Commands.literal("tpawaittime")
                                .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                        .suggests(TIME_SUGGESTIONS)
                                        .executes(context -> {
                                            int time = IntegerArgumentType.getInteger(context, "time");
                                            TIMEOUT_TICKS.set(time * 20); // 转换为ticks (1秒=20ticks)
                                            TIMEOUT_TICKS.save();
                                            context.getSource().sendSuccess(
                                                    () -> translateWithFallback(
                                                            "command.tpatool.tpawaittime.success",
                                                            "TPA wait time set to %d seconds.",
                                                            time
                                                    ),
                                                    true
                                            );
                                            DebugLog.info("TPA wait time set to {} seconds by {}",
                                                    time, context.getSource().getDisplayName().getString());
                                            return 1;
                                        }))
                        )
                        .then(Commands.literal("debug")
                                .then(Commands.argument("enable", StringArgumentType.string())
                                        .suggests(BOOLEAN_SUGGESTIONS)
                                        .executes(context -> {
                                            String enableStr = StringArgumentType.getString(context, "enable");
                                            boolean enable = enableStr.equalsIgnoreCase("true");
                                            DEBUG_MODE.set(enable);
                                            DEBUG_MODE.save();
                                            context.getSource().sendSuccess(
                                                    () -> translateWithFallback(
                                                            enable ? "command.tpatool.debug.enabled" : "command.tpatool.debug.disabled",
                                                            enable ? "Debug mode enabled." : "Debug mode disabled."
                                                    ),
                                                    true
                                            );
                                            DebugLog.info("Debug mode {} by {}", enable ? "enabled" : "disabled", context.getSource().getDisplayName().getString());
                                            return 1;
                                        }))
                                .executes(context -> {
                                    boolean currentState = DEBUG_MODE.get();
                                    context.getSource().sendSuccess(
                                            () -> translateWithFallback(
                                                    "command.tpatool.debug.status",
                                                    "Debug mode is currently %s.",
                                                    currentState ? "enabled" : "disabled"
                                            ),
                                            true
                                    );
                                    return 1;
                                }))
        );
    }
}
