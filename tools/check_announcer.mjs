import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const root = path.resolve(import.meta.dirname, "..");
process.chdir(root);
const packageRoot = "src/main/java/com/misaka10843/create_railway_announcer/";
const integrationMutations = {
    "--reverse-condition": { file: "sequence/SequenceConditions.java", needle: 'match &= value;', value: 'match |= value;', expected: "Condition did not select false branch" },
    "--reverse-pause": { file: "playback/PlaybackClock.java", needle: '(paused?pausedAt:time.getAsLong())', value: 'time.getAsLong()', expected: "Playback timeline advanced while paused" },
    "--reverse-door-hook": { file: "mixin/CarriageDoorChimeMixin.java", needle: 'method = "setBlock"', value: 'method = "setBlocks"', expected: "Door mixin injection target is wrong" },
    "--reverse-platform-audio": { file: "sequence/SequenceResolver.java", needle: 'case "platform_arrival_dynamic" -> profile.platformArrivalSsml();', value: 'case "platform_arrival_dynamic" -> profile.arrivalSsml();', expected: "Station and onboard arrival SSML leaked across channels" },
    "--reverse-door-chime": { file: "compat/create/DoorChimeGate.java", needle: "tick - previous < 5", value: "tick - previous < 0", expected: "Simultaneous doors played duplicate chimes" },
    "--reverse-cache": { file: "audio/AudioCacheFiles.java", needle: "if (generation != epoch) return false;", value: "if (generation < 0) return false;", expected: "Cleared cache was repopulated by stale generation" },
    "--reverse-profile": { file: "model/StationAnnouncementSettings.java", needle: 'tag.getString("Voice"),', value: 'tag.getString("MissingVoice"),', expected: "Station announcement profile lost in NBT" },
    "--reverse-station": { file: "client/runtime/AnnouncementPlaybackRequest.java", needle: "if (!next && notBlank(packet.currentStationConfigId())) return packet.currentStationConfigId();", value: "if (next && notBlank(packet.currentStationConfigId())) return packet.currentStationConfigId();", expected: "Announcement uses wrong event station" },
    "--reverse-global": { file: "data/AnnouncementSavedData.java", needle: 'tag.putBoolean("Enabled",enabled);', value: 'tag.putBoolean("Enabled",true);', expected: "World snapshot lost global switch or audio profile" },
    "--reverse-template": { file: "announcement/AnnouncementSequenceMapper.java", needle: 'case PLATFORM_ARRIVAL -> "platform_arrival_dynamic";', value: 'case PLATFORM_ARRIVAL -> "missing_platform_arrival_dynamic";', expected: "Dispatched event has no bundled template" },
    "--reverse-geometry": { file: "model/SpeakerGeometry.java", needle: "up=Shapes.or(up,Shapes.create(box.minX,1-box.maxZ,box.minY,box.maxX,1-box.minZ,box.maxY));", value: "up=Shapes.or(up,Shapes.create(box.minX,box.minZ,box.minY,box.maxX,box.maxZ,box.maxY));", expected: "Speaker model/selection bounds differ" },
    "--reverse-error": { file: "tts/HttpTtsProvider.java", needle: "while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) cause = cause.getCause();", value: "while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) break;", expected: "Network error did not expose its underlying cause", probe: "main" }
};
const reverseKind = ["--reverse-test", "--reverse-lifecycle", "--reverse-playback", "--reverse-runtime", "--reverse-queue", "--reverse-headers",...Object.keys(integrationMutations)].find(flag => process.argv.includes(flag));
const output = path.join(root, "build/announcer-probe", reverseKind ? reverseKind.slice(2) : "normal");
fs.mkdirSync(output, { recursive: true });
if (process.argv.includes("--live")) {
    const ssml = '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="ja-JP"><voice name="ja-JP-NanamiNeural"><prosody rate="-15%">次は、<break time="300ms"/>秋葉原、秋葉原です。</prosody></voice></speak>';
    const response = await fetch("https://tts.wangwangit.com/v1/audio/speech", {
        method: "POST", headers: { "Content-Type": "application/json",
            "Referer": "https://tts.wangwangit.com/", "Origin": "https://tts.wangwangit.com",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0" },
        body: JSON.stringify({ format: "ssml", ssml }), signal: AbortSignal.timeout(45000)
    });
    const bytes = Buffer.from(await response.arrayBuffer());
    console.log(JSON.stringify({ status: response.status, type: response.headers.get("content-type"), bytes: bytes.length, magic: bytes.subarray(0, 12).toString("hex") }));
    if (!response.ok || bytes.length < 128) throw new Error("Live synthesis failed");
    fs.writeFileSync(path.join(output, "live.mp3"), bytes);
    process.exit(0);
}
const javaHome = process.env.JAVA_HOME || "D:/PL/Zulu/zulu-21";
const artifacts = fs.readdirSync("build/moddev/artifacts").filter(name => /^neoforge-.*\.jar$/.test(name) && !/sources|merged|client-extra/.test(name));
const runtimeFile = process.argv.includes("--runtime-file") ? process.argv[process.argv.indexOf("--runtime-file") + 1] : "build/moddev/clientLegacyClasspath.txt";
const runtime = fs.readFileSync(runtimeFile, "utf8").trim().split(/\r?\n/);
if (reverseKind === "--reverse-runtime") {
    const mutated = runtime.filter(file => path.basename(file) !== "jave-core-3.5.0.jar");
    if (mutated.length !== runtime.length - 1) throw new Error("Runtime mutation did not remove exactly one core jar");
    const target = path.join(output, "clientLegacyClasspath.txt");
    fs.writeFileSync(target, mutated.join("\n"));
    const result = spawnSync(process.execPath, [process.argv[1], "--runtime-file", target], { encoding: "utf8" });
    if (result.status === 0 || !result.stderr.includes("Client runtime is missing jave-core")) throw new Error("Missing client runtime was not rejected");
    console.log("PASS reverse test: stale client runtime without JAVE is rejected");
    process.exit(0);
}
for (const artifact of ["jave-core", "jave-nativebin-win64", "jave-nativebin-linux64", "jave-nativebin-osx64", "jave-nativebin-osxm1"]) {
    if (!runtime.some(file => path.basename(file) === artifact + "-3.5.0.jar" && fs.existsSync(file))) {
        throw new Error("Client runtime is missing " + artifact + "; run prepareClientRun or reload the Gradle project");
    }
}
const classpath = [path.join(root, "build/classes/java/main"), path.join(root,"build/resources/main"), ...artifacts.map(name => path.join(root, "build/moddev/artifacts", name)),
    ...runtime,...fs.readFileSync("build/announcerProbeClasspath.txt","utf8").trim().split(/\r?\n/)].join(path.delimiter);
const reverse = reverseKind === "--reverse-test";
const files = ["AnnouncerProbe.java", "AnnouncerLifecycleProbe.java", "AnnouncerIntegrationProbe.java"].map(file => path.join(root, "tools/java", file));
const integrationMutation = integrationMutations[reverseKind];
if (integrationMutation) {
    const source = fs.readFileSync(packageRoot + integrationMutation.file,"utf8");
    if (source.split(integrationMutation.needle).length !== 2) throw new Error("Integration mutation did not match exactly once");
    const mutated = source.replace(integrationMutation.needle,integrationMutation.value);
    if (mutated === source) throw new Error("Integration mutation was a no-op");
    const target = path.join(output,path.basename(integrationMutation.file));
    fs.writeFileSync(target,mutated);
    files.push(target);
}
if (reverse) {
    const source = fs.readFileSync("src/main/java/com/misaka10843/create_railway_announcer/tts/Ssml.java", "utf8");
    const needle = 'factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);';
    if (source.split(needle).length !== 2) throw new Error("Reverse mutation did not match exactly once");
    const mutated = source.replace(needle, 'factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);');
    if (mutated === source) throw new Error("Reverse mutation was a no-op");
    const target = path.join(output, "Ssml.java");
    fs.writeFileSync(target, mutated);
    files.push(target);
}
if (["--reverse-lifecycle", "--reverse-playback", "--reverse-queue", "--reverse-headers"].includes(reverseKind)) {
    const lifecycle = reverseKind === "--reverse-lifecycle";
    const queue = reverseKind === "--reverse-queue";
    const headers = reverseKind === "--reverse-headers";
    const name = lifecycle ? "client/runtime/ClientAnnouncementServices.java" : queue || headers ? "tts/HttpTtsProvider.java" : "playback/PlaybackScheduler.java";
    const source = fs.readFileSync("src/main/java/com/misaka10843/create_railway_announcer/" + name, "utf8");
    const needle = lifecycle ? "public static void disconnect() {" : headers ? '"Referer", "https://tts.wangwangit.com/"' : queue ? "new ArrayBlockingQueue<>(64)" : "executor.shutdown();";
    if (source.split(needle).length !== 2) throw new Error("Lifecycle mutation did not match exactly once");
    const mutated = source.replace(needle, lifecycle ? needle + "\n        services();" : headers ? '"Referer", "https://invalid.example/"' : queue ? "new LinkedBlockingQueue<>()" : "");
    if (mutated === source) throw new Error("Lifecycle mutation was a no-op");
    const target = path.join(output, path.basename(name));
    fs.writeFileSync(target, mutated);
    files.push(target);
}
function run(executable, args) {
    const file = path.join(output, executable + ".args");
    fs.writeFileSync(file, args.map(value => JSON.stringify(value)).join("\n"));
    return spawnSync(path.join(javaHome, "bin", executable), ["@" + file], { encoding: "utf8", env: {
        ...process.env, JAVA_TOOL_OPTIONS: "-Djdk.net.unixdomain.tmpdir=D:/PL/.gradle"
    } });
}
let result = run("javac", ["-proc:none", "-encoding", "UTF-8", "-cp", classpath, "-d", output, ...files]);
if (result.status !== 0) throw new Error(result.stdout + result.stderr);
if (process.argv.includes("--live-java")) {
    result = run("java", ["-cp",output + path.delimiter + classpath,"AnnouncerProbe",output,"--live-http"]);
    process.stdout.write(result.stdout);
    if (result.status !== 0) throw new Error(result.stderr);
    process.exit(0);
}
result = run("java", ["--add-modules", "jdk.httpserver", "-cp", output + path.delimiter + classpath, "AnnouncerProbe", output]);
if (integrationMutation) {
    if (integrationMutation.probe !== "main") {
        if (result.status !== 0) throw new Error(result.stderr);
        result = run("java",["-cp",output + path.delimiter + classpath,"AnnouncerIntegrationProbe",output]);
    }
    if (result.status === 0 || !result.stderr.includes(integrationMutation.expected)) throw new Error("Integration regression was not rejected: "+result.stderr);
    console.log("PASS reverse test: "+reverseKind+" detected");
    process.exit(0);
}
if (reverse) {
    if (result.status === 0 || !result.stderr.includes("DOCTYPE accepted")) throw new Error("Reverse test did not reject insecure XML parsing: " + result.stderr);
    console.log("PASS reverse test: allowing DOCTYPE fails the actual SSML probe");
} else if (reverseKind === "--reverse-playback") {
    if (result.status === 0 || !result.stderr.includes("Playback scheduler threads leaked")) throw new Error("Scheduler leak was not rejected: " + result.stderr);
    console.log("PASS reverse test: leaking playback executor threads fails the actual playback probe");
} else if (reverseKind === "--reverse-queue") {
    if (result.status === 0 || !result.stderr.includes("Saturated synthesis queue did not reject promptly")) throw new Error("Unbounded synthesis queue was not rejected: " + result.stderr);
    console.log("PASS reverse test: unbounded synthesis queue fails the saturation probe");
} else if (reverseKind === "--reverse-headers") {
    if (result.status === 0 || !result.stderr.includes("Missing service Referer")) throw new Error("Wrong service headers were not rejected: " + result.stderr);
    console.log("PASS reverse test: incorrect service Referer is rejected");
} else {
    process.stdout.write(result.stdout);
    if (result.status !== 0) throw new Error(result.stderr);
    result = run("java", ["-cp", output + path.delimiter + classpath, "AnnouncerIntegrationProbe", output]);
    process.stdout.write(result.stdout);
    if (result.status !== 0) throw new Error(result.stderr);
    const noJave = classpath.split(path.delimiter).filter(file => !path.basename(file).startsWith("jave-")).join(path.delimiter);
    result = run("java", ["-cp", output + path.delimiter + noJave, "AnnouncerLifecycleProbe", output]);
    if (reverseKind === "--reverse-lifecycle") {
        if (result.status === 0 || !result.stderr.includes("Disconnect triggered service initialization")) throw new Error("Eager disconnect initialization was not rejected: " + result.stderr);
        console.log("PASS reverse test: initializing announcement services during disconnect is rejected");
        process.exit(0);
    }
    process.stdout.write(result.stdout);
    if (result.status !== 0) throw new Error(result.stderr);
    const jars = fs.readdirSync("build/libs").filter(file => file.endsWith(".jar") && !/sources|javadoc/.test(file));
    if (jars.length !== 1) throw new Error("Expected one distributable mod jar");
    result = run("java", ["-cp", output + path.delimiter + classpath, "AnnouncerLifecycleProbe", output, path.join(root, "build/libs", jars[0])]);
    process.stdout.write(result.stdout);
    if (result.status !== 0) throw new Error(result.stderr);
}
