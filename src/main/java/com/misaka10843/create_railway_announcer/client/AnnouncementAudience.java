package com.misaka10843.create_railway_announcer.client;

import com.misaka10843.create_railway_announcer.audio.AudioChannel;
import com.misaka10843.create_railway_announcer.client.runtime.AnnouncementPlaybackRequest;
import net.minecraft.core.BlockPos;
import java.util.UUID;

public record AnnouncementAudience(AudioChannel channel, UUID trainId, BlockPos position, int horizontal, int vertical) {
    public static AnnouncementAudience forRequest(AnnouncementPlaybackRequest request, AudioChannel channel) {
        UUID train = request.trainId().isBlank() ? null : UUID.fromString(request.trainId());
        return new AnnouncementAudience(channel,train,request.position(),request.horizontalRange(),request.verticalRange());
    }
    public boolean accepts(BlockPos listener, UUID ridingTrain) {
        if (channel == AudioChannel.ONBOARD_VOICE) return trainId != null && trainId.equals(ridingTrain);
        if ((channel == AudioChannel.PLATFORM_VOICE || channel == AudioChannel.MELODY) && ridingTrain!=null) return false;
        if (channel == AudioChannel.DOOR_CHIME && trainId != null && trainId.equals(ridingTrain)) return true;
        if (position == null) return true;
        return Math.abs((long)listener.getX()-position.getX()) <= horizontal
                && Math.abs((long)listener.getZ()-position.getZ()) <= horizontal
                && Math.abs((long)listener.getY()-position.getY()) <= vertical;
    }
}
