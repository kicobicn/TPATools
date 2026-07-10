# TPAtools

### Overview

**TPAtools** is a comprehensive mod that brings essential server utilities to your Minecraft world. It includes robust TPA (Teleport Ask), Home, Warp, and Back commands, along with various quality-of-life features.

> **️ GUI Menus Available!** (Since v1.1.0)  
> Don't want to memorize all these commands? We've got you covered!  
> You can now open GUI menus for `/tpatools` management and `/home` simply by running the commands **without any parameters**.

---

## ️ Features & Commands

<details>
<summary><b> English Commands</b></summary>

####  TPA Commands
- `/tpa [player]` - Send a teleport request to the specified player.
- `/tpaccept [player]` - Accept a request. *(If no player is specified, accepts the latest request)*.
- `/tpadeny [player]` - Deny a request. *(If no player is specified, denies the latest request)*.
- `/tpahere [player]` - Request the specified player to teleport to you.
- `/tpacancel [player]` - Cancel an outgoing request to the specified player.
- `/tpatoggle` - Toggle incoming teleport requests (Do Not Disturb mode).
- `/tpalock [player]` - Block teleport requests from a specific player.
- `/tpaunlock [player]` - Unblock teleport requests from a specific player.

####  Home Commands
- `/home set [name]` - Set your current location as a home.
- `/home tp [name]` - Teleport to a specified home.
- `/home list` - List all your homes.
- `/home remove [name]` - Remove a specified home.
- `/home rename [name] [new_name]` - Rename a specified home.
- `/home share [name] [player]` - Share a specific home with a player.
- `/home unshare [name] [player]` - Cancel sharing a home with a player. *(If no player is specified, cancels sharing for everyone)*.
- `/home sharelist [in/out]` - View shared homes. (`in` = homes shared with you, `out` = homes you shared with others).
- `/home public [name]` - Make a home publicly accessible.
- `/home otherhome [player:home]` - Teleport to another player's public home.
- `/home otherlist` - List all publicly available homes.
- `/home invite [name] [player]` - Invite a player to your home.
- `/home invite cancel [player]` - Cancel a home invitation. *(If no player is specified, cancels all invitations)*.
- `/home invite accept [player]` - Accept a home invitation. *(If no player is specified, accepts the latest invitation)*.
- `/home invite deny [player]` - Deny a home invitation. *(If no player is specified, denies the latest invitation)*.

#### ️ Warp Commands
- `/warp tp [name]` - Teleport to a specified warp point.
- `/warp set [name]` - Set a warp point at your current location **(OP Only)**.
- `/warp remove [name]` - Remove a warp point **(OP Only)**.
- `/warp list` - List all available warp points.

#### ️ Utility Commands
- `/grave` - Teleport to your last death location.
- `/back` - Teleport to your previous location before the last teleport.
- `/rtp` - Randomly teleport within the configured radius.

#### ️ Configuration (`/tpatools`)
- `/tpatools config setlanguage [en_us/zh_cn]` - Change the mod language.
- `/tpatools config setmaxhome [count]` - Set the maximum number of homes allowed per player.
- `/tpatools config needop [tpa/home/grave/back/warp]` - Toggle whether a specific command requires OP permissions.
- `/tpatools config tpawaittime [time]` - Set the TPA request timeout duration.
- `/tpatools config tpacdtime [time]` - Set the TPA command cooldown time.
- `/tpatools config homeinviteovertime [time]` - Set home invitation timeout (in seconds).
- `/tpatools config homeinvitecdtime [time]` - Set home invitation cooldown (in seconds).
- `/tpatools config rtpscope [count]` - Change the maximum RTP radius.
- `/tpatools config rtpcdtime [count]` - Change the RTP cooldown time.
- `/tpatools config setmaxwarpcount [count]` - Set the maximum number of warp points.
- `/tpatools config allowteleportrideentity [true/false]` - Allow pets/mounts to teleport with you via TPA.
- `/tpatools config safeteleport [true/false]` - Enable safer teleportation checks for `/home`, `/grave`, and `/back` *(v1.1.3+)*.
- `/tpatools debug [true/false]` - Toggle debug logging *(Added in v1.0.6)*.
- `/tpatools about` - Display mod information and credits.

####  Extra Features
- **Chat Mentions**: Messages containing player names will be highlighted and trigger a notification.
- **Quick Share**: Press `&1` to `&9` to share items from your hotbar directly into the chat.

</details>

<details>
<summary><b> 中文指令列表</b></summary>

