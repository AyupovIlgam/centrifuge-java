package io.github.centrifugal.centrifuge.presence;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.Map;

import io.github.centrifugal.centrifuge.common.ClientInfo;
import io.github.centrifugal.centrifuge.common.Error;
import io.github.centrifugal.centrifuge.protobuf.Protocol;

public interface PresenceCallback {

    void onReplySuccess(PresenceResult presenceResult);

    void onReplyError(Error error);

    void onSendFail(Throwable throwable);

    class PresenceResult {

        private Map<String, ClientInfo> presence;

        private PresenceResult(Protocol.PresenceResult presenceResult) {
            Map<String, ClientInfo> presence = new HashMap<>();
            for (Map.Entry<String, Protocol.ClientInfo> entry : presenceResult.getPresenceMap().entrySet())
                presence.put(entry.getKey(), ClientInfo.fromProto(entry.getValue()));
            this.presence = presence;
        }

        public Map<String, ClientInfo> getPresence() {
            return presence;
        }

        public static PresenceResult fromReply(Protocol.Reply reply) throws InvalidProtocolBufferException {
            return new PresenceResult(Protocol.PresenceResult.parseFrom(reply.getResult()));
        }
    }
}
