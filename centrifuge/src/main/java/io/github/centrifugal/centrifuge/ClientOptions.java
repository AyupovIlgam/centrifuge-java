package io.github.centrifugal.centrifuge;

import java.util.Map;

import okhttp3.Interceptor;

public class ClientOptions {

    private static final int DEFAULT_TIMEOUT = 5000;
    private static final int DEFAULT_PING_INTERVAL = 25000;

    private int timeout = DEFAULT_TIMEOUT;
    private int pingInterval = DEFAULT_PING_INTERVAL;
    private String privateChannelPrefix = "$";
    private Map<String, String> headers;
    private Interceptor interceptor;
    private boolean logsEnabled = false;

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getPingInterval() {
        return pingInterval;
    }

    public void setPingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
    }

    public String getPrivateChannelPrefix() {
        return privateChannelPrefix;
    }

    public void setPrivateChannelPrefix(String privateChannelPrefix) {
        this.privateChannelPrefix = privateChannelPrefix;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Interceptor getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    public boolean isLogsEnabled() {
        return logsEnabled;
    }

    public void setLogsEnabled(boolean logsEnabled) {
        this.logsEnabled = logsEnabled;
    }
}
