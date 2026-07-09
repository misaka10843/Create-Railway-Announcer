**_此内容为GPT进行总结生成，实际实现等可能与此内容不同，以实际功能为主_**

---

# Create Railway Announcer 项目计划

## 1. 项目定位
项目目标是在 **Minecraft 1.21.1 + NeoForge + Create 6.0+** 环境下，为 Create 的列车系统增加一个可配置的铁路广播系统。

核心功能包括：

```text
1. 列车车内广播
2. 站台 / 站内广播
3. Windows 本地 TTS 支持
4. NaturalVoiceSAPIAdapter 支持
5. 语音包系统
6. 游戏内 GUI 配置站点、读音、换乘、开门侧等信息
7. 发车铃、门铃、提示音、melody 自定义
8. JR-like / 日本铁路风格播报模板
9. 音频片段缓存与复用
10. 多人服务器兼容
```

这个 mod 不应该宣传成“JR 官方广播”或“山手线官方声音复刻”，而应该定位为：

```text
Japanese railway-style announcements for Create trains.
```

也就是 **日本铁路风格 / JR-like 风格**，而不是直接内置真实 JR 录音、真实发车旋律或官方素材。

---

# 2. 目标用户

## 2.1 普通玩家

普通玩家希望：

```text
安装 mod 后就能用
不想额外开 TTS 服务
不想手写复杂 JSON
能在游戏内配置站点名称
列车到站、发车时有广播
```

所以默认体验应该是：

```text
Windows 用户：
  使用内置 WinTtsBridge.exe
  默认调用 Windows WinRT / OneCore voice
  如果用户装了 NaturalVoiceSAPIAdapter，则优先使用 Nanami / Keita Natural Voice

非 Windows 用户：
  默认字幕 + 资源包音频
  后续可选外部 TTS provider
```

---

## 2.2 高级玩家 / 整合包作者

高级玩家希望：

```text
自定义发车铃
自定义门铃
自定义广播模板
自定义语音片段
导入自己的音频包
做某条线路专用广播
做整合包预设
```

所以项目需要提供：

```text
语音包系统
模板系统
sequence 编排系统
station / line 导入导出
缓存清理和预生成工具
```

---

## 2.3 服务器服主

服主希望：

```text
站点配置保存在服务器世界中
玩家加入后能自动收到站点广播数据
普通玩家不能乱改站点播报
服务器性能不能被 TTS 拖垮
```

所以设计原则是：

```text
服务端只负责事件和站点数据
客户端负责 TTS、缓存、播放、混音
TTS 不在服务端执行
```

---

# 3. 总体架构

整体架构分为五层：

```text
Create Train Event Layer
        ↓
Announcement Event Layer
        ↓
Announcement Pack / Template Layer
        ↓
Client Audio Runtime Layer
        ↓
TTS Bridge / Audio Cache / Minecraft Sound Playback
```

更具体一点：

```text
服务端：
  监听 Create 列车到站 / 离站 / 接近车站等事件
  读取服务器保存的 StationConfig / LineConfig
  生成 AnnouncementPacket
  发给站点范围内的客户端

客户端：
  接收 AnnouncementPacket
  判断玩家当前位置：车内 / 站台 / 附近 / 远离
  根据当前语音包生成 sequence
  查找音频片段缓存
  缺失则调用 WinTtsBridge.exe 生成 wav
  播放音效、TTS 片段、停顿、字幕
  处理音频通道、优先级、ducking
```

---

# 4. 技术选型

## 4.1 Minecraft / Mod 开发

目标环境：

```text
Minecraft: 1.21.1
Mod Loader: NeoForge
Create: 6.0+
Language: Java
Build Tool: Gradle
Mappings: Mojang / Parchment，具体以后根据 NeoForge 项目模板确定
```

主要依赖：

```text
NeoForge API
Create API / Create internal classes
Mixin
Gson 或 Jackson
SLF4J / Log4j
```

推荐项目结构：

```text
create-railway-announcer/
  build.gradle
  settings.gradle
  gradle.properties
  src/main/java/
  src/main/resources/
  bridge/
    WinTtsBridge/
  docs/
  examples/
```

---

## 4.2 TTS Bridge

TTS Bridge 使用 C# 编写。

目标：

```text
WinTtsBridge.exe
```

技术：

```text
C# / .NET 8
System.Speech.Synthesis
Windows.Media.SpeechSynthesis
JSON 输出
CLI 命令行接口
```

Bridge 后端：

