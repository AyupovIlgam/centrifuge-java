package io.github.centrifugal.centrifuge.common;

import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class ClientInfo {

    private String user;
    private String client;
    private byte[] connInfo;
    private byte[] chanInfo;

    private ClientInfo(Protocol.ClientInfo clientInfo) {
        user = clientInfo.getUser();
        client = clientInfo.getClient();
        connInfo = clientInfo.getConnInfo().toByteArray();
        chanInfo = clientInfo.getChanInfo().toByteArray();
    }

    public String getUser() {
        return user;
    }

    public String getClient() {
        return client;
    }

    public byte[] getConnInfo() {
        return connInfo;
    }

    public byte[] getChanInfo() {
        return chanInfo;
    }

    public static ClientInfo fromProto(Protocol.ClientInfo clientInfo) {
        return new ClientInfo(clientInfo);
    }
}
