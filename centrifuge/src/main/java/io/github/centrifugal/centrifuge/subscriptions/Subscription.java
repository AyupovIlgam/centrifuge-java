package io.github.centrifugal.centrifuge.subscriptions;

import io.github.centrifugal.centrifuge.Client;
import io.github.centrifugal.centrifuge.protobuf.Protocol;

public class Subscription {

    private final Client client;
    private final String channel;
    private final SubscriptionEventListener subscriptionEventListener;
    private SubscriptionState state;
    private boolean needResubscribe;

    public Subscription(
            final Client client,
            final String channel,
            final SubscriptionEventListener subscriptionEventListener
    ) {
        this.client = client;
        this.channel = channel;
        this.subscriptionEventListener = subscriptionEventListener;
        this.state = SubscriptionState.UNSUBSCRIBED;
        this.needResubscribe = true;
    }

    public String getChannel() {
        return channel;
    }

    public boolean isPrivate() {
        return channel.startsWith(client.getClientOptions().getPrivateChannelPrefix());
    }

    public SubscriptionEventListener getSubscriptionEventListener() {
        return subscriptionEventListener;
    }

    public SubscriptionState getState() {
        return state;
    }

    public boolean needResubscribe() {
        return needResubscribe;
    }

    public void onSubscribeSuccess(Protocol.SubscribeResult subscribeResult) {
        state = SubscriptionState.SUBSCRIBED;
        SubscriptionEventListener.SubscribeSuccessEvent subscribeSuccessEvent =
                SubscriptionEventListener.SubscribeSuccessEvent.fromProto(client.getConnectionId(), channel, subscribeResult);
        subscriptionEventListener.onSubscribeSuccess(this, subscribeSuccessEvent);
    }

    public void onSubscribeError(int code, String message) {
        state = SubscriptionState.SUBSCRIBE_ERROR;
        SubscriptionEventListener.SubscribeErrorEvent event = new SubscriptionEventListener.SubscribeErrorEvent(client.getConnectionId(), channel, code, message);
        subscriptionEventListener.onSubscribeError(this, event);
    }

    public void onUnsubscribed() {
        if (state == SubscriptionState.SUBSCRIBED) {
            SubscriptionEventListener.UnsubscribeEvent unsubscribeEvent =
                    new SubscriptionEventListener.UnsubscribeEvent(client.getConnectionId(), channel, needResubscribe);
            subscriptionEventListener.onUnsubscribe(this, unsubscribeEvent);
        }
        state = SubscriptionState.UNSUBSCRIBED;
    }
}