```text
winrt:
  Windows.Media.SpeechSynthesis
  用于 OneCore voices
  例如 Microsoft Haruka / Ayumi / Ichiro

sapi:
  System.Speech.Synthesis
  用于 SAPI voices
  支持 NaturalVoiceSAPIAdapter 暴露的 Nanami / Keita Natural Voice

auto:
  优先 SAPI Natural Voice
  找不到再退回 WinRT OneCore
```

Bridge 打包方式：

```text
dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```

输出：

```text
WinTtsBridge.exe
```

mod jar 内置路径建议：

```text
assets/create_railway_announcer/bridge/windows/x64/WinTtsBridge.exe
```

运行时释放到：

```text
.minecraft/config/create_railway_announcer/bridge/win-x64/WinTtsBridge.exe
```

---

## 4.3 音频格式

推荐支持：

```text
输入音效：
  ogg
  wav

TTS 输出：
  wav

缓存：
  第一版用 wav
  后续可选转 ogg
```

第一版建议先直接缓存 wav，因为 Bridge 已经能输出 wav，减少复杂度。

后续如果要更深度接入 Minecraft 声音系统，可以考虑：

```text
wav → ogg 转换
动态 SoundInstance
OpenAL 播放
```

---

# 5. 核心模块拆分

## 5.1 create_compat 模块

负责和 Create 交互。

功能：

```text
监听列车到站
监听列车离站
识别当前站点
识别列车 UUID / 名称
识别 schedule / destination
获取 Create Station 方块位置
获取 GlobalStation 信息
```

建议封装成兼容层：

```java
CreateTrainCompat
CreateStationCompat
CreateScheduleCompat
CreateRailwayCompat
```

这样以后 Create 小版本更新时，只需要集中改 compat。

---

## 5.2 announcement_event 模块

把 Create 内部事件转成 mod 自己的广播事件。

内部事件类型建议：

```java
enum AnnouncementEventType {
    ONBOARD_NEXT_STOP,
    ONBOARD_APPROACHING,
    ONBOARD_ARRIVED,
    ONBOARD_DEPARTING,
    ONBOARD_TERMINAL,

    PLATFORM_APPROACH,
    PLATFORM_ARRIVAL,
    PLATFORM_PRE_DEPARTURE,
    PLATFORM_DEPARTURE_MELODY,
    PLATFORM_DOOR_CLOSING,
    PLATFORM_DEPARTED,

    DOOR_OPENING,
    DOOR_CLOSING,
    SAFETY_NOTICE
}
```

事件数据：

```java
class AnnouncementEvent {
    UUID trainId;
    String trainName;

    UUID stationId;
    BlockPos stationPos;

    String currentStationConfigId;
    String nextStationConfigId;
    String destinationStationConfigId;

    AnnouncementEventType type;

    long gameTime;
    int priority;
    AudioChannel channel;
}
```

---

## 5.3 station_config 模块

负责游戏内配置的站点数据。

StationConfig 示例：

```json
{
  "station_id": "create_global_station_uuid",
  "custom_id": "yurakucho",
  "enabled": true,
  "display": {
    "ja_jp": "有楽町",
    "en_us": "Yurakucho",
    "zh_cn": "有乐町"
  },
  "reading": {
    "ja_jp": "有楽町",
    "en_us": "Yurakucho"
  },
  "door_side": "left",
  "platform": "1",
  "station_range": {
    "horizontal": 64,
    "vertical": 24
  },
  "transfer_line_ids": [
    "tokyo_metro_hibiya",
    "tokyo_metro_yurakucho"
  ],
  "events": {
    "onboard_next_stop": true,
    "onboard_arrived": true,
    "platform_approach": true,
    "platform_departure_melody": true,
    "door_closing": true
  }
}
```

服务端保存位置：

```text
world/serverconfig/create_railway_announcer/stations.json
```

或者用 Minecraft SavedData：

```java
CreateRailwayAnnouncerSavedData
```

第一版为了调试方便，可以先用 JSON 文件，稳定后再考虑 SavedData。

---

## 5.4 line_config 模块

负责换乘线路、所属线路、目的地线路名称等。

LineConfig 示例：

```json
{
  "id": "tokyo_metro_hibiya",
  "display": {
    "ja_jp": "地下鉄日比谷線",
    "en_us": "Tokyo Metro Hibiya Line",
    "zh_cn": "东京地铁日比谷线"
  },
  "reading": {
    "ja_jp": "地下鉄日比谷線",
    "en_us": "Tokyo Metro Hibiya Line"
  },
  "short_name": {
    "ja_jp": "日比谷線"
  }
}
```

线路片段可以缓存：

```text
cache/
  voices/
    sapi_nanami_ja_jp/
      lines/
        tokyo_metro_hibiya_bare.wav
        tokyo_metro_yurakucho_bare.wav
```

