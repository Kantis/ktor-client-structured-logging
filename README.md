# Ktor-client-structured-logging

>  work in progress, not yet published.

Adds structured logging to Ktor client.

Instead of:

```text
RESPONSE: 200 OK
METHOD: GET
FROM: http://localhost:56607/foo2
COMMON HEADERS
-> Content-Type: application/json
-> Matched-Stub-Id: 95314019-d197-402c-89bd-3d4683163593
-> Transfer-Encoding: chunked
BODY Content-Type: application/json
BODY START
{ "foo": "bar" }
BODY END
```

You get something like this as a single log statement (`logger.info` is called just once, with the json content as message). This format
can be easily parsed and indexed by log aggregators like ELK, Splunk, etc.

```json
{
    "url": "http://localhost:56429/foo2",
    "method": "GET",
    "statusCode": 200,
    "headers": {
        "Content-Type": "application/json",
        "Matched-Stub-Id": "f0ad8ec3-2f32-44ea-bd8e-00a0fb36ae6f",
        "Transfer-Encoding": "chunked"
    },
    "body": { "foo": "bar" }
}
```

## Usage

Install like a regular Ktor plugin
```kotlin
val client = HttpClient(CIO) {
   install(StructuredLogging) {
      requestLogging {
         enabled = true // default
         logHeaders = true // default
         logBody = true // Note: disabled by default!
      }

      responseLogging {
         logBody = true
      }
   }
}
```

Supports filters and sanitizing headers like regular Ktor logging plugin.

```kotlin
val client = HttpClient(CIO) {
  install(StructuredLogging) {
    filter { request -> request.url.host.contains("ktor.io") }
    sanitizeHeader { header -> header == HttpHeaders.Authorization }
  }
}
```

## Log implementation support

### Log4j2

Log4j2 does not yet have full support for structured logging. The fact that it uses the ThreadContext to store the structured
data makes it risky when used with coroutines. https://github.com/apache/logging-log4j2/issues/1813

### Logback

TODO..
