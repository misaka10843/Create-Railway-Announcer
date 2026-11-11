import com.misaka10843.create_railway_announcer.audio.*;
import com.misaka10843.create_railway_announcer.tts.*;
import com.misaka10843.create_railway_announcer.client.runtime.ClientAnnouncementServices;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.jar.*;

public class AnnouncerLifecycleProbe {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) { packaged(Path.of(args[1])); return; }
        try {
            Class.forName("ws.schild.jave.EncoderException");
            throw new AssertionError("Missing-dependency test still has JAVE on its classpath");
        } catch (ClassNotFoundException expected) {}
        if (ClientAnnouncementServices.isInitialized()) throw new AssertionError("Services initialized before use");
        try {
            ClientAnnouncementServices.disconnect();
            ClientAnnouncementServices.disconnect();
        } catch (Throwable error) { throw new AssertionError("Disconnect triggered service initialization", error); }
        if (ClientAnnouncementServices.isInitialized()) throw new AssertionError("Disconnect triggered service initialization");
        var provider = new TtsProvider() {
            public String id() { return "test"; }
            public boolean isAvailable() { return true; }
            public CompletableFuture<TtsResult> synthesize(TtsRequest request) { throw new AssertionError("Unexpected synthesis"); }
        };
        var cache = new AudioCache(provider);
        var field = AudioCache.class.getDeclaredField("encoder");
        field.setAccessible(true);
        System.out.println("Testing the missing-JAVE fallback; the following encoder error is intentional");
        var result = ((AudioEncoder) field.get(cache)).encodeToOgg(Path.of(args[0], "unused.mp3"), Path.of(args[0], "unused.ogg"));
        if (result.ok() || !result.error().contains("NoClassDefFoundError")) throw new AssertionError("Missing encoder did not become an audio failure");
        System.out.println("PASS: disconnect and cache construction work without JAVE; encoding fails gracefully");
    }
    private static void packaged(Path jar) throws Exception {
        try (var archive = new JarFile(jar.toFile())) {
            var entry = archive.getJarEntry("META-INF/jarjar/jave-core-3.5.0.jar");
            if (entry == null) throw new AssertionError("Distributable does not embed JAVE core");
            boolean encoder = false, exception = false;
            try (var nested = new JarInputStream(archive.getInputStream(entry))) {
                for (var file = nested.getNextJarEntry(); file != null; file = nested.getNextJarEntry()) {
                    encoder |= file.getName().equals("ws/schild/jave/Encoder.class");
                    exception |= file.getName().equals("ws/schild/jave/EncoderException.class");
                }
            }
            if (!encoder || !exception) throw new AssertionError("Embedded JAVE core is incomplete");
            for (String platform : new String[]{"win64", "linux64", "osx64", "osxm1"}) {
                if (archive.getJarEntry("META-INF/jarjar/jave-nativebin-" + platform + "-3.5.0.jar") == null) throw new AssertionError("Missing native binary " + platform);
            }
        }
        System.out.println("PASS: distributable embeds JAVE core classes and four native runtimes");
    }
}