---

## 5.5 voice_pack 模块

负责语音包读取、校验、加载。

语音包目录：

```text
.minecraft/config/create_railway_announcer/packs/
  jr_like_default/
    pack.json
    templates/
      ja_jp.json
      en_us.json
      zh_cn.json
    phrases/
      ja_jp.json
      en_us.json
      zh_cn.json
    sounds/
      chime/
      melody/
      door/
      ambient/
      voice/
```

pack.json 示例：

```json
{
  "pack_format": 1,
  "id": "jr_like_default",
  "name": "JR-like Default Pack",
  "description": "Japanese railway-style announcement templates. No official railway assets included.",
  "author": "Create Railway Announcer",
  "version": "1.0.0",
  "default_language": "ja_jp",
  "supported_languages": ["ja_jp", "en_us", "zh_cn"]
}
```

语音包支持内容：

```text
1. 通用短语
2. 站台播报模板
3. 车内播报模板
4. 发车铃
5. 到站 chime
6. 开门 / 关门提示音
7. 用户自定义语音片段
8. sequence 编排
```

---

## 5.6 sequence_runtime 模块

这是整个广播系统的核心。

一段播报不是一整个音频，而是一串 sequence：

```json
{
  "event": "onboard_next_stop",
  "channel": "onboard_voice",
  "priority": 70,
  "sequence": [
    { "type": "sound", "file": "chime/ding_dong.ogg" },
    { "type": "pause", "duration_ms": 350 },
    { "type": "phrase", "id": "common.next_is" },
    { "type": "pause", "duration_ms": 420 },
    { "type": "station", "target": "next_station", "variant": "bare" },
    { "type": "pause", "duration_ms": 260 },
    { "type": "station", "target": "next_station", "variant": "desu" },
    { "type": "pause", "duration_ms": 420 },
    { "type": "door_side_phrase" },
    {
      "type": "condition",
      "if": "next_station.has_transfers",
      "then": [
        { "type": "pause", "duration_ms": 500 },
        { "type": "transfer_lines", "target": "next_station", "separator_pause_ms": 180 },
        { "type": "phrase", "id": "common.transfer_suffix" }
      ]
    }
  ]
}
```

支持的 sequence item：

```text
sound
pause
phrase
station
line
transfer_lines
door_side_phrase
destination
subtitle
condition
tts_raw
```

---

## 5.7 audio_cache 模块

负责缓存 TTS 生成出来的片段。

缓存策略：

```text
同一个 voice + language + rate + volume + phrase text 只生成一次
站名片段可复用
线路片段可复用
通用短语可复用
完整句子尽量不缓存，除非用户选择 natural_full_sentence 模式
```

缓存 key 组成：

```text
provider
backend
voice_id
language
rate
volume
text_or_ssml_hash
phrase_id
variant
pack_id
pack_version
```

缓存路径示例：

```text
.minecraft/config/create_railway_announcer/cache/
  voices/
    sapi/
      Local-MicrosoftWindows.Voice.ja-JP.Nanami.1/
        rate_092_volume_95/
          common/
            next_is.wav
            door_left.wav
            transfer_suffix.wav
          stations/
            yurakucho_bare.wav
            yurakucho_desu.wav
          lines/
            tokyo_metro_hibiya_bare.wav
```

优先级：

```text
1. 用户语音包提供的音频片段
2. 已缓存 TTS 片段
3. 调 Bridge 生成
4. 失败则显示字幕
```

---

## 5.8 audio_mixer 模块

负责播放队列、优先级、ducking。

音频通道：

```java
enum AudioChannel {
    ONBOARD_VOICE,
    PLATFORM_VOICE,
    MELODY,
    DOOR_CHIME,
    AMBIENT,
    UI_PREVIEW
}
```

默认优先级：

```text
door_closing       100
door_chime          90
onboard_arrived     85
onboard_voice       80
onboard_next_stop   70
platform_voice      60
melody              50
ambient             10
```

混音规则：

```text
同一 channel：
  高优先级打断低优先级
  同优先级可以排队
  低优先级新事件可以丢弃

不同 channel：
  可以同时播放
  但低优先级可被 ducking 压低音量
```

玩家状态影响音量：

```text
玩家在相关列车上：
  onboard_voice = 1.0
  platform_voice = 0.25
  melody = 0.4

玩家在站点范围内：
  platform_voice = 1.0
  melody = 1.0
  onboard_voice = 0.0 或 0.2

玩家远离站点：
  忽略站台播报
```

---

# 6. 玩家位置判断方案

站点范围第一版以 Create Station 方块为中心。

默认：

```toml
[station_audio]
horizontal_range = 64
vertical_range = 24
```

