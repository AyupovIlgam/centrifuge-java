package io.github.centrifugal.centrifuge;

public class ClientLogger {

    private final boolean logsEnabled;

    public ClientLogger(boolean logsEnabled) {
        this.logsEnabled = logsEnabled;
    }

    public void d(String message) {
        if (logsEnabled)
            System.out.println("------> " + message + " [" + Thread.currentThread().getName() + "]");
    }
}
