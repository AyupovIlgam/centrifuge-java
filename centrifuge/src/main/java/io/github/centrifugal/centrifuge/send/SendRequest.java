package io.github.centrifugal.centrifuge.send;

import com.google.protobuf.ByteString;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class SendRequest {

    private byte[] data;

    public SendRequest(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public ByteString toByteString() {
        return Protocol.SendRequest.newBuilder()
                .setData(ByteString.copyFrom(getData()))
                .build()
                .toByteString();
    }
}