判断使用 AABB：

```text
x/z 半径 64
y 半径 24
```

不要一开始做复杂区域选择。

---

## 6.1 服务端职责

服务端只做粗筛：

```text
1. 某个站发生事件
2. 获取 station center
3. 获取 station_range
4. 遍历在线玩家
5. 如果玩家在 AABB 内，发送 AnnouncementPacket
```

不要服务端每 tick 判断玩家在不在车厢内。

这样性能开销较低，因为只在事件触发时运行。

---

## 6.2 客户端职责

客户端收到 packet 后判断：

```java
enum ListenerPlace {
    ON_RELATED_TRAIN,
    ON_OTHER_TRAIN,
    IN_STATION_AREA,
    NEAR_STATION,
    OUTSIDE
}
```

第一版判断方式：

```text
1. 如果玩家正坐在相关 Create train / carriage seat 上：
     ON_RELATED_TRAIN

2. 否则如果玩家在 station AABB 内：
     IN_STATION_AREA

3. 否则：
     OUTSIDE
```

暂时不判断“玩家站在移动车厢内但没有坐下”的情况。
这个判断放到后续高级版本。

原因：

```text
Create contraption 内部结构复杂
客户端 bounding box 判断容易出 bug
服务端精确判断性能和兼容性都不好
第一版用 seat 判断更稳定
```

---

# 7. 车内广播设计

## 7.1 触发事件

车内广播主要有：

```text
onboard_next_stop
onboard_approaching
onboard_arrived
onboard_departing
onboard_terminal
door_closing
```

触发来源：

```text
Train.leaveStation():
  触发 onboard_next_stop
  可预生成下一站片段

Train.arriveAt(station):
  触发 onboard_arrived

接近站点：
  低频轮询或导航距离判断
  触发 onboard_approaching

终点：
  根据 schedule 判断当前站是否为最后一站
  触发 onboard_terminal
```

---

## 7.2 车内下一站播报示例

sequence：

```json
{
  "event": "onboard_next_stop",
  "channel": "onboard_voice",
  "priority": 70,
  "sequence": [
    { "type": "sound", "file": "chime/ding_dong.ogg" },
    { "type": "pause", "duration_ms": 350 },
    { "type": "phrase", "id": "common.next_is" },
    { "type": "pause", "duration_ms": 420 },
    { "type": "station", "target": "next_station", "variant": "bare" },
    { "type": "pause", "duration_ms": 260 },
    { "type": "station", "target": "next_station", "variant": "desu" },
    { "type": "pause", "duration_ms": 420 },
    { "type": "door_side_phrase" },
    {
      "type": "condition",
      "if": "next_station.has_transfers",
      "then": [
        { "type": "pause", "duration_ms": 500 },
        { "type": "transfer_lines", "target": "next_station", "separator_pause_ms": 180 },
        { "type": "phrase", "id": "common.transfer_suffix" }
      ]
    }
  ]
}
```

听感：

```text
♪ ding-dong
次は
有楽町
有楽町です
お出口は左側です
地下鉄日比谷線
地下鉄有楽町線はお乗り換えです
```

---

# 8. 站台 / 站内广播设计

站台广播和车内广播分离。

站台事件：

```text
platform_approach
platform_arrival
platform_pre_departure
platform_departure_melody
platform_door_closing
platform_departed
safety_notice
```

---

## 8.1 站台广播范围

第一版：

```text
以 Create Station 方块为中心
horizontal_range = 64
vertical_range = 24
```

站点 GUI 可配置：

```text
广播范围：
  小：32
  中：64
  大：96

高度范围：
  8
  16
  24
```

后续版本可以加：

```text
Station Speaker 方块
多个 speaker 分组
最近 speaker 播放
```

第一版先不做，避免复杂。

---

## 8.2 站台进站播报示例

sequence：

```json
{
  "event": "platform_approach",
  "channel": "platform_voice",
  "priority": 60,
  "sequence": [
    { "type": "phrase", "id": "platform.soon" },
    { "type": "pause", "duration_ms": 250 },
    { "type": "platform", "variant": "number" },
    { "type": "phrase", "id": "platform.track_ni" },
    { "type": "pause", "duration_ms": 250 },
    { "type": "line", "target": "train_line", "variant": "bare" },
    { "type": "pause", "duration_ms": 200 },
    { "type": "destination", "variant": "yuki" },
    { "type": "phrase", "id": "platform.train_arriving" },
    { "type": "pause", "duration_ms": 450 },
    { "type": "phrase", "id": "platform.keep_behind_line" }
  ]
}
```

听感：

