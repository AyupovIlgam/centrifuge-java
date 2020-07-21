package io.github.centrifugal.centrifuge.demo;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import io.github.centrifugal.centrifuge.Client;
import io.github.centrifugal.centrifuge.ClientEventListener;
import io.github.centrifugal.centrifuge.ClientOptions;
import io.github.centrifugal.centrifuge.subscriptions.Subscription;
import io.github.centrifugal.centrifuge.subscriptions.SubscriptionEventListener;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView tv = findViewById(R.id.text);

        // 1 Step
        ClientOptions clientOptions = new ClientOptions();
        clientOptions.setTimeout(10000);
        clientOptions.setPingInterval(10000);
        //options.setInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));
        clientOptions.setLogsEnabled(true); // BuildConfig.DEBUG

        // 2 Step
        SubscriptionEventListener subscriptionEventListener = new SubscriptionEventListener() {
            @Override
            public void onPrivateSub(Client client, PrivateSubEvent event, PrivateSubTokenCallback cb) {
                super.onPrivateSub(client, event, cb);
            }

            @Override
            public void onSubscribeSuccess(Subscription subscription, SubscribeSuccessEvent event) {
                super.onSubscribeSuccess(subscription, event);
            }

            @Override
            public void onSubscribeError(Subscription subscription, SubscribeErrorEvent event) {
                super.onSubscribeError(subscription, event);
            }

            @Override
            public void onPublication(Subscription subscription, PublicationEvent event) {
                super.onPublication(subscription, event);
            }

            @Override
            public void onJoin(Subscription subscription, JoinEvent event) {
                super.onJoin(subscription, event);
            }

            @Override
            public void onLeave(Subscription subscription, LeaveEvent event) {
                super.onLeave(subscription, event);
            }

            @Override
            public void onUnsubscribe(Subscription subscription, UnsubscribeEvent event) {
                super.onUnsubscribe(subscription, event);
            }
        };

        // 3 Step
        ClientEventListener clientEventListener = new ClientEventListener() {
            @Override
            public void onConnect(Client client, ConnectData data) {
                super.onConnect(client, data);
                MainActivity.this.runOnUiThread(() -> tv.setText(R.string.connected));
                client.subscribe("channel", subscriptionEventListener);
            }

            @Override
            public void onMessage(Client client, MessageData data) {
                super.onMessage(client, data);
            }

            @Override
            public void onRefresh(Client client, RefreshTokenData data, RefreshTokenCallback cb) {
                super.onRefresh(client, data, cb);
                // Refresh token and call:
                cb.onSuccess("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0c3VpdGVfand0In0.hPmHsVqvtY88PvK4EmJlcdwNuKFuy3BGaF7dMaKdPlw");
            }

            @Override
            public void onDisconnect(Client client, DisconnectData data) {
                super.onDisconnect(client, data);
                MainActivity.this.runOnUiThread(() -> tv.setText(R.string.disconnected));
                client.unsubscribe("channel");
            }

            @Override
            public void onError(Client client, ErrorData data) {
                super.onError(client, data);
            }
        };

        // 4 Step
        Client client = new Client(clientOptions, clientEventListener, "ws://192.168.1.35:8000/connection/websocket?format=protobuf");
        // Request your own token
        client.connect("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0c3VpdGVfand0In0.hPmHsVqvtY88PvK4EmJlcdwNuKFuy3BGaF7dMaKdPlw");
    }
}
