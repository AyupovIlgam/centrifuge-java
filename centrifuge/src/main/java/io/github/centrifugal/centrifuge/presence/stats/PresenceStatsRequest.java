package io.github.centrifugal.centrifuge.presence.stats;

import com.google.protobuf.ByteString;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class PresenceStatsRequest {

    private String channel;

    public PresenceStatsRequest(String channel) {
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }

    public ByteString toByteString() {
        return Protocol.PresenceStatsRequest.newBuilder()
                .setChannel(getChannel())
                .build()
                .toByteString();
    }
}