```text
まもなく、1番線に、山手線、東京方面行きがまいります。
危ないですから、黄色い線の内側までお下がりください。
```

---

## 8.3 发车 melody

语音包可以提供：

```text
sounds/melody/departure.ogg
sounds/melody/platform_1.ogg
sounds/melody/platform_2.ogg
```

sequence：

```json
{
  "event": "platform_pre_departure",
  "channel": "melody",
  "priority": 50,
  "sequence": [
    { "type": "sound", "file": "melody/departure.ogg", "volume": 1.0 },
    { "type": "pause", "duration_ms": 200 },
    { "type": "phrase", "id": "platform.door_closing" }
  ]
}
```

默认包只放原创 melody。
用户可自行替换。

---

# 9. TTS Bridge 详细计划

## 9.1 CLI 命令

### list-voices

```powershell
WinTtsBridge.exe list-voices --backend all --language ja-JP
```

参数：

```text
--backend winrt|sapi|all
--language ja-JP
--natural-only
```

输出：

```json
{
  "Ok": true,
  "Count": 1,
  "Voices": [
    {
      "Backend": "sapi",
      "Id": "Local-MicrosoftWindows.Voice.ja-JP.Nanami.1",
      "DisplayName": "Microsoft Nanami",
      "Language": "ja-JP",
      "Gender": "Female",
      "Description": "Microsoft Nanami (Natural) - Japanese (Japan)",
      "Provider": "Microsoft",
      "IsProbablyNatural": true
    }
  ]
}
```

---

### probe

```powershell
WinTtsBridge.exe probe --backend auto --language ja-JP --voice-contains Nanami
```

作用：

```text
返回当前最推荐使用哪个 voice
用于游戏内检测和设置界面
```

---

### synth text

```powershell
WinTtsBridge.exe synth ^
  --backend sapi ^
  --text-file input.txt ^
  --out test.wav ^
  --language ja-JP ^
  --voice-contains Local-MicrosoftWindows.Voice.ja-JP.Nanami.1 ^
  --rate 0.92 ^
  --volume 95
```

---

### synth ssml

```powershell
WinTtsBridge.exe synth ^
  --backend sapi ^
  --ssml-file input.ssml ^
  --out test.wav ^
  --language ja-JP ^
  --voice-contains Local-MicrosoftWindows.Voice.ja-JP.Nanami.1 ^
  --rate 0.92 ^
  --volume 95
```

SSML 示例：

```xml
<speak version="1.0" xml:lang="ja-JP">
  次は<break time="500ms"/>
  有楽町<break time="300ms"/>
  有楽町です<break time="300ms"/>
  お出口は<break time="180ms"/>
  左側です
</speak>
```

---

## 9.2 Bridge 错误处理

Bridge 永远输出 JSON。
成功：

```json
{
  "Ok": true,
  "Backend": "sapi",
  "Output": "test.wav",
  "Bytes": 518024,
  "Voice": {
    "Id": "Local-MicrosoftWindows.Voice.ja-JP.Nanami.1",
    "DisplayName": "Microsoft Nanami"
  },
  "InputKind": "ssml"
}
```

失败：

```json
{
  "Ok": false,
  "Error": "Voice not found",
  "Backend": "sapi",
  "ExitCode": 2
}
```

Java 侧只解析 JSON，不解析控制台杂项文本。

---

# 10. 游戏内 GUI 计划

## 10.1 广播配置器方块

新增一个方块：

```text
Announcer Configurator
广播配置器
```

用途：

```text
右键打开站点广播配置 GUI
绑定附近 Create Station
配置站点信息
配置换乘线路
试听播报
预生成缓存
```

也可以支持直接对 Create Station 右键打开，但为了兼容和减少侵入，第一版建议新增自己的方块或工具。

---

## 10.2 站点配置 GUI

字段：

```text
绑定 Create Station
站点 ID
启用车内广播
启用站台广播
显示名（日）
读音（日）
显示名（英）
显示名（中）
开门侧
站台号
广播范围
高度范围
所属线路
终点方向
换乘线路列表
```

按钮：

```text
保存
取消
试听：下一站
试听：即将到站
试听：到站
试听：站台进站
预生成本站语音
清理本站缓存
```

---

## 10.3 换乘线路 GUI

全局线路管理：

```text
新建线路
编辑线路
删除线路
选择换乘线路
```

字段：

```text
线路 ID
显示名（日）
读音（日）
显示名（英）
显示名（中）
短名
是否作为换乘可选项
```

---

## 10.4 客户端设置 GUI

客户端个人设置：

```text
启用 TTS
启用字幕
选择语音包
TTS backend：auto / sapi / winrt / off
首选声音
语速
音量
缓存目录
清理缓存
检测 NaturalVoiceSAPIAdapter
打开安装说明
```

