package io.github.centrifugal.centrifuge.rpc;

import com.google.protobuf.ByteString;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class RPCRequest {

    private byte[] data;

    public RPCRequest(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public ByteString toByteString() {
        return Protocol.RPCRequest.newBuilder()
                .setData(ByteString.copyFrom(getData()))
                .build()
                .toByteString();
    }
}
