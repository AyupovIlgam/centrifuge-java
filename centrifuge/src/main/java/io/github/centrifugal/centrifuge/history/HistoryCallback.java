package io.github.centrifugal.centrifuge.history;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

import io.github.centrifugal.centrifuge.common.Error;
import io.github.centrifugal.centrifuge.common.Publication;
import io.github.centrifugal.centrifuge.protobuf.Protocol;

public interface HistoryCallback {

    void onReplySuccess(HistoryResult historyResult);

    void onReplyError(Error error);

    void onSendFail(Throwable throwable);

    class HistoryResult {

        private List<Publication> publications;

        private HistoryResult(Protocol.HistoryResult historyResult) {
            List<Publication> publications = new ArrayList<>();
            for (Protocol.Publication item : historyResult.getPublicationsList())
                publications.add(Publication.fromProto(item));
            this.publications = publications;
        }

        public List<Publication> getPublications() {
            return publications;
        }

        public static HistoryResult fromReply(Protocol.Reply reply) throws InvalidProtocolBufferException {
            return new HistoryResult(Protocol.HistoryResult.parseFrom(reply.getResult()));
        }
    }
}
