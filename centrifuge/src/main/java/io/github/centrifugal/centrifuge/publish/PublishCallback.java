package io.github.centrifugal.centrifuge.publish;

import com.google.protobuf.InvalidProtocolBufferException;

import io.github.centrifugal.centrifuge.common.Error;
import io.github.centrifugal.centrifuge.protobuf.Protocol;

public interface PublishCallback {

    void onReplySuccess(PublishResult publishResult);

    void onReplyError(Error error);

    void onSendFail(Throwable throwable);

    class PublishResult {

        private PublishResult(Protocol.PublishResult publishResult) {
        }

        public static PublishResult fromReply(Protocol.Reply reply) throws InvalidProtocolBufferException {
            return new PublishResult(Protocol.PublishResult.parseFrom(reply.getResult()));
        }
    }
}
