package io.github.centrifugal.centrifuge;

public enum ClientConnectionState {

    CONNECTING("CONNECTING"),
    CONNECTED("CONNECTED"),
    DISCONNECTING("DISCONNECTING"),
    DISCONNECTED("DISCONNECTED");

    private final String value;

    ClientConnectionState(final String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
