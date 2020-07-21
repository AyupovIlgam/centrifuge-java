package io.github.centrifugal.centrifuge.presence;

import com.google.protobuf.ByteString;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class PresenceRequest {

    private String channel;

    public PresenceRequest(String channel) {
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }

    public ByteString toByteString() {
        return Protocol.PresenceRequest.newBuilder()
                .setChannel(getChannel())
                .build()
                .toByteString();
    }
}
