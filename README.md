# TPAtools

### Overview

A mod of TPA and other server tools

This mod support /tpa,/home,/back... and other command and function

If you don't want to remember so many commands, I have created menus for the /tpatools management command and the /home command, which can be called up without parameters(After version 1.1.0)

## What it can do?

The currently supported function and commands are:
<details>
<summary>English</summary>

- TPA class
1. /tpa [player] - request sent to the specified player
2. /tpaccept $[player] - to accept the request (if the player's name is not filled in, the latest request is considered accepted)
3. /tpadeny $[player] - Reject the request (if the player's name is not filled in, the latest request will be accepted)
4. /tpahere [player] - request the designated player to teleport to you
5. /tpacencel [player] - Cancel the request for the specified player
6. /tpatoggle open/close - transfer requests to yourself (do not disturb)
7. /tpalock [player] - Block the transfer request of the specified player
8. /tpaunlock [player] - unblock the transfer request of the specified player

- Home category
1. /home set [name] - Set your own location for home
2. /home tp [name] - to send the specified home
3. /home list - listall your homes
4. /home remove [name] - remove the specified home
5. /home rename [name] [rename] - rename the specified home
6. /home share [name] [player] - share your home to one player
7. /home unshare [name] [player] - Cancel sharing for players in the family (if [player] is not filled in, cancel sharing for everyone in the family)
8. /home sharelist [in/out] - View the list of shared homes (the “in” category is the list of homes shared by players for themselves, and the “out” category is the list of homes shared by players for others).
9. /home public [name] - public your home
10. /home otherhome [playername:homename] - to someone else's home [already public to you]
11. /home otherlist - shows publicly available homes
12. /home invite invite <homename> <playername> Invite the player to your home
13. /home invite cancel $<playername> Cancel invitation (if <playername> is not filled in, all invitations will be canceled)
14. /home invite accept $<playername> Accept invitation (If <playername> is not provided, the latest request will be processed by default)
15. /home invite deny $<playername> Deny invitation (If <playername> is not specified, the latest request will be processed by default)

- warp
1. /warp tp [name] - teleport to warp
2. /warp set [name] - set warp (only op)
3. /warp remove [name] - remove warp (only op)
4. /warp list - list warps

- other
1. /grave - teleported to the last place of death
2. /back - transfer to the location before using the transfer
3. /rtp - random to teleport somewhere

- setting
1. /tpatools config setlanguage [en_us/zh_cn] - change mod language
2. /tpatools config setmaxhome [count] - Set the maximum number of homes
3. /tpatools config needop [tpa/home/grave/back/warp] - changing the selected command require op or not
4. /tpatools config tpawaittime [time] (to set the required timeout duration)
5. /tpatools config tpacdtime [time] (for setting cooldown time)
6. /tpatools config homeinviteovertime <time> Set timeout (unit: seconds)
7. /tpatools config homeinvitecdtime <time> Set the cooldown time (unit: seconds)
8. /tpatools config rtpscope [count] - to change the maximum radius of rtp
9. /tpatools config rtpcdtime [count] - to change the rtp cooling time
10. /tpatools config setmaxwarpcount [count] - to change maximum of warps
11. /tpatools config allowteleportrideentity [false/true] - When use TPA, the riding creature will be teleported away.
12. /tpatools debug [true/false] - to switch debug log (add in 1.0.6)
13. /tpatools about - Show message about mod and another says

- fuctions

Send message containing player name will be highlighted and remind player

Use & 1-9 to share items from your Quick Items section with the chat section

</details>

目前支持的功能和指令有：
<details>
<summary>中文</summary>

- tpa类
1. /tpa [player] 请求传送到指定玩家
2. /tpaccept $[player] 接受请求(若未填写玩家名字则视为接受最新请求)
3. /tpadeny $[player] 拒绝请求(若未填写玩家名字则视为接受最新请求)
4. /tpahere [player] 请求指定玩家传送到自己身边
5. /tpacencel [player] 取消对指定玩家的请求
6. /tpatoggle 开启/关闭对自己的传送请求(免打扰)
7. /tpalock [player] 屏蔽指定玩家的传送请求
8. /tpaunlock [player] 解除屏蔽指定玩家的传送请求

- home类
1. /home set [name] 设置自己的位置为家
2. /home tp [name] 传送指定的家
3. /home list 列出你所有的家
4. /home remove [name] 移除指定的家
5. /home rename [name] [rename] 重命名指定的家
6. /home share [name] [player] 向指定玩家分享你的家
7. /home unshare [name] [player] 取消家对玩家的分享(若未填写[player],则取消该家对所有人的分享)
8. /home sharelist [in/out] 查看已分享的家的列表(in类为玩家给自己分享的家的列表，out类为自己给别人分享的家的列表)
9. /home public [name] 公开你指定的家
10. /home otherhome [name] 传送至其他人的家[已对你公开的]
11. /home otherlist 显示已公开的家
12. /home invite invite <homename> <playername> 邀请玩家去你的家
13. /home invite cancel $<playername> 取消邀请（若不填<playername>则取消所有邀请）
14. /home invite accept $<playername> 接受邀请（若不填<playername>默认处理最新请求）
15. /home invite deny $<playername> 拒绝邀请（若不填<playername>默认处理最新请求）

- warp类
1. /warp tp [name] 传送到指定领域
2. /warp set [name] 设置领域在此地(仅op可用)
3. /warp remove [name] 移除领域(仅op可用)
4. /warp list 列出领域列表

- 其他
1. /grave 传送到上一个死亡地点
2. /back 传送到使用传送前的位置
3. /rtp 随机传送到范围内某位置

- 设置
1. /tpatools config setlanguage [en_us/zh_cn] 更改mod语言
2. /tpatools config setmaxhome [count] 设置最大家的数量
3. /tpatools config needop [tpa/home/grave/back/warp] 设置指定指令是否需要op
4. /tpatools config tpawaittime [time] (用于设置超时所需时间)
5. /tpatools config tpacdtime [time]（用于设置冷却时间）
6. /tpatools config homeinviteovertime <time> 设置超时时间（单位：秒）
7. /tpatools config homeinvitecdtime <time> 设置冷却时间(单位：秒)
8. /tpatools config rtpscope [count] 更改rtp最大半径
9. /tpatools config rtpcdtime [count] 更改rtp冷却时间
10. /tpatools config setmaxwarpcount [count] 设置最大领域数量
11. /tpatools config allowteleportrideentity [false/true] 关闭/打开允许骑乘生物通过tpa传送 (默认关闭)
12. /tpatools debug [true/false] 打开/关闭debug日志 (默认关闭) （于1.0.6添加）
13. /tpatools about 显示一些模组信息和一些作者的话


- 其他功能

1. 发送信息包含玩家名会被高亮并提醒玩家

2. 可使用&1-9来向聊天栏分享你的快捷物品栏的物品

</details>

This mod supports mySQL storage （Mod version 1.1.1 and above）

This mod will gradually be adapted for other versions.Please stay tuned.

If you like this mod, recommending it to others will be the greatest motivation for me to update it

;)