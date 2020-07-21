package io.github.centrifugal.centrifuge.rpc;

import com.google.protobuf.InvalidProtocolBufferException;

import io.github.centrifugal.centrifuge.common.Error;
import io.github.centrifugal.centrifuge.protobuf.Protocol;

public interface RPCCallback {

    void onReplySuccess(RPCResult rpcResult);

    void onReplyError(Error error);

    void onSendFail(Throwable throwable);

    class RPCResult {

        private byte[] data;

        private RPCResult(Protocol.RPCResult rpcResult) {
            this.data = rpcResult.getData().toByteArray();
        }

        public byte[] getData() {
            return data;
        }

        public static RPCResult fromReply(Protocol.Reply reply) throws InvalidProtocolBufferException {
            return new RPCResult(Protocol.RPCResult.parseFrom(reply.getResult()));
        }
    }
}
