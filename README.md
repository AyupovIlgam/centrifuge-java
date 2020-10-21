# centrifuge-java

This is a Websocket client for Centrifugo and Centrifuge library. Client uses Protobuf protocol for client-server communication. `centrifuge-java` runs all operations in its own threads and provides necessary callbacks so you don't need to worry about managing concurrency yourself.

## Status of library

This library is developed by Ilgam Ayupov for Chocolife application. It is tested in production application and is stable. However any feedback is appreciated.

## Basic usage

See more example code in [console Java example](https://github.com/AyupovIlgam/centrifuge-java/tree/master/example/src/main/java/io/github/centrifugal/centrifuge/example/Main.java) or in [demo Android app](https://github.com/AyupovIlgam/centrifuge-java/tree/master/demo/src/main/java/io/github/centrifugal/centrifuge/demo/MainActivity.java)

Note that *you must use* `?format=protobuf` in connection URL as this client communicates with Centrifugo/Centrifuge over Protobuf protocol.

Also in case of running in Android emulator don't forget to use proper connection address to Centrifuge/Centrifugo (as `localhost` is pointing to emulator vm and obviously your server instance is not available there).

To connect to Centrifugo you need to additionally set connection JWT:

To use with Android don't forget to set INTERNET permission to `AndroidManifest.xml`:

```
<uses-permission android:name="android.permission.INTERNET" />
```

## Feature matrix

- [ ] connect to server using JSON protocol format
- [x] connect to server using Protobuf protocol format
- [x] connect with JWT
- [x] connect with custom header
- [x] automatic reconnect in case of errors, network problems etc
- [x] exponential backoff for reconnect
- [x] connect and disconnect events
- [x] handle disconnect reason
- [x] subscribe on channel and handle asynchronous Publications
- [x] handle Join and Leave messages
- [x] handle Unsubscribe notifications
- [x] reconnect on subscribe timeout
- [x] publish method of Subscription
- [x] unsubscribe method of Subscription
- [x] presence method of Subscription
- [x] presence stats method of Subscription
- [x] history method of Subscription
- [x] send asynchronous messages to server
- [x] handle asynchronous messages from server
- [x] send RPC commands
- [x] publish to channel without being subscribed
- [x] subscribe to private channels with JWT
- [x] connection JWT refresh
- [ ] private channel subscription JWT refresh
- [ ] handle connection expired error
- [ ] handle subscription expired error
- [x] ping/pong to find broken connection
- [ ] server-side subscriptions
- [ ] message recovery mechanism

## Generate proto

```
protoc --java_out=./ client.proto
mv io/github/centrifugal/centrifuge/internal/protocol/* centrifuge/src/main/java/io/github/centrifugal/centrifuge/internal/protocol/centrifuge/internal/protocol
rm -r io/
```

## License

Library is available under the MIT license. See LICENSE for details.

## Contributors

* Thanks to [Alexander Emelin](https://github.com/FZambia) for initial library setup