检测界面显示：

```text
WinTtsBridge: OK
SAPI backend: OK
Natural voice: Microsoft Nanami
WinRT fallback: Microsoft Haruka
```

如果没有 NaturalVoiceSAPIAdapter：

```text
未检测到 Natural Voice。
你仍然可以使用 Windows OneCore 语音。
如需更自然的播报，请安装 NaturalVoiceSAPIAdapter，并在 Windows 中安装日语自然语音。
```

---

# 11. 网络通信设计

## 11.1 服务端发给客户端

AnnouncementPacket：

```java
class AnnouncementPacket {
    UUID announcementId;
    AnnouncementEventType eventType;

    UUID trainId;
    String trainName;

    UUID stationId;
    BlockPos stationPos;

    String currentStationConfigId;
    String nextStationConfigId;
    String destinationStationConfigId;

    int horizontalRange;
    int verticalRange;

    AudioChannel channel;
    int priority;

    long serverGameTime;
}
```

---

## 11.2 客户端请求服务端

用于 GUI：

```text
RequestStationConfigPacket
UpdateStationConfigPacket
RequestLineConfigsPacket
UpdateLineConfigPacket
PreviewAnnouncementPacket
```

权限：

```text
单机默认允许
服务器默认 OP 才能编辑
后续支持权限节点
```

权限节点预留：

```text
create_railway_announcer.edit_station
create_railway_announcer.edit_line
create_railway_announcer.preview
create_railway_announcer.reload_packs
```

---

# 12. 数据保存

## 12.1 服务端数据

保存：

```text
站点配置
线路配置
站点和 Create Station 的绑定关系
站点广播范围
站点启用事件
```

路径：

```text
world/serverconfig/create_railway_announcer/
  stations.json
  lines.json
  routes.json
```

也可以后续迁移到 SavedData。

---

## 12.2 客户端数据

保存：

```text
TTS 设置
选择的 voice pack
缓存
Bridge exe
试听历史
```

路径：

```text
.minecraft/config/create_railway_announcer/
  client.toml
  packs/
  cache/
  bridge/
```

---

# 13. 缓存与预生成

## 13.1 懒生成

默认策略：

```text
第一次需要播放某片段
  ↓
查用户音频覆盖
  ↓
查缓存 wav
  ↓
没有则调用 Bridge
  ↓
生成 wav 后播放
```

优点：

```text
启动快
不生成没用的站点
```

缺点：

```text
第一次播报可能延迟
```

---

## 13.2 预生成

GUI 提供：

```text
预生成本站语音
预生成当前线路语音
预生成全部常用短语
```

适合服主建线后提前生成。

---

## 13.3 缓存清理

清理方式：

```text
清理当前站点缓存
清理当前 voice 缓存
清理所有无效缓存
清理旧 pack 版本缓存
```

缓存元数据：

```json
{
  "voice_id": "Local-MicrosoftWindows.Voice.ja-JP.Nanami.1",
  "backend": "sapi",
  "rate": 0.92,
  "volume": 95,
  "language": "ja-JP",
  "pack_id": "jr_like_default",
  "pack_version": "1.0.0"
}
```

---

# 14. 开发阶段计划

## Phase 0：Bridge 独立完成

目标：

```text
WinTtsBridge.exe 可独立使用
支持 winrt / sapi / auto
支持 list-voices
支持 probe
支持 synth text
支持 synth ssml
支持 JSON 输出
支持错误 JSON
```

验收：

```text
能列出 Microsoft Haruka / Ayumi / Ichiro
能通过 NaturalVoiceSAPIAdapter 调用 Microsoft Nanami
能合成带 break 的 SSML
输出 wav 正常播放
```

---

## Phase 1：Mod 基础框架

目标：

```text
NeoForge 1.21.1 mod 项目搭建
依赖 Create
注册配置文件
注册网络 packet
客户端/服务端区分
日志系统
```

内容：

```text
主 mod 类
Config
PacketChannel
ClientInit
ServerInit
```

验收：

```text
游戏可启动
客户端/服务端均可加载
能发送一个测试 packet
```

---

## Phase 2：Create 事件监听

目标：

```text
能监听列车到站和离站
能拿到 trainId
能拿到 stationId / stationPos
```

实现路线：

```text
优先 Mixin Train.arriveAt(GlobalStation)
优先 Mixin Train.leaveStation()
```

也可以保留轮询 fallback。

验收：

```text
列车到站时日志输出：
  Train arrived at station X

列车离站时日志输出：
  Train departed from station X
```

---

## Phase 3：服务端站点配置

