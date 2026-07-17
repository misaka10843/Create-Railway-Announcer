package com.misaka10843.createrailwayannouncer.announcement;

import com.misaka10843.createrailwayannouncer.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerAnnouncementCooldown {
    private static final Map<ServerAnnouncementKey, Long> LAST_DISPATCH_GAME_TIME = new LinkedHashMap<>();

    private ServerAnnouncementCooldown() {
    }

    public static synchronized CooldownResult tryAcquire(
            ServerLevel level,
            ServerAnnouncementRequest request,
            long gameTime
    ) {
        ServerAnnouncementKey key = createKey(level, request);

        int cooldownTicks = Math.max(0, ServerConfig.ANNOUNCEMENT_COOLDOWN_TICKS.get());

        if (cooldownTicks <= 0) {
            LAST_DISPATCH_GAME_TIME.put(key, gameTime);
            return CooldownResult.allowed(key, 0);
        }

        Long lastGameTime = LAST_DISPATCH_GAME_TIME.get(key);
        if (lastGameTime != null) {
            long elapsed = gameTime - lastGameTime;
            long remaining = cooldownTicks - elapsed;

            if (remaining > 0) {
                return CooldownResult.suppressed(key, remaining);
            }
        }

        LAST_DISPATCH_GAME_TIME.put(key, gameTime);
        cleanup(gameTime, cooldownTicks);

        return CooldownResult.allowed(key, 0);
    }

    public static synchronized int clear() {
        int size = LAST_DISPATCH_GAME_TIME.size();
        LAST_DISPATCH_GAME_TIME.clear();
        return size;
    }

    public static synchronized int size() {
        return LAST_DISPATCH_GAME_TIME.size();
    }

    private static ServerAnnouncementKey createKey(
            ServerLevel level,
            ServerAnnouncementRequest request
    ) {
        ResourceLocation levelId = level.dimension().location();

        UUID trainId = request.trainId() == null
                ? new UUID(0L, 0L)
                : request.trainId();

        String stationConfigId = primaryStationConfigId(request);
        if (stationConfigId.isBlank() && request.stationId() != null) {
            stationConfigId = request.stationId().toString();
        }

        if (stationConfigId.isBlank()) {
            stationConfigId = "unknown_station";
        }

        return new ServerAnnouncementKey(
                levelId,
                trainId,
                stationConfigId,
                request.eventType()
        );
    }

    private static String primaryStationConfigId(ServerAnnouncementRequest request) {
        return switch (request.eventType()) {
            case ONBOARD_ARRIVED,
                 PLATFORM_ARRIVAL,
                 DOOR_OPENING -> firstNonBlank(
                    request.currentStationConfigId(),
                    request.nextStationConfigId(),
                    request.destinationStationConfigId()
            );

            case ONBOARD_NEXT_STOP,
                 ONBOARD_APPROACHING,
                 PLATFORM_APPROACH -> firstNonBlank(
                    request.nextStationConfigId(),
                    request.currentStationConfigId(),
                    request.destinationStationConfigId()
            );

            default -> firstNonBlank(
                    request.currentStationConfigId(),
                    request.nextStationConfigId(),
                    request.destinationStationConfigId()
            );
        };
    }

    private static String firstNonBlank(
            String first,
            String second,
            String third
    ) {
        if (notBlank(first)) {
            return first;
        }

        if (notBlank(second)) {
            return second;
        }

        if (notBlank(third)) {
            return third;
        }

        return "";
    }

    private static void cleanup(long gameTime, int cooldownTicks) {
        long maxAge = Math.max(cooldownTicks * 4L, 1200L);

        Iterator<Map.Entry<ServerAnnouncementKey, Long>> iterator =
                LAST_DISPATCH_GAME_TIME.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<ServerAnnouncementKey, Long> entry = iterator.next();
            long age = gameTime - entry.getValue();

            if (age > maxAge) {
                iterator.remove();
            }
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record CooldownResult(
            boolean allowed,
            ServerAnnouncementKey key,
            long remainingTicks
    ) {
        public static CooldownResult allowed(ServerAnnouncementKey key, long remainingTicks) {
            return new CooldownResult(true, key, remainingTicks);
        }

        public static CooldownResult suppressed(ServerAnnouncementKey key, long remainingTicks) {
            return new CooldownResult(false, key, remainingTicks);
        }
    }
}