####  TPA 传送类
- `/tpa [player]` - 请求传送到指定玩家。
- `/tpaccept [player]` - 接受传送请求。（*若未填写玩家名，则默认接受最新请求*）
- `/tpadeny [player]` - 拒绝传送请求。（*若未填写玩家名，则默认拒绝最新请求*）
- `/tpahere [player]` - 请求指定玩家传送到你身边。
- `/tpacancel [player]` - 取消对指定玩家的传送请求。
- `/tpatoggle` - 开启/关闭接收传送请求（免打扰模式）。
- `/tpalock [player]` - 屏蔽指定玩家的传送请求。
- `/tpaunlock [player]` - 解除屏蔽指定玩家的传送请求。

####  Home 家类
- `/home set [name]` - 将当前位置设置为家。
- `/home tp [name]` - 传送到指定的家。
- `/home list` - 列出你所有的家。
- `/home remove [name]` - 删除指定的家。
- `/home rename [name] [new_name]` - 重命名指定的家。
- `/home share [name] [player]` - 将指定的家分享给指定玩家。
- `/home unshare [name] [player]` - 取消对指定玩家的家分享。（*若未填写 `[player]`，则取消该家对所有人的分享*）
- `/home sharelist [in/out]` - 查看已分享的家列表。（`in` = 别人分享给你的家，`out` = 你分享给别人的家）
- `/home public [name]` - 公开你指定的家。
- `/home otherhome [player:home]` - 传送到其他玩家已公开的家。
- `/home otherlist` - 显示所有已公开的家。
- `/home invite [name] [player]` - 邀请玩家前往你的家。
- `/home invite cancel [player]` - 取消邀请。（*若未填写 `<player>`，则取消所有邀请*）
- `/home invite accept [player]` - 接受邀请。（*若未填写 `<player>`，默认接受最新邀请*）
- `/home invite deny [player]` - 拒绝邀请。（*若未填写 `<player>`，默认拒绝最新邀请*）

#### ️ Warp 领域类
- `/warp tp [name]` - 传送到指定领域。
- `/warp set [name]` - 在当前地点设置领域 **(仅限 OP)**。
- `/warp remove [name]` - 移除指定领域 **(仅限 OP)**。
- `/warp list` - 列出所有领域。

#### ️ 其他功能指令
- `/grave` - 传送到上一个死亡地点。
- `/back` - 传送到使用传送指令前的位置。
- `/rtp` - 在设定范围内随机传送。

#### ️ 设置 (`/tpatools`)
- `/tpatools config setlanguage [en_us/zh_cn]` - 更改模组语言。
- `/tpatools config setmaxhome [count]` - 设置玩家最大可设置家的数量。
- `/tpatools config needop [tpa/home/grave/back/warp]` - 设置指定指令是否需要 OP 权限。
- `/tpatools config tpawaittime [time]` - 设置 TPA 请求超时时间。
- `/tpatools config tpacdtime [time]` - 设置 TPA 指令冷却时间。
- `/tpatools config homeinviteovertime [time]` - 设置家邀请超时时间（单位：秒）。
- `/tpatools config homeinvitecdtime [time]` - 设置家邀请冷却时间（单位：秒）。
- `/tpatools config rtpscope [count]` - 更改 RTP 最大传送半径。
- `/tpatools config rtpcdtime [count]` - 更改 RTP 冷却时间。
- `/tpatools config setmaxwarpcount [count]` - 设置最大领域数量。
- `/tpatools config allowteleportrideentity [true/false]` - 允许/禁止骑乘生物随玩家一起通过 TPA 传送（默认关闭）。
- `/tpatools config safeteleport [true/false]` - 开启安全传送检测（使用 `/home`、`/grave`、`/back` 时防止卡入方块）*(v1.1.3+)*。
- `/tpatools debug [true/false]` - 开启/关闭 Debug 日志（默认关闭）*(v1.0.6 添加)*。
- `/tpatools about` - 显示模组信息及作者寄语。

####  其他特性
- **聊天高亮提醒**：发送包含其他玩家名字的消息时，会被高亮显示并提醒该玩家。
- **快捷物品分享**：在聊天框输入 `&1` 到 `&9`，即可向聊天栏分享快捷物品栏中对应位置的物品。

</details>

---

## ️ Database Support

> **️ EXPERIMENTAL FEATURE**  
> **MySQL Storage Supported!** *(Mod version 1.1.1 and above)*  
> Please note that MySQL support is currently in the **experimental testing phase**. If you encounter any issues, please provide timely feedback via **[GitHub Issues](https://github.com/kicobicn/TPATools/issues)** so we can improve it!

---

##  Future Plans
This mod will gradually be adapted for more Minecraft versions. Please stay tuned for updates!

##  Support the Project
If you like this mod, **recommending it to others** would be the greatest motivation for me to keep updating and improving it!  

*Happy teleporting!* ;)