目标：

```text
保存 StationConfig
保存 LineConfig
绑定 Create Station
命令行或临时 JSON 可编辑
```

先不用 GUI，先用命令或 JSON 验证。

命令示例：

```text
/cra station setName ja_jp 有楽町
/cra station setReading ja_jp 有楽町
/cra station setDoorSide left
/cra station addTransfer tokyo_metro_hibiya
```

验收：

```text
站点配置能保存
重启后能读取
列车事件能匹配 StationConfig
```

---

## Phase 4：客户端 Bridge 调用

目标：

```text
mod 内置 WinTtsBridge.exe
启动时释放 exe
客户端调用 Bridge
生成 test.wav
```

验收：

```text
客户端设置界面或命令可执行：
  /cra client listVoices
  /cra client testTts Nanami
```

---

## Phase 5：语音包与 sequence runtime

目标：

```text
读取 pack.json
读取 phrases
读取 templates
执行 sequence
支持 sound / pause / phrase / station / line / transfer_lines
```

先用简单文件播放或日志模拟。

验收：

```text
给定 station=yurakucho
能生成并播放：
  ding-dong
  次は
  有楽町
  有楽町です
  お出口は左側です
```

---

## Phase 6：音频缓存

目标：

```text
片段级缓存
用户音频覆盖
缺失自动 TTS
缓存命中直接播放
```

验收：

```text
第一次播放生成 wav
第二次播放不再调用 Bridge
改 voice 后生成新缓存
改 station reading 后生成新缓存
```

---

## Phase 7：车内广播 MVP

目标：

```text
列车离站后播下一站
列车到站后播到站
玩家坐在相关列车座位上时听到车内广播
站台玩家默认不听车内广播或低音量
```

验收：

```text
坐在列车上能听到：
  次は、有楽町、有楽町です
到站能听到：
  有楽町、有楽町です
```

---

## Phase 8：站台广播 MVP

目标：

```text
站点范围以 Create Station 方块为中心
范围内玩家听到站台播报
列车接近时播 platform_approach
发车前播 melody / door_closing
```

验收：

```text
站台范围内玩家能听到：
  まもなく、1番線に……
玩家在列车上时站台广播音量降低
```

---

## Phase 9：GUI

目标：

```text
站点配置 GUI
线路配置 GUI
客户端 TTS 设置 GUI
试听按钮
预生成按钮
```

验收：

```text
无需手写 JSON 即可配置站点
点试听能直接播放
保存后服务器同步
```

---

## Phase 10：优化与发布

目标：

```text
错误提示完善
缓存清理
README
NaturalVoiceSAPIAdapter 安装说明
默认语音包
示例语音包
服务端权限
配置迁移
多人测试
```

---

# 15. MVP 范围建议

第一版不要做太多。最小可发布版本建议包含：

```text
1. WinTtsBridge.exe
2. SAPI + WinRT TTS
3. NaturalVoiceSAPIAdapter 支持
4. 站点配置 GUI
5. 车内下一站 / 到站广播
6. 站台进站 / 发车铃广播
7. 简单语音包
8. 片段缓存
9. Station 方块中心范围
10. 玩家坐在列车座位时识别为车内
```

不建议第一版做：

```text
复杂站点区域框选
Station Speaker 多点广播
车厢 AABB 判断
完整线路运行图
复杂时刻表播报
跨平台 TTS
自动识别所有换乘线路
真实 JR 素材内置
```

---

# 16. 风险点与解决策略

## 16.1 Create API 变化

风险：

```text
Create 6.x 小版本更新导致 Train.arriveAt / leaveStation 变动
```

解决：

```text
所有 Create 访问集中在 compat 模块
Mixin 尽量少
保留低频轮询 fallback
README 标明支持的 Create 版本
```

---

## 16.2 NaturalVoiceSAPIAdapter 不是官方组件

风险：

```text
用户没装
安装步骤复杂
不同系统表现不同
```

解决：

```text
mod 不强依赖
README 写安装步骤
游戏内检测
没有则 fallback 到 WinRT / OneCore
```

---

## 16.3 TTS 延迟

风险：

```text
第一次播报需要生成音频，可能延迟
```

解决：

```text
片段缓存
列车离站后预生成下一站
GUI 提供预生成
后台线程调用 Bridge
```

---

## 16.4 音频重叠

风险：

```text
车内广播、站台广播、melody、门铃同时播放导致混乱
```

解决：

```text
AudioChannel
Priority
Ducking
同 channel 排队或打断
玩家状态决定音量
```

---

## 16.5 站点过大

风险：

```text
以 station 方块为中心范围不够覆盖大站
```

第一版解决：

