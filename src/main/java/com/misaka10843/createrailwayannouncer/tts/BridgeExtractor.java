package com.misaka10843.createrailwayannouncer.tts;

import com.misaka10843.createrailwayannouncer.CreateRailwayAnnouncer;
import com.misaka10843.createrailwayannouncer.config.ClientConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class BridgeExtractor {
    private static final String BUNDLED_WINDOWS_X64_BRIDGE =
            "/assets/" + CreateRailwayAnnouncer.MODID + "/bridge/windows/x64/WinTtsBridge.exe";

    private BridgeExtractor() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    public static Path resolveBridgeExecutable() throws IOException {
        String configuredPath = ClientConfig.TTS_BRIDGE_PATH.get().trim();
        if (!configuredPath.isEmpty()) {
            Path path = Path.of(configuredPath);
            if (!Files.isRegularFile(path)) {
                throw new IOException("Configured WinTtsBridge.exe does not exist: " + path);
            }
            return path;
        }

        if (!isWindows()) {
            throw new IOException("WinTtsBridge is only supported on Windows.");
        }

        Path target = getExtractedBridgePath();

        if (Files.isRegularFile(target)) {
            return target;
        }

        extractBundledBridge(target);
        return target;
    }

    public static Path getExtractedBridgePath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(CreateRailwayAnnouncer.MODID)
                .resolve("bridge")
                .resolve("win-x64")
                .resolve("WinTtsBridge.exe");
    }

    private static void extractBundledBridge(Path target) throws IOException {
        Files.createDirectories(target.getParent());

        try (InputStream inputStream = BridgeExtractor.class.getResourceAsStream(BUNDLED_WINDOWS_X64_BRIDGE)) {
            if (inputStream == null) {
                throw new IOException("Bundled WinTtsBridge.exe was not found in mod resources: " + BUNDLED_WINDOWS_X64_BRIDGE);
            }

            Files.copy(inputStream, target);
            CreateRailwayAnnouncer.LOGGER.info("Extracted WinTtsBridge.exe to {}", target);
        }
    }
}