package io.github.centrifugal.centrifuge.common;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class Publication {

    private byte[] data;

    private Publication(Protocol.Publication publication) {
        this.data = publication.getData().toByteArray();
    }

    public byte[] getData() {
        return data;
    }

    public static Publication fromProto(Protocol.Publication publication) {
        return new Publication(publication);
    }
}
