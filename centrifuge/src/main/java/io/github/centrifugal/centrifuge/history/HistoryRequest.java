package io.github.centrifugal.centrifuge.history;

import com.google.protobuf.ByteString;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class HistoryRequest {

    private String channel;

    public HistoryRequest(String channel) {
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }

    public ByteString toByteString() {
        return Protocol.HistoryRequest.newBuilder()
                .setChannel(getChannel())
                .build()
                .toByteString();
    }
}
