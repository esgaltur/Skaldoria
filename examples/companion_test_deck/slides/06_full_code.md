<!-- layout: code -->
```kotlin [4, 7-10]
private fun isAuthorized(params: Map<String, String>, headers: Map<String, String>): Boolean {
    val expected = sessionToken
    if (expected.isEmpty()) return false
    val supplied = headers["x-skaldoria-token"] ?: params["t"] ?: return false

    // Constant-time compare so the token cannot be recovered by timing responses.
    return MessageDigest.isEqual(
        supplied.toByteArray(UTF_8),
        expected.toByteArray(UTF_8)
    )
}
```

<!-- note: Lines 4 and 7-10 are highlighted by the [4, 7-10] annotation on the fence. Handy for walking through code without a laser pointer. -->
