package com.kicobicn.TPATools.Commands;

import com.kicobicn.TPATools.config.ModConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {

    // 修改正则表达式匹配 &数字 模式 (例如 &1)
    private static final Pattern ITEM_PATTERN = Pattern.compile("&(\\d)");

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            String originalMessage = event.getRawText();

            ModConfigs.DebugLog.info("Processing chat message from {}: {}", player.getName().getString(), originalMessage);

            // 处理提到玩家功能
            Component processedMessage = Component.nullToEmpty(processMentions(originalMessage, player));

            // 处理展示物品功能
            MutableComponent finalMessage = processItemDisplay(processedMessage, player);

            // 设置处理后的消息
            event.setMessage(finalMessage);
            ModConfigs.DebugLog.info("Chat message processed successfully");
        } catch (Exception e) {
            ModConfigs.DebugLog.error("Error processing chat message: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理提到玩家功能 (返回Component以更好地处理颜色代码)
     */
    private String processMentions(String message, ServerPlayer sender) {
        // 获取所有在线玩家
        var onlinePlayers = sender.getServer().getPlayerList().getPlayers();
        String processedMessage = message; // 初始化为原消息

        for (ServerPlayer target : onlinePlayers) {
            String playerName = target.getName().getString();

            // 跳过自己
            if (target.getUUID().equals(sender.getUUID())) {
                continue;
            }

            // 如果消息中包含玩家名（不区分大小写）
            if (message.toLowerCase().contains(playerName.toLowerCase())) {
                ModConfigs.DebugLog.info("Player {} mentioned {}", sender.getName().getString(), playerName);

                // 将玩家名标色
                processedMessage = processedMessage.replaceAll("(?i)" + Pattern.quote(playerName),
                        ChatFormatting.AQUA + playerName + ChatFormatting.RESET);

                // 使用 Title 数据包来实现居中大标题提示的效果
                ClientboundSetTitleTextPacket titlePacket =
                        new ClientboundSetTitleTextPacket(
                                ModConfigs.translateWithFallback(
                                        "command.tpatool.chat.mention",
                                        "%s mentioned you",
                                        sender.getName()
                                ).withStyle(ChatFormatting.AQUA)
                        );
// 设置标题显示时间、淡入淡出时间
                ClientboundSetTitlesAnimationPacket timesPacket =
                        new ClientboundSetTitlesAnimationPacket(10, 40, 10);
                target.connection.send(timesPacket);
                target.connection.send(titlePacket);

                // 播放音效
                target.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        // 特别处理：如果消息中包含自己的名字，也将其设置为青色
        String senderName = sender.getName().getString();
        if (processedMessage.contains(senderName)) {
            processedMessage = processedMessage.replaceAll("(?i)" + Pattern.quote(senderName),
                    ChatFormatting.AQUA + senderName + ChatFormatting.RESET);
        }

        return processedMessage;
    }

    /**
     * 处理展示物品功能
     */
    private MutableComponent processItemDisplay(Component originalComponent, ServerPlayer player) {
        String message = originalComponent.getString();
        List<Object> messageParts = new ArrayList<>();
        Matcher matcher = ITEM_PATTERN.matcher(message);
        int lastIndex = 0;

        while (matcher.find()) {
            // 添加前面的普通文本
            if (matcher.start() > lastIndex) {
                messageParts.add(message.substring(lastIndex, matcher.start()));
            }

            // 处理物品占位符
            int slot = Integer.parseInt(matcher.group(1)) - 1; // 转换为0-8的索引
            if (slot >= 0 && slot <= 8) {
                ItemStack itemStack = player.getInventory().getItem(slot);
                if (!itemStack.isEmpty()) {
                    // 添加物品组件
                    messageParts.add(createItemComponent(itemStack));
                    ModConfigs.DebugLog.info("Replaced &{} with item: {}", slot + 1, itemStack.getDisplayName().getString());
                } else {
                    // 槽位为空，保留原文本
                    messageParts.add(matcher.group(0));
                    ModConfigs.DebugLog.info("Slot {} is empty, keeping original text", slot + 1);
                }
            } else {
                // 无效槽位，保留原文本
                messageParts.add(matcher.group(0));
                ModConfigs.DebugLog.info("Invalid slot {}, keeping original text", slot + 1);
            }

            lastIndex = matcher.end();
        }

        // 添加剩余的文本
        if (lastIndex < message.length()) {
            messageParts.add(message.substring(lastIndex));
        }

        // 构建最终的消息组件
        MutableComponent finalMessage = Component.literal("");
        for (Object part : messageParts) {
            if (part instanceof String) {
                finalMessage.append(Component.literal((String) part));
            } else if (part instanceof MutableComponent) {
                finalMessage.append((MutableComponent) part);
            }
        }

        return finalMessage;
    }

    //展示物品组件
    private MutableComponent createItemComponent(ItemStack itemStack) {
        // 获取物品原有的显示名称和颜色（保持其原始样式）
        MutableComponent itemDisplayName = itemStack.getDisplayName().copy();

        // 构建组件：外括号为金色，物品名称为其原始颜色
        return Component.literal("[") // 左外括号，金色
                .withStyle(ChatFormatting.GOLD)
                .append(itemDisplayName) // 物品名称，保持原色
                .append(Component.literal("]").withStyle(ChatFormatting.GOLD));
    }
}