```text
可配置 station_range
小 / 中 / 大范围
```

后续解决：

```text
Station Speaker 方块
Speaker group
最近 speaker 去重播放
```

---

## 16.6 版权风险

风险：

```text
用户想放真实 JR melody 或广播录音
```

解决：

```text
默认包只放原创素材
README 声明用户自定义素材由用户自行负责
不宣传 JR 官方
不内置真实铁路素材
```

---

# 17. 推荐默认配置

client.toml：

```toml
[tts]
enabled = true
backend = "auto"
language = "ja-JP"
preferred_voice_contains = "Nanami"
fallback_voice_contains = "Keita,Haruka,Ayumi"
rate = 0.92
volume = 95
cache = true

[audio]
enable_subtitles = true
master_volume = 1.0
onboard_volume = 1.0
platform_volume = 1.0
melody_volume = 0.8
door_chime_volume = 0.9

[audio_mixing]
onboard_ducks_platform = true
platform_volume_when_on_train = 0.25
onboard_volume_when_on_platform = 0.0
same_channel_policy = "queue"
higher_priority_policy = "duck"

[station_audio]
horizontal_range = 64
vertical_range = 24
```

server.toml：

```toml
[permissions]
op_required_to_edit_station = true
op_required_to_edit_line = true

[events]
enable_onboard_announcements = true
enable_platform_announcements = true
enable_departure_melody = true

[performance]
approach_check_interval_ticks = 20
announcement_cooldown_ticks = 600
```

---

# 18. 目录结构建议

```text
create-railway-announcer/
  src/main/java/com/misaka/cra/
    CreateRailwayAnnouncer.java

    compat/create/
      CreateTrainCompat.java
      CreateStationCompat.java
      CreateScheduleCompat.java
      mixin/

    announcement/
      AnnouncementEvent.java
      AnnouncementEventType.java
      AnnouncementDispatcher.java
      AnnouncementContext.java

    config/
      ClientConfig.java
      ServerConfig.java
      StationConfig.java
      LineConfig.java
      StationConfigManager.java
      LineConfigManager.java

    pack/
      VoicePack.java
      VoicePackLoader.java
      PhraseRegistry.java
      SequenceTemplate.java

    sequence/
      SequenceItem.java
      SequenceRuntime.java
      SequenceResolver.java
      ConditionEvaluator.java

    audio/
      AudioChannel.java
      AudioPriority.java
      AudioMixer.java
      AudioCache.java
      AudioPlayer.java

    tts/
      TtsProvider.java
      WindowsBridgeTtsProvider.java
      TtsRequest.java
      TtsResult.java
      BridgeExtractor.java
      BridgeProcessRunner.java

    network/
      AnnouncementPacket.java
      StationConfigPackets.java
      LineConfigPackets.java

    client/gui/
      StationConfigScreen.java
      LineConfigScreen.java
      ClientTtsSettingsScreen.java
      PreviewScreen.java

    block/
      AnnouncerConfiguratorBlock.java
      AnnouncerConfiguratorBlockEntity.java

  src/main/resources/
    META-INF/neoforge.mods.toml
    pack.mcmeta
    assets/create_railway_announcer/
      lang/
      textures/
      sounds/
      bridge/windows/x64/WinTtsBridge.exe
      default_packs/jr_like_default/

  bridge/
    WinTtsBridge/
      src/
      README.md
```

---

# 19. 最终总结

这个项目的最终形态可以概括成：

```text
Create Railway Announcer 是一个 Create 铁路广播附属 mod。

它通过服务端监听 Create 列车事件，
通过游戏内 GUI 配置站点、读音、换乘和开门侧，
通过语音包定义发车铃、门铃、提示音和播报 sequence，
通过 Windows C# Bridge 调用 WinRT / SAPI / NaturalVoiceSAPIAdapter 生成 TTS，
通过片段缓存复用站名、线路名和通用短语，
通过客户端音频通道、优先级和 ducking 区分车内广播和站台广播。
```

第一版开发重点应该是：

```text
Bridge 稳定
Create 到站/离站事件稳定
站点 GUI 能用
片段缓存能用
车内和站台基础播报能用
```

后续再逐步加：

```text
Station Speaker
更精确车厢判断
复杂站台区域
更多语音包格式
跨平台 TTS
外部 TTS Provider
```

这个计划整体是可落地的，而且技术风险主要集中在两个地方：**Create 内部事件兼容** 和 **Minecraft 客户端动态音频播放**。TTS Bridge 现在已经跑通 Nanami，自定义语音包和片段缓存的方向也已经比较清晰，所以项目可以先从 Bridge + mod 基础框架开始推进。
