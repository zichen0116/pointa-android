# Android protocol implementation

This JVM module implements the existing Windows v1 contract without Android UI or storage dependencies. The app owns Keystore persistence and intentional reconnects; callbacks run on the client's single protocol thread and must be dispatched to the UI thread.

Scope: strict QR and discovered-host validation; one isolated TLS/WebSocket client per connection; pinned QR/resume and certificate-bound SRP-6a; explicit authentication phases and deadlines; approved credentials; monotonic server-time synchronization; bounded control rates; action/effect/display feedback; JVM tests and real Windows-server interoperability.

Trust boundary: discovery is only a hint. Only private/loopback numeric IPv4 destinations are accepted. Candidate TLS is available solely to a fresh six-digit-code attempt, records the actual DER certificate, and cannot send remembered credentials. The SRP server proof must succeed before credentials are accepted. Approved pins and stable server IDs must match before reconnect authentication. Credentials are never logged or automatically replayed to another host identity.

Controls are never replayed across connections. Pointer updates use one latest-value slot flushed at 60 Hz; hide clears it synchronously on the protocol thread. Slide presses use fresh UUIDs with a 180 ms cooldown. Heartbeat and deadlines use monotonic time. Connection generations reject late socket callbacks.

Checks cover URI/JSON boundaries, local destinations, SRP evidence conventions and fixed vectors, invalid authentication transitions, certificate binding, and a loopback C# host. Physical sensor behavior, Android lifecycle, Keystore, Wi-Fi discovery on real hardware and latency measurements belong to the app/integration checks.
