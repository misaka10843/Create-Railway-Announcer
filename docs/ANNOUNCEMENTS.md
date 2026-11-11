# Railway announcements

Update both client and server together: controller and speaker packet protocols
changed in this development build. Station audio now has one `Announcements` NBT
profile. Old experimental reading/door tags are not migrated; configure those
audio fields again in the controller. Station names and platform bindings remain
in the existing station registry.

## Operator setup

1. Use a wrench on a station controller. Define the station's name, romanized
   name, lines and Create platform bindings in its first two pages.
2. Open **Station audio**. Set the station enable switch, reading override and
   each platform's door side. Onboard and platform sections have independent
   voice, speed, pitch and SSML settings. Blank voice/speed/pitch fields inherit
   each listener's local defaults, without borrowing the other section's values.
3. Onboard next-stop/arrival/closing and platform approach/arrival/closing SSML
   are separate. Empty fields use
   the bundled sequences. The native multiline editors preserve drafts when
   switching controller pages or returning from personal settings.
4. Save the controller before testing. Operator permission is required for
   audio/device edits by default; `permissions.opRequiredToEditStation` in the
   announcer server config controls that permission.
5. Place **Station Speakers** near the bound controller (within the controller
   locator's 64-block search radius). They bind to its station automatically.
   Right-click or wrench a speaker to select all platforms or one platform,
   its horizontal/vertical range, enable switch and redstone-triggered event.
6. Put an enabled **Onboard Speaker** into a Create train before assembling it.
   No train UUID or command binding is needed: the adapter reads the assembled
   carriage's block data.
7. Run a real Create schedule using the bound stations. Next-stop and arrival
   speech requires an onboard speaker; platform approach/arrival speech requires
   a loaded, enabled station speaker. Unsupported/unbound destinations remain
   silent.

Both speakers are craftable and appear in the mod creative tab. They support all
six mounting directions and Create wrench pickup. Their casing uses Create pixel
materials; generated speech is converted to mono OGG for positional station audio.

The controller's saved-event selector can test platform approach, arrival,
door-closing and onboard next-stop, arrival and door-closing at a selected platform. The onboard test
requires a train stopped at that Create station and an onboard speaker.
A station speaker's **Test saved broadcast settings** button uses its saved
redstone event; a rising redstone input triggers the same event. Approach and
arrival also follow Create's train navigation automatically. Door-closing is an
explicit trigger for speech. The supplied door chime instead follows actual
Create sliding-door state changes, including manual operation; it does not
predict or delay door movement from schedule conditions.

The bundled `door_bip.ogg` is a mono Vorbis conversion of the user-supplied
`doorBip.mp3` (about 3.4 seconds). Both opening and closing use this one recording.
It plays on `DOOR_CHIME`, independently of either voice channel. Multiple doors
in the same direction of change within five ticks share one delivery ID; each
listener receives it once, including listeners near doors on rear carriages.
An enabled onboard speaker is required.
Passengers hear it onboard; nearby listeners hear spatial audio within 24 blocks
horizontally and 8 vertically. No catch-up replay is registered for this short sound.
The global switch, server `events.enableDoorChimes`, personal master switch and
personal door-chime switch apply. This adapter covers Create sliding doors;
doors supplied by other mods need their own state-change adapters.

Personal settings have independent receive switches for onboard speech,
platform speech and door chimes. They retain separate playback channels and
volume configuration. A station speaker never substitutes for an onboard speaker.

See [ANNOUNCEMENTS_AUDIT.md](ANNOUNCEMENTS_AUDIT.md) for the current implementation
inventory and remaining gaps.

Stations, lines and platforms come from the same registry as signage and tickets.
The adapter uses actual Create station UUIDs and the navigation target. An
ambiguous wildcard with no concrete target is left unresolved.

## Server-wide switch

Operators with permission level 2 can change **Enable announcements server-wide**
on the controller's audio page. It is saved with the world, mirrored to clients,
and checked by automatic events, manual tests and redstone triggers. Turning it
off stops active playback and automatic preloading. Saving another station
without touching the global switch does not overwrite that switch.

The existing announcer server config retains separate onboard/platform event
switches, ranges, detection interval and duplicate-event cooldowns. The global
world switch does not override a disabled server-config event family.

## Personal playback and cache

Open the mod's **Config** screen, the personal-settings button inside a station
controller, or bind **Announcement Settings** in Minecraft's controls. The client
command `/crs_announcer` is also available as a shortcut.

Personal settings provide:

- Receive announcements independently of whether new TTS may be generated.
- Automatic preloading on registry updates and a manual preload action.
- Subtitles and local voice/service defaults.
- Ordinary-text or complete-SSML preview, plus stop playback.
- Cache file count, MiB, in-flight generation count and pending preload stations.
- Refresh statistics and delete generated audio.

Automatic preloading is opt-in and processes stations in sequence. Manual
preloading can prepare audio while reception is disabled. It uses the saved
station configuration and reports failures rather than claiming every preload
succeeded. Existing cached audio can still play with new TTS generation disabled.

Cache deletion only removes owned generated filenames inside
`config/create_railway_signage/announcer/cache/`. It preserves other files and
directories. Deletion invalidates in-flight generations, so old completions
cannot publish finished OGG files back into the cleared cache. Already running
HTTP requests may finish and clean up their temporary input files.

Station announcements are spatial, use the speaker's range and stop when the
listener leaves it. Onboard speech is heard by passengers of that train and stops
after leaving it. Overlapping speakers' packets share an announcement UUID and
are deduplicated on the client. Each playback has its own audio backend; stopping
one channel does not stop every other channel.

## HTTP and SSML

Client config: `config/create_railway_signage-announcer-client.toml`.
Server config: the world's
`serverconfig/create_railway_signage-announcer-server.toml`.

The default endpoint is `https://tts.wangwangit.com/v1/audio/speech`.
Requests to that host include the supplied website Referer, Origin and Edge 156
User-Agent. Those headers are not attached to other hosts. A local Bearer token
and timeout are editable in personal settings.

The provider uses HTTP/1.1 and this service's SSML JSON envelope:

```json
{
  "format": "ssml",
  "ssml": "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"ja-JP\"><voice name=\"ja-JP-NanamiNeural\"><prosody rate=\"-15%\" pitch=\"0Hz\">次は、<break time=\"300ms\"/>秋葉原、秋葉原です。</prosody></voice></speak>"
}
```

Ordinary text is XML-escaped and wrapped in SSML. Complete SSML controls its own
voice and prosody. Controller SSML supports `{station}`, `{station_reading}`,
`{platform}`, `{voice}`, `{rate}` and `{pitch}`; inserted text is escaped.
For example:

```xml
<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="ja-JP">
  <voice name="{voice}">
    <prosody rate="{rate}" pitch="{pitch}">
      次は、<break time="300ms"/>{station_reading}、{station_reading}です。
    </prosody>
  </voice>
</speak>
```

DTD/external entities are rejected. Limits are 8 KiB per SSML document, 16 MiB per
audio response, two synthesis workers and two conversion workers, with at most
64 queued tasks in each pool. Idle workers expire after 30 seconds. Cached keys
include the endpoint, voice, language, exact speed, volume, pitch, text and SSML
mode. Identical in-flight requests on the shared cache reuse one future.

Network failures unwrap completion/execution wrappers and display the underlying
type/message. The log contains diagnostic details with the configured token
redacted from the displayed message. A network failure does not crash the world.
Subtitles remain available when the selected sequence supplies them.

The JAVE/FFmpeg runtime is bundled for Windows x64, Linux x64, macOS x64 and macOS
ARM64. Only Windows native conversion has been tested here. Services/encoders load
on demand; disconnect does not initialize them. Playback threads are released at
completion and cancellation interrupts pauses/audio waits.

## Packs and generated resources

Bundled pack:
`assets/create_railway_announcer/default_packs/jr_like_default/`.
The mod extracts it to
`config/create_railway_signage/packs/jr_like_default/`.
New built-in template IDs are merged into the extracted file without replacing
existing customized IDs. `/crs_announcer reload` reloads the local pack.

- `pack.json`: metadata.
- `phrases/ja_jp.json`: reusable plain-text or SSML phrases.
- `templates/ja_jp.json`: ordered station/line/phrase/sound/pause/subtitle items.
- `sounds/`: optional local OGG files such as `departure.ogg`; no copyrighted
  recordings are bundled. The departure-melody template is available to custom
  pack/event integrations.

A `tts_raw` item accepts complete SSML with `ssml: true`. Sequence pauses are
local scheduling delays; SSML uses standard `<break>`. Another TTS service may
need a different HTTP envelope or authentication adapter even if it supports SSML.

Speaker resources are generated by `data/SignPanelModelProvider` into
`src/generated/resources`. `model/SpeakerGeometry` owns the body parts and
selection geometry. There is one shared body, two material children and six
facing variants per speaker, plus item models, loot and recipes. Enabled/redstone
states reuse those models.

## Verification

```powershell
.\gradlew.bat compileJava check build --no-daemon
.\gradlew.bat runData --no-daemon
node tools/check_announcer.mjs --live-java
node tools/check_announcer.mjs --reverse-test
node tools/check_announcer.mjs --reverse-lifecycle
node tools/check_announcer.mjs --reverse-playback
node tools/check_announcer.mjs --reverse-runtime
node tools/check_announcer.mjs --reverse-queue
node tools/check_announcer.mjs --reverse-headers
node tools/check_announcer.mjs --reverse-cache
node tools/check_announcer.mjs --reverse-profile
node tools/check_announcer.mjs --reverse-station
node tools/check_announcer.mjs --reverse-global
node tools/check_announcer.mjs --reverse-template
node tools/check_announcer.mjs --reverse-geometry
node tools/check_announcer.mjs --reverse-error
```

Gradle `check` runs actual SSML, NBT, codec, cache-publication, audience and audio
tests, checks generated client dependencies and embedded libraries, and compares
all six speaker models with the selection shapes using Minecraft's own rotation
matrices. Reverse tests compile isolated regressions and require them to fail.
IDE synchronization regenerates client/server dependency classpaths; reload the
Gradle project after changing dependencies.

On 2026-10-27 the actual Java provider received 26,784 bytes and converted the
response into a 4.464-second OGG. This proves that request/conversion path in this
environment, not in-game pronunciation, sound positioning or device behavior.

In-game acceptance still needs: controller drafts and GUI scale 3; administrator
and non-operator accounts; single/wide stations; speaker placement at normal and
oblique angles; redstone triggers; assembled trains; entering/leaving range and
carriages; server mute; cold/warm caches; resource and sound-device reloads.
The existing section-indicator model still has 150 out-of-range UVs in the
resource validator; this announcement work does not edit that model.
