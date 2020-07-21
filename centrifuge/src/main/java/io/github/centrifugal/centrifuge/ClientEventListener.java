package io.github.centrifugal.centrifuge;

import io.github.centrifugal.centrifuge.protobuf.Protocol;
import okhttp3.Response;

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class ClientEventListener {

    public void onConnect(Client client, ConnectData data) {

    }

    public void onMessage(Client client, MessageData data) {

    }

    public void onRefresh(Client client, RefreshTokenData data, RefreshTokenCallback cb) {

    }

    public void onDisconnect(Client client, DisconnectData data) {

    }

    public void onError(Client client, ErrorData data) {

    }

    public static class ConnectData {

        private String connectionId;
        private String version;
        private boolean expires;
        private int ttl;
        private byte[] data;
        private int subsCount;

        private ConnectData(Protocol.ConnectResult connectResult) {
            connectionId = connectResult.getClient();
            version = connectResult.getVersion();
            expires = connectResult.getExpires();
            ttl = connectResult.getTtl();
            data = connectResult.getData().toByteArray();
            subsCount = connectResult.getSubsCount();
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getVersion() {
            return version;
        }

        public boolean isExpires() {
            return expires;
        }

        public int getTtl() {
            return ttl;
        }

        public byte[] getData() {
            return data;
        }

        public int getSubsCount() {
            return subsCount;
        }

        public static ConnectData fromProto(Protocol.ConnectResult connectResult) {
            return new ConnectData(connectResult);
        }
    }

    public static class MessageData {

        private String connectionId;
        private String channel;
        private byte[] data;

        public String getConnectionId() {
            return connectionId;
        }

        public void setConnectionId(String connectionId) {
            this.connectionId = connectionId;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }
    }

    public static class RefreshTokenData {

        private String connectionId;

        public RefreshTokenData(String connectionId) {
            this.connectionId = connectionId;
        }

        public String getConnectionId() {
            return connectionId;
        }
    }

    public interface RefreshTokenCallback {

        void onSuccess(String token);

        void onFail(Throwable e);
    }

    public static class DisconnectData {

        private String connectionId;
        private String reason;
        private boolean isReconnectScheduled;

        public DisconnectData(String connectionId, String reason, boolean isReconnectScheduled) {
            this.connectionId = connectionId;
            this.reason = reason;
            this.isReconnectScheduled = isReconnectScheduled;
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getReason() {
            return reason;
        }

        public boolean isReconnectScheduled() {
            return isReconnectScheduled;
        }

        public boolean isCleanDisconnect() {
            return reason.equals("clean disconnect");
        }
    }

    public static class ErrorData {

        private Throwable exception;
        private Response response;

        public ErrorData(Throwable exception, Response response) {
            this.exception = exception;
            this.response = response;
        }

        public Throwable getException() {
            return exception;
        }

        public Response getResponse() {
            return response;
        }
    }
}
