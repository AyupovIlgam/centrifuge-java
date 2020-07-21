package io.github.centrifugal.centrifuge.subscriptions;

import java.util.ArrayList;

import io.github.centrifugal.centrifuge.Client;
import io.github.centrifugal.centrifuge.common.ClientInfo;
import io.github.centrifugal.centrifuge.common.Publication;
import io.github.centrifugal.centrifuge.protobuf.Protocol;

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class SubscriptionEventListener {

    public void onPrivateSub(Client client, PrivateSubEvent event, PrivateSubTokenCallback cb) {

    }

    public void onSubscribeSuccess(Subscription subscription, SubscribeSuccessEvent event) {

    }

    public void onSubscribeError(Subscription subscription, SubscribeErrorEvent event) {

    }

    public void onPublication(Subscription subscription, PublicationEvent event) {

    }

    public void onJoin(Subscription subscription, JoinEvent event) {

    }

    public void onLeave(Subscription subscription, LeaveEvent event) {

    }

    public void onUnsubscribe(Subscription subscription, UnsubscribeEvent event) {

    }

    public static class PrivateSubEvent {

        private String connectionId;
        private String channel;

        public PrivateSubEvent(String connectionId, String channel) {
            this.connectionId = connectionId;
            this.channel = channel;
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getChannel() {
            return channel;
        }
    }

    public interface PrivateSubTokenCallback {

        void onSuccess(String token);

        void onFail(Throwable e);
    }

    public static class SubscribeSuccessEvent {

        private String connectionId;
        private String channel;
        private boolean expires;
        private int ttl;
        private boolean recoverable;
        private int seq;
        private int gen;
        private String epoch;
        private ArrayList<Publication> publications;
        private boolean recovered;

        private SubscribeSuccessEvent(String connectionId, String channel, Protocol.SubscribeResult subscribeResult) {
            this.connectionId = connectionId;
            this.channel = channel;
            this.expires = subscribeResult.getExpires();
            this.ttl = subscribeResult.getTtl();
            this.recoverable = subscribeResult.getRecoverable();
            this.seq = subscribeResult.getSeq();
            this.gen = subscribeResult.getGen();
            this.epoch = subscribeResult.getEpoch();
            this.publications = new ArrayList<>();
            for (Protocol.Publication publication : subscribeResult.getPublicationsList())
                publications.add(Publication.fromProto(publication));
            this.recovered = subscribeResult.getRecovered();
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getChannel() {
            return channel;
        }

        public boolean isExpires() {
            return expires;
        }

        public int getTtl() {
            return ttl;
        }

        public boolean isRecoverable() {
            return recoverable;
        }

        public int getSeq() {
            return seq;
        }

        public int getGen() {
            return gen;
        }

        public String getEpoch() {
            return epoch;
        }

        public ArrayList<Publication> getPublications() {
            return publications;
        }

        public boolean isRecovered() {
            return recovered;
        }

        public static SubscribeSuccessEvent fromProto(String connectionId, String channel, Protocol.SubscribeResult subscribeResult) {
            return new SubscribeSuccessEvent(connectionId, channel, subscribeResult);
        }
    }

    public static class SubscribeErrorEvent {

        private String connectionId;
        private String channel;
        private int code;
        private String message;

        public SubscribeErrorEvent(String connectionId, String channel, int code, String message) {
            this.connectionId = connectionId;
            this.channel = channel;
            this.code = code;
            this.message = message;
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getChannel() {
            return channel;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class PublicationEvent {

        private String connectionId;
        private String channel;
        private int seq;
        private int gen;
        private String uid;
        private byte[] data;
        private ClientInfo clientInfo;

        private PublicationEvent(String connectionId, String channel, Protocol.Publication publication) {
            this.connectionId = connectionId;
            this.channel = channel;
            this.seq = publication.getSeq();
            this.gen = publication.getGen();
            this.uid = publication.getUid();
            this.data = publication.getData().toByteArray();
            this.clientInfo = ClientInfo.fromProto(publication.getInfo());
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getChannel() {
            return channel;
        }

        public int getSeq() {
            return seq;
        }

        public int getGen() {
            return gen;
        }

        public String getUid() {
            return uid;
        }

        public byte[] getData() {
            return data;
        }

        public ClientInfo getClientInfo() {
            return clientInfo;
        }

        public static PublicationEvent fromProto(String connectionId, String channel, Protocol.Publication publication) {
            return new PublicationEvent(connectionId, channel, publication);
        }
    }

    public static class JoinEvent {

        private String connectionId;
        private String channel;
        private ClientInfo clientInfo;

        private JoinEvent(String connectionId, String channel, Protocol.Join join) {
            this.connectionId = connectionId;
            this.channel = channel;
            this.clientInfo = ClientInfo.fromProto(join.getInfo());
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getChannel() {
            return channel;
        }

        public ClientInfo getClientInfo() {
            return clientInfo;
        }

        public static JoinEvent fromProto(String connectionId, String channel, Protocol.Join join) {
            return new JoinEvent(connectionId, channel, join);
        }
    }

    public static class LeaveEvent {

        private String connectionId;
        private String channel;
        private ClientInfo clientInfo;

        private LeaveEvent(String connectionId, String channel, Protocol.Leave leave) {
            this.connectionId = connectionId;
            this.channel = channel;
            this.clientInfo = ClientInfo.fromProto(leave.getInfo());
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getChannel() {
            return channel;
        }

        public ClientInfo getClientInfo() {
            return clientInfo;
        }

        public static LeaveEvent fromProto(String connectionId, String channel, Protocol.Leave leave) {
            return new LeaveEvent(connectionId, channel, leave);
        }
    }

    public static class UnsubscribeEvent {

        private String connectionId;
        private String channel;
        private boolean resubscribe;

        public UnsubscribeEvent(String connectionId, String channel, boolean resubscribe) {
            this.connectionId = connectionId;
            this.channel = channel;
            this.resubscribe = resubscribe;
        }

        private UnsubscribeEvent(String connectionId, String channel, Protocol.Unsub unsub) {
            this(connectionId, channel, unsub.getResubscribe());
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getChannel() {
            return channel;
        }

        public boolean isResubscribe() {
            return resubscribe;
        }

        public static UnsubscribeEvent fromProto(String connectionId, String channel, Protocol.Unsub unsub) {
            return new UnsubscribeEvent(connectionId, channel, unsub.getResubscribe());
        }
    }
}
