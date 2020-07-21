package io.github.centrifugal.centrifuge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.centrifugal.centrifuge.backoff.Backoff;
import io.github.centrifugal.centrifuge.common.Error;
import io.github.centrifugal.centrifuge.history.HistoryCallback;
import io.github.centrifugal.centrifuge.history.HistoryRequest;
import io.github.centrifugal.centrifuge.presence.PresenceCallback;
import io.github.centrifugal.centrifuge.presence.PresenceRequest;
import io.github.centrifugal.centrifuge.presence.stats.PresenceStatsCallback;
import io.github.centrifugal.centrifuge.presence.stats.PresenceStatsRequest;
import io.github.centrifugal.centrifuge.protobuf.Protocol;
import io.github.centrifugal.centrifuge.publish.PublishCallback;
import io.github.centrifugal.centrifuge.publish.PublishRequest;
import io.github.centrifugal.centrifuge.rpc.RPCCallback;
import io.github.centrifugal.centrifuge.rpc.RPCRequest;
import io.github.centrifugal.centrifuge.send.SendCallback;
import io.github.centrifugal.centrifuge.send.SendRequest;
import io.github.centrifugal.centrifuge.subscriptions.Subscription;
import io.github.centrifugal.centrifuge.subscriptions.SubscriptionEventListener;
import java8.util.concurrent.CompletableFuture;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class Client {

    // https://tools.ietf.org/html/rfc6455
    // normal closure
    private static final int RFC_STATUS_1000 = 1000;

    private WebSocket webSocket;
    private ClientOptions clientOptions;
    private ClientEventListener clientEventListener;
    private ClientConnectionState clientConnectionState;
    private ClientLogger clientLogger;
    private Backoff backoff;
    private String url;
    private String token;
    private String connectionId;
    private String disconnectReasonJson;
    private int incrementalCommandId;
    private boolean needScheduleReconnect;

    private final ExecutorService mainExecutorService = Executors.newSingleThreadExecutor();
    private final ExecutorService reconnectExecutorService = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> pingScheduledFuture;
    private ScheduledFuture<?> refreshScheduledFuture;

    private final Map<Integer, CompletableFuture<Protocol.Reply>> futures = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public Client(final ClientOptions clientOptions, final ClientEventListener clientEventListener, final String url) {
        this.clientOptions = clientOptions;
        this.clientEventListener = clientEventListener;
        this.clientConnectionState = ClientConnectionState.DISCONNECTED;
        this.backoff = new Backoff();
        this.clientLogger = new ClientLogger(clientOptions.isLogsEnabled());
        this.url = url;
        this.disconnectReasonJson = "";
        this.incrementalCommandId = 0;
        this.needScheduleReconnect = true;
    }

    public ClientOptions getClientOptions() {
        return clientOptions;
    }

    public boolean isConnected() {
        return clientConnectionState == ClientConnectionState.CONNECTED;
    }

    public boolean isConnecting() {
        return clientConnectionState == ClientConnectionState.CONNECTING;
    }

    public boolean isDisconnecting() {
        return clientConnectionState == ClientConnectionState.DISCONNECTING;
    }

    public boolean isDisconnected() {
        return clientConnectionState == ClientConnectionState.DISCONNECTED;
    }

    public String getConnectionId() {
        return connectionId;
    }

    //region SOCKET
    public void connect(String token) {
        mainExecutorService.submit(() -> {
            if (!isConnected() && !isConnecting()) {
                this.token = token;
                openSocket();
            }
        });
    }

    private void openSocket() {
        if (webSocket != null) {
            webSocket.cancel();
        }

        clientConnectionState = ClientConnectionState.CONNECTING;

        Headers.Builder headersBuilder = new Headers.Builder();
        if (clientOptions.getHeaders() != null) {
            for (Map.Entry<String, String> entry : clientOptions.getHeaders().entrySet()) {
                headersBuilder.add(entry.getKey(), entry.getValue());
            }
        }

        Request request = new Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .build();

        webSocket = buildNetworkClient()
                .newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        super.onOpen(webSocket, response);
                        clientLogger.d("onOpen");
                        mainExecutorService.submit(Client.this::onOpen);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        super.onMessage(webSocket, bytes);
                        clientLogger.d("onMessage " + bytes.toString());
                        mainExecutorService.submit(() -> Client.this.onMessage(bytes.toByteArray()));
                    }

                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        super.onClosing(webSocket, code, reason);
                        clientLogger.d("onClosing " + code + " " + reason);
                        mainExecutorService.submit(() -> Client.this.onClosing(webSocket, code, reason));
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        super.onClosed(webSocket, code, reason);
                        clientLogger.d("onClosed " + code + " " + reason);
                        mainExecutorService.submit(() -> Client.this.onClosed(code, reason));
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                        super.onFailure(webSocket, throwable, response);
                        clientLogger.d("onFailure " + throwable.toString());
                        mainExecutorService.submit(() -> Client.this.onFailure(throwable, response));
                    }
                });
    }

    private OkHttpClient buildNetworkClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.callTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS)
                .connectTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS);

        if (clientOptions.getInterceptor() != null)
            builder.addInterceptor(clientOptions.getInterceptor());

        return builder.build();
    }

    @SuppressWarnings("ConstantConditions")
    public void disconnect() {
        mainExecutorService.submit(() -> {
            String reason = "clean disconnect";
            boolean needScheduleReconnect = false;
            closeSocket(buildDisconnectReasonJson(reason, needScheduleReconnect), needScheduleReconnect);
        });
    }

    private void closeSocket(String disconnectReasonJson, boolean needScheduleReconnect) {
        clientConnectionState = ClientConnectionState.DISCONNECTING;
        this.disconnectReasonJson = disconnectReasonJson;
        this.needScheduleReconnect = needScheduleReconnect;
        webSocket.close(RFC_STATUS_1000, this.disconnectReasonJson);
    }

    private String buildDisconnectReasonJson(String reason, boolean needScheduleReconnect) {
        return String.format("{\"reason\": \"%s\", \"reconnect\": %b}", reason, needScheduleReconnect);
    }
    //endregion

    //region COMMAND / REPLY
    private Protocol.Command buildCommand(Protocol.MethodType methodType, com.google.protobuf.ByteString bytes) {
        return Protocol.Command.newBuilder()
                .setId(++incrementalCommandId)
                .setMethod(methodType)
                .setParams(bytes)
                .build();
    }

    private ByteString serializeCommand(Protocol.Command command) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            command.writeDelimitedTo(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ByteString.of(stream.toByteArray());
    }

    private void sendCommand(Protocol.Command command, CompletableFuture<Protocol.Reply> future) {
        futures.put(command.getId(), future);
        boolean sent = webSocket.send(serializeCommand(command));
        if (!sent)
            future.completeExceptionally(new IOException());
    }

    private void sendCommandAndComplete(Protocol.Command command, CompletableFuture<Protocol.Reply> future) {
        futures.put(command.getId(), future);
        boolean sent = webSocket.send(serializeCommand(command));
        if (!sent) {
            future.completeExceptionally(new IOException());
        } else {
            future.complete(null);
        }
    }

    private void clearCommand(Protocol.Command command) {
        futures.remove(command.getId());
    }

    private boolean hasReplyError(Protocol.Reply reply) {
        return reply.getError() != null && reply.getError().getCode() != 0;
    }
    //endregion

    //region ON OPEN
    private void onOpen() {
        sendConnectCommand();
    }
    //endregion

    //region CONNECT
    private void sendConnectCommand() {
        Protocol.ConnectRequest connectRequest = Protocol.ConnectRequest.newBuilder().setToken(token).build();
        Protocol.Command command = buildCommand(Protocol.MethodType.CONNECT, connectRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                handleConnectFailure(reply.getError().getMessage(), false);
            } else {
                try {
                    handleConnectSuccess(Protocol.ConnectResult.parseFrom(reply.getResult()));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                handleConnectFailure("connect error", true);
                e.printStackTrace();
            });
            return null;
        });
        sendCommand(command, future);
    }

    private void handleConnectSuccess(Protocol.ConnectResult connectResult) {
        connectionId = connectResult.getClient();
        clientConnectionState = ClientConnectionState.CONNECTED;
        disconnectReasonJson = "";
        needScheduleReconnect = true;
        backoff.reset();

        clientEventListener.onConnect(this, ClientEventListener.ConnectData.fromProto(connectResult));

        synchronized (subscriptions) {
            for (Map.Entry<String, Subscription> entry : subscriptions.entrySet()) {
                Subscription subscription = entry.getValue();
                if (subscription.needResubscribe()) {
                    if (subscription.isPrivate()) {
                        notifyPrivateSub(subscription.getChannel());
                    } else {
                        sendSubscribeCommand(subscription.getChannel(), "");
                    }
                } else {
                    subscriptions.remove(entry.getKey());
                }
            }
        }

        schedulePingAtFixedRate();
        if (connectResult.getExpires())
            scheduleRefresh(connectResult.getTtl());
    }

    @SuppressWarnings("SameParameterValue")
    private void handleConnectFailure(String reason, boolean needScheduleReconnect) {
        closeSocket(buildDisconnectReasonJson(reason, needScheduleReconnect), needScheduleReconnect);
    }
    //endregion

    //region ON MESSAGE
    private void onMessage(byte[] bytes) {
        processBytes(bytes);
    }

    private void processBytes(byte[] bytes) {
        InputStream inputStream = new ByteArrayInputStream(bytes);
        try {
            while (inputStream.available() > 0) {
                processReply(Protocol.Reply.parseDelimitedFrom(inputStream));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processReply(Protocol.Reply reply) {
        if (reply.getId() > 0) {
            CompletableFuture<Protocol.Reply> future = futures.get(reply.getId());
            if (future != null)
                future.complete(reply);
        } else {
            processAsyncReply(reply);
        }
    }

    private void processAsyncReply(Protocol.Reply reply) {
        try {
            Protocol.Push push = Protocol.Push.parseFrom(reply.getResult());
            switch (push.getType()) {
                case PUBLICATION: {
                    Subscription subscription = getSubscriptionOrNull(push.getChannel());
                    if (subscription != null) {
                        Protocol.Publication publication = Protocol.Publication.parseFrom(push.getData());
                        SubscriptionEventListener.PublicationEvent publicationEvent =
                                SubscriptionEventListener.PublicationEvent.fromProto(connectionId, push.getChannel(), publication);
                        subscription.getSubscriptionEventListener().onPublication(subscription, publicationEvent);
                    }
                    break;
                }
                case JOIN: {
                    Subscription subscription = getSubscriptionOrNull(push.getChannel());
                    if (subscription != null) {
                        Protocol.Join join = Protocol.Join.parseFrom(push.getData());
                        SubscriptionEventListener.JoinEvent joinEvent =
                                SubscriptionEventListener.JoinEvent.fromProto(connectionId, push.getChannel(), join);
                        subscription.getSubscriptionEventListener().onJoin(subscription, joinEvent);
                    }
                    break;
                }
                case LEAVE: {
                    Subscription subscription = getSubscriptionOrNull(push.getChannel());
                    if (subscription != null) {
                        Protocol.Leave leave = Protocol.Leave.parseFrom(push.getData());
                        SubscriptionEventListener.LeaveEvent leaveEvent =
                                SubscriptionEventListener.LeaveEvent.fromProto(connectionId, push.getChannel(), leave);
                        subscription.getSubscriptionEventListener().onLeave(subscription, leaveEvent);
                    }
                    break;
                }
                case UNSUB: {
                    Subscription subscription = getSubscriptionOrNull(push.getChannel());
                    if (subscription != null) {
                        Protocol.Unsub unsubscribe = Protocol.Unsub.parseFrom(push.getData());
                        SubscriptionEventListener.UnsubscribeEvent unsubscribeEvent =
                                SubscriptionEventListener.UnsubscribeEvent.fromProto(connectionId, push.getChannel(), unsubscribe);
                        subscription.getSubscriptionEventListener().onUnsubscribe(subscription, unsubscribeEvent);
                    }
                    break;
                }
                case MESSAGE:
                    Protocol.Message message = Protocol.Message.parseFrom(push.getData());
                    ClientEventListener.MessageData messageData = new ClientEventListener.MessageData();
                    messageData.setConnectionId(connectionId);
                    messageData.setChannel(push.getChannel());
                    messageData.setData(message.getData().toByteArray());
                    clientEventListener.onMessage(this, messageData);
                    break;
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
    //endregion

    //region ON CLOSING
    private void onClosing(WebSocket webSocket, int code, String reason) {
        clientConnectionState = ClientConnectionState.DISCONNECTING;
        webSocket.close(code, reason);
    }
    //endregion

    //region ON CLOSED
    @SuppressWarnings("unused")
    private void onClosed(int code, String disconnectReasonJson) {
        if (disconnectReasonJson != null && !disconnectReasonJson.isEmpty()) {
            try {
                JsonObject jsonObject = new JsonParser().parse(disconnectReasonJson).getAsJsonObject();
                String disconnectReason = jsonObject.get("reason").getAsString();
                boolean needScheduleReconnect = jsonObject.get("reconnect").getAsBoolean();
                onClosed(disconnectReason, needScheduleReconnect);
            } catch (JsonParseException e) {
                onClosed("connection closed", true);
            }
            return;
        }

        if (this.disconnectReasonJson != null && !this.disconnectReasonJson.isEmpty()) {
            JsonObject jsonObject = new JsonParser().parse(this.disconnectReasonJson).getAsJsonObject();
            String disconnectReason = jsonObject.get("reason").getAsString();
            boolean needScheduleReconnect = jsonObject.get("reconnect").getAsBoolean();
            this.disconnectReasonJson = "";
            onClosed(disconnectReason, needScheduleReconnect);
            return;
        }

        onClosed("connection closed", true);
    }

    private void onClosed(String disconnectReason, boolean needScheduleReconnect) {
        ClientConnectionState previousConnectionState = clientConnectionState;
        clientConnectionState = ClientConnectionState.DISCONNECTED;

        cancelPing();

        cancelRefresh();

        synchronized (subscriptions) {
            for (Map.Entry<String, Subscription> entry : subscriptions.entrySet())
                entry.getValue().onUnsubscribed();
        }

        if (previousConnectionState != ClientConnectionState.DISCONNECTED) {
            for (Map.Entry<Integer, CompletableFuture<Protocol.Reply>> entry : futures.entrySet()) {
                CompletableFuture<Protocol.Reply> future = entry.getValue();
                future.completeExceptionally(new IOException());
            }
            ClientEventListener.DisconnectData disconnectData =
                    new ClientEventListener.DisconnectData(connectionId, disconnectReason, needScheduleReconnect);
            clientEventListener.onDisconnect(this, disconnectData);
        }

        this.needScheduleReconnect = needScheduleReconnect;
        if (this.needScheduleReconnect) {
            scheduleReconnect();
        }
    }
    //endregion

    //region ON FAILURE
    private void onFailure(Throwable throwable, Response response) {
        clientEventListener.onError(this, new ClientEventListener.ErrorData(throwable, response));
        onClosed(throwable.getMessage(), true);
    }
    //endregion

    //region SUBSCRIPTIONS
    private Subscription getSubscriptionOrNull(String channel) {
        Subscription subscription;
        synchronized (subscriptions) {
            subscription = subscriptions.get(channel);
        }
        return subscription;
    }

    public void subscribe(String channel, SubscriptionEventListener listener) {
        mainExecutorService.submit(() -> {
            Subscription subscription = new Subscription(this, channel, listener);
            synchronized (subscriptions) {
                subscriptions.remove(channel);
                subscriptions.put(channel, subscription);
                if (subscription.isPrivate()) {
                    notifyPrivateSub(subscription.getChannel());
                } else {
                    sendSubscribeCommand(subscription.getChannel(), "");
                }
            }
        });
    }

    private void notifyPrivateSub(String channel) {
        Subscription subscription = getSubscriptionOrNull(channel);
        if (subscription != null) {
            SubscriptionEventListener.PrivateSubEvent privateSubEvent =
                    new SubscriptionEventListener.PrivateSubEvent(connectionId, channel);
            subscription.getSubscriptionEventListener().onPrivateSub(this, privateSubEvent,
                    new SubscriptionEventListener.PrivateSubTokenCallback() {
                        @Override
                        public void onFail(Throwable e) {
                            mainExecutorService.submit(() -> {
                                if (connectionId.equals(privateSubEvent.getConnectionId()))
                                    handleSubscribeFailure(channel, 0, "private subscribe error");
                            });
                        }

                        @Override
                        public void onSuccess(String token) {
                            mainExecutorService.submit(() -> {
                                if (clientConnectionState == ClientConnectionState.CONNECTED)
                                    sendSubscribeCommand(channel, token);
                            });
                        }
                    });
        }
    }

    private void sendSubscribeCommand(String channel, String token) {
        Protocol.SubscribeRequest subscribeRequest = Protocol.SubscribeRequest.newBuilder()
                .setChannel(channel)
                .setToken(token)
                .build();
        Protocol.Command command = buildCommand(Protocol.MethodType.SUBSCRIBE, subscribeRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                handleSubscribeFailure(channel, reply.getError().getCode(), reply.getError().getMessage());
            } else {
                try {
                    handleSubscribeSuccess(channel, Protocol.SubscribeResult.parseFrom(reply.getResult()));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                handleSubscribeFailure(channel, 0, "subscribe error");
                e.printStackTrace();
            });
            return null;
        });
        sendCommand(command, future);
    }

    private void handleSubscribeSuccess(String channel, Protocol.SubscribeResult subscribeResult) {
        Subscription subscription = getSubscriptionOrNull(channel);
        if (subscription != null)
            subscription.onSubscribeSuccess(subscribeResult);
    }

    private void handleSubscribeFailure(String channel, int code, String message) {
        Subscription subscription = getSubscriptionOrNull(channel);
        if (subscription != null) {
            synchronized (subscriptions) {
                subscription.onSubscribeError(code, message);
                subscriptions.remove(channel);
            }
        }
    }

    public void unsubscribe(String channel) {
        mainExecutorService.submit(() -> {
            Subscription subscription = getSubscriptionOrNull(channel);
            if (subscription != null) {
                synchronized (subscriptions) {
                    subscription.onUnsubscribed();
                    subscriptions.remove(subscription.getChannel());
                    if (clientConnectionState == ClientConnectionState.CONNECTED)
                        sendUnsubscribeCommand(subscription);
                }
            }
        });
    }

    private void sendUnsubscribeCommand(Subscription subscription) {
        String channel = subscription.getChannel();
        Protocol.UnsubscribeRequest unsubscribeRequest = Protocol.UnsubscribeRequest.newBuilder()
                .setChannel(channel)
                .build();
        Protocol.Command command = buildCommand(Protocol.MethodType.UNSUBSCRIBE, unsubscribeRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                e.printStackTrace();
            });
            return null;
        });
        sendCommand(command, future);
    }
    //endregion

    //region HISTORY
    public void history(HistoryRequest historyRequest, HistoryCallback historyCallback) {
        mainExecutorService.submit(() -> sendHistoryCommand(historyRequest, historyCallback));
    }

    private void sendHistoryCommand(HistoryRequest historyRequest, HistoryCallback historyCallback) {
        Protocol.Command command = buildCommand(Protocol.MethodType.HISTORY, historyRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                historyCallback.onReplyError(Error.fromReply(reply));
            } else {
                try {
                    historyCallback.onReplySuccess(HistoryCallback.HistoryResult.fromReply(reply));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                historyCallback.onSendFail(e);
            });
            return null;
        });
        sendCommand(command, future);
    }
    //endregion

    //region PING
    private void schedulePingAtFixedRate() {
        pingScheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
                Client.this::sendPingCommand,
                clientOptions.getPingInterval(),
                clientOptions.getPingInterval(),
                TimeUnit.MILLISECONDS
        );
    }

    private void sendPingCommand() {
        mainExecutorService.submit(() -> {
            if (clientConnectionState != ClientConnectionState.CONNECTED)
                return;
            Protocol.PingRequest pingRequest = Protocol.PingRequest.newBuilder().build();
            Protocol.Command command = buildCommand(Protocol.MethodType.PING, pingRequest.toByteString());
            CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
            future.thenAccept(reply ->
                    clearCommand(command)
            ).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
                mainExecutorService.submit(() -> {
                    clearCommand(command);
                    handlePingFailure();
                    e.printStackTrace();
                });
                return null;
            });
            sendCommand(command, future);
        });
    }

    private void handlePingFailure() {
        closeSocket(buildDisconnectReasonJson("no ping error", true), true);
    }

    private void cancelPing() {
        if (pingScheduledFuture != null)
            pingScheduledFuture.cancel(true);
    }
    //endregion

    //region PRESENCE
    public void presence(PresenceRequest presenceRequest, PresenceCallback presenceCallback) {
        mainExecutorService.submit(() -> sendPresenceCommand(presenceRequest, presenceCallback));
    }

    private void sendPresenceCommand(PresenceRequest presenceRequest, PresenceCallback presenceCallback) {
        Protocol.Command command = buildCommand(Protocol.MethodType.PRESENCE, presenceRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                presenceCallback.onReplyError(Error.fromReply(reply));
            } else {
                try {
                    presenceCallback.onReplySuccess(PresenceCallback.PresenceResult.fromReply(reply));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                presenceCallback.onSendFail(e);
            });
            return null;
        });
        sendCommand(command, future);
    }
    //endregion

    //region PRESENCE STATS
    public void presenceStats(PresenceStatsRequest presenceStatsRequest, PresenceStatsCallback presenceStatsCallback) {
        mainExecutorService.submit(() -> sendPresenceStatsCommand(presenceStatsRequest, presenceStatsCallback));
    }

    private void sendPresenceStatsCommand(PresenceStatsRequest presenceStatsRequest, PresenceStatsCallback presenceStatsCallback) {
        Protocol.Command command = buildCommand(Protocol.MethodType.PRESENCE_STATS, presenceStatsRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                presenceStatsCallback.onReplyError(Error.fromReply(reply));
            } else {
                try {
                    presenceStatsCallback.onReplySuccess(PresenceStatsCallback.PresenceStatsResult.fromReply(reply));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                presenceStatsCallback.onSendFail(e);
            });
            return null;
        });
        sendCommand(command, future);
    }
    //endregion

    //region PUBLISH
    // Use publish method to publish into channel without subscribing to it.
    public void publish(PublishRequest publishRequest, PublishCallback publishCallback) {
        mainExecutorService.submit(() -> sendPublishCommand(publishRequest, publishCallback));
    }

    private void sendPublishCommand(PublishRequest publishRequest, PublishCallback publishCallback) {
        Protocol.Command command = buildCommand(Protocol.MethodType.PUBLISH, publishRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                publishCallback.onReplyError(Error.fromReply(reply));
            } else {
                try {
                    publishCallback.onReplySuccess(PublishCallback.PublishResult.fromReply(reply));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                publishCallback.onSendFail(e);
            });
            return null;
        });
        sendCommand(command, future);
    }
    //endregion

    //region REFRESH
    private void scheduleRefresh(int ttl) {
        refreshScheduledFuture = scheduledExecutorService.schedule(
                Client.this::notifyRefreshToken,
                ttl,
                TimeUnit.SECONDS
        );
    }

    private void notifyRefreshToken() {
        mainExecutorService.submit(() -> {
            ClientEventListener.RefreshTokenData refreshTokenData = new ClientEventListener.RefreshTokenData(connectionId);
            ClientEventListener.RefreshTokenCallback refreshTokenCallback = new ClientEventListener.RefreshTokenCallback() {
                @Override
                public void onSuccess(String token) {
                    mainExecutorService.submit(() -> {
                        if (clientConnectionState == ClientConnectionState.CONNECTED) {
                            sendRefreshTokenCommand(token);
                        } else {
                            handleRefreshFailure("token refreshed, but socket is closed", true);
                        }
                    });
                }

                @Override
                public void onFail(Throwable e) {
                    mainExecutorService.submit(() -> handleRefreshFailure("refresh token failed", false));
                }
            };

            clientEventListener.onRefresh(this, refreshTokenData, refreshTokenCallback);
        });
    }

    private void sendRefreshTokenCommand(String token) {
        Protocol.RefreshRequest refreshRequest = Protocol.RefreshRequest.newBuilder()
                .setToken(token)
                .build();
        Protocol.Command command = buildCommand(Protocol.MethodType.REFRESH, refreshRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                handleRefreshFailure(reply.getError().getMessage(), false);
            } else {
                try {
                    handleRefreshSuccess(Protocol.RefreshResult.parseFrom(reply.getResult()));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                handleRefreshFailure("refresh error", true);
                e.printStackTrace();
            });
            return null;
        });
        sendCommand(command, future);
    }

    private void handleRefreshSuccess(Protocol.RefreshResult refreshResult) {
        if (refreshResult.getExpires())
            scheduleRefresh(refreshResult.getTtl());
    }

    @SuppressWarnings("SameParameterValue")
    private void handleRefreshFailure(String reason, boolean needScheduleReconnect) {
        closeSocket(buildDisconnectReasonJson(reason, needScheduleReconnect), needScheduleReconnect);
    }

    private void cancelRefresh() {
        if (refreshScheduledFuture != null)
            refreshScheduledFuture.cancel(true);
    }
    //endregion

    //region RECONNECT
    private void scheduleReconnect() {
        reconnectExecutorService.submit(() -> {
            try {
                Thread.sleep(backoff.duration());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            mainExecutorService.submit(() -> {
                if (needScheduleReconnect) {
                    openSocket();
                }
            });
        });
    }
    //endregion

    //region RPC
    // Use rpc method to send RPC request to server.
    public void rpc(RPCRequest rpcRequest, RPCCallback rpcCallback) {
        mainExecutorService.submit(() -> sendRPCCommand(rpcRequest, rpcCallback));
    }

    private void sendRPCCommand(RPCRequest rpcRequest, RPCCallback rpcCallback) {
        Protocol.Command command = buildCommand(Protocol.MethodType.RPC, rpcRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            if (hasReplyError(reply)) {
                rpcCallback.onReplyError(Error.fromReply(reply));
            } else {
                try {
                    rpcCallback.onReplySuccess(RPCCallback.RPCResult.fromReply(reply));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                rpcCallback.onSendFail(e);
            });
            return null;
        });
        sendCommand(command, future);
    }
    //endregion

    //region SEND
    // Use send method to send asynchronous message to server (without waiting response).
    public void send(SendRequest sendRequest, SendCallback sendCallback) {
        mainExecutorService.submit(() -> sendSendCommand(sendRequest, sendCallback));
    }

    private void sendSendCommand(SendRequest sendRequest, SendCallback sendCallback) {
        Protocol.Command command = buildCommand(Protocol.MethodType.SEND, sendRequest.toByteString());
        CompletableFuture<Protocol.Reply> future = new CompletableFuture<>();
        future.thenAccept(reply -> {
            clearCommand(command);
            sendCallback.onSendSuccess();
        }).orTimeout(clientOptions.getTimeout(), TimeUnit.MILLISECONDS).exceptionally(e -> {
            mainExecutorService.submit(() -> {
                clearCommand(command);
                sendCallback.onSendFail(e);
            });
            return null;
        });
        sendCommandAndComplete(command, future);
    }
    //endregion
}
