package io.github.centrifugal.centrifuge.send;

public interface SendCallback {

    void onSendSuccess();

    void onSendFail(Throwable e);
}
