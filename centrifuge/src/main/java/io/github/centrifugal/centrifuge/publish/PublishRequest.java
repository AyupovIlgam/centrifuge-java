package io.github.centrifugal.centrifuge.publish;

import com.google.protobuf.ByteString;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class PublishRequest {

    private String channel;
    private byte[] data;

    public PublishRequest(String channel, byte[] data) {
        this.channel = channel;
        this.data = data;
    }

    public String getChannel() {
        return channel;
    }

    public byte[] getData() {
        return data;
    }

    public ByteString toByteString() {
        return Protocol.PublishRequest.newBuilder()
                .setChannel(getChannel())
                .setData(ByteString.copyFrom(getData()))
                .build()
                .toByteString();
    }
}
