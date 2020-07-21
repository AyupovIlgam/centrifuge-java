package io.github.centrifugal.centrifuge.common;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class Error {

    private int code;
    private String message;

    private Error(Protocol.Error error) {
        this.code = error.getCode();
        this.message = error.getMessage();
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static Error fromReply(Protocol.Reply reply) {
        return new Error(reply.getError());
    }
}
