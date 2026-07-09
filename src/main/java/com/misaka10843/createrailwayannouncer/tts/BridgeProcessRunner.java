package com.misaka10843.createrailwayannouncer.tts;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class BridgeProcessRunner {
    private BridgeProcessRunner() {
    }

    public static BridgeProcessResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);

        Process process = builder.start();

        CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            return new BridgeProcessResult(-1, stdout, stderr, true);
        }

        int exitCode = process.exitValue();
        String stdout = stdoutFuture.join();
        String stderr = stderrFuture.join();

        if (!stderr.isBlank()) {
            CreateRailwayAnnouncer.LOGGER.debug("WinTtsBridge stderr: {}", stderr);
        }

        return new BridgeProcessResult(exitCode, stdout, stderr, false);
    }

    private static String readStream(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}