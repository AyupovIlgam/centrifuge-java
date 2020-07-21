package io.github.centrifugal.centrifuge.presence.stats;

import com.google.protobuf.InvalidProtocolBufferException;

import io.github.centrifugal.centrifuge.common.Error;
import io.github.centrifugal.centrifuge.protobuf.Protocol;

public interface PresenceStatsCallback {

    void onReplySuccess(PresenceStatsResult presenceStatsResult);

    void onReplyError(Error error);

    void onSendFail(Throwable throwable);

    class PresenceStatsResult {

        private int numClients;
        private int numUsers;

        private PresenceStatsResult(Protocol.PresenceStatsResult presenceStatsResult) {
            numClients = presenceStatsResult.getNumClients();
            numUsers = presenceStatsResult.getNumUsers();
        }

        public int getNumClients() {
            return numClients;
        }

        public int getNumUsers() {
            return numUsers;
        }

        public static PresenceStatsResult fromReply(Protocol.Reply reply) throws InvalidProtocolBufferException {
            return new PresenceStatsResult(Protocol.PresenceStatsResult.parseFrom(reply.getResult()));
        }
    }
}
