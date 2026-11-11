package com.misaka10843.create_railway_announcer.tts;

import com.google.gson.JsonObject;
import com.misaka10843.create_railway_announcer.config.ClientConfig;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.*;

public final class HttpTtsProvider implements TtsProvider {
    public record Settings(String endpoint, String token, String pitch, int timeoutSeconds, boolean enabled) {}
    private final Settings fixedSettings;
    public HttpTtsProvider() { this.fixedSettings = null; }
    public HttpTtsProvider(Settings settings) { this.fixedSettings = settings; }
    private Settings settings() {
        return fixedSettings != null ? fixedSettings : new Settings(ClientConfig.TTS_ENDPOINT.get(),
                ClientConfig.TTS_TOKEN.get(), ClientConfig.TTS_PITCH.get(), ClientConfig.TTS_TIMEOUT.get(),
                ClientConfig.TTS_ENABLED.get() && !ClientConfig.TTS_BACKEND.get().equalsIgnoreCase("off"));
    }
    private static final int MAX_AUDIO_BYTES = 16 * 1024 * 1024;
    private static final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    private static final ThreadPoolExecutor worker = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
        Thread thread = new Thread(runnable, "Railway Signage TTS");
        thread.setDaemon(true);
        return thread;
    });
    static { worker.allowCoreThreadTimeOut(true); }

    @Override public String id() { return "http_ssml"; }
    @Override public TtsProvider snapshot() { return new HttpTtsProvider(settings()); }
    public HttpTtsProvider withPitch(String pitch) {
        var current = settings();
        return new HttpTtsProvider(new Settings(current.endpoint(), current.token(), pitch, current.timeoutSeconds(), current.enabled()));
    }
    @Override public boolean isAvailable() {
        return settings().enabled();
    }

    public String cacheIdentity() {
        var settings = settings();
        return id() + "\n" + settings.endpoint() + "\n" + settings.pitch();
    }

    @Override public CompletableFuture<TtsResult> synthesize(TtsRequest request) {
        if (!isAvailable()) return CompletableFuture.completedFuture(TtsResult.failure("TTS disabled"));
        var settings = settings();
        try {
            return CompletableFuture.supplyAsync(() -> {
            try {
                URI endpoint = URI.create(settings.endpoint());
                if (!("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()))) {
                    return TtsResult.failure("TTS endpoint must use HTTP or HTTPS");
                }
                String ssml = Ssml.document(request, settings.pitch());
                if (ssml.getBytes(StandardCharsets.UTF_8).length > 8192) return TtsResult.failure("SSML exceeds 8 KiB");
                JsonObject body = new JsonObject();
                body.addProperty("format", "ssml");
                body.addProperty("ssml", ssml);
                var builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
                applyServiceHeaders(endpoint, builder);
                String token = settings.token();
                if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
                var pending = client.sendAsync(builder.build(), info -> new LimitedAudioSubscriber());
                HttpResponse<byte[]> response;
                try { response = pending.get(settings.timeoutSeconds(), TimeUnit.SECONDS); }
                catch (Exception error) { pending.cancel(true); throw error; }
                {
                    if (response.statusCode() != 200) return TtsResult.failure("TTS HTTP " + response.statusCode());
                    byte[] bytes = response.body();
                    if (bytes.length > MAX_AUDIO_BYTES) return TtsResult.failure("TTS response exceeds 16 MiB");
                    boolean mp3 = bytes.length > 3 && ((bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3')
                            || ((bytes[0] & 255) == 255 && (bytes[1] & 224) == 224));
                    boolean ogg = bytes.length > 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
                    boolean wav = bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                            && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
                    if (!mp3 && !ogg && !wav) return TtsResult.failure("TTS returned unsupported audio");
                    Files.createDirectories(request.output().getParent());
                    Files.write(request.output(), bytes);
                    return TtsResult.success(request.output(), id(), request.voiceContains());
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return TtsResult.failure("TTS interrupted");
            } catch (Exception error) {
                Throwable cause = error;
                while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) cause = cause.getCause();
                String detail = cause.getMessage() == null ? "" : cause.getMessage();
                if (!settings.token().isBlank()) detail = detail.replace(settings.token(), "[redacted]");
                if (detail.length() > 240) detail = detail.substring(0, 240);
                if (!(cause instanceof IllegalArgumentException)) com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer.LOGGER.warn(
                        "TTS request failed: {}: {}", cause.getClass().getSimpleName(), detail);
                com.misaka10843.create_railway_announcer.CreateRailwayAnnouncer.LOGGER.debug("TTS request failure details",cause);
                return TtsResult.failure("TTS " + cause.getClass().getSimpleName() + (detail.isBlank() ? "" : ": " + detail));
            }
            }, worker);
        } catch (RejectedExecutionException error) {
            return CompletableFuture.completedFuture(TtsResult.failure("TTS queue is full; try again after current requests finish"));
        }
    }

    public static void applyServiceHeaders(URI endpoint, HttpRequest.Builder builder) {
        if (!"tts.wangwangit.com".equalsIgnoreCase(endpoint.getHost())) return;
        builder.header("Referer", "https://tts.wangwangit.com/")
                .header("Origin", "https://tts.wangwangit.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0");
    }

    private static final class LimitedAudioSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (bytes.size() + buffer.remaining() > MAX_AUDIO_BYTES) {
                    subscription.cancel();
                    result.completeExceptionally(new java.io.IOException("TTS audio exceeds 16 MiB"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
