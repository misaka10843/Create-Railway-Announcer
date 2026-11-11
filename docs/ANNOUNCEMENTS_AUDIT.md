# 播报功能核查（2026-10-27）

本清单以当前代码中的配置入口、触发点、消息与播放链路为依据。原 Announcer
README 仍为 MDK 模板，不能作为功能完成证明。枚举值、模板或配置字段单独存在，
不算完整功能。以下“已接通”指代码链路与自动检查，游戏内验收仍单独列出。

| 功能 | 当前状态及边界 | 主要代码入口 |
| --- | --- | --- |
| 站点与站台数据 | 使用现有站点控制器及 Create 站点 UUID 绑定 | `StationControllerUi`、`RailwaySignageSavedData` |
| 站台播报配置 | 独立音色/语速/音高、进站/到站/关门 SSML，随世界保存和同步 | `StationAnnouncementSettings`、`ControllerAnnouncementEditor` |
| 车内播报配置 | 独立音色/语速/音高、下一站/到站/关门 SSML；开门侧不再写死左侧 | 同上、`SequenceResolver` |
| 播放分离 | `PLATFORM_VOICE` 面向设备范围；`ONBOARD_VOICE` 面向对应列车乘客；独立会话，服务器拒绝事件与通道不匹配的请求 | `ServerAnnouncementDispatcher`、`ClientAnnouncementRuntime` |
| 实体站台扬声器 | 可合成、放置、选择站台、范围、启停、红石触发及测试；区块卸载会注销音源 | `RailwaySpeakerBlockEntity`、`SpeakerUi` |
| 实体车载扬声器 | 装配列车后从 carriage contraption 识别；没有设备则不播放车内语音或门提示音 | `Create6TrainAdapter.hasOnboardSpeaker` |
| 自动下一站/到站语音 | 根据列车运动变化、绑定站点及导航/时刻表目标触发；没有可识别下一站时保持静默 | `TrainAnnouncementController` |
| 自动站台进站/到站 | 导航剩余距离 128 内触发进站；实际停靠已绑定站点触发到站；低频检测并非精确到 tick | 同上 |
| 关门语音 | 控制器测试、站台扬声器红石/测试入口；车内关门测试要求对应站台有列车。不会把发车视为可提前预测关门 | `SpeakerScreenPayload.Test` |
| 开关门提示音 | 已加入用户的 `doorBip.mp3` 转换 OGG；监听 Create 列车滑动门 OPEN 实际变化，时刻表和手动操作走同一入口 | `CarriageDoorChimeMixin`、`CreateDoorChimes` |
| 提示音时序 | 门开始改变状态时播放；不阻塞门动作。同列多门在 5 tick 内共用事件，每名听众只收一次，车尾附近听众也可收到；与语音通道并行，无迟到补播 | `DoorChimeGate` |
| 全局/个人控制 | 世界全局管理员开关；个人总开关、车内/站台/门提示音独立开关；服务端各事件类别开关 | 服务端/客户端 config、个人设置屏幕 |
| TTS | HTTP SSML、指定站点请求头、HTTP/1.1、真实错误原因、有界后台任务 | `HttpTtsProvider`、`AudioCache` |
| 缓存 | 生成缓存复用、统计、手动/自动预载、删除、避免删除后后台写回；不删除用户录音 | `AudioCacheFiles`、`ClientAnnouncementServices` |

## 尚未形成完整产品功能的部分

- `ONBOARD_APPROACHING`、`ONBOARD_TERMINAL`、`PLATFORM_DEPARTED`、`SAFETY_NOTICE`
  没有完整的自动触发、默认模板及用户配置链路。它们仍是枚举占位，不能算支持。
- `DOOR_OPENING` 语音不是新增的实际开门提示音。当前只有开关门音效监听，
  不把开门状态事件当作一套已完成的开门语音功能。
- `ONBOARD_DEPARTING` 和 `PLATFORM_PRE_DEPARTURE` 有模板别名，但没有独立的自动触发流程。
- 关门前预告、播放完语音后才关门、发车联锁尚未实现。条件式时刻表可能等待红石、
  乘客、货物或时刻；静态停站时长不足以预测门动作。当前也不会控制 Create 车门。
- 提供的文件是同一段提示音，开门/关门共用；没有另行虚构两套不同录音。
- 其他模组车门、原版门、未装配的静态门不在当前列车滑动门适配范围内。
- 发车音乐只有可选模板与本地 `sounds/departure.ogg` 读取能力；没有随模组提供
  发车音乐文件，也没有控制器配置/正常自动触发入口，不算已完成的发车音乐系统。
- `CONDITION` 序列节点仍返回空列表，不能当作已实现的条件表达式引擎。
- 内置运行包仍为 `jr_like_default`、`ja_jp`；没有完整的语音包管理 GUI、
  服务端分发录音、多语言轮播、按线路/车型播报规则或逐站例外规则编辑器。
- 在同一列车的多节车厢中，车载设备目前按整列列车识别，未实现逐车厢声音区域。
- TTS 初次合成需要网络时间，客户端独立合成；没有服务器统一音频时间轴，
  不保证不同客户端首次未缓存语音严格同步。短门提示音是本地文件，不依赖 TTS。

## 验证范围

自动检查覆盖配置 NBT/消息往返、站台与车内音色和 SSML 隔离、实际播放策略、
缓存所有权/清除、模板和设备几何，以及门事件去重。门适配使用实际 Create 6.0.10
类的 `setBlock(BlockPos, StructureBlockInfo)` 签名与编译后的 hook 检查，反向测试会
破坏通道选择或去重并确认检查失败。

仍需游戏中验证：进档与多人连接、GUI 比例、站台和车内同时播放、自动停站与手动停站、
手动门操作、同列多门去重、切换门控制模式、装拆列车、进出区块和维度、客户端静音开关。
构建和无界面检查不能证明以上游戏表现已验收。原有区间指示灯模型 150 处 UV 越界
与本轮播报修改无关，仍保留在资源验证结果中。
