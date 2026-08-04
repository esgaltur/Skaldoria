## Pairing Flow
### Split text and code

- The token is generated fresh on every server start
- It travels in the QR, then moves to a request header
- Stopping the server invalidates it immediately

```kotlin
fun presenterUrl(): String =
    "http://{IP}:{PORT}/remote?t={TOKEN}"

fun audienceUrl(): String =
    "http://{IP}:{PORT}/audience"
```

<!-- note: Point out that the audience URL has no token parameter at all. That is the whole security boundary in one line. -->
