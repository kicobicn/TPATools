package com.kicobicn.TPATools.chat;

import com.kicobicn.TPATools.Commands.HomeHandler;
import com.kicobicn.TPATools.Commands.WarpHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.util.ModUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModChatMenus {

    // 存储玩家的UI状态
    private static final Map<UUID, UIState> playerUIStates = new ConcurrentHashMap<>();

    // UI状态类
    private static class UIState {
        String currentMenu;
        List<String> menuData;

        public UIState(String currentMenu, List<String> menuData) {
            this.currentMenu = currentMenu;
            this.menuData = menuData;
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 初始化玩家UI状态
            playerUIStates.put(player.getUUID(), new UIState("home", new ArrayList<>()));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 清理玩家UI状态
            playerUIStates.remove(player.getUUID());
        }
    }

    public static class HomeMenus {

        public static void showHomeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - Home ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.home.title.main", "        -Home menu -\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

                    // 按钮
                    menu.append(createI18nButton("menu.tpatools.home.button.sethome",
                                    "menu.tpatools.home.hover.sethome",
                                    "/home set ",
                                    ChatFormatting.GREEN,
                                    false))
                            .append(Component.literal("\n"));
            
                    menu.append(createI18nButton("menu.tpatools.home.button.managehome",
                                    "menu.tpatools.home.hover.managehome",
                                    "/home list",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal(" "))
                            .append(createI18nButton("menu.tpatools.home.button.visitother",
                                    "menu.tpatools.home.hover.visitother",
                                    "/home otherlist",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal("\n"));
            
                    menu.append(createI18nButton("menu.tpatools.home.button.sharedouthomes",
                                    "menu.tpatools.home.hover.sharedouthomes",
                                    "/home sharelist out",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal(" "))
                            .append(createI18nButton("menu.tpatools.home.button.sharedinhomes",
                                    "menu.tpatools.home.hover.sharedinhomes",
                                    "/home sharelist in",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal("\n"));
            // 分割线
            menu.append(Component.literal("==============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showOwnHomesList(ServerPlayer player, int page) {
            Map<String, HomeHandler.Home> homes = HomeHandler.playerHomes.get(player.getUUID());
            if (homes == null || homes.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatool.home.list_empty", "You have no homes set."));
                return;
            }

            List<String> homeNames = new ArrayList<>(homes.keySet());
            int pageSize = 5;
            int totalPages = (int) Math.ceil((double) homeNames.size() / pageSize);
            page = Math.max(0, Math.min(page, totalPages - 1));

            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - Home/list ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.home.title.homelist", "        - Your Current Homes -\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

            // 显示当前页的家
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, homeNames.size());

            for (int i = startIndex; i < endIndex; i++) {
                String homeName = homeNames.get(i);
                HomeHandler.Home home = homes.get(homeName);

                menu.append(Component.literal("- " + homeName + "\n")
                        .withStyle(ChatFormatting.WHITE));

                String position = String.format("%s (x=%.2f, y=%.2f, z=%.2f)",
                        home.position.dimension, home.position.x, home.position.y, home.position.z);
                menu.append(Component.literal("  " + position + "\n")
                        .withStyle(ChatFormatting.GRAY));

                                // 按钮行
                                MutableComponent buttons = Component.literal(" ");
                                buttons.append(createI18nButton("menu.tpatools.home.button.teleport",
                                                "menu.tpatools.home.hover.teleport",
                                                "/home tp " + homeName,
                                                ChatFormatting.GREEN,
                                                true))
                                        .append(Component.literal(" "));
                                buttons.append(createI18nButton("menu.tpatools.home.button.rename",
                                                "menu.tpatools.home.hover.rename",
                                                "/home rename " + homeName + " ",
                                                ChatFormatting.YELLOW,
                                                false))
                                        .append(Component.literal(" "));

                                // 公开/私密按钮
                                Map<String, HomeHandler.PublicHomeInfo> publicHomes = HomeHandler.publicHomesByOwner.get(player.getUUID().toString());
                                boolean isPublic = publicHomes != null && publicHomes.containsKey(homeName);
                                String publicCommand = isPublic ? "/home private " + homeName : "/home public " + homeName;
                                String publicText = isPublic ? "menu.tpatools.home.button.setprivate" : "menu.tpatools.home.button.setpublic";
                                String publicHover = isPublic ? "menu.tpatools.home.hover.setprivate" : "menu.tpatools.home.hover.setpublic";
                                ChatFormatting publicColor = isPublic ? ChatFormatting.RED : ChatFormatting.GREEN;

                                buttons.append(createI18nButton(publicText,
                                                publicHover,
                                                publicCommand,
                                                publicColor,
                                                true))
                                        .append(Component.literal(" "));

                                // 分享按钮
                                int sharedCount = home.sharedPlayers.size();
                                buttons.append(createI18nButtonWithHoverArgs("menu.tpatools.home.button.share",
                                                "menu.tpatools.home.hover.share",
                                                "/home share " + homeName + " ",
                                                ChatFormatting.AQUA,
                                                false,
                                                new Object[]{sharedCount},
                                                new Object[]{sharedCount}))
                                        .append(Component.literal(" "));

                                buttons.append(createI18nButton("menu.tpatools.home.button.invite",
                                                "menu.tpatools.home.hover.invite",
                                                "/home invite invite " + homeName + " ",
                                                ChatFormatting.LIGHT_PURPLE,
                                                false))
                                        .append(Component.literal(" "));

                                buttons.append(createI18nButton("menu.tpatools.home.button.delete",
                                                "menu.tpatools.home.hover.delete",
                                                "/home remove " + homeName,
                                                ChatFormatting.RED,
                                                true))
                                        .append(Component.literal(" "));
                menu.append(buttons).append(Component.literal("\n"));
            }

            // 分页系统
            if (totalPages > 1) {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));

                MutableComponent pagination = Component.literal(" ");
                pagination.append(createI18nButton("menu.tpatools.home.button.previouspage",
                                "menu.tpatools.home.hover.previouspage",
                                "/home list page " + Math.max(0, page - 1),
                                ChatFormatting.GRAY,
                                true))
                        .append(Component.literal(" - " + (page + 1) + "/" + totalPages + " - "))
                        .append(createI18nButton("menu.tpatools.home.button.nextpage",
                                "menu.tpatools.home.hover.nextpage",
                                "/home list page " + Math.min(totalPages - 1, page + 1),
                                ChatFormatting.GRAY,
                                true));

                menu.append(pagination).append(Component.literal("\n"));
            } else {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));
            }

            player.sendSystemMessage(menu);
        }

        // 其他菜单方法...
    }

    public static class ConfigMenus {
        // 配置菜单方法...
    }

    // 创建国际化按钮的辅助方法
    private static MutableComponent createI18nButton(String textKey, String hoverKey, String command, ChatFormatting color, boolean isButton) {
        String text = ModUtils.translations.getOrDefault(textKey, textKey);
        String hoverText = ModUtils.translations.getOrDefault(hoverKey, hoverKey);
        
        MutableComponent button = Component.literal(text)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                        .withColor(color));
        
        if (isButton) {
            button = Component.literal("[").withStyle(ChatFormatting.GRAY)
                    .append(button)
                    .append(Component.literal("]").withStyle(ChatFormatting.GRAY));
        }
        
        return button;
    }

    private static MutableComponent createI18nButtonWithHoverArgs(String textKey, String hoverKey, String command, ChatFormatting color, boolean isButton, Object[] textArgs, Object[] hoverArgs) {
        String text = String.format(ModUtils.translations.getOrDefault(textKey, textKey), textArgs);
        String hoverText = String.format(ModUtils.translations.getOrDefault(hoverKey, hoverKey), hoverArgs);
        
        MutableComponent button = Component.literal(text)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                        .withColor(color));
        
        if (isButton) {
            button = Component.literal("[").withStyle(ChatFormatting.GRAY)
                    .append(button)
                    .append(Component.literal("]").withStyle(ChatFormatting.GRAY));
        }
        
        return button;
    }

    // Warp菜单方法
    public static void showWarpMenu(ServerPlayer player) {
        // Warp菜单实现...
    }

    public static void showWarpList(ServerPlayer player, int page) {
        // Warp列表实现...
    }